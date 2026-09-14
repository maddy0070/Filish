package com.filish.core.fs.ops

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.filish.core.fs.MediaStoreIndex
import com.filish.core.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Deletion.
 *
 * ---------------------------------------------------------------------------
 * WHAT ANDROID ACTUALLY PERMITS - and what it does not
 * ---------------------------------------------------------------------------
 *
 * The requirement FILISH was built to meet is "deleting should put the file
 * in the system's recycle bin, like Google Files does, with the usual 30-day
 * expiry". Here is the honest position after establishing what the platform
 * exposes.
 *
 * There is no universal, device-wide Trash that any application may write
 * into. What exists is MediaStore's trash: from Android 11 (API 30),
 * [MediaStore.createTrashRequest] sets IS_TRASHED on rows in MediaProvider's
 * database. Those items disappear from galleries and file listings, remain
 * recoverable, and are permanently removed by MediaProvider itself once
 * DATE_EXPIRES passes. This is a real, OS-managed recycle bin - the same
 * mechanism the Google Photos and Files bins are built on - and FILISH uses
 * it as its primary deletion route.
 *
 * What FILISH cannot do, and does not claim to do:
 *
 *   - It cannot place files into Google Files' own Trash. That bin is that
 *     application's private storage plus its own bookkeeping. No API exposes
 *     it. Any app claiming otherwise is describing MediaStore's trash and
 *     calling it Google's.
 *   - It cannot trash anything on Android 10 or older. createTrashRequest
 *     does not exist before API 30. On those releases deletion is permanent,
 *     and FILISH says so, in those words, before the user confirms.
 *   - It cannot trash a document reached through the Storage Access
 *     Framework. DocumentsContract has deleteDocument and no trash
 *     equivalent; a provider's own bin, if it has one, is not addressable.
 *   - It cannot promise "30 days". The retention period is MediaProvider's
 *     policy and has varied across releases and vendors. So FILISH reads
 *     DATE_EXPIRES back from the item it just trashed and reports the actual
 *     date. If the platform says the 14th, the user is told the 14th.
 *
 * Where FILISH goes further than it strictly has to: a file that is not in
 * MediaStore has no row to trash - which would ordinarily force a permanent
 * delete on, say, a .zip in a user-made folder. Before giving up, FILISH asks
 * MediaProvider to index the file ([MediaStoreIndex.index]); if it accepts,
 * the file acquires an identity and becomes trashable. This meaningfully
 * widens recoverable deletion beyond photos and videos. It is attempted, then
 * *verified* by reading IS_TRASHED back - never assumed.
 *
 * The route taken is resolved before the user confirms, and it is stated in
 * the confirmation. A delete that says "recoverable until 12 October" and a
 * delete that says "permanent, cannot be undone" are different decisions, and
 * the user makes them with the right information.
 * ---------------------------------------------------------------------------
 */
