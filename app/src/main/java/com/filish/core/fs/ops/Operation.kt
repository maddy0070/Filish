package com.filish.core.fs.ops

import com.filish.core.model.FileNode

/** What the user asked for. */
enum class OperationKind(val verb: String, val gerund: String, val past: String) {
    Copy("Copy", "Copying", "Copied"),
    Move("Move", "Moving", "Moved"),
    Delete("Delete", "Deleting", "Deleted"),
    Compress("Compress", "Compressing", "Compressed"),
    Extract("Extract", "Extracting", "Extracted"),
}

/**
 * What to do when the destination already has something by that name.
 *
 * Notably absent: a silent default. Overwriting without asking destroys data,
 * and skipping without asking silently fails to do what was asked. Either is
 * defensible only when the user chose it. FILISH stops and asks, and offers
 * to apply the answer to the rest of the batch - because being asked eleven
 * times is its own kind of broken.
 */
enum class ConflictPolicy {
    Ask,
    /** Keep both: the incoming file is renamed "name (2).ext". */
    KeepBoth,
    Overwrite,
    Skip,
    /** Overwrite only if the source is newer. Useful for folder merges. */
    OverwriteIfNewer,
}

data class Conflict(
    val source: FileNode,
    val destinationPath: String,
    val existingSize: Long,
    val existingModified: Long,
) {
    val sourceIsNewer: Boolean get() = source.lastModified > existingModified
    val sizesDiffer: Boolean get() = source.size != existingSize
}

/** A live view of an operation, published continuously while it runs. */
data class OperationProgress(
    val id: Long,
    val kind: OperationKind,
    val state: OperationState,
    val currentItemName: String,
    val itemsDone: Int,
    val itemsTotal: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
    val bytesPerSecond: Long,
    val destinationLabel: String,
    val sourceLabel: String,
    val skipped: Int = 0,
    val errors: List<OperationError> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
) {
    val fraction: Float
        get() = when {
            bytesTotal > 0 -> (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
            itemsTotal > 0 -> (itemsDone.toFloat() / itemsTotal).coerceIn(0f, 1f)
            else -> 0f
        }

    /**
     * Remaining time, or null when FILISH does not yet know enough to say.
     *
     * Returning null rather than a wild first guess is deliberate: an ETA
     * that says "3 hours" for the first second and then "20 seconds" has told
     * the user nothing and cost their trust. FILISH stays silent until the
     * throughput estimate has settled.
     */
    val etaMillis: Long?
        get() {
            if (bytesPerSecond <= 0 || bytesTotal <= 0) return null
            if (System.currentTimeMillis() - startedAt < 1_200) return null
            val remaining = bytesTotal - bytesDone
            if (remaining <= 0) return null
            return remaining * 1000 / bytesPerSecond
        }
}

enum class OperationState { Preparing, Running, Paused, AwaitingConflict, Completed, Failed, Cancelled }

data class OperationError(val path: String, val reason: String, val recoverable: Boolean)

/**
 * The outcome, written so the report can be specific.
 *
 * A file operation that half-worked is the normal case, not the exception -
 * storage fills, files vanish, permissions bite. Carrying the counts and the
 * per-path reasons means FILISH can say "moved 46 of 48; two were open in
 * another app" instead of "something went wrong".
 */
data class OperationResult(
    val id: Long,
    val kind: OperationKind,
    val succeeded: Int,
    val skipped: Int,
    val failed: List<OperationError>,
    val bytesMoved: Long,
    val destination: String?,
    val cancelled: Boolean = false,
    /** Paths written by this operation, in case the user wants them back. */
    val createdPaths: List<String> = emptyList(),
    val durationMillis: Long = 0,
) {
    val total: Int get() = succeeded + skipped + failed.size
    val isClean: Boolean get() = failed.isEmpty() && !cancelled
}
