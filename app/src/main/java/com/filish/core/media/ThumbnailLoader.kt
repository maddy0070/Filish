package com.filish.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.filish.core.model.FileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thumbnails, written rather than imported.
 *
 * Every mainstream Android image-loading library carries an HTTP stack. Coil
 * pulls OkHttp; Glide pulls its own networking. FILISH declares no INTERNET
 * permission at all, so those dependencies would ship dead code into the APK
 * and quietly contradict the claim that this application cannot talk to the
 * network. Writing the loader is a few hundred lines and removes the
 * contradiction entirely.
 *
 * It also happens to be better suited to the job. A file manager's thumbnail
 * problem is not a web gallery's: sources are local, arbitrarily large,
 * frequently malformed, and the user scrolls fast through directories of tens
 * of thousands of entries. What matters is bounded memory, aggressive
 * downsampling, hard concurrency limits, and never decoding a 108-megapixel
 * photograph to draw it 40dp wide.
 *
 * MEMORY IS BOUNDED BY BYTES, NOT BY COUNT. A cache of "200 bitmaps" is
 * meaningless when one bitmap may be 40 KB and another 4 MB. The budget is a
 * fraction of the process heap and entries are sized by their actual
 * allocation.
 *
 * CONCURRENCY IS CAPPED. Decoding is CPU- and allocation-heavy; running one
 * decode per visible row on a fast scroll produces jank and OOM. A small
 * permit pool keeps the device responsive and, since cancelled requests never
 * acquire a permit, scrolling past an item costs nothing.
 */
class ThumbnailLoader(private val context: Context) {

    /** A decoded thumbnail, or a considered reason there isn't one. */
    sealed interface Result {
        data class Image(val bitmap: Bitmap) : Result
        /** The file is a type FILISH can represent with a glyph instead. */
        data object NoPreview : Result
        /** The file claims to be an image but could not be decoded. Shown as
         *  a distinct state: "this photo is damaged" is useful information. */
        data object Unreadable : Result
    }

    private val budgetBytes: Int =
        ((Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024)).toInt()

    private val cache = object : LruCache<String, Bitmap>(budgetBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Files already known to be undecodable, so a broken image in a folder is
     *  attempted once rather than on every scroll pass. */
    private val failed = object : LruCache<String, Boolean>(512) {}

    private val decodeGate = Semaphore(3)

    private fun key(path: String, px: Int, mtime: Long) = "$path|$px|$mtime"

    fun cached(path: String, px: Int, mtime: Long): Bitmap? = cache.get(key(path, px, mtime))

    suspend fun load(
        path: String,
        kind: FileKind,
        targetPx: Int,
        lastModified: Long,
    ): Result = withContext(Dispatchers.IO) {
        val k = key(path, targetPx, lastModified)
        cache.get(k)?.let { return@withContext Result.Image(it) }
        if (failed.get(path) == true) return@withContext Result.Unreadable

        if (!kind.isMedia && kind != FileKind.Pdf) return@withContext Result.NoPreview

        decodeGate.withPermit {
            // Re-check: another request may have finished while queued.
            cache.get(k)?.let { return@withPermit Result.Image(it) }

            val bitmap = when (kind) {
                FileKind.Image, FileKind.RawImage -> decodeImage(path, targetPx)
                FileKind.Video -> decodeVideoFrame(path, targetPx)
                FileKind.Audio -> decodeAudioArt(path, targetPx)
                else -> null
            }

            if (bitmap == null) {
                failed.put(path, true)
                Result.Unreadable
            } else {
                cache.put(k, bitmap)
                Result.Image(bitmap)
            }
        }
    }

    /**
     * Two-pass decode. The first pass reads only the header to learn the real
     * dimensions; the second decodes at the smallest power-of-two reduction
     * that still exceeds the target. Decoding full-size and scaling down would
     * allocate the entire image - a 50 MP photo is 200 MB as an ARGB bitmap,
     * which is an OOM on most phones, for a picture drawn 40dp wide.
     */
    private fun decodeImage(path: String, targetPx: Int): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // RAW and HEIF headers are not always readable by BitmapFactory.
            // ThumbnailUtils delegates to the platform extractors, which
            // frequently succeed where a direct decode does not.
            return platformThumbnail(file, targetPx)
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val raw = runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
            ?: return platformThumbnail(file, targetPx)

        return applyExifRotation(path, raw)
    }

    private fun platformThumbnail(file: File, targetPx: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { ThumbnailUtils.createImageThumbnail(file, Size(targetPx, targetPx), null) }
                .getOrNull()
        } else {
            null
        }

    /**
     * A video's thumbnail is whichever frame best represents it. The first
     * frame is usually black - fades from black are near-universal - so a
     * frame a little way in is requested first, with the first frame as the
     * fallback for clips too short to have one.
     */
    private fun decodeVideoFrame(path: String, targetPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val atUs = if (durationMs > 2_000) 1_000_000L else 0L

            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                runCatching {
                    retriever.getScaledFrameAtTime(
                        atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetPx, targetPx,
                    )
                }.getOrNull()
            } else {
                null
            } ?: retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            frame ?: retriever.frameAtTime
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeAudioArt(path: String, targetPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(art, 0, art.size, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(art, 0, art.size, opts)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
        if (targetPx <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= targetPx && h / 2 >= targetPx) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * Phone cameras record orientation in EXIF rather than rotating pixels.
     * Ignoring it shows a quarter of a camera roll sideways, which reads as a
     * bug in the file manager rather than as a property of the file.
     */
    private fun applyExifRotation(path: String, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            when (
                ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        return runCatching {
            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
            )
            if (rotated != bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    /** Called when memory is tight. Dropping thumbnails is always preferable
     *  to being killed mid-operation. */
    fun trim(aggressive: Boolean) {
        if (aggressive) cache.evictAll() else cache.trimToSize(cache.size() / 2)
    }
}
