package com.filish.design.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ln

/**
 * MASS — magnitude as a visual channel.
 *
 * ===========================================================================
 * The idea
 * ===========================================================================
 *
 * Size is the quantity a file manager is fundamentally about. It is why people
 * open one: something is full, something is too big, something is duplicated.
 * And every file manager in existence renders that quantity as a string of
 * text, which means the user has to read fourteen numbers and compare them in
 * their head to answer "what is taking up the room here".
 *
 * So FILISH gives magnitude a visual channel. The leading mark on every object
 * is as wide as the object is big. The left edge of a directory then becomes a
 * PROFILE of that directory - a size histogram you read in one glance, for
 * free, in gutter space that was empty anyway.
 *
 * ===========================================================================
 * Why width and not height
 * ===========================================================================
 *
 * The exploration tried height first (Direction I, "core sample"): each row as
 * tall as its file is big. It worked as information - you could see which
 * files mattered without reading a figure - and it was unusable as an
 * interface. Six files filled a phone screen. Vertical space is the one
 * resource a mobile file manager cannot spend, and a 500-file directory would
 * have been a scrolling nightmare.
 *
 * Width costs nothing. Rows stay uniform, so scanning and scrolling are
 * untouched, and the channel is carved out of a margin.
 *
 * ===========================================================================
 * Why relative and not absolute
 * ===========================================================================
 *
 * The first scale ran from 1 KB to 64 GB absolute. The middle of every real
 * directory went flat: 418 MB and 51 MB landed a pixel apart, and so did
 * 2.2 MB and 184 KB. Twenty-six doublings across thirteen dp is half a dp per
 * doubling, which is nothing.
 *
 * Scaling against the largest item in the current listing spends the whole
 * channel on the spread actually present. It also matches the question people
 * ask, which is never "how big is this in the abstract" but "what is taking up
 * the room in HERE".
 *
 * The cost is that the same file looks different in different folders. That is
 * acceptable because of the rule below.
 *
 * ===========================================================================
 * MATERIAL SHOWS MAGNITUDE; INK STATES IT
 * ===========================================================================
 *
 * The mark is for the glance. The figure in the metadata line is for the
 * answer, and it is always present. The mark is never the only place a size
 * appears, so a user who cannot perceive the width difference - or who is
 * comparing across folders - loses nothing but a shortcut.
 *
 * This is the same shape as the V3 rule that information is never made of
 * glass: the material may carry a hint, never the fact.
 */
@Immutable
object Mass {

    /**
     * How many doublings below the largest item reach the floor.
     *
     * 16 doublings is 65,536x. Anything smaller than that fraction of the
     * biggest thing in the folder is genuinely negligible there, and saying so
     * is more useful than spending channel on it.
     */
    const val SPAN = 16.0

    /**
     * THE SPINE.
     *
     * The channel the marks live in, and the distance from the frame to it.
     * Text starts at [textInset] always, so a ragged spine never produces a
     * ragged text column.
     *
     * The channel widened from 18dp to 26dp in V3.2. At 16dp the marks read as
     * coloured tabs stuck to rows - a labelling convention, and a borrowed one.
     * At 26dp, detached from the text with substrate visible between, they
     * read as a continuous profile of the folder: a landscape legible from
     * across the room. That is the difference between a design system and an
     * art direction, and it costs 8dp of a 411dp screen.
     */
    val gutter: Dp = 18.dp
    val channel: Dp = 26.dp
    val textInset: Dp = 60.dp

    /**
     * The gap above and below each mark.
     *
     * LOAD-BEARING. The remove-30% pass rendered the spine without gaps and
     * four consecutive RAW files of near-identical size fused into a single
     * continuous orange bar - as did the two folders, and the three HEICs.
     * Runs of same-kind, same-size files are not an edge case; they are burst
     * photography, screenshots and exports, which is most of a camera folder.
     *
     * This replaces V3.1's cut hairline. With no row body there is nothing to
     * cut, so separation moves into the spine, where it also makes the profile
     * more graphic rather than less.
     */
    val gap: Dp = 2.dp

    /**
     * Floor. Nothing is ever invisible - a 0-byte file is still an object.
     *
     * Raised from 3dp: at 3dp on a dark ground the smallest marks were a faint
     * hairline that read as an artefact rather than as an object.
     */
    val markMin: Dp = 4.dp

