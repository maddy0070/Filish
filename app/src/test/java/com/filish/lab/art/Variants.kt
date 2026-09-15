package com.filish.lab.art

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
import androidx.compose.ui.draw.clipToBounds
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
import com.filish.design.glass.Mass

/**
 * V3.2 — three art directions for Mass & Light.
 *
 * V3.1 settled the physics and then stopped: a black ground, uniform flat
 * rows, a coloured tab at the leading edge, and one gradient. Correct, and
 * under-directed. These three push the SAME system in three different
 * compositional directions to find out which one has authorship in it.
 *
 * Every variant renders the same twenty-one objects, including a media-heavy
 * run, two folders, a 3 KB config file and a Cyrillic name.
 */

data class Item(
    val name: String,
    val kind: String,
    val bytes: Long,
    val tint: Color,
    /** Stand-in for a real thumbnail: the lab has no bitmaps. */
    val media: Pair<Color, Color>? = null,
    val folder: Boolean = false,
)

private val img = Color(0xFFD08A5A)
private val vid = Color(0xFF9A87C9)
private val doc = Color(0xFF7CA876)
private val aud = Color(0xFF6BA6C7)
private val arc = Color(0xFFB59560)
private val cod = Color(0xFF8397B8)
private val fld = Color(0xFFA8A49C)

val Listing = listOf(
    Item("Camera", "428 items", 2_297_000_000L, fld, folder = true),
    Item("Screen recordings", "18 items", 4_812_000_000L, fld, folder = true),
    Item("VID_20240918_final.mp4", "MP4 · 2 days", 1_975_000_000L, vid, Color(0xFF2E3A46) to Color(0xFF6A7C8A)),
    Item("DSC01847.ARW", "RAW · 1 wk", 51_275_000L, img, Color(0xFF8A5A32) to Color(0xFFD9A86E)),
    Item("DSC01848.ARW", "RAW · 1 wk", 49_910_000L, img, Color(0xFF6E4630) to Color(0xFFC08A5A)),
    Item("DSC01849.ARW", "RAW · 1 wk", 52_004_000L, img, Color(0xFF3E5A48) to Color(0xFF8CB49A)),
    Item("DSC01850.ARW", "RAW · 1 wk", 48_220_000L, img, Color(0xFF5A3A4A) to Color(0xFFB08098)),
    Item("IMG_4417.HEIC", "HEIC · 2 wk", 3_880_000L, img, Color(0xFF2A4A5E) to Color(0xFF7EAEC8)),
    Item("IMG_4418.HEIC", "HEIC · 2 wk", 4_102_000L, img, Color(0xFF6A5A2A) to Color(0xFFC8B87E)),
    Item("IMG_4419.HEIC", "HEIC · 2 wk", 3_640_000L, img, Color(0xFF4A2A2A) to Color(0xFFA87070)),
    Item("podcast-ep-114.m4a", "M4A · 1 mo", 78_400_000L, aud),
    Item("Archive.zip", "ZIP · 3 mo", 418_000_000L, arc),
    Item("Договор аренды.pdf", "PDF · 4 wk", 2_243_000L, doc),
    Item("invoice-2024-09.pdf", "PDF · 6 days", 184_000L, doc),
    Item("scan_0042.pdf", "PDF · 2 mo", 940_000L, doc),
    Item("notes.txt", "TXT · today", 12_288L, cod),
    Item("config.json", "JSON · today", 3_104L, cod),
    Item("build.gradle.kts", "KTS · 5 days", 8_940L, cod),
    Item("export-final-v3.mp4", "MP4 · 1 wk", 812_000_000L, vid, Color(0xFF1E2E3A) to Color(0xFF52707E)),
    Item("IMG_4420.HEIC", "HEIC · 2 wk", 4_400_000L, img, Color(0xFF3A4A2A) to Color(0xFF8AA870)),
    Item("receipt.png", "PNG · 3 days", 640_000L, img, Color(0xFF4A4A4A) to Color(0xFF9A9A9A)),
)

val Largest = Mass.quantiseLargest(Listing.maxOf { it.bytes })

fun sizeOf(b: Long): String = when {
    b >= 1_000_000_000L -> "%.2f GB".format(b / 1e9)
    b >= 1_000_000L -> "%.1f MB".format(b / 1e6)
    b >= 1_000L -> "%.0f KB".format(b / 1e3)
    else -> "$b B"
}

// Night environment, shared by all three so only composition differs.
private val envHigh = Color(0xFF131418)
private val envLow = Color(0xFF0A0B0D)
private val ink = Color(0xFFF2F3F4)
private val ink1 = Color(0xFFA9AEB3)
private val ink2 = Color(0xFF82878C)

private fun Modifier.env() = drawWithCache {
    val b = Brush.verticalGradient(listOf(envHigh, envLow))
    onDrawBehind { drawRect(b) }
}

// ===========================================================================
// P — THE SPINE
// ===========================================================================

