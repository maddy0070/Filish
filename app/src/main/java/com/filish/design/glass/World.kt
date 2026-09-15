package com.filish.design.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * THE WORLD — the V3.1 environment, and what light is allowed to mean in it.
 *
 * ===========================================================================
 * Night is a place. Day is a page.
 * ===========================================================================
 *
 * This is the answer to "does the material language actually work in light
 * mode", and it is not "invert the dark theme".
 *
 * In night, FILISH is an environment: a deep, quiet space with a below, in
 * which objects rest and light means something is happening. That reading is
 * coherent and it is what the art direction is for.
 *
 * In day that reading collapses. A near-white ground is not a place you are
 * inside; it is a surface you are looking at. Forcing atmosphere onto it
 * produces exactly the grey haze that makes light glassmorphism look like
 * cling film - V3 already measured that failure at the rim, and it is the same
 * failure here at the scale of the whole screen.
 *
 * So day is a different medium with the same physics. FILISH in day is a
 * printed index: the ground is paper, the marks are printed, magnitude is ink
 * width. Every rule below still applies; only the substance changes. The
 * hierarchy, the mark, the information and the interaction are identical, and
 * a user switching themes loses nothing and learns nothing new.
 *
 * ===========================================================================
 * THE RULE: LIGHT IS NEVER LOAD-BEARING
 * ===========================================================================
 *
 * Direction H proposed hierarchy by illumination - objects near the light are
 * bright, objects far from it fade. Rendered, it was beautiful and it was an
 * accessibility catastrophe: a file two thirds down the list sat at 26% alpha
 * and could not be read. A file manager that dims the user's data according to
 * its position in a list is hiding the thing it exists to show, and the
 * ranking it implies is not real.
 *
 * So light is never ambient and never a ranking. It is TRANSIENT EMPHASIS, and
 * it only ever adds:
 *
 *   - a folder whose size is still being measured
 *   - objects matching a live search
 *   - the region a storage insight is about
 *   - an operation in flight
 *
 * Every one of those is a thing the system is doing right now, and every one
 * stops. Remove all illumination from any FILISH screen and it must remain
 * fully legible and fully ranked - the contrast audit asserts the unlit state,
 * never the lit one.
 *
 * ===========================================================================
 * THE SUBSTRATE CANNOT CARRY INFORMATION
 * ===========================================================================
 *
 * A storage horizon in the environment - a soft density change at the device's
 * fill level - was prototyped and rejected, for a better reason than the one
 * V3 found.
 *
 * V3 rejected a hard waterline because rows sliced it into fragments. The
 * softened version has no line to fragment, and it still failed: in a working
 * directory the environment is almost entirely covered by content. There is
 * nowhere for an ambient signal to live.
 *
 * This kills a whole family of ideas rather than one - ambient storage colour,
 * mood grounds, content-derived atmosphere - and it is worth stating plainly.
 * The substrate's only job is to establish that this is a place. It gets the
 * screen edges, empty states, the opening and the storage screen. It never
 * gets a fact.
 */
@Immutable
object World {

    /** Night: a deep place with a below. Two stops, close together. */
    val nightHigh = Color(0xFF131418)
    val nightLow = Color(0xFF0A0B0D)

    /**
     * Day: the desk the page sits on.
     *
     * Deliberately several steps darker than V3's paper ground, and the reason
     * is the same structural wall V3 hit at the rim. An object's body is a
     * white tint; over a near-white ground it has nowhere to go, and the audit
     * measured objects at 1.05:1 against the environment - a list of things
     * that is indistinguishable from the surface under it, which is to say a
     * wall of text.
     *
     * Dropping the environment gives the paper somewhere to be. It also makes
     * the day metaphor honest: night is a place you are inside, day is a page
     * on a desk. The list is the sheet; this is the surface it rests on.
     *
     * Consequence, and it is the recessed-ground rule from V3 applying again
     * rather than a new exception: the environment is darker than the paper, so
     * text drawn directly on it takes ink1, never ink2.
     */
    val dayHigh = Color(0xFFE8E4DC)
    val dayLow = Color(0xFFE1DCD2)

    fun high(dark: Boolean): Color = if (dark) nightHigh else dayHigh
    fun low(dark: Boolean): Color = if (dark) nightLow else dayLow

    /**
     * The resting body of an object.
     *
     * A flat tone, not a gradient. The remove-30% pass rendered the gradient
     * version beside the flat one and the gradient was doing nothing - the
     * rows are 58dp tall and a two-stop fade over 58dp at these alphas is
     * invisible. What it did do was band the list, which fought the one thing
     * the composition wanted: a continuous body.
     */
    fun body(dark: Boolean): Color = if (dark) Color(0x0EFFFFFF) else Color(0x8CFFFFFF)

    /**
     * The cut between objects.
     *
     * LOAD-BEARING, and the strip test proved it. With the cut removed, three
     * consecutive RAW files of near-identical size merged into one
     * indistinguishable orange block - and adjacent files sharing a kind and a
     * size is not an edge case, it is burst photography, screenshots and
     * exports, which is most of what is in a real camera folder.
     *
     * The cut must cross the mark channel, not stop at it. Stopping at the
     * mark leaves the marks fused into a continuous bar, which says "one
     * object" about three.
     */
    fun cut(dark: Boolean): Color = if (dark) Color(0x59000000) else Color(0x1F000000)
}

/**
 * LIGHT — transient emphasis, carried ENTIRELY by the mark.
 *
 * ===========================================================================
 * Light never touches a text background. At all.
 * ===========================================================================
 *
 * The first version of this lit the object's body as well, at a strength small
 * enough to survive the contrast audit. It did not survive: day metadata on a
 * lit body measured 4.22:1, and every value strong enough to actually be seen
 * failed in both themes, because the resting body already spends nearly all
 * the headroom AA allows.
 *
 * Tuning it down would have produced a "highlight" nobody could see, which is
 * the worst of both. Deleting it produced a better rule than the one being
 * defended: light lives in the gutter, on the mark, where no text sits and no
 * text can ever sit. "Light is never load-bearing" stops being a principle
 * someone has to remember and becomes a property of where the light is.
 *
 * A region still reads as a region, because the blooms on adjacent marks form
 * a continuous column of light down the edge of that region - which is more
 * legible than a 2% body tint ever was.
 *
 * What is allowed to light up, and nothing else:
 *
 *   - a folder whose size is still being measured
 *   - objects matching a search that is still running
 *   - the region a storage insight is about
 *   - an object in an operation that is in flight
 *
 * Every one is something the system is doing right now, and every one stops.
 * Nothing is ever dimmed to make something else stand out: Direction H tried
 * exactly that and put a file two thirds down the list at 26% alpha, which is
 * a file manager hiding the user's data to look good.
 */
@Immutable
object Light {

    /** The mark at rest, and the mark under attention. */
    const val REST = 0.92f
    const val ATTENTION = 1f

    /** The bloom beside a lit mark: one extra rect, no layer, no blur. */
    const val BLOOM_ALPHA = 0.22f
    val bloom = 7.dp
}

/**
 * Paint the environment.
 *
 * One brush, two stops, built in drawWithCache so it is rebuilt on resize
 * rather than per frame. It is very nearly free, and it is very nearly
 * invisible in a full directory - which is correct. Its job is the screen
 * edges, the empty states, the opening and the storage screen, where the
 * difference between "a place" and "a black background" is the whole
 * impression the application makes.
 */
fun Modifier.environment(dark: Boolean): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(listOf(World.high(dark), World.low(dark)))
    onDrawBehind { drawRect(brush) }
}
