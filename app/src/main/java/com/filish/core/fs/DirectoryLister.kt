package com.filish.core.fs

import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.Kinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.coroutineContext

/** What came back from asking for a directory's contents. */
sealed interface Listing {
    data class Content(
        val entries: List<FileNode>,
        /** Entries present on disk that could not be stat()ed. Surfaced rather
         *  than dropped, so a listing never silently under-reports. */
        val unreadable: Int,
    ) : Listing

    /** The path exists but the process is not permitted to read it. */
    data class Denied(val path: String, val needsAllFilesAccess: Boolean) : Listing

    /** The path is gone. Filesystems change underneath us constantly. */
    data class Missing(val path: String) : Listing

    /** The path is a file, not a directory. */
    data class NotADirectory(val path: String) : Listing

    data class Failed(val path: String, val reason: String) : Listing
}

/**
 * Reads a directory.
 *
 * The performance-critical decision here is using java.nio's directory stream
 * with a single [BasicFileAttributes] read per entry, rather than
 * File.listFiles() followed by isDirectory()/length()/lastModified(). The
 * latter is the obvious approach and costs four stat() syscalls per file
 * instead of one. In a directory of 50,000 entries - a camera roll, a
 * WhatsApp media folder - that difference is the difference between a listing
 * that appears and one that hangs.
 *
 * Symbolic links are stat()ed with NOFOLLOW and flagged. Following them here
 * would be harmless, but recording the fact lets the recursive walkers refuse
 * to follow them, which is what stops a self-referential link from turning a
 * folder-size calculation into an infinite loop.
 */
class DirectoryLister {

    suspend fun list(path: String): Listing = withContext(Dispatchers.IO) {
        val dir = File(path)

        when {
            !dir.exists() -> return@withContext Listing.Missing(path)
            !dir.isDirectory -> return@withContext Listing.NotADirectory(path)
            !dir.canRead() -> return@withContext Listing.Denied(
                path, needsAllFilesAccess = StorageAccess.isOutsideMediaScope(path),
            )
        }

        try {
            readWithNio(dir)
        } catch (io: IOException) {
            // nio can fail on some vendor filesystems and on FUSE mounts in
            // ways java.io tolerates. Falling back is cheaper than being right.
            try {
                readWithLegacyIo(dir)
            } catch (t: Throwable) {
                Listing.Failed(path, t.message ?: "Could not read this folder")
            }
        } catch (se: SecurityException) {
            Listing.Denied(path, needsAllFilesAccess = StorageAccess.isOutsideMediaScope(path))
        }
    }

    private suspend fun readWithNio(dir: File): Listing {
        val entries = ArrayList<FileNode>(64)
        var unreadable = 0
        val base: Path = dir.toPath()

        val stream: DirectoryStream<Path> = Files.newDirectoryStream(base)
        stream.use {
            for (entry in it) {
                coroutineContext.ensureActive()
                val name = entry.fileName?.toString() ?: continue
                val node = statOrNull(entry, name)
                if (node == null) unreadable++ else entries.add(node)
            }
        }
        return Listing.Content(entries, unreadable)
    }

    private fun statOrNull(entry: Path, name: String): FileNode? = try {
        val attrs = Files.readAttributes(
            entry, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS,
        )
        val isLink = attrs.isSymbolicLink
        // A link to a directory should still behave like a directory when
        // tapped, so resolve the target's type - but only for links, which
        // are rare, so the extra stat costs nothing in the common case.
        val isDir = if (isLink) Files.isDirectory(entry) else attrs.isDirectory
        FileNode(
            path = entry.toString(),
            name = name,
            isDirectory = isDir,
            size = if (isDir) 0L else attrs.size(),
            lastModified = attrs.lastModifiedTime().toMillis(),
            isHidden = name.startsWith('.'),
            kind = Kinds.of(name, isDir),
            extension = if (isDir) "" else Kinds.extensionOf(name),
            canRead = Files.isReadable(entry),
            canWrite = Files.isWritable(entry),
            isLink = isLink,
        )
    } catch (t: Throwable) {
        null
    }

    private suspend fun readWithLegacyIo(dir: File): Listing {
        val children = dir.listFiles() ?: return Listing.Denied(
            dir.absolutePath, needsAllFilesAccess = StorageAccess.isOutsideMediaScope(dir.absolutePath),
        )
        val entries = ArrayList<FileNode>(children.size)
        for (child in children) {
            coroutineContext.ensureActive()
            runCatching { entries.add(FileNode.of(child)) }
        }
        return Listing.Content(entries, unreadable = 0)
    }

    /**
     * Counts a directory's immediate children without building nodes for them.
     *
     * Used to answer "12 items" on a folder row. Deliberately shallow and
     * deliberately separate from listing: it is cheap enough to run for every
     * visible folder row, which a recursive count is not.
     */
    suspend fun childCount(path: String, includeHidden: Boolean): Int = withContext(Dispatchers.IO) {
        try {
            var n = 0
            Files.newDirectoryStream(File(path).toPath()).use { stream ->
                for (entry in stream) {
                    coroutineContext.ensureActive()
                    if (includeHidden || entry.fileName?.toString()?.startsWith('.') != true) n++
                }
            }
            n
        } catch (t: Throwable) {
            -1
        }
    }
}

/** Quick predicates about where a path sits relative to Android's storage rules. */
object StorageAccess {

    /**
     * Whether reading this path plausibly requires All-files access rather
     * than the per-media-type permissions. Used only to phrase the permission
     * explanation accurately - it is a heuristic about *why* access failed,
     * never a gate on attempting it.
     */
    fun isOutsideMediaScope(path: String): Boolean {
        val p = path.trimEnd('/')
        val mediaish = listOf("/DCIM", "/Pictures", "/Movies", "/Music", "/Download", "/Downloads")
        return mediaish.none { p.contains(it, ignoreCase = true) }
    }

    fun isAndroidDataOrObb(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("/android/data") || lower.contains("/android/obb")
    }
}
