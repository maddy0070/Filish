package com.filish

import com.filish.core.fs.DirectoryLister
import com.filish.core.fs.Listing
import com.filish.design.glass.Mass
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

/**
 * The mandated 5,000-file test, against the real pipeline.
 *
 * ===========================================================================
 * What this proves, and what it does not
 * ===========================================================================
 *
 * PROVES: five thousand real files are created on a real filesystem, listed by
 * the production [DirectoryLister], and turned into spine geometry by the
 * production [Mass] code. Listing cost, scale stability, mark bounds and
 * ordering are all measured on real data rather than reasoned about.
 *
 * DOES NOT PROVE: scrolling frame rate, thumbnail decode cost, or memory on a
 * phone. This is a JVM test; there is no device and no emulator in this
 * environment (no KVM), so no claim about frames is made anywhere in this
 * file. What it does catch is the class of failure that would make those
 * numbers hopeless before a device ever sees them - an accidental O(n^2), a
 * rescale per file, a mark that inverts.
 *
 * Files are created SPARSE - `RandomAccessFile.setLength` rather than writing
 * bytes. The first attempt wrote real content and exhausted the disk before it
 * finished, which is its own small lesson: the listing pipeline never opens a
 * file, it stats one, so the only thing that has to be real is the size the
 * filesystem reports. A sparse file reports it exactly.
 *
 * The dataset is deliberately hostile, per the ugly-filesystem requirement:
 * names with spaces, brackets, dots, emoji, CJK, a 200-character name, files
 * that differ only by case, sizes spanning eleven orders of magnitude, and
 * empty files.
 */
class LargeDirectoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val uglyNames = listOf(
        "IMG_20260914_173829.RAW",
        "Screenshot_2026-09-14-23-41-02.png",
        "WhatsApp Image 2024-11-02 at 19.44.07 (1).jpeg",
        "document (47).pdf",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.pdf",
        ".final.final2.REALLYFINAL.docx",
        "VID_20230817_190233.mp4",
        "Договор аренды 2024.pdf",
        "会議メモ.txt",
        "report — final (copy) [2].xlsx",
        "🎉 party pics.zip",
        "a".repeat(200) + ".bin",
        "CASE.txt",
        "case.txt",
        "no-extension",
        "..oddly.named..",
    )

    private fun buildDirectory(count: Int): File {
        val dir = temp.newFolder("bulk")
        val rnd = Random(20260915)
        // Eleven orders of magnitude, weighted the way a camera folder really
        // is: mostly small, a few enormous.
        fun size(): Int = when (rnd.nextInt(100)) {
            in 0..4 -> 0
            in 5..49 -> rnd.nextInt(1, 64_000)
            in 50..84 -> rnd.nextInt(64_000, 6_000_000)
            in 85..96 -> rnd.nextInt(6_000_000, 60_000_000)
            else -> rnd.nextInt(60_000_000, 120_000_000)
        }

        fun make(f: File, bytes: Long) {
            RandomAccessFile(f, "rw").use { it.setLength(bytes) }
        }

        uglyNames.forEachIndexed { i, n ->
            // Case-only duplicates cannot coexist on a case-insensitive FS;
            // skipping the collision is correct rather than failing the test.
            val f = File(dir, n)
            if (!f.exists()) make(f, if (i == 0) 94_000_000L else size().toLong())
        }
        val exts = listOf("jpg", "arw", "mp4", "pdf", "txt", "mp3", "zip", "heic", "png", "json")
        var i = 0
        while ((dir.list()?.size ?: 0) < count) {
            val ext = exts[i % exts.size]
            make(File(dir, "file_%05d.%s".format(i, ext)), size().toLong())
            i++
            if (i > count * 2) break
        }
        repeat(40) { File(dir, "folder_%03d".format(it)).mkdirs() }
        return dir
    }

    @Test
    fun `five thousand real files list and scale correctly`() = runBlocking {
        val dir = buildDirectory(5_000)
        val onDisk = dir.list()?.size ?: 0
        assertTrue("only built $onDisk entries", onDisk >= 5_000)

        val listStart = System.nanoTime()
        val listing = DirectoryLister().list(dir.absolutePath)
        val listMs = (System.nanoTime() - listStart) / 1_000_000

        val content = listing as? Listing.Content
            ?: throw AssertionError("listing failed: $listing")
        assertEquals(onDisk, content.entries.size)

        // The spine's geometry for the whole directory, from production code.
        val scaleStart = System.nanoTime()
        val scale = Mass.scaleFor(content.entries.map { if (it.isDirectory) 0L else it.size })
        val widths = content.entries.map { Mass.markWidth(if (it.isDirectory) 0L else it.size, scale) }
        val scaleMs = (System.nanoTime() - scaleStart) / 1_000_000

        println(
            "LARGE DIRECTORY: ${content.entries.size} entries · " +
                "list ${listMs}ms · spine geometry ${scaleMs}ms",
        )

        // Every mark is inside its channel, and nothing vanished.
        for (w in widths) {
            assertTrue("mark ${w} below the floor", w >= Mass.markMin)
            assertTrue("mark ${w} above the ceiling", w <= Mass.markMax)
        }

        // Computing the whole spine must be trivially cheap next to the
        // listing itself. If this ever inverts, something became O(n^2).
        assertTrue(
            "spine geometry took ${scaleMs}ms for ${content.entries.size} entries",
            scaleMs < 250,
        )
    }

    @Test
    fun `a bigger file never draws a narrower mark across a real directory`() = runBlocking {
        val dir = buildDirectory(1_200)
        val content = DirectoryLister().list(dir.absolutePath) as Listing.Content
        val files = content.entries.filterNot { it.isDirectory }.sortedBy { it.size }
        val scale = Mass.scaleFor(files.map { it.size })

        var previous = 0f
        for (f in files) {
            val w = Mass.markWidth(f.size, scale).value
            assertTrue(
                "${f.name} (${f.size} B) drew ${w}dp after a smaller file drew ${previous}dp",
                w >= previous - 0.001f,
            )
            previous = w
        }
    }

    /**
     * Ugly names must not break the geometry. They cannot, because the mark
     * never reads the name - but this pins that, because the day someone
     * derives a tint from a filename hash it will stop being true.
     */
    @Test
    fun `hostile filenames do not affect the spine`() = runBlocking {
        val dir = buildDirectory(200)
        val content = DirectoryLister().list(dir.absolutePath) as Listing.Content
        val byName = content.entries.associateBy { it.name }
        val scale = Mass.scaleFor(content.entries.map { if (it.isDirectory) 0L else it.size })

        for (n in uglyNames) {
            val node = byName[n] ?: continue
            val w = Mass.markWidth(node.size, scale)
            assertTrue("$n produced an out-of-range mark", w >= Mass.markMin && w <= Mass.markMax)
        }
        // The 200-character name is a real entry with a real mark.
        val monster = byName.keys.firstOrNull { it.length > 150 }
        assertTrue("the 200-character name did not survive listing", monster != null)
    }
}
