package com.filish.core.search

import com.filish.core.model.FileKind
import java.util.concurrent.TimeUnit

/**
 * FILISH's search language.
 *
 * The obvious design is a text box that matches filenames. That is what
 * almost every file manager ships, and it is nearly useless, because the
 * things people actually want to find are not describable by name: "the big
 * videos", "what did I download last week", "photos over 10 MB". Users
 * already know the properties of the file they are hunting for; the name is
 * usually the one property they have forgotten.
 *
 * The other obvious design is natural-language search backed by a model. That
 * would be a lie here - FILISH has no network, and pretending to understand
 * arbitrary English while actually matching three keywords is worse than not
 * offering it.
 *
 * So: a small, honest, *deterministic* grammar that covers the real questions,
 * accepts the phrasing people naturally reach for, and - critically - shows
 * the user exactly how it understood them. Every parsed query renders back as
 * a set of visible, individually removable terms. If FILISH misreads
 * "large videos from last month", the user can see which part it got wrong
 * and correct that part, instead of rephrasing blindly.
 *
 * Anything not recognised as a term stays a literal name match. Nothing is
 * silently dropped.
 */
sealed interface Term {
    /** The chip text shown back to the user. */
    val label: String

    data class Text(val value: String) : Term {
        override val label get() = "\"$value\""
    }

    data class Kind(val kinds: Set<FileKind>, val spoken: String) : Term {
        override val label get() = spoken
    }

    data class LargerThan(val bytes: Long, val spoken: String) : Term {
        override val label get() = "larger than $spoken"
    }

    data class SmallerThan(val bytes: Long, val spoken: String) : Term {
        override val label get() = "smaller than $spoken"
    }

    data class ModifiedAfter(val epochMs: Long, val spoken: String) : Term {
        override val label get() = spoken
    }

    data class ModifiedBefore(val epochMs: Long, val spoken: String) : Term {
        override val label get() = spoken
    }

    data class Extension(val ext: String) : Term {
        override val label get() = ".$ext"
    }

    data object HiddenOnly : Term {
        override val label get() = "hidden"
    }

    data object EmptyOnly : Term {
        override val label get() = "empty"
    }
}

data class ParsedQuery(
    val terms: List<Term>,
    val raw: String,
) {
    val textTerms: List<String> get() = terms.filterIsInstance<Term.Text>().map { it.value }
    val isEmpty: Boolean get() = terms.isEmpty()

    /** True when FILISH understood something beyond plain text - used to show
     *  the user that their phrasing did something. */
    val hasStructure: Boolean get() = terms.any { it !is Term.Text }
}

/**
 * Turns typed text into terms.
 *
 * Deliberately conservative. A phrase is only consumed as a structured term
 * when it matches a known pattern exactly; everything else falls through to
 * name matching. Over-eager parsing that swallows part of a filename as a
 * "keyword" is the failure mode that makes this kind of feature untrustworthy.
 */
object QueryParser {

    private val sizeUnits = mapOf(
        "b" to 1L, "kb" to 1_000L, "k" to 1_000L, "mb" to 1_000_000L, "m" to 1_000_000L,
        "gb" to 1_000_000_000L, "g" to 1_000_000_000L, "tb" to 1_000_000_000_000L,
    )

    private val kindWords: List<Pair<Set<String>, Pair<Set<FileKind>, String>>> = listOf(
        setOf("image", "images", "photo", "photos", "picture", "pictures") to
            (setOf(FileKind.Image) to "images"),
        setOf("raw", "raws") to (setOf(FileKind.RawImage) to "RAW images"),
        setOf("video", "videos", "movie", "movies", "clip", "clips") to
            (setOf(FileKind.Video) to "videos"),
        setOf("audio", "music", "song", "songs", "track", "tracks", "sound", "sounds") to
            (setOf(FileKind.Audio) to "audio"),
        setOf("document", "documents", "doc", "docs") to
            (setOf(FileKind.Document, FileKind.Pdf, FileKind.Text) to "documents"),
        setOf("pdf", "pdfs") to (setOf(FileKind.Pdf) to "PDFs"),
        setOf("archive", "archives", "zip", "zips") to (setOf(FileKind.Archive) to "archives"),
        setOf("apk", "apks", "app", "apps") to (setOf(FileKind.App) to "app packages"),
        setOf("folder", "folders", "directory", "directories") to
            (setOf(FileKind.Folder) to "folders"),
        setOf("code") to (setOf(FileKind.Code) to "code"),
    )

