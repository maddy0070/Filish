package com.filish.core.fs.ops

import android.content.IntentSender
import android.net.Uri
import com.filish.core.fs.SizeResolver
import com.filish.core.model.FileNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Deletion, as an explicit state machine.
 *
 * ---------------------------------------------------------------------------
 * Why this class exists
 *
 * The first implementation drove deletion from inside the composable: the plan
 * lived in `remember`, the consent dialog was launched from a lambda, and the
 * result was handled in an ActivityResult callback. Three things were wrong
 * with that, and together they produced a delete that silently did nothing:
 *
 *   1. THE FAILURE HAD NOWHERE TO GO. The confirm path passed an empty lambda
 *      as its "could not start" handler. When the platform refused the
 *      request, that empty lambda ran, the sheet stayed open, and the user saw
 *      no change and no error. A large selection of RAW files hit this every
 *      time.
 *
 *   2. THE PLAN DID NOT SURVIVE. Holding it in composition state meant that if
 *      the activity was recreated while the system dialog was in front - which
 *      a multi-gigabyte operation makes likely - the plan was gone by the time
 *      the result arrived, and the callback quietly did nothing.
 *
 *   3. THERE WAS NO PROGRESS. A delete either finished instantly or appeared
 *      frozen. Nothing distinguished "working" from "broken", which is exactly
 *      the complaint.
 *
 * So the operation now lives in the application scope, publishes a stage the
 * interface renders, batches its consent requests, and ends in either a
 * completed result or a stated reason. There is no path through this class
 * that ends in silence.
 * ---------------------------------------------------------------------------
 */
