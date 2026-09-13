package com.filish.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.core.search.ParsedQuery
import com.filish.core.search.QueryParser
import com.filish.core.search.SearchProgress
import com.filish.core.search.Term
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.EmptyState
import com.filish.design.component.FilishChip
import com.filish.design.component.FilishField
import com.filish.design.component.Gap
import com.filish.design.component.GlyphButton
import com.filish.design.component.Indeterminate
import com.filish.design.component.SectionLabel
import com.filish.feature.browse.FileRow
import com.filish.feature.browse.RowFacts
import com.filish.feature.places.friendlyPath
import com.filish.filish
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/**
 * Search.
 *
 * ---------------------------------------------------------------------------
 * Showing the user how they were understood
 *
 * The feature that makes this trustworthy is not the parser, it is the row of
 * chips beneath the field. Every structured term FILISH extracted is rendered
 * back as a visible token - "videos", "larger than 100 MB", "in the last
 * week". If the interpretation is wrong, the user can see *which part* is
 * wrong and change that part.
 *
 * Compare the alternative: a box that silently decides what you meant and
 * returns results. When those results are wrong you have no idea whether the
 * file is missing, or the query was misread, and nothing to do but rephrase
 * and hope. Making the interpretation visible converts an opaque guess into a
 * conversation.
 *
 * Results stream in as they are found, and the location currently being
 * walked is shown, so a long search is legible rather than an indeterminate
 * wait.
 */
@Composable
fun SearchScreen(
    roots: List<String>,
    showHidden: Boolean,
    onOpen: (FileNode) -> Unit,
    onReveal: (FileNode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val context = LocalContext.current
    val engine = remember(context) { context.filish.search }
    val focus = remember { FocusRequester() }

    var input by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(SearchProgress(emptyList(), 0, complete = true)) }
    var parsed by remember { mutableStateOf(ParsedQuery(emptyList(), "")) }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(input.text, showHidden, roots) {
        parsed = QueryParser.parse(input.text)
        if (parsed.isEmpty) {
            progress = SearchProgress(emptyList(), 0, complete = true)
            return@LaunchedEffect
        }
        // Debounce: a search kicked off on every keystroke would start and
        // abandon a filesystem walk per character typed.
        delay(260)
        engine.search(roots, parsed, showHidden).collectLatest { progress = it }
    }

    Box(modifier.fillMaxSize().background(palette.ground0)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().padding(horizontal = Space.gutter)) {
                Gap(Space.group)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphButton(
                        Glyphs.ChevronLeft, "Back", onBack,
                        glyphSize = 21.dp, touchSize = 42.dp,
                    )
                    Gap(Space.near)
                    FilishField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = "Find anything",
                        leadingGlyph = Glyphs.Search,
                        focused = focused,
                        imeAction = ImeAction.Search,
                        textStyle = type.name,
                        trailing = if (input.text.isNotEmpty()) {
                            {
                                GlyphButton(
                                    Glyphs.Close, "Clear", { input = TextFieldValue("") },
                                    glyphSize = 17.dp, touchSize = 38.dp,
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus)
                            .onFocusChanged { focused = it.isFocused },
                    )
                }

                // How FILISH read the query.
                if (parsed.hasStructure) {
                    Gap(Space.group)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
                        items(parsed.terms, key = { it.label }) { term ->
                            FilishChip(
                                label = term.label,
                                selected = term !is Term.Text,
                                onClick = { },
                            )
                        }
                    }
                }

                Gap(Space.near)
                if (!progress.complete) {
                    Indeterminate(active = true)
                    Gap(Space.near)
                    BasicTextCompat(
                        "Looking in ${progress.currentLocation}  ·  " +
                            "${Format.count(progress.scanned)} checked",
                        type.meta.copy(color = palette.ink2),
                    )
                } else if (parsed.isEmpty.not()) {
                    BasicTextCompat(
                        "${Format.plural(progress.results.size, "result", "results")} from " +
                            "${Format.count(progress.scanned)} files" +
                            if (progress.truncated) "  ·  showing the first 2,000" else "",
                        type.meta.copy(color = palette.ink2),
                    )
                }
                Gap(Space.near)
            }

            Box(Modifier.weight(1f)) {
                when {
                    parsed.isEmpty -> Suggestions { input = TextFieldValue(it, androidx.compose.ui.text.TextRange(it.length)) }

                    progress.results.isEmpty() && progress.complete -> EmptyState(
                        headline = "Nothing found",
                        explanation = "No files match this. Try removing one of the terms " +
                            "above, or searching for part of the name instead.",
                        glyph = Glyphs.Search,
                    )

                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Space.zone),
                    ) {
                        items(progress.results, key = { it.path }) { node ->
                            Column {
                                FileRow(
                                    node = node,
                                    selected = false,
                                    selectionActive = false,
                                    facts = RowFacts(),
                                    showThumbnails = true,
                                    showExtensions = true,
                                    density = 1f,
                                    onClick = { onOpen(node) },
                                    onLongClick = { onReveal(node) },
                                )
                                // A search result out of context is ambiguous -
                                // three files called "invoice.pdf" look
                                // identical without their location.
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
    }
}

/**
 * The empty state teaches the grammar by example.
 *
 * Tapping an example runs it, so the user learns what the field understands by
 * using it rather than by reading a help screen they would never open.
 */
@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    val palette = Filish.palette
    val type = Filish.type
    Column(Modifier.fillMaxSize().padding(Space.gutter)) {
        Gap(Space.group)
        SectionLabel("Try")
        Gap(Space.group)
        BasicTextCompat(
            "Filish understands more than file names. Describe what you are after.",
            type.body.copy(color = palette.ink2),
        )
        Gap(Space.apart)
        QueryParser.examples.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.near),
                horizontalArrangement = Arrangement.spacedBy(Space.near),
            ) {
                pair.forEach { example ->
                    FilishChip(
                        label = example,
                        selected = false,
                        onClick = { onPick(example) },
                        glyph = Glyphs.Search,
                    )
                }
            }
        }
    }
}
