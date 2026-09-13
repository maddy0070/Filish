package com.filish

import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.MeasuredSize
import com.filish.feature.browse.Refinements
import com.filish.feature.browse.Selection
import com.filish.feature.browse.SelectionRefinement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The selection ledger's arithmetic.
 *
 * This is FILISH's headline feature, so the two properties that make it
 * trustworthy are pinned down: a selection of plain files is exact and
 * instant, and a selection containing folders is never reported as final
 * until the walk that measures it has actually finished.
 */
class SelectionTest {

    private fun file(name: String, size: Long, modified: Long = 1_000L) = FileNode(
        path = "/storage/emulated/0/$name", name = name, isDirectory = false, size = size,
        lastModified = modified, isHidden = false,
        kind = com.filish.core.model.Kinds.of(name, false),
        extension = com.filish.core.model.Kinds.extensionOf(name),
        canRead = true, canWrite = true,
    )

    private fun folder(name: String) = FileNode(
        path = "/storage/emulated/0/$name", name = name, isDirectory = true, size = 0,
        lastModified = 1_000L, isHidden = false, kind = FileKind.Folder, extension = "",
        canRead = true, canWrite = true,
    )

    @Test
    fun `a selection of files alone is exact and needs no walk`() {
        val selection = Selection().add(listOf(file("a.jpg", 100), file("b.mp4", 250)))
        assertFalse(selection.needsMeasurement)
        assertTrue(selection.isSettled)
        assertEquals(350L, selection.displayBytes)
    }

    @Test
    fun `a selection containing a folder is not settled until measured`() {
        val selection = Selection().add(listOf(file("a.jpg", 100), folder("Docs")))
        assertTrue(selection.needsMeasurement)
        // The critical property: an unmeasured total must never claim to be
        // the answer.
        assertFalse(selection.isSettled)

        val measuring = selection.withMeasurement(MeasuredSize(4_000, 12, 1, settled = false))
        assertFalse(measuring.isSettled)
        assertEquals(4_000L, measuring.displayBytes)

        val done = selection.withMeasurement(MeasuredSize(9_000, 30, 2, settled = true))
        assertTrue(done.isSettled)
        assertEquals(9_000L, done.displayBytes)
    }

    @Test
    fun `the composition reads as files and folders, never as a bare count`() {
        val selection = Selection().add(
            listOf(file("a.jpg", 1), file("b.jpg", 1), folder("X")),
        )
        assertEquals("2 files, 1 folder", selection.describe())
        assertEquals(2, selection.fileCount)
        assertEquals(1, selection.folderCount)
    }

    @Test
    fun `changing the selection discards the previous measurement`() {
        // A stale total attached to a different set of files is worse than no
        // total at all.
        val base = Selection().add(listOf(folder("X")))
            .withMeasurement(MeasuredSize(5_000, 10, 1, settled = true))
        assertTrue(base.isSettled)

        val extended = base.toggle(folder("Y"))
        assertFalse("stale measurement survived a selection change", extended.isSettled)
    }

    @Test
    fun `toggling removes as well as adds`() {
        val a = file("a.jpg", 10)
        val selection = Selection().toggle(a)
        assertTrue(selection.contains(a.path))
        assertFalse(selection.toggle(a).contains(a.path))
    }

    @Test
    fun `inverting produces exactly the complement`() {
        val items = listOf(file("a.jpg", 1), file("b.jpg", 1), file("c.jpg", 1))
        val selection = Selection().add(listOf(items[0]))
        val inverted = selection.inverted(items)
        assertEquals(setOf(items[1].path, items[2].path), inverted.paths)
    }

    @Test
    fun `unreadable entries are reported rather than silently dropped`() {
        val selection = Selection().add(listOf(folder("X")))
            .withMeasurement(MeasuredSize(100, 3, 1, settled = true, unreadable = 4))
        assertEquals("4 items could not be read", selection.measurementNote())
    }

    @Test
    fun `refinements are only offered when they would do something`() {
        // A folder of nothing but images should not offer "select images" -
        // an action that changes nothing is worse than an absent one.
        val allImages = listOf(file("a.jpg", 1), file("b.jpg", 1), file("c.jpg", 1))
        val offered = Refinements.forListing(allImages)
        assertFalse(
            offered.any { it is SelectionRefinement.ByKind && it.kind == FileKind.Image },
        )

        val mixed = allImages + listOf(file("d.mp4", 1), file("e.mp4", 1))
        val mixedOffered = Refinements.forListing(mixed)
        assertTrue(
            mixedOffered.any { it is SelectionRefinement.ByKind && it.kind == FileKind.Image },
        )
    }

    @Test
    fun `file and folder refinements appear only in a mixed listing`() {
        val onlyFiles = listOf(file("a.jpg", 1), file("b.jpg", 1))
        assertFalse(Refinements.forListing(onlyFiles).contains(SelectionRefinement.Folders))

        val mixed = onlyFiles + folder("X")
        assertTrue(Refinements.forListing(mixed).contains(SelectionRefinement.Folders))
    }

    @Test
    fun `a size refinement selects a real subset, never everything`() {
        val items = listOf(
            file("a.jpg", 10), file("b.jpg", 20), file("c.jpg", 30), file("d.jpg", 4000),
        )
        val refinement = Refinements.forListing(items)
            .filterIsInstance<SelectionRefinement.LargerThan>().firstOrNull()
        assertTrue("no size refinement offered", refinement != null)
        val applied = Refinements.apply(refinement!!, items, Selection())
        assertTrue(applied.count in 1 until items.size)
    }

    @Test
    fun `an empty listing offers no refinements at all`() {
        assertTrue(Refinements.forListing(emptyList()).isEmpty())
    }
}
