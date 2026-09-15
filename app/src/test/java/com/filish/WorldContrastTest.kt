package com.filish

import androidx.compose.ui.graphics.Color
import com.filish.design.DarkPalette
import com.filish.design.LightPalette
import com.filish.design.Palette
import com.filish.design.glass.Skin
import com.filish.design.glass.World
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The world, audited.
 *
 * Two things make an art direction like this dangerous, and both are pinned
 * here rather than trusted:
 *
 *   1. The body of an object is a translucent tone over an environment, so the
 *      real background of a file name is a composite - the same problem V3
 *      solved for the lens, at the scale of the whole screen.
 *
 *   2. The direction is *about* light, and light is the easiest thing in
 *      interface design to overspend. Direction H put a file at 26% alpha
 *      because it looked good.
 *
 * So the central assertion in this file is not "the lit state looks nice". It
 * is that **the UNLIT state is fully legible on its own** - illumination is
 * only ever added, so if the dark state passes, every state passes.
 */
class WorldContrastTest {

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun ratio(fg: Color, bg: Color): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertAtLeast(minimum: Double, fg: Color, bg: Color, what: String) {
        val r = ratio(fg, bg)
        if (r < minimum) {
            throw AssertionError("$what has contrast ${Math.round(r * 100) / 100.0}:1, needs $minimum:1")
        }
    }

    private val themes = listOf("night" to DarkPalette, "day" to LightPalette)

    /** The opaque colour a file name is actually drawn against. */
    private fun bodyOver(p: Palette, atTop: Boolean): Color {
        val ground = if (atTop) World.high(p.isDark) else World.low(p.isDark)
        return Skin.over(World.body(p.isDark), ground)
    }

    @Test
    fun `an unlit object is fully legible on its own`() {
        for ((theme, p) in themes) {
            for (atTop in listOf(true, false)) {
                val bg = bodyOver(p, atTop = atTop)
                assertAtLeast(4.5, p.ink0, bg, "$theme ink0 on an unlit object")
                assertAtLeast(4.5, p.ink1, bg, "$theme ink1 on an unlit object")
                // Sizes, dates and counts. In a file manager this is content.
                assertAtLeast(4.5, p.ink2, bg, "$theme ink2 on an unlit object")
            }
        }
    }

    /**
     * The structural guarantee: there is exactly ONE object ground, and
     * illumination is not a variant of it.
     *
     * This started life as "illumination must not cost a row its legibility",
     * which failed at 4.22:1 in day. The fix was to take light off the body
     * entirely, and this test is the shape that fix leaves behind - not "the
     * lit state also passes" but "there is no lit state to pass". A future
     * change that reintroduces a lit body has to delete this test first, which
     * is the point.
     */
    @Test
    fun `there is no second object ground for light to live on`() {
        for ((theme, p) in themes) {
            val fields = World::class.java.methods.map { it.name }
            if (fields.any { it == "lit" }) {
                throw AssertionError(
                    "$theme: World.lit exists again - light belongs in the gutter, " +
                        "on the mark, never on a text background",
                )
            }
        }
    }

    @Test
    fun `semantic colours survive the world`() {
        for ((theme, p) in themes) {
            val bg = bodyOver(p, atTop = true)
            assertAtLeast(4.5, p.danger, bg, "$theme danger on an object")
            assertAtLeast(4.5, p.signal, bg, "$theme signal on an object")
            assertAtLeast(4.5, p.warn, bg, "$theme warn on an object")
            assertAtLeast(4.5, p.ok, bg, "$theme ok on an object")
        }
    }

    /**
     * The mark is the whole art direction. If it cannot be told apart from the
     * body it sits on, magnitude, kind and identity all fail at once - and it
     * has to hold on the inverted ground of a selection too, which is exactly
     * when the user is deciding what to delete.
     */
    @Test
    fun `the magnitude mark is visible on every ground it can sit on`() {
        for ((theme, p) in themes) {
            val resting = bodyOver(p, atTop = true)
            for ((name, tint) in tints(p)) {
                assertAtLeast(3.0, tint, resting, "$theme $name mark on an object")
                assertAtLeast(3.0, onInverted(tint, p), p.selectGround, "$theme $name mark when selected")
            }
        }
    }

    /**
     * An object must be distinguishable from the environment it rests in, or
     * the list stops being a list of things and becomes a wall of text.
     */
    @Test
    fun `objects are distinguishable from the environment`() {
        for ((theme, p) in themes) {
            val obj = bodyOver(p, atTop = true)
            val env = World.high(p.isDark)
            val r = ratio(obj, env)
            if (r < 1.12) {
                throw AssertionError(
                    "$theme objects are indistinguishable from the world " +
                        "(${Math.round(r * 100) / 100.0}:1)",
                )
            }
        }
    }

    /**
     * The environment is a recessed ground in both themes, so it carries ink1
     * as its quietest step - never ink2. Same rule V3 established for wells and
     * the storage volume, applying again rather than a new exception.
     *
     * In day, dropping the environment to give the paper somewhere to be puts
     * ink2 on it at 4.12:1. Rather than lighten the desk back into the wall
     * that caused it, the ramp steps up, exactly as it does everywhere else
     * something is darker than the surface beside it.
     */
    @Test
    fun `the environment carries the ink steps that clear AA on it`() {
        for ((theme, p) in themes) {
            for (stop in listOf(World.high(p.isDark), World.low(p.isDark))) {
                assertAtLeast(4.5, p.ink0, stop, "$theme ink0 on the environment")
                assertAtLeast(4.5, p.ink1, stop, "$theme ink1 on the environment")
            }
        }
    }

    /**
     * The cut is load-bearing - three consecutive same-size RAW files merged
     * into one block without it - so it has to actually be visible.
     */
    @Test
    fun `the cut between objects is visible`() {
        for ((theme, p) in themes) {
            val obj = bodyOver(p, atTop = true)
            val cut = Skin.over(World.cut(p.isDark), obj)
            val r = ratio(cut, obj)
            if (r < 1.10) {
                throw AssertionError(
                    "$theme cut is invisible against the object body " +
                        "(${Math.round(r * 100) / 100.0}:1)",
                )
            }
        }
    }

    private fun onInverted(tint: Color, p: Palette): Color =
        if (p.isDark) {
            Color(tint.red * 0.38f, tint.green * 0.38f, tint.blue * 0.38f, 1f)
        } else {
            Color(
                tint.red + (1f - tint.red) * 0.45f,
                tint.green + (1f - tint.green) * 0.45f,
                tint.blue + (1f - tint.blue) * 0.45f,
                1f,
            )
        }

    private fun tints(p: Palette) = listOf(
        "image" to p.catImage, "video" to p.catVideo, "audio" to p.catAudio,
        "document" to p.catDocument, "archive" to p.catArchive, "app" to p.catApp,
        "code" to p.catCode, "other" to p.catOther, "folder" to p.catFolder,
    )
}
