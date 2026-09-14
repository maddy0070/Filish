package com.filish.design.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.filish.design.Palette

/**
 * The V3 colour system: what the interface is MADE of.
 *
 * ===========================================================================
 * Why this is a second object and not a bigger Palette
 * ===========================================================================
 *
 * [Palette] answers "what colour is this thing?" - ink, signal, danger,
 * category tints. Every pair in it is already asserted against WCAG AA, and
 * none of it changes in V3, because none of it is glass. Information is not
 * glass; that is the rule the whole material rests on.
 *
 * [Skin] answers a different question: "what is the SURFACE under that ink
 * made of?" Substrate stops, lens stops, rim light, the departing shade, the
 * meniscus. It is the material, not the marking.
 *
 * Keeping them apart means a change to the material can never silently change
 * a text colour, and the composite of the two is auditable - which matters
 * enormously here, because the surface under the ink is now partially
 * transparent.
 *
 * ===========================================================================
 * The composite problem, and why this file computes colours instead of
 * declaring them
 * ===========================================================================
 *
 * The fatal flaw in conventional glassmorphism is that the effective
 * background of a piece of text is unknown at design time. It depends on what
 * happens to be behind the panel. In a file manager, what is behind the panel
 * is a wall of photo thumbnails - so the contrast of a file size is a function
 * of the user's camera roll. That is not a system; it is a gamble, and the
 * exploration rendered it to be sure.
 *
 * Substrate & Lens removes the gamble by construction. A lens only ever rests
 * on the substrate, and the substrate is a known two-stop tonal ground. So the
 * colour behind every glyph is COMPUTABLE - [settled] does exactly that - and
 * GlassContrastTest audits real composited surfaces rather than the opaque
 * approximations a designer wishes they were.
 *
 * If a tint value here is raised for looks and that test fails, the tint was
 * wrong. That has already happened once: dark Resting started at 13% white and
 * measured 3.97:1 for metadata. It is now 7%.
 */
