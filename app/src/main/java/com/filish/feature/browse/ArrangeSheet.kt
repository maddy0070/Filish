package com.filish.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.filish.core.model.FileNode
import com.filish.core.model.FilterSpec
import com.filish.core.model.Format
import com.filish.core.model.GroupKey
import com.filish.core.model.SortDirection
import com.filish.core.model.SortKey
import com.filish.core.model.SortSpec
import com.filish.core.model.ViewMode
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.ActionWeight
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishAction
import com.filish.design.component.FilishChip
import com.filish.design.component.FilishToggle
import com.filish.design.component.Gap
import com.filish.design.component.SectionLabel
import com.filish.design.component.Sheet

/**
 * ARRANGE - one door for "how am I seeing this?"
 *
 * ---------------------------------------------------------------------------
 * Why these were merged
 *
 * V1 had three separate controls: a sort sheet behind a line of text, a filter
 * sheet behind a funnel icon, and a layout toggle behind a grid icon. Three
 * controls, three icons of identical weight, and a beginner had to learn which
 * was which by pressing them.
 *
 * But sorting, grouping, filtering and layout are not three questions. They
 * are one: *how should this folder be presented to me right now?* Splitting
 * one question across three anonymous icons is why none of them were
 * discoverable - each was a fragment of an intention the user could not name.
 *
 * So there is one door, it is labelled with the current state in words, and
 * behind it the four choices sit in the order a person actually thinks about
 * them: what shape (layout), what order (sort), what grouping, what subset.
 *
 * ---------------------------------------------------------------------------
 * Beginner first
 *
 * Every option is a word, never an icon alone. Every filter states how many
 * rows it would leave, computed from the folder in front of the user, so
 * nothing leads to an unexplained empty list. The summary line at the top
 * reads back the whole arrangement as a sentence, which is what makes this
 * learnable: you change one chip and watch the sentence change.
 */
@Composable
fun ArrangeSheet(
    visible: Boolean,
    sort: SortSpec,
    group: GroupKey,
    filter: FilterSpec,
    viewMode: ViewMode,
    items: List<FileNode>,
    onSort: (SortSpec) -> Unit,
    onGroup: (GroupKey) -> Unit,
    onFilter: (FilterSpec) -> Unit,
    onViewMode: (ViewMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    val counts = items.groupingBy { it.kind }.eachCount()
    val shown = items.count { filter.matches(it) }

    Sheet(visible = visible, onDismiss = onDismiss, title = "Arrange") {
        // The whole arrangement as one sentence. Change a chip, watch this
        // change - which is how the controls teach what they do.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Corner.token))
                .background(palette.ground1)
                .padding(Space.group),
        ) {
            BasicTextCompat(
                summarise(sort, group, filter, viewMode, shown, items.size),
                type.body.copy(color = palette.ink0),
            )
        }

        Gap(Space.apart)
        SectionLabel("Shape")
        Gap(Space.near)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            ViewMode.entries.forEach { mode ->
                FilishChip(
                    label = mode.label,
                    selected = viewMode == mode,
                    onClick = { onViewMode(mode) },
                    glyph = if (mode == ViewMode.List) Glyphs.ViewList else Glyphs.ViewGrid,
                )
            }
        }

        Gap(Space.apart)
        SectionLabel("Order")
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
        Gap(Space.near)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            // Named directions, not an arrow that flips on repeated taps: a
            // toggle's state is only discoverable by pressing it.
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

        if (counts.isNotEmpty()) {
            Gap(Space.apart)
            SectionLabel("Show only")
            Gap(Space.near)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
                items(
                    counts.entries.sortedByDescending { it.value }.toList(),
                    key = { it.key.name },
                ) { entry ->
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

        Gap(Space.group + 2.dp)
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
                    "Files whose names start with a dot. Usually created by apps.",
                    type.meta.copy(color = palette.ink2),
                )
            }
        }

        if (filter.isActive || group != GroupKey.None || sort != SortSpec()) {
            Gap(Space.apart)
            FilishAction(
                "Reset to default",
                {
                    onSort(SortSpec())
                    onGroup(GroupKey.None)
                    onFilter(FilterSpec(showHidden = filter.showHidden))
                },
                weight = ActionWeight.Secondary,
                fillWidth = true,
            )
        }
        Gap(Space.group)
    }
}

/**
 * The arrangement, as a sentence a person can read.
 *
 * This is the component that makes the sheet teach itself. A beginner does not
 * know what "group by type" will do until they see it described alongside
 * everything else that is currently true.
 */
private fun summarise(
    sort: SortSpec,
    group: GroupKey,
    filter: FilterSpec,
    viewMode: ViewMode,
    shown: Int,
    total: Int,
): String = buildString {
    append(if (viewMode == ViewMode.Grid) "Showing previews of " else "Showing ")
    if (filter.isActive && shown != total) {
        append("${Format.count(shown)} of ${Format.count(total)} items")
    } else {
        append(Format.plural(total, "item", "items"))
    }
    append(", ")
    append(sort.statement)
    if (group != GroupKey.None) {
        append(", grouped by ")
        append(group.label.lowercase())
    }
    append(".")
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
