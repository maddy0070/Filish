package com.filish.design.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The V3 shape language.
 *
 * ===========================================================================
 * A revision, stated openly
 * ===========================================================================
 *
 * V2's position was "FILISH rounds almost nothing" - see
 * [com.filish.design.Corner]. That was right for a system built out of tone
 * and space, where a rounded rectangle would have been an unearned claim that
 * something is a discrete container.
 *
 * V3 changes the claim, so it changes the shape. A lens IS a discrete object:
 * it is a piece of material with a cut edge resting on a ground. Giving it no
 * radius would make it a painted region rather than a thing. The old tokens
 * stay for the components that are still painted regions; new material uses
 * these.
 *
 * ===========================================================================
 * The rule, which is the only interesting part
 * ===========================================================================
 *
 * ASYMMETRIC shapes rest ON the substrate and can be acted on. Their leading
 * edge is nearly square, their trailing edge is soft.
 *
 * SYMMETRIC shapes are cut INTO the substrate - wells, fields, gauge tracks -
 * or arrive from a screen edge. Nothing rests on them and they have no leading
 * edge, so they have no direction.
 *
 * So the silhouette alone tells you whether something is an object or a hole,
 * before any colour or label is read. That is a shape language doing work,
 * rather than a radius scale.
 *
 * ===========================================================================
 * Why the leading edge is nearly square
 * ===========================================================================
 *
 * Because the refraction band lives there, 4dp wide and full height. Put that
 * against a 12dp corner and the band gets clipped into a pair of crescents
 * that read as a rendering artefact - the exploration drew it, and it looked
 * broken. 4dp of radius against a 4dp band keeps the band a band while still
 * killing the hard mathematical corner that makes an interface feel like a
 * spreadsheet.
 *
 * The result is also directional: soft at the trailing edge, crisp at the
 * leading one, so the object reads as entering from the start side. In RTL
 * that reverses for free, because these are start/end corners rather than
 * left/right ones, and the band is drawn at the leading edge for the same
 * reason.
 */
@Immutable
object Facet {

    /** A row, a card, a file. The primary shape of the system. */
    val lens: Shape = RoundedCornerShape(
        topStart = 4.dp, bottomStart = 4.dp,
        topEnd = 12.dp, bottomEnd = 12.dp,
    )

    /** Chips, tokens, small pressable controls. The lens, scaled down. */
    val token: Shape = RoundedCornerShape(
        topStart = 3.dp, bottomStart = 3.dp,
        topEnd = 10.dp, bottomEnd = 10.dp,
    )

    /** Large panels: the storage gauge, a group header, a result block. */
    val slab: Shape = RoundedCornerShape(
        topStart = 6.dp, bottomStart = 6.dp,
        topEnd = 16.dp, bottomEnd = 16.dp,
    )

    /** A sheet arrives from the bottom edge, so its lower corners do not
     *  exist. Symmetric: it is not resting on anything, it is covering. */
    val sheet: Shape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = 0.dp, bottomEnd = 0.dp,
    )

    /** Cut into the substrate: search fields, input wells, gauge tracks. */
    val well: Shape = RoundedCornerShape(10.dp)

    /** A picture has its own edges; it gets the smallest radius that stops it
     *  looking pasted on. */
    val thumb: Shape = RoundedCornerShape(5.dp)

    /** Hairline. A real 1dp, so it survives low-density panels. */
    val rim: Dp = 1.dp

    /** The refraction band. Mirrors [Band.width] - one number, one meaning. */
    val band: Dp = Band.width

    /**
     * How far content must be inset past the band before it starts.
     *
     * The band is not padding, it is part of the object's edge, so text that
     * begins immediately after it reads as crowded against a bright line. This
     * is the gap that stops that.
     */
    val bandGutter: Dp = 12.dp
}
