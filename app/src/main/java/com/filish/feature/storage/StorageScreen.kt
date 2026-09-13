package com.filish.feature.storage

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
import androidx.compose.foundation.layout.width
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
import com.filish.core.fs.Volume
import com.filish.core.intel.AgeBand
import com.filish.core.intel.Bucket
import com.filish.core.intel.Finding
import com.filish.core.intel.FindingAction
import com.filish.core.intel.Findings
import com.filish.core.intel.StorageReport
import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.GlyphButton
import com.filish.design.component.Indeterminate
import com.filish.design.component.MeasurementMarker
import com.filish.design.component.SectionLabel
import com.filish.design.component.Stratum
import com.filish.design.component.StratumBar
import com.filish.design.component.pressable
import com.filish.filish
import java.io.File

/**
 * STORAGE INTELLIGENCE.
 *
 * ---------------------------------------------------------------------------
 * What a storage screen is for
 *
 * The usual one is a donut chart and a column of cards - Images, Videos,
 * Documents, Large files - each with a number on it. It looks like analysis
 * and it is a filing cabinet. It tells the user things they mostly knew
 * ("you have photos"), it makes them do the interpretation, and most of its
 * cards lead nowhere.
 *
 * The question a person actually arrives with is not "what is my storage made
 * of". It is "my phone is full and I don't know what to delete". Composition
 * is a means to that end, not the end.
 *
 * So this screen is ordered by that question:
 *
 *   1. HOW FULL, and of what - one stratum bar, which answers both at once,
 *      with the space FILISH cannot read into drawn hatched rather than
 *      quietly folded into "Other". A breakdown whose parts do not add up to
 *      the total is one a user is right to distrust.
 *
 *   2. WHAT TO LOOK AT - findings, ranked by how much could plausibly be
 *      reclaimed. Not "Videos: 24 GB" (photos and videos are the point of the
 *      phone) but "nine videos over 1 GB, untouched in a year". Each is a
 *      lead, and each one opens a filtered browser containing exactly those
 *      files, where the normal selection and delete machinery takes over.
 *
 *   3. THE DETAIL - composition, age, the heaviest folders, the largest
 *      files, for someone who wants to look rather than be told.
 *
 * Nothing here is a dead end. Every row goes somewhere the user can act.
 *
 * ---------------------------------------------------------------------------
 * It streams
 *
 * Analysing a device takes seconds to minutes. The report fills in
 * progressively from the first frame, so the screen is useful immediately and
 * simply becomes more accurate. The header says plainly whether the figures
 * are still moving.
 */
