package com.filish

import androidx.compose.ui.graphics.Color
import com.filish.design.DarkPalette
import com.filish.design.LightPalette
import com.filish.design.Palette
import com.filish.design.glass.DaySkin
import com.filish.design.glass.Material
import com.filish.design.glass.NightSkin
import com.filish.design.glass.Skin
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contrast on translucent material.
 *
 * ContrastTest audits opaque pairs. This audits the thing that actually
 * shipped: ink drawn on a lens, which is a partially transparent surface, over
 * a substrate.
 *
 * This test is the reason the material is shippable. The standard objection to
 * glass interfaces is that nobody can tell you what the background of a given
 * glyph is, so nobody can prove it is legible - and in most such systems that
 * objection is correct, because the backdrop is arbitrary content. Substrate &
 * Lens is constructed so the backdrop is never arbitrary: a lens rests on the
 * substrate and nowhere else, so the composite is computable and therefore
 * testable.
 *
 * The audit uses the TOP of each lens, where it catches the most light. In
 * light mode that is where dark ink has the least contrast; the bottom of the
 * lens is always safer. Testing the easy end would be theatre.
 *
 * It has already earned its place. Dark Resting was specified at 13% white and
 * measured 3.97:1 for metadata text - a clear AA failure that looked perfectly
 * pleasant on screen. The tint is now 7% and the rim carries the structure
 * instead.
 */
