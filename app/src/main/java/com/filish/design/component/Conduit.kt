package com.filish.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.Motion

/**
 * THE CONDUIT - FILISH's transfer visualisation.
 *
 * ---------------------------------------------------------------------------
 * Why not a progress bar
 *
 * A progress bar answers exactly one question - how far along - and it answers
 * it in a way that is indistinguishable from a bar that has frozen. A spinner
 * answers nothing at all; it is a promise that the application has not
 * crashed, rendered as a moving picture. Neither says what is happening, to
 * what, or where it is going, and a file transfer is precisely a question of
 * *what is going where*.
 *
 * What a person actually wants to know while files are moving is: is it
 * moving, which way, how fast, how much is left, and can I leave.
 *
 * ---------------------------------------------------------------------------
 * What this draws instead
 *
 * A band with two ends. The source sits at the left, the destination at the
 * right. Material *drains* from the left block and *accumulates* in the right
 * one, so the two solid masses are the progress reading - the left shrinking,
 * the right growing, conserved between them. Nothing is invented: the widths
 * are the byte fractions.
 *
 * Between them, discrete segments travel left to right. Their speed is tied
 * to measured throughput, not to a fixed animation loop. That single decision
 * is what makes this more honest than a progress bar: **a stalled transfer
 * visibly stops.** A copy that has hung on a locked file stops dead, and the
 * user knows within a second, without reading a number. A fast copy streams.
 * A slow one crawls. The animation is a readout, not decoration.
 *
 * When throughput is zero the segments stop and the ends stay put, which is
 * the correct and truthful thing for the interface to do.
 *
 * ---------------------------------------------------------------------------
 * Cost and accessibility
 *
 * Seven segments, one Canvas, no layers, no shadow, no blur. The phase
 * advances from a frame callback and stops entirely when the transfer stops,
 * so an idle conduit costs nothing.
 *
 * Under reduced motion the travelling segments are removed and the two masses
 * remain, animated only by the progress value itself. Every piece of
 * information survives; only the movement goes. The whole component carries a
 * progress semantic, so a screen reader hears a determinate percentage
 * regardless.
 */
