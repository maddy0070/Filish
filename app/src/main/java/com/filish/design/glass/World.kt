package com.filish.design.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * THE WORLD — the environment, and what light is allowed to mean in it.
 *
 * ===========================================================================
 * V3.2: the body is gone, and that solved more than it cost
 * ===========================================================================
 *
 * V3.1 gave every file a translucent body - a filled rectangle spanning the
 * screen. It was correct about materials and wrong about art direction: a
 * column of filled rectangles is the most generic form an interface has, and
 * it made FILISH look like a design system rendered onto a file manager rather
 * than a designed thing.
 *
 * V3.2 deletes it. At rest a file is a MARK IN THE SPINE and two lines of text
 * on open ground. The body becomes a STATE: it appears under the finger and on
 * selection, so the material that used to be the default now means "this
 * object is under your control".
 *
 * That was an art-direction decision, and it turned out to be an accessibility
 * win as well. With a body, the real background of every file name was a
 * translucent tone composited over a gradient - which is why day mode kept
 * failing: the day environment had to be dropped so the body had somewhere to
 * be, which then broke metadata contrast on the environment itself, which
 * forced the recessed-ground rule, which pushed the signal colour. A chain of
 * compromises, all descending from a rectangle that was not earning its place.
 *
 * With no body, text sits on a known, flat, opaque ground. The whole composite
 * problem disappears, and the day environment goes back to paper.
 *
 * ===========================================================================
 * Night is a place. Day is a page.
 * ===========================================================================
 *
 * Light mode is not the dark theme inverted, and not its metaphor forced onto
 * paper. In night FILISH is an environment you are inside. In day it is a
 * printed index - the ground is paper, the marks are printed, magnitude is ink
 * width. Identical physics, different substance; a user switching themes loses
 * nothing and learns nothing new.
 *
 * ===========================================================================
 * THE RULE: LIGHT IS NEVER LOAD-BEARING
 * ===========================================================================
 *
 * Direction H proposed hierarchy by illumination - bright near the light, dim
 * far from it. Rendered, it put a file two thirds down the list at 26% alpha.
 * A file manager that dims the user's data by its position in a list is hiding
 * the thing it exists to show.
 *
 * So light is never ambient and never a ranking. It is transient emphasis, it
 * only ever adds, and it lives in the gutter - see [Light].
 *
 * ===========================================================================
 * THE SUBSTRATE CANNOT CARRY INFORMATION
 * ===========================================================================
 *
 * A storage horizon in the environment was prototyped and rejected twice. V3
 * rejected a hard waterline because rows sliced it into fragments; V3.1
 * rejected the softened version for a better reason - in a working directory
 * the environment is almost entirely covered by content, so there is nowhere
 * for an ambient signal to live.
 *
 * This kills a family of ideas, not one: ambient storage colour, mood grounds,
 * content-derived atmosphere. The substrate establishes that this is a place.
 * It never gets a fact.
 */
@Immutable
object World {

    /** Night: a deep place with a below. Two stops, close together. */
    val nightHigh = Color(0xFF131418)
    val nightLow = Color(0xFF0A0B0D)

    /**
     * Day: paper.
     *
     * Restored to a true near-white in V3.2. V3.1 had to darken this so a
     * translucent object body had somewhere to be; with the body gone, the
     * page can be a page again, and every ink step clears AA directly on it.
     */
    val dayHigh = Color(0xFFF6F4EF)
    val dayLow = Color(0xFFEFEDE7)

    fun high(dark: Boolean): Color = if (dark) nightHigh else dayHigh
    fun low(dark: Boolean): Color = if (dark) nightLow else dayLow

    /**
     * The body under a finger.
     *
     * Not a ripple. The object is briefly materialising - the same thing
     * selection does, at lower commitment - so press and selection are one
     * gesture at two strengths rather than two unrelated effects.
     */
    fun touched(dark: Boolean): Color = if (dark) Color(0x0FFFFFFF) else Color(0x0D000000)

    /**
     * The body of a held object. Full inversion, from [com.filish.design.Palette].
     *
     * A selected object is the only thing in a listing that has a surface, so
     * selection needs no checkbox, no tint and no second colour: the object
     * simply becomes solid while everything around it stays open ground.
     */
    val holdInvertsGround = true
}

/**
 * LIGHT — transient emphasis, carried ENTIRELY by the mark.
 *
 * Putting illumination on the mark rather than on a body is what makes "light
 * is never load-bearing" STRUCTURAL rather than merely asserted. The mark
 * lives in a gutter with no text on it and no text behind it, so no amount of
 * light there can reduce the contrast of a file name.
 *
 * An earlier version lit the body too, at a strength chosen to survive the
 * audit. It did not: day metadata on a lit body measured 4.22:1, and every
 * value strong enough to see failed the same way. Deleting it produced a
 * better rule than the one being defended - and V3.2 deleted the body
 * entirely, so the question can no longer be asked.
 *
 * What may light up, and nothing else:
 *
 *   - a folder whose size is still being measured
 *   - objects matching a search that is still running
 *   - the region a storage insight is about
 *   - an object in an operation that is in flight
 *
 * Every one is something the system is doing now, and every one stops.
 * Nothing is ever dimmed to make something else stand out.
 */
@Immutable
object Light {

    /** The mark at rest, and the mark under attention. */
    const val REST = 1f
    const val ATTENTION = 1f

    /** The bloom beside a lit mark: one rect with a falling gradient. A flat
     *  rect reads as a SECOND mark rather than as light - the prototype proved
     *  it with an orange bloom beside an orange mark. */
    const val BLOOM_ALPHA = 0.26f
    val bloom = 8.dp
}

/**
 * Paint the environment.
 *
 * One brush, two stops, built in drawWithCache so it is rebuilt on resize
 * rather than per frame. Nearly free, and nearly invisible under a full
 * directory - which is correct. Its job is the threshold, the empty states,
 * the opening and the root, where the difference between "a place" and "a
 * black background" is the whole impression the application makes.
 */
fun Modifier.environment(dark: Boolean): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(listOf(World.high(dark), World.low(dark)))
    onDrawBehind { drawRect(brush) }
}
