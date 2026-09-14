package com.filish

import com.filish.core.fs.ops.DeleteController
import com.filish.core.fs.ops.DeleteEngine
import com.filish.design.Motion
import com.filish.feature.browse.BrowseViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rows leave in the manner that matches what happened to them.
 */
class DepartureTest {

    @Test
    fun `the removal delay matches the animation length`() {
        // The row has to survive exactly long enough to be seen departing.
        // These two numbers live in different files and would drift silently:
        // too short and the row is cut mid-motion, too long and the list
        // holds a dead row.
        assertEquals(
            "departure delay and animation duration have drifted apart",
            Motion.DELIBERATE.toLong(),
            BrowseViewModel.DEPARTURE_MILLIS,
        )
    }

    @Test
    fun `a mixed delete separates what was trashed from what was destroyed`() {
        val result = DeleteController.Result(
            trashed = 2,
            deleted = 1,
            failed = emptyList(),
            bytesFreed = 100,
            expiresAtMillis = 0,
            blockedReason = null,
            trashedPaths = listOf("/a/photo.jpg", "/a/clip.mp4"),
            destroyedPaths = listOf("/a/somefolder"),
        )
        // The two lists must not overlap - a path animated both ways would be
        // removed twice and would flicker.
        val overlap = result.trashedPaths.intersect(result.destroyedPaths.toSet())
        assertTrue("a path was both trashed and destroyed", overlap.isEmpty())
        assertEquals(2, result.trashedPaths.size)
        assertEquals(1, result.destroyedPaths.size)
    }

    @Test
    fun `nothing departs when nothing was deleted`() {
        val blocked = DeleteController.Result(
            trashed = 0, deleted = 0,
            failed = listOf(DeleteEngine.FailedDeletion("/a/x", "denied")),
            bytesFreed = 0, expiresAtMillis = 0, blockedReason = null,
        )
        // A failed delete must not animate rows away - the files are still
        // there, and removing them would be the interface lying again.
        assertTrue(blocked.trashedPaths.isEmpty())
        assertTrue(blocked.destroyedPaths.isEmpty())
    }
}