class GlassContrastTest {

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
            val rounded = Math.round(r * 100) / 100.0
            throw AssertionError("$what has contrast $rounded:1, needs $minimum:1")
        }
    }

    private val themes = listOf(
        Triple("day", LightPalette, DaySkin),
        Triple("night", DarkPalette, NightSkin),
    )

    /**
     * Tiers that carry the full ink ramp.
     *
     * These are the surfaces tinted TOWARD the light, so they have more
     * headroom for dark ink than the substrate does, not less - the whole ramp
     * is safe on them. The recessed ones are audited separately below.
     */
    private val inkBearing = listOf(
        Material.Tier.Substrate,
        Material.Tier.Resting,
        Material.Tier.Lifted,
        Material.Tier.Modal,
    )

    @Test
    fun `text is legible on every tier of glass`() {
        for ((theme, palette, skin) in themes) {
            for (tier in inkBearing) {
                val surface = skin.settled(tier, atTop = true)
                assertAtLeast(4.5, palette.ink0, surface, "$theme ink0 on $tier")
                assertAtLeast(4.5, palette.ink1, surface, "$theme ink1 on $tier")
                // Metadata is content in a file manager, not decoration. A
                // size, a date and a count are the whole reason the row exists.
                assertAtLeast(4.5, palette.ink2, surface, "$theme ink2 on $tier")
            }
        }
    }

    @Test
    fun `the lens is legible at its dark end too`() {
        for ((theme, palette, skin) in themes) {
            for (tier in inkBearing) {
                val surface = skin.settled(tier, atTop = false)
                assertAtLeast(4.5, palette.ink2, surface, "$theme ink2 on the base of $tier")
            }
        }
    }

    /**
     * THE RECESSED-GROUND RULE.
     *
     * Any ground tinted AWAY from the substrate - a well, the storage volume -
     * takes one step up the ink ramp: its quietest text is ink1, never ink2.
     *
     * This is measured, not stylistic. Day ink2 lands at 4.26:1 on the base of
     * a well and 3.55:1 on the light volume, both clear AA failures, and both
     * looked completely unremarkable on screen. The substrate only has about
     * 0.3 of contrast headroom below it before ink2 stops clearing the bar, so
     * anything darker than the ground simply cannot carry the bottom of the
     * ramp.
     *
     * The rule is also just better interface: a search field whose placeholder
     * is the faintest grey available is a well-documented usability failure,
     * and this makes that impossible to specify by accident.
     */
    @Test
    fun `recessed grounds carry the ink steps that clear AA on them`() {
        for ((theme, palette, skin) in themes) {
            for (atTop in listOf(true, false)) {
                val surface = skin.settled(Material.Tier.Well, atTop)
                assertAtLeast(4.5, palette.ink0, surface, "$theme ink0 in a well")
                assertAtLeast(4.5, palette.ink1, surface, "$theme ink1 in a well")
            }
        }
    }

    @Test
    fun `semantic colours survive the glass`() {
        for ((theme, palette, skin) in themes) {
            for (tier in listOf(Material.Tier.Resting, Material.Tier.Modal)) {
                val surface = skin.settled(tier)
                assertAtLeast(4.5, palette.danger, surface, "$theme danger on $tier")
                assertAtLeast(4.5, palette.signal, surface, "$theme signal on $tier")
                assertAtLeast(4.5, palette.warn, surface, "$theme warn on $tier")
                assertAtLeast(4.5, palette.ok, surface, "$theme ok on $tier")
            }
        }
    }

    /**
     * The band is the signature, the file-kind indicator and the selection
     * marker in one mark. If it cannot be distinguished from the lens it sits
     * on, all three jobs fail at once - so it is held to the 3:1 non-text
     * threshold against every surface it can appear on.
     */
    @Test
    fun `the refraction band is visible against the lens it marks`() {
        for ((theme, palette, skin) in themes) {
            val tints = categoryTints(palette)
            for (tier in listOf(Material.Tier.Resting, Material.Tier.Lifted)) {
                val surface = skin.settled(tier)
                for ((name, tint) in tints) {
                    assertAtLeast(3.0, tint, surface, "$theme $name band on $tier")
                }
            }
        }
    }

    /**
     * The whole reason the volume exists is to be read as a quantity, which
     * means it has to be distinguishable from the air above it by more than a
     * hue shift - colour-blind and greyscale users included.
     */
    @Test
    fun `the storage volume is distinguishable from the air above it`() {
        for ((theme, _, skin) in themes) {
            // 1.28:1 is not a WCAG figure - there isn't one for "these are two
            // different materials". It is the smallest luminance step that
            // still reads as a boundary rather than as banding in the
            // substrate's own gradient, taken from the rendered specimens.
            assertAtLeast(1.28, skin.volumeLow, skin.airHigh, "$theme volume against air")
            // The meniscus is the single most informative line on the screen.
            assertAtLeast(3.0, opaque(skin.meniscus, skin.volumeHigh), skin.volumeHigh, "$theme meniscus")
        }
    }

    /** The volume is a recessed ground - see the rule above. */
    @Test
    fun `text on the storage volume uses the ink steps that clear AA there`() {
        for ((theme, palette, skin) in themes) {
            for (stop in listOf(skin.volumeHigh, skin.volumeLow)) {
                assertAtLeast(4.5, palette.ink0, stop, "$theme ink0 on the volume")
                assertAtLeast(4.5, palette.ink1, stop, "$theme ink1 on the volume")
            }
        }
    }

    /**
     * The rim is the only thing holding a dark-mode surface together once
     * translucency is taken away, so it has to actually be visible.
     */
    @Test
    fun `the rim separates a lens from its ground`() {
        for ((theme, _, skin) in themes) {
            for (tier in listOf(Material.Tier.Resting, Material.Tier.Lifted, Material.Tier.Well)) {
                val body = skin.settled(tier)
                val rimmed = opaque(skin.rim(tier), body)
                val delta = ratio(rimmed, body)
                if (delta < 1.06) {
                    throw AssertionError(
                        "$theme rim on $tier is indistinguishable from the surface " +
                            "(${Math.round(delta * 100) / 100.0}:1)",
                    )
                }
            }
        }
    }

    /** Dark mode keeps only its lightest tier as glass - see Material.translucent. */
    @Test
    fun `dark mode stops being translucent above the resting tier`() {
        for (tier in Material.Tier.entries) {
            val darkTranslucent = Material.translucent(tier, dark = true)
            // Resting is thin enough that a tint reads as material; a well is
            // a tint by definition, since a hole has no substance of its own.
            val expected = tier == Material.Tier.Resting || tier == Material.Tier.Well
            if (darkTranslucent != expected) {
                throw AssertionError(
                    "dark $tier translucency is $darkTranslucent; translucency over a " +
                        "near-black ground costs contrast and reveals nothing",
                )
            }
        }
    }

    /**
     * A hole must be darker than the surface it is a hole in, and an object
     * lighter. The tier ladder was rendered with a well drawn as a lens and it
     * came out LIGHTER than its ground, which is how the tier came to exist -
     * so the direction is pinned here rather than left to whoever next edits a
     * tint value.
     */
    @Test
    fun `objects sit above the ground and wells sit below it`() {
        for ((theme, _, skin) in themes) {
            val ground = luminance(skin.airHigh)
            val well = luminance(skin.settled(Material.Tier.Well))
            if (well >= ground) {
                throw AssertionError("$theme well is not darker than the substrate")
            }
            for (tier in listOf(Material.Tier.Resting, Material.Tier.Lifted, Material.Tier.Modal)) {
                if (luminance(skin.settled(tier)) <= ground) {
                    throw AssertionError("$theme $tier is not lighter than the substrate")
                }
            }
        }
    }

    private fun opaque(top: Color, bottom: Color): Color = Skin.over(top, bottom)

    private fun categoryTints(p: Palette) = listOf(
        "image" to p.catImage, "video" to p.catVideo, "audio" to p.catAudio,
        "document" to p.catDocument, "archive" to p.catArchive, "app" to p.catApp,
        "code" to p.catCode, "other" to p.catOther, "folder" to p.catFolder,
    )
}
