package com.filish.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.Motion
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * THE OPENING.
 *
 * ---------------------------------------------------------------------------
 * Not a splash screen
 *
 * The conventional opening is a logo, a pause, a fade, and then the
 * application - a sequence whose only content is a brand impression, paid for
 * with the user's time on every single launch. It is also a lie about
 * performance: the pause usually exists because the app is not ready, and
 * where it is not needed it is added anyway.
 *
 * ---------------------------------------------------------------------------
 * What this does
 *
 * The FILISH mark is three strata - the same motif as the launcher icon and
 * the storage glyph. Here those three bars do not sit still and fade. They
 * *become the interface*: each stratum stretches to the full width of the
 * screen and settles into the position of the first rows of content, while
 * the real list resolves underneath. The identity turns into the thing the
 * identity is for.
 *
 * Two rules keep it from being a tax:
 *
 *   It never delays startup. The opening runs concurrently with enumerating
 *   volumes and reading settings, and it ends the moment both the work is
 *   done and a minimum has elapsed. On a fast device it is brief; on a slow
 *   one it covers work that was happening anyway. It never adds time, it only
 *   occupies time that already existed.
 *
 *   Under reduced motion the bars do not travel. They cross-fade, and the
 *   whole sequence shortens.
 *
 * This is a deliberately provisional first version - strong enough to ship,
 * left open for a dedicated motion pass later.
 * ---------------------------------------------------------------------------
 */
@Composable
fun Opening(
    ready: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion

    // 0 -> mark at rest. 1 -> strata have become the interface.
    val spread = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val wordmark = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // The system splash has already shown the mark and held until the app
        // was ready, so this picks up mid-gesture rather than introducing
        // itself again. The wordmark arrives beside the strata that are
        // already there; there is no separate reveal to sit through.
        launch {
            wordmark.animateTo(1f, tween(if (reduce) Motion.REDUCED else 200, easing = Motion.enter))
        }
        delay(if (reduce) 80 else 180)
    }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        if (reduce) {
            fade.animateTo(0f, tween(Motion.REDUCED))
        } else {
            spread.animateTo(1f, tween(Motion.DELIBERATE, easing = Motion.enter))
            fade.animateTo(0f, tween(Motion.BASE, easing = Motion.exit))
        }
        onFinished()
    }

    if (fade.value <= 0.01f) return

    Box(
        modifier
            .fillMaxSize()
            .background(palette.ground0)
            .semantics { contentDescription = "Filish is starting" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawStrata(
                spread = if (reduce) 0f else spread.value,
                alpha = fade.value,
                bar = palette.ink0,
                accent = palette.signal,
            )
        }
        if (wordmark.value > 0.01f && spread.value < 0.5f) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.padding(top = 128.dp)) {
                    BasicTextCompat(
                        "Filish",
                        type.wordmark.copy(
                            color = palette.ink0.copy(
                                alpha = wordmark.value * (1f - spread.value * 2f).coerceIn(0f, 1f) * fade.value,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The three strata, interpolating from mark to interface.
 *
 * At spread 0 they are the logo: short, centred, descending in length. At
 * spread 1 they span the content width at the vertical rhythm of the first
 * list rows. The third stratum keeps the accent colour throughout, so the eye
 * has one element to track through the whole transformation - which is what
 * makes it read as one object changing rather than as two separate pictures.
 */
private fun DrawScope.drawStrata(
    spread: Float,
    alpha: Float,
    bar: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    val markWidths = floatArrayOf(132f, 90f, 46f)
    val markHeight = 22f
    val markGap = 12f

    val gutter = 20.dp.toPx()
    val targetWidth = size.width - gutter * 2f
    val rowHeight = 58.dp.toPx()

    for (i in 0..2) {
        val startW = markWidths[i].dp.toPx()
        val startH = markHeight.dp.toPx()
        val startX = cx - startW / 2f
        val startY = cy - (startH * 1.5f + markGap.dp.toPx()) + i * (startH + markGap.dp.toPx())

        // Where this stratum ends up: the vertical position of list row i.
        val endW = targetWidth
        val endH = rowHeight * 0.34f
        val endX = gutter
        val endY = size.height * 0.30f + i * rowHeight

        // Staggered so the strata arrive in sequence rather than as a block -
        // the eye reads three objects moving, which is the point.
        val local = ((spread - i * 0.08f) / (1f - 0.16f)).coerceIn(0f, 1f)
        val eased = local * local * (3f - 2f * local)

        val w = startW + (endW - startW) * eased
        val h = startH + (endH - startH) * eased
        val x = startX + (endX - startX) * eased
        val y = startY + (endY - startY) * eased

        val color = if (i == 2) accent else bar
        drawRoundRect(
            color = color.copy(alpha = alpha * (1f - eased * 0.55f)),
            topLeft = Offset(x, y),
            size = Size(w.coerceAtLeast(1f), h.coerceAtLeast(1f)),
            cornerRadius = CornerRadius(h / 2.6f, h / 2.6f),
        )
    }
}
