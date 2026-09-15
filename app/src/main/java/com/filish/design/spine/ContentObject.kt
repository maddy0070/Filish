package com.filish.design.spine

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import com.filish.design.Palette
import com.filish.design.glass.Light
import com.filish.design.glass.Mass
import com.filish.design.glass.World

/**
 * A content object: the production P · Spine row surface.
 *
 * ===========================================================================
 * At rest a file has no body
 * ===========================================================================
 *
 * No card, no filled rectangle, no glass panel, no border, no shadow, no
 * rounded container. The user sees SUBSTRATE + MARK + INK, and that is the
 * default state of every object in FILISH.
 *
 * This is the defining characteristic of the design language, and it is also
 * the easiest thing to lose by accident: any Material `Surface`, `Card`,
 * `ListItem` or `Button` brings a container with it. Everything drawn here is
 * drawn explicitly, by this one modifier, so there is exactly one place a body
 * can come from and it is auditable.
 *
 * ===========================================================================
 * The body is a state
 * ===========================================================================
 *
 *   PRESSED   a WELL is cut into the substrate under the object. Content has
 *             no rim to invert - only chrome has edges - so the well is a fill
 *             rather than a lit recess, which is the same material logic
 *             expressed in the one channel content actually has.
 *   SELECTED  the object materialises: a full inversion of the ground.
 *
 * Because rows abut and share a ground, a run of selected objects merges into
 * ONE COLLECTIVE BODY with the unselected ones cut out of it. That is what
 * "these are now under my control" should look like, and it is free - no
 * grouping logic, no first/last rounding, just adjacency.
 *
 * ===========================================================================
 * One draw modifier, one pass
 * ===========================================================================
 *
 * Body, mark, bloom and all state are drawn in a single `drawWithCache` block.
 * On a 5,000-entry listing this is the hot path, so there are no nested draw
 * modifiers, no extra layout nodes for the mark, no graphics layers and no
 * offscreen buffers. Brushes are built in the cache block, which re-runs on
 * size or parameter change rather than per frame.
 */
fun Modifier.contentObject(
    palette: Palette,
    /** Magnitude in bytes. Use 0 when genuinely unknown. */
    bytes: Long,
    /** The quantised reference for this listing. See [Mass.quantiseLargest]. */
    scale: Long,
    /** Fallback identity when no media signature exists. */
    tint: Color,
    /**
     * A media signature: two or three tones sampled from the object's own
     * thumbnail. Null for everything that has no visual content, which is most
     * files and every folder today.
     */
    signature: List<Color>? = null,
    selected: Boolean = false,
    pressed: Boolean = false,
    /** The size is still being measured, so the mark is provisional. */
    provisional: Boolean = false,
    /** The system is doing something to this object right now. */
    illuminated: Boolean = false,
): Modifier = drawWithCache {
    val dark = palette.isDark
    val markWidth = Mass.markWidth(bytes, scale).toPx()
    val gap = Spine.markGap.toPx()
    val x = Spine.gutter.toPx()
    val leading = layoutDirection == LayoutDirection.Ltr
    val bloomWidth = Light.bloom.toPx()

    // The body is a state, never a default.
    val body: Color? = when {
        selected -> palette.selectGround
        pressed -> World.touched(dark)
        else -> null
    }

    val identity = if (selected) onInverted(tint, palette) else tint
    val markColour = if (provisional) identity.copy(alpha = Mass.PROVISIONAL_INTENSITY) else identity

    // A signature is a handful of colours, not a bitmap. See MediaSignature:
    // drawing the thumbnail itself into a 26dp strip costs a bitmap draw per
    // row per frame for detail nobody can resolve at that width.
    val signatureBrush: Brush? = signature
        ?.takeIf { it.size >= 2 && !provisional }
        ?.map { if (selected) onInverted(it, palette) else it }
        ?.let { Brush.verticalGradient(it) }

    onDrawBehind {
        val markX = if (leading) x else size.width - x - markWidth
        val top = gap
        // A row shorter than two gaps would invert; clamp rather than crash.
        val height = (size.height - gap * 2).coerceAtLeast(1f)

        body?.let { drawRect(it) }

        if (illuminated) {
            // Light falls off. A flat rect beside the mark reads as a second
            // mark rather than as light.
            val bloomX = if (leading) markX + markWidth else markX - bloomWidth
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = if (leading) {
                        listOf(identity.copy(alpha = Light.BLOOM_ALPHA), identity.copy(alpha = 0f))
                    } else {
                        listOf(identity.copy(alpha = 0f), identity.copy(alpha = Light.BLOOM_ALPHA))
                    },
                    startX = bloomX,
                    endX = bloomX + bloomWidth,
                ),
                topLeft = Offset(bloomX, top),
                size = Size(bloomWidth, height),
            )
        }

        if (signatureBrush != null) {
            drawRect(signatureBrush, Offset(markX, top), Size(markWidth, height))
        } else {
            drawRect(markColour, Offset(markX, top), Size(markWidth, height))
        }
    }
}

/**
 * A tint adjusted to read against the inverted ground of a selection.
 *
 * Scaled toward the ground rather than alpha-faded, because alpha over an
 * inverted ground washes every kind toward the same colour and the hue channel
 * is the point. A selected object must keep both its magnitude and its
 * identity - selection is exactly when the user is deciding what to delete.
 */
internal fun onInverted(tint: Color, palette: Palette): Color =
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
