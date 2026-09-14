package com.filish.feature.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.filish.core.media.ThumbnailLoader
import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.core.model.Kinds
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Motion
import com.filish.design.Reach
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.MeasurementMarker
import com.filish.design.component.pressable
import com.filish.filish

/**
 * One entry in the browser.
 *
 * The row is the most-read object in FILISH, so its hierarchy is decided
 * rather than inherited:
 *
 *   NAME is the only thing at full ink. It is what the user is scanning for.
 *   METADATA sits directly beneath at the "near" distance, in recessive ink,
 *     because it qualifies the name rather than competing with it.
 *   THE MARK is a glyph or a thumbnail - a thumbnail whenever one exists,
 *     because for photographs and video the picture *is* the name.
 *
 * There is no divider between rows. The gap does the separating; a line
 * between every pair of rows makes a list of forty files into a list of forty
 * boxes, and the eye then has to cross a border for every item it scans.
 *
 * SELECTION inverts the row's ground and adds a leading marker. It does not
 * add a checkbox. A checkbox column steals horizontal space from names on
 * every row forever, in order to be useful during the small fraction of time
 * a selection is active, and it makes the selected state depend on a small
 * glyph rather than on the whole row changing.
 */
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
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion

    val ground by animateColorAsState(
        if (selected) palette.selectGround else Color.Transparent,
        if (reduce) Motion.reduced() else Motion.quick(), label = "rowGround",
    )
    val ink by animateColorAsState(
        if (selected) palette.selectInk else palette.ink0,
        if (reduce) Motion.reduced() else Motion.quick(), label = "rowInk",
    )
    val inkQuiet by animateColorAsState(
        if (selected) palette.selectInk.copy(alpha = 0.68f) else palette.ink2,
        if (reduce) Motion.reduced() else Motion.quick(), label = "rowInkQuiet",
    )
    // The marker wipes down rather than fading, so selection reads as
    // something being applied to the row rather than appearing on it.
    val markerExtent by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduce) Motion.reduced() else Motion.quick(), label = "rowMarker",
    )

    /*
     * DEPARTURE.
     *
     * Two motions, because two different things happen.
     *
     * TO TRASH: the row is *withdrawn*. It slides toward the trailing edge -
     * the direction the delete action lives - while its height closes behind
     * it, and its ground briefly takes the signal wash. It reads as being
     * claimed and taken somewhere, not destroyed, which is exactly what a
     * recoverable delete is.
     *
     * DESTROYED: no lateral movement at all, because nothing is going
     * anywhere. The ink drains out and the row closes straight down.
     *
     * Both hold for a beat before leaving - anticipation, so the eye has time
     * to register which row is going - then release quickly. Under reduced
     * motion both collapse to a fast fade, which loses the metaphor but keeps
     * the fact.
     */
    val leaving = departing != null
    val departure = animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = if (reduce) {
            tween(Motion.REDUCED)
        } else {
            tween(Motion.DELIBERATE, easing = Motion.enter)
        },
        label = "departure",
    ).value

    val displayName = if (showExtensions || node.isDirectory) node.name else node.stem
    val vertical = (Space.group - 2.dp) * density

    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (leaving) {
                    Modifier
                        .graphicsLayer {
                            // Hold, then go: nothing happens for the first
                            // fifth of the motion so the row can be seen.
                            val t = ((departure - 0.2f) / 0.8f).coerceIn(0f, 1f)
                            alpha = 1f - t
                            if (departing == Departure.ToTrash) {
                                translationX = size.width * 0.32f * t * t
                            }
                            scaleY = 1f - t * 0.25f
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        // The row closes by reporting a smaller height as it
                        // goes, so the list below it rises to fill the gap.
                        // Measuring the row up front would mean knowing its
                        // height before it has one.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val t = ((departure - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            val h = (placeable.height * (1f - t)).roundToInt()
                            layout(placeable.width, h) { placeable.place(0, 0) }
                        }
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = if (leaving) 0.dp else Reach.touch)
            .background(
                if (departing == Departure.ToTrash) {
                    palette.signalWash.copy(alpha = (1f - departure).coerceIn(0f, 1f))
                } else {
                    ground
                },
            )
            .pressable(
                onClick = onClick,
                onLongClick = onLongClick,
                contentDescription = null,
                scaleOnPress = !selectionActive,
            )
            .semantics {
                this.selected = selected
                this.contentDescription = accessibleDescription(node, facts)
            }
            .padding(start = Space.gutter - 8.dp, end = Space.gutter, top = vertical, bottom = vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The colour-independent selection marker.
        Canvas(Modifier.width(4.dp).height(Reach.glyph)) {
            if (markerExtent > 0.01f) {
                val h = size.height * markerExtent
                drawRoundRect(
                    color = palette.selectMark,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - h) / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width, size.width),
                )
            }
        }
        Gap(Space.near + 1.dp)

        FileMark(
            node = node,
            showThumbnail = showThumbnails,
            size = Reach.thumb,
            tint = if (selected) palette.selectInk else Glyphs.tintFor(node.kind, palette),
        )
        Gap(Space.group)

        Column(Modifier.weight(1f)) {
            BasicTextCompat(displayName, type.name.copy(color = ink), maxLines = 2)
            Gap(Space.bond)
            MetadataLine(node, facts, inkQuiet)
        }

        // A folder's weight sits at the trailing edge, where the eye can run
        // down a column of figures and compare them. Inline with the name it
        // would be uncomparable.
        Gap(Space.group)
        TrailingFigure(node, facts, selected)
    }
}

