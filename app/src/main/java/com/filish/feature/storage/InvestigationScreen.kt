package com.filish.feature.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.core.model.MeasuredSize
import com.filish.core.search.ParsedQuery
import com.filish.core.search.SearchProgress
import com.filish.core.search.Term
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.EmptyState
import com.filish.design.component.Gap
import com.filish.design.component.GlyphButton
import com.filish.design.component.Indeterminate
import com.filish.feature.browse.FileRow
import com.filish.feature.browse.RowFacts
import com.filish.feature.browse.Refinements
import com.filish.feature.browse.Selection
import com.filish.feature.browse.SelectionLedger
import com.filish.feature.places.friendlyPath
import com.filish.filish
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/**
 * A finding, opened.
 *
 * ---------------------------------------------------------------------------
 * This screen is what stops storage analysis being a dashboard
 *
 * The first version of the storage screen produced findings whose actions all
 * fell through to "open the heaviest folder" - so tapping "RAW photos are
 * taking up real space" landed the user in some unrelated directory. That is
 * worse than having no finding at all: it looks like analysis and behaves
 * like a broken link.
 *
 * This is the destination those findings were always promising. It gathers
 * exactly the files the finding described - from anywhere on the volume, not
 * from one folder - and hands them to the same selection ledger and the same
 * actions used everywhere else in FILISH. The analysis and the doing are the
 * same surface.
 *
 * The results stream in, ordered largest first, because a screen whose whole
 * purpose is reclaiming space should lead with the biggest thing to reclaim.
 */
@Composable
fun InvestigationScreen(
    title: String,
    roots: List<String>,
    kinds: Set<FileKind>,
    minSize: Long?,
    olderThan: Long?,
    includeHidden: Boolean,
    onOpenFile: (FileNode) -> Unit,
    onReveal: (FileNode) -> Unit,
    onDelete: (List<FileNode>) -> Unit,
    onShare: (List<FileNode>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val context = LocalContext.current
    val engine = remember(context) { context.filish.search }

    var progress by remember(title) {
        mutableStateOf(SearchProgress(emptyList(), 0, complete = false))
    }
    var selection by remember(title) { mutableStateOf(Selection()) }

    // The finding's criteria expressed in the search grammar, so investigation
    // reuses the one engine that already walks and filters rather than growing
    // a second, subtly different implementation of the same thing.
    val query = remember(kinds, minSize, olderThan) {
        ParsedQuery(
            terms = buildList {
                if (kinds.isNotEmpty()) add(Term.Kind(kinds, "matching"))
                minSize?.let { add(Term.LargerThan(it, Format.size(it))) }
                olderThan?.let { add(Term.ModifiedBefore(it, "older")) }
            },
            raw = title,
        )
    }

    LaunchedEffect(query, roots, includeHidden) {
        if (query.isEmpty) return@LaunchedEffect
        engine.search(roots, query, includeHidden).collectLatest { progress = it }
    }

    // Largest first: this screen exists to reclaim space.
    val results = remember(progress.results) { progress.results.sortedByDescending { it.size } }
    val totalBytes = remember(results) { results.sumOf { it.size } }

    Box(modifier.fillMaxSize().background(palette.ground0)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Space.gutter - 12.dp, end = Space.gutter, top = Space.group),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphButton(Glyphs.ChevronLeft, "Back", onBack, glyphSize = 21.dp)
                    Gap(Space.bond)
                    BasicTextCompat(
                        title,
                        type.title.copy(color = palette.ink0),
                        Modifier.weight(1f),
                        maxLines = 2,
                    )
                }
                Gap(Space.group)
                Row(
                    Modifier.padding(horizontal = Space.gutter),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val parts = Format.sizeParts(totalBytes)
                    BasicTextCompat(parts.value, type.figureLarge.copy(color = palette.ink0))
                    Gap(Space.bond)
                    Box(Modifier.padding(bottom = 3.dp)) {
                        BasicTextCompat(
                            "${parts.unit} across ${Format.plural(results.size, "file", "files")}" +
                                if (!progress.complete) " so far" else "",
                            type.meta.copy(color = palette.ink2),
                        )
                    }
                }
                Gap(Space.near)
                if (!progress.complete) {
                    Indeterminate(active = true)
                } else {
                    Gap(2.dp)
                }
            }

            Box(Modifier.weight(1f)) {
                if (results.isEmpty() && progress.complete) {
                    EmptyState(
                        headline = "Nothing matches any more",
                        explanation = "These files may already have been moved or deleted. " +
                            "Storage analysis reflects the last scan.",
                        glyph = Glyphs.Search,
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = if (selection.isActive) 230.dp else Space.zone,
                        ),
                    ) {
                        items(results.size, key = { results[it].path }) { index ->
                            val node = results[index]
                            Column {
                                FileRow(
                                    node = node,
                                    selected = selection.contains(node.path),
                                    selectionActive = selection.isActive,
                                    facts = RowFacts(),
                                    showThumbnails = true,
                                    showExtensions = true,
                                    density = 1f,
                                    onClick = {
                                        if (selection.isActive) {
                                            selection = selection.toggle(node)
                                        } else {
                                            onOpenFile(node)
                                        }
                                    },
                                    onLongClick = { selection = selection.toggle(node) },
                                )
                                // A result outside its folder is ambiguous
                                // without its location - three files called
                                // IMG_0001.jpg look identical otherwise.
                                Box(
                                    Modifier.padding(
                                        start = Space.gutter + Space.textColumn - 8.dp,
                                        end = Space.gutter,
                                        bottom = Space.near,
                                    ),
                                ) {
                                    BasicTextCompat(
                                        friendlyPath(File(node.path).parent ?: ""),
                                        type.technical.copy(color = palette.ink2),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            SelectionLedger(
                selection = selection.let {
                    // Every result is a plain file, so the total is exact and
                    // immediate - no walk, and no unsettled state to show.
                    if (it.isActive && !it.needsMeasurement) {
                        it.withMeasurement(
                            MeasuredSize(it.knownFileBytes, it.fileCount, 0, settled = true),
                        )
                    } else {
                        it
                    }
                },
                refinements = remember(results) { Refinements.forListing(results) },
                onRefine = { selection = Refinements.apply(it, results, selection) },
                onCopy = { },
                onMove = { },
                onDelete = { onDelete(selection.selectedNodes) },
                onShare = { onShare(selection.selectedNodes) },
                onMore = { selection.selectedNodes.firstOrNull()?.let(onReveal) },
                onDismiss = { selection = Selection() },
            )
        }
    }
}
