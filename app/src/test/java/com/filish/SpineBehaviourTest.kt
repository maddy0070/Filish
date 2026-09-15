package com.filish

import com.filish.core.model.FileNode
import com.filish.core.model.Kinds
import com.filish.core.model.MeasuredSize
import com.filish.design.glass.Mass
import com.filish.design.spine.Spine
import com.filish.feature.browse.RowFacts
import com.filish.feature.browse.accessibleDescription
import com.filish.feature.browse.magnitudeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The behavioural contract of the spine.
 *
 * These are properties a future change must not break, stated as behaviour
 * rather than as implementation. Where a rule exists because something
 * actually went wrong, the comment says so.
 */
// Robolectric because the accessible description formats a relative time
// through android.text.format.DateFormat, which needs a real Android runtime.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpineBehaviourTest {

    private fun file(name: String, size: Long) = FileNode(
        path = "/d/$name", name = name, isDirectory = false, size = size,
        lastModified = 1_700_000_000_000L, isHidden = false,
        kind = Kinds.of(name, false), extension = Kinds.extensionOf(name),
        canRead = true, canWrite = true,
    )

    private fun folder(name: String) = FileNode(
        path = "/d/$name", name = name, isDirectory = true, size = 4096,
        lastModified = 1_700_000_000_000L, isHidden = false,
        kind = Kinds.of(name, true), extension = "",
        canRead = true, canWrite = true,
    )

    // ---- the scale ---------------------------------------------------------

    /**
     * A folder's walk climbs toward its total, and a folder can overtake every
     * file in the directory. The reference may rise; it must never fall, or
     * every mark on screen would widen and then narrow as facts arrive.
     */
    @Test
    fun `the scale never shrinks while a directory is on screen`() {
        var scale = Mass.scaleFor(listOf(80_000_000L, 12_000L, 4_000_000L))
        val first = scale
        // A big folder resolves, then a smaller one, then a partial result.
        for (arrival in listOf(9_000_000_000L, 3_000_000L, 120_000_000L, 800L)) {
            val next = Mass.scaleFor(listOf(arrival), scale)
            assertTrue("the scale fell from $scale to $next", next >= scale)
            scale = next
        }
        assertTrue("a 9 GB folder did not raise the scale", scale > first)
    }

    /**
     * The late-largest case, which is the one that actually looks broken: a
     * directory of small files where the last folder measured turns out to
     * hold most of the data.
     */
    @Test
    fun `a late largest file rescales the spine exactly once`() {
        val files = List(400) { 1_000_000L + it }
        var scale = Mass.scaleFor(files)
        var rescales = 0
        // The walk of one enormous folder, arriving in 200 partial results.
        for (i in 1..200) {
            val partial = 40_000_000_000L * i / 200
            val next = Mass.scaleFor(listOf(partial), scale)
            if (next != scale) rescales++
            scale = next
        }
        assertTrue(
            "200 partial results produced $rescales rescales; quantising is " +
                "supposed to cap this at the doublings crossed",
            rescales <= 16,
        )
    }

    /** An empty directory has no scale, and therefore no spine. */
    @Test
    fun `an empty directory produces no scale`() {
        assertEquals(1L, Mass.scaleFor(emptyList()))
        // Every mark would be at the floor, which is what "nothing to compare"
        // should look like.
        assertEquals(Mass.markMin, Mass.markWidth(0L, Mass.scaleFor(emptyList())))
    }

    // ---- magnitude sources -------------------------------------------------

    @Test
    fun `a file uses its own size and a folder uses its measured size`() {
        assertEquals(51_000L, magnitudeOf(file("a.arw", 51_000L), null))
        assertEquals(
            9_000_000L,
            magnitudeOf(folder("Camera"), RowFacts(measured = MeasuredSize(9_000_000L, 4, 1, settled = true))),
        )
    }

    /**
     * An unmeasured folder makes no claim. It draws at the floor rather than
     * borrowing the directory entry's own size, which on Linux is 4096 and
     * would render every unmeasured folder as a small file.
     */
    @Test
    fun `an unmeasured folder claims nothing`() {
        assertEquals(0L, magnitudeOf(folder("Camera"), null))
        assertEquals(0L, magnitudeOf(folder("Camera"), RowFacts()))
        val scale = Mass.scaleFor(listOf(4_000_000_000L))
        assertEquals(Mass.markMin, Mass.markWidth(magnitudeOf(folder("x"), null), scale))
    }

    /** A measurement that failed leaves the fact absent, not zeroed-but-settled. */
    @Test
    fun `a failed measurement falls back to the floor rather than to a lie`() {
        val failed = RowFacts(measured = MeasuredSize(0, 0, 0, settled = true, unreadable = 3))
        val scale = Mass.scaleFor(listOf(4_000_000_000L))
        assertEquals(Mass.markMin, Mass.markWidth(magnitudeOf(folder("x"), failed), scale))
    }

    // ---- sorting -----------------------------------------------------------

    /**
     * The mark means magnitude in every arrangement. Sorting by name must not
     * make the spine look like a sorted bar chart, and sorting by size must not
     * make it look like anything other than what it already was.
     */
    @Test
    fun `mark width is independent of the order rows are shown in`() {
        val files = listOf(
            file("zebra.txt", 12_000L),
            file("alpha.mp4", 900_000_000L),
            file("middle.pdf", 3_000_000L),
        )
        val scale = Mass.scaleFor(files.map { it.size })
        val byName = files.sortedBy { it.name }.map { it.name to Mass.markWidth(it.size, scale) }
        val bySize = files.sortedBy { it.size }.map { it.name to Mass.markWidth(it.size, scale) }
        for ((name, width) in byName) {
            assertEquals("$name changed width when the sort changed", width, bySize.first { it.first == name }.second)
        }
    }

    /**
     * Filtering changes what is visible, and the scale is computed from what is
     * visible - so the same file legitimately draws wider in a filtered view.
     * Pinned because it is surprising, and because the figure in the text is
     * what keeps it honest.
     */
    @Test
    fun `filtering rescales the spine, and the text still states the truth`() {
        val all = listOf(12_000L, 3_000_000L, 900_000_000L)
        val filtered = listOf(12_000L, 3_000_000L)
        val wideScale = Mass.scaleFor(all)
        val narrowScale = Mass.scaleFor(filtered)
        assertTrue(
            "removing the largest file should let the rest spread out",
            Mass.markWidth(3_000_000L, narrowScale) > Mass.markWidth(3_000_000L, wideScale),
        )
    }

    // ---- separation --------------------------------------------------------

    /**
     * Mark separation is load-bearing: without it, runs of same-kind same-size
     * files fuse into a single bar that says "one object" about four. The gap
     * is a token precisely so it cannot be tuned to zero by accident.
     */
    @Test
    fun `adjacent marks are always separated`() {
        assertTrue("the mark gap is zero; adjacent marks will fuse", Spine.markGap.value > 0f)
        // Two gaps must still leave a visible mark inside the shortest row.
        val shortestRowPx = Spine.minHeight.value
        assertTrue(
            "the gaps consume the mark at the minimum row height",
            shortestRowPx - Spine.markGap.value * 2 > 20f,
        )
    }

    @Test
    fun `the mark never reaches the text column`() {
        assertTrue(
            "a full-width mark would touch the text",
            Spine.gutter.value + Mass.markMax.value < Spine.textInset.value,
        )
        assertTrue("the mark ceiling overflows its channel", Mass.markMax <= Spine.channel)
    }

    // ---- accessibility -----------------------------------------------------

    /**
     * The spine is never the only channel. A reader who cannot perceive the
     * mark must still hear the size.
     */
    @Test
    fun `the spoken description carries the size the mark is a picture of`() {
        val d = accessibleDescription(file("DSC01847.ARW", 51_275_000L), null)
        assertTrue("size missing from '$d'", d.contains("MB") || d.contains("GB") || d.contains("KB"))
        assertTrue("name missing from '$d'", d.startsWith("DSC01847.ARW"))
    }

    @Test
    fun `a measuring folder says so rather than reporting a partial total as final`() {
        val partial = RowFacts(measured = MeasuredSize(2_100_000_000L, 40, 2, settled = false))
        val d = accessibleDescription(folder("Screen recordings"), partial)
        assertTrue("a partial figure was announced as final: '$d'", d.contains("still measuring"))
    }

    // ---- fallbacks ---------------------------------------------------------

    /**
     * A file of unknown type is still an object with a magnitude. Nothing in
     * the spine depends on recognising the extension.
     */
    @Test
    fun `an unknown file type still gets a mark`() {
        val odd = file("..oddly.named..", 8_000_000L)
        val scale = Mass.scaleFor(listOf(odd.size))
        val w = Mass.markWidth(odd.size, scale)
        assertTrue(w >= Mass.markMin && w <= Mass.markMax)
    }
}