class DeleteEngine(
    private val context: Context,
    private val index: MediaStoreIndex,
) {

    /** How a given set of items will actually be removed. */
    enum class Route {
        /** MediaStore trash. Recoverable until the OS expires it. */
        SystemTrash,

        /** Not indexed yet; FILISH will index then trash. Recoverable if
         *  MediaProvider accepts the file, permanent if it declines. */
        IndexThenTrash,

        /** Gone immediately. No recovery. */
        Permanent,
    }

    /** The platform-level reason a set of items cannot be trashed. */
    enum class PermanentReason {
        None,
        /** createTrashRequest does not exist before Android 11. */
        PlatformTooOld,
        /** MediaProvider does not index this location (app-private dirs,
         *  paths outside shared storage). */
        OutsideMediaStore,
        /** A directory. MediaStore rows are files; a folder has no row, and
         *  trashing its contents individually would destroy the folder
         *  structure the user expects to get back. */
        IsDirectory,
    }

    data class Plan(
        val nodes: List<FileNode>,
        val route: Route,
        val permanentReason: PermanentReason,
        val trashableUris: List<Uri>,
        /** Files with no MediaStore row yet. FILISH will try to give them one
         *  so they can be trashed rather than destroyed. */
        val unindexedPaths: List<String>,
        val permanentPaths: List<String>,
        val totalBytes: Long,
        val fileCount: Int,
        val folderCount: Int,
    ) {
        val isMixed: Boolean get() = trashableUris.isNotEmpty() && permanentPaths.isNotEmpty()
        val isFullyRecoverable: Boolean get() = permanentPaths.isEmpty() && trashableUris.isNotEmpty()

        /**
         * The sentence shown to the user before they commit. Written to be
         * accurate first and reassuring second - never the other way round.
         */
        val consequence: String
            get() = when {
                isFullyRecoverable -> "Recoverable from your device's trash"
                isMixed -> "${permanentPaths.size} of these cannot be recovered"
                permanentReason == PermanentReason.PlatformTooOld ->
                    "Permanent - this version of Android has no system trash"
                permanentReason == PermanentReason.IsDirectory ->
                    "Permanent - folders cannot be placed in the system trash"
                permanentReason == PermanentReason.OutsideMediaStore ->
                    "Permanent - this location is not covered by the system trash"
                else -> "Permanent - this cannot be undone"
            }
    }

    private val trashSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private companion object {
        /**
         * URIs per consent dialog.
         *
         * Chosen well below where a parcelled PendingIntent starts risking the
         * Binder transaction limit. Several small dialogs that work beat one
         * large dialog that throws.
         */
        const val CONSENT_BATCH = 100
    }

    /**
     * Works out what will happen, before anything happens.
     *
     * Runs off the main thread and touches the filesystem, so it is a
     * suspending call and its result is what the confirmation surface renders.
     */
    suspend fun plan(nodes: List<FileNode>): Plan = withContext(Dispatchers.IO) {
        val files = nodes.filter { !it.isDirectory }
        val folders = nodes.filter { it.isDirectory }
        val totalBytes = files.sumOf { it.size }

        if (!trashSupported) {
            return@withContext Plan(
                nodes = nodes,
                route = Route.Permanent,
                permanentReason = PermanentReason.PlatformTooOld,
                trashableUris = emptyList(),
                unindexedPaths = emptyList(),
                permanentPaths = nodes.map { it.path },
                totalBytes = totalBytes,
                fileCount = files.size,
                folderCount = folders.size,
            )
        }

        val known = index.urisFor(files.map { it.path })
        val trashable = ArrayList<Uri>(known.size)
        val unindexed = ArrayList<String>()

        for (f in files) {
            val uri = known[f.path]
            if (uri != null) trashable.add(uri) else unindexed.add(f.path)
        }

        // Folders always go permanently: MediaStore has no representation for
        // a directory, and shredding a folder into individually-trashed files
        // would "restore" as a flat pile in the wrong place.
        val permanent = ArrayList<String>(folders.map { it.path })

        val route = when {
            trashable.isEmpty() && unindexed.isEmpty() -> Route.Permanent
            unindexed.isNotEmpty() -> Route.IndexThenTrash
            else -> Route.SystemTrash
        }

        val reason = when {
            folders.isNotEmpty() && trashable.isEmpty() && unindexed.isEmpty() ->
                PermanentReason.IsDirectory
            route == Route.Permanent -> PermanentReason.OutsideMediaStore
            else -> PermanentReason.None
        }

        Plan(
            nodes = nodes,
            route = route,
            permanentReason = reason,
            trashableUris = trashable,
            unindexedPaths = if (route == Route.IndexThenTrash) unindexed else emptyList(),
            permanentPaths = permanent + if (route == Route.IndexThenTrash) emptyList() else unindexed,
            totalBytes = totalBytes,
            fileCount = files.size,
            folderCount = folders.size,
        )
    }

    /**
     * Resolves the URIs to hand to [MediaStore.createTrashRequest], indexing
     * anything that needs it first. Returns the paths that could not be made
     * trashable at all.
     */
    suspend fun resolveTrashable(plan: Plan): Pair<List<Uri>, List<String>> =
        withContext(Dispatchers.IO) {
            val uris = ArrayList(plan.trashableUris)
            val stillPermanent = ArrayList<String>()

            if (plan.route == Route.IndexThenTrash && plan.unindexedPaths.isNotEmpty()) {
                // Re-resolve rather than trusting the plan's snapshot: time has
                // passed and MediaProvider may have indexed things since.
                val fresh = index.urisFor(plan.unindexedPaths)
                val stillMissing = plan.unindexedPaths.filter { it !in fresh }
                fresh.values.forEach { if (it !in uris) uris.add(it) }

                // One batched scan, not one four-second scan per file.
                val scanned = index.indexAll(stillMissing)
                for (path in stillMissing) {
                    val uri = scanned[path]
                    if (uri != null && uri.scheme == "content") {
                        if (uri !in uris) uris.add(uri)
                    } else {
                        stillPermanent.add(path)
                    }
                }
            }

            uris.distinct() to (plan.permanentPaths + stillPermanent).distinct()
        }

    /**
     * Builds the system's trash consent request.
     *
     * The user confirming inside this dialog is the platform's requirement,
     * not FILISH's choice - MediaProvider will not trash another app's files
     * without it. FILISH therefore does not stack its own confirmation on top
     * of it for the recoverable case: two dialogs to delete one photo is a
     * tax, not a safeguard.
     */
    fun consentRequest(uris: List<Uri>, trash: Boolean = true): ConsentRequest {
        if (!trashSupported) {
            return ConsentRequest.Impossible("This version of Android has no system trash.")
        }
        if (uris.isEmpty()) return ConsentRequest.Impossible("Nothing to move.")
        return try {
            ConsentRequest.Ready(
                MediaStore.createTrashRequest(context.contentResolver, uris, trash).intentSender,
            )
        } catch (t: Throwable) {
            // Never swallowed. The previous implementation returned null here
            // and the caller had no way to tell "not supported" from "it threw",
            // which is precisely how a delete could appear to do nothing at all.
            ConsentRequest.Impossible(describeTrashFailure(t))
        }
    }

    /** The result of asking the platform for a consent dialog. */
    sealed interface ConsentRequest {
        data class Ready(val sender: IntentSender) : ConsentRequest
        data class Impossible(val reason: String) : ConsentRequest
    }

    /**
     * Splits a selection into batches small enough for one consent dialog.
     *
     * Every URI in a trash request is parcelled into a PendingIntent and
     * crosses a Binder transaction, which is capped at roughly a megabyte for
     * the whole process. A large selection therefore does not fail gracefully
     * - it throws, and before this was batched a big delete could throw before
     * anything happened.
     */
    fun batchesOf(uris: List<Uri>): List<List<Uri>> =
        if (uris.isEmpty()) emptyList() else uris.chunked(CONSENT_BATCH)

    /**
     * Moves items to the system trash, without a dialog where that is allowed.
     *
     * ---------------------------------------------------------------------
     * Why this is not simply createTrashRequest
     *
     * createTrashRequest exists so that an app WITHOUT broad storage access
     * can ask the user to approve a change to files it does not own. FILISH
     * holds MANAGE_EXTERNAL_STORAGE, and an app holding it already has write
     * access to those MediaStore rows - so it can set IS_TRASHED directly,
     * and the consent dialog is at best redundant.
     *
     * Routing everything through the dialog regardless was the original bug.
     * It also made every large delete fragile, because the dialog path is the
     * one with the Binder size limit.
     *
     * So: write directly when we are permitted to, and fall back to the
     * consent flow only when the platform actually refuses. Anything that
     * fails is reported per-path with a reason - never silently dropped.
     * ---------------------------------------------------------------------
     */
    suspend fun trashDirectly(uris: List<Uri>): DirectTrashResult = withContext(Dispatchers.IO) {
        if (!trashSupported) {
            return@withContext DirectTrashResult(
                0, emptyList(), uris, "This version of Android has no system trash.",
            )
        }
        if (uris.isEmpty()) return@withContext DirectTrashResult(0, emptyList(), emptyList(), null)

        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }
        var trashed = 0
        val failures = ArrayList<FailedDeletion>()
        val needConsent = ArrayList<Uri>()

        for (uri in uris) {
            coroutineContext.ensureActive()
            try {
                val rows = context.contentResolver.update(uri, values, null, null)
                if (rows > 0) trashed++ else needConsent.add(uri)
            } catch (security: SecurityException) {
                // The platform is telling us to ask the user. That is not a
                // failure, it is the other branch.
                needConsent.add(uri)
            } catch (t: Throwable) {
                failures.add(FailedDeletion(uri.toString(), describeTrashFailure(t)))
            }
        }

        DirectTrashResult(trashed, failures, needConsent, null)
    }

    data class DirectTrashResult(
        val trashed: Int,
        val failed: List<FailedDeletion>,
        /** Items the platform would not let us trash without asking the user. */
        val needsConsent: List<Uri>,
        /** Set when nothing could be attempted at all. */
        val blockedReason: String?,
    )

    /** Turns a trash failure into something a person can act on. */
    private fun describeTrashFailure(t: Throwable): String = when {
        t is android.os.TransactionTooLargeException ||
            t.cause is android.os.TransactionTooLargeException ->
            "Too many files in one request for Android to handle at once."
        t is SecurityException -> "Android denied permission to move these files."
        t is IllegalArgumentException ->
            "Android did not recognise one of these files in its media library."
        t is UnsupportedOperationException ->
            "This storage volume does not support the system trash."
        else -> t.message?.takeIf { it.isNotBlank() }
            ?: "Android refused the request (${t.javaClass.simpleName})."
    }

    /** Permanently removes items that are in MediaStore. */
    fun deleteRequest(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }.getOrNull()
    }

    data class Outcome(
        val trashed: Int,
        val deleted: Int,
        val failed: List<FailedDeletion>,
        /** When the OS will permanently remove the trashed items. Zero when
         *  nothing was trashed or the value could not be read back. */
        val expiresAtMillis: Long,
    ) {
        val total: Int get() = trashed + deleted
        val allSucceeded: Boolean get() = failed.isEmpty()
    }

    data class FailedDeletion(val path: String, val reason: String)

    /**
     * Permanent deletion of plain filesystem paths.
     *
     * Directories are removed depth-first. A failure part-way through is
     * reported per-path rather than aborting: the user asked for ten things to
     * go, and eight of them going is a better outcome than none, provided
     * they are told which two remain and why.
     */
    suspend fun deletePermanently(paths: List<String>): Outcome = withContext(Dispatchers.IO) {
        var deleted = 0
        val failures = ArrayList<FailedDeletion>()
        val touched = ArrayList<String>()

        for (path in paths) {
            val file = File(path)
            when {
                !file.exists() -> {
                    // Already gone. Not an error: the user's intent is
                    // satisfied, and racing with another process is normal.
                    deleted++
                }
                file.isDirectory -> {
                    val failedAt = deleteTree(file)
                    if (failedAt == null) {
                        deleted++
                        touched.add(path)
                    } else {
                        failures.add(FailedDeletion(path, describeFailure(failedAt)))
                    }
                }
                else -> {
                    if (runCatching { file.delete() }.getOrDefault(false)) {
                        deleted++
                        touched.add(path)
                    } else {
                        failures.add(FailedDeletion(path, describeFailure(file)))
                    }
                }
            }
        }

        index.notifyChanged(touched)
        Outcome(trashed = 0, deleted = deleted, failed = failures, expiresAtMillis = 0L)
    }

    /** Returns null on success, or the first file that refused to go. */
    private fun deleteTree(root: File): File? {
        val children = runCatching { root.listFiles() }.getOrNull()
        if (children != null) {
            for (child in children) {
                // Do not descend through a symlink: deleting the link is
                // correct, deleting what it points at is catastrophic.
                val isLink = runCatching {
                    java.nio.file.Files.isSymbolicLink(child.toPath())
                }.getOrDefault(false)
                if (child.isDirectory && !isLink) {
                    deleteTree(child)?.let { return it }
                } else if (!runCatching { child.delete() }.getOrDefault(false)) {
                    return child
                }
            }
        }
        return if (runCatching { root.delete() }.getOrDefault(false)) null else root
    }

    private fun describeFailure(file: File): String = when {
        !file.exists() -> "It no longer exists"
        file.isDirectory && (file.listFiles()?.isNotEmpty() == true) ->
            "Something inside it could not be removed"
        !file.canWrite() -> "This location is read-only"
        com.filish.core.fs.StorageAccess.isAndroidDataOrObb(file.absolutePath) ->
            "Android restricts access to app data folders"
        else -> "The system refused the deletion"
    }

    /** Reads back the real expiry the OS assigned, for the success report. */
    suspend fun expiryOf(uris: List<Uri>): Long {
        for (uri in uris) {
            index.trashExpiryMillis(uri)?.takeIf { it > 0 }?.let { return it }
        }
        return 0L
    }

    /**
     * Confirms how many items really are trashed.
     *
     * One query for the whole set rather than one per URI - verifying a
     * few hundred files was previously a few hundred round trips to
     * MediaProvider, on the main flow, after the user had already waited.
     */
    suspend fun verifyTrashed(uris: List<Uri>): Int = index.trashedCount(uris)
}

/** Result of the system consent dialog. */
enum class ConsentResult { Granted, Denied, Unavailable;
    companion object {
        fun of(resultCode: Int): ConsentResult =
            if (resultCode == Activity.RESULT_OK) Granted else Denied
    }
}

/** Holder so the UI layer can carry an IntentSender to a launcher. */
data class PendingConsent(val sender: IntentSender, val uris: List<Uri>) {
    companion object {
        fun from(pi: PendingIntent, uris: List<Uri>) = PendingConsent(pi.intentSender, uris)
    }
}
