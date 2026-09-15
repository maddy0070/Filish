package com.filish

import com.filish.design.glass.Mass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The magnitude channel.
 *
 * A visual channel that carries a quantity is a claim about that quantity, and
 * a claim has to be correct before it is pretty. If a bigger file can ever draw
 * a narrower mark, the channel is worse than useless - it is lying about the
 * user's data, quietly, in the one place they are looking to decide what to
 * delete.
 */
class MassTest {

    private val GB = 1_000_000_000L
    private val MB = 1_000_000L
    private val KB = 1_000L

    @Test
    fun `a bigger file never draws a narrower mark`() {
        val largest = 8 * GB
        val sizes = listOf(
            0L, 1L, 512L, 4 * KB, 64 * KB, 900 * KB,
            3 * MB, 48 * MB, 700 * MB, 2 * GB, 5 * GB, 8 * GB,
        )
        var previous = -1f
        for (s in sizes) {
            val m = Mass.relative(s, largest)
            assertTrue(
                "magnitude went backwards at $s bytes ($m after $previous)",
                m >= previous,
            )
            previous = m
        }
    }

    @Test
    fun `the largest item in a listing fills the channel`() {
        assertEquals(1f, Mass.relative(4 * GB, 4 * GB), 0.0001f)
        assertEquals(Mass.markMax, Mass.markWidth(4 * GB, 4 * GB))
    }

    @Test
    fun `nothing is ever invisible`() {
        // A zero-byte file is still an object the user can act on.
        assertEquals(Mass.markMin, Mass.markWidth(0L, 4 * GB))
        assertEquals(Mass.markMin, Mass.markWidth(1L, 4 * GB))
        for (s in listOf(0L, 1L, 10L, KB, MB, GB)) {
            assertTrue(Mass.markWidth(s, 900 * GB) >= Mass.markMin)
        }
    }

    @Test
    fun `the mark never overflows its channel`() {
        for (s in listOf(0L, KB, MB, GB, 64 * GB, Long.MAX_VALUE / 2)) {
            val w = Mass.markWidth(s, GB)
            assertTrue("$s overflowed the mark ceiling", w <= Mass.markMax)
            assertTrue("$s must fit the gutter", w <= Mass.channel)
        }
    }

    /**
     * The spread the absolute scale failed to deliver. These four sit inside
     * one plausible camera folder, and the whole point of relative scaling is
     * that they are visibly different from one another.
     */
    @Test
    fun `sizes inside one real directory are visibly distinguishable`() {
        val largest = 4_812_000_000L // a screen-recording folder
        val marks = listOf(
            "folder" to Mass.markWidth(2_297_000_000L, largest),
            "archive" to Mass.markWidth(418_000_000L, largest),
            "raw" to Mass.markWidth(51_275_000L, largest),
            "pdf" to Mass.markWidth(2_243_000L, largest),
            "txt" to Mass.markWidth(12_288L, largest),
        )
        for (i in 0 until marks.size - 1) {
            val (an, a) = marks[i]
            val (bn, b) = marks[i + 1]
            val delta = (a - b).value
            assertTrue(
                "$an and $bn differ by only ${delta}dp - the channel is flat there",
                delta >= 1.0f,
            )
        }
    }

    @Test
    fun `an empty or unknown listing does not divide by zero`() {
        assertEquals(0f, Mass.relative(500L, 0L), 0.0001f)
        assertEquals(0f, Mass.relative(500L, -1L), 0.0001f)
        assertEquals(Mass.markMin, Mass.markWidth(500L, 0L))
    }

    @Test
    fun `anything negligible against the largest item sits at the floor`() {
        // 2^16 times smaller and below: negligible here, and saying so is more
        // useful than spending channel on it.
        val largest = 1L shl 40
        assertEquals(0f, Mass.relative(largest shr 16, largest), 0.0001f)
        assertEquals(0f, Mass.relative(largest shr 30, largest), 0.0001f)
        assertTrue(Mass.relative(largest shr 15, largest) > 0f)
    }

    /**
     * A provisional mark must be distinguishable from a settled one, or a
     * folder still being measured looks like a folder that IS that size.
     */
    /**
     * The reference size must be stable while a folder is still resolving, or
     * every mark on screen rescales on every partial result - a jittering left
     * edge, and a cache invalidation per row per update.
     */
    @Test
    fun `the reference size is stable across a streaming measurement`() {
        // SizeResolver emits a partial total per directory it finishes, so a
        // real walk produces hundreds of them. What matters is that the number
        // of RESCALES is bounded by the doublings crossed, not by the number of
        // updates - otherwise the left edge of the list jitters all the way up.
        val target = 2_297_000_000L
        val partials = (1..400).map { target * it / 400 }
        val references = partials.map { Mass.quantiseLargest(it) }.distinct()
        assertTrue(
            "400 partial results produced ${references.size} different scales; " +
                "quantising is supposed to cap this at the doublings crossed",
            references.size <= 12,
        )
        // And once inside a doubling, the scale does not move at all. These two
        // both sit between 2^31 and 2^32, so a folder growing across them
        // rescales nothing.
        assertEquals(
            Mass.quantiseLargest(2_200_000_000L),
            Mass.quantiseLargest(2_297_000_000L),
        )
    }

    @Test
    fun `quantising never shrinks the reference below the real size`() {
        for (b in listOf(1L, 2L, 3L, 1023L, 1024L, 1025L, 999_999_999L, 1L shl 40)) {
            assertTrue(
                "$b quantised to ${Mass.quantiseLargest(b)}, which is smaller",
                Mass.quantiseLargest(b) >= b,
            )
            // The largest item must still fill the channel after quantising.
            assertTrue(Mass.relative(b, Mass.quantiseLargest(b)) > 0.9f)
        }
    }

    @Test
    fun `a provisional size is visibly weaker than a known one`() {
        assertTrue(Mass.PROVISIONAL_INTENSITY < 0.7f)
        assertTrue(Mass.PROVISIONAL_INTENSITY > 0.25f)
    }
}
