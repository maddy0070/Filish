package com.filish.feature.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filish.core.fs.Listing
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.core.model.ViewMode
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Motion
import com.filish.design.Space
import com.filish.design.component.ActionWeight
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.EmptyState
import com.filish.design.component.FilishAction
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.GlyphButton
import com.filish.design.component.Indeterminate
import com.filish.design.component.ProblemState
import com.filish.design.component.RaisedSurface
import com.filish.design.component.SectionLabel
import com.filish.design.component.Severity
import com.filish.design.component.pressable
import com.filish.design.component.BasicTextCompat as Text

/**
 * The browser - the surface FILISH mostly is.
 *
 * Structure, top to bottom: the path rail (where you are), a control strip
 * (how this is ordered), the content, and - only when they apply - the
 * selection ledger and the staged-operation bar.
 *
 * The control strip is worth noting. It states the current ordering in words
 * ("largest first") rather than hiding it behind a sort icon. Ordering is a
 * property of what you are looking at; if the user has to open a menu to
 * discover why the list is in this order, the list has been lying quietly.
 * The same strip shows the filter count, because a filtered list that looks
 * unfiltered is how people conclude their files have disappeared.
 */
@Composable
fun BrowseScreen(
    state: BrowseState,
    clipboard: Clipboard,
    showThumbnails: Boolean,
    showExtensions: Boolean,
    onOpen: (FileNode) -> Unit,
    onSelect: (FileNode) -> Unit,
    onRefine: (SelectionRefinement) -> Unit,
    onClearSelection: () -> Unit,
    onTokenClick: (PathToken) -> Unit,
    onStorage: () -> Unit,
    onSearch: () -> Unit,
    onSortTap: () -> Unit,
    onFilterTap: () -> Unit,
    onViewToggle: () -> Unit,
    onNewFolder: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    onPaste: () -> Unit,
    onCancelPaste: () -> Unit,
    volumes: List<com.filish.core.fs.Volume>,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val density = Filish.density
    val listState = rememberLazyListState()

    val tokens = remember(state.path, volumes) { pathTokens(state.path, volumes) }

    Box(modifier.fillMaxSize().background(palette.ground0)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) {
                Gap(Space.group)
                PathRail(
                    tokens = tokens,
                    volume = state.volume,
                    onTokenClick = onTokenClick,
                    onStorageClick = onStorage,
                )
                Gap(Space.group - 2.dp)
                ControlStrip(
                    state = state,
                    onSortTap = onSortTap,
                    onFilterTap = onFilterTap,
                    onViewToggle = onViewToggle,
                    onSearch = onSearch,
                    onNewFolder = onNewFolder,
                )
                if (state.loading) {
                    Gap(Space.near)
                    Indeterminate(active = true)
                } else {
                    Gap(Space.near + 1.dp)
                }
            }

            Box(Modifier.weight(1f)) {
                when (val listing = state.listing) {
                    null -> if (!state.loading) {
                        ProblemState(
                            what = "This folder could not be opened",
                            why = null,
                            whatNext = "Try going back and opening it again.",
                        )
                    }

                    is Listing.Denied -> ProblemState(
                        what = "Filish cannot read this folder",
                        why = if (listing.needsAllFilesAccess) {
                            "Android restricts folders outside your media directories unless " +
                                "an app has all-files access."
                        } else {
                            "The system denied read access to this location."
                        },
                        whatNext = "Grant access in Settings, or browse a folder Filish can reach.",
                        severity = Severity.Locked,
                    )

                    is Listing.Missing -> ProblemState(
                        what = "This folder is gone",
                        why = "It was removed or renamed after you opened it.",
                        whatNext = "Go back to see what is there now.",
                    )

                    is Listing.NotADirectory -> ProblemState(
                        what = "This is a file, not a folder",
                        why = null,
                        whatNext = "Go back and open it from the list.",
                    )

                    is Listing.Failed -> ProblemState(
                        what = "This folder could not be read",
                        why = listing.reason,
                        whatNext = "This can happen on removable storage that was ejected.",
                    )

                    is Listing.Content -> when {
                        state.isEmpty && state.rawCount == 0 -> EmptyState(
                            headline = "Nothing here",
                            explanation = "This folder is empty. Anything you copy or move here " +
                                "will show up in this list.",
                            glyph = Glyphs.FolderOpen,
                            action = {
                                FilishAction(
                                    "New folder", onNewFolder,
                                    weight = ActionWeight.Secondary, glyph = Glyphs.Plus,
                                )
                            },
                        )

                        state.isEmpty -> EmptyState(
                            headline = "Nothing matches",
                            explanation = "This folder holds ${Format.plural(state.rawCount, "item", "items")}, " +
                                "but none of them match the filter you have set.",
                            glyph = Glyphs.Filter,
                            action = {
                                FilishAction(
                                    "Clear filter", onFilterTap, weight = ActionWeight.Secondary,
                                )
                            },
                        )

                        state.viewMode == ViewMode.Grid -> FileGrid(
                            state, showThumbnails, showExtensions, onOpen, onSelect,
                        )

                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomInset(state, clipboard)),
                        ) {
                            items(
                                items = state.rows,
                                key = { row ->
                                    when (row) {
                                        is BrowseRow.Heading -> "h:${row.label}"
                                        is BrowseRow.Item -> row.node.path
                                    }
                                },
                            ) { row ->
                                when (row) {
                                    is BrowseRow.Heading -> GroupHeading(row)
                                    is BrowseRow.Item -> FileRow(
                                        node = row.node,
                                        selected = state.selection.contains(row.node.path),
                                        selectionActive = state.selection.isActive,
                                        facts = state.folderFacts[row.node.path],
                                        showThumbnails = showThumbnails,
                                        showExtensions = showExtensions,
                                        density = density.rowScale,
                                        onClick = {
                                            if (state.selection.isActive) onSelect(row.node)
                                            else onOpen(row.node)
                                        },
                                        onLongClick = { onSelect(row.node) },
                                    )
                                }
                            }
                        }
                    }
                }

                // The unreadable-entries note. A listing that quietly omits
                // files it could not stat is a listing that lies.
                val unreadable = (state.listing as? Listing.Content)?.unreadable ?: 0
                if (unreadable > 0 && !state.isEmpty) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(Space.gutter),
                    ) {
                        BasicTextCompat(
                            "${Format.count(unreadable)} items in this folder could not be read",
                            type.meta.copy(color = palette.ink2),
                        )
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            StagedBar(clipboard, onPaste, onCancelPaste)
            SelectionLedger(
                selection = state.selection,
                refinements = state.refinements,
                onRefine = onRefine,
                onCopy = onCopy,
                onMove = onMove,
                onDelete = onDelete,
                onShare = onShare,
                onMore = onMore,
                onDismiss = onClearSelection,
            )
        }
    }
}

