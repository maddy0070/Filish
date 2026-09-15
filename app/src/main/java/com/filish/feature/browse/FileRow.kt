package com.filish.feature.browse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Motion
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.spine.Spine
import com.filish.design.spine.contentObject
import kotlin.math.roundToInt

/**
 * One object in the browser. The production P · Spine row.
 *
 * ===========================================================================
 * MARK + INK, not CARD + ICON + TEXT
 * ===========================================================================
 *
 * At rest this row draws no container of any kind. What the user sees is the
 * substrate, one mark in the spine whose width is this object's magnitude, and
 * two lines of ink.
 *
 * THE LEADING GLYPH IS GONE, and that is deliberate rather than an omission.
 * A 40dp icon or thumbnail beside every name is the "card + icon + text"
 * pattern the direction rejects, and the kind it communicated is already in
 * the metadata line in words. Removing it reclaims roughly the same horizontal
 * space the 60dp spine inset costs, so the name column is no narrower than it
 * was - the row simply spends its leading space on a quantity instead of on a
 * decoration.
 *
 * ===========================================================================
 * The height is measured, never specified
 * ===========================================================================
 *
 * There is no fixed row height anywhere in this file. The name may wrap to two
 * lines, the metadata may grow with the user's font scale, and the row grows
 * with them. [Spine.minHeight] is a FLOOR for the finger, not a target: a
 * single-line row at default type lands a little under it and gets padded up
 * to it; anything larger is left alone.
 *
 * The mark's height follows the row, so the spine survives a font scale change
 * automatically - it is drawn from the measured size, not from a constant.
 *
 * ===========================================================================
 * Press is a well, not a ripple
 * ===========================================================================
 *
 * `indication = null` on the clickable is load-bearing: Compose's default
 * indication is a ripple, which would reintroduce a borrowed material on the
 * most-touched element in the application. The press state is collected
 * explicitly and handed to [contentObject], which cuts a well into the
 * substrate under the object. No scale, no shadow, no glow, and nothing that
 * affects a neighbouring row.
 *
 * ===========================================================================
 * The spine is never the only channel
 * ===========================================================================
 *
 * Size, kind and date are in the metadata line as text, and the whole row's
 * semantics carry them for TalkBack. A user who cannot perceive the mark loses
 * a glance, never a fact.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    node: FileNode,
    selected: Boolean,
    selectionActive: Boolean,
    facts: RowFacts?,
    showThumbnails: Boolean,
    showExtensions: Boolean,
    density: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Non-null while this row is animating away. */
    departing: Departure? = null,
    /** The quantised magnitude reference for this listing. */
    massScale: Long = 0L,
    /** Tones sampled from this object's own content, when available. */
    signature: List<Color>? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val bytes = magnitudeOf(node, facts)
    val measuring = node.isDirectory && facts?.measured?.settled == false
    val ink = if (selected) palette.selectInk else palette.ink0
    val inkQuiet = if (selected) palette.selectInk.copy(alpha = 0.72f) else palette.ink2

    /*
     * DEPARTURE. Unchanged from V2 in meaning, because it was right.
     *
     * TO TRASH: withdrawn toward the trailing edge - the direction the action
     * lives - while the height closes behind it. It reads as being claimed and
     * taken somewhere, which is what a recoverable delete is.
     *
     * DESTROYED: no lateral movement at all, because nothing is going
     * anywhere. The ink drains and the row closes straight down.
     */
    val leaving = departing != null
    val departure = animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = if (reduce) tween(Motion.REDUCED) else tween(Motion.DELIBERATE, easing = Motion.enter),
        label = "departure",
    ).value

    val displayName = if (showExtensions || node.isDirectory) node.name else node.stem
    // At a large font setting two lines is not enough for a real filename, and
    // a truncated name is the one thing a file manager must never do. The row
    // is content-height, so giving it a third line costs nothing but space it
    // already knows how to take.
    val nameLines = if (LocalDensity.current.fontScale >= 1.5f) 3 else 2
    val vertical = Spine.rowPadding * density

    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (leaving) {
                    Modifier
                        .graphicsLayer {
                            val t = ((departure - 0.2f) / 0.8f).coerceIn(0f, 1f)
                            alpha = 1f - t
                            if (departing == Departure.ToTrash) translationX = size.width * 0.32f * t * t
                            scaleY = 1f - t * 0.25f
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val t = ((departure - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            layout(placeable.width, (placeable.height * (1f - t)).roundToInt()) {
                                placeable.place(0, 0)
                            }
                        }
                } else {
                    Modifier
                },
            )
            // The floor, not the height. Content decides the rest.
            .defaultMinSize(minHeight = if (leaving) 0.dp else Spine.minHeight)
            .then(
                if (departing == Departure.ToTrash) {
                    // A departing row briefly takes a wash - a state, like
                    // selection, not a resting body.
                    Modifier.background(palette.signalWash.copy(alpha = (1f - departure).coerceIn(0f, 1f)))
                } else {
                    Modifier.contentObject(
                        palette = palette,
                        bytes = bytes,
                        scale = massScale,
                        tint = Glyphs.tintFor(node.kind, palette),
                        signature = if (showThumbnails) signature else null,
                        selected = selected,
                        pressed = pressed && !selected,
                        provisional = measuring,
                        illuminated = measuring,
                    )
                },
            )
            .combinedClickable(
                interactionSource = interaction,
                // No ripple. Press is the well drawn above.
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics {
                this.selected = selected
                this.contentDescription = accessibleDescription(node, facts)
            }
            .padding(
                start = Spine.textInset,
                end = Spine.trailing,
                top = vertical,
                bottom = vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicTextCompat(displayName, type.name.copy(color = ink), maxLines = nameLines)
            Gap(Space.bond)
            MetadataLineImpl(node, facts, inkQuiet, measuring)
        }
        if (selectionActive) {
            Gap(Space.near)
            SelectionTick(selected)
        }
    }
}

