package com.filish.feature.properties

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.filish.core.media.MediaFacts
import com.filish.core.media.MediaMetadata
import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.core.model.Kinds
import com.filish.core.model.MeasuredSize
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishChip
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.MeasurementMarker
import com.filish.design.component.SectionLabel
import com.filish.design.component.Sheet
import com.filish.design.component.pressable
import com.filish.feature.browse.FileMark
import com.filish.filish
import java.io.File

/**
 * Everything FILISH knows about one file.
 *
 * ---------------------------------------------------------------------------
 * Progressive disclosure, done by audience rather than by count
 *
 * A properties screen can show forty fields. Showing forty fields to everyone
 * is how the three that matter become invisible.
 *
 * FILISH splits them by who is asking. The top of the sheet answers the
 * questions anyone has: what is this, how big, where, when. Below that,
 * sections that only open when the file actually has that kind of information
 * - camera settings for a photograph, codec and duration for a video, track
 * details for audio. And behind an explicit "Technical" toggle: MIME type,
 * absolute path, permission bits, the things only someone who came looking
 * would want.
 *
 * Nothing is hidden that the user asked for; what is hidden is what they did
 * not.
 *
 * ---------------------------------------------------------------------------
 * Folder size, again
 *
 * For a folder, the size here is measured - streaming, settling, the same as
 * everywhere else in FILISH. The item and subfolder counts come from the same
 * single walk rather than from three separate ones.
 *
 * ---------------------------------------------------------------------------
 * Location data
 *
 * If a photo is geotagged, FILISH says so and does not print the
 * coordinates. Knowing a picture carries your location before you share it is
 * useful; putting your home address on a properties screen that anyone
 * holding the phone can open is not.
 */
