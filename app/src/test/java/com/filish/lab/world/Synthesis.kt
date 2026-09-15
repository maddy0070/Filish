package com.filish.lab.world

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filish.design.component.BasicTextCompat

/**
 * M — MASS & LIGHT. The synthesis.
 *
 * From I: magnitude is information, and the directory is one continuous body.
 * From J: an object resolves IN PLACE; there is no per-file sheet.
 * From H: the environment is a place, and light means something.
 * From K: a folder should be identifiable before it is read - but NEVER by
 *         tinting the ground, which is how contrast becomes a function of the
 *         user's photographs.
 *
 * The one structural move: MAGNITUDE MOVES FROM ROW HEIGHT INTO BAND WIDTH.
 *
 * Direction I proved that size-as-extent is real information - you could see
 * which files mattered without reading a figure. It also proved the channel
 * was wrong: six files filled a phone screen, and vertical space is the one
 * resource a mobile file manager cannot spend. Width costs nothing, because
 * the leading gutter was empty anyway, and it turns the left edge of the list
 * into a PROFILE of the folder that can be read in one glance.
 */

private val nameStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val metaStyle = TextStyle(fontSize = 12.sp)
private val eyebrow = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)

/** The magnitude channel. Fixed width; the mark inside it varies. */
private val CHANNEL = 18.dp
private val MARK_MIN = 3.dp
private val MARK_MAX = 16.dp

/**
 * Magnitude RELATIVE to the largest thing in this directory.
 *
 * The first version scaled against an absolute 1 KB - 64 GB range and the
 * middle of the list went flat: 418 MB and 51 MB were a pixel apart, and so
 * were 2.2 MB and 184 KB. Twenty-six doublings across thirteen dp is half a dp
 * per doubling, which is nothing.
 *
 * Relative scaling spends the whole channel on the spread that is actually
 * present, and it matches the question people ask: not "how big is this in the
 * abstract" but "what is taking up the room in HERE". The absolute figure is
 * always in the metadata line, so nothing is lost.
 *
 * SPAN is 16 doublings - 65536x - below the largest item. Anything smaller
 * than that is at the floor, which is honest: it is negligible in this folder.
 */
private const val SPAN = 16.0

fun relativeMagnitude(bytes: Long, largest: Long): Float {
    if (largest <= 0L) return 0f
    val l2 = { b: Long -> kotlin.math.ln(b.coerceAtLeast(1L).toDouble()) / kotlin.math.ln(2.0) }
    val drop = l2(largest) - l2(bytes)
    return ((1.0 - drop / SPAN).coerceIn(0.0, 1.0)).toFloat()
}

/** A directory with a realistic spread, long enough for a profile to emerge. */
val Directory = listOf(
    Obj("Camera", "428 items", 2_297_000_000L, Color(0xFFD08A5A)),
    Obj("Screen recordings", "18 items", 4_812_000_000L, Color(0xFF9A87C9)),
    Obj("VID_20240918_final.mp4", "MP4 · 2 days ago", 1_975_000_000L, Color(0xFF9A87C9)),
    Obj("DSC01847.ARW", "RAW · 1 wk ago", 51_275_000L, Color(0xFFD9A05A)),
    Obj("DSC01848.ARW", "RAW · 1 wk ago", 49_910_000L, Color(0xFFD9A05A)),
    Obj("DSC01849.ARW", "RAW · 1 wk ago", 52_004_000L, Color(0xFFD9A05A)),
    Obj("Archive.zip", "ZIP · 3 mo ago", 418_000_000L, Color(0xFFB59560)),
    Obj("Договор аренды.pdf", "PDF · 4 wk ago", 2_243_000L, Color(0xFF7CA876)),
    Obj("invoice-2024-09.pdf", "PDF · 6 days ago", 184_000L, Color(0xFF7CA876)),
    Obj("notes.txt", "TXT · today", 12_288L, Color(0xFF8397B8)),
    Obj("config.json", "JSON · today", 3_104L, Color(0xFF8397B8)),
    Obj("IMG_4417.HEIC", "HEIC · 2 wk ago", 3_880_000L, Color(0xFFD08A5A)),
    Obj("IMG_4418.HEIC", "HEIC · 2 wk ago", 4_102_000L, Color(0xFFD08A5A)),
    Obj("podcast-ep-114.m4a", "M4A · 1 mo ago", 78_400_000L, Color(0xFF6BA6C7)),
)

// ---- The environment -------------------------------------------------------

/**
 * The substrate: a deep, quiet place with real depth.
 *
 * Not black. Black is an absence, and this has to read as somewhere you are.
 * Two stops, close together, denser toward the bottom - the environment has a
 * below.
 *
 * [horizon] optionally adds a very soft density change at the device's storage
 * level, so the environment itself carries one permanent fact. V3 rejected a
 * waterline behind a list because a hard 1.6px meniscus got sliced into
 * fragments by every row and read as a rendering fault. This is the same idea
 * with the parameter that caused the failure removed: no line at all, and the
 * transition spread over two hundred dp. Rendered both ways as an A/B, because
 * re-testing a rejection with the cause removed is honest and re-asserting one
 * from memory is not.
 */
