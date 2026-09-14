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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.filish.core.intel.DuplicateFinder
import com.filish.core.intel.DuplicateGroup
import com.filish.core.intel.DuplicateProgress
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.ActionWeight
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.EmptyState
import com.filish.design.component.FilishAction
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.GlyphButton
import com.filish.design.component.Indeterminate
import com.filish.design.component.MeasurementMarker
import com.filish.design.component.RaisedSurface
import com.filish.design.component.SectionLabel
import com.filish.design.component.pressable
import com.filish.feature.browse.FileMark
import com.filish.feature.places.friendlyPath
import com.filish.filish
import java.io.File

/**
 * Duplicates.
 *
 * ---------------------------------------------------------------------------
 * The interface problem is not finding them, it is choosing
 *
 * Once identical files are found, the user faces a decision per group: which
 * copy survives. Most duplicate tools answer this with a grid of checkboxes
 * and a "select all duplicates" button that nobody trusts, because the
 * consequence of getting it wrong is losing the only copy of something.
 *
 * FILISH inverts it. Each group shows one file marked KEEP and the rest
 * marked as redundant, with the choice pre-made and visible: the oldest copy
 * is kept, because a copy acquires a later timestamp than the thing it was
 * copied from, and ties break toward the shorter path - the file sitting
 * somewhere deliberate rather than buried in a downloads folder. Tapping any
 * other file in the group moves the KEEP mark to it.
 *
 * So the default is always safe and always visible, and changing it is one
 * tap on the file you want to keep rather than a careful audit of checkboxes.
 * Nothing is ever deleted without at least one copy remaining, because the
 * kept file is not selectable for deletion at all - that is a property of the
 * data model here, not a validation rule that could be forgotten.
 *
 * The scan names the phase it is in. "Comparing files of the same size" and
 * "Confirming exact matches" are meaningfully different waits, and the second
 * is the only one that reads whole files.
 */