private fun bottomInset(state: BrowseState, clipboard: Clipboard) =
    when {
        state.selection.isActive -> 230.dp
        clipboard.isActive -> 110.dp
        else -> Space.zone
    }

/**
 * The control strip.
 *
 * States the ordering in words. An icon alone would require the user to open
 * a sheet to find out why their files are in this order, which is a question
 * the interface should never make anyone ask.
 */
@Composable
private fun ControlStrip(
    state: BrowseState,
    onSortTap: () -> Unit,
    onFilterTap: () -> Unit,
    onViewToggle: () -> Unit,
    onSearch: () -> Unit,
    onNewFolder: () -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    val itemCount = state.rows.count { it is BrowseRow.Item }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .pressable(
                    onClick = onSortTap,
                    contentDescription = "Sorted by ${state.sort.key.label}, ${state.sort.statement}. " +
                        "Change ordering.",
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(com.filish.design.Corner.token),
                )
                .padding(vertical = Space.bond),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextCompat(
                Format.plural(itemCount, "item", "items"),
                type.metaStrong.copy(color = palette.ink1),
                maxLines = 1,
            )
            BasicTextCompat(
                "  ·  ${state.sort.statement}",
                type.meta.copy(color = palette.ink2),
                maxLines = 1,
            )
            Gap(Space.bond)
            Glyph(
                if (state.sort.descending) Glyphs.ArrowDown else Glyphs.ArrowUp,
                null, size = 13.dp, tint = palette.ink2,
            )
        }

        GlyphButton(Glyphs.Search, "Search", onSearch, glyphSize = 20.dp, touchSize = 42.dp)
        GlyphButton(
            Glyphs.Filter,
            if (state.filter.isActive) {
                "Filter, ${state.filter.activeCount} active"
            } else {
                "Filter"
            },
            onFilterTap,
            glyphSize = 20.dp, touchSize = 42.dp,
            tint = if (state.filter.isActive) palette.signal else palette.ink1,
        )
        GlyphButton(
            if (state.viewMode == ViewMode.List) Glyphs.ViewGrid else Glyphs.ViewList,
            if (state.viewMode == ViewMode.List) "Switch to grid" else "Switch to list",
            onViewToggle,
            glyphSize = 20.dp, touchSize = 42.dp,
        )
        GlyphButton(Glyphs.Plus, "New folder", onNewFolder, glyphSize = 20.dp, touchSize = 42.dp)
    }
}

