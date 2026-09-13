package com.filish.core.fs

import android.util.LruCache
import com.filish.core.model.MeasuredSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * How much space does this actually take?
 *
 * This is the question conventional file managers refuse to answer. They show
 * a folder's size as blank, or as the 4 KB of its directory entry, and leave
 * the user to discover the truth by copying folders somewhere and watching a
 * progress bar. FILISH answers it - the constraint being that the honest
 * answer requires walking the entire subtree, which for a large folder takes
 * seconds and for a pathological one takes minutes.
 *
 * The design consequence is the important part: **the answer is a stream, not
 * a value.** A measurement emits a running total from the moment it starts,
 * climbing as the walk proceeds, and marks itself settled when the walk
 * finishes. The interface shows a number that is rising rather than a spinner
 * that means nothing, so the user gets a usable approximation in the first
 * hundred milliseconds and an exact figure when it is available. Nothing ever
 * blocks, and a partial answer is never mistaken for a final one.
 *
 * Parallelism is bounded and depth-limited. Directories near the root are
 * walked concurrently because that is where the branching factor pays, but
 * below a shallow depth the walk goes sequential, which keeps the number of
 * live coroutines proportional to the top of the tree rather than to its
 * total size. A 500,000-file tree must not create 500,000 of anything.
 */
class SizeResolver {

    private data class Cached(val size: MeasuredSize, val measuredAt: Long, val dirMtime: Long)

    /**
     * Bounded cache. Bounded is the operative word: an unbounded map keyed by
     * path is a memory leak with extra steps on a device with a deep tree.
     */
    private val cache = object : LruCache<String, Cached>(512) {}

    companion object {
        /** Below this depth the walk stops branching into new coroutines. */
        private const val PARALLEL_DEPTH = 2
        private const val PARALLELISM = 6
        /** How often a running measurement publishes its progress. Fast enough
         *  to read as continuous motion, slow enough not to recompose a list
         *  on every file. */
        private const val EMIT_INTERVAL_MS = 110L
        /** A cached figure older than this is re-measured even if the folder's
         *  own mtime is unchanged, because mtime does not change when a file
         *  deep inside the subtree does. */
        private const val CACHE_TTL_MS = 30_000L
    }

    /**
     * Measures one or more roots as a single aggregate.
     *
     * Passing several paths at once is what the selection ledger needs: a
     * mixed selection of files and folders is one question, not N questions,
     * and the files' sizes are known immediately while the folders resolve.
     */
    fun measure(paths: Collection<String>): Flow<MeasuredSize> = flow {
        val bytes = AtomicLong(0)
        val files = AtomicInteger(0)
        val folders = AtomicInteger(0)
        val unreadable = AtomicInteger(0)

        // Anything already known is folded in before the walk starts, so the
        // first emission is immediate and non-zero rather than a zero that
        // flashes.
        val toWalk = ArrayList<File>()
        for (p in paths) {
            val f = File(p)
            when {
                !f.exists() -> unreadable.incrementAndGet()
                f.isDirectory -> {
                    val hit = cachedFor(f)
                    if (hit != null) {
                        bytes.addAndGet(hit.bytes)
                        files.addAndGet(hit.files)
                        folders.addAndGet(hit.folders + 1)
                        unreadable.addAndGet(hit.unreadable)
                    } else {
                        folders.incrementAndGet()
                        toWalk.add(f)
                    }
                }
                else -> {
                    bytes.addAndGet(f.length())
                    files.incrementAndGet()
                }
            }
        }

        fun snapshot(settled: Boolean) = MeasuredSize(
            bytes = bytes.get(),
            files = files.get(),
            folders = folders.get(),
            settled = settled,
            unreadable = unreadable.get(),
        )

        if (toWalk.isEmpty()) {
            emit(snapshot(settled = true))
            return@flow
        }

        emit(snapshot(settled = false))

        coroutineScope {
            val gate = Semaphore(PARALLELISM)
            val worker = launch(Dispatchers.IO) {
                coroutineScope {
                    for (root in toWalk) {
                        launch { walk(root.toPath(), 0, gate, bytes, files, folders, unreadable) }
                    }
                }
                // Cache each root only once its own walk is complete and only
                // when the walk was not cancelled - a partial total must never
                // be remembered as the truth.
                for (root in toWalk) {
                    runCatching {
                        val single = measureBlocking(root)
                        cache.put(root.absolutePath, Cached(single, System.currentTimeMillis(), root.lastModified()))
                    }
                }
            }

            var last = -1L
            while (worker.isActive) {
                delay(EMIT_INTERVAL_MS)
                val now = bytes.get()
                // Do not re-emit an identical total; a list of identical values
                // is recomposition for nothing.
                if (now != last) {
                    last = now
                    emit(snapshot(settled = false))
                }
            }
            emit(snapshot(settled = true))
        }
    }

