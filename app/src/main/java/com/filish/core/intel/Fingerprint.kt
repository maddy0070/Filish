package com.filish.core.intel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Content fingerprints.
 *
 * The strategy that makes duplicate detection affordable is doing as little
 * of this as possible. Hashing is the most expensive thing a file manager can
 * do - it reads every byte of every candidate - so it is the *last* step, not
 * the first:
 *
 *   1. Group by exact byte length. Two files of different sizes cannot be
 *      identical, and this eliminates almost every pair for the cost of one
 *      number that the directory listing already provided.
 *   2. Within a size group, compare a cheap fingerprint: the head and tail of
 *      each file. Different media of the same size almost always differ in
 *      their first kilobyte, because that is where container headers live.
 *   3. Only for files that survive both, compute a full SHA-256.
 *
 * On a real device this turns "hash forty thousand files" into "hash the
 * dozen that might actually be duplicates".
 */
object Fingerprint {

    private const val SAMPLE = 64 * 1024

    /**
     * A cheap, non-cryptographic fingerprint from the ends of a file.
     *
     * Not proof of equality and never used as such - it is a filter that
     * removes non-duplicates before the expensive step. Files smaller than
     * two samples are read whole, which makes the fingerprint exact for them.
     */
    suspend fun sample(file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val length = file.length()
            if (length == 0L) return@runCatching "empty"
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val head = ByteArray(minOf(SAMPLE.toLong(), length).toInt())
                var read = 0
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n <= 0) break
                    read += n
                }
                digest.update(head, 0, read)
            }
            if (length > SAMPLE * 2L) {
                java.io.RandomAccessFile(file, "r").use { raf ->
                    raf.seek(length - SAMPLE)
                    val tail = ByteArray(SAMPLE)
                    val n = raf.read(tail)
                    if (n > 0) digest.update(tail, 0, n)
                }
            }
            digest.update(length.toString().toByteArray())
            digest.digest().toHex()
        }.getOrNull()
    }

    /** Full content hash. Streamed, cancellable, never loads the file. */
    suspend fun sha256(file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1 shl 18)
            file.inputStream().use { input ->
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n <= 0) break
                    digest.update(buffer, 0, n)
                }
            }
            digest.digest().toHex()
        }.getOrNull()
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            out.append("0123456789abcdef"[v ushr 4])
            out.append("0123456789abcdef"[v and 0x0F])
        }
        return out.toString()
    }
}
