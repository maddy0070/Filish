package com.filish.design.glass

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SUBSTRATE & LENS — the FILISH V3 material model.
 *
 * ===========================================================================
 * The constraint that shaped everything
 * ===========================================================================
 *
 * Generic "liquid glass" is backdrop blur: a surface that diffuses whatever is
 * behind it. On Android that is not a style choice, it is a wall.
 *
 *   - RenderEffect, which backs Modifier.blur(), is API 31+. FILISH supports
 *     API 26, so a third of the supported range gets NO blur at all - the
 *     effect silently becomes nothing.
 *   - Compose has no backdrop-blur primitive. Modifier.blur() blurs a
 *     composable's OWN content, not what is behind it. Blurring the backdrop
 *     means rendering it into an offscreen buffer and blurring that, per
 *     surface, per frame - while scrolling a directory of fifty thousand
 *     files.
 *
 * A design that needs backdrop blur is therefore a design that looks broken on
 * older devices and drops frames on newer ones. That is not a compromise to
 * manage; it is a direction to reject.
 *
 * ===========================================================================
 * What replaced it
 * ===========================================================================
 *
 * Ask what glass actually does when it is THIN and IN CONTACT with a surface,
 * rather than thick and floating far above one. It barely diffuses at all.
 * What it does instead:
 *
 *   TINTS      it shifts the tone of what is under it
 *   CATCHES    it takes a bright edge where light enters, dark where it leaves
 *   REFRACTS   it bends light in a narrow band right at its rim
 *   CONTACTS   it casts a tight, close shadow, not a soft drop shadow
 *
 * All four are a fill, two gradient stops, a hairline and a small shadow.
 * No offscreen buffers. Identical on API 26 and API 35. And it happens to be
 * the correct metaphor: files rest ON storage, they do not float in front of
 * it.
 *
 * ===========================================================================
 * Three materials, and one rule
 * ===========================================================================
 *
 *   SUBSTRATE  The ground. Storage itself. The only genuinely liquid element:
 *              it has depth, and where storage is the subject it has a LEVEL
 *              and a meniscus.
 *
 *   LENS       Thin glass resting on the substrate. Chrome, controls,
 *              transient surfaces. Tints and catches light. Never blurs.
 *
 *   INK        The content. File names, sizes, dates. Solid, opaque, maximum
 *              contrast.
 *
 * THE RULE: information is never made of glass. Everything a user reads to
 * make a decision is ink on a settled surface. Translucency is for the things
 * AROUND information, never for information itself. This single rule is what
 * stops the material from eating legibility, and it is not negotiable.
 *
 * ===========================================================================
 * Where the liquid belongs
 * ===========================================================================
 *
 * An early version put the storage level behind the whole file list. It was
 * removed, and that was the single biggest improvement in the exploration.
 * Behind a scrolling list a waterline is wallpaper, and every row cuts it into
 * fragments so it reads as a rendering fault rather than as a level.
 *
 * The volume appears only where storage is the subject: the header gauge, the
 * storage screen, the delete confirmation, the transfer. Everywhere else the
 * substrate is a quiet tonal ground.
 */
@Immutable
object Material {

    /**
     * Blur is permitted in exactly one situation, and even then it is an
     * enhancement rather than the effect.
     *
     * A modal surface genuinely occludes: the content behind it is no longer
     * available, and saying so with diffusion is honest. It is ONE surface, it
     * is not scrolling, and it is on screen briefly. Everywhere else, blur is
     * banned.
     *
     * Below API 31 it degrades to a heavier tint - which carries the same
     * meaning ("you cannot see through this clearly"), so nothing is lost but
     * refinement.
     */
    val blurAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** The only blur radius in the system. One value, one use. */
    val modalBlur: Dp = 24.dp

    /**
     * The material tiers.
     *
     * Deliberately few. A system with nine surface types has no hierarchy,
     * because the reader cannot hold nine levels in mind; they end up reading
     * it as "some things are glassy".
     */
    enum class Tier {
        /** The ground. Never has an edge, never casts a shadow. */
        Substrate,

        /**
         * Cut INTO the ground: search fields, gauge tracks, the hole a
         * dragged object leaves behind.
         *
         * Added after rendering the tier ladder, where a well drawn as a
         * Resting lens came out LIGHTER than the surface it was supposed to be
         * a hole in. A recess is not a surface with a different radius; it is
         * the other direction, and the model had no word for it.
         */
        Well,

        /**
         * Resting on the ground: rows, chips, the arrange token. Weakest
         * tint, thinnest rim, tightest contact shadow. This is most of the
         * interface.
         */
        Resting,

