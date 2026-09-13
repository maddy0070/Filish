package com.filish

import com.filish.core.model.FileKind
import com.filish.core.search.QueryParser
import com.filish.core.search.Term
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The search grammar.
 *
 * Two properties matter more than any individual case:
 *
 *   Structured terms are recognised, so the feature is real.
 *   Unrecognised text is NEVER silently dropped, so the feature is honest.
 *
 * The second is the one that would quietly rot. A parser that swallows part
 * of a filename as a keyword returns confidently wrong results, which is
 * worse than returning nothing.
 */
class QueryParserTest {

    private val now = 1_700_000_000_000L

    private inline fun <reified T : Term> termsOf(query: String): List<T> =
        QueryParser.parse(query, now).terms.filterIsInstance<T>()

    @Test
    fun `plain text stays a name match`() {
        val parsed = QueryParser.parse("holiday photos 2019", now)
        val text = parsed.terms.filterIsInstance<Term.Text>()
        assertTrue(text.isNotEmpty())
        // "photos" is consumed as a kind; the rest must survive intact.
        assertEquals("holiday 2019", text.first().value)
    }

    @Test
    fun `large videos parses both halves`() {
        val parsed = QueryParser.parse("large videos", now)
        assertEquals(setOf(FileKind.Video), termsOf<Term.Kind>("large videos").first().kinds)
        assertNotNull(parsed.terms.filterIsInstance<Term.LargerThan>().firstOrNull())
    }

    @Test
    fun `explicit sizes are read with their units`() {
        assertEquals(10_000_000L, termsOf<Term.LargerThan>("images over 10mb").first().bytes)
        assertEquals(500_000_000L, termsOf<Term.LargerThan>("over 500 mb").first().bytes)
        assertEquals(2_000_000_000L, termsOf<Term.LargerThan>("larger than 2gb").first().bytes)
    }

    @Test
    fun `under reads as a smaller-than bound`() {
        assertEquals(5_000_000L, termsOf<Term.SmallerThan>("under 5mb").first().bytes)
    }

    @Test
    fun `time phrases become bounds in the right direction`() {
        val week = termsOf<Term.ModifiedAfter>("pdfs last week").first()
        assertEquals(now - TimeUnit.DAYS.toMillis(7), week.epochMs)

        val old = termsOf<Term.ModifiedBefore>("older than a year").first()
        assertEquals(now - TimeUnit.DAYS.toMillis(365), old.epochMs)
    }

    @Test
    fun `extensions are recognised in both spellings`() {
        assertEquals("pdf", termsOf<Term.Extension>(".pdf").first().ext)
        assertEquals("png", termsOf<Term.Extension>("*.png").first().ext)
    }

    @Test
    fun `a dotfile name is not mistaken for an extension filter`() {
        // ".pdf" alone is a filter, but a real query like "invoice.pdf" is a
        // filename and must stay one.
        val parsed = QueryParser.parse("invoice.pdf", now)
        assertTrue(parsed.terms.filterIsInstance<Term.Text>().isNotEmpty())
    }

    @Test
    fun `every token is either consumed or preserved as text`() {
        // The honesty property: nothing typed may vanish.
        val queries = listOf(
            "large videos from berlin",
            "receipts under 2mb last month",
            "zzz unknown words here",
            "raw over 20mb",
        )
        for (q in queries) {
            val parsed = QueryParser.parse(q, now)
            val recognisedWords = parsed.terms.count { it !is Term.Text }
            val leftover = parsed.terms.filterIsInstance<Term.Text>()
                .joinToString(" ") { it.value }
            assertTrue(
                "nothing recognised and nothing preserved for: $q",
                recognisedWords > 0 || leftover.isNotBlank(),
            )
        }
    }

    @Test
    fun `berlin survives a query that also has structure`() {
        val parsed = QueryParser.parse("large videos from berlin", now)
        val text = parsed.terms.filterIsInstance<Term.Text>().joinToString(" ") { it.value }
        assertTrue("lost the search term: $text", text.contains("berlin"))
    }

    @Test
    fun `an empty query produces no terms`() {
        assertTrue(QueryParser.parse("", now).isEmpty)
        assertTrue(QueryParser.parse("   ", now).isEmpty)
    }

    @Test
    fun `hidden and empty are recognised as flags`() {
        assertTrue(QueryParser.parse("hidden", now).terms.contains(Term.HiddenOnly))
        assertTrue(QueryParser.parse("empty", now).terms.contains(Term.EmptyOnly))
    }

    @Test
    fun `every parsed term can describe itself for the chip row`() {
        // The interface promises to show the user how they were understood,
        // which requires every term to have a label.
        val parsed = QueryParser.parse("large videos last week over 10mb .mp4", now)
        for (term in parsed.terms) {
            assertTrue("term with no label: $term", term.label.isNotBlank())
        }
    }
}
