package com.filish

import android.net.Uri
import com.filish.core.fs.ops.DeleteController
import com.filish.core.fs.ops.DeleteEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The contract that the delete bug violated.
 *
 * A user deleted several gigabytes of RAW files, saw the "recoverable"
 * confirmation, pressed delete, and nothing happened at all - no change, no
 * error. The cause was a chain of three defects, and these tests pin the
 * properties that each one broke, so none of them can return quietly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteContractTest {

    private val context = androidx.test.core.app.ApplicationProvider
        .getApplicationContext<android.content.Context>()
    private val engine = DeleteEngine(context, com.filish.core.fs.MediaStoreIndex(context))

    private fun uris(n: Int): List<Uri> =
        (1..n).map { Uri.parse("content://media/external/file/$it") }

    // ---- defect 3: no batching, so large selections always threw ----------

    @Test
    fun `a large selection is split into batches`() {
        // Every URI in a trash request is parcelled into a PendingIntent and
        // crosses a Binder transaction. One request for hundreds of files is
        // how a big delete threw before it did anything.
        val batches = engine.batchesOf(uris(450))
        assertTrue("not batched", batches.size > 1)
        assertTrue("a batch is too large", batches.all { it.size <= 100 })
        assertEquals("files were lost in batching", 450, batches.sumOf { it.size })
    }

    @Test
    fun `batching preserves order and loses nothing`() {
        val input = uris(250)
        val flattened = engine.batchesOf(input).flatten()
        assertEquals(input, flattened)
    }

    @Test
    fun `an empty selection produces no batches`() {
        assertTrue(engine.batchesOf(emptyList()).isEmpty())
    }

    @Test
    fun `a small selection stays a single batch`() {
        assertEquals(1, engine.batchesOf(uris(12)).size)
    }

    // ---- defect 1: the failure was swallowed -----------------------------

    @Test
    fun `a consent request that cannot be built explains itself`() {
        // The old signature returned IntentSender? and used getOrNull(), so a
        // caller could not distinguish "unsupported" from "it threw". Every
        // outcome now carries a reason a person can read.
        val result = engine.consentRequest(emptyList())
        assertTrue(result is DeleteEngine.ConsentRequest.Impossible)
        val reason = (result as DeleteEngine.ConsentRequest.Impossible).reason
        assertTrue("reason is empty", reason.isNotBlank())
    }

    @Test
    fun `every consent outcome is one of the two stated cases`() {
        // There is deliberately no null and no third state to forget to handle.
        val outcomes = listOf(
            engine.consentRequest(emptyList()),
            engine.consentRequest(uris(3)),
        )
        assertTrue(
            outcomes.all {
                it is DeleteEngine.ConsentRequest.Ready ||
                    it is DeleteEngine.ConsentRequest.Impossible
            },
        )
    }

    // ---- the result type must never be able to report nothing -------------

    @Test
    fun `a result that achieved nothing is not mistaken for success`() {
        val blocked = DeleteController.Result(
            trashed = 0, deleted = 0, failed = emptyList(), bytesFreed = 0,
            expiresAtMillis = 0, blockedReason = "Android denied permission.",
        )
        assertTrue(blocked.didNothing)
        assertFalse("a blocked delete reported itself as clean", blocked.isClean)
    }

    @Test
    fun `a cancelled result is not clean`() {
        val cancelled = DeleteController.Result(
            trashed = 4, deleted = 0, failed = emptyList(), bytesFreed = 100,
            expiresAtMillis = 0, blockedReason = null, cancelled = true,
        )
        assertFalse(cancelled.isClean)
    }

    @Test
    fun `a partial failure is not clean and keeps its reasons`() {
        val partial = DeleteController.Result(
            trashed = 8, deleted = 0,
            failed = listOf(DeleteEngine.FailedDeletion("/x/a.arw", "In use by another app")),
            bytesFreed = 0, expiresAtMillis = 0, blockedReason = null,
        )
        assertFalse(partial.isClean)
        assertEquals(1, partial.failed.size)
        assertTrue(partial.failed.first().reason.isNotBlank())
    }

    @Test
    fun `a genuinely successful result is clean`() {
        val ok = DeleteController.Result(
            trashed = 12, deleted = 0, failed = emptyList(), bytesFreed = 5_000_000_000,
            expiresAtMillis = System.currentTimeMillis(), blockedReason = null,
        )
        assertTrue(ok.isClean)
        assertFalse(ok.didNothing)
        assertEquals(12, ok.total)
    }

    // ---- the rows must be able to leave immediately ----------------------

    @Test
    fun `a successful delete names the paths the list should drop`() {
        // The interface stayed unchanged partly because nothing told it which
        // rows had gone; it waited for a directory re-read.
        val result = DeleteController.Result(
            trashed = 2, deleted = 0, failed = emptyList(), bytesFreed = 10,
            expiresAtMillis = 0, blockedReason = null,
            trashedPaths = listOf(
                "/storage/emulated/0/DCIM/a.arw",
                "/storage/emulated/0/DCIM/b.arw",
            ),
        )
        assertEquals(2, result.trashedPaths.size)
    }
}