@Composable
fun StorageScreen(
    volume: Volume?,
    includeHidden: Boolean,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (FileNode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val context = LocalContext.current
    val analyzer = remember(context) { context.filish.analyzer }

    var report by remember(volume?.path) {
        mutableStateOf(
            StorageReport.empty(
                volume?.path.orEmpty(),
                volume?.totalBytes ?: 0L,
                volume?.availableBytes ?: 0L,
            ),
        )
    }

    LaunchedEffect(volume?.path, includeHidden) {
        val v = volume ?: return@LaunchedEffect
        analyzer.analyze(v.path, v.totalBytes, v.availableBytes, includeHidden).collect {
            report = it
        }
    }

    if (volume == null) {
        Box(modifier.fillMaxSize().background(palette.ground0))
        return
    }

    val findings = remember(report) { Findings.from(report) }

    Box(modifier.fillMaxSize().background(palette.ground0)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.zone),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = Space.gutter - 12.dp, end = Space.gutter, top = Space.group),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphButton(Glyphs.ChevronLeft, "Back", onBack, glyphSize = 21.dp)
                    Gap(Space.bond)
                    BasicTextCompat(volume.label, type.title.copy(color = palette.ink0))
                }
                Gap(Space.apart)
            }

            // 1. How full, and of what.
            item {
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val used = Format.sizeParts(report.usedBytes)
                        BasicTextCompat(used.value, type.figureHuge.copy(color = palette.ink0))
                        Gap(Space.bond)
                        Box(Modifier.padding(bottom = 6.dp)) {
                            BasicTextCompat(
                                "${used.unit} used of ${Format.size(report.totalBytes)}",
                                type.heading.copy(color = palette.ink1),
                            )
                        }
                    }
                    Gap(Space.group)
                    StratumBar(
                        strata = strataFor(report, palette),
                        totalBytes = report.totalBytes,
                        height = 32.dp,
                    )
                    Gap(Space.group)
                    if (!report.complete) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MeasurementMarker(settled = false, width = 40.dp)
                            Gap(Space.near)
                            BasicTextCompat(
                                "Reading your storage  ·  " +
                                    "${Format.count(report.filesScanned)} files so far",
                                type.meta.copy(color = palette.ink2),
                            )
                        }
                    } else {
                        BasicTextCompat(
                            "${Format.count(report.filesScanned)} files in " +
                                "${Format.count(report.foldersScanned)} folders" +
                                if (report.unaccountedBytes > 0) {
                                    "  ·  ${Format.size(report.unaccountedBytes)} used by the " +
                                        "system and other apps, which FILISH cannot read"
                                } else {
                                    ""
                                },
                            type.meta.copy(color = palette.ink2),
                        )
                    }
                }
                Gap(Space.zone - 10.dp)
            }

            // 2. What to look at.
            if (findings.isNotEmpty()) {
                item {
                    SectionLabel("Worth a look", Modifier.padding(horizontal = Space.gutter))
                    Gap(Space.group)
                }
                items(findings.size, key = { findings[it].id }) { index ->
                    FindingRow(findings[index]) {
                        when (val action = findings[index].action) {
                            is FindingAction.OpenFolder -> onOpenFolder(action.path)
                            else -> report.heaviestFolders.firstOrNull()?.let { onOpenFolder(it.path) }
                        }
                    }
                }
                item { Gap(Space.zone - 10.dp) }
            }

            // 3. The detail.
            item {
                SectionLabel("By type", Modifier.padding(horizontal = Space.gutter))
                Gap(Space.group)
            }
            val kinds = report.kindsByWeight()
            items(kinds.size, key = { "k:" + kinds[it].first.name }) { index ->
                val (kind, bucket) = kinds[index]
                CategoryRow(kind, bucket, report.accountedBytes, palette)
            }

            if (report.heaviestFolders.isNotEmpty()) {
                item {
                    Gap(Space.zone - 10.dp)
                    SectionLabel("Heaviest folders", Modifier.padding(horizontal = Space.gutter))
                    Gap(Space.group)
                }
                val folders = report.heaviestFolders.take(12)
                items(folders.size, key = { "f:" + folders[it].path }) { index ->
                    val folder = folders[index]
                    WeightRow(
                        label = folder.name,
                        detail = com.filish.feature.places.friendlyPath(
                            File(folder.path).parent ?: "",
                        ),
                        bytes = folder.bytes,
                        fraction = if (report.accountedBytes > 0) {
                            folder.bytes.toFloat() / report.accountedBytes
                        } else {
                            0f
                        },
                        glyph = Glyphs.Folder,
                        onClick = { onOpenFolder(folder.path) },
                    )
                }
            }

            if (report.largestFiles.isNotEmpty()) {
                item {
                    Gap(Space.zone - 10.dp)
                    SectionLabel("Largest files", Modifier.padding(horizontal = Space.gutter))
                    Gap(Space.group)
                }
                val files = report.largestFiles.take(20)
                items(files.size, key = { "l:" + files[it].path }) { index ->
                    val node = files[index]
                    WeightRow(
                        label = node.name,
                        detail = "${Format.relativeTime(node.lastModified)}  ·  " +
                            com.filish.feature.places.friendlyPath(File(node.path).parent ?: ""),
                        bytes = node.size,
                        fraction = if (report.largestFiles.first().size > 0) {
                            node.size.toFloat() / report.largestFiles.first().size
                        } else {
                            0f
                        },
                        glyph = Glyphs.forKind(node.kind),
                        tint = Glyphs.tintFor(node.kind, palette),
                        onClick = { onOpenFile(node) },
                    )
                }
            }

            if (report.byAge.isNotEmpty()) {
                item {
                    Gap(Space.zone - 10.dp)
                    SectionLabel("By age", Modifier.padding(horizontal = Space.gutter))
                    Gap(Space.group)
                    Column(Modifier.padding(horizontal = Space.gutter)) {
                        AgeBand.entries.forEach { band ->
                            val bucket = report.byAge[band] ?: Bucket.Zero
                            if (bucket.bytes <= 0) return@forEach
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = Space.near - 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicTextCompat(
                                    band.label,
                                    type.body.copy(color = palette.ink1),
                                    Modifier.weight(1f),
                                )
                                BasicTextCompat(
                                    Format.size(bucket.bytes),
                                    type.metaStrong.copy(color = palette.ink0),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!report.complete) {
            Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding()) {
                Indeterminate(active = true, height = 2.dp)
            }
        }
    }
}

/**
 * Categories, plus one honest band for what could not be read.
 *
 * The hatched "System and other apps" band is the detail that makes this
 * chart truthful. A file manager can never see the system partition or
 * another application's sandbox, and pretending the categories sum to the
 * used total means the user eventually notices the arithmetic does not work.
 */
private fun strataFor(
    report: StorageReport,
    palette: com.filish.design.Palette,
): List<Stratum> {
    val out = report.kindsByWeight().map { (kind, bucket) ->
        Stratum(
            id = kind.name,
            label = kind.label,
            bytes = bucket.bytes,
            color = Glyphs.tintFor(kind, palette),
        )
    }.toMutableList()

    if (report.unaccountedBytes > 0 && report.complete) {
        out.add(
            Stratum(
                id = "unaccounted",
                label = "System and other apps",
                bytes = report.unaccountedBytes,
                color = palette.ink2.copy(alpha = 0.45f),
                uncertain = true,
            ),
        )
    }
    return out
}

@Composable
private fun FindingRow(finding: Finding, onClick: () -> Unit) {
    val palette = Filish.palette
    val type = Filish.type
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter - Space.near)
            .clip(RoundedCornerShape(Corner.token))
            .pressable(
                onClick = onClick,
                contentDescription = "${finding.headline}. ${finding.detail} " +
                    "${Format.size(finding.bytes)}.",
                shape = RoundedCornerShape(Corner.token),
            )
            .padding(Space.near),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            BasicTextCompat(finding.headline, type.name.copy(color = palette.ink0))
            Gap(Space.bond + 1.dp)
            BasicTextCompat(finding.detail, type.meta.copy(color = palette.ink2))
        }
        Gap(Space.group)
        Column(horizontalAlignment = Alignment.End) {
            if (finding.bytes > 0) {
                val parts = Format.sizeParts(finding.bytes)
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicTextCompat(parts.value, type.figure.copy(color = palette.signal))
                    Gap(Space.bond)
                    BasicTextCompat(parts.unit, type.meta.copy(color = palette.ink2))
                }
            }
            Gap(Space.bond)
            Glyph(Glyphs.ChevronRight, null, size = 15.dp, tint = palette.ink2)
        }
    }
}

