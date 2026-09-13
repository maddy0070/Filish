package com.filish.core.media

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.filish.core.model.FileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The facts about a file that only its contents can tell you.
 *
 * Every field is nullable and every read is guarded, because media metadata
 * is the least trustworthy data on a device: files are truncated by failed
 * downloads, written by buggy encoders, and renamed to extensions they are
 * not. A properties screen that crashes on a malformed MP3 is a worse
 * properties screen than one that says "unknown".
 *
 * Read lazily. None of this is needed to *list* a file, and doing it during a
 * listing would mean opening every file in a directory.
 */
data class MediaFacts(
    val width: Int? = null,
    val height: Int? = null,
    val durationMillis: Long? = null,
    val bitrate: Int? = null,
    val mimeType: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val trackNumber: String? = null,
    val frameRate: String? = null,
    val hasAudio: Boolean? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val exposure: String? = null,
    val focalLength: String? = null,
    val dateTaken: String? = null,
    /**
     * True when the file carries GPS coordinates.
     *
     * FILISH reports the *presence* of location data, not the coordinates.
     * Knowing a photo is geotagged is genuinely useful - it is what someone
     * needs to know before sharing it. Printing the latitude and longitude on
     * a properties screen is a privacy hazard for anyone who hands their phone
     * to another person, and it answers a question almost nobody asked.
     */
    val hasLocation: Boolean = false,
) {
    val dimensions: String?
        get() = if (width != null && height != null && width > 0 && height > 0) {
            "$width x $height"
        } else {
            null
        }

    val megapixels: String?
        get() = if (width != null && height != null && width > 0 && height > 0) {
            String.format(java.util.Locale.US, "%.1f MP", width.toLong() * height / 1_000_000.0)
        } else {
            null
        }

    val isEmpty: Boolean
        get() = width == null && durationMillis == null && title == null &&
            bitrate == null && cameraMake == null
}

object MediaMetadata {

    suspend fun read(path: String, kind: FileKind): MediaFacts = withContext(Dispatchers.IO) {
        when (kind) {
            FileKind.Image, FileKind.RawImage -> readImage(path)
            FileKind.Video, FileKind.Audio -> readAv(path, kind)
            else -> MediaFacts()
        }
    }

    private fun readImage(path: String): MediaFacts {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, bounds) }

        val exif = runCatching { ExifInterface(path) }.getOrNull()

        // EXIF orientation means the stored pixel dimensions may be the
        // transpose of what the user sees. Report what they see.
        val rotated = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )?.let { it == ExifInterface.ORIENTATION_ROTATE_90 || it == ExifInterface.ORIENTATION_ROTATE_270 }
            ?: false

        var w = bounds.outWidth.takeIf { it > 0 }
        var h = bounds.outHeight.takeIf { it > 0 }
        if (w == null || h == null) {
            w = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)?.takeIf { it > 0 }
            h = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)?.takeIf { it > 0 }
        }
        if (rotated && w != null && h != null) {
            val t = w; w = h; h = t
        }

        return MediaFacts(
            width = w,
            height = h,
            mimeType = bounds.outMimeType,
            cameraMake = exif?.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.ifBlank { null },
            cameraModel = exif?.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.ifBlank { null },
            iso = exif?.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?.ifBlank { null }?.let { "ISO $it" },
            aperture = exif?.getAttribute(ExifInterface.TAG_F_NUMBER)?.ifBlank { null }?.let { "f/$it" },
            exposure = exif?.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.ifBlank { null }
                ?.toDoubleOrNull()?.let { formatExposure(it) },
            focalLength = exif?.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.ifBlank { null }
                ?.let { formatFocalLength(it) },
            dateTaken = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.ifBlank { null },
            hasLocation = exif?.latLong != null,
        )
    }

    /** "1/250" reads as a shutter speed; "0.004" does not. */
    private fun formatExposure(seconds: Double): String = when {
        seconds <= 0 -> "-"
        seconds >= 1 -> String.format(java.util.Locale.US, "%.1fs", seconds)
        else -> "1/${Math.round(1.0 / seconds)}s"
    }

    /** EXIF stores focal length as a rational, e.g. "2600/1000". */
    private fun formatFocalLength(raw: String): String? {
        val value = if (raw.contains('/')) {
            val parts = raw.split('/')
            val n = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
            val d = parts.getOrNull(1)?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
            n / d
        } else {
            raw.toDoubleOrNull() ?: return null
        }
        return String.format(java.util.Locale.US, "%.0f mm", value)
    }

    private fun readAv(path: String, kind: FileKind): MediaFacts {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            fun get(key: Int) = runCatching { r.extractMetadata(key) }.getOrNull()?.ifBlank { null }

            MediaFacts(
                width = get(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = get(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                durationMillis = get(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                bitrate = get(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                mimeType = get(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                title = get(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = get(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: get(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = get(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                year = get(MediaMetadataRetriever.METADATA_KEY_YEAR),
                trackNumber = get(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                frameRate = if (android.os.Build.VERSION.SDK_INT >= 23) {
                    get(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        ?.toFloatOrNull()?.let { String.format(java.util.Locale.US, "%.0f fps", it) }
                } else {
                    null
                },
                hasAudio = if (kind == FileKind.Video) {
                    get(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                } else {
                    true
                },
                hasLocation = get(MediaMetadataRetriever.METADATA_KEY_LOCATION) != null,
            )
        } catch (t: Throwable) {
            MediaFacts()
        } finally {
            runCatching { r.release() }
        }
    }
}