/**
 * P · THE SPINE
 *
 * PRINCIPLE: the marks leave the rows and become a landscape.
 *
 * Three moves, each aimed at one of V3.1's failures:
 *
 * 1. THE ROW BODY IS DELETED. At rest a file is a mark and some text on open
 *    ground - no rectangle, no container, no surface. V3.1's rows were filled
 *    rectangles edge to edge, which is the most generic form an interface has.
 *    The body becomes a STATE: it appears under the finger and on selection.
 *    So the material that used to be the default is now the thing that means
 *    "this object is under your control".
 *
 * 2. THE MARKS DETACH AND GAIN GAPS. Substrate shows between mark and text,
 *    and between one mark and the next. The spine stops reading as coloured
 *    tabs stuck to rows and starts reading as a stacked profile of the folder
 *    - a landscape you can read from across the room.
 *
 * 3. MEDIA ENTERS THE SPINE. An image or video file's mark is a vertical
 *    SLIVER OF THE ACTUAL THUMBNAIL rather than a flat category tint. A photo
 *    folder's spine is therefore literally made of the photographs in it, and
 *    you recognise your own pictures by tone before you read a filename. It is
 *    contrast-safe by construction because the spine is a gutter: no text sits
 *    on it, and none ever can.
 *
 * The title bleeds past the content gutter to the screen edge and is cropped
 * by it - type as architecture rather than as a label.
 */
