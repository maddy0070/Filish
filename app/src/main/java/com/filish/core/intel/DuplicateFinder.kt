package com.filish.core.intel

import com.filish.core.model.FileNode
import com.filish.core.model.Kinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.coroutineContext

/** A set of files with identical content. */
data class DuplicateGroup(
    val hash: String,
    val files: List<FileNode>,
    val sizeEach: Long,
) {
    /** What deleting all but one copy would free. */
    val reclaimable: Long get() = sizeEach * (files.size - 1).coerceAtLeast(0)

    /**
     * Which copy to keep, by default.
     *
     * The oldest, because it is almost always the original - the one whose
     * path the user knows and that other things may reference. Copies acquire
     * later timestamps when they are made. Ties break on the shortest path,
     * which favours the file sitting somewhere deliberate over one buried in
     * a download or cache directory.
     */
    val suggestedKeep: FileNode
        get() = files.minWithOrNull(
            compareBy<FileNode> { it.lastModified }.thenBy { it.path.length },
        ) ?: files.first()

    val redundant: List<FileNode> get() = files.filter { it.path != suggestedKeep.path }
}

data class DuplicateProgress(
    val groups: List<DuplicateGroup>,
    val filesScanned: Int,
    val candidatesCompared: Int,
    val bytesHashed: Long,
    val complete: Boolean,
    val phase: Phase,
) {
    enum class Phase(val label: String) {
        Walking("Listing your files"),
        Sampling("Comparing files of the same size"),
        Hashing("Confirming exact matches"),
        Done("Finished"),
    }

    val totalReclaimable: Long get() = groups.sumOf { it.reclaimable }
    val totalRedundant: Int get() = groups.sumOf { it.files.size - 1 }
}

/**
 * Exact duplicate detection.
 *
 * ---------------------------------------------------------------------------
 * The whole design is about NOT hashing
 *
 * The obvious implementation hashes every file and groups by digest. On a
 * phone with 80,000 files and 200 GB of media that means reading 200 GB -
 * twenty minutes of solid I/O and a measurable dent in the battery, to answer
 * a question where the answer is usually "a few dozen files".
 *
 * FILISH eliminates non-duplicates in increasing order of cost, and only ever
 * pays the expensive price for candidates that have survived everything
 * cheaper:
 *
 *   1. GROUP BY EXACT BYTE LENGTH. Two files of different sizes cannot be
 *      identical. The size came free with the directory walk, so this costs
 *      nothing and discards the overwhelming majority of pairs.
 *
 *   2. FINGERPRINT THE ENDS. For files that share a size, hash the first and
 *      last 64 KB. Container headers live at the start, so distinct media of
 *      identical length almost always differ there. This reads 128 KB of a
 *      file rather than all of it - for a 2 GB video, 0.006% of the work.
 *
 *   3. FULL HASH, only for files that survived both. Usually a handful.
 *
 * A size collision is not evidence of duplication; only a full digest match
 * is reported as a duplicate. FILISH never tells the user two files are
 * identical because they happened to be the same length.
 *
 * The scan streams its groups as it finds them and names the phase it is in,
 * so a long run is legible rather than an indeterminate wait.
 */
class DuplicateFinder {

    companion object {
        /** Below this, files are so numerous and so cheap to store that
         *  reporting them as duplicates is noise rather than a finding. */
        private const val MIN_SIZE = 16 * 1024L
        private const val EMIT_INTERVAL_MS = 220L
    }