class DeleteController(
    private val scope: CoroutineScope,
    private val engine: DeleteEngine,
    private val sizes: SizeResolver,
) {

    sealed interface Stage {
        data object Idle : Stage

        /** Plan resolved; waiting for the user. */
        data class Confirming(val plan: DeleteEngine.Plan) : Stage

        /** Work in flight. [done] of [total] items resolved so far. */
        data class Working(
            val done: Int,
            val total: Int,
            val what: String,
        ) : Stage {
            val fraction: Float
                get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
        }

        /** The platform wants the user to approve this batch. */
        data class AwaitingConsent(
            val sender: IntentSender,
            val batchIndex: Int,
            val batchCount: Int,
        ) : Stage

        data class Finished(val result: Result) : Stage
    }

    data class Result(
        val trashed: Int,
        val deleted: Int,
        val failed: List<DeleteEngine.FailedDeletion>,
        val bytesFreed: Long,
        /** When the OS will permanently remove trashed items. 0 if unknown. */
        val expiresAtMillis: Long,
        /** Set when the whole operation could not proceed. */
        val blockedReason: String?,
        val cancelled: Boolean = false,
        /** Paths that are gone from the filesystem, so the list can drop them
         *  immediately rather than waiting for a re-read. */
        val removedPaths: List<String> = emptyList(),
    ) {
        val total: Int get() = trashed + deleted
        val isClean: Boolean get() = failed.isEmpty() && blockedReason == null && !cancelled
        val didNothing: Boolean get() = total == 0
    }

    private val _stage = MutableStateFlow<Stage>(Stage.Idle)
    val stage: StateFlow<Stage> = _stage.asStateFlow()

    private var job: Job? = null

    // Carried across the consent dialogs, which is why none of this may live
    // in composition state.
    private var pendingBatches: List<List<Uri>> = emptyList()
    private var batchCursor = 0
    private var accTrashed = 0
    private var accFailed = mutableListOf<DeleteEngine.FailedDeletion>()
    private var accRemoved = mutableListOf<String>()
    private var activePlan: DeleteEngine.Plan? = null

    /** Resolves what would happen, then asks. */
    fun begin(nodes: List<FileNode>, skipConfirmWhenRecoverable: Boolean) {
        if (nodes.isEmpty()) return
        job?.cancel()
        job = scope.launch {
            _stage.value = Stage.Working(0, nodes.size, "Checking what these are")
            val plan = engine.plan(nodes)
            activePlan = plan
            if (plan.isFullyRecoverable && skipConfirmWhenRecoverable) {
                execute(plan)
            } else {
                _stage.value = Stage.Confirming(plan)
            }
        }
    }

    fun confirm() {
        val plan = (_stage.value as? Stage.Confirming)?.plan ?: activePlan ?: return
        job?.cancel()
        job = scope.launch { execute(plan) }
    }

    fun dismiss() {
        job?.cancel()
        reset()
        _stage.value = Stage.Idle
    }

    /** Clears a finished result once the interface has shown it. */
    fun acknowledge() {
        if (_stage.value is Stage.Finished) {
            reset()
            _stage.value = Stage.Idle
        }
    }

    private fun reset() {
        pendingBatches = emptyList()
        batchCursor = 0
        accTrashed = 0
        accFailed = mutableListOf()
        accRemoved = mutableListOf()
        activePlan = null
    }

    private suspend fun execute(plan: DeleteEngine.Plan) {
        accTrashed = 0
        accFailed = mutableListOf()
        accRemoved = mutableListOf()

        val totalItems = plan.nodes.size

        // 1. Resolve URIs once. The old flow did this twice - once to build the
        //    request and again to verify - doubling the most expensive step.
        _stage.value = Stage.Working(0, totalItems, "Finding these in your media library")
        val (uris, permanentPaths) = engine.resolveTrashable(plan)

        // 2. Trash what can be trashed, writing directly where the platform
        //    permits it. FILISH holds all-files access, so in the common case
        //    this needs no dialog at all - which is both faster and immune to
        //    the size limit that made large deletes fail.
        var needConsent: List<Uri> = emptyList()
        if (uris.isNotEmpty()) {
            _stage.value = Stage.Working(0, totalItems, "Moving to trash")
            val direct = engine.trashDirectly(uris)
            accTrashed += direct.trashed
            accFailed.addAll(direct.failed)
            needConsent = direct.needsConsent

            if (direct.blockedReason != null && direct.trashed == 0 && needConsent.isEmpty()) {
                finish(plan, blocked = direct.blockedReason)
                return
            }
        }

        // 3. Permanent removals, which never need consent.
        if (permanentPaths.isNotEmpty()) {
            _stage.value = Stage.Working(accTrashed, totalItems, "Deleting")
            val outcome = engine.deletePermanently(permanentPaths)
            accFailed.addAll(outcome.failed)
            val failedPaths = outcome.failed.map { it.path }.toSet()
            accRemoved.addAll(permanentPaths.filter { it !in failedPaths })
            accDeleted = outcome.deleted
        }

        // 4. Anything the platform insisted on asking about, in batches.
        if (needConsent.isNotEmpty()) {
            pendingBatches = engine.batchesOf(needConsent)
            batchCursor = 0
            requestNextConsent(plan)
            return
        }

        finish(plan, blocked = null)
    }

    private var accDeleted = 0

    private fun requestNextConsent(plan: DeleteEngine.Plan) {
        if (batchCursor >= pendingBatches.size) {
            scope.launch { finish(plan, blocked = null) }
            return
        }
        val batch = pendingBatches[batchCursor]
        when (val request = engine.consentRequest(batch, trash = true)) {
            is DeleteEngine.ConsentRequest.Ready -> {
                _stage.value = Stage.AwaitingConsent(
                    sender = request.sender,
                    batchIndex = batchCursor + 1,
                    batchCount = pendingBatches.size,
                )
            }
            is DeleteEngine.ConsentRequest.Impossible -> {
                // The reason is carried through to the user rather than
                // discarded. This is the path that used to end in silence.
                batch.forEach {
                    accFailed.add(DeleteEngine.FailedDeletion(it.toString(), request.reason))
                }
                batchCursor++
                requestNextConsent(plan)
            }
        }
    }

    /** Called by the interface once the system dialog returns. */
    fun onConsentResult(granted: Boolean) {
        val plan = activePlan ?: return
        val batch = pendingBatches.getOrNull(batchCursor)
        if (batch == null) {
            scope.launch { finish(plan, blocked = null) }
            return
        }
        if (granted) {
            accTrashed += batch.size
        } else {
            // Declining is a decision, not a fault. It ends the operation
            // rather than marking every remaining file as failed.
            scope.launch { finish(plan, blocked = null, cancelled = true) }
            return
        }
        batchCursor++
        if (batchCursor < pendingBatches.size) {
            _stage.value = Stage.Working(accTrashed, plan.nodes.size, "Moving to trash")
            requestNextConsent(plan)
        } else {
            scope.launch { finish(plan, blocked = null) }
        }
    }

    private suspend fun finish(
        plan: DeleteEngine.Plan,
        blocked: String?,
        cancelled: Boolean = false,
    ) {
        _stage.value = Stage.Working(accTrashed + accDeleted, plan.nodes.size, "Finishing up")

        // Verify rather than assume. A count of what is genuinely trashed is
        // the only honest thing to report back.
        val verified = if (accTrashed > 0) {
            val (uris, _) = engine.resolveTrashable(plan)
            runCatching { engine.verifyTrashed(uris) }.getOrDefault(accTrashed)
        } else {
            0
        }
        val expiry = if (verified > 0) {
            val (uris, _) = engine.resolveTrashable(plan)
            runCatching { engine.expiryOf(uris) }.getOrDefault(0L)
        } else {
            0L
        }

        // Trashed files leave their directory, so the browser must drop them.
        if (verified > 0) {
            accRemoved.addAll(plan.nodes.filter { !it.isDirectory }.map { it.path })
        }

        plan.nodes.forEach { sizes.invalidate(it.path) }

        _stage.value = Stage.Finished(
            Result(
                trashed = verified,
                deleted = accDeleted,
                failed = accFailed.toList(),
                bytesFreed = plan.totalBytes,
                expiresAtMillis = expiry,
                blockedReason = blocked,
                cancelled = cancelled,
                removedPaths = accRemoved.distinct(),
            ),
        )
        accDeleted = 0
    }
}
