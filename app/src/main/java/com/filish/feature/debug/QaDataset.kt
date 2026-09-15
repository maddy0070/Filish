package com.filish.feature.debug

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

/**
 * Builds the directories the device QA pass needs.
 *
 * ===========================================================================
 * Why this exists
 * ===========================================================================
 *
 * The browse list cannot be validated on a tester's own filesystem alone,
 * because the failure modes that matter are shaped by size distribution:
 * five thousand entries, a wall of tiny files, a handful of enormous ones, a
 * folder whose size resolves late and turns out to be the biggest thing on
 * the device. Nobody has all of those lying around, and asking a tester to
 * create them by hand guarantees they will not be created the same way twice.
 *
 * ===========================================================================
 * Sparse files
 * ===========================================================================
 *
 * Every file here is created with `RandomAccessFile.setLength` rather than by
 * writing bytes, so a 4 GB file reports 4 GB and occupies almost no blocks.
 * The listing pipeline never opens a file - it stats one - so the only thing
 * that has to be real is the size the filesystem reports, and a sparse file
 * reports it exactly.
 *
 * The first attempt at the equivalent JVM test wrote real content and
 * exhausted the disk before it finished. On a phone that would have been a
 * much less funny mistake.
 *
 * NOTE: a sparse file is NOT a real photograph. The media-signature flag
 * cannot be evaluated against this dataset - there is nothing to decode. For
 * that scenario the tester must point FILISH at their own camera roll, which
 * is the honest answer anyway.
 *
 * ===========================================================================
 * Where it writes, and why that is safe
 * ===========================================================================
 *
 * Everything lands under the app's own external files directory, so it needs
 * no storage permission, it is visible in FILISH like any other folder, and
 * uninstalling the app removes all of it. Nothing is written anywhere the user
 * keeps real data.
 *
 * DEBUG BUILDS ONLY. The call sites are inside `if (BuildConfig.DEBUG)`.
 */
object QaDataset {

    const val ROOT = "filish-qa"

    data class Scenario(
        val id: String,
        val title: String,
        /** What this directory is for, shown beside the button. */
        val purpose: String,
    )

    val scenarios = listOf(
        Scenario(
            "01-mixed-5000",
            "5,000 mixed",
            "Scroll, fling, select. The density and frame-rate test.",
        ),
        Scenario(
            "02-tiny",
            "2,000 tiny files",
            "Every mark near the floor. Do they vanish or turn to noise?",
        ),
        Scenario(
            "03-huge",
            "12 huge files",
            "Does one 8 GB file crush everything else out of the channel?",
        ),
        Scenario(
            "04-ugly",
            "Hostile names",
            "Unicode, spaces, brackets, 200 chars, zero bytes, no extension.",
        ),
        Scenario(
            "05-late-largest",
            "Late largest",
            "Small files plus a deep folder that resolves last and is biggest. Watch the spine.",
        ),
        Scenario(
            "06-same-size",
            "40 identical sizes",
            "The mark-fusion test. Adjacent marks must stay separate.",
        ),
        Scenario(
            "07-relative-a",
            "Relative A (tiny neighbours)",
            "Contains a 40 MB file among tiny ones.",
        ),
        Scenario(
            "08-relative-b",
            "Relative B (huge neighbours)",
            "Contains the SAME 40 MB file among huge ones. Compare its mark with A.",
        ),
    )

    fun root(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, ROOT)

    fun directoryFor(context: Context, id: String): File = File(root(context), id)

    private fun sparse(f: File, bytes: Long) {
        f.parentFile?.mkdirs()
        RandomAccessFile(f, "rw").use { it.setLength(bytes) }
    }