        /**
         * Lifted a little: the action bar, the selection ledger, the transfer
         * bar. Stronger tint so content scrolling beneath does not disturb it,
         * a visible rim, a longer shadow.
         */
        Lifted,

        /**
         * Genuinely in front: sheets and confirmations. Opaque enough to read
         * against anything, and the only tier permitted a blur behind it.
         */
        Modal,

        /**
         * Carried by the finger: a dragged object. Tighter, darker shadow
         * because it is close to the hand and far from the page.
         */
        Carried,
    }

    /**
     * Whether a tier is genuinely translucent, or merely a tone.
     *
     * TRANSLUCENCY NEEDS LIGHT BEHIND IT. In dark mode there is none: a
     * semi-transparent white over a near-black ground does not reveal the
     * ground, it just produces a muddy grey - and every percent of white it
     * adds is a percent of contrast taken away from the text on top.
     *
     * This is why dark glassmorphism universally looks worse than light
     * glassmorphism, and most systems simply accept it. FILISH does not. In
     * dark mode only the lightest tier stays glass, because a row is thin
     * enough that the tint reads as material; everything above it becomes an
     * honest opaque tone with a lit rim. The hierarchy is unchanged - only the
     * medium carrying it changes, exactly as [com.filish.design.Plane] already
     * does for shadow.
     */
    fun translucent(tier: Tier, dark: Boolean): Boolean = when (tier) {
        Tier.Substrate -> false
        Tier.Resting, Tier.Well -> true
        else -> !dark
    }

    /** A recess rather than an object. Its rim is lit from the opposite side. */
    fun recessed(tier: Tier): Boolean = tier == Tier.Well

    /**
     * Fill opacity of the lens at its top edge, where it catches light.
     *
     * The dark values are far lower than the light ones and that is not a
     * mistake: 13% white over a near-black ground measured 3.97:1 for metadata
     * text, which fails AA. The tint came down until it passed with margin and
     * the rim was raised to carry the structure instead. GlassContrastTest
     * pins this.
     */
    fun tintHigh(tier: Tier, dark: Boolean): Float = when (tier) {
        Tier.Substrate -> 0f
        Tier.Well -> if (dark) 0.35f else 0.07f
        Tier.Resting -> if (dark) 0.07f else 0.55f
        Tier.Lifted -> 0.78f
        Tier.Modal -> 0.97f
        Tier.Carried -> 0.92f
    }

    /**
     * Fill opacity at the lower edge. The gap between the two IS the light.
     *
     * That gap used to be much wider - Resting fell from 55% to 20% in day,
     * which is what a thin lens over a ground physically does. Rendered in a
     * list it made the bottom third of every row dissolve back into the
     * substrate, so the row's lower boundary became ambiguous and a column of
     * them read as smeared rather than as stacked.
     *
     * Physical accuracy lost that argument. In a file manager the edges of a
     * row are how you scan, and a material that softens them is worse at the
     * job regardless of what real glass does.
     */
    fun tintLow(tier: Tier, dark: Boolean): Float = when (tier) {
        Tier.Substrate -> 0f
        Tier.Well -> if (dark) 0.42f else 0.10f
        Tier.Resting -> if (dark) 0.045f else 0.38f
        Tier.Lifted -> 0.52f
        Tier.Modal -> 0.97f
        Tier.Carried -> 0.80f
    }

    /**
     * Rim opacity.
     *
     * =======================================================================
     * EDGES ARE DRAWN AWAY FROM THE GROUND, NOT TOWARD THE LIGHT
     * =======================================================================
     *
     * The first version of this system drew every rim as a bright white
     * hairline, because that is what a lit glass edge looks like and it is
     * what every glass interface does. GlassContrastTest measured the light
     * theme's resting rim at 1.03:1 against the surface it was supposed to be
     * separating. It was not subtle; it was absent.
     *
     * The cause is structural rather than a bad number. A lens tints TOWARD
     * white, so on a near-white ground the lens is already close to white and
     * there is no tonal headroom above it. Light can only be caught at an edge
     * when the surround is darker than the object.
     *
     * So the rim is drawn in whichever direction has room:
     *
     *   NIGHT   the lens is brighter than its ground - the rim is light
     *   DAY     the lens is already near-white - the rim is SHADE
     *
     * Which is also simply what a sheet of glass on white paper looks like:
     * you do not see a bright edge, you see a fine dark line. The same law
     * governs the meniscus, for the same reason and with the same measurement
     * behind it, and it is the single thing that keeps the light theme from
     * looking like cling film - which is the characteristic failure of light
     * glassmorphism everywhere.
     *
     * [Skin] resolves the colour; this is only the strength.
     */
    fun rim(tier: Tier, dark: Boolean): Float = when (tier) {
        Tier.Substrate -> 0f
        Tier.Well -> if (dark) 0.22f else 0.24f
        Tier.Resting -> if (dark) 0.22f else 0.20f
        Tier.Lifted -> if (dark) 0.26f else 0.26f
        Tier.Modal -> if (dark) 0.30f else 0.22f
        Tier.Carried -> if (dark) 0.34f else 0.30f
    }