@Composable
fun SpineVariant(pressedIndex: Int = -1, selectedIndex: Int = 2, withMedia: Boolean = true) {
    val markMax = 26.dp
    val markMin = 3.dp
    val channel = 26.dp
    val gutter = 18.dp
    val textX = gutter + channel + 16.dp

    Box(Modifier.fillMaxSize().env().clipToBounds()) {
        Column(Modifier.fillMaxSize()) {
            // THRESHOLD — the only place the composition breathes.
            Box(Modifier.height(20.dp))
            BasicTextCompat(
                "CAMERA",
                TextStyle(color = ink2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
                Modifier.padding(start = gutter),
            )
            Box(Modifier.height(6.dp))
            // Bleeds left past the gutter, cropped by the frame.
            BasicTextCompat(
                "9.8 GB",
                TextStyle(color = ink, fontSize = 62.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
                Modifier.padding(start = 10.dp),
            )
            BasicTextCompat(
                "21 of 428 items · 4.8 GB in one file",
                TextStyle(color = ink2, fontSize = 11.5.sp, letterSpacing = 0.3.sp),
                Modifier.padding(start = gutter),
            )
            Box(Modifier.height(26.dp))

            Listing.forEachIndexed { i, it ->
                val selected = i == selectedIndex
                val pressed = i == pressedIndex
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .drawWithCache {
                            val w = (markMin.toPx() +
                                (markMax.toPx() - markMin.toPx()) * Mass.relative(it.bytes, Largest))
                            val x = gutter.toPx()
                            val gap = 2.dp.toPx()
                            val bodyTone = when {
                                selected -> Color(0xFFE9E7E2)
                                pressed -> Color(0x14FFFFFF)
                                else -> null
                            }
                            val media = if (withMedia) it.media else null
                            onDrawBehind {
                                // The body is a STATE, not a default.
                                bodyTone?.let { t -> drawRect(t) }
                                val markBrush = when {
                                    selected -> null
                                    media != null -> Brush.verticalGradient(
                                        listOf(media.first, media.second),
                                        startY = 0f,
                                        endY = size.height,
                                    )
                                    else -> null
                                }
                                val top = gap
                                val h = size.height - gap * 2
                                if (markBrush != null) {
                                    drawRect(markBrush, Offset(x, top), Size(w, h))
                                } else {
                                    drawRect(
                                        if (selected) Color(0xFF15171A) else it.tint,
                                        Offset(x, top),
                                        Size(w, h),
                                    )
                                }
                            }
                        },
                ) {
                    Column(Modifier.padding(start = textX, end = 18.dp, top = 9.dp)) {
                        BasicTextCompat(
                            it.name,
                            TextStyle(
                                color = if (selected) Color(0xFF15171A) else ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.2).sp,
                            ),
                            maxLines = 1,
                        )
                        Box(Modifier.height(2.dp))
                        Row {
                            BasicTextCompat(
                                sizeOf(it.bytes),
                                TextStyle(
                                    color = if (selected) Color(0xFF4A4D52) else ink1,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                ),
                            )
                            Box(Modifier.width(7.dp))
                            BasicTextCompat(
                                it.kind,
                                TextStyle(
                                    color = if (selected) Color(0xFF6A6D72) else ink2,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.4.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Q — THE PLATE
// ===========================================================================

/**
 * Q · THE PLATE
 *
 * PRINCIPLE: depth by layering and margin, with no shadow and no blur.
 *
 * The listing is a single plate of material inset from the screen on all
 * sides, so the environment becomes a visible frame rather than something you
 * only see at the edges. The contextual title sits in that margin and is
 * OCCLUDED by the plate - occlusion is the cheapest and strongest depth cue
 * there is, and V3.1 never used it once.
 *
 * Rows keep their bodies here; the differentiation is compositional rather
 * than material, which is the point of running it against P.
 */
@Composable
fun PlateVariant(selectedIndex: Int = 2) {
    Box(Modifier.fillMaxSize().env().clipToBounds()) {
        // FAR PLANE — large, quiet, and about to be covered.
        Box(Modifier.padding(start = 16.dp, top = 40.dp)) {
            BasicTextCompat(
                "Camera",
                TextStyle(color = Color(0xFF3A3F45), fontSize = 76.sp, fontWeight = FontWeight.Light, letterSpacing = (-3).sp),
            )
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.height(96.dp))
            // NEAR PLANE — the plate, overlapping the title.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .drawWithCache {
                        onDrawBehind { drawRect(Color(0xFF191A1E)) }
                    },
            ) {
                Box(Modifier.height(14.dp))
                BasicTextCompat(
                    "9.8 GB · 21 OF 428",
                    TextStyle(color = ink2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp),
                    Modifier.padding(start = 18.dp),
                )
                Box(Modifier.height(12.dp))
                Listing.take(17).forEachIndexed { i, it ->
                    val selected = i == selectedIndex
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .drawWithCache {
                                val w = Mass.markWidth(it.bytes, Largest).toPx()
                                onDrawBehind {
                                    drawRect(if (selected) Color(0xFFE9E7E2) else Color(0x0BFFFFFF))
                                    drawRect(
                                        if (selected) Color(0xFF15171A) else it.tint,
                                        size = Size(w, size.height),
                                    )
                                    drawRect(
                                        Color(0x59000000),
                                        Offset(0f, size.height - 1.dp.toPx()),
                                        Size(size.width, 1.dp.toPx()),
                                    )
                                }
                            },
                    ) {
                        Column(Modifier.padding(start = 34.dp, end = 16.dp, top = 9.dp)) {
                            BasicTextCompat(
                                it.name,
                                TextStyle(
                                    color = if (selected) Color(0xFF15171A) else ink,
                                    fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                            BasicTextCompat(
                                "${sizeOf(it.bytes)} · ${it.kind}",
                                TextStyle(
                                    color = if (selected) Color(0xFF55585C) else ink2,
                                    fontSize = 11.5.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===========================================================================
// R — THE ATLAS
// ===========================================================================

/**
 * R · THE ATLAS
 *
 * PRINCIPLE: magnitude becomes AREA, not just width.
 *
 * Objects above a threshold get a full-width plate with a media band and
 * display-size typography. Everything below it compresses into dense runs -
 * several files to a line, name and size only.
 *
 * The hypothesis is that a directory is not a uniform list at all: a folder
 * with one 4.8 GB video and two hundred 3 KB configs is mostly ONE THING, and
 * an interface that gives them equal room is lying about the folder's shape.
 *
 * This is the most aggressive reading of "scale as a primary design variable",
 * and it is rendered precisely so the cost can be seen rather than argued.
 */
@Composable
fun AtlasVariant() {
    val big = Listing.filter { it.bytes >= 400_000_000L }
    val small = Listing.filter { it.bytes < 400_000_000L }
    Box(Modifier.fillMaxSize().env().clipToBounds()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Box(Modifier.height(22.dp))
            BasicTextCompat(
                "CAMERA · 9.8 GB",
                TextStyle(color = ink2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
            )
            Box(Modifier.height(16.dp))

            big.forEach { it ->
                val h = (58 + Mass.relative(it.bytes, Largest) * 86).toInt()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(h.dp)
                        .drawWithCache {
                            val m = it.media
                            onDrawBehind {
                                if (m != null) {
                                    drawRect(Brush.horizontalGradient(listOf(m.first, m.second)))
                                } else {
                                    drawRect(Color(0x14FFFFFF))
                                    drawRect(it.tint, size = Size(6.dp.toPx(), size.height))
                                }
                            }
                        },
                ) {
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        BasicTextCompat(
                            sizeOf(it.bytes),
                            TextStyle(color = ink, fontSize = 26.sp, fontWeight = FontWeight.Medium),
                        )
                        BasicTextCompat(
                            it.name,
                            TextStyle(color = ink1, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
                Box(Modifier.height(3.dp))
            }

            Box(Modifier.height(14.dp))
            BasicTextCompat(
                "AND 16 SMALLER · 231 MB TOTAL",
                TextStyle(color = ink2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
            )
            Box(Modifier.height(10.dp))
            small.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(34.dp)) {
                    pair.forEach { it ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .drawWithCache {
                                    val w = Mass.markWidth(it.bytes, Largest).toPx()
                                    onDrawBehind {
                                        drawRect(Color(0x0BFFFFFF), size = Size(size.width - 4.dp.toPx(), size.height))
                                        drawRect(it.tint, size = Size(w, size.height))
                                    }
                                },
                        ) {
                            Column(Modifier.padding(start = 20.dp, top = 4.dp)) {
                                BasicTextCompat(
                                    it.name,
                                    TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                )
                                BasicTextCompat(
                                    sizeOf(it.bytes),
                                    TextStyle(color = ink2, fontSize = 10.sp),
                                )
                            }
                        }
                    }
                }
                Box(Modifier.height(3.dp))
            }
        }
    }
}