/**
 * The magnitude a row's mark represents.
 *
 * A file knows its size immediately. A folder's is resolved by a streaming
 * walk, so until the first partial result arrives the mark sits at its floor -
 * present, but making no claim.
 */
internal fun magnitudeOf(node: FileNode, facts: RowFacts?): Long =
    if (node.isDirectory) facts?.measured?.bytes ?: 0L else node.size

/**
 * The line beneath the name: how big, what kind, when.
 *
 * Size comes FIRST and is the only promoted piece, because the size is what
 * the mark is a picture of and the two must agree. Kind and date follow,
 * quieter. This is the "what is it / how big / when" hierarchy made literal.
 *
 * A folder being measured says so in words rather than through a spinner, and
 * reports the running total, so a partial figure is never mistaken for a
 * final one.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MetadataLineImpl(node: FileNode, facts: RowFacts?, color: Color, measuring: Boolean) {
    val type = Filish.type
    val pieces = buildList {
        if (node.isDirectory) {
            val measured = facts?.measured
            when {
                measured == null -> Unit
                measuring -> add("measuring · ${Format.size(measured.bytes)}")
                else -> add(Format.size(measured.bytes))
            }
            val n = facts?.childCount ?: -1
            if (n == 0) add("Empty") else if (n > 0) add(Format.plural(n, "item", "items"))
        } else {
            add(Format.size(node.size))
            if (node.extension.isNotEmpty()) add(node.extension.uppercase())
        }
        add(Format.relativeTime(node.lastModified))
    }
    // FLOW, not Row. At a 2.0 font scale a single line cannot hold
    // "6.24 GB - 4,180 items - 3 days ago" on a 411dp screen, and a Row
    // silently truncated the date. Wrapping costs a line of height on a row
    // that is already content-height; truncating costs the user a fact.
    FlowRow(verticalArrangement = Arrangement.Center) {
        pieces.forEachIndexed { i, piece ->
            // The separator travels WITH its piece so a wrap never leaves an
            // orphaned dot at the start of a line.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) {
                    Gap(Space.near - 1.dp)
                    // A drawn dot rather than a bullet character: it stays
                    // optically centred at every text size and no screen reader
                    // tries to pronounce it.
                    Canvas(Modifier.size(2.5.dp)) { drawCircle(color.copy(alpha = 0.55f)) }
                    Gap(Space.near - 1.dp)
                }
                BasicTextCompat(
                    piece,
                    if (i == 0) type.metaStrong.copy(color = color) else type.meta.copy(color = color),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A minimal trailing tick, shown ONLY while a selection is active.
 *
 * Selection's primary signal is the object gaining a body - that is the
 * signature, and it is what a run of selected files merging into one mass is
 * made of. This is a secondary, colour-independent confirmation for the case
 * where a single row is selected out of view of its neighbours, and it costs
 * nothing when no selection is running because it is not composed at all.
 *
 * It is deliberately not a checkbox column: a checkbox steals width from every
 * name forever in order to be useful during the small fraction of time a
 * selection exists, and it makes the state depend on a small glyph rather than
 * on the whole object changing.
 */
@Composable
private fun SelectionTick(selected: Boolean) {
    val palette = Filish.palette
    Canvas(Modifier.size(14.dp)) {
        val stroke = 1.6.dp.toPx()
        if (selected) {
            drawLine(
                color = palette.selectInk,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.54f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.80f),
                strokeWidth = stroke,
            )
            drawLine(
                color = palette.selectInk,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.80f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.22f),
                strokeWidth = stroke,
            )
        } else {
            drawCircle(
                color = palette.ink2.copy(alpha = 0.45f),
                radius = size.minDimension / 2f - stroke,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
        }
    }
}

/**
 * What a screen reader says about a row.
 *
 * Assembled as a sentence rather than read field by field, and ordered by
 * what matters: name, what it is, how big, when. Announcing "folder" before
 * the name would make every item in a directory of folders start identically,
 * which is exactly the situation a screen-reader user is trying to navigate.
 */
internal fun accessibleDescription(node: FileNode, facts: RowFacts?): String = buildString {
    append(node.name)
    append(", ")
    append(node.kind.label)
    if (node.isDirectory) {
        facts?.childCount?.takeIf { it >= 0 }?.let {
            append(", ")
            append(Format.plural(it, "item", "items"))
        }
        facts?.measured?.let {
            append(", ")
            append(Format.size(it.bytes))
            // Never let a partial total be heard as a final one - but do not
            // stay silent either, or a folder that is slow to walk simply has
            // no size for a screen-reader user.
            if (!it.settled) append(" so far, still measuring")
        }
    } else {
        append(", ")
        append(Format.size(node.size))
    }
    append(", modified ")
    append(Format.relativeTime(node.lastModified))
}

/** A bare box carrying only the spine mark, for headings and placeholders. */
@Composable
fun SpineGutter(modifier: Modifier = Modifier) {
    Box(modifier.padding(start = Spine.textInset))
}
