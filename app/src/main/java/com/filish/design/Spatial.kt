package com.filish.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * FILISH space.
 *
 * There is no 4dp grid here, and that is deliberate.
 *
 * A uniform scale answers the question "what number goes here?" but it never
 * answers "what is the relationship between these two things?" - and spacing
 * is the only tool in the interface that communicates relationship without
 * drawing anything. When every gap is 16dp, every gap means the same thing,
 * which is to say none of them mean anything, and the interface then needs
 * dividers, boxes and cards to re-introduce the grouping that uniform spacing
 * destroyed. Most card-heavy interfaces are card-heavy for exactly this
 * reason.
 *
 * So FILISH names space by relationship rather than by size. The steps are
 * spaced at roughly 1.9x, which is the smallest ratio at which two gaps read
 * as *different kinds of gap* rather than as a mistake, and they are offset
 * off the common 8dp rhythm so the page has its own cadence.
 *
 * The consequence: FILISH needs very few dividers and almost no cards.
 * Grouping is done by proximity, which is free, silent, and survives every
 * theme.
 */
@Immutable
object Space {
    /** 3dp - one thing. A number and its unit; a glyph and its badge. */
    val bond: Dp = 3.dp

    /** 7dp - directly dependent. A file name and the metadata describing it. */
    val near: Dp = 7.dp

    /** 14dp - peers in a group. Row to row; chip to chip. */
    val group: Dp = 14.dp

    /** 26dp - separate concerns. One section to the next. */
    val apart: Dp = 26.dp

    /** 44dp - major regions of a screen. Used sparingly. */
    val zone: Dp = 44.dp

    /**
     * 20dp - the distance from the screen edge to content.
     *
     * Wider than the gap between rows so that the list reads as a column of
     * material sitting on a ground, rather than as content clamped to the
     * device. Narrow enough that a file name still gets its width back on a
     * small phone.
     */
    val gutter: Dp = 20.dp

    /** Inset for content that hangs below a leading glyph, so the text column
     *  of a row lines up with the text column of a header. */
    val textColumn: Dp = 54.dp
}

/**
 * Sizes that exist for the hand rather than for the eye.
 *
 * These are floors, not targets. A row is as tall as its content needs; it is
 * simply never allowed to be shorter than a finger.
 */
@Immutable
object Reach {
    /** The smallest thing a finger may be asked to hit. */
    val touch: Dp = 48.dp
    /** A comfortable touch target for a primary, frequent action. */
    val touchGenerous: Dp = 56.dp
    /** Leading glyph in a list row. */
    val glyph: Dp = 34.dp
    /** Thumbnail in a list row. */
    val thumb: Dp = 40.dp
    /** Hairline. Kept at a real 1dp rather than hairline-thin so it survives
     *  low-density panels. */
    val line: Dp = 1.dp
}

/**
 * Corner geometry.
 *
 * FILISH rounds almost nothing. A rounded rectangle announces "I am a discrete
 * container", and the file list is not a stack of containers - it is a
 * continuous body of material. Rounding is therefore reserved for objects that
 * really are discrete and really do float: sheets, chips, the transfer
 * overlay, thumbnails (which are pictures, and pictures have edges).
 */
@Immutable
object Corner {
    val none: Dp = 0.dp
    /** Thumbnails and small inline objects. */
    val small: Dp = 5.dp
    /** Chips, fields, and pressable tokens. */
    val token: Dp = 9.dp
    /** Transient surfaces that leave the ground. */
    val surface: Dp = 18.dp
}
