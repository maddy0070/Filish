package com.filish.core.fs

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * FILISH's bridge to the index Android already maintains.
 *
 * A deliberate architectural decision sits behind this class: **FILISH does
 * not build its own file index.** The obvious move for a file manager with
 * search and storage analysis is a local database - scan everything on first
 * run, keep it fresh, query it fast. FILISH does not, for three reasons.
 *
 *   1. Android already has one. MediaProvider indexes shared storage, keeps
 *      it current through the OS's own filesystem observers, and survives our
 *      process dying. A second index would be a strictly worse copy of it.
 *   2. A private index is permanently at risk of being wrong. Files change
 *      when FILISH is not running. Every stale row is a file the user is told
 *      exists when it does not, which is worse than being slow.
 *   3. It costs the user storage and battery to duplicate data they already
 *      have, in an application whose entire purpose is saving them storage.
 *
 * So: MediaStore answers the questions it is good at (find every video on the
 * device, ordered by size) and direct filesystem walks answer the rest, with
 * results streamed rather than precomputed. The one thing FILISH caches is
 * measured folder sizes, which MediaStore genuinely cannot provide.
 */
class MediaStoreIndex(private val context: Context) {

    private val filesUri: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

    /**
     * The MediaStore URI for a filesystem path, or null if it is not indexed.
     *
     * Queries by DATA, which is deprecated but remains the only column that
     * maps a path to a row and is still populated on every release FILISH
     * supports. The deprecation concerns writing through it, not reading it.
     */
    suspend fun uriFor(path: String): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                filesUri,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.DATA}=?",
                arrayOf(path),
                null,
            )?.use { c ->
                if (c.moveToFirst()) ContentUris.withAppendedId(filesUri, c.getLong(0)) else null
            }
        }.getOrNull()
    }

    /** Resolves many paths in one query rather than N. */
    suspend fun urisFor(paths: List<String>): Map<String, Uri> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext emptyMap()
        val out = HashMap<String, Uri>(paths.size)
        // SQLite caps variables per statement; chunk well below the limit.
        paths.chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            runCatching {
                context.contentResolver.query(
                    filesUri,
                    arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA),
                    "${MediaStore.Files.FileColumns.DATA} IN ($placeholders)",
                    chunk.toTypedArray(),
                    null,
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    while (c.moveToNext()) {
                        out[c.getString(dataCol)] = ContentUris.withAppendedId(filesUri, c.getLong(idCol))
                    }
                }
            }
        }
        out
    }

    /**
     * Asks MediaProvider to index a path, and waits for the URI it assigns.
     *
     * This is what makes system trash reachable for files that are not
     * already indexed - an arbitrary .zip in a user-made folder, say. Indexing
     * it first gives it a MediaStore identity, and a MediaStore identity is
     * what the trash API operates on.
     *
     * Bounded by a timeout: MediaProvider occasionally never calls back for
     * files it declines to index, and a delete must not hang because of it.
     */
    suspend fun index(path: String): Uri? = withTimeoutOrNull(4_000) {
        suspendCancellableCoroutine<Uri?> { cont ->
            runCatching {
                MediaScannerConnection.scanFile(
                    context, arrayOf(path), null,
                ) { _, uri -> if (cont.isActive) cont.resume(uri) }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    /** Tells MediaProvider that paths changed, so its index does not go stale
     *  after a FILISH operation. Fire and forget. */
    fun notifyChanged(paths: Collection<String>) {
        if (paths.isEmpty()) return
        runCatching {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
        }
    }

    fun notifyChanged(vararg paths: String) = notifyChanged(paths.toList())

    /**
     * Reads back when MediaProvider intends to permanently remove a trashed
     * item, as a wall-clock timestamp.
     *
     * FILISH reports this value rather than asserting a retention period.
     * The retention is MediaProvider's policy, it has differed between
     * Android releases and between vendors, and telling a user "30 days" when
     * their device means something else is exactly the kind of confident
     * inaccuracy that destroys trust in a delete.
     */
    suspend fun trashExpiryMillis(uri: Uri): Long? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext null
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.DATE_EXPIRES), null, null, null,
            )?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) * 1000L else null
            }
        }.getOrNull()
    }

    /** Confirms an item really is trashed, rather than trusting that the
     *  request we launched did what it said. */
    suspend fun isTrashed(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext false
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), null, null, null,
            )?.use { c -> c.moveToFirst() && c.getInt(0) == 1 } ?: false
        }.getOrDefault(false)
    }

    /**
     * How many of these URIs are actually trashed, in one query.
     *
     * The previous implementation asked MediaProvider once per URI. For a
     * few hundred files that is a few hundred round trips, run after the user
     * has already waited through the delete itself.
     */
    suspend fun trashedCount(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) {
            return@withContext 0
        }
        val ids = uris.mapNotNull { ContentUris.parseId(it).takeIf { id -> id > 0 } }
        if (ids.isEmpty()) return@withContext 0

        var count = 0
        ids.chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            runCatching {
                val args = android.os.Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    putString(
                        android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "${MediaStore.Files.FileColumns._ID} IN ($placeholders) AND " +
                            "${MediaStore.Files.FileColumns.IS_TRASHED}=1",
                    )
                    putStringArray(
                        android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        chunk.map { it.toString() }.toTypedArray(),
                    )
                }
                context.contentResolver.query(
                    filesUri,
                    arrayOf(MediaStore.Files.FileColumns._ID),
                    args,
                    null,
                )?.use { count += it.count }
            }
        }
        count
    }

    /**
     * Asks MediaProvider to index many paths at once.
     *
     * [index] scans a single path and waits up to four seconds for the
     * callback. Calling it in a loop for a large selection means the wait is
     * four seconds times the number of files - minutes of apparent hang for a
     * folder of RAW images, which MediaProvider is least likely to have
     * indexed already.
     *
     * MediaScannerConnection.scanFile accepts the whole array, so one
     * connection and one overall deadline replaces N of each.
     */
    suspend fun indexAll(paths: List<String>): Map<String, Uri> {
        if (paths.isEmpty()) return emptyMap()
        val resolved = HashMap<String, Uri>(paths.size)
        // Scale the deadline with the work, but bound it: a scan that has not
        // finished by now is one MediaProvider has declined, and the caller
        // has a correct answer for those already (they stay permanent).
        val budget = (3_000L + paths.size * 40L).coerceAtMost(20_000L)
        withTimeoutOrNull(budget) {
            suspendCancellableCoroutine<Unit> { cont ->
                val remaining = java.util.concurrent.atomic.AtomicInteger(paths.size)
                runCatching {
                    MediaScannerConnection.scanFile(
                        context, paths.toTypedArray(), null,
                    ) { path, uri ->
                        if (uri != null && path != null) {
                            synchronized(resolved) { resolved[path] = uri }
                        }
                        if (remaining.decrementAndGet() <= 0 && cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                }.onFailure { if (cont.isActive) cont.resume(Unit) }
            }
        }
        return synchronized(resolved) { HashMap(resolved) }
    }

    /** Items currently in the system trash that FILISH is permitted to see. */
    suspend fun trashedItems(): List<TrashedItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext emptyList()
        val out = ArrayList<TrashedItem>()
        runCatching {
            val args = android.os.Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
            context.contentResolver.query(
                filesUri,
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_EXPIRES,
                    MediaStore.Files.FileColumns.DATA,
                ),
                args, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        TrashedItem(
                            uri = ContentUris.withAppendedId(filesUri, c.getLong(0)),
                            name = c.getString(1) ?: File(c.getString(4) ?: "").name,
                            size = c.getLong(2),
                            expiresAtMillis = if (c.isNull(3)) 0L else c.getLong(3) * 1000L,
                            originalPath = c.getString(4),
                        ),
                    )
                }
            }
        }
        out.sortedBy { it.expiresAtMillis }
    }
}

data class TrashedItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    /** When MediaProvider will delete this permanently. Reported, not assumed. */
    val expiresAtMillis: Long,
    val originalPath: String?,
)
