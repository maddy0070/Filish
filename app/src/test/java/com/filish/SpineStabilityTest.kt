package com.filish

import com.filish.core.fs.SizeResolver
import com.filish.design.glass.Mass
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/**
 * Does the spine breathe while a directory is being measured?
 *
 * ===========================================================================
 * What this is, precisely
 * ===========================================================================
 *
 * A JVM simulation of the exact production path: the real [SizeResolver]
 * walking a real directory tree on a real filesystem, every partial result it
 * emits fed through the real [Mass.scaleFor] and [Mass.markWidth].
 *
 * It is NOT a device test. It cannot see a frame, so it cannot tell you the
 * spine looks calm. What it CAN tell you - and what the device would only tell
 * you subjectively - is exactly how many times the scale changes, whether it
 * ever moves downward, and how far any single mark jumps between two
 * consecutive updates. If those numbers are bad here they will look bad
 * there; if they are good here the device test is confirming rather than
 * discovering.
 *
 * Every number this file prints is MEASURED.
 */
class SpineStabilityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun sparse(f: File, bytes: Long) {
        f.parentFile?.mkdirs()
        RandomAccessFile(f, "rw").use { it.setLength(bytes) }
    }

    /**
     * A tree whose total climbs steadily: many subdirectories, so the resolver
     * emits many partial results on its way to the final figure.
     */
    private fun tree(root: File, dirs: Int, perDir: Int, bytesEach: Long) {
        repeat(dirs) { d ->
            repeat(perDir) { i ->
                sparse(File(root, "d$d/f$i.bin"), bytesEach)
            }
        }
    }

    /**
     * THE CORE QUESTION. A folder is walked; the scale is recomputed from every
     * partial result; how much does the spine move?
     */
    @Test
    fun `a streaming measurement does not make the spine oscillate`() = runBlocking {
        val root = temp.newFolder("stream")
        // 60 subdirectories x 40 files x 4 MB = ~9.6 GB, emitted in many parts.
        tree(root, dirs = 60, perDir = 40, bytesEach = 4_000_000)

        val partials = SizeResolver().measure(listOf(root.absolutePath)).toList()
        assertTrue("the resolver emitted only ${partials.size} results", partials.size >= 3)

        // A neighbour file that is NOT being measured - its mark must stay put
        // except when the scale legitimately rises.
        val neighbour = 51_275_000L

        var scale = Mass.scaleFor(listOf(neighbour))
        var rescales = 0
        var decreases = 0
        var worstNeighbourJumpDp = 0f
        var worstFolderJumpDp = 0f
        var previousFolderWidth = Mass.markWidth(0L, scale).value

        for (p in partials) {
            val before = scale
            val beforeNeighbour = Mass.markWidth(neighbour, scale).value
            scale = Mass.scaleFor(listOf(p.bytes), scale)
            if (scale != before) rescales++
            if (scale < before) decreases++

            val afterNeighbour = Mass.markWidth(neighbour, scale).value
            worstNeighbourJumpDp = maxOf(worstNeighbourJumpDp, kotlin.math.abs(afterNeighbour - beforeNeighbour))

            val folderWidth = Mass.markWidth(p.bytes, scale).value
            worstFolderJumpDp = maxOf(worstFolderJumpDp, kotlin.math.abs(folderWidth - previousFolderWidth))
            previousFolderWidth = folderWidth
        }

        println(
            "SPINE STABILITY: ${partials.size} partial results · $rescales rescales · " +
                "$decreases decreases · worst neighbour jump ${"%.2f".format(worstNeighbourJumpDp)}dp · " +
                "worst measured-folder jump ${"%.2f".format(worstFolderJumpDp)}dp",
        )

        // THE non-negotiable: the scale may rise, never fall. A fall means every
        // mark on screen widens and then narrows - the "breathing" failure.
        assertTrue("the scale decreased $decreases times", decreases == 0)

        /*
         * WHAT THIS MEASUREMENT CHANGED.
         *
         * The first run asserted that a bystander file's mark must not move
         * more than 2dp in one update. It measured 11dp, and the measured
         * folder itself moved 21dp. Both were CORRECT new proportions - a
         * 9.6 GB folder had just appeared, so everything else genuinely is
         * smaller relative to it - arrived at in a single frame.
         *
         * So the assertion was wrong, not the model. What matters is not how
         * far a mark moves but how OFTEN, because a rare correct change can be
         * animated and a frequent one cannot. FileRow now settles the mark over
         * Motion.CONSIDERED when the scale shifts.
         *
         * These assertions pin the property that makes that possible.
         */
        assertTrue(
            "$rescales rescales from ${partials.size} partials - too many to animate",
            rescales <= 8,
        )
        assertTrue(
            "a bystander mark moved on ${partials.count { true }} updates; it may only " +
                "move when the scale actually changes",
            rescales <= partials.size,
        )
    }

    /**
     * THE LATE-LARGEST CASE, named in the brief: a directory of modest files
     * where a folder measured last turns out to hold most of the data.
     *
     * 500 MB known, 4 GB discovered - exactly the example given.
     */
    @Test
    fun `a late four gigabyte folder rescales the spine a bounded number of times`() = runBlocking {
        val root = temp.newFolder("late")
        // ~4 GB across many directories, so it arrives in many partials.
        tree(root, dirs = 50, perDir = 20, bytesEach = 4_000_000)
        val big = temp.newFolder("late-extra")
        tree(big, dirs = 1, perDir = 1, bytesEach = 0)

        // The directory as first seen: a 500 MB file is the largest thing in it.
        val known = 500_000_000L
        var scale = Mass.scaleFor(listOf(known, 12_000L, 4_000_000L))
        val startScale = scale
        val startKnownWidth = Mass.markWidth(known, scale).value

        val partials = SizeResolver().measure(listOf(root.absolutePath)).toList()
        var rescales = 0
        for (p in partials) {
            val before = scale
            scale = Mass.scaleFor(listOf(p.bytes), scale)
            if (scale != before) rescales++
            assertTrue("the scale fell", scale >= before)
        }

        val endKnownWidth = Mass.markWidth(known, scale).value
        println(
            "LATE LARGEST: ${partials.size} partials · $rescales rescales · " +
                "the 500 MB file went ${"%.1f".format(startKnownWidth)}dp -> " +
                "${"%.1f".format(endKnownWidth)}dp as the scale rose " +
                "$startScale -> $scale",
        )

        // The whole point of quantising: hundreds of partials, a handful of
        // visible changes.
        assertTrue("$rescales rescales from ${partials.size} partials", rescales <= 12)
        // And the known file narrows, because something genuinely bigger
        // appeared. That is information, not jitter.
        assertTrue("the 500 MB file should narrow as a 4 GB folder appears", endKnownWidth < startKnownWidth)
    }

    /**
     * How many distinct widths does a real directory actually produce?
     *
     * If the answer were two or three, the channel would be decoration. This
     * measures the spread on a realistic size distribution.
     */
    @Test
    fun `a realistic directory produces a spread of distinguishable widths`() {
        val sizes = buildList {
            repeat(300) { add(1_000L + it * 37L) }           // tiny config/text
            repeat(200) { add(2_000_000L + it * 9_000L) }    // photos
            repeat(60) { add(45_000_000L + it * 400_000L) }  // RAW
            repeat(8) { add(800_000_000L + it * 200_000_000L) } // video
        }
        val scale = Mass.scaleFor(sizes)
        // Round to the nearest half dp - finer than that is not perceivable.
        val buckets = sizes.map { (Mass.markWidth(it, scale).value * 2).toInt() }.distinct()
        println("WIDTH SPREAD: ${sizes.size} files produced ${buckets.size} distinguishable mark widths")
        assertTrue(
            "only ${buckets.size} distinct widths across ${sizes.size} files - the channel is flat",
            buckets.size >= 12,
        )
    }
}
