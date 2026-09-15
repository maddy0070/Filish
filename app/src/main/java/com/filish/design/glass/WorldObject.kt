package com.filish.design.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.LayoutDirection
import com.filish.design.Palette

/**
 * An object in the world.
 *
 * ===========================================================================
 * At rest, a file has no surface
 * ===========================================================================
 *
 * No rectangle, no container, no card, no rim, no radius, no shadow. A file is
 * a MARK IN THE SPINE and two lines of text on open ground.
 *
 * This is the V3.2 move and it is the one that gave FILISH authorship. V3.1's
 * filled rows were materially correct and visually anonymous; the control
 * render - the same screen with the mark removed - could have been any of a
 * dozen apps. Taking the rectangle away leaves the spine as the only structure
 * on screen, and the spine is the thing nobody else has.
 *
 * ===========================================================================
 * THE BODY IS A STATE
 * ===========================================================================
 *
 * A body appears under the finger and on selection. So the material that used
 * to be every row's default now means exactly one thing: this object is under
 * your control. Press and selection become one gesture at two strengths rather
 * than two unrelated effects, and a run of selected files reads as a single
 * solid body with the unselected ones cut out of it - which is what "these are
 * now a collection" should look like.
 *
 * ===========================================================================
 * THE MARK SHOWS THE MOST SPECIFIC THING AVAILABLE
 * ===========================================================================
 *
 *   a photograph   a vertical sliver of that photograph
 *   a video        a sliver of its poster frame
 *   a folder       a composite of what is inside it
 *   anything else  its kind tint
 *
 * The no-media control proved this is load-bearing. With flat category tints a
 * run of seven photographs is seven identical orange bars, and the spine says
 * only "these are images" - which the extension already said. With slivers the
 * spine is a record of the actual pictures, and a shot is findable by tone
 * before its filename is read.
 *
 * It is contrast-safe by construction: the spine is a gutter. No text sits on
 * it and none ever can, so unlike every other attempt to bring media into the
 * composition, the user's photographs cannot affect the legibility of a single
 * glyph.
 *
 * The cost, stated plainly: hue stops carrying KIND for media files. That is
 * the same discipline as everywhere else here - kind is in the metadata line,
 * in words, always. The mark is for the glance; the ink is for the answer.
 */
fun Modifier.worldObject(
    palette: Palette,
    bytes: Long,
    largest: Long,
    tint: Color,
    /** A sliver of the object's own content, when it has any. */
    media: ShaderBrush? = null,
    selected: Boolean = false,
    pressed: Boolean = false,
    illuminated: Boolean = false,
    provisional: Boolean = false,
): Modifier = drawWithCache {
    val dark = palette.isDark
    val markWidth = Mass.markWidth(bytes, largest).toPx()
    val gap = Mass.gap.toPx()
    val x = Mass.gutter.toPx()
    val leading = layoutDirection == LayoutDirection.Ltr
    val bloomWidth = Light.bloom.toPx()

    // The body is a state, never a default.
    val body: Color? = when {
        selected -> palette.selectGround
        pressed -> World.touched(dark)
        else -> null
    }

    val flat = if (selected) onInverted(tint, palette) else tint
    val markColour = if (provisional) flat.copy(alpha = Mass.PROVISIONAL_INTENSITY) else flat

    onDrawBehind {
        val markX = if (leading) x else size.width - x - markWidth
        val top = gap
        val height = size.height - gap * 2

        body?.let { drawRect(it) }

        if (illuminated) {
            // Light falls off. A flat rect beside the mark reads as a second
            // mark, not as light.
            val bloomX = if (leading) markX + markWidth else markX - bloomWidth
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = if (leading) {
                        listOf(flat.copy(alpha = Light.BLOOM_ALPHA), flat.copy(alpha = 0f))
                    } else {
                        listOf(flat.copy(alpha = 0f), flat.copy(alpha = Light.BLOOM_ALPHA))
                    },
                    startX = bloomX,
                    endX = bloomX + bloomWidth,
                ),
                topLeft = Offset(bloomX, top),
                size = Size(bloomWidth, height),
            )
        }

        if (media != null && !selected && !provisional) {
            drawRect(media, Offset(markX, top), Size(markWidth, height))
        } else {
            drawRect(markColour, Offset(markX, top), Size(markWidth, height))
        }
    }
}

/**
 * A kind tint adjusted to read against the inverted ground of a selection.
 *
 * Scaling toward the ground rather than applying alpha, because alpha over an
 * inverted ground washes every kind toward the same colour, and the hue
 * channel is the point. A selected object must keep both its magnitude and its
 * identity - selection is exactly when the user is deciding what to delete.
 */
private fun onInverted(tint: Color, palette: Palette): Color =
    if (palette.isDark) {
        Color(tint.red * 0.34f, tint.green * 0.34f, tint.blue * 0.34f, 1f)
    } else {
        Color(
            tint.red + (1f - tint.red) * 0.45f,
            tint.green + (1f - tint.green) * 0.45f,
            tint.blue + (1f - tint.blue) * 0.45f,
            1f,
        )
    }