@Immutable
data class Skin(
    /** Substrate, top stop. The ground's lit end. */
    val airHigh: Color,
    /** Substrate, bottom stop. Very slightly denser - the ground has depth. */
    val airLow: Color,

    /**
     * The volume: storage drawn as material with a level.
     *
     * Used ONLY where storage is the subject - the header gauge, the storage
     * screen, a delete confirmation, a transfer. Behind a scrolling list it is
     * wallpaper, and every row slices the waterline into fragments so it reads
     * as a rendering fault. Removing it from the browsing ground was the single
     * biggest improvement in the V3 exploration.
     */
    val volumeHigh: Color,
    val volumeLow: Color,
    /** The waterline. The brightest line in the system, and the only one. */
    val meniscus: Color,

    /** What a lens tints toward. Always the light source, never a hue. */
    val lensTint: Color,
    /** What a well tints toward: away from the light, because it is a hole. */
    val wellTint: Color,
    /**
     * The colour every edge in the system is drawn in.
     *
     * Light in night, shade in day - see [Material.rim] for why that is a law
     * and not a preference. One colour, because a system with a highlight edge
     * AND a shadow edge has one edge that measures as nothing.
     */
    val edge: Color,

    /** Opaque tones for the tiers that stop being glass in dark mode. */
    val solidLifted: Color,
    val solidModal: Color,
    val solidCarried: Color,

    /** The contact shadow's colour. Warm in light, so it reads as shade and
     *  not as dirt on a paper ground. */
    val contact: Color,

    val isDark: Boolean,
) {

    /** The colour this tier's fill is made of - light for objects, dark for
     *  holes. The substrate has no fill at all; it IS the fill. */
    private fun tintOf(tier: Material.Tier): Color =
        if (Material.recessed(tier)) wellTint else lensTint

    /** Top stop of a tier's fill, as actually drawn. */
    fun high(tier: Material.Tier): Color = when {
        tier == Material.Tier.Substrate -> Color.Transparent
        Material.translucent(tier, isDark) ->
            tintOf(tier).copy(alpha = Material.tintHigh(tier, isDark))
        else -> solid(tier)
    }

    /** Bottom stop of a tier's fill. The gap between the two is the light. */
    fun low(tier: Material.Tier): Color = when {
        tier == Material.Tier.Substrate -> Color.Transparent
        Material.translucent(tier, isDark) ->
            tintOf(tier).copy(alpha = Material.tintLow(tier, isDark))
        else -> solid(tier)
    }

    /** The rim at its strongest point. */
    fun rim(tier: Material.Tier): Color =
        edge.copy(alpha = Material.rim(tier, isDark))

    /**
     * A PRESSED LENS BECOMES A WELL.
     *
     * The first version of press interpolated the fill a little way toward the
     * substrate and relied on the contact shadow collapsing to sell the sink.
     * Then the remove-30% pass deleted the resting contact shadow - correctly,
     * it looked like a misprint - and took the press signal with it. Rendered
     * side by side, rest, hover and pressed were indistinguishable. For the
     * single most-used interaction in the application, that is not a polish
     * issue, it is a broken control.
     *
     * The fix was already in the system. Pushing an object into a soft ground
     * far enough makes it a recess, and [Material.Tier.Well] is exactly that:
     * a different fill direction and a reversed rim. So press interpolates the
     * whole lens toward a well, and at full press the row is unmistakably
     * pushed in - fill inverted, edge light flipped to the far side.
     *
     * No ripple, no scale, no shadow, no new token, and two interpolations of
     * work. A ripple would have been a claim that the surface is a pool of ink
     * spreading from the touch point, which is a fine idea belonging to
     * somebody else's material; this is what actually happens when you press
     * something resting on a soft ground.
     *
     * [hover] lifts a hair toward [Material.Tier.Lifted], for the pointer and
     * stylus cases that exist on tablets and desktops. It is deliberately tiny:
     * on the phones this application is mostly for, it never fires at all.
     */
    fun bodyStops(tier: Material.Tier, press: Float = 0f, hover: Float = 0f): Pair<Color, Color> {
        var top = high(tier)
        var bottom = low(tier)
        if (press > 0f) {
            top = lerp(top, high(Material.Tier.Well), press)
            bottom = lerp(bottom, low(Material.Tier.Well), press)
        }
        if (hover > 0f) {
            val lift = hover * 0.35f
            top = lerp(top, high(Material.Tier.Lifted), lift)
            bottom = lerp(bottom, low(Material.Tier.Lifted), lift)
        }
        return top to bottom
    }

    /** The rim, flipping from object-lit to recess-lit as the lens sinks. */
    fun rimStopsFor(tier: Material.Tier, press: Float = 0f): Pair<Color, Color> {
        val (top, bottom) = rimStops(tier)
        if (press <= 0f) return top to bottom
        val (wellTop, wellBottom) = rimStops(Material.Tier.Well)
        return lerp(top, wellTop, press) to lerp(bottom, wellBottom, press)
    }

    /**
     * The rim stroke's two stops, top first.
     *
     * Strongest at the top in night, where light enters; strongest at the
     * bottom in day, where the glass casts its own shade. The gradient runs
     * away from the ground in both, which is the same law stated vertically.
     *
     * A well reverses it again. A hole lit from above is shaded at its near
     * lip and lit on its far wall, which is the opposite of an object - so the
     * same brush, read backwards, is the difference between something sitting
     * on the surface and something cut into it. Free, and it is what makes a
     * search field read as a slot rather than as a pale row.
     */
    fun rimStops(tier: Material.Tier): Pair<Color, Color> {
        val peak = rim(tier)
        val fade = peak.copy(alpha = peak.alpha * Material.RIM_FADE)
        val peakAtTop = isDark != Material.recessed(tier)
        return if (peakAtTop) peak to fade else fade to peak
    }

    fun contactShadow(tier: Material.Tier): Color =
        contact.copy(alpha = Material.contactAlpha(tier, isDark))

    private fun solid(tier: Material.Tier): Color = when (tier) {
        Material.Tier.Lifted -> solidLifted
        Material.Tier.Modal -> solidModal
        Material.Tier.Carried -> solidCarried
        else -> airHigh
    }

    /**
     * The opaque colour a glyph is actually drawn against.
     *
     * This is the honest answer to "what is the background of this text", and
     * it is what the contrast audit uses. [atTop] picks the worst case: the
     * lens is brightest where it catches light, which in light mode is where
     * dark ink has least contrast, so that end is the one that has to pass.
     */
    fun settled(tier: Material.Tier, atTop: Boolean = true): Color {
        val ground = if (atTop) airHigh else airLow
        if (tier == Material.Tier.Substrate) return ground
        return over(if (atTop) high(tier) else low(tier), ground)
    }

    /** The opaque colour of the volume at its densest point. */
    val volumeFloor: Color get() = volumeLow

    companion object {
        /**
         * Source-over compositing, the same arithmetic the GPU does.
         *
         * Having this in the design system rather than only in a test is the
         * point: a component that needs to know its own effective background -
         * to pick an ink step, to decide whether a hairline is needed - can ask
         * instead of guessing.
         */
        fun over(top: Color, bottom: Color): Color {
            val a = top.alpha
            if (a >= 1f) return top.copy(alpha = 1f)
            if (a <= 0f) return bottom
            return Color(
                red = top.red * a + bottom.red * (1f - a),
                green = top.green * a + bottom.green * (1f - a),
                blue = top.blue * a + bottom.blue * (1f - a),
                alpha = 1f,
            )
        }
    }
}