    /** "large"/"big"/"huge" need a threshold. These are judgement calls, and
     *  they are stated in the chip so the user can see what FILISH assumed. */
    private const val LARGE = 100L * 1_000_000
    private const val HUGE = 1_000L * 1_000_000
    private const val SMALL = 1L * 1_000_000

    fun parse(input: String, now: Long = System.currentTimeMillis()): ParsedQuery {
        val text = input.trim()
        if (text.isEmpty()) return ParsedQuery(emptyList(), input)

        val terms = ArrayList<Term>()
        val tokens = text.split(Regex("\\s+"))
        val consumed = BooleanArray(tokens.size)

        fun take(vararg idx: Int) { idx.forEach { if (it in tokens.indices) consumed[it] = true } }

        for (i in tokens.indices) {
            if (consumed[i]) continue
            // Only TRAILING punctuation is shed. Stripping a leading dot
            // would turn the extension filter ".pdf" into the type word
            // "pdf", which is a different query with different results.
            val t = tokens[i].lowercase().trimEnd('.', ',', '!', '?')
            val next = tokens.getOrNull(i + 1)?.lowercase()?.trimEnd('.', ',')
            val nextNext = tokens.getOrNull(i + 2)?.lowercase()?.trimEnd('.', ',')

            // ">10mb", "over 500 mb", "larger than 1gb"
            val explicitSize = parseSize(t)
            if (explicitSize != null && (t.startsWith(">") || t.startsWith("<"))) {
                if (t.startsWith(">")) terms.add(Term.LargerThan(explicitSize.first, explicitSize.second))
                else terms.add(Term.SmallerThan(explicitSize.first, explicitSize.second))
                take(i)
                continue
            }

            if (t in setOf("over", "above", "bigger", "larger", "more") ||
                (t == "greater" && next == "than")
            ) {
                val at = if (next == "than") i + 2 else i + 1
                val joined = joinSize(tokens, at)
                if (joined != null) {
                    terms.add(Term.LargerThan(joined.first, joined.second))
                    take(i, i + 1, at, joined.third)
                    continue
                }
            }

            if (t in setOf("under", "below", "smaller", "less") ||
                (t == "fewer" && next == "than")
            ) {
                val at = if (next == "than") i + 2 else i + 1
                val joined = joinSize(tokens, at)
                if (joined != null) {
                    terms.add(Term.SmallerThan(joined.first, joined.second))
                    take(i, i + 1, at, joined.third)
                    continue
                }
            }

            when (t) {
                "large", "big" -> { terms.add(Term.LargerThan(LARGE, "100 MB")); take(i); continue }
                "huge", "enormous", "massive" -> { terms.add(Term.LargerThan(HUGE, "1 GB")); take(i); continue }
                "small", "tiny" -> { terms.add(Term.SmallerThan(SMALL, "1 MB")); take(i); continue }
                "hidden" -> { terms.add(Term.HiddenOnly); take(i); continue }
                "empty" -> { terms.add(Term.EmptyOnly); take(i); continue }
            }

            // "today", "yesterday", "this week", "last month", "older than a year"
            val time = parseTime(t, next, nextNext, tokens.getOrNull(i + 3)?.lowercase(), now)
            if (time != null) {
                terms.add(time.first)
                for (k in 0 until time.second) take(i + k)
                continue
            }

            // ".pdf" or "*.pdf"
            if (t.startsWith(".") && t.length > 1 && !t.contains('/')) {
                terms.add(Term.Extension(t.drop(1))); take(i); continue
            }
            if (t.startsWith("*.") && t.length > 2) {
                terms.add(Term.Extension(t.drop(2))); take(i); continue
            }

            // A type word only counts when it is not the whole query - "video"
            // alone might genuinely be a folder called "video".
            val kind = kindWords.firstOrNull { t in it.first }
            if (kind != null) {
                terms.add(Term.Kind(kind.second.first, kind.second.second))
                take(i)
                continue
            }
        }

        // Everything unconsumed is a literal name fragment, rejoined so
        // "holiday photos 2019" still matches "holiday 2019" as one phrase.
        val leftover = tokens.filterIndexed { i, _ -> !consumed[i] }
            .joinToString(" ").trim()
        if (leftover.isNotEmpty()) terms.add(0, Term.Text(leftover))

        return ParsedQuery(terms, input)
    }