    /** Ceiling. Wider than this and the mark stops being an edge and starts
     *  being a panel. */
    val markMax: Dp = 26.dp

    /**
     * Magnitude as 0..1, relative to the largest item in the same listing.
     *
     * Log2 rather than linear: a 12 KB file beside a 2 GB folder is 0.0006% of
     * it, so a linear mark would make every small file literally invisible.
     * Doublings are also how people actually reason about file size - "twice
     * as big", not "180 megabytes bigger".
     */
    fun relative(bytes: Long, largest: Long): Float {
        // Nothing to compare against - an empty directory, or one where no
        // size is known yet. Every mark sits at its floor, which is what "no
        // claim" looks like.
        //
        // The <= 1 rather than <= 0 is a real bug fix: quantiseLargest floors
        // at 1, and log2 clamps its input to 1, so a zero-byte file against a
        // scale of 1 computed a drop of zero doublings - i.e. "this file is
        // the largest thing here" - and drew at FULL WIDTH. An empty folder
        // rendered as a wall of maximum marks.
        if (largest <= 1L) return 0f
        val drop = log2(largest) - log2(bytes)
        return ((1.0 - drop / SPAN).coerceIn(0.0, 1.0)).toFloat()
    }

    /** The drawn width of the mark. */
    fun markWidth(bytes: Long, largest: Long): Dp =
        markMin + (markMax - markMin) * relative(bytes, largest)

    /**
     * The mark for an object whose size is not known yet.
     *
     * Folder sizes stream in - SizeResolver emits partial totals and flips a
     * `settled` flag when the walk completes - so a folder's mark starts at the
     * floor and GROWS as the measurement converges. That growth is not a
     * loading animation bolted on; it is the measurement itself, made visible.
     * It is also the one place in a directory listing where something is
     * allowed to be moving, and it stops the moment the figure settles, which
     * is the clearest possible signal that it settled.
     *
     * Until then the mark is drawn at reduced intensity, so a provisional
     * quantity never looks like a known one.
     */
    const val PROVISIONAL_INTENSITY = 0.45f

    /**
     * Quantise the reference size before handing it to [relative].
     *
     * THIS IS A CORRECTNESS REQUIREMENT, NOT AN OPTIMISATION, and it is the
     * sharpest edge in the whole art direction.
     *
     * Every mark is scaled against the largest item in the listing. Folder
     * sizes stream in, so that largest value changes repeatedly while a
     * directory is being measured - and each change rescales EVERY mark on
     * screen. Two things go wrong at once: the entire left edge of the list
     * jitters while the user is trying to read it, and every row's cached
     * draw state is invalidated on every update, which is the one way this
     * otherwise almost-free material could cost real frames.
     *
     * Snapping the reference to whole doublings fixes both. The scale then
     * changes at most a couple of dozen times over the life of any listing,
     * each change is a real change in what is being shown, and a mark that
     * has settled stops moving.
     *
     * Call this once per listing, not once per row.
     */
    fun quantiseLargest(bytes: Long): Long {
        if (bytes <= 1L) return 1L
        val doublings = kotlin.math.ceil(log2(bytes)).toInt().coerceIn(0, 62)
        return 1L shl doublings
    }

    /**
     * The reference a whole listing is scaled against.
     *
     * Two properties, both required, both tested:
     *
     * QUANTISED, so a streaming measurement does not rescale the spine on
     * every partial result. See [quantiseLargest].
     *
     * MONOTONIC within a listing, via [previous]. A folder's size climbs as
     * its walk proceeds, and a late folder can overtake everything else - but
     * the reference must never go DOWN while a directory is on screen, or
     * every mark would widen and then narrow again as facts arrive. Going up
     * is honest (something bigger was found); going down is a glitch.
     *
     * Reset [previous] to 0 when the user navigates, because a new directory
     * is a new scale.
     */
    fun scaleFor(bytes: Iterable<Long>, previous: Long = 0L): Long {
        var max = previous
        for (b in bytes) if (b > max) max = b
        return quantiseLargest(max)
    }

    private fun log2(v: Long): Double = ln(v.coerceAtLeast(1L).toDouble()) / ln(2.0)
}