/**
 * Day.
 *
 * The substrate is a warm near-white that is fractionally darker than V2's
 * paper ground. That was not a style preference: a lens tints TOWARD white, so
 * if the ground is already white the lens has nowhere to go and the material
 * disappears. Dropping the ground three steps is what gives the glass
 * somewhere to be.
 */
val DaySkin = Skin(
    airHigh = Color(0xFFF6F4EF),
    airLow = Color(0xFFEFEDE7),

    volumeHigh = Color(0xFFDCE2DC),
    volumeLow = Color(0xFFC6D0C9),
    // Seen from above against a pale ground, a waterline is a dark line, not a
    // bright one. A white meniscus here measured 1.28:1 - see Material.rim.
    meniscus = Color(0xD935564C),

    lensTint = Color(0xFFFFFFFF),
    wellTint = Color(0xFF6E6A61),
    edge = Color(0xFF6E6A61),

    // Light mode never falls back to a solid tier, but the values exist so the
    // type is total and a mistake fails loudly instead of rendering black.
    solidLifted = Color(0xFFFDFDFC),
    solidModal = Color(0xFFFFFEFB),
    solidCarried = Color(0xFFFFFEFC),

    contact = Color(0xFF3A342A),

    isDark = false,
)

/**
 * Night.
 *
 * Only the Resting tier is still glass here - see [Material.translucent]. The
 * volume is dark and faintly cool, as though lit from inside rather than from
 * above, and the meniscus is the one place in the whole application where a
 * saturated light is allowed: it is the single most informative line on the
 * screen, so it gets to be the brightest.
 */
val NightSkin = Skin(
    airHigh = Color(0xFF0D0F11),
    airLow = Color(0xFF101315),

    // Dark enough that the figure and its caption clear AA on it, light enough
    // that it still reads as a different material from the air above. Both
    // ends of that are measured; there is very little room between them.
    volumeHigh = Color(0xFF18231F),
    volumeLow = Color(0xFF1E3029),
    meniscus = Color(0xB36FD9C4),

    lensTint = Color(0xFFFFFFFF),
    wellTint = Color(0xFF000000),
    edge = Color(0xFFFFFFFF),

    solidLifted = Color(0xFF1B1E21),
    solidModal = Color(0xFF1E2226),
    solidCarried = Color(0xFF232729),

    contact = Color(0xFF000000),

    isDark = true,
)

/** The skin that goes with a palette. One decision, made in one place. */
fun skinFor(palette: Palette): Skin = if (palette.isDark) NightSkin else DaySkin