    private fun parseSize(token: String): Pair<Long, String>? {
        val cleaned = token.removePrefix(">").removePrefix("<").removePrefix("=")
        val m = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*([a-z]+)$").find(cleaned) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = sizeUnits[m.groupValues[2]] ?: return null
        val bytes = (value * unit).toLong()
        return bytes to "${m.groupValues[1]} ${m.groupValues[2].uppercase()}"
    }

    /** Handles "500 mb" split across two tokens as well as "500mb" in one. */
    private fun joinSize(tokens: List<String>, at: Int): Triple<Long, String, Int>? {
        if (at !in tokens.indices) return null
        parseSize(tokens[at].lowercase())?.let { return Triple(it.first, it.second, at) }
        val two = tokens.getOrNull(at + 1) ?: return null
        parseSize((tokens[at] + two).lowercase())?.let { return Triple(it.first, it.second, at + 1) }
        return null
    }

    private val ARTICLES = setOf("a", "an", "one", "the")

    private fun durationDays(unit: String?): Long? = when (unit) {
        "day", "days" -> 1L
        "week", "weeks" -> 7L
        "month", "months" -> 30L
        "year", "years" -> 365L
        else -> null
    }

    private fun parseTime(
        t: String,
        next: String?,
        nextNext: String?,
        fourth: String?,
        now: Long,
    ): Pair<Term, Int>? {
        fun daysAgo(d: Long) = now - TimeUnit.DAYS.toMillis(d)
        return when {
            t == "today" -> Term.ModifiedAfter(startOfDay(now), "today") to 1
            t == "yesterday" -> Term.ModifiedAfter(startOfDay(now) - TimeUnit.DAYS.toMillis(1), "since yesterday") to 1
            t == "recent" || t == "recently" -> Term.ModifiedAfter(daysAgo(7), "in the last week") to 1
            (t == "this" || t == "last" || t == "past") && next != null -> {
                val d = durationDays(next)
                if (d == null) {
                    null
                } else {
                    val spoken = if (d == 1L) "in the last day" else "in the last ${next.trimEnd('s')}"
                    Term.ModifiedAfter(daysAgo(d), spoken) to 2
                }
            }
            t == "old" || t == "older" -> {
                if (next == "than" && nextNext != null) {
                    // People write "older than a year", not "older than year".
                    // The article has to be stepped over or the unit is never
                    // reached and the whole phrase silently becomes a name.
                    val skipped = if (nextNext in ARTICLES) 1 else 0
                    val unit = if (skipped == 1) fourth else nextNext
                    val d = durationDays(unit)
                    if (d != null) {
                        Term.ModifiedBefore(daysAgo(d), "older than a $unit") to (3 + skipped)
                    } else {
                        null
                    }
                } else {
                    Term.ModifiedBefore(daysAgo(365), "older than a year") to 1
                }
            }
            else -> null
        }
    }

    private fun startOfDay(now: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Examples surfaced in the empty search state. Teaching by showing what
     *  works beats a help screen nobody opens. */
    val examples = listOf(
        "large videos",
        "pdfs last month",
        "images over 10mb",
        "screenshots this week",
        "older than a year",
        "apk",
    )
}
