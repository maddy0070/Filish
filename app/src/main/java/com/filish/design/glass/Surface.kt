package com.filish.design.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The material, as drawing operations.
 *
 * Everything in this file is a fill, a gradient, a hairline stroke or a small
 * offset rectangle. There is no offscreen buffer, no RenderEffect, no
 * saveLayer, and nothing whose behaviour differs between API 26 and API 35 -
 * which is the entire reason the direction was chosen. What the material does
 * is described in [Material]; this is that description compiled.
 */

/**
 * Paint a surface as the substrate: the ground everything else rests on.
 *
 * Two stops, very close together. The gradient is almost invisible and that is
 * correct - it is not decoration, it is the reason a lens has somewhere to sit.
 * Against a perfectly flat ground a translucent white panel is indetectable at
 * the top of the screen and obvious at the bottom, which reads as a bug.
 */
fun Modifier.substrate(skin: Skin): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(listOf(skin.airHigh, skin.airLow))
    onDrawBehind { drawRect(brush) }
}

/**
 * Paint a surface as the volume: storage with a level.
 *
 * [fullness] is the proportion of the space that is USED, so the material
 * fills from the bottom and the level rises as the device fills. That is the
 * right way round for a measurement of occupancy, and it is the opposite of a
 * progress bar, which fills toward completion. The two must never be confused,
 * so they never share a shape: progress is a track, occupancy is a body.
 *
 * Only use this where storage is the subject. See [Skin.volumeHigh].
 */
fun Modifier.volume(skin: Skin, fullness: Float): Modifier = drawWithCache {
    val air = Brush.verticalGradient(listOf(skin.airHigh, skin.airLow))
    val level = size.height * (1f - fullness.coerceIn(0f, 1f))
    val body = Brush.verticalGradient(
        colors = listOf(skin.volumeHigh, skin.volumeLow),
        startY = level,
        endY = size.height,
    )
    val meniscusWidth = 1.6.dp.toPx()
    onDrawBehind {
        drawRect(air)
        drawRect(
            brush = body,
            topLeft = Offset(0f, level),
            size = Size(size.width, size.height - level),
        )
        drawLine(
            color = skin.meniscus,
            start = Offset(0f, level),
            end = Offset(size.width, level),
            strokeWidth = meniscusWidth,
        )
    }
}

/**
 * Paint a surface as a lens: thin glass resting on the substrate.
 *
 * Four operations, in the order light actually reaches the eye:
 *
 *   1. the tinted body, brighter at the edge light enters
 *   2. the rim, drawn in whichever tonal direction the ground leaves room for
 *   3. the refraction band at the leading edge, if this object has one
 *
 * plus a real platform shadow, for the three tiers that have genuinely left
 * the ground. A resting row has none: see [Material.contactAlpha].
 *
 * [press] is 0 at rest and 1 fully pressed, and it makes the lens SINK until
 * it is a well - fill inverted, rim light flipped to the far side. There is no
 * ripple and no scale. See [Skin.bodyStops] for why that is the material's own
 * logic rather than an effect chosen for it.
 *
 * [band] is the file-kind tint, or null for an object with no kind. Passing a
 * band is what makes something read as a FILISH object rather than a panel.
 *
 * [rimScale] thickens the edge. Only focus uses it - see [Response] - because
 * a keyboard or D-pad user has no pointer to look at and needs a mark that
 * cannot be missed, and a thicker edge is the one emphasis available that does
 * not borrow a colour the file-kind system is already using.
 */
fun Modifier.lens(
    skin: Skin,
    tier: Material.Tier,
    shape: Shape = Facet.lens,
    press: Float = 0f,
    hover: Float = 0f,
    band: Color? = null,
    selected: Boolean = false,
    reduceMotion: Boolean = false,
    rimScale: Float = 1f,
): Modifier {
    // The substrate is not a surface you can put on top of the substrate. A
    // component asking for it is asking to disappear into the ground, which is
    // exactly what Response.Disabled means, so this draws nothing and that is
    // the correct answer rather than a missing case.
    if (tier == Material.Tier.Substrate) return this

    val sunk = press.coerceIn(0f, 1f)
    val shadowed = if (Material.castsRealShadow(tier) && Material.contactAlpha(tier, skin.isDark) > 0f) {
        this.shadow(
            elevation = Material.contactOffset(tier) * (1f - sunk * 0.6f),
            shape = shape,
            clip = false,
            ambientColor = skin.contact,
            spotColor = skin.contact,
        )
    } else {
        this
    }
    return shadowed.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val silhouette = Path().apply { addOutline(outline) }

        // 1. Body. Pressing turns the lens into a well - see Skin.bodyStops.
        val (top, bottom) = skin.bodyStops(tier, sunk, hover.coerceIn(0f, 1f))
        val body = Brush.verticalGradient(listOf(top, bottom))

        // 2. Rim. One stroke, drawn in whichever tonal direction has headroom
        //    - shade in day, light in night - and flipping to the recess
        //    direction as the lens sinks. See Material.rim.
        val (rimTop, rimBottom) = skin.rimStopsFor(tier, sunk)
        val rimBrush = Brush.verticalGradient(listOf(rimTop, rimBottom))
        val rimWidth = Facet.rim.toPx() * rimScale

        // 3. Band.
        val bandWidth = Facet.band.toPx()
        val bandBrush = band?.let {
            if (reduceMotion) {
                Brush.verticalGradient(
                    listOf(
                        it.copy(alpha = Band.flatAlpha(selected)),
                        it.copy(alpha = Band.flatAlpha(selected)),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        it.copy(alpha = Band.topAlpha(selected)),
                        it.copy(alpha = Band.bottomAlpha(selected)),
                    ),
                )
            }
        }
        val bandAtStart = layoutDirection == LayoutDirection.Ltr

        onDrawBehind {
            drawOutline(outline, body)
            if (bandBrush != null) {
                clipPath(silhouette) {
                    drawRect(
                        brush = bandBrush,
                        topLeft = Offset(if (bandAtStart) 0f else size.width - bandWidth, 0f),
                        size = Size(bandWidth, size.height),
                    )
                }
            }
            drawOutline(
                outline = outline,
                brush = rimBrush,
                style = Stroke(width = rimWidth),
            )
        }
    }
}
