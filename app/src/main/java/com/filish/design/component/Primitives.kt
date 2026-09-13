package com.filish.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Motion
import com.filish.design.Reach

/**
 * Draws a FILISH glyph.
 *
 * Takes the mark and a colour, nothing else. The size rounds to whole pixels
 * so a 1.9-unit stroke does not land on a half pixel and go grey.
 */
@Composable
fun Glyph(
    glyph: Glyphs.Glyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = Filish.palette.ink0,
) {
    val semantics = if (contentDescription == null) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(modifier.size(size).then(semantics)) {
        with(Glyphs) { paint(glyph, tint, this@Canvas.size.minDimension) }
    }
}

/**
 * FILISH's press feedback.
 *
 * Material's ripple is an expanding circle originating at the touch point - a
 * metaphor for a drop landing on water. It is decorative, it costs a
 * render-node per press, and it says nothing about what was pressed or what
 * will happen.
 *
 * FILISH does something simpler and more physical: the surface *takes weight*.
 * It darkens very slightly and contracts by a fraction of a percent, which is
 * how a real button behaves under a finger. It is immediate on press-down
 * (60ms, effectively instantaneous) and unhurried on release (180ms), because
 * confirming a touch registered must never lag, while the recovery is what
 * gives the interaction its sense of material.
 *
 * The scale is intentionally almost imperceptible. Anything more reads as a
 * toy, and on a list row - where the whole row presses - a visible shrink is
 * nauseating during fast scrolling.
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    contentDescription: String? = null,
    shape: RoundedCornerShape? = null,
    scaleOnPress: Boolean = true,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = Filish.a11y.reduceMotion
    val palette = Filish.palette

    val scale by animateFloatAsState(
        targetValue = if (pressed && scaleOnPress && !reduceMotion) 0.988f else 1f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (pressed) 60 else 180,
            easing = Motion.adjust,
        ),
        label = "pressScale",
    )
    val veil by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (pressed) 60 else 180,
            easing = Motion.adjust,
        ),
        label = "pressVeil",
    )

    val veilColor = if (palette.isDark) Color.White.copy(alpha = 0.055f)
    else Color.Black.copy(alpha = 0.045f)

    this
        .then(
            if (contentDescription != null) {
                Modifier.semantics {
                    this.contentDescription = contentDescription
                    if (role != null) this.role = role
                }
            } else if (role != null) {
                Modifier.semantics { this.role = role }
            } else {
                Modifier
            },
        )
        .scale(scale)
        .drawBehind {
            if (veil > 0f) {
                if (shape != null) {
                    val r = shape.topStart.toPx(size, this)
                    drawRoundRect(
                        veilColor.copy(alpha = veilColor.alpha * veil),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                } else {
                    drawRect(veilColor.copy(alpha = veilColor.alpha * veil))
                }
            }
        }
        .pointerInput(enabled, onClick, onLongClick) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = { offset ->
                    val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                    interaction.tryEmit(press)
                    val released = tryAwaitRelease()
                    interaction.tryEmit(
                        if (released) {
                            androidx.compose.foundation.interaction.PressInteraction.Release(press)
                        } else {
                            androidx.compose.foundation.interaction.PressInteraction.Cancel(press)
                        },
                    )
                },
                onTap = { onClick() },
                onLongPress = onLongClick?.let { handler -> { handler() } },
            )
        }
}

/**
 * A tappable glyph with a touch target larger than the mark it contains.
 *
 * The visual size and the hit size are separate parameters because they
 * answer different questions: how big should this look, and how big must it
 * be for a thumb. Conflating them produces either enormous icons or targets
 * that miss.
 */
@Composable
fun GlyphButton(
    glyph: Glyphs.Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyphSize: Dp = 22.dp,
    touchSize: Dp = Reach.touch,
    tint: Color = Filish.palette.ink1,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(touchSize)
            .pressable(
                onClick = onClick,
                enabled = enabled,
                contentDescription = contentDescription,
                shape = RoundedCornerShape(touchSize / 2),
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Glyph(glyph, null, size = glyphSize, tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

/** A hairline. Used sparingly - proximity does most of FILISH's grouping. */
@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = Filish.palette.line,
    thickness: Dp = Reach.line,
) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

/** Draws a rounded rectangle outline in device pixels. */
fun DrawScope.outline(color: Color, radiusPx: Float, strokeWidthPx: Float) {
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
        style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidthPx),
    )
}