@Composable
fun PropertiesSheet(
    visible: Boolean,
    node: FileNode?,
    onDismiss: () -> Unit,
    onOpenLocation: ((String) -> Unit)? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var facts by remember(node?.path) { mutableStateOf(MediaFacts()) }
    var measured by remember(node?.path) { mutableStateOf<MeasuredSize?>(null) }
    var hash by remember(node?.path) { mutableStateOf<String?>(null) }
    var hashing by remember(node?.path) { mutableStateOf(false) }
    var technical by remember(node?.path) { mutableStateOf(false) }

    LaunchedEffect(node?.path, visible) {
        val n = node ?: return@LaunchedEffect
        if (!visible) return@LaunchedEffect
        if (n.kind.isMedia) facts = MediaMetadata.read(n.path, n.kind)
        if (n.isDirectory) {
            context.filish.sizes.measure(listOf(n.path)).collect { measured = it }
        }
    }

    if (node == null) {
        Sheet(visible = false, onDismiss = onDismiss) { }
        return
    }

    Sheet(visible = visible, onDismiss = onDismiss, title = null) {
        Column(
            Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileMark(
                    node = node,
                    showThumbnail = true,
                    size = 54.dp,
                    tint = Glyphs.tintFor(node.kind, palette),
                )
                Gap(Space.group)
                Column(Modifier.weight(1f)) {
                    BasicTextCompat(node.name, type.heading.copy(color = palette.ink0), maxLines = 3)
                    Gap(Space.bond)
                    BasicTextCompat(
                        node.kind.label + if (node.isHidden) "  ·  hidden" else "",
                        type.meta.copy(color = palette.ink2),
                    )
                }
            }

            Gap(Space.apart)

            // The headline figure.
            Row(verticalAlignment = Alignment.Bottom) {
                val bytes = if (node.isDirectory) measured?.bytes ?: 0L else node.size
                val parts = Format.sizeParts(bytes)
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        BasicTextCompat(parts.value, type.figureHuge.copy(color = palette.ink0))
                        Gap(Space.bond)
                        Box(Modifier.padding(bottom = 6.dp)) {
                            BasicTextCompat(parts.unit, type.heading.copy(color = palette.ink1))
                        }
                    }
                    if (node.isDirectory) {
                        Gap(Space.bond)
                        MeasurementMarker(settled = measured?.settled == true, width = 54.dp)
                    }
                }
            }

            if (node.isDirectory) {
                Gap(Space.near)
                val m = measured
                BasicTextCompat(
                    when {
                        m == null -> "Measuring"
                        m.settled -> "${Format.plural(m.files, "file", "files")} in " +
                            "${Format.plural(m.folders, "folder", "folders")}" +
                            if (m.unreadable > 0) "  ·  ${m.unreadable} unreadable" else ""
                        else -> "${Format.count(m.files)} files so far"
                    },
                    type.meta.copy(color = palette.ink2),
                )
            }

            Gap(Space.apart)
            Field("Where", com.filish.feature.places.friendlyPath(File(node.path).parent ?: "/")) {
                onOpenLocation?.invoke(File(node.path).parent ?: "/")
            }
            Field("Modified", Format.absoluteDateTime(node.lastModified))
            if (!node.isDirectory && node.extension.isNotEmpty()) {
                Field("Extension", "." + node.extension)
            }

            // Sections that appear only when the file has that kind of fact.
            if (!facts.isEmpty) {
                Gap(Space.apart)
                SectionLabel(
                    when (node.kind) {
                        FileKind.Image, FileKind.RawImage -> "Picture"
                        FileKind.Video -> "Video"
                        FileKind.Audio -> "Audio"
                        else -> "Media"
                    },
                )
                Gap(Space.near)
                facts.dimensions?.let { Field("Dimensions", it + (facts.megapixels?.let { m -> "  ·  $m" } ?: "")) }
                facts.durationMillis?.takeIf { it > 0 }?.let { Field("Duration", Format.duration(it)) }
                facts.bitrate?.takeIf { it > 0 }?.let {
                    Field("Bitrate", "${it / 1000} kbps")
                }
                facts.frameRate?.let { Field("Frame rate", it) }
                facts.title?.let { Field("Title", it) }
                facts.artist?.let { Field("Artist", it) }
                facts.album?.let { Field("Album", it) }
                facts.year?.let { Field("Year", it) }

                val camera = listOfNotNull(facts.cameraMake, facts.cameraModel)
                    .distinct().joinToString(" ")
                if (camera.isNotBlank()) Field("Camera", camera)
                val exposure = listOfNotNull(
                    facts.focalLength, facts.aperture, facts.exposure, facts.iso,
                ).joinToString("  ·  ")
                if (exposure.isNotBlank()) Field("Exposure", exposure)
                facts.dateTaken?.let { Field("Taken", it) }

                if (facts.hasLocation) {
                    Gap(Space.near)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Corner.token))
                            .background(palette.warnWash)
                            .padding(Space.group),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(Glyphs.Pin, null, size = 16.dp, tint = palette.warn)
                        Gap(Space.near)
                        BasicTextCompat(
                            "This file records where it was captured. That travels with it if " +
                                "you share it.",
                            type.meta.copy(color = palette.ink1),
                        )
                    }
                }
            }

            Gap(Space.apart)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
                FilishChip(
                    label = "Technical details",
                    selected = technical,
                    onClick = { technical = !technical },
                    glyph = Glyphs.Code,
                )
                if (!node.isDirectory && hash == null && !hashing) {
                    FilishChip(
                        label = "Checksum",
                        selected = false,
                        onClick = { hashing = true },
                        glyph = Glyphs.Lock,
                    )
                }
            }

            if (hashing && hash == null) {
                LaunchedEffect(node.path) {
                    hash = com.filish.core.intel.Fingerprint.sha256(File(node.path))
                    hashing = false
                }
            }

            if (technical) {
                Gap(Space.group)
                Field("Full path", node.path, monospace = true) {
                    clipboard.setText(AnnotatedString(node.path))
                }
                Field("Type", Kinds.mimeOf(node.name), monospace = true)
                if (!node.isDirectory) Field("Exact size", Format.exactBytes(node.size))
                Field(
                    "Access",
                    buildList {
                        if (node.canRead) add("readable")
                        if (node.canWrite) add("writable")
                        if (!node.canRead && !node.canWrite) add("no access")
                        if (node.isLink) add("symbolic link")
                    }.joinToString(", "),
                )
                facts.mimeType?.let { Field("Container", it, monospace = true) }
            }

            if (hash != null) {
                Gap(Space.group)
                Field("SHA-256", hash!!, monospace = true) {
                    clipboard.setText(AnnotatedString(hash!!))
                }
            } else if (hashing) {
                Gap(Space.group)
                BasicTextCompat("Reading the file...", type.meta.copy(color = palette.ink2))
            }

            Gap(Space.group)
        }
    }
}

/**
 * One labelled fact.
 *
 * Label above value rather than beside it. A two-column layout has to choose a
 * gutter position that works for both the longest label and the longest value,
 * and on a phone one of them always loses - usually the value, which is the
 * part that matters.
 */
@Composable
private fun Field(
    label: String,
    value: String,
    monospace: Boolean = false,
    onTap: (() -> Unit)? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner.small))
            .then(
                if (onTap != null) {
                    Modifier.pressable(
                        onClick = onTap,
                        contentDescription = "$label: $value",
                        shape = RoundedCornerShape(Corner.small),
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = Space.near - 1.dp),
    ) {
        BasicTextCompat(label.uppercase(), type.eyebrow.copy(color = palette.ink2))
        Gap(Space.bond)
        BasicTextCompat(
            value,
            (if (monospace) type.technical else type.body).copy(color = palette.ink0),
        )
    }
}
