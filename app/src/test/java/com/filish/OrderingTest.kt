package com.filish

import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.FilterSpec
import com.filish.core.model.SortDirection
import com.filish.core.model.SortKey
import com.filish.core.model.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderingTest {

    private fun node(
        name: String,
        size: Long = 0,
        dir: Boolean = false,
        modified: Long = 1_000,
        hidden: Boolean = false,
    ) = FileNode(
        path = "/x/$name", name = name, isDirectory = dir, size = size, lastModified = modified,
        isHidden = hidden, kind = com.filish.core.model.Kinds.of(name, dir),
        extension = com.filish.core.model.Kinds.extensionOf(name), canRead = true, canWrite = true,
    )

    @Test
    fun `the first tap on a key gives the answer people are looking for`() {
        // Nobody sorts by size to find the smallest file.
        assertEquals(
            SortDirection.Descending,
            SortSpec(SortKey.Name).toggled(SortKey.Size).direction,
        )
        assertEquals(
            SortDirection.Descending,
            SortSpec(SortKey.Name).toggled(SortKey.Modified).direction,
        )
        // Names are the exception: A to Z is what people mean.
        assertEquals(
            SortDirection.Ascending,
            SortSpec(SortKey.Size).toggled(SortKey.Name).direction,
        )
    }

    @Test
    fun `tapping the same key again reverses it`() {
        val first = SortSpec(SortKey.Name, SortDirection.Ascending)
        assertEquals(SortDirection.Descending, first.toggled(SortKey.Name).direction)
        assertEquals(
            SortDirection.Ascending,
            first.toggled(SortKey.Name).toggled(SortKey.Name).direction,
        )
    }

    @Test
    fun `the current ordering can always be stated in words`() {
        // The control strip shows this instead of hiding ordering behind an
        // icon, so every combination has to produce a readable phrase.
        for (key in SortKey.entries) {
            for (direction in SortDirection.entries) {
                val statement = SortSpec(key, direction).statement
                assertTrue("no statement for $key $direction", statement.isNotBlank())
            }
        }
        assertEquals("largest first", SortSpec(SortKey.Size, SortDirection.Descending).statement)
        assertEquals("A to Z", SortSpec(SortKey.Name, SortDirection.Ascending).statement)
    }

    @Test
    fun `hidden files are excluded unless asked for`() {
        val hidden = node(".thumbnails", dir = true, hidden = true)
        assertFalse(FilterSpec().matches(hidden))
        assertTrue(FilterSpec(showHidden = true).matches(hidden))
    }

    @Test
    fun `a size filter never hides the folders that lead to matches`() {
        // Excluding directories from a size filter would hide the route to the
        // very files the filter is looking for.
        val folder = node("Videos", dir = true)
        assertTrue(FilterSpec(minSize = 1_000_000).matches(folder))

        val small = node("tiny.txt", size = 10)
        assertFalse(FilterSpec(minSize = 1_000_000).matches(small))
    }

    @Test
    fun `an active filter is always countable so it can never be invisible`() {
        val filter = FilterSpec(kinds = setOf(FileKind.Video), minSize = 1_000)
        assertTrue(filter.isActive)
        assertEquals(2, filter.activeCount)
        // Showing hidden files is a visibility choice, not a filter, and must
        // not inflate the count on the filter control.
        assertFalse(FilterSpec(showHidden = true).isActive)
    }

    @Test
    fun `name matching is case-insensitive and partial`() {
        val n = node("Holiday Photo.jpg")
        assertTrue(FilterSpec(nameContains = "holiday").matches(n))
        assertTrue(FilterSpec(nameContains = "PHOTO").matches(n))
        assertFalse(FilterSpec(nameContains = "video").matches(n))
    }
}
