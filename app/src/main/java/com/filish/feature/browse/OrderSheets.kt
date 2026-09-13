package com.filish.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.filish.core.model.FileKind
import com.filish.core.model.FilterSpec
import com.filish.core.model.Format
import com.filish.core.model.GroupKey
import com.filish.core.model.SortDirection
import com.filish.core.model.SortKey
import com.filish.core.model.SortSpec
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.ActionWeight
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishAction
import com.filish.design.component.FilishChip
import com.filish.design.component.FilishField
import com.filish.design.component.FilishToggle
import com.filish.design.component.Gap
import com.filish.design.component.SectionLabel
import com.filish.design.component.Sheet

/**
 * Ordering and grouping.
 *
 * Sort and group live in one sheet because they are two halves of the same
 * decision - how this list is arranged - and separating them into different
 * menus is what forces users to open two menus to express one intention.
 * Filtering is a different question entirely (which rows exist at all), and
 * it gets its own sheet.
 *
 * The direction control is a pair of explicit choices rather than an
 * arrow that flips on repeated taps. A toggle's current state is only
 * discoverable by pressing it and seeing what happens, and "does another tap
 * reverse this or change the key?" is not a question worth making anyone ask.
 * The labels change with the key, because "Z to A" and "largest first" are the
 * words for the same direction under different keys.
 */
@Composable
fun OrderSheet(
    visible: Boolean,
    sort: SortSpec,
    group: GroupKey,
    onSort: (SortSpec) -> Unit,
    onGroup: (GroupKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type

    Sheet(visible = visible, onDismiss = onDismiss, title = "Arrange") {
        SectionLabel("Order by")
        Gap(Space.near)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            items(SortKey.entries.toList(), key = { it.name }) { key ->
                FilishChip(
                    label = key.label,
                    selected = sort.key == key,
                    onClick = { onSort(sort.copy(key = key)) },
                )
            }
        }

        Gap(Space.apart)
        SectionLabel("Direction")
        Gap(Space.near)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            FilishChip(
                label = ascendingLabel(sort.key),
                selected = !sort.descending,
                onClick = { onSort(sort.copy(direction = SortDirection.Ascending)) },
                glyph = Glyphs.ArrowUp,
            )
            FilishChip(
                label = descendingLabel(sort.key),
                selected = sort.descending,
                onClick = { onSort(sort.copy(direction = SortDirection.Descending)) },
                glyph = Glyphs.ArrowDown,
            )
        }

        Gap(Space.apart)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilishToggle(
                checked = sort.foldersFirst,
                onCheckedChange = { onSort(sort.copy(foldersFirst = it)) },
                contentDescription = "Keep folders above files",
            )
            Gap(Space.near)
            Column(Modifier.weight(1f)) {
                BasicTextCompat("Folders first", type.name.copy(color = palette.ink0))
                Gap(Space.bond)
                BasicTextCompat(
                    "Turn this off to see what is biggest in here, whatever it is.",
                    type.meta.copy(color = palette.ink2),
                )
            }
        }

        Gap(Space.apart)
        SectionLabel("Group under headings")
        Gap(Space.near)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            items(GroupKey.entries.toList(), key = { it.name }) { key ->
                FilishChip(
                    label = key.label,
                    selected = group == key,
                    onClick = { onGroup(key) },
                )
            }
        }
        Gap(Space.group)
    }
}

private fun ascendingLabel(key: SortKey) = when (key) {
    SortKey.Name -> "A to Z"
    SortKey.Size -> "Smallest first"
    SortKey.Modified -> "Oldest first"
    SortKey.Count -> "Fewest first"
    else -> "Ascending"
}

private fun descendingLabel(key: SortKey) = when (key) {
    SortKey.Name -> "Z to A"
    SortKey.Size -> "Largest first"
    SortKey.Modified -> "Newest first"
    SortKey.Count -> "Most first"
    else -> "Descending"
}

