package com.filish.core.fs

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A place files can live.
 *
 * "Internal storage" and "SD card" are the user's words for what Android
 * calls storage volumes, and the distinction matters far beyond labelling:
 * a move between volumes is a copy-then-delete and can fail halfway, whereas
 * a move within one volume is an atomic rename that cannot. Knowing which
 * volume a path is on is therefore an operational fact, not a cosmetic one.
 */
data class Volume(
    val id: String,
    val label: String,
    val path: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val isEmulated: Boolean,
    val totalBytes: Long,
    val availableBytes: Long,
    val readOnly: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    /** Storage is "tight" well before it is full: Android itself starts
     *  misbehaving in the last few percent, and an operation that would land
     *  the user there deserves a warning before it starts, not an error after. */
    val isTight: Boolean get() = usedFraction > 0.90f
    val isCritical: Boolean get() = usedFraction > 0.97f

    fun contains(path: String): Boolean =
        path == this.path || path.startsWith(this.path.trimEnd('/') + "/")
}

/**
 * Enumerates storage volumes.
 *
 * Android's history here is messy. [StorageVolume.getDirectory] only exists
 * from API 30; before that the path is reachable through a long-standing but
 * non-public getPath(). Rather than reflect and hope, FILISH derives paths
 * from [Context.getExternalFilesDirs], which is public on every supported
 * release and returns one app-private directory per volume - the volume root
 * is that path with the /Android/data/... suffix removed. The StorageManager
 * volumes then supply the labels and the removable/emulated flags.
 */
class VolumeRegistry(private val context: Context) {

    suspend fun volumes(): List<Volume> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val roots = derivedVolumeRoots()
        val described = manager?.storageVolumes.orEmpty()

        val result = ArrayList<Volume>(roots.size)
        roots.forEachIndexed { index, root ->
            val describing = described.firstOrNull { sv -> matches(sv, root, index) }
            val isPrimary = index == 0 || describing?.isPrimary == true
            result.add(describe(root, describing, isPrimary))
        }

        // Some devices expose a volume through StorageManager that
        // getExternalFilesDirs did not return (it can be null while ejecting).
        for (sv in described) {
            val dir = volumeDirectory(sv) ?: continue
            if (result.none { it.path == dir.absolutePath }) {
                result.add(describe(dir, sv, sv.isPrimary))
            }
        }

        result.sortedWith(compareByDescending<Volume> { it.isPrimary }.thenBy { it.label })
    }

    private fun matches(sv: StorageVolume, root: File, index: Int): Boolean {
        val dir = volumeDirectory(sv)
        if (dir != null) return dir.absolutePath == root.absolutePath
        // Without a directory the best available correlation is ordering:
        // getExternalFilesDirs and getStorageVolumes both put primary first.
        return index == 0 && sv.isPrimary
    }

    private fun volumeDirectory(sv: StorageVolume): File? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sv.directory else null

    private fun derivedVolumeRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        Environment.getExternalStorageDirectory()?.takeIf { it.exists() }?.let { roots.add(it) }
        for (appDir in context.getExternalFilesDirs(null)) {
            if (appDir == null) continue
            val root = stripAppPrivateSuffix(appDir) ?: continue
            if (root.exists()) roots.add(root)
        }
        return roots.toList()
    }

    /** /storage/XXXX-XXXX/Android/data/com.filish/files -> /storage/XXXX-XXXX */
    private fun stripAppPrivateSuffix(appDir: File): File? {
        val path = appDir.absolutePath
        val marker = "/Android/data/"
        val at = path.indexOf(marker)
        return if (at > 0) File(path.substring(0, at)) else null
    }

    private fun describe(root: File, sv: StorageVolume?, isPrimary: Boolean): Volume {
        val stat = runCatching { StatFs(root.absolutePath) }.getOrNull()
        val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
        val available = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
        val removable = sv?.isRemovable ?: !isPrimary
        val label = when {
            isPrimary -> "Internal storage"
            else -> sv?.getDescription(context)?.takeIf { it.isNotBlank() }
                ?: if (removable) "SD card" else "External storage"
        }
        return Volume(
            id = root.absolutePath,
            label = label,
            path = root.absolutePath,
            isPrimary = isPrimary,
            isRemovable = removable,
            isEmulated = isPrimary && Environment.isExternalStorageEmulated(),
            totalBytes = total,
            availableBytes = available,
            readOnly = !root.canWrite(),
        )
    }

    /** Which volume a path lives on, or null if it is outside all of them. */
    fun volumeOf(path: String, known: List<Volume>): Volume? =
        known.firstOrNull { it.contains(path) }

    /** Whether two paths are on the same volume - i.e. whether a move can be a
     *  rename. Answered by path prefix rather than by st_dev because FUSE
     *  emulated storage reports device ids that do not mean what they look
     *  like they mean. */
    fun sameVolume(a: String, b: String, known: List<Volume>): Boolean {
        val va = volumeOf(a, known) ?: return false
        val vb = volumeOf(b, known) ?: return false
        return va.id == vb.id
    }
}

/** Well-known directories worth offering as destinations and shortcuts. */
object KnownPlaces {
    fun standard(): List<Pair<String, String>> = buildList {
        val ext = Environment.getExternalStorageDirectory()?.absolutePath ?: return@buildList
        fun addIfExists(label: String, name: String) {
            val f = File(ext, name)
            if (f.isDirectory) add(label to f.absolutePath)
        }
        addIfExists("Downloads", Environment.DIRECTORY_DOWNLOADS)
        addIfExists("Camera", "DCIM")
        addIfExists("Pictures", Environment.DIRECTORY_PICTURES)
        addIfExists("Movies", Environment.DIRECTORY_MOVIES)
        addIfExists("Music", Environment.DIRECTORY_MUSIC)
        addIfExists("Documents", "Documents")
    }
}
