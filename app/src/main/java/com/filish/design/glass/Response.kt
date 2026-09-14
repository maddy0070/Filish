package com.filish.design.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.filish.design.Palette

/**
 * Interaction states, expressed as material rather than as colour.
 *
 * ===========================================================================
 * The principle
 * ===========================================================================
 *
 * Almost every design system expresses interaction by tinting: a hover is 4%
 * of something, a press is 12% of something, selection is a blue wash. It is
 * easy to specify and it has two failures that matter here. It is invisible to
 * anyone who cannot separate those tints - at 4% that is most people, in
 * sunlight it is everyone - and it collides with the colours the application
 * already uses to say what a file IS.
 *
 * Under Substrate & Lens there is a physical vocabulary available instead, so
 * each state is a different thing HAPPENING to the material:
 *
 *   REST      the lens sits on the substrate
 *   HOVER     it lifts a hair - a pointer is near it but not on it
 *   PRESSED   it SINKS into the substrate; the contact shadow collapses
 *   FOCUSED   the band goes to signal at full strength and the rim doubles
 *   SELECTED  the ground inverts and the band goes solid
 *   DISABLED  it stops being an object: rim gone, fill gone, back to ground
 *   DRAGGING  it leaves the substrate entirely and leaves a well behind
 *
 * Every one survives greyscale, and none of them needs a colour the file-kind
 * system is already using.
 *
 * ===========================================================================
 * Two of these are answers to real bugs
 * ===========================================================================
 *
 * DISABLED is not "the same thing at 38% alpha". In V2 that produced solid
 * black slabs, because Color.Transparent is black at zero alpha and scaling
 * its alpha makes it visible - the disabled Copy and Move buttons became the
 * heaviest elements on the surface. TransparentAlphaTest pins the trap. The
 * deeper lesson is that fading a composite is not the same as fading a colour,
 * so disabled is defined here as a DESTINATION - the substrate - rather than
 * as an operation on whatever the control happened to be.
 *
 * DRAGGING leaves a well. The shape language says symmetric shapes are cut
 * into the substrate ([Facet.well]), so the hole a lifted object leaves is
 * literally a different silhouette from the object itself. That is why the
 * list never looks like it simply lost a row.
 */
@Immutable
enum class Response {
    Rest, Hover, Pressed, Focused, Selected, Disabled, Dragging;

    /** How far the lens is pushed into the substrate, 0..1. */
    fun press(): Float = if (this == Pressed) 1f else 0f

    /** How far it lifts toward a pointer, 0..1. Never fires on a phone. */
    fun hover(): Float = if (this == Hover) 1f else 0f

    /** Which material tier the object is on while in this state. */
    fun tier(resting: Material.Tier): Material.Tier = when (this) {
        Dragging -> Material.Tier.Carried
        Disabled -> Material.Tier.Substrate
        else -> resting
    }

    /** Whether the object still reads as a discrete thing at all. */
    val isObject: Boolean get() = this != Disabled

    /**
     * The band's colour.
     *
     * Kind is the default, because kind is what the band is FOR. Focus
     * overrides it with the signal colour - focus is transient and unique on
     * screen, so it is allowed to take the band over. Disabled drops the band
     * to the quietest ink: the object has no kind any more because it cannot
     * be acted on.
     */
    fun band(kind: Color, palette: Palette): Color = when (this) {
        Focused -> palette.signal
        Disabled -> palette.ink2
        else -> kind
    }

    /** Focus is the one state allowed to thicken the rim, because a keyboard
     *  or D-pad user has no pointer to look at and needs an unmistakable mark. */
    val rimMultiplier: Float get() = if (this == Focused) 2f else 1f

    /**
     * The ink this state's content is drawn in.
     *
     * Disabled steps the whole ramp down, because an object that has stopped
     * being an object should not still be shouting its name at full contrast -
     * the first render of the state board had a perfectly legible, perfectly
     * black filename sitting on nothing, which reads as a bug rather than as
     * an unavailable control.
     *
     * It steps rather than fades: ink1 in place of ink0, ink2 in place of
     * ink1. Alpha is not used, for the reason in the class comment above.
     */
    fun ink(palette: Palette, base: Color): Color = when {
        this != Disabled -> base
        base == palette.ink0 -> palette.ink1
        else -> palette.ink2
    }

    /** Selection inverts the ground rather than tinting it - see [Palette]. */
    val invertsGround: Boolean get() = this == Selected
}
