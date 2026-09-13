package com.filish.core.intel

import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.Kinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.PriorityQueue
import kotlin.coroutines.coroutineContext

/**
 * Analyses a volume.
 *
 * The central decision: **one walk answers every question.**
 *
 * The naive implementation of a storage screen runs a separate scan per
 * panel - one for the category breakdown, one for largest files, one for
 * largest folders, one for old files, one for duplicate candidates. That is
 * five full traversals of the user's storage, five times the I/O, five times
 * the battery, and five progress bars that finish at different moments.
 *
 * Every statistic this class produces is accumulated during a single
 * breadth-first pass. Largest-files uses a bounded min-heap so the top N is
 * maintained in O(log N) per file with no sorting at the end and no list of
 * half a million nodes in memory. Folder weights are rolled up on the way
 * back out. Duplicate *candidates* fall out for free: a map of size to count
 * costs one hash insert per file and eliminates the overwhelming majority of
 * pairs before any file is ever opened.
 *
 * The report streams, like everything else in FILISH. A user who opens the
 * storage screen sees the composition of their device fill in over a couple
 * of seconds rather than watching an indeterminate spinner and then being
 * handed a finished answer.
 */
class StorageAnalyzer {

    companion object {
        private const val TOP_FILES = 60
        private const val TOP_FOLDERS = 40
        private const val EMIT_INTERVAL_MS = 220L
        /** Depth at which a directory still counts as a "place" worth naming
         *  in the heaviest-folders list. Below this the answer degenerates
         *  into a list of leaf directories nobody recognises. */
        private const val FOLDER_REPORT_DEPTH = 4
    }

    fun analyze(
        volumePath: String,
        totalBytes: Long,
        availableBytes: Long,
        includeHidden: Boolean,
    ): Flow<StorageReport> = flow {
        val now = System.currentTimeMillis()

        val byKind = HashMap<FileKind, Bucket>()
        val byAge = HashMap<AgeBand, Bucket>()
        val bySize = HashMap<SizeBand, Bucket>()
        // Min-heap of the largest files seen: the smallest of the top N sits
        // at the head and is evicted first.
        val largest = PriorityQueue<FileNode>(TOP_FILES + 1, compareBy { it.size })
        val folderBytes = HashMap<String, LongArray>() // path -> [bytes, files]
        val sizeCounts = HashMap<Long, Int>()

        var accounted = 0L
        var files = 0
        var folders = 0
        var empties = 0
        var hidden = 0L
        var unreadable = 0
        var lastEmit = 0L

        fun snapshot(complete: Boolean): StorageReport {
            var dupCandidates = 0
            var dupBytes = 0L
            for ((size, n) in sizeCounts) {
                // Zero-length files collide trivially and are never worth
                // reporting as duplicates.
                if (n > 1 && size > 0) {
                    dupCandidates += n
                    dupBytes += size * (n - 1)
                }
            }
            val heavy = folderBytes.entries
                .asSequence()
                .map { (path, v) ->
                    HeavyFolder(
                        path = path,
                        name = File(path).name.ifEmpty { path },
                        bytes = v[0],
                        fileCount = v[1].toInt(),
                        depth = path.count { it == '/' },
                    )
                }
                .filter { it.bytes > 0 }
                .sortedByDescending { it.bytes }
                .take(TOP_FOLDERS)
                .toList()

            return StorageReport(
                volumePath = volumePath,
                totalBytes = totalBytes,
                availableBytes = availableBytes,
                accountedBytes = accounted,
                byKind = HashMap(byKind),
                byAge = HashMap(byAge),
                bySize = HashMap(bySize),
                largestFiles = largest.sortedByDescending { it.size },
                heaviestFolders = heavy,
                emptyFolders = empties,
                hiddenBytes = hidden,
                duplicateCandidates = dupCandidates,
                duplicateCandidateBytes = dupBytes,
                filesScanned = files,
                foldersScanned = folders,
                unreadable = unreadable,
                complete = complete,
            )
        }

        emit(snapshot(complete = false))

        // Breadth-first with an explicit queue: recursion on a user's
        // filesystem is an invitation to a StackOverflowError, and a deque
        // costs nothing.
        val root = File(volumePath)
        if (!root.isDirectory) {
            emit(snapshot(complete = true))
            return@flow
        }

        val frontier = ArrayDeque<Pair<Path, Int>>()
        frontier.addLast(root.toPath() to 0)

        while (frontier.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (dir, depth) = frontier.removeFirst()

            val stream = try {
                Files.newDirectoryStream(dir)
            } catch (t: Throwable) {
                unreadable++
                continue
            }

            var childCount = 0
            var dirBytes = 0L
            var dirFiles = 0

            stream.use { entries ->
                for (entry in entries) {
                    coroutineContext.ensureActive()
                    childCount++
                    val name = entry.fileName?.toString() ?: continue
                    val isHidden = name.startsWith('.')
                    if (isHidden && !includeHidden) continue

                    val attrs = try {
                        Files.readAttributes(
                            entry, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS,
                        )
                    } catch (t: Throwable) {
                        unreadable++
                        continue
                    }
                    // Never traversed and never counted: following a link
                    // double-counts its target's bytes.
                    if (attrs.isSymbolicLink) continue

                    if (attrs.isDirectory) {
                        folders++
                        frontier.addLast(entry to depth + 1)
                        continue
                    }

                    val size = attrs.size()
                    val modified = attrs.lastModifiedTime().toMillis()
                    val kind = Kinds.of(name, false)

                    files++
                    accounted += size
                    dirBytes += size
                    dirFiles++
                    if (isHidden) hidden += size

                    byKind[kind] = (byKind[kind] ?: Bucket.Zero) + Bucket(size, 1)
                    val age = AgeBand.of(modified, now)
                    byAge[age] = (byAge[age] ?: Bucket.Zero) + Bucket(size, 1)
                    val band = SizeBand.of(size)
                    bySize[band] = (bySize[band] ?: Bucket.Zero) + Bucket(size, 1)

                    if (size > 0) sizeCounts[size] = (sizeCounts[size] ?: 0) + 1

                    if (size > 0 && (largest.size < TOP_FILES || size > (largest.peek()?.size ?: 0))) {
                        largest.add(
                            FileNode(
                                path = entry.toString(), name = name, isDirectory = false,
                                size = size, lastModified = modified, isHidden = isHidden,
                                kind = kind, extension = Kinds.extensionOf(name),
                                canRead = true, canWrite = true,
                            ),
                        )
                        if (largest.size > TOP_FILES) largest.poll()
                    }
                }
            }

            if (childCount == 0) empties++

            // Roll this directory's own bytes into every ancestor within
            // reporting depth, so "Downloads" shows the weight of everything
            // beneath it rather than only its loose files.
            if (dirBytes > 0) {
                var p: Path? = dir
                var d = depth
                while (p != null && d >= 0) {
                    if (d <= FOLDER_REPORT_DEPTH && d > 0) {
                        val key = p.toString()
                        val acc = folderBytes.getOrPut(key) { longArrayOf(0, 0) }
                        acc[0] += dirBytes
                        acc[1] += dirFiles
                    }
                    p = p.parent
                    d--
                }
            }

            val now2 = System.currentTimeMillis()
            if (now2 - lastEmit > EMIT_INTERVAL_MS) {
                lastEmit = now2
                emit(snapshot(complete = false))
            }
        }

        emit(snapshot(complete = true))
    }.flowOn(Dispatchers.IO)
}
