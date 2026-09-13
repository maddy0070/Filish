package com.filish.feature.operations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.filish.core.fs.ops.OperationProgress
import com.filish.core.fs.ops.OperationState
import com.filish.core.model.Format
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Motion
import com.filish.design.Space
import com.filish.design.component.ActionWeight
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Conduit
import com.filish.design.component.FilishAction
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.RaisedSurface

/**
 * A transfer in flight.
 *
 * ---------------------------------------------------------------------------
 * Not a modal dialog
 *
 * Conventional file managers block the interface during a copy. That is a
 * choice made for the programmer's convenience - there is no state to
 * reconcile if the user cannot do anything - and it is paid for by the user,
 * who cannot look at another folder while 8 GB moves.
 *
 * FILISH runs the operation in the background and surfaces it as a bar the
 * user can ignore. Browsing continues. The operation is owned by a scope that
 * outlives the screen, so navigating away does not cancel it.
 *
 * ---------------------------------------------------------------------------
 * What is shown, and what is deliberately not
 *
 * Progress engines can produce a dozen metrics, and showing all of them is a
 * way of showing none. FILISH shows, at any moment, only what the user could
 * act on:
 *
 *   ALWAYS - what is moving, where to, and the Conduit, which carries
 *   direction, proportion and live throughput in one reading.
 *
 *   WHILE PREPARING - that it is preparing. Enumerating a large tree takes
 *   real time, and a bar sitting at zero during it looks like a hang. Saying
 *   "working out what this involves" is the honest description.
 *
 *   ONCE THE RATE HAS SETTLED - the remaining time. Not before: an ETA
 *   computed from the first half-second is noise, and a figure that swings
 *   from three hours to twenty seconds teaches the user to disbelieve it.
 *
 *   ONLY WHEN NON-ZERO - skipped items and errors. A permanent "0 errors"
 *   readout is visual noise that makes the appearance of a real error harder
 *   to notice.
 *
 * Byte counts and throughput are available but subordinate. They are how the
 * transfer is doing, not what it is doing.
 */
@Composable
fun TransferBar(
    progress: OperationProgress?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion

    AnimatedVisibility(
        visible = progress != null,
        enter = if (reduce) fadeIn(Motion.reduced()) else slideInVertically(Motion.base()) { it } + fadeIn(Motion.quick()),
        exit = if (reduce) fadeOut(Motion.reduced()) else slideOutVertically(Motion.leaving()) { it } + fadeOut(Motion.leaving()),
        modifier = modifier,
    ) {
        val p = progress ?: return@AnimatedVisibility
        val preparing = p.state == OperationState.Preparing
        val stalled = !preparing && p.bytesPerSecond == 0L &&
            System.currentTimeMillis() - p.startedAt > 3_000

        RaisedSurface(Modifier.fillMaxWidth().padding(Space.near)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.group + 2.dp, vertical = Space.group),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(
                        if (p.kind == com.filish.core.fs.ops.OperationKind.Move) Glyphs.Move else Glyphs.Copy,
                        null, size = 18.dp, tint = palette.signal,
                    )
                    Gap(Space.near + 1.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = announcement(p, preparing)
                            },
                    ) {
                        BasicTextCompat(
                            if (preparing) {
                                "Working out what this involves"
                            } else {
                                "${p.kind.gerund} to ${p.destinationLabel}"
                            },
                            type.name.copy(color = palette.ink0), maxLines = 1,
                        )
                        Gap(Space.bond)
                        BasicTextCompat(
                            detailLine(p, preparing),
                            type.meta.copy(color = palette.ink2), maxLines = 1,
                        )
                    }
                    Gap(Space.near)
                    FilishAction("Stop", onCancel, weight = ActionWeight.Quiet)
                }

                Gap(Space.group - 2.dp)

                if (preparing) {
                    com.filish.design.component.Indeterminate(active = true)
                } else {
                    Conduit(
                        fraction = p.fraction,
                        bytesPerSecond = p.bytesPerSecond,
                        stalled = stalled,
                        contentDescription = announcement(p, false),
                    )
                }

                if (stalled) {
                    Gap(Space.near)
                    BasicTextCompat(
                        "Nothing has moved for a few seconds. The file may be in use by " +
                            "another app.",
                        type.meta.copy(color = palette.warn),
                    )
                }

                if (p.skipped > 0 || p.errors.isNotEmpty()) {
                    Gap(Space.near)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.group)) {
                        if (p.skipped > 0) {
                            BasicTextCompat(
                                "${p.skipped} skipped",
                                type.meta.copy(color = palette.ink2),
                            )
                        }
                        if (p.errors.isNotEmpty()) {
                            BasicTextCompat(
                                "${p.errors.size} could not be ${p.kind.past.lowercase()}",
                                type.meta.copy(color = palette.warn),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The line under the headline.
 *
 * Progressively specific: item counts always, bytes once they are meaningful,
 * an estimate only once the rate has settled enough to be worth stating.
 */
private fun detailLine(p: OperationProgress, preparing: Boolean): String {
    if (preparing) return "Reading the folders involved"
    val pieces = ArrayList<String>(3)
    pieces.add("${Format.count(p.itemsDone)} of ${Format.count(p.itemsTotal)}")
    if (p.bytesTotal > 0) {
        pieces.add("${Format.size(p.bytesDone)} of ${Format.size(p.bytesTotal)}")
    }
    p.etaMillis?.let { pieces.add("${Format.eta(it)} left") }
    return pieces.joinToString("  ·  ")
}

private fun announcement(p: OperationProgress, preparing: Boolean): String = when {
    preparing -> "${p.kind.gerund}. Preparing."
    else -> "${p.kind.gerund} to ${p.destinationLabel}. " +
        "${(p.fraction * 100).toInt()} per cent. " +
        "${Format.count(p.itemsDone)} of ${Format.count(p.itemsTotal)} items."
}
