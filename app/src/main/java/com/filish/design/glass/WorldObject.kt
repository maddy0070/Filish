package com.filish.design.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.filish.design.Palette

/**
 * An object in the world.
 *
 * ===========================================================================
 * Objects are not lenses
 * ===========================================================================
 *
 * V3 drew every row as a lens - a discrete piece of glass with a rim and a
 * radius, resting on the substrate. V3.1 separates the two, and the split is
 * the most important structural change in this round:
 *
 *   LENS    a transient surface that is NOT content - an action bar, a sheet,
 *           a confirmation, a ledger. It has a rim, a radius, a shadow and it
 *           will go away again. Modifier.lens().
 *
 *   OBJECT  the user's own files. No rim, no radius, no shadow. Cuts in a
 *           continuous body, distinguished by a mark and a hairline.
 *
 * The reason is the V3 density finding, taken to its conclusion. At list
 * spacing a column of lenses stopped reading as cards and started reading as
 * one body - so the rim, the radius and the corner geometry were doing nothing
 * except costing draw calls on the most frequent element in the application.
 * Content is a material you cut; chrome is an object you place.
 *
 * It is also what stops FILISH looking like every other dark file manager.
 * The control render - this list with the mark removed - is clean, legible and
 * completely anonymous. The mark is the difference.
 *
 * ===========================================================================
 * What the mark carries
 * ===========================================================================
 *
 *   width      magnitude, relative to the listing  (see [Mass])
 *   hue        kind
 *   intensity  state: full when known, reduced while a size is still resolving
 *   presence   this is a FILISH object
 *
 * Width, hue and intensity are separable channels, so they do not interfere;
 * width and intensity survive greyscale. This is a deliberate concentration -
 * one element carries a lot so that the rest of the row can carry almost
 * nothing, which is what makes the list quiet.
 */
fun Modifier.worldObject(
    palette: Palette,
    bytes: Long,
    largest: Long,
    tint: Color,
    selected: Boolean = false,
    illuminated: Boolean = false,
    provisional: Boolean = false,
    cut: Boolean = true,
): Modifier = drawWithCache {
    val dark = palette.isDark
    val markWidth = Mass.markWidth(bytes, largest).toPx()
    val hairline = 1.dp.toPx()
    val leading = layoutDirection == LayoutDirection.Ltr

    // Illumination deliberately does NOT appear here. See Light: the body of
    // an object is a text background, and light never touches one.
    val ground = if (selected) palette.selectGround else World.body(dark)

    // A selected row inverts its ground, so the mark has to move with it or the
    // row loses its magnitude the moment it is selected - which is exactly when
    // the user is deciding whether to delete it.
    val mark = if (selected) onInverted(tint, palette) else tint
    val markColour = when {
        provisional -> mark.copy(alpha = Mass.PROVISIONAL_INTENSITY)
        illuminated -> mark.copy(alpha = Light.ATTENTION)
        else -> mark.copy(alpha = Light.REST)
    }
    val bloomWidth = Light.bloom.toPx()
    val cutColour = World.cut(dark)

    onDrawBehind {
        drawRect(ground)
        val markX = if (leading) 0f else size.width - markWidth
        // The bloom: one extra rect just outside the mark. No blur, no layer,
        // and it sits in the gutter, so light never touches a text background.
        if (illuminated) {
            // A flat rect here reads as a SECOND mark, not as light - the
            // prototype rendered an orange bloom beside an orange mark and the
            // pair looked like a two-tone bar. Light falls off; a gradient is
            // the same single draw call.
            val bloomX = if (leading) markX + markWidth else markX - bloomWidth
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = if (leading) {
                        listOf(mark.copy(alpha = Light.BLOOM_ALPHA), mark.copy(alpha = 0f))
                    } else {
                        listOf(mark.copy(alpha = 0f), mark.copy(alpha = Light.BLOOM_ALPHA))
                    },
                    startX = bloomX,
                    endX = bloomX + bloomWidth,
                ),
                topLeft = Offset(bloomX, 0f),
                size = Size(bloomWidth, size.height),
            )
        }
        drawRect(
            color = markColour,
            topLeft = Offset(markX, 0f),
            size = Size(markWidth, size.height),
        )
        if (cut) {
            // Full width, ON TOP of the mark. Stopping the cut at the mark
            // fuses the marks of adjacent same-kind, same-size files into one
            // continuous bar, which says "one object" about three of them.
            drawRect(
                color = cutColour,
                topLeft = Offset(0f, size.height - hairline),
                size = Size(size.width, hairline),
            )
        }
    }
}

/**
 * A kind tint adjusted to read against the inverted ground of a selection.
 *
 * In night the selected ground is near-white, so the tint is darkened; in day
 * it is near-black, so the tint is lifted. Scaling toward the ground rather
 * than applying alpha, because alpha over an inverted ground washes every kind
 * toward the same colour and the hue channel is the point.
 */
private fun onInverted(tint: Color, palette: Palette): Color =
    if (palette.isDark) {
        Color(tint.red * 0.38f, tint.green * 0.38f, tint.blue * 0.38f, 1f)
    } else {
        Color(
            tint.red + (1f - tint.red) * 0.45f,
            tint.green + (1f - tint.green) * 0.45f,
            tint.blue + (1f - tint.blue) * 0.45f,
            1f,
        )
    }
