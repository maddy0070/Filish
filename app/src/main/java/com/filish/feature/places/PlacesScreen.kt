package com.filish.feature.places

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.filish.core.fs.Volume
import com.filish.core.model.Format
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.GlyphButton
import com.filish.design.component.SectionLabel
import com.filish.design.component.StratumBar
import com.filish.design.component.Stratum
import com.filish.design.component.pressable
import java.io.File

/**
 * PLACES - the root of the path, not a home screen.
 *
 * Reached by scrolling the path rail to its start, which makes "go all the
 * way up" a continuous gesture rather than a jump to a different world.
 *
 * A volume is presented as a *quantity*, not as a name with an icon. The
 * primary text on a volume row is how much space is free, set large, because
 * that is the fact a person opens a file manager holding in their head. The
 * band beneath is the stratum bar - the same component the storage screen
 * leads with - so the composition of a volume is legible before it is even
 * opened, and the visual language of storage is identical everywhere.
 *
 * Pinned and recent places sit below, in that order. Pinned is deliberate
 * and small; recent is automatic and larger. Deliberate choices rank above
 * inferred ones.
 */
@Composable
fun PlacesScreen(
    volumes: List<Volume>,
    pinned: List<String>,
    recents: List<String>,
    onOpen: (String) -> Unit,
    onStorage: (Volume) -> Unit,
    onUnpin: (String) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type

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
                        .padding(start = Space.gutter, end = Space.gutter - 6.dp, top = Space.apart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextCompat(
                        "Filish",
                        type.wordmark.copy(color = palette.ink0),
                        Modifier.weight(1f),
                    )
                    GlyphButton(Glyphs.Search, "Search", onSearch, glyphSize = 21.dp)
                    GlyphButton(Glyphs.Settings, "Settings", onSettings, glyphSize = 21.dp)
                }
                Gap(Space.apart)
            }

            items(volumes.size, key = { volumes[it].id }) { index ->
                VolumeRow(
                    volume = volumes[index],
                    onOpen = { onOpen(volumes[index].path) },
                    onAnalyse = { onStorage(volumes[index]) },
                )
                Gap(Space.group)
            }

            val shortcuts = com.filish.core.fs.KnownPlaces.standard()
            if (shortcuts.isNotEmpty()) {
                item {
                    Gap(Space.group)
                    SectionLabel("Common folders", Modifier.padding(horizontal = Space.gutter))
                    Gap(Space.near)
                }
                items(shortcuts.size, key = { "s:" + shortcuts[it].second }) { index ->
                    val (label, path) = shortcuts[index]
                    PlaceRow(label = label, path = path, glyph = Glyphs.Folder, onOpen = { onOpen(path) })
                }
            }

            if (pinned.isNotEmpty()) {
                item {
                    Gap(Space.apart)
                    SectionLabel("Pinned", Modifier.padding(horizontal = Space.gutter))
                    Gap(Space.near)
                }
                items(pinned.size, key = { "p:" + pinned[it] }) { index ->
                    val path = pinned[index]
                    PlaceRow(
                        label = File(path).name.ifEmpty { path },
                        path = path,
                        glyph = Glyphs.Pin,
                        onOpen = { onOpen(path) },
                        trailing = {
                            GlyphButton(
                                Glyphs.Close, "Unpin", { onUnpin(path) },
                                glyphSize = 16.dp, touchSize = 40.dp,
                            )
                        },
                    )
                }
            }

            if (recents.isNotEmpty()) {
                item {
                    Gap(Space.apart)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("Recent")
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(Corner.token))
                                .pressable(
                                    onClick = onClearRecents,
                                    contentDescription = "Clear recent places",
                                    shape = RoundedCornerShape(Corner.token),
                                )
                                .padding(horizontal = Space.near, vertical = Space.bond),
                        ) {
                            BasicTextCompat("Clear", type.eyebrow.copy(color = palette.ink2))
                        }
                    }
                    Gap(Space.near)
                }
                items(recents.size, key = { "r:" + recents[it] }) { index ->
                    val path = recents[index]
                    PlaceRow(
                        label = File(path).name.ifEmpty { path },
                        path = path,
                        glyph = Glyphs.Clock,
                        onOpen = { onOpen(path) },
                    )
                }
            }
        }
    }
}