/** The line beneath the name: what this is, how big, when. */
@Composable
private fun MetadataLine(node: FileNode, facts: RowFacts?, color: Color) {
    val type = Filish.type
    val pieces = buildList {
        if (node.isDirectory) {
            val n = facts?.childCount ?: -1
            if (n == 0) add("Empty") else if (n > 0) add(Format.plural(n, "item", "items"))
        } else {
            add(Format.size(node.size))
            if (node.extension.isNotEmpty()) add(node.extension.uppercase())
        }
        add(Format.relativeTime(node.lastModified))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        pieces.forEachIndexed { i, piece ->
            if (i > 0) {
                // A dot separator rather than a bullet character: it stays
                // optically centred at every text size and never becomes a
                // glyph a screen reader tries to pronounce.
                Gap(Space.near - 1.dp)
                Canvas(Modifier.size(2.5.dp)) { drawCircle(color.copy(alpha = 0.55f)) }
                Gap(Space.near - 1.dp)
            }
            BasicTextCompat(piece, type.meta.copy(color = color), maxLines = 1)
        }
    }
}

/** A folder's measured size, settling in place. */
@Composable
private fun TrailingFigure(node: FileNode, facts: RowFacts?, selected: Boolean) {
    val palette = Filish.palette
    val type = Filish.type
    if (!node.isDirectory) return
    val measured = facts?.measured ?: return

    val ink = if (selected) palette.selectInk else palette.ink1
    Column(horizontalAlignment = Alignment.End) {
        val parts = Format.sizeParts(measured.bytes)
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextCompat(parts.value, type.figure.copy(color = ink), maxLines = 1)
            Gap(Space.bond)
            BasicTextCompat(
                parts.unit,
                type.meta.copy(color = if (selected) palette.selectInk.copy(alpha = 0.7f) else palette.ink2),
                maxLines = 1,
            )
        }
        if (!measured.settled) {
            Gap(Space.bond)
            MeasurementMarker(settled = false, width = 30.dp)
        }
    }
}

/**
 * The leading mark: a thumbnail if one can be made, otherwise the kind glyph.
 *
 * Loading is keyed on path and size and cancelled with the composition, so
 * scrolling past a row before its thumbnail arrives costs nothing - the
 * coroutine dies before it ever acquires a decode permit.
 */
