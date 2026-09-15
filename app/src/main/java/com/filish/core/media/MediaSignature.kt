package com.filish.core.media

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A media signature: three tones sampled from an object's own content.
 *
 * ===========================================================================
 * Why a signature and not the thumbnail itself
 * ===========================================================================
 *
 * The design calls for a photograph's mark to carry a sliver of that
 * photograph, so a run of pictures reads as a record of the actual shots
 * rather than as seven identical orange bars.
 *
 * The obvious implementation - crop the thumbnail to a 26dp-wide strip and
 * draw it - was considered and rejected on cost. It means a bitmap draw per
 * visible row per frame, a decoded bitmap held per row, and a second
 * thumbnail pipeline at a different target size, all to render detail nobody
 * can resolve in a strip 26dp wide. At 5,000 entries, scrolled fast, that is
 * exactly the kind of per-item expense the performance rules forbid.
 *
 * What a 26dp strip actually communicates is TONE - is this shot warm, cold,
 * dark, bright - and tone survives reduction to three colours. So a signature
 * is three ARGB ints. Drawing it is a three-stop vertical gradient, which is
 * the same cost as the flat kind tint it replaces. Holding it is twelve bytes
 * per file instead of a bitmap.
 *
 * The rendered exploration is the evidence this is enough: with per-file tonal
 * gradients the spine reads as the user's own photographs; with flat category
 * tints it does not.
 *
 * ===========================================================================
 * What this deliberately does NOT do
 * ===========================================================================
 *
 * It never decodes on its own. It only samples a bitmap the thumbnail loader
 * has ALREADY produced for some other reason, so enabling signatures adds
 * decode cost of exactly zero. If no thumbnail exists yet, the caller gets
 * null and the mark falls back to its kind tint - which is a complete,
 * legible, permanent answer rather than a placeholder.
 *
 * That is what makes the feature safe to ship behind a flag: the worst case is
 * the design without it.
 */
class MediaSignature(private val thumbnails: ThumbnailLoader) {

    /**
     * Three tones: top, middle, bottom of a centre strip.
     *
     * Vertical rather than horizontal because the mark is vertical, so a
     * portrait shot's sky-to-ground gradient survives into the mark.
     */
    data class Tones(val top: Int, val mid: Int, val bottom: Int) {
        fun asColors(): List<Color> = listOf(Color(top), Color(mid), Color(bottom))
    }

    // Twelve bytes a file, so this can be generous without mattering.
    private val cache = object : LruCache<String, Tones>(4096) {}
    private val absent = object : LruCache<String, Boolean>(1024) {}

    private fun key(path: String, mtime: Long) = "$path|$mtime"

    /** Non-blocking: the signature if it is already known, else null. */
    fun cached(path: String, mtime: Long): Tones? = cache.get(key(path, mtime))

    /** True when this object has been established to have no signature, so
     *  callers can stop asking rather than retrying every recomposition. */
    fun known(path: String, mtime: Long): Boolean =
        cache.get(key(path, mtime)) != null || absent.get(key(path, mtime)) == true

    /**
     * Derive a signature from an already-decoded thumbnail, if there is one.
     *
     * [px] must match the size the caller asked the loader for, or the cache
     * key will miss and this will correctly return null rather than
     * triggering a decode.
     */
    suspend fun derive(path: String, mtime: Long, px: Int): Tones? {
        val k = key(path, mtime)
        cache.get(k)?.let { return it }
        if (absent.get(k) == true) return null

        val bitmap = thumbnails.cached(path, px, mtime)
        if (bitmap == null) {
            // Not a failure - the thumbnail simply is not there yet. Do not
            // record absence, or a signature would never appear once the
            // thumbnail arrived.
            return null
        }
        val tones = withContext(Dispatchers.Default) { sample(bitmap) }
        if (tones == null) {
            absent.put(k, true)
            return null
        }
        cache.put(k, tones)
        return tones
    }

    /** Records that an object can never have a signature - a PDF, a folder. */
    fun markAbsent(path: String, mtime: Long) {
        absent.put(key(path, mtime), true)
    }

    fun clear() {
        cache.evictAll()
        absent.evictAll()
    }

    /**
     * Average three horizontal bands of the bitmap's centre column.
     *
     * Averaging rather than picking a dominant colour: a dominant-colour
     * algorithm on a photograph returns whatever the largest flat region is,
     * which for most pictures is sky or wall, and makes half a camera roll the
     * same pale blue. An average keeps the shot's overall cast, which is what
     * a 26dp strip can express anyway.
     */
    private fun sample(bitmap: Bitmap): Tones? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        // A centre column, because edges are frequently letterboxing, a
        // watermark, or the dark border a video frame arrives with.
        val x0 = (w * 0.30f).toInt().coerceIn(0, w - 1)
        val x1 = (w * 0.70f).toInt().coerceIn(x0 + 1, w)
        val bandHeight = (h / 3).coerceAtLeast(1)

        fun band(index: Int): Int {
            val yStart = (index * bandHeight).coerceIn(0, h - 1)
            val yEnd = ((index + 1) * bandHeight).coerceIn(yStart + 1, h)
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0L
            // Step rather than read every pixel: a 40dp thumbnail is small
            // already, and the average of a quarter of it is the same average.
            val stepX = ((x1 - x0) / 8).coerceAtLeast(1)
            val stepY = ((yEnd - yStart) / 8).coerceAtLeast(1)
            var y = yStart
            while (y < yEnd) {
                var x = x0
                while (x < x1) {
                    val p = bitmap.getPixel(x, y)
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    n++
                    x += stepX
                }
                y += stepY
            }
            if (n == 0L) return 0xFF808080.toInt()
            return (0xFF shl 24) or
                (((r / n).toInt() and 0xFF) shl 16) or
                (((g / n).toInt() and 0xFF) shl 8) or
                ((b / n).toInt() and 0xFF)
        }

        return Tones(band(0), band(1), band(2))
    }
}
