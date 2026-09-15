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
 * ===========================================================================
 * V3.2 made this test much shorter, and that is the finding
 * ===========================================================================
 *
 * In V3.1 every file name sat on a translucent body composited over a gradient
 * environment, so the audit had to reason about composites, worst-case ends,
 * lit variants and a recessed-ground exception - and day mode still kept
 * failing, which forced the environment darker, which broke metadata on the
 * environment, which pushed the signal colour. A chain of compromises
 * descending from a rectangle that was not earning its place.
 *
 * V3.2 deleted the rectangle. At rest, text sits on a flat opaque ground, and
 * the entire composite problem went with it. An art-direction decision taken
 * for authorship turned out to be the accessibility simplification too.
 *
 * What remains to prove:
 *
 *   1. the full ink ramp is legible directly on the environment, both themes
 *   2. the mark is distinguishable from the environment it sits in
 *   3. a selected object - the only thing with a surface - is legible, and
 *      keeps its magnitude and its identity
 *   4. light still cannot touch a text background
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

    private fun stops(p: Palette) = listOf(World.high(p.isDark), World.low(p.isDark))

    /**
     * The central assertion. With no object body, this IS the background of
     * every file name, size, date and count in the application.
     */
    @Test
    fun `the whole ink ramp is legible directly on the environment`() {
        for ((theme, p) in themes) {
            for (stop in stops(p)) {
                assertAtLeast(4.5, p.ink0, stop, "$theme ink0 on the environment")
                assertAtLeast(4.5, p.ink1, stop, "$theme ink1 on the environment")
                // Sizes, dates and counts. In a file manager this is content.
                assertAtLeast(4.5, p.ink2, stop, "$theme ink2 on the environment")
            }
        }
    }

    @Test
    fun `semantic colours are legible on the environment`() {
        for ((theme, p) in themes) {
            val bg = World.high(p.isDark)
            assertAtLeast(4.5, p.danger, bg, "$theme danger")
            assertAtLeast(4.5, p.signal, bg, "$theme signal")
            assertAtLeast(4.5, p.warn, bg, "$theme warn")
            assertAtLeast(4.5, p.ok, bg, "$theme ok")
        }
    }

    /**
     * The spine is the whole art direction. A mark that cannot be told apart
     * from the ground carries neither magnitude nor kind nor identity.
     */
    @Test
    fun `every mark is visible against the environment`() {
        for ((theme, p) in themes) {
            for (stop in stops(p)) {
                for ((name, tint) in tints(p)) {
                    assertAtLeast(3.0, tint, stop, "$theme $name mark on the environment")
                }
            }
        }
    }

    /**
     * A selected object is the only thing in a listing with a surface, and it
     * must not lose the two things the mark carries.
     */
    @Test
    fun `a held object keeps its legibility, its magnitude and its identity`() {
        for ((theme, p) in themes) {
            assertAtLeast(4.5, p.selectInk, p.selectGround, "$theme ink on a held object")
            for ((name, tint) in tints(p)) {
                assertAtLeast(3.0, onInverted(tint, p), p.selectGround, "$theme $name mark when held")
            }
            // And a held object must be obvious against the open ground it
            // materialised out of.
            assertAtLeast(3.0, p.selectGround, World.high(p.isDark), "$theme held object against the world")
        }
    }

    /** Pressing must be visible without being a second selection state. */
    @Test
    fun `a touched object is visible but weaker than a held one`() {
        for ((theme, p) in themes) {
            val env = World.high(p.isDark)
            val touched = Skin.over(World.touched(p.isDark), env)
            val touchDelta = ratio(touched, env)
            val holdDelta = ratio(p.selectGround, env)
            if (touchDelta < 1.10) {
                throw AssertionError(
                    "$theme press is invisible (${Math.round(touchDelta * 100) / 100.0}:1)",
                )
            }
            if (touchDelta >= holdDelta) {
                throw AssertionError("$theme press is as strong as selection; they must rank")
            }
            // Text stays legible while the finger is down.
            assertAtLeast(4.5, p.ink2, touched, "$theme ink2 on a touched object")
        }
    }

    /**
     * The structural guarantee. Light lives in the gutter, on the mark, where
     * no text sits - so there is no lit text background to audit, and a future
     * change that reintroduces one has to delete this test first.
     */
    @Test
    fun `there is no lit text background for light to live on`() {
        val methods = World::class.java.methods.map { it.name }
        if (methods.any { it == "lit" || it == "body" }) {
            throw AssertionError(
                "World.lit or World.body is back - at rest an object has no surface, " +
                    "and light belongs on the mark, never on a text background",
            )
        }
    }

    private fun onInverted(tint: Color, p: Palette): Color =
        if (p.isDark) {
            Color(tint.red * 0.34f, tint.green * 0.34f, tint.blue * 0.34f, 1f)
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