/**
 * Filtering.
 *
 * Each constraint states how many rows it would leave, computed from the
 * listing in front of the user. A filter that silently produces an empty list
 * is a dead end; one that says "4" before you tap it never does.
 */
@Composable
fun FilterSheet(
    visible: Boolean,
    filter: FilterSpec,
    items: List<com.filish.core.model.FileNode>,
    onFilter: (FilterSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    val counts = remember(items) { items.groupingBy { it.kind }.eachCount() }

    Sheet(visible = visible, onDismiss = onDismiss, title = "Show only") {
        if (counts.isEmpty()) {
            BasicTextCompat(
                "There is nothing in this folder to filter.",
                type.body.copy(color = palette.ink2),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
                items(counts.entries.sortedByDescending { it.value }.toList(), key = { it.key.name }) { entry ->
                    FilishChip(
                        label = entry.key.label,
                        selected = entry.key in filter.kinds,
                        onClick = {
                            onFilter(
                                filter.copy(
                                    kinds = if (entry.key in filter.kinds) {
                                        filter.kinds - entry.key
                                    } else {
                                        filter.kinds + entry.key
                                    },
                                ),
                            )
                        },
                        count = entry.value,
                    )
                }
            }
        }

        Gap(Space.apart)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilishToggle(
                checked = filter.showHidden,
                onCheckedChange = { onFilter(filter.copy(showHidden = it)) },
                contentDescription = "Show hidden files",
            )
            Gap(Space.near)
            Column(Modifier.weight(1f)) {
                BasicTextCompat("Hidden files", type.name.copy(color = palette.ink0))
                Gap(Space.bond)
                BasicTextCompat(
                    "Files whose names begin with a dot. Usually created by apps.",
                    type.meta.copy(color = palette.ink2),
                )
            }
        }

        if (filter.isActive) {
            Gap(Space.apart)
            FilishAction(
                "Clear all filters",
                { onFilter(FilterSpec(showHidden = filter.showHidden)) },
                weight = ActionWeight.Secondary,
                fillWidth = true,
            )
        }
        Gap(Space.group)
    }
}

/**
 * Naming something.
 *
 * On rename, the extension is deselected: the cursor selects the stem only,
 * so a user retyping "IMG_2938" into "Kitchen" does not silently destroy the
 * ".jpg" that makes the file openable. It remains editable - it is simply not
 * what gets replaced by the first keystroke.
 *
 * Validation happens on submit with a specific reason, not as a live red
 * border that scolds you for having typed one character so far.
 */
@Composable
fun NameSheet(
    visible: Boolean,
    title: String,
    initial: String,
    actionLabel: String,
    selectStemOnly: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    var value by remember(visible, initial) {
        val stemLength = if (selectStemOnly) {
            val ext = com.filish.core.model.Kinds.extensionOf(initial)
            if (ext.isEmpty()) initial.length else initial.length - ext.length - 1
        } else {
            initial.length
        }
        mutableStateOf(
            TextFieldValue(initial, TextRange(0, stemLength.coerceIn(0, initial.length))),
        )
    }

    LaunchedEffect(visible) {
        if (visible) runCatching { focus.requestFocus() }
    }

    Sheet(visible = visible, onDismiss = onDismiss, title = title) {
        FilishField(
            value = value,
            onValueChange = { value = it },
            placeholder = "Name",
            focused = focused,
            textStyle = type.name,
            onImeAction = { onSubmit(value.text) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
        )

        if (error != null) {
            Gap(Space.near)
            BasicTextCompat(error, type.meta.copy(color = palette.danger))
        }

        Gap(Space.apart)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            FilishAction("Cancel", onDismiss, Modifier.weight(1f), weight = ActionWeight.Secondary)
            FilishAction(
                actionLabel,
                { onSubmit(value.text) },
                Modifier.weight(1f),
                weight = ActionWeight.Primary,
                enabled = value.text.isNotBlank(),
            )
        }
        Gap(Space.group)
    }
}
