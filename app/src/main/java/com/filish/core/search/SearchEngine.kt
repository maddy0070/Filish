package com.filish.core.search

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
import kotlin.coroutines.coroutineContext

/** Results arrive progressively; the UI renders whatever has arrived. */
data class SearchProgress(
    val results: List<FileNode>,
    val scanned: Int,
    val complete: Boolean,
    /** The directory currently being walked, so a long search is legible
     *  rather than an indeterminate wait. */
    val currentLocation: String = "",
    val truncated: Boolean = false,
)

/**
 * Executes a [ParsedQuery] against the filesystem.
 *
 * Streaming rather than batch. A search over a device with half a million
 * files takes tens of seconds to exhaust, and a user who wanted one file
 * usually sees it within the first two hundred milliseconds. Waiting for
 * completeness before rendering anything would be slower in every way that
 * the user experiences, so results are published as they are found.
 *
 * Breadth-first rather than depth-first, deliberately. Depth-first plunges
 * into the first subdirectory it meets and can spend a minute inside an
 * Android/data tree before it ever looks at DCIM. Breadth-first surfaces
 * shallow, likely matches first, which is almost always what the user meant.
 */
class SearchEngine {

    companion object {
        /** A ceiling on results held in memory. Past this the user needs to
         *  narrow the query, not scroll further; FILISH says so rather than
         *  quietly consuming the heap. */
        private const val MAX_RESULTS = 2_000
        private const val EMIT_EVERY = 24
        private const val EMIT_INTERVAL_MS = 130L
    }

    fun search(
        roots: List<String>,
        query: ParsedQuery,
        includeHidden: Boolean,
    ): Flow<SearchProgress> = flow {
        if (query.isEmpty) {
            emit(SearchProgress(emptyList(), 0, complete = true))
            return@flow
        }

        val matcher = Matcher(query, includeHidden)
        val results = ArrayList<FileNode>(128)
        var scanned = 0
        var lastEmit = 0L
        var sinceEmit = 0
        var truncated = false
        var location = ""

        // Breadth-first frontier.
        val frontier = ArrayDeque<Path>()
        for (r in roots) {
            val f = File(r)
            if (f.isDirectory) frontier.addLast(f.toPath())
        }

        suspend fun maybeEmit(force: Boolean) {
            val now = System.currentTimeMillis()
            if (!force && sinceEmit < EMIT_EVERY && now - lastEmit < EMIT_INTERVAL_MS) return
            lastEmit = now
            sinceEmit = 0
            emit(SearchProgress(ArrayList(results), scanned, complete = false, location, truncated))
        }

        while (frontier.isNotEmpty() && !truncated) {
            coroutineContext.ensureActive()
            val dir = frontier.removeFirst()
            location = dir.fileName?.toString() ?: dir.toString()

            val stream = try {
                Files.newDirectoryStream(dir)
            } catch (t: Throwable) {
                continue // Unreadable directory. Skipping is correct; the user
                         // cannot act on "you lack permission to /proc".
            }

            stream.use { entries ->
                for (entry in entries) {
                    coroutineContext.ensureActive()
                    scanned++

                    val name = entry.fileName?.toString() ?: continue
                    val hidden = name.startsWith('.')

                    val attrs = try {
                        Files.readAttributes(
                            entry, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS,
                        )
                    } catch (t: Throwable) {
                        continue
                    }
                    if (attrs.isSymbolicLink) continue

                    val isDir = attrs.isDirectory
                    if (isDir && (includeHidden || !hidden)) frontier.addLast(entry)

                    if (hidden && !includeHidden && !query.terms.contains(Term.HiddenOnly)) continue

                    val node = FileNode(
                        path = entry.toString(),
                        name = name,
                        isDirectory = isDir,
                        size = if (isDir) 0L else attrs.size(),
                        lastModified = attrs.lastModifiedTime().toMillis(),
                        isHidden = hidden,
                        kind = Kinds.of(name, isDir),
                        extension = if (isDir) "" else Kinds.extensionOf(name),
                        canRead = true,
                        canWrite = false,
                    )

                    if (matcher.matches(node, entry)) {
                        results.add(node)
                        sinceEmit++
                        if (results.size >= MAX_RESULTS) {
                            truncated = true
                            break
                        }
                    }
                }
            }
            maybeEmit(force = false)
        }

        // Best matches first: a name that starts with the query beats one that
        // merely contains it, and a shallower path beats a deeper one.
        val text = query.textTerms.firstOrNull()?.lowercase()
        val ranked = if (text.isNullOrBlank()) results else results.sortedWith(
            compareByDescending<FileNode> { it.name.lowercase().startsWith(text) }
                .thenBy { it.path.count { c -> c == '/' } }
                .thenBy { it.name.length },
        )

        emit(SearchProgress(ranked, scanned, complete = true, "", truncated))
    }.flowOn(Dispatchers.IO)

    /** Compiled once per search rather than re-interpreted per file. */
    private class Matcher(query: ParsedQuery, private val includeHidden: Boolean) {
        private val texts = query.terms.filterIsInstance<Term.Text>().map { it.value.lowercase() }
        private val kinds: Set<FileKind> = query.terms.filterIsInstance<Term.Kind>()
            .flatMap { it.kinds }.toSet()
        private val exts = query.terms.filterIsInstance<Term.Extension>().map { it.ext.lowercase() }.toSet()
        private val minSize = query.terms.filterIsInstance<Term.LargerThan>().maxOfOrNull { it.bytes }
        private val maxSize = query.terms.filterIsInstance<Term.SmallerThan>().minOfOrNull { it.bytes }
        private val after = query.terms.filterIsInstance<Term.ModifiedAfter>().maxOfOrNull { it.epochMs }
        private val before = query.terms.filterIsInstance<Term.ModifiedBefore>().minOfOrNull { it.epochMs }
        private val hiddenOnly = query.terms.contains(Term.HiddenOnly)
        private val emptyOnly = query.terms.contains(Term.EmptyOnly)

        fun matches(node: FileNode, path: Path): Boolean {
            if (hiddenOnly && !node.isHidden) return false
            if (kinds.isNotEmpty() && node.kind !in kinds) return false
            if (exts.isNotEmpty() && node.extension.lowercase() !in exts) return false

            for (t in texts) if (!node.name.lowercase().contains(t)) return false

            // Size and age constraints do not apply to folders - a folder has
            // no meaningful size until it is measured, and excluding folders
            // here would hide the route to matching files inside them.
            if (!node.isDirectory) {
                minSize?.let { if (node.size < it) return false }
                maxSize?.let { if (node.size > it) return false }
            } else if (minSize != null || maxSize != null) {
                return false
            }

            after?.let { if (node.lastModified < it) return false }
            before?.let { if (node.lastModified > it) return false }

            if (emptyOnly) {
                return if (node.isDirectory) isEmptyDir(path) else node.size == 0L
            }

            // A query with no constraint at all should not match everything.
            return texts.isNotEmpty() || kinds.isNotEmpty() || exts.isNotEmpty() ||
                minSize != null || maxSize != null || after != null || before != null ||
                hiddenOnly
        }

        private fun isEmptyDir(path: Path): Boolean = runCatching {
            Files.newDirectoryStream(path).use { !it.iterator().hasNext() }
        }.getOrDefault(false)
    }
}
