package com.filish.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.Motion

/** One band in a stratum bar. */
data class Stratum(
    val id: String,
    val label: String,
    val bytes: Long,
    val color: Color,
    /** Drawn hatched rather than solid - used for space FILISH cannot account
     *  for, so "we don't know" never masquerades as a measured category. */
    val uncertain: Boolean = false,
)

/**
 * THE STRATUM BAR - how FILISH shows what storage is made of.
 *
 * ---------------------------------------------------------------------------
 * Why not a donut
 *
 * Storage screens reach for a pie or donut chart almost reflexively. It is
 * the wrong instrument twice over.
 *
 * First, angle is among the hardest visual encodings for a person to compare
 * accurately, and length along a common axis is among the easiest. A storage
 * breakdown is *entirely* a set of comparisons - is video bigger than
 * photos, is this worth acting on - and a donut makes every one of those
 * comparisons harder than a bar would.
 *
 * Second, and more importantly, a donut of categories answers the wrong
 * question. It shows the composition of what is used, while the question the
 * user opened the screen with is "how full am I, and what is doing it". A
 * donut needs a separate number, usually in its hole, to answer the first
 * half.
 *
 * ---------------------------------------------------------------------------
 * What this does instead
 *
 * One band spanning the full width represents the whole volume. Categories
 * are laid along it in proportion, free space is simply the unfilled
 * remainder, and the space FILISH cannot see into is drawn hatched. So a
 * single element answers both questions at once: how full the device is, is
 * the extent of the filled region; what is filling it, is the composition of
 * that region; and what FILISH does not know is visibly marked rather than
 * quietly folded into "Other".
 *
 * That last part matters more than it looks. A file manager can only measure
 * what it can read - never the system partition or other apps' sandboxes -
 * and a breakdown whose parts silently fail to add up to the total is one a
 * user is right to stop trusting.
 *
 * Bands narrower than a finger are still selectable: taps resolve to the
 * nearest band, so a 200 MB category next to a 40 GB one is reachable.
 */
@Composable
fun StratumBar(
    strata: List<Stratum>,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    selectedId: String? = null,
    onSelect: ((String) -> Unit)? = null,
) {
    val palette = Filish.palette
    val reduce = Filish.a11y.reduceMotion

    val accounted = strata.sumOf { it.bytes }
    val denominator = maxOf(totalBytes, accounted, 1L)

    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduce) Motion.reduced() else Motion.considered(),
        label = "strataReveal",
    )

    val description = buildString {
        append("Storage composition. ")
        strata.take(5).forEach {
            append(it.label)
            append(": ")
            append(com.filish.core.model.Format.size(it.bytes))
            append(". ")
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .then(
                if (onSelect == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(strata, denominator) {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            var cursor = 0f
                            // Nearest-band resolution, so a sliver stays tappable.
                            var bestId: String? = null
                            var bestDistance = Float.MAX_VALUE
                            for (s in strata) {
                                val w = s.bytes.toFloat() / denominator
                                val centre = cursor + w / 2f
                                val d = kotlin.math.abs(fraction - centre)
                                if (fraction in cursor..(cursor + w)) {
                                    bestId = s.id
                                    break
                                }
                                if (d < bestDistance) {
                                    bestDistance = d
                                    bestId = s.id
                                }
                                cursor += w
                            }
                            bestId?.let(onSelect)
                        }
                    }
                },
            ),
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val r = CornerRadius(h / 2f, h / 2f)

            // Free space is the ground, not a category. It is the absence of
            // material, and drawing it as a coloured slice implies otherwise.
            drawRoundRect(palette.ground2, cornerRadius = r)

            var x = 0f
            // Hairline gaps between bands so adjacent categories of similar
            // tone remain countable.
            val gap = 1.5.dp.toPx()

            for (s in strata) {
                if (s.bytes <= 0) continue
                val bandW = (s.bytes.toFloat() / denominator) * w * reveal
                if (bandW <= 0.2f) continue
                val dim = selectedId != null && selectedId != s.id
                val color = if (dim) s.color.copy(alpha = 0.28f) else s.color

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size((bandW - gap).coerceAtLeast(1f), h),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )

                // Hatching marks measured-unknown rather than measured-zero.
                if (s.uncertain) {
                    val step = 6.dp.toPx()
                    var hx = x
                    while (hx < x + bandW - gap) {
                        drawLine(
                            color = palette.ground0.copy(alpha = 0.5f),
                            start = Offset(hx, h),
                            end = Offset(hx + h * 0.6f, 0f),
                            strokeWidth = 1.4.dp.toPx(),
                        )
                        hx += step
                    }
                }
                x += bandW
            }
        }
    }
}

