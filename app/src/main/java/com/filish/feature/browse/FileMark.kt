package com.filish.feature.browse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.filish.design.component.pressable
import com.filish.filish

/*
 * Components that are NOT part of the P - Spine browse row.
 *
 * FileMark (a thumbnail or kind glyph at a fixed size) and FileCell (a grid
 * tile) belong to surfaces that have not been migrated yet: Properties,
 * Duplicates, and the grid view mode. They were moved out of FileRow.kt when
 * the browse row became MARK + INK, so that nothing in the browse list can
 * accidentally pull an icon back in.
 *
 * They are deliberately left as they were rather than half-migrated. The
 * design record calls for propagating P - Spine screen by screen once the
 * browse list is proven, and a component that is neither the old language nor
 * the new one is worse than either.
 */

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