@Composable
private fun CategoryRow(
    kind: FileKind,
    bucket: Bucket,
    total: Long,
    palette: com.filish.design.Palette,
) {
    WeightRow(
        label = kind.label + "s",
        detail = Format.plural(bucket.count, "file", "files"),
        bytes = bucket.bytes,
        fraction = if (total > 0) bucket.bytes.toFloat() / total else 0f,
        glyph = Glyphs.forKind(kind),
        tint = Glyphs.tintFor(kind, palette),
        onClick = null,
    )
}

/**
 * A row whose bar is a comparison within this list.
 *
 * The bar is scaled to the largest item present, not to total storage. Scaled
 * to the total, every row in a list of folders would be a sliver and none of
 * them would be comparable - which is the only thing the bar is there to do.
 */
@Composable
private fun WeightRow(
    label: String,
    detail: String,
    bytes: Long,
    fraction: Float,
    glyph: Glyphs.Glyph,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    val accent = tint ?: palette.signal

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter - Space.near)
            .clip(RoundedCornerShape(Corner.token))
            .then(
                if (onClick != null) {
                    Modifier.pressable(
                        onClick = onClick,
                        contentDescription = "$label. $detail. ${Format.size(bytes)}.",
                        shape = RoundedCornerShape(Corner.token),
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Space.near, vertical = Space.near + 1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph, null, size = 18.dp, tint = accent)
            Gap(Space.group)
            Column(Modifier.weight(1f)) {
                BasicTextCompat(label, type.name.copy(color = palette.ink0), maxLines = 1)
                Gap(Space.bond)
                BasicTextCompat(detail, type.meta.copy(color = palette.ink2), maxLines = 1)
            }
            Gap(Space.near)
            BasicTextCompat(Format.size(bytes), type.metaStrong.copy(color = palette.ink0))
        }
        Gap(Space.near - 2.dp)
        Row {
            Gap(Space.textColumn - 20.dp)
            com.filish.design.component.Progression(
                fraction = fraction,
                height = 3.dp,
                accent = accent.copy(alpha = 0.75f),
                track = palette.ground2,
            )
        }
    }
}