@Composable
fun Conduit(
    fraction: Float,
    bytesPerSecond: Long,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    accent: Color = Filish.palette.signal,
    stalled: Boolean = false,
    contentDescription: String = "Transfer progress",
) {
    val palette = Filish.palette
    val reduceMotion = Filish.a11y.reduceMotion

    val target = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) Motion.reduced() else Motion.considered(),
        label = "conduitFraction",
    )

    // Phase advances only while bytes are actually moving.
    var phase by remember { mutableFloatStateOf(0f) }
    val moving = !reduceMotion && !stalled && bytesPerSecond > 0 && target < 1f

    LaunchedEffect(moving, bytesPerSecond) {
        if (!moving) return@LaunchedEffect
        // Map throughput to travel speed on a logarithmic curve. Linear would
        // make a 1 GB/s copy a strobe and a 200 KB/s copy look frozen; the log
        // keeps the whole realistic range legible as motion.
        val mbPerSec = (bytesPerSecond / 1_000_000.0).coerceIn(0.05, 600.0)
        val lapsPerSecond = (0.25 + kotlin.math.ln(1.0 + mbPerSec) * 0.30).coerceIn(0.25, 2.6)
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000.0
                    phase = ((phase + dt * lapsPerSecond) % 1.0).toFloat()
                }
                last = now
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                this.contentDescription = contentDescription
                this.progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f)
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val bandH = h * 0.44f
            val top = (h - bandH) / 2f
            val radius = CornerRadius(bandH / 2f, bandH / 2f)

            // The channel: where material will be, drawn faintly so the full
            // extent of the job is visible from the first frame.
            drawRoundRect(
                color = palette.ground2,
                topLeft = Offset(0f, top),
                size = Size(w, bandH),
                cornerRadius = radius,
            )

            // Source mass, draining. Never quite reaches zero while work
            // remains, so "still going" stays visible at 99%.
            val remaining = (1f - animated).coerceIn(0f, 1f)
            val sourceW = w * 0.42f * remaining
            if (sourceW > 0.5f) {
                drawRoundRect(
                    // Strong enough to read as material still waiting rather
                    // than as unfilled track.
                    color = accent.copy(alpha = 0.42f),
                    topLeft = Offset(0f, top),
                    size = Size(sourceW, bandH),
                    cornerRadius = radius,
                )
            }

            // Destination mass, accumulating from the right edge inward.
            val destW = w * animated
            if (destW > 0.5f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(w - destW, top),
                    size = Size(destW, bandH),
                    cornerRadius = radius,
                )
            }

            // Travelling segments occupy the gap between the two masses.
            if (moving) {
                val gapStart = sourceW
                val gapEnd = w - destW
                val gap = gapEnd - gapStart
                if (gap > bandH) {
                    // Elongated along the axis of travel. Circular segments
                    // are direction-neutral and read as decoration; a segment
                    // twice as long as it is tall reads as something moving.
                    val segW = bandH * 1.05f
                    // The count follows the space available rather than being
                    // fixed. With a constant count the segments overlap into a
                    // single smear as the gap closes toward the end of a
                    // transfer - which is exactly when the user is watching.
                    val count = (gap / (segW * 2.4f)).toInt().coerceIn(2, 7)
                    for (i in 0 until count) {
                        val p = ((phase + i.toFloat() / count) % 1f)
                        val x = gapStart + p * (gap - segW)
                        // Fade in and out at the ends so segments emerge from
                        // the source and are absorbed by the destination
                        // rather than popping into existence.
                        val edge = kotlin.math.min(p, 1f - p) * 4f
                        val alpha = edge.coerceIn(0f, 1f) * 0.85f
                        if (alpha > 0.02f) {
                            drawRoundRect(
                                color = accent.copy(alpha = alpha),
                                topLeft = Offset(x, top + bandH * 0.28f),
                                size = Size(segW, bandH * 0.42f),
                                cornerRadius = CornerRadius(bandH * 0.21f, bandH * 0.21f),
                            )
                        }
                    }
                }
            }

            // A stalled transfer is marked, not merely still - stillness alone
            // is ambiguous with "finished".
            if (stalled) {
                val y = top + bandH / 2f
                val x = w * 0.5f
                drawLine(
                    color = palette.warn,
                    start = Offset(x - bandH * 0.3f, y),
                    end = Offset(x + bandH * 0.3f, y),
                    strokeWidth = bandH * 0.18f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * The same language at rest: a determinate reading with no source or
 * destination, for work that has a measurable extent but no direction -
 * scanning, analysing, hashing.
 */
@Composable
fun Progression(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    accent: Color = Filish.palette.signal,
    track: Color = Filish.palette.ground2,
    contentDescription: String? = null,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (Filish.a11y.reduceMotion) Motion.reduced() else Motion.considered(),
        label = "progression",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        this.progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val r = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        if (animated > 0f) {
            drawRoundRect(accent, size = Size(size.width * animated, size.height), cornerRadius = r)
        }
    }
}

/**
 * Work with no knowable extent - a walk of unknown depth.
 *
 * Deliberately not a spinner. A slow sweep travels the width once, which
 * communicates "in progress" without implying a proportion that FILISH does
 * not know. Under reduced motion it becomes a static tinted band, because a
 * traversing element is exactly what reduced motion exists to remove.
 */
@Composable
fun Indeterminate(
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    accent: Color = Filish.palette.signal,
    active: Boolean = true,
) {
    val palette = Filish.palette
    val reduce = Filish.a11y.reduceMotion
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(active, reduce) {
        if (!active || reduce) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) phase = ((phase + (now - last) / 1_400_000_000.0) % 1.0).toFloat()
                last = now
            }
        }
    }

    Canvas(modifier.fillMaxWidth().height(height)) {
        drawRect(palette.ground2)
        if (!active) return@Canvas
        if (reduce) {
            drawRect(accent.copy(alpha = 0.5f))
        } else {
            val bandW = size.width * 0.32f
            val x = -bandW + phase * (size.width + bandW)
            drawRoundRect(
                color = accent,
                topLeft = Offset(x, 0f),
                size = Size(bandW, size.height),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            )
        }
    }
}