@Composable
private fun GroupHeading(row: BrowseRow.Heading) {
    val palette = Filish.palette
    val type = Filish.type
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = Space.gutter, end = Space.gutter,
                top = Space.apart - 4.dp, bottom = Space.near,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(row.label)
            Gap(Space.near)
            BasicTextCompat(
                Format.count(row.count),
                type.eyebrow.copy(color = palette.ink2.copy(alpha = 0.7f)),
            )
        }
        if (row.bytes != null) {
            BasicTextCompat(Format.size(row.bytes), type.metaStrong.copy(color = palette.ink2))
        }
    }
}

@Composable
private fun FileGrid(
    state: BrowseState,
    showThumbnails: Boolean,
    showExtensions: Boolean,
    onOpen: (FileNode) -> Unit,
    onSelect: (FileNode) -> Unit,
) {
    val configuration = LocalConfiguration.current
    // Cell width is fixed rather than the column count, so a tablet or an
    // unfolded device gets more columns instead of larger thumbnails - which
    // is what extra space is actually for.
    val columns = (configuration.screenWidthDp / 116).coerceIn(2, 8)

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.gutter - Space.near,
            end = Space.gutter - Space.near,
            bottom = if (state.selection.isActive) 230.dp else Space.zone,
        ),
    ) {
        items(state.items, key = { it.path }) { node ->
            FileCell(
                node = node,
                selected = state.selection.contains(node.path),
                showThumbnails = showThumbnails,
                showExtensions = showExtensions,
                facts = state.folderFacts[node.path],
                onClick = {
                    if (state.selection.isActive) onSelect(node) else onOpen(node)
                },
                onLongClick = { onSelect(node) },
            )
        }
    }
}

/**
 * Files staged for a copy or move.
 *
 * Deliberately a persistent bar rather than a modal "choose destination"
 * screen. Staging and then navigating normally means the user picks the
 * destination the same way they find anything else - by browsing - instead of
 * inside a stripped-down folder picker that shows less than the browser does.
 */
@Composable
private fun StagedBar(
    clipboard: Clipboard,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion

    AnimatedVisibility(
        visible = clipboard.isActive,
        enter = if (reduce) fadeIn(Motion.reduced()) else slideInVertically(Motion.base()) { it } + fadeIn(Motion.quick()),
        exit = if (reduce) fadeOut(Motion.reduced()) else slideOutVertically(Motion.leaving()) { it } + fadeOut(Motion.leaving()),
    ) {
        RaisedSurface(Modifier.fillMaxWidth().padding(Space.near)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.group + 2.dp, vertical = Space.group),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(
                    if (clipboard.move) Glyphs.Move else Glyphs.Copy,
                    null, size = 20.dp, tint = palette.signal,
                )
                Gap(Space.group)
                Column(Modifier.weight(1f)) {
                    BasicTextCompat(
                        "${Format.plural(clipboard.nodes.size, "item", "items")} ready to " +
                            if (clipboard.move) "move" else "copy",
                        type.name.copy(color = palette.ink0), maxLines = 1,
                    )
                    Gap(Space.bond)
                    BasicTextCompat(
                        "From ${clipboard.originLabel}. Open a folder, then bring them here.",
                        type.meta.copy(color = palette.ink2), maxLines = 2,
                    )
                }
                Gap(Space.near)
                GlyphButton(Glyphs.Close, "Cancel", onCancel, touchSize = 40.dp, tint = palette.ink2)
                Gap(Space.bond)
                FilishAction(
                    if (clipboard.move) "Move here" else "Copy here",
                    onPaste,
                    weight = ActionWeight.Primary,
                )
            }
        }
    }
}
