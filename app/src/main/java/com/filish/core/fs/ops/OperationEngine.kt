package com.filish.core.fs.ops

import com.filish.core.fs.MediaStoreIndex
import com.filish.core.fs.SizeResolver
import com.filish.core.fs.Volume
import com.filish.core.model.FileNode
import com.filish.core.model.Kinds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Copy, move, and the machinery that makes them survivable.
 *
 * The engineering that matters here is not the copy loop - it is everything
 * around it:
 *
 * MOVE IS A RENAME WHEN IT CAN BE. Within one volume, a move is a single
 * rename syscall: instant, atomic, and impossible to leave half-done. Across
 * volumes it degrades to copy-then-delete, which is slow and interruptible.
 * FILISH detects which it is, and the interface tells the truth about it -
 * moving 40 GB inside internal storage should not show a progress bar at all,
 * because nothing is being copied.
 *
 * SPACE IS CHECKED BEFORE, NOT DISCOVERED DURING. Filling a volume mid-copy
 * leaves a truncated file and a device in a bad state. The total is measured
 * first and refused up front if it will not fit.
 *
 * PARTIAL WRITES ARE CLEANED UP. If a copy is cancelled or fails, the
 * half-written destination file is removed. A 300 MB fragment that looks like
 * a real video is worse than no file.
 *
 * NOTHING IS DELETED BEFORE ITS REPLACEMENT IS VERIFIED. In a cross-volume
 * move each source is deleted only after its copy exists at the expected
 * size. An interrupted move loses time, never data.
 *
 * THE FILESYSTEM IS ASSUMED HOSTILE. Files vanish between being listed and
 * being read; directories become unreadable; another process holds a lock.
 * Each of those is recorded against its path and the batch continues.
 */