    /** Single-shot measurement for callers that genuinely need one value. */
    suspend fun measureOnce(path: String): MeasuredSize = withContext(Dispatchers.IO) {
        val f = File(path)
        if (!f.exists()) return@withContext MeasuredSize(0, 0, 0, settled = true, unreadable = 1)
        if (!f.isDirectory) return@withContext MeasuredSize.exact(f.length())
        cachedFor(f)?.let { return@withContext it }

        val bytes = AtomicLong(0)
        val files = AtomicInteger(0)
        val folders = AtomicInteger(0)
        val unreadable = AtomicInteger(0)
        coroutineScope {
            walk(f.toPath(), 0, Semaphore(PARALLELISM), bytes, files, folders, unreadable)
        }
        val result = MeasuredSize(bytes.get(), files.get(), folders.get(), true, unreadable.get())
        cache.put(f.absolutePath, Cached(result, System.currentTimeMillis(), f.lastModified()))
        result
    }

    private suspend fun measureBlocking(root: File): MeasuredSize {
        val b = AtomicLong(0); val fi = AtomicInteger(0)
        val fo = AtomicInteger(0); val u = AtomicInteger(0)
        coroutineScope { walk(root.toPath(), PARALLEL_DEPTH + 1, Semaphore(1), b, fi, fo, u) }
        return MeasuredSize(b.get(), fi.get(), fo.get(), true, u.get())
    }

    private fun cachedFor(dir: File): MeasuredSize? {
        val hit = cache.get(dir.absolutePath) ?: return null
        val fresh = System.currentTimeMillis() - hit.measuredAt < CACHE_TTL_MS
        val unchanged = hit.dirMtime == dir.lastModified()
        return if (fresh && unchanged) hit.size else null
    }

    /**
     * The walk itself.
     *
     * Symbolic links are counted as their own (tiny) size and never followed.
     * A link pointing at an ancestor is not a hypothetical: it is how a size
     * calculation becomes an infinite loop, and it exists in the wild.
     */
    private suspend fun walk(
        dir: Path,
        depth: Int,
        gate: Semaphore,
        bytes: AtomicLong,
        files: AtomicInteger,
        folders: AtomicInteger,
        unreadable: AtomicInteger,
    ) {
        coroutineContext.ensureActive()
        val children = ArrayList<Path>(16)

        try {
            gate.acquire()
            try {
                Files.newDirectoryStream(dir).use { stream ->
                    for (entry in stream) {
                        coroutineContext.ensureActive()
                        val attrs = try {
                            Files.readAttributes(
                                entry, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS,
                            )
                        } catch (t: Throwable) {
                            unreadable.incrementAndGet()
                            continue
                        }
                        when {
                            attrs.isSymbolicLink -> {
                                // Counted, not traversed.
                                files.incrementAndGet()
                            }
                            attrs.isDirectory -> {
                                folders.incrementAndGet()
                                children.add(entry)
                            }
                            else -> {
                                bytes.addAndGet(attrs.size())
                                files.incrementAndGet()
                            }
                        }
                    }
                }
            } finally {
                gate.release()
            }
        } catch (t: Throwable) {
            unreadable.incrementAndGet()
            return
        }

        if (children.isEmpty()) return

        if (depth < PARALLEL_DEPTH) {
            coroutineScope {
                for (child in children) {
                    launch { walk(child, depth + 1, gate, bytes, files, folders, unreadable) }
                }
            }
        } else {
            for (child in children) {
                walk(child, depth + 1, gate, bytes, files, folders, unreadable)
            }
        }
    }

    /** Invalidate after an operation changed something under [path]. */
    fun invalidate(path: String) {
        cache.remove(path)
        // Ancestors' totals are now wrong too.
        var parent = File(path).parent
        while (parent != null) {
            cache.remove(parent)
            parent = File(parent).parent
        }
    }

    fun invalidateAll() = cache.evictAll()
}
