package com.filish.feature.browse

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.filish.core.model.Format
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Motion
import com.filish.design.Space
import com.filish.design.component.ActionWeight
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishAction
import com.filish.design.component.FilishChip
import com.filish.design.component.Gap
import com.filish.design.component.GlyphButton
import com.filish.design.component.RaisedSurface
import com.filish.design.component.SettlingFigure

/**
 * THE SELECTION LEDGER.
 *
 * ---------------------------------------------------------------------------
 * This is the component FILISH was built around.
 *
 * Select four folders and two videos anywhere else and you are told "6
 * selected" - the one fact you already had. The ledger instead leads with the
 * number you actually wanted: how much space this is. Files contribute
 * immediately; folders are walked in the background and the figure climbs
 * toward the truth, with a travelling marker beneath it that resolves to a
 * rule once the walk settles. A usable approximation in the first hundred
 * milliseconds, an exact answer a moment later, and never any ambiguity about
 * which one you are looking at.
 *
 * The alternative - what people actually do today - is to create a temporary
 * folder, move everything into it, open its properties, and move everything
 * back. That is the problem this solves.
 *
 * ---------------------------------------------------------------------------
 * Hierarchy
 *
 * The size is the largest thing here, set in the display face, because it is
 * the answer. The composition ("4 folders, 2 files") is secondary. The count
 * alone appears nowhere on its own, because on its own it is not worth the
 * space.
 *
 * Refinements sit between the figure and the actions: they change *what* is
 * selected, so they belong nearer the thing they change than the actions that
 * consume it. They are derived from the actual listing, so a folder with no
 * videos never offers "Videos", and the thresholds come from the content's
 * own distribution rather than from constants.
 *
 * The figure is a live region, so a screen reader hears the total settle
 * instead of being told a number that was already stale when it was read.
 */
@Composable
fun SelectionLedger(
    selection: Selection,
    /** True while the user is choosing, even before anything is chosen. */
    choosing: Boolean = false,
    refinements: List<SelectionRefinement>,
    onRefine: (SelectionRefinement) -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion

    AnimatedVisibility(
        visible = selection.isActive || choosing,
        enter = if (reduce) fadeIn(Motion.reduced()) else slideInVertically(Motion.base()) { it } + fadeIn(Motion.quick()),
        exit = if (reduce) fadeOut(Motion.reduced()) else slideOutVertically(Motion.leaving()) { it } + fadeOut(Motion.leaving()),
        modifier = modifier,
    ) {
        RaisedSurface(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = Space.gutter, end = Space.gutter,
                        top = Space.group + 2.dp, bottom = Space.group + 2.dp,
                    ),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(
                        Modifier
                            .weight(1f)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = announcement(selection)
                            },
                    ) {
                        if (!selection.isActive) {
                            // Entering selection mode with nothing chosen yet.
                            // The empty state explains the mode rather than
                            // showing a meaningless "0 B".
                            BasicTextCompat(
                                "Choose files",
                                type.heading.copy(color = palette.ink0),
                            )
                            Gap(Space.bond + 1.dp)
                            BasicTextCompat(
                                "Tap anything in the list. Filish adds up their real size " +
                                    "as you go, folders included.",
                                type.meta.copy(color = palette.ink2),
                            )
                        } else {
                        SettlingFigure(
                            bytes = selection.displayBytes,
                            settled = selection.isSettled,
                            style = type.figureHuge,
                            unitStyle = type.heading,
                            showMarker = selection.needsMeasurement,
                        )
                        Gap(Space.near - 2.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextCompat(
                                selection.describe(),
                                type.metaStrong.copy(color = palette.ink1),
                                maxLines = 1,
                            )
                            selection.measurementNote()?.let { note ->
                                BasicTextCompat(
                                    "  ·  $note",
                                    type.meta.copy(color = palette.ink2),
                                    maxLines = 1,
                                )
                            }
                        }
                        }
                    }
                    GlyphButton(
                        Glyphs.Close, "Stop choosing", onDismiss,
                        glyphSize = 20.dp, touchSize = 42.dp, tint = palette.ink2,
                    )
                }

                if (refinements.isNotEmpty()) {
                    Gap(Space.group)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
                        items(refinements, key = { it.label }) { refinement ->
                            FilishChip(
                                label = refinement.label,
                                selected = false,
                                onClick = { onRefine(refinement) },
                                count = countFor(refinement),
                            )
                        }
                    }
                }

                Gap(Space.group + 2.dp)
                val enabled = selection.isActive
                Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
                    FilishAction(
                        "Copy", onCopy, Modifier.weight(1f),
                        weight = ActionWeight.Secondary, glyph = Glyphs.Copy,
                        enabled = enabled,
                    )
                    FilishAction(
                        "Move", onMove, Modifier.weight(1f),
                        weight = ActionWeight.Secondary, glyph = Glyphs.Move,
                        enabled = enabled,
                    )
                    GlyphButton(
                        Glyphs.Share, "Share selection", onShare,
                        tint = palette.ink1, enabled = enabled,
                    )
                    GlyphButton(
                        Glyphs.Trash, "Delete selection", onDelete,
                        tint = palette.danger, enabled = enabled,
                    )
                    GlyphButton(
                        Glyphs.More, "More actions", onMore,
                        tint = palette.ink1, enabled = enabled,
                    )
                }
            }
        }
    }
}

private fun countFor(refinement: SelectionRefinement): Int? = when (refinement) {
    is SelectionRefinement.ByKind -> refinement.count
    is SelectionRefinement.LargerThan -> refinement.count
    is SelectionRefinement.OlderThan -> refinement.count
    else -> null
}

/**
 * What a screen reader hears.
 *
 * Says explicitly whether the figure is final. A blind user cannot see the
 * travelling marker that conveys this visually, and being read a total that
 * is still climbing as though it were the answer is worse than being told
 * nothing.
 */
private fun announcement(selection: Selection): String = buildString {
    if (!selection.isActive) {
        append("Choosing files. Nothing selected yet.")
        return@buildString
    }
    append(selection.describe())
    append(" selected. ")
    if (selection.isSettled) {
        append(Format.size(selection.displayBytes))
        append(" in total.")
    } else {
        append("Measuring. ")
        append(Format.size(selection.displayBytes))
        append(" so far.")
    }
}
