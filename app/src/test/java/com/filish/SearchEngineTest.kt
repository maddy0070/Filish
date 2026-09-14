package com.filish

import com.filish.core.model.FileKind
import com.filish.core.search.ParsedQuery
import com.filish.core.search.QueryParser
import com.filish.core.search.SearchEngine
import com.filish.core.search.Term
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Search over a real tree.
 *
 * Covers the path storage findings depend on: a synthesised query built from
 * a finding's criteria has to return the files that finding counted, or the
 * investigation screen is a dead end with extra steps.
 */
class SearchEngineTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val engine = SearchEngine()

    private fun write(path: String, size: Int, ageDays: Long = 1): File {
        val f = File(temp.root, path)
        f.parentFile?.mkdirs()
        f.writeBytes(ByteArray(size))
        f.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ageDays))
        return f
    }

    private fun run(query: ParsedQuery, hidden: Boolean = false) = runBlocking {
        engine.search(listOf(temp.root.absolutePath), query, hidden).last()
    }

    @Test
    fun `a kind query finds only that kind, at any depth`() {
        write("DCIM/Raw/DSC01841.ARW", 1000)
        write("DCIM/Raw/DSC01842.ARW", 1000)
        write("DCIM/Camera/IMG_0001.jpg", 1000)
        write("Documents/notes.txt", 100)

        val result = run(ParsedQuery(listOf(Term.Kind(setOf(FileKind.RawImage), "raw")), "raw"))
        assertEquals(2, result.results.size)
        assertTrue(result.results.all { it.extension == "arw" })
    }

    @Test
    fun `a size floor excludes smaller files but keeps folders out of the way`() {
        write("big.bin", 2_000_000)
        write("small.bin", 1_000)

        val result = run(ParsedQuery(listOf(Term.LargerThan(1_000_000, "1 MB")), "large"))
        assertEquals(1, result.results.size)
        assertEquals("big.bin", result.results.single().name)
    }

    @Test
    fun `an age ceiling finds only older files`() {
        write("recent.bin", 500_000, ageDays = 2)
        write("ancient.bin", 500_000, ageDays = 500)

        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365)
        val result = run(ParsedQuery(listOf(Term.ModifiedBefore(cutoff, "old")), "old"))
        assertEquals(1, result.results.size)
        assertEquals("ancient.bin", result.results.single().name)
    }

    @Test
    fun `combined criteria intersect rather than union`() {
        write("Movies/big-old.mp4", 3_000_000, ageDays = 500)
        write("Movies/big-new.mp4", 3_000_000, ageDays = 2)
        write("Movies/small-old.mp4", 1_000, ageDays = 500)
        write("Documents/big-old.pdf", 3_000_000, ageDays = 500)

        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365)
        val result = run(
            ParsedQuery(
                listOf(
                    Term.Kind(setOf(FileKind.Video), "videos"),
                    Term.LargerThan(1_000_000, "1 MB"),
                    Term.ModifiedBefore(cutoff, "old"),
                ),
                "large old videos",
            ),
        )
        assertEquals(1, result.results.size)
        assertEquals("big-old.mp4", result.results.single().name)
    }

    @Test
    fun `a typed query works end to end from text to results`() {
        // The parser and the engine have to agree; testing them apart would
        // let a mismatch between them pass unnoticed.
        write("Movies/holiday.mp4", 200_000_000)
        write("Movies/short.mp4", 500)
        write("Music/song.mp3", 200_000_000)

        val result = run(QueryParser.parse("large videos"))
        assertEquals(1, result.results.size)
        assertEquals("holiday.mp4", result.results.single().name)
    }

    @Test
    fun `name search is partial and case-insensitive`() {
        write("Documents/Invoice April 2024.pdf", 1000)
        write("Documents/receipt.pdf", 1000)

        val result = run(QueryParser.parse("invoice"))
        assertEquals(1, result.results.size)
    }

    @Test
    fun `hidden files stay out unless asked for`() {
        write(".secret/hidden.arw", 1000)
        val query = ParsedQuery(listOf(Term.Kind(setOf(FileKind.RawImage), "raw")), "raw")
        assertTrue(run(query).results.isEmpty())
        assertEquals(1, run(query, hidden = true).results.size)
    }

    @Test
    fun `an empty query returns nothing rather than everything`() {
        write("a.txt", 100)
        write("b.txt", 100)
        assertTrue(run(ParsedQuery(emptyList(), "")).results.isEmpty())
    }

    @Test
    fun `results are marked complete when the walk finishes`() {
        write("x.txt", 100)
        assertTrue(run(QueryParser.parse("x")).complete)
    }
}