    /**
     * How much the rim fades along the edge away from its strongest point.
     *
     * Same law, applied vertically: the rim is strongest at the top in night
     * (where light enters) and strongest at the bottom in day (where the glass
     * casts its own shade). One gradient brush, no second stroke.
     *
     * An earlier version drew a bright top rim AND a dark bottom rim as two
     * strokes. In day the bright one measured as nothing, so it was removed.
     */
    const val RIM_FADE = 0.35f

    /**
     * Contact shadow.
     *
     * Short offsets, tight radii. A resting lens is touching the ground, so
     * its shadow sits almost directly beneath it. Long soft shadows say
     * "floating", which is the claim this system deliberately does not make.
     */
    /**
     * Whether this tier may use a real, blurred platform shadow.
     *
     * A blurred shadow forces a graphics layer, and Resting is the tier that
     * appears two hundred times in a scrolling directory. So Resting gets a
     * drawn contact edge instead - one offset round-rect at low alpha, no
     * layer, no offscreen buffer - and only the few transient surfaces that
     * genuinely leave the ground pay for a real shadow.
     *
     * The tier that appears most often is the tier that costs nothing. That is
     * the whole performance strategy in one line.
     */
    fun castsRealShadow(tier: Tier): Boolean =
        tier == Tier.Lifted || tier == Tier.Modal || tier == Tier.Carried

    fun contactOffset(tier: Tier): Dp = when (tier) {
        Tier.Lifted -> 6.dp
        Tier.Modal -> 14.dp
        Tier.Carried -> 10.dp
        else -> 0.dp
    }

    /**
     * Shadow alpha. Dark grounds swallow shadow, so tone does the work.
     *
     * RESTING HAS NO SHADOW AT ALL, in either theme, and that is the largest
     * single change the remove-30% pass produced.
     *
     * It had one: a hard-edged offset outline at 6%, because a resting row
     * cannot afford a real blurred shadow. Rendered beside a stripped version
     * it did not read as contact, it read as a printing misregistration - a
     * grey duplicate of the row sitting two pixels down and to the side. The
     * stripped column was plainly better, and the rule is that when the
     * stripped version wins, the stripped version ships.
     *
     * What is lost is nothing: the rim and the tint already separate a row
     * from the ground. What is gained is one fewer full-size draw call on the
     * one element that appears two hundred times in a scrolling list.
     */
    fun contactAlpha(tier: Tier, dark: Boolean): Float = when {
        dark -> when (tier) {
            Tier.Modal -> 0.55f
            Tier.Carried -> 0.45f
            else -> 0f
        }
        else -> when (tier) {
            Tier.Lifted -> 0.10f
            Tier.Modal -> 0.20f
            Tier.Carried -> 0.16f
            else -> 0f
        }
    }
}

/**
 * THE BAND — FILISH's signature.
 *
 * A narrow vertical sliver of light at the leading edge of every interactive
 * object, brightest where light enters at the top and falling away down the
 * edge. It is the refraction you would see at the cut rim of a real lens.
 *
 * It earns its place by doing three jobs at once, which is the test every
 * decorative idea has to pass:
 *
 *   IDENTITY   it is what makes a FILISH row recognisable without a logo
 *   KIND       it carries the file-category tint, so type is legible at a
 *              glance rather than by reading the extension
 *   STATE      it goes to full strength on selection, and to the signal
 *              colour on focus
 *
 * Because it is a solid mark and not a tint on the whole row, it survives
 * greyscale and it does not reduce the contrast of any text.
 */
@Immutable
object Band {
    val width: Dp = 4.dp

    /** Alpha at the top of the band. */
    fun topAlpha(selected: Boolean): Float = if (selected) 1f else 0.95f

    /**
     * Alpha at the bottom. The fall-off is the refraction.
     *
     * It used to fall to 26%, which looked like refraction in isolation and
     * like the band running out of ink in a list - the lower half of an image
     * row's band lost its orange entirely, so the kind signal, which is the
     * band's primary job, was only legible in the top half of the mark.
     *
     * A shallower fall still reads as a lit edge and keeps the tint identifiable
     * over the band's whole length. Decoration yields to the job.
     */
    fun bottomAlpha(selected: Boolean): Float = if (selected) 0.75f else 0.62f

    /** Reduced to a flat mark when the user has asked for less. */
    fun flatAlpha(selected: Boolean): Float = if (selected) 1f else 0.7f
}