/**
 * A number that is still being worked out.
 *
 * This is the visual counterpart to [com.filish.core.fs.SizeResolver] emitting
 * a stream rather than a value, and it is the component that makes measured
 * folder sizes feel like a feature rather than a wait.
 *
 * While a measurement is running the figure climbs, and a marker travels
 * beneath it. When the walk settles, the marker resolves to a full underline
 * and the figure stops. The user can read a usable approximation immediately
 * and knows, without being told, whether they are looking at a final answer.
 *
 * The number is never animated *downward*. A total that visibly falls reads
 * as an error even when it is a correction, so a lower value simply replaces
 * the previous one.
 */
@Composable
fun SettlingFigure(
    bytes: Long,
    settled: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = Filish.type.figureLarge,
    unitStyle: TextStyle = Filish.type.metaStrong,
    color: Color = Filish.palette.ink0,
    showMarker: Boolean = true,
) {
    val palette = Filish.palette
    val parts = com.filish.core.model.Format.sizeParts(bytes)

    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextCompat(parts.value, style.copy(color = color), maxLines = 1)
            Gap(com.filish.design.Space.bond)
            BasicTextCompat(
                parts.unit,
                unitStyle.copy(color = if (settled) palette.ink2 else palette.signal),
                maxLines = 1,
            )
        }
        if (showMarker) {
            Gap(com.filish.design.Space.bond)
            MeasurementMarker(settled = settled)
        }
    }
}

/**
 * The marker beneath a settling figure.
 *
 * Unsettled: a short segment travels beneath the number - the same visual
 * grammar as the Conduit's segments, so "work in progress" reads identically
 * everywhere in FILISH. Settled: a full-width rule in the quiet ink.
 */
@Composable
fun MeasurementMarker(
    settled: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 46.dp,
    accent: Color = Filish.palette.signal,
) {
    val palette = Filish.palette
    val reduce = Filish.a11y.reduceMotion
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(settled, reduce) {
        if (settled || reduce) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) phase = ((phase + (now - last) / 900_000_000.0) % 1.0).toFloat()
                last = now
            }
        }
    }

    Canvas(modifier.height(2.dp).width(width)) {
        if (settled) {
            drawRoundRect(
                palette.line,
                cornerRadius = CornerRadius(size.height, size.height),
            )
        } else if (reduce) {
            drawRoundRect(
                accent.copy(alpha = 0.45f),
                cornerRadius = CornerRadius(size.height, size.height),
            )
        } else {
            drawRoundRect(palette.ground2, cornerRadius = CornerRadius(size.height, size.height))
            val segW = size.width * 0.4f
            val x = -segW + phase * (size.width + segW)
            drawRoundRect(
                color = accent,
                topLeft = Offset(x.coerceAtLeast(0f), 0f),
                size = Size(
                    segW.coerceAtMost(size.width - x.coerceAtLeast(0f)).coerceAtLeast(0f),
                    size.height,
                ),
                cornerRadius = CornerRadius(size.height, size.height),
            )
        }
    }
}

/** A compact horizontal gauge for one volume's fullness. */
@Composable
fun VolumeGauge(
    usedFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp,
    tight: Boolean = false,
    critical: Boolean = false,
) {
    val palette = Filish.palette
    val accent = when {
        critical -> palette.danger
        tight -> palette.warn
        else -> palette.signal
    }
    Progression(
        fraction = usedFraction,
        modifier = modifier,
        height = height,
        accent = accent,
        track = palette.ground2,
    )
}