private fun Modifier.environment(horizon: Float?) = drawWithCache {
    val ground = Brush.verticalGradient(listOf(Color(0xFF131418), Color(0xFF0A0B0D)))
    val depth = horizon?.let { h ->
        val y = size.height * (1f - h)
        Brush.verticalGradient(
            colors = listOf(Color(0x00000000), Color(0x40000000)),
            startY = (y - 100.dp.toPx()).coerceAtLeast(0f),
            endY = size.height,
        )
    }
    onDrawBehind {
        drawRect(ground)
        depth?.let { drawRect(it) }
    }
}

// ---- One object ------------------------------------------------------------

/**
 * A file, as an object in the world.
 *
 * The mark in the leading channel is FOUR things at once, on three separable
 * visual channels:
 *
 *   width      magnitude        (log2, floored so nothing is ever invisible)
 *   hue        kind             (or, for a folder, its dominant content)
 *   intensity  state            (full on selection, signal hue on focus)
 *   presence   it is a FILISH object
 *
 * Width, hue and intensity do not interfere with one another, and width and
 * intensity survive greyscale. This is a deliberate concentration: one element
 * carries a lot so that the rest of the row can carry almost nothing.
 *
 * MATERIAL SHOWS MAGNITUDE; INK STATES IT. The mark is for the glance, the
 * figure in the metadata line is for the answer. The mark is never the only
 * place a size appears.
 */
@Composable
fun WorldObject(
    o: Obj,
    ink: Color,
    quiet: Color,
    largest: Long,
    illuminated: Boolean = false,
    showMark: Boolean = true,
    flat: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .drawWithCache {
                val markWidth = MARK_MIN.toPx() +
                    (MARK_MAX.toPx() - MARK_MIN.toPx()) * relativeMagnitude(o.bytes, largest)
                val body = when {
                    o.selected -> Brush.verticalGradient(listOf(Color(0xFFEDEBE6), Color(0xFFDFDCD6)))
                    flat -> Brush.verticalGradient(listOf(Color(0x0BFFFFFF), Color(0x0BFFFFFF)))
                    illuminated -> Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x14FFFFFF)))
                    else -> Brush.verticalGradient(listOf(Color(0x0EFFFFFF), Color(0x08FFFFFF)))
                }
                val cut = Color(0x2E000000)
                onDrawBehind {
                    drawRect(body)
                    if (showMark) {
                        // A selected object keeps its magnitude. Inverting the
                        // ground must not cost the row its most useful channel.
                        val mark = if (o.selected) darken(o.tint) else o.tint
                        drawRect(mark, size = Size(markWidth, size.height))
                    }
                    if (!flat) {
                        drawRect(
                            cut,
                            topLeft = Offset(0f, size.height - 1.dp.toPx()),
                            size = Size(size.width, 1.dp.toPx()),
                        )
                    }
                }
            },
    ) {
        Column(Modifier.padding(start = CHANNEL + 12.dp, end = 16.dp, top = 10.dp)) {
            BasicTextCompat(
                o.name,
                nameStyle.copy(color = if (o.selected) Color(0xFF15171A) else ink),
                maxLines = 1,
            )
            BasicTextCompat(
                "${sizeLabel(o.bytes)} · ${o.meta}",
                metaStyle.copy(color = if (o.selected) Color(0xFF55585C) else quiet),
            )
        }
    }
}

// ---- The world -------------------------------------------------------------

/** A kind tint dark enough to read on an inverted (selected) ground. */
private fun darken(c: Color): Color =
    Color(red = c.red * 0.38f, green = c.green * 0.38f, blue = c.blue * 0.38f, alpha = 1f)

@Composable
fun MassAndLight(
    horizon: Float? = null,
    showMark: Boolean = true,
    flat: Boolean = false,
    label: String = "M · mass & light",
) {
    val ink = Color(0xFFF1F2F3)
    val quiet = Color(0xFF9DA2A7)
    val largest = Directory.maxOf { it.bytes }
    Box(
        Modifier
            .fillMaxSize()
            .then(if (flat) Modifier.drawWithCache { onDrawBehind { drawRect(Color(0xFF101114)) } } else Modifier.environment(horizon)),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
            BasicTextCompat(label.uppercase(), eyebrow.copy(color = quiet))
            Box(Modifier.height(12.dp))
            // Typography as architecture: the context is the largest thing on
            // screen, and it is quiet rather than loud.
            BasicTextCompat(
                "Camera",
                TextStyle(color = ink, fontSize = 38.sp, fontWeight = FontWeight.Light),
            )
            BasicTextCompat(
                "14 of 428 · 9.8 GB · mostly photographs",
                metaStyle.copy(color = quiet),
            )
            Box(Modifier.height(18.dp))
            Directory.forEachIndexed { i, o ->
                WorldObject(
                    o.copy(selected = i == 2),
                    ink,
                    quiet,
                    largest = largest,
                    showMark = showMark,
                    flat = flat,
                )
            }
        }
    }
}