@Composable
fun DuplicatesScreen(
    volumePath: String,
    includeHidden: Boolean,
    onOpenFile: (FileNode) -> Unit,
    onDelete: (List<FileNode>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val context = LocalContext.current
    val finder = remember { DuplicateFinder() }

    var progress by remember(volumePath) {
        mutableStateOf(
            DuplicateProgress(
                emptyList(), 0, 0, 0, complete = false,
                phase = DuplicateProgress.Phase.Walking,
            ),
        )
    }
    // Which file the user has chosen to keep, per group. Absent means the
    // suggested default still stands.
    var keepOverrides by remember(volumePath) { mutableStateOf(mapOf<String, String>()) }

    LaunchedEffect(volumePath, includeHidden) {
        finder.scan(volumePath, includeHidden).collect { progress = it }
    }

    fun keptPath(group: DuplicateGroup) =
        keepOverrides[group.hash] ?: group.suggestedKeep.path

    val redundant = remember(progress.groups, keepOverrides) {
        progress.groups.flatMap { group ->
            val keep = keptPath(group)
            group.files.filter { it.path != keep }
        }
    }
    val reclaimable = remember(redundant) { redundant.sumOf { it.size } }

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
                    BasicTextCompat("Duplicates", type.title.copy(color = palette.ink0))
                }

                Gap(Space.group)
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val parts = Format.sizeParts(reclaimable)
                        BasicTextCompat(parts.value, type.figureHuge.copy(color = palette.ink0))
                        Gap(Space.bond)
                        Box(Modifier.padding(bottom = 6.dp)) {
                            BasicTextCompat(
                                "${parts.unit} recoverable",
                                type.heading.copy(color = palette.ink1),
                            )
                        }
                    }
                    Gap(Space.near - 2.dp)
                    if (!progress.complete) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MeasurementMarker(settled = false, width = 40.dp)
                            Gap(Space.near)
                            BasicTextCompat(
                                "${progress.phase.label}  ·  " +
                                    "${Format.count(progress.filesScanned)} files seen",
                                type.meta.copy(color = palette.ink2),
                            )
                        }
                    } else {
                        BasicTextCompat(
                            "${Format.plural(progress.groups.size, "group", "groups")}  ·  " +
                                "${Format.plural(redundant.size, "extra copy", "extra copies")}  ·  " +
                                "${Format.size(progress.bytesHashed)} read to confirm",
                            type.meta.copy(color = palette.ink2),
                        )
                    }
                }
                Gap(Space.near)
                if (!progress.complete) Indeterminate(active = true) else Gap(2.dp)
            }

            Box(Modifier.weight(1f)) {
                if (progress.groups.isEmpty() && progress.complete) {
                    EmptyState(
                        headline = "No duplicates",
                        explanation = "Filish compared every file that shares an exact size " +
                            "with another and found no identical content. Files smaller than " +
                            "16 KB are skipped - there are a great many of them and they " +
                            "recover nothing worth the reading.",
                        glyph = Glyphs.Check,
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = if (redundant.isNotEmpty()) 120.dp else Space.zone,
                        ),
                    ) {
                        items(progress.groups.size, key = { progress.groups[it].hash }) { index ->
                            val group = progress.groups[index]
                            GroupBlock(
                                group = group,
                                keptPath = keptPath(group),
                                onKeep = { path ->
                                    keepOverrides = keepOverrides + (group.hash to path)
                                },
                                onOpen = onOpenFile,
                            )
                        }
                    }
                }
            }
        }

        if (redundant.isNotEmpty()) {
            RaisedSurface(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Space.near),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.group + 2.dp, vertical = Space.group),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicTextCompat(
                            "Delete ${Format.plural(redundant.size, "extra copy", "extra copies")}",
                            type.name.copy(color = palette.ink0), maxLines = 1,
                        )
                        Gap(Space.bond)
                        BasicTextCompat(
                            "One copy of each file is always kept.",
                            type.meta.copy(color = palette.ink2), maxLines = 1,
                        )
                    }
                    Gap(Space.near)
                    FilishAction(
                        "Delete",
                        { onDelete(redundant) },
                        weight = ActionWeight.Destructive,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupBlock(
    group: DuplicateGroup,
    keptPath: String,
    onKeep: (String) -> Unit,
    onOpen: (FileNode) -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type

    Column(Modifier.fillMaxWidth().padding(top = Space.apart - 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("${group.files.size} identical copies")
            Gap(Space.near)
            BasicTextCompat(
                "${Format.size(group.sizeEach)} each",
                type.eyebrow.copy(color = palette.ink2.copy(alpha = 0.75f)),
            )
        }
        Gap(Space.near)

        group.files.forEach { file ->
            val kept = file.path == keptPath
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter - Space.near)
                    .clip(RoundedCornerShape(Corner.token))
                    .pressable(
                        onClick = { if (!kept) onKeep(file.path) else onOpen(file) },
                        contentDescription = if (kept) {
                            "${file.name}, kept. Open it."
                        } else {
                            "${file.name}, will be deleted. Keep this one instead."
                        },
                        shape = RoundedCornerShape(Corner.token),
                    )
                    .padding(horizontal = Space.near, vertical = Space.near + 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileMark(
                    node = file,
                    showThumbnail = true,
                    size = 38.dp,
                    tint = Glyphs.tintFor(file.kind, palette),
                )
                Gap(Space.group)
                Column(Modifier.weight(1f)) {
                    BasicTextCompat(
                        file.name,
                        type.name.copy(
                            color = if (kept) palette.ink0 else palette.ink1,
                        ),
                        maxLines = 1,
                    )
                    Gap(Space.bond)
                    BasicTextCompat(
                        friendlyPath(File(file.path).parent ?: ""),
                        type.technical.copy(color = palette.ink2),
                        maxLines = 1,
                    )
                    Gap(Space.bond)
                    BasicTextCompat(
                        Format.relativeTime(file.lastModified),
                        type.meta.copy(color = palette.ink2),
                        maxLines = 1,
                    )
                }
                Gap(Space.near)
                // The state is stated in words, not implied by a tick, because
                // "which of these am I about to lose" is the only question on
                // this screen and it must not require decoding an icon.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Corner.small))
                        .background(if (kept) palette.okWash else palette.ground1)
                        .padding(horizontal = Space.near, vertical = 3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (kept) {
                            Glyph(Glyphs.Check, null, size = 13.dp, tint = palette.ok)
                            Gap(Space.bond)
                        }
                        BasicTextCompat(
                            if (kept) "KEEP" else "extra",
                            type.eyebrow.copy(
                                color = if (kept) palette.ok else palette.ink2,
                            ),
                        )
                    }
                }
            }
        }
    }
}