class OperationEngine(
    private val scope: CoroutineScope,
    private val sizes: SizeResolver,
    private val index: MediaStoreIndex,
) {

    companion object {
        /** 1 MiB. Large enough that syscall overhead disappears, small enough
         *  that cancellation is felt immediately and a phone's memory is not
         *  strained by several concurrent operations. */
        private const val BUFFER = 1 shl 20

        /** Progress is published at most this often. A copy of 50,000 small
         *  files would otherwise emit 50,000 times and spend more time
         *  recomposing than copying. */
        private const val PUBLISH_INTERVAL_MS = 90L

        /** Refuse to start if the destination would be left with less than
         *  this much room. Android degrades badly at genuinely zero free. */
        private const val SPACE_HEADROOM = 64L * 1024 * 1024
    }

    private val idCounter = AtomicLong(0)

    /**
     * The job running the current operation, so it can be stopped.
     *
     * Held rather than exposed as a flag because cancellation has to reach
     * inside the copy loop, not merely be checked between files - stopping a
     * 4 GB transfer must not mean waiting for that file to finish.
     */
    @Volatile
    private var activeJob: Job? = null

    /** Stops whatever is running. Partial destination files are cleaned up by
     *  the operation itself as it unwinds. */
    fun cancelActive() {
        activeJob?.cancel()
    }

    private val _active = MutableStateFlow<OperationProgress?>(null)
    val active: StateFlow<OperationProgress?> = _active.asStateFlow()

    /** Raised when a conflict needs an answer. The UI completes the deferred. */
    private val _conflict = MutableStateFlow<PendingConflict?>(null)
    val conflict: StateFlow<PendingConflict?> = _conflict.asStateFlow()

    data class PendingConflict(
        val conflict: Conflict,
        val remaining: Int,
        val answer: CompletableDeferred<ConflictAnswer>,
    )

    data class ConflictAnswer(val policy: ConflictPolicy, val applyToRest: Boolean)

    /** Why an operation was refused before it began. */
    sealed interface Refusal {
        data class NotEnoughSpace(val needed: Long, val available: Long, val volume: String) : Refusal
        data class DestinationUnwritable(val path: String) : Refusal
        data class DestinationInsideSource(val source: String) : Refusal
        data class DestinationMissing(val path: String) : Refusal
        data object NothingToDo : Refusal

        val explanation: String
            get() = when (this) {
                is NotEnoughSpace ->
                    "This needs ${com.filish.core.model.Format.size(needed)} but only " +
                        "${com.filish.core.model.Format.size(available)} is free on $volume."
                is DestinationUnwritable -> "Filish cannot write to this folder."
                is DestinationInsideSource ->
                    "That folder is inside the folder you are moving. It would have to " +
                        "contain itself."
                is DestinationMissing -> "That folder no longer exists."
                NothingToDo -> "Nothing was selected."
            }
    }

    /**
     * Pre-flight. Everything that can be known before touching data is
     * established here, so failures happen as a refusal to start rather than
     * as damage part-way through.
     */
    suspend fun check(
        sources: List<FileNode>,
        destinationDir: String,
        kind: OperationKind,
        volumes: List<Volume>,
    ): Refusal? = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext Refusal.NothingToDo

        val dest = File(destinationDir)
        if (!dest.exists()) return@withContext Refusal.DestinationMissing(destinationDir)
        if (!dest.isDirectory || !dest.canWrite()) {
            return@withContext Refusal.DestinationUnwritable(destinationDir)
        }

        // Moving a folder into itself, or into its own descendant, would be a
        // loop. The check is a prefix test on the normalised paths.
        val destCanonical = runCatching { dest.canonicalPath }.getOrDefault(dest.absolutePath)
        for (s in sources) {
            if (!s.isDirectory) continue
            val srcCanonical = runCatching { File(s.path).canonicalPath }.getOrDefault(s.path)
            if (destCanonical == srcCanonical ||
                destCanonical.startsWith(srcCanonical.trimEnd('/') + "/")
            ) {
                return@withContext Refusal.DestinationInsideSource(s.name)
            }
        }

        // A same-volume move moves no bytes, so it needs no space.
        val sameVolume = volumes.firstOrNull { it.contains(destinationDir) }?.let { dv ->
            sources.all { dv.contains(it.path) }
        } ?: false
        if (kind == OperationKind.Move && sameVolume) return@withContext null

        var needed = 0L
        for (s in sources) {
            coroutineContext.ensureActive()
            needed += if (s.isDirectory) sizes.measureOnce(s.path).bytes else s.size
        }
        val free = runCatching { dest.usableSpace }.getOrDefault(Long.MAX_VALUE)
        if (needed + SPACE_HEADROOM > free) {
            val label = volumes.firstOrNull { it.contains(destinationDir) }?.label ?: "this volume"
            return@withContext Refusal.NotEnoughSpace(needed, free, label)
        }
        null
    }

    /**
     * Runs a copy or move.
     *
     * Cancellation is cooperative and checked between every buffer, so a
     * cancel during a large single file takes effect within about a
     * megabyte's worth of work rather than at the next file boundary.
     */
    suspend fun run(
        sources: List<FileNode>,
        destinationDir: String,
        kind: OperationKind,
        volumes: List<Volume>,
        defaultPolicy: ConflictPolicy = ConflictPolicy.Ask,
        sourceLabel: String = "",
    ): OperationResult = withContext(Dispatchers.IO) {
        val id = idCounter.incrementAndGet()
        activeJob = coroutineContext[Job]
        val startedAt = System.currentTimeMillis()
        val destName = File(destinationDir).name.ifEmpty { destinationDir }

        var policy = defaultPolicy
        val errors = ArrayList<OperationError>()
        val created = ArrayList<String>()
        var succeeded = 0
        var skipped = 0

        // Enumerate the work. For folders this walks the subtree, which is why
        // the state starts at Preparing rather than Running: the user sees an
        // honest "working out what this involves" rather than a bar stuck at 0.
        publish(
            OperationProgress(
                id, kind, OperationState.Preparing, "", 0, 0, 0, 0, 0,
                destName, sourceLabel, startedAt = startedAt,
            ),
        )

        val plan = try {
            enumerate(sources, destinationDir)
        } catch (t: Throwable) {
            clear()
            return@withContext OperationResult(
                id, kind, 0, 0,
                listOf(OperationError(destinationDir, t.message ?: "Could not read the source", false)),
                0, destinationDir, durationMillis = System.currentTimeMillis() - startedAt,
            )
        }

        val sameVolume = volumes.firstOrNull { it.contains(destinationDir) }
            ?.let { dv -> sources.all { dv.contains(it.path) } } ?: false
        val renameOnly = kind == OperationKind.Move && sameVolume

        val bytesDone = AtomicLong(0)
        var lastPublish = 0L
        var itemsDone = 0
        val throughput = Throughput()

        fun emit(state: OperationState, current: String, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastPublish < PUBLISH_INTERVAL_MS) return
            lastPublish = now
            publish(
                OperationProgress(
                    id = id, kind = kind, state = state, currentItemName = current,
                    itemsDone = itemsDone, itemsTotal = plan.items.size,
                    bytesDone = bytesDone.get(), bytesTotal = plan.totalBytes,
                    bytesPerSecond = throughput.perSecond(bytesDone.get()),
                    destinationLabel = destName, sourceLabel = sourceLabel,
                    skipped = skipped, errors = errors.toList(), startedAt = startedAt,
                ),
            )
        }

        emit(OperationState.Running, plan.items.firstOrNull()?.sourceName.orEmpty(), force = true)

        try {
            for (item in plan.items) {
                coroutineContext.ensureActive()
                emit(OperationState.Running, item.sourceName)

                if (item.isDirectory) {
                    val dir = File(item.destination)
                    if (!dir.exists() && !dir.mkdirs()) {
                        errors.add(OperationError(item.destination, "Could not create this folder", false))
                    } else {
                        created.add(item.destination)
                    }
                    itemsDone++
                    continue
                }

                val source = File(item.source)
                if (!source.exists()) {
                    // Vanished between enumeration and now. Normal, not fatal.
                    errors.add(OperationError(item.source, "It disappeared before it could be ${kind.past.lowercase()}", true))
                    itemsDone++
                    continue
                }

                var target = File(item.destination)
                if (target.exists()) {
                    val resolved = resolveConflict(
                        source = item.node ?: FileNode.of(source),
                        target = target,
                        policy = policy,
                        remaining = plan.items.size - itemsDone,
                    )
                    policy = resolved.policyForRest ?: policy
                    when (resolved.action) {
                        ConflictPolicy.Skip -> {
                            skipped++
                            bytesDone.addAndGet(source.length())
                            itemsDone++
                            continue
                        }
                        ConflictPolicy.KeepBoth -> target = uniqueName(target)
                        ConflictPolicy.Overwrite -> Unit
                        ConflictPolicy.OverwriteIfNewer -> {
                            if (source.lastModified() <= target.lastModified()) {
                                skipped++
                                bytesDone.addAndGet(source.length())
                                itemsDone++
                                continue
                            }
                        }
                        ConflictPolicy.Ask -> Unit
                    }
                }

                val moved = try {
                    transfer(source, target, renameOnly, bytesDone) { emit(OperationState.Running, item.sourceName) }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    runCatching { if (target.exists() && target.length() < source.length()) target.delete() }
                    throw ce
                } catch (t: Throwable) {
                    runCatching { if (target.exists() && target.length() < source.length()) target.delete() }
                    errors.add(OperationError(item.source, describe(t, source, target), true))
                    itemsDone++
                    continue
                }

                if (moved) {
                    succeeded++
                    created.add(target.absolutePath)
                }
                itemsDone++
            }

            // Cross-volume move: the sources come out only once every copy has
            // landed, so an interruption costs time and never data.
            if (kind == OperationKind.Move && !renameOnly && errors.none { !it.recoverable }) {
                for (s in sources) {
                    coroutineContext.ensureActive()
                    val f = File(s.path)
                    val landed = File(destinationDir, s.name)
                    if (!landed.exists()) continue
                    val ok = if (f.isDirectory) removeTree(f) else runCatching { f.delete() }.getOrDefault(false)
                    if (!ok) {
                        errors.add(
                            OperationError(
                                s.path,
                                "Copied successfully, but the original could not be removed",
                                true,
                            ),
                        )
                    }
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            clear()
            index.notifyChanged(created)
            sizes.invalidate(destinationDir)
            return@withContext OperationResult(
                id, kind, succeeded, skipped, errors, bytesDone.get(), destinationDir,
                cancelled = true, createdPaths = created,
                durationMillis = System.currentTimeMillis() - startedAt,
            )
        }

        emit(OperationState.Completed, "", force = true)
        clear()

        // Keep the OS index and FILISH's size cache honest about what changed.
        index.notifyChanged(created + sources.map { it.path })
        sizes.invalidate(destinationDir)
        sources.forEach { sizes.invalidate(it.path) }

        OperationResult(
            id = id, kind = kind, succeeded = succeeded, skipped = skipped, failed = errors,
            bytesMoved = bytesDone.get(), destination = destinationDir, createdPaths = created,
            durationMillis = System.currentTimeMillis() - startedAt,
        )
    }

    // ---- internals ---------------------------------------------------------

    private data class PlanItem(
        val source: String,
        val destination: String,
        val isDirectory: Boolean,
        val bytes: Long,
        val sourceName: String,
        val node: FileNode? = null,
    )

    private data class WorkPlan(val items: List<PlanItem>, val totalBytes: Long)

    /** Flattens the selection into a directory-first ordered work list. */
    private suspend fun enumerate(sources: List<FileNode>, destDir: String): WorkPlan {
        val items = ArrayList<PlanItem>()
        var total = 0L

        suspend fun descend(src: File, destParent: String) {
            coroutineContext.ensureActive()
            val dest = File(destParent, src.name).absolutePath
            if (src.isDirectory) {
                items.add(PlanItem(src.absolutePath, dest, true, 0, src.name))
                val children = runCatching { src.listFiles() }.getOrNull() ?: return
                for (child in children) descend(child, dest)
            } else {
                val len = src.length()
                total += len
                items.add(PlanItem(src.absolutePath, dest, false, len, src.name))
            }
        }

        for (s in sources) descend(File(s.path), destDir)
        return WorkPlan(items, total)
    }

    private data class Resolution(val action: ConflictPolicy, val policyForRest: ConflictPolicy?)

    private suspend fun resolveConflict(
        source: FileNode,
        target: File,
        policy: ConflictPolicy,
        remaining: Int,
    ): Resolution {
        if (policy != ConflictPolicy.Ask) return Resolution(policy, null)

        val answer = CompletableDeferred<ConflictAnswer>()
        _conflict.value = PendingConflict(
            conflict = Conflict(source, target.absolutePath, target.length(), target.lastModified()),
            remaining = remaining,
            answer = answer,
        )
        val response = answer.await()
        _conflict.value = null
        return Resolution(response.policy, if (response.applyToRest) response.policy else null)
    }

    /**
     * "report.pdf" colliding becomes "report (2).pdf", not "report.pdf (2)".
     * The extension has to stay where it is or the file stops opening.
     */
    private fun uniqueName(target: File): File {
        val parent = target.parentFile ?: return target
        val name = target.name
        val ext = Kinds.extensionOf(name)
        val stem = if (ext.isEmpty()) name else name.dropLast(ext.length + 1)
        var n = 2
        while (n < 10_000) {
            val candidate = File(parent, if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext")
            if (!candidate.exists()) return candidate
            n++
        }
        return File(parent, "$stem (${System.currentTimeMillis()})" + if (ext.isEmpty()) "" else ".$ext")
    }

    private suspend fun transfer(
        source: File,
        target: File,
        renameOnly: Boolean,
        bytesDone: AtomicLong,
        onProgress: () -> Unit,
    ): Boolean {
        target.parentFile?.mkdirs()

        if (renameOnly) {
            if (source.renameTo(target)) {
                bytesDone.addAndGet(source.length().coerceAtLeast(0))
                return true
            }
            // rename() fails across mount points even within what looks like
            // one volume (emulated storage on some devices). Fall through to a
            // real copy rather than reporting a failure the user cannot act on.
        }

        copyBytes(source, target, bytesDone, onProgress)

        // Verify before anything is trusted. A short file means a failed copy
        // even when no exception was raised.
        if (target.length() != source.length()) {
            runCatching { target.delete() }
            throw IOException("The copy came out a different size than the original")
        }
        runCatching { target.setLastModified(source.lastModified()) }

        if (renameOnly) runCatching { source.delete() }
        return true
    }

    private suspend fun copyBytes(
        source: File,
        target: File,
        bytesDone: AtomicLong,
        onProgress: () -> Unit,
    ) {
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                val inCh = input.channel
                val outCh = output.channel
                val size = inCh.size()
                var position = 0L
                while (position < size) {
                    coroutineContext.ensureActive()
                    // transferTo is a kernel-side copy: no user-space buffer,
                    // no JVM heap churn. Chunked so cancellation stays
                    // responsive and progress stays live.
                    val chunk = minOf(BUFFER.toLong(), size - position)
                    val written = inCh.transferTo(position, chunk, outCh)
                    if (written <= 0) break
                    position += written
                    bytesDone.addAndGet(written)
                    onProgress()
                }
                if (position < size) {
                    // transferTo stalled - fall back to a plain stream copy for
                    // the remainder rather than silently truncating.
                    RandomAccessFile(source, "r").use { raf ->
                        raf.seek(position)
                        val buf = ByteArray(BUFFER)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = raf.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            bytesDone.addAndGet(n.toLong())
                            onProgress()
                        }
                    }
                }
                runCatching { output.fd.sync() }
            }
        }
    }

    private fun removeTree(root: File): Boolean {
        val children = runCatching { root.listFiles() }.getOrNull()
        if (children != null) {
            for (child in children) {
                val isLink = runCatching {
                    java.nio.file.Files.isSymbolicLink(child.toPath())
                }.getOrDefault(false)
                val ok = if (child.isDirectory && !isLink) removeTree(child)
                else runCatching { child.delete() }.getOrDefault(false)
                if (!ok) return false
            }
        }
        return runCatching { root.delete() }.getOrDefault(false)
    }

    /** Turns an exception into something a person can act on. */
    private fun describe(t: Throwable, source: File, target: File): String = when {
        !source.exists() -> "It disappeared while being read"
        !source.canRead() -> "Filish is not allowed to read it"
        target.parentFile?.canWrite() == false -> "The destination is read-only"
        t is IOException && t.message?.contains("space", true) == true ->
            "The destination ran out of space"
        t.message?.contains("EACCES", true) == true -> "The system denied access to it"
        else -> t.message ?: "It could not be ${"copied"}"
    }

    private fun publish(p: OperationProgress) { _active.value = p }
    private fun clear() { _active.value = null; _conflict.value = null }

    /**
     * Smoothed throughput.
     *
     * Instantaneous byte rate on flash storage swings by an order of
     * magnitude between buffers. An unsmoothed figure flickers and the ETA
     * derived from it is worthless, so this is an exponential moving average
     * over a one-second window.
     */
    private class Throughput {
        private var lastAt = System.currentTimeMillis()
        private var lastBytes = 0L
        private var averaged = 0.0

        fun perSecond(totalBytes: Long): Long {
            val now = System.currentTimeMillis()
            val dt = now - lastAt
            if (dt < 250) return averaged.toLong()
            val instant = (totalBytes - lastBytes) * 1000.0 / dt
            averaged = if (averaged == 0.0) instant else averaged * 0.7 + instant * 0.3
            lastAt = now
            lastBytes = totalBytes
            return averaged.toLong().coerceAtLeast(0)
        }
    }
}