    fun scan(root: String, includeHidden: Boolean): Flow<DuplicateProgress> = flow {
        val bySize = HashMap<Long, MutableList<FileNode>>()
        var scanned = 0
        var lastEmit = 0L

        val groups = ArrayList<DuplicateGroup>()
        var compared = 0
        var bytesHashed = 0L

        suspend fun emitNow(phase: DuplicateProgress.Phase, complete: Boolean = false) {
            emit(
                DuplicateProgress(
                    groups = ArrayList(groups),
                    filesScanned = scanned,
                    candidatesCompared = compared,
                    bytesHashed = bytesHashed,
                    complete = complete,
                    phase = phase,
                ),
            )
        }

        emitNow(DuplicateProgress.Phase.Walking)

        // Phase 1 - walk, grouping by size. Breadth-first with an explicit
        // queue; recursion over a user's filesystem invites a stack overflow.
        val frontier = ArrayDeque<Path>()
        val rootPath = java.io.File(root)
        if (!rootPath.isDirectory) {
            emitNow(DuplicateProgress.Phase.Done, complete = true)
            return@flow
        }
        frontier.addLast(rootPath.toPath())

        while (frontier.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = frontier.removeFirst()
            val stream = try {
                Files.newDirectoryStream(dir)
            } catch (t: Throwable) {
                continue
            }
            stream.use { entries ->
                for (entry in entries) {
                    coroutineContext.ensureActive()
                    val name = entry.fileName?.toString() ?: continue
                    val hidden = name.startsWith('.')
                    if (hidden && !includeHidden) continue

                    val attrs = try {
                        Files.readAttributes(
                            entry, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS,
                        )
                    } catch (t: Throwable) {
                        continue
                    }
                    // A symlink and its target are the same bytes on disk;
                    // reporting them as duplicates would invite the user to
                    // "reclaim" space that does not exist.
                    if (attrs.isSymbolicLink) continue

                    if (attrs.isDirectory) {
                        frontier.addLast(entry)
                        continue
                    }
                    scanned++
                    val size = attrs.size()
                    if (size < MIN_SIZE) continue

                    bySize.getOrPut(size) { ArrayList(2) }.add(
                        FileNode(
                            path = entry.toString(), name = name, isDirectory = false,
                            size = size, lastModified = attrs.lastModifiedTime().toMillis(),
                            isHidden = hidden, kind = Kinds.of(name, false),
                            extension = Kinds.extensionOf(name), canRead = true, canWrite = true,
                        ),
                    )
                }
            }
            val now = System.currentTimeMillis()
            if (now - lastEmit > EMIT_INTERVAL_MS) {
                lastEmit = now
                emitNow(DuplicateProgress.Phase.Walking)
            }
        }

        // Only sizes shared by more than one file can contain duplicates.
        // Largest first, so the most valuable findings arrive earliest.
        val candidateGroups = bySize.entries
            .filter { it.value.size > 1 }
            .sortedByDescending { it.key }

        emitNow(DuplicateProgress.Phase.Sampling)

        for ((size, candidates) in candidateGroups) {
            coroutineContext.ensureActive()
            compared += candidates.size

            // Phase 2 - cheap fingerprint from both ends of each file.
            val bySample = HashMap<String, MutableList<FileNode>>()
            for (candidate in candidates) {
                coroutineContext.ensureActive()
                val sample = Fingerprint.sample(java.io.File(candidate.path)) ?: continue
                bytesHashed += minOf(size, 128 * 1024L)
                bySample.getOrPut(sample) { ArrayList(2) }.add(candidate)
            }

            // Phase 3 - full digest, only for what survived.
            for (sampleGroup in bySample.values) {
                coroutineContext.ensureActive()
                if (sampleGroup.size < 2) continue

                val byDigest = HashMap<String, MutableList<FileNode>>()
                for (candidate in sampleGroup) {
                    coroutineContext.ensureActive()
                    val digest = Fingerprint.sha256(java.io.File(candidate.path)) ?: continue
                    bytesHashed += size
                    byDigest.getOrPut(digest) { ArrayList(2) }.add(candidate)
                }
                for ((digest, identical) in byDigest) {
                    if (identical.size > 1) {
                        groups.add(DuplicateGroup(digest, identical.sortedBy { it.lastModified }, size))
                    }
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastEmit > EMIT_INTERVAL_MS) {
                lastEmit = now
                emitNow(DuplicateProgress.Phase.Hashing)
            }
        }

        groups.sortByDescending { it.reclaimable }
        emitNow(DuplicateProgress.Phase.Done, complete = true)
    }.flowOn(Dispatchers.IO)
}