    private val ugly = listOf(
        "IMG_20260914_173829.RAW",
        "Screenshot_2026-09-14-23-41-02.png",
        "WhatsApp Image 2024-11-02 at 19.44.07 (1).jpeg",
        "document (47).pdf",
        "a".repeat(200) + ".pdf",
        ".final.final2.REALLYFINAL.docx",
        "VID_20230817_190233.mp4",
        "Договор аренды 2024.pdf",
        "会議メモ.txt",
        "report — final (copy) [2].xlsx",
        "🎉 party pics.zip",
        "no-extension",
        "..oddly.named..",
        "trailing space .txt",
        "UPPER.TXT",
        "file.with.many.dots.tar.gz",
    )

    /** Builds one scenario. Returns the directory, or null if the id is unknown. */
    suspend fun build(context: Context, id: String): File? = withContext(Dispatchers.IO) {
        val dir = directoryFor(context, id)
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        val rnd = Random(20260915)

        when (id) {
            "01-mixed-5000" -> {
                val exts = listOf("jpg", "arw", "mp4", "pdf", "txt", "mp3", "zip", "heic", "png", "json")
                repeat(4_960) { i ->
                    val size = when (rnd.nextInt(100)) {
                        in 0..4 -> 0L
                        in 5..49 -> rnd.nextLong(1, 64_000)
                        in 50..84 -> rnd.nextLong(64_000, 6_000_000)
                        in 85..96 -> rnd.nextLong(6_000_000, 60_000_000)
                        else -> rnd.nextLong(60_000_000, 1_200_000_000)
                    }
                    sparse(File(dir, "file_%05d.%s".format(i, exts[i % exts.size])), size)
                }
                repeat(40) { File(dir, "folder_%02d".format(it)).mkdirs() }
            }

            "02-tiny" -> repeat(2_000) { i ->
                sparse(File(dir, "note_%04d.txt".format(i)), rnd.nextLong(0, 40_000))
            }

            "03-huge" -> {
                val sizes = listOf(
                    8_000_000_000L, 6_400_000_000L, 4_100_000_000L, 3_300_000_000L,
                    2_000_000_000L, 1_500_000_000L, 900_000_000L, 700_000_000L,
                    400_000_000L, 120_000_000L, 4_000_000L, 2_000L,
                )
                sizes.forEachIndexed { i, s -> sparse(File(dir, "huge_%02d.mp4".format(i)), s) }
            }

            "04-ugly" -> ugly.forEachIndexed { i, n ->
                val f = File(dir, n)
                if (!f.exists()) {
                    sparse(f, if (i % 5 == 0) 0L else rnd.nextLong(1, 200_000_000))
                }
            }

            "05-late-largest" -> {
                repeat(120) { i -> sparse(File(dir, "small_%03d.txt".format(i)), rnd.nextLong(1, 400_000)) }
                sparse(File(dir, "known-largest.mp4"), 500_000_000L)
                // A deep tree so its walk is slow and arrives in several parts.
                repeat(60) { d ->
                    repeat(30) { i ->
                        sparse(File(dir, "deep-archive/d$d/f$i.bin"), 2_400_000L)
                    }
                }
            }

            "06-same-size" -> repeat(40) { i ->
                // Identical kind AND identical size: the pair of properties that
                // made marks fuse into one bar before the gap became a token.
                sparse(File(dir, "DSC0%04d.ARW".format(1800 + i)), 51_275_000L)
            }

            "07-relative-a" -> {
                repeat(60) { i -> sparse(File(dir, "tiny_%03d.txt".format(i)), rnd.nextLong(1, 20_000)) }
                sparse(File(dir, "THE-SAME-FILE-40MB.bin"), 40_000_000L)
            }

            "08-relative-b" -> {
                repeat(12) { i -> sparse(File(dir, "huge_%02d.mp4".format(i)), rnd.nextLong(2_000_000_000, 7_000_000_000)) }
                sparse(File(dir, "THE-SAME-FILE-40MB.bin"), 40_000_000L)
            }

            else -> return@withContext null
        }
        dir
    }

    suspend fun clear(context: Context): Boolean = withContext(Dispatchers.IO) {
        root(context).deleteRecursively()
    }
}