/**
 * A volume, led by what is left rather than by its name.
 *
 * The name is on the row, but it is the quiet part. Someone opening a file
 * manager rarely needs to be told they have internal storage.
 */
@Composable
private fun VolumeRow(volume: Volume, onOpen: () -> Unit, onAnalyse: () -> Unit) {
    val palette = Filish.palette
    val type = Filish.type
    val free = Format.sizeParts(volume.availableBytes)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter - Space.near)
            .clip(RoundedCornerShape(Corner.token))
            .pressable(
                onClick = onOpen,
                contentDescription = "${volume.label}. ${Format.size(volume.availableBytes)} " +
                    "free of ${Format.size(volume.totalBytes)}. Open.",
                shape = RoundedCornerShape(Corner.token),
            )
            .padding(horizontal = Space.near, vertical = Space.group),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(
                        Glyphs.Storage, null, size = 15.dp,
                        tint = if (volume.isRemovable) palette.catArchive else palette.ink2,
                    )
                    Gap(Space.bond + 1.dp)
                    BasicTextCompat(
                        volume.label + if (volume.readOnly) "  ·  read-only" else "",
                        type.metaStrong.copy(color = palette.ink1), maxLines = 1,
                    )
                }
                Gap(Space.near - 2.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicTextCompat(
                        free.value,
                        type.figureLarge.copy(
                            color = when {
                                volume.isCritical -> palette.danger
                                volume.isTight -> palette.warn
                                else -> palette.ink0
                            },
                        ),
                        maxLines = 1,
                    )
                    Gap(Space.bond)
                    BasicTextCompat(
                        "${free.unit} free of ${Format.size(volume.totalBytes)}",
                        type.meta.copy(color = palette.ink2), maxLines = 1,
                    )
                }
            }
            GlyphButton(
                Glyphs.ChevronRight, "Analyse ${volume.label}", onAnalyse,
                glyphSize = 18.dp, touchSize = 42.dp,
            )
        }
        Gap(Space.group)
        // Used versus free, in the same visual language the storage screen
        // uses for composition. The used portion is deliberately undifferentiated
        // here: breaking it down is what the storage screen is for, and doing
        // it twice would mean scanning the whole volume to draw this row.
        StratumBar(
            strata = listOf(
                Stratum(
                    id = volume.id,
                    label = "In use",
                    bytes = volume.usedBytes,
                    color = when {
                        volume.isCritical -> palette.danger
                        volume.isTight -> palette.warn
                        else -> palette.signal
                    },
                ),
            ),
            totalBytes = volume.totalBytes,
            height = 10.dp,
        )
    }
}

@Composable
private fun PlaceRow(
    label: String,
    path: String,
    glyph: Glyphs.Glyph,
    onOpen: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onOpen, contentDescription = "$label. $path")
            .padding(horizontal = Space.gutter, vertical = Space.group - 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(glyph, null, size = 19.dp, tint = palette.ink2)
        Gap(Space.group)
        Column(Modifier.weight(1f)) {
            BasicTextCompat(label, type.name.copy(color = palette.ink0), maxLines = 1)
            Gap(Space.bond)
            BasicTextCompat(
                friendlyPath(path),
                type.technical.copy(color = palette.ink2), maxLines = 1,
            )
        }
        if (trailing != null) trailing()
    }
}

/**
 * Paths, made readable.
 *
 * "/storage/emulated/0/DCIM/Camera" is a fact about how Android mounts
 * emulated storage. Nobody asked for it, and it makes every path in a list
 * start with the same twenty characters, which destroys scannability.
 */
fun friendlyPath(path: String): String = path
    .replace("/storage/emulated/0", "Internal storage")
    .replace(Regex("^/storage/([A-Za-z0-9-]+)"), "SD card")
    .ifEmpty { "/" }
