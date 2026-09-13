package com.filish.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.filish.core.fs.Volume
import com.filish.core.model.Format
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Reach
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.VolumeGauge
import com.filish.design.component.pressable
import java.io.File

/** One step of the current location. */
data class PathToken(val label: String, val path: String, val isRoot: Boolean)

/**
 * THE PATH RAIL - where you are, and how to leave.
 *
 * ---------------------------------------------------------------------------
 * Why not breadcrumbs, and why not a title bar
 *
 * A conventional file manager puts the current folder's name in a title bar
 * with a back arrow. That tells you where you are and nothing about how you
 * got there, so "go up two levels" means tapping back twice and watching two
 * screens you did not want.
 *
 * A conventional breadcrumb fixes that and breaks something else: when the
 * path is longer than the screen - which on Android it almost always is -
 * breadcrumbs truncate, and what they truncate is the *end*, which is the
 * folder you are actually in. The component whose job is to say where you are
 * hides where you are.
 *
 * ---------------------------------------------------------------------------
 * What this does
 *
 * The rail scrolls horizontally and is anchored to its trailing edge, so the
 * current folder is always visible and it is the ancestors that run off the
 * left. Ancestors recede: smaller, quieter ink, no weight. The current folder
 * is set in the display face at full ink and is unmistakably the subject.
 *
 * Every token is a target. Tapping an ancestor pops straight to it rather
 * than unwinding one level at a time. The leading token is Places - the root
 * of everything - so "home" is simply the top of the path rather than a
 * separate destination with its own tab.
 *
 * The volume gauge rides at the trailing edge: it states how full this volume
 * is at all times and is the door into storage analysis. One element, two
 * jobs, no permanent navigation bar.
 */
@Composable
fun PathRail(
    tokens: List<PathToken>,
    volume: Volume?,
    onTokenClick: (PathToken) -> Unit,
    onStorageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val listState = rememberLazyListState()

    // Anchor to the trailing edge whenever the path changes, so the folder you
    // are in is never the part that scrolled away.
    LaunchedEffect(tokens.size, tokens.lastOrNull()?.path) {
        if (tokens.isNotEmpty()) listState.scrollToItem(tokens.lastIndex)
    }

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Space.gutter, end = Space.group,
            ),
        ) {
            itemsIndexed(items = tokens, key = { _, t -> t.path }) { index, token ->
                val isCurrent = index == tokens.lastIndex
                if (index > 0) {
                    Glyph(
                        Glyphs.ChevronRight, null,
                        size = 13.dp,
                        tint = palette.ink2.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = Space.bond),
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Corner.token))
                        .pressable(
                            onClick = { onTokenClick(token) },
                            contentDescription = if (isCurrent) {
                                "Current folder, ${token.label}"
                            } else {
                                "Go to ${token.label}"
                            },
                            shape = RoundedCornerShape(Corner.token),
                        )
                        .padding(horizontal = Space.near, vertical = Space.near - 1.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (token.isRoot) {
                            Glyph(
                                Glyphs.Storage, null, size = 15.dp,
                                tint = if (isCurrent) palette.ink0 else palette.ink2,
                            )
                            Gap(Space.bond + 1.dp)
                        }
                        BasicTextCompat(
                            token.label,
                            if (isCurrent) {
                                type.title.copy(color = palette.ink0)
                            } else {
                                type.action.copy(color = palette.ink2)
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (volume != null) {
            VolumeChip(volume, onStorageClick)
            Gap(Space.gutter - Space.near)
        }
    }
}

/** Information and door in one: fullness, and the way into analysis. */
@Composable
private fun VolumeChip(volume: Volume, onClick: () -> Unit) {
    val palette = Filish.palette
    val type = Filish.type
    val free = Format.sizeParts(volume.availableBytes)

    Column(
        Modifier
            .clip(RoundedCornerShape(Corner.token))
            .pressable(
                onClick = onClick,
                contentDescription = "Storage. ${Format.size(volume.availableBytes)} free " +
                    "of ${Format.size(volume.totalBytes)} on ${volume.label}. Open analysis.",
                shape = RoundedCornerShape(Corner.token),
            )
            .padding(horizontal = Space.near + 1.dp, vertical = Space.near - 2.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextCompat(
                free.value,
                type.figure.copy(
                    color = when {
                        volume.isCritical -> palette.danger
                        volume.isTight -> palette.warn
                        else -> palette.ink0
                    },
                ),
                maxLines = 1,
            )
            Gap(Space.bond)
            BasicTextCompat("${free.unit} free", type.meta.copy(color = palette.ink2), maxLines = 1)
        }
        Gap(Space.bond)
        Box(Modifier.size(width = 58.dp, height = 4.dp)) {
            VolumeGauge(
                usedFraction = volume.usedFraction,
                tight = volume.isTight,
                critical = volume.isCritical,
                height = 4.dp,
            )
        }
    }
}

/**
 * Builds the rail's tokens from a path.
 *
 * A volume root becomes the volume's human label - "Internal storage", not
 * "/storage/emulated/0", which is a fact about Android's mount layout that no
 * user asked for. Everything below it keeps its real directory name, because
 * that is what the user named it.
 */
fun pathTokens(path: String, volumes: List<Volume>): List<PathToken> {
    val out = ArrayList<PathToken>(8)
    out.add(PathToken("Places", PLACES_TOKEN, isRoot = false))

    val volume = volumes.firstOrNull { it.contains(path) }
    if (volume == null) {
        // Outside any known volume - show the raw path so the user is not
        // lied to about where they are.
        var cursor = File(path)
        val stack = ArrayList<File>()
        while (cursor.parent != null) {
            stack.add(cursor)
            cursor = cursor.parentFile ?: break
        }
        stack.reversed().forEach { out.add(PathToken(it.name.ifEmpty { "/" }, it.absolutePath, false)) }
        return out
    }

    out.add(PathToken(volume.label, volume.path, isRoot = true))
    val relative = path.removePrefix(volume.path).trim('/')
    if (relative.isNotEmpty()) {
        var built = volume.path
        for (segment in relative.split('/')) {
            if (segment.isEmpty()) continue
            built = "$built/$segment"
            out.add(PathToken(segment, built, isRoot = false))
        }
    }
    return out
}

const val PLACES_TOKEN = "filish://places"
