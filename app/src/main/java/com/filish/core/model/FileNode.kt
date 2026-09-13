package com.filish.core.model

import java.io.File

/**
 * One entry in a directory, as FILISH sees it.
 *
 * Flat and immutable by design. A directory listing can hold hundreds of
 * thousands of these, so this class carries only what can be obtained from a
 * single stat() during the walk. Anything requiring further I/O - recursive
 * size, child count, thumbnails, media metadata, hashes - is resolved later,
 * asynchronously, and merged in by the presentation layer. Putting a
 * recursive size on this object would mean a directory listing could not
 * return until the whole subtree had been walked, which is precisely the
 * behaviour that makes conventional file managers freeze.
 */
data class FileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    /** Bytes. For a directory this is the directory entry's own size, which
     *  is meaningless to a user - use [com.filish.core.fs.SizeResolver]. */
    val size: Long,
    val lastModified: Long,
    val isHidden: Boolean,
    val kind: FileKind,
    val extension: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    /** True when the entry is a symbolic link. Walks must not follow these or
     *  a cyclic link turns a size calculation into an infinite loop. */
    val isLink: Boolean = false,
) {
    val file: File get() = File(path)

    val parentPath: String?
        get() = path.substringBeforeLast('/', "").ifEmpty { null }

    /** Name without its extension, for rename fields that should preselect
     *  the part the user actually wants to change. */
    val stem: String
        get() = if (extension.isEmpty()) name
        else name.dropLast(extension.length + 1).ifEmpty { name }

    companion object {
        fun of(file: File): FileNode {
            val isDir = file.isDirectory
            val name = file.name
            return FileNode(
                path = file.absolutePath,
                name = name,
                isDirectory = isDir,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                isHidden = name.startsWith('.'),
                kind = Kinds.of(name, isDir),
                extension = if (isDir) "" else Kinds.extensionOf(name),
                canRead = file.canRead(),
                canWrite = file.canWrite(),
            )
        }
    }
}

/** A measured size that knows whether it is finished. */
data class MeasuredSize(
    val bytes: Long,
    val files: Int,
    val folders: Int,
    /** False while a walk is still running. The UI shows a settling figure
     *  rather than a spinner, and the number climbs toward the truth. */
    val settled: Boolean,
    /** Entries the walk could not read. Reported rather than silently
     *  dropped - a total that quietly omits 4 GB is worse than no total. */
    val unreadable: Int = 0,
) {
    companion object {
        val Unknown = MeasuredSize(0, 0, 0, settled = false)
        fun exact(bytes: Long) = MeasuredSize(bytes, 1, 0, settled = true)
    }
}