@Composable
fun FileMark(
    node: FileNode,
    showThumbnail: Boolean,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val context = LocalContext.current
    val loader = remember(context) { context.filish.thumbnails }
    val px = with(LocalDensity.current) { size.roundToPx() }

    val wantsThumbnail = showThumbnail && (node.kind.isMedia) && !node.isDirectory

    var result by remember(node.path, px) {
        mutableStateOf<ThumbnailLoader.Result?>(
            loader.cached(node.path, px, node.lastModified)?.let { ThumbnailLoader.Result.Image(it) },
        )
    }

    LaunchedEffect(node.path, px, wantsThumbnail) {
        if (!wantsThumbnail || result is ThumbnailLoader.Result.Image) return@LaunchedEffect
        result = loader.load(node.path, node.kind, px, node.lastModified)
    }

    val bitmap = (result as? ThumbnailLoader.Result.Image)?.bitmap

    Box(
        modifier.size(size).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Corner.small))
                    .background(palette.ground2),
                contentScale = ContentScale.Crop,
            )
            // Video thumbnails are still frames and are otherwise
            // indistinguishable from photographs. The badge is what makes the
            // difference readable at 40dp.
            if (node.kind == FileKind.Video) {
                Box(
                    Modifier
                        .size(size * 0.44f)
                        .clip(RoundedCornerShape(Corner.small - 2.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(Glyphs.Play, null, size = size * 0.26f, tint = Color.White)
                }
            }
        } else {
            val damaged = result is ThumbnailLoader.Result.Unreadable && node.kind.isMedia
            Glyph(
                if (damaged) Glyphs.Warning else Glyphs.forKind(node.kind),
                null,
                size = size * 0.82f,
                tint = if (damaged) palette.warn else tint,
            )
        }
    }
}

/**
 * A grid cell.
 *
 * The grid exists for directories whose content is visual. It is not a
 * different design, it is the same row with the picture promoted and the
 * metadata reduced to what still fits honestly - which is the name and one
 * figure, not four truncated fields.
 */
@Composable
fun FileCell(
    node: FileNode,
    selected: Boolean,
    showThumbnails: Boolean,
    showExtensions: Boolean,
    facts: RowFacts?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion

    val border by animateColorAsState(
        if (selected) palette.selectMark else Color.Transparent,
        if (reduce) Motion.reduced() else Motion.quick(), label = "cellBorder",
    )

    Column(
        modifier
            .clip(RoundedCornerShape(Corner.token))
            .pressable(onClick = onClick, onLongClick = onLongClick, contentDescription = null)
            .semantics {
                this.selected = selected
                this.contentDescription = accessibleDescription(node, facts)
            }
            .padding(Space.near),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Corner.small + 1.dp))
                .background(palette.ground1)
                .then(
                    if (selected) {
                        Modifier.androidBorder(border)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            FileMark(
                node = node,
                showThumbnail = showThumbnails,
                size = if (node.kind.isMedia && showThumbnails) 400.dp else 46.dp,
                tint = Glyphs.tintFor(node.kind, palette),
                modifier = if (node.kind.isMedia && showThumbnails) Modifier.fillMaxSize() else Modifier,
            )
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.selectMark.copy(alpha = 0.22f)),
                )
                Box(Modifier.fillMaxSize().padding(Space.near), contentAlignment = Alignment.TopEnd) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(palette.selectMark),
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(Glyphs.Check, null, size = 15.dp, tint = palette.inkOn)
                    }
                }
            }
        }
        Gap(Space.near)
        BasicTextCompat(
            if (showExtensions || node.isDirectory) node.name else node.stem,
            type.nameTight.copy(color = palette.ink0),
            maxLines = 2,
        )
        Gap(Space.bond)
        BasicTextCompat(
            if (node.isDirectory) {
                facts?.measured?.let { Format.size(it.bytes) }
                    ?: facts?.childCount?.takeIf { it >= 0 }?.let { Format.plural(it, "item", "items") }
                    ?: "Folder"
            } else {
                Format.size(node.size)
            },
            type.meta.copy(color = palette.ink2),
            maxLines = 1,
        )
    }
}

private fun Modifier.androidBorder(color: Color): Modifier =
    border(2.dp, color, RoundedCornerShape(Corner.small + 1.dp))

/**
 * What a screen reader says about a row.
 *
 * Assembled as a sentence rather than read field by field, and ordered by
 * what matters: name, what it is, how big, when. Announcing "folder" before
 * the name would make every item in a directory of folders start identically,
 * which is exactly the situation a screen-reader user is trying to navigate.
 */
private fun accessibleDescription(node: FileNode, facts: RowFacts?): String = buildString {
    append(node.name)
    append(", ")
    append(node.kind.label)
    if (node.isDirectory) {
        facts?.childCount?.takeIf { it >= 0 }?.let {
            append(", ")
            append(Format.plural(it, "item", "items"))
        }
        facts?.measured?.takeIf { it.settled }?.let {
            append(", ")
            append(Format.size(it.bytes))
        }
    } else {
        append(", ")
        append(Format.size(node.size))
    }
    append(", modified ")
    append(Format.relativeTime(node.lastModified))
}
