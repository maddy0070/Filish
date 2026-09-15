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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filish.design.DarkPalette
import com.filish.design.LightPalette
import com.filish.design.Palette
import com.filish.design.component.BasicTextCompat
import com.filish.design.glass.Facet
import com.filish.design.glass.Light
import com.filish.design.glass.Mass
import com.filish.design.glass.Material
import com.filish.design.glass.World
import com.filish.design.glass.environment
import com.filish.design.glass.lens
import com.filish.design.glass.skinFor
import com.filish.design.glass.worldObject

/**
 * The V3.1 prototypes.
 *
 * Isolated experiments, not screens. Every one is built from the PRODUCTION
 * tokens in com.filish.design.glass, so a token that stops meaning what it
 * says breaks these pictures - which is the only way a design system survives
 * contact with people editing numbers.
 */

private val name = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val meta = TextStyle(fontSize = 12.sp)
private val eyebrow = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
private val figure = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium)
private val huge = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Light)

@Composable
private fun Cap(t: String, p: Palette) =
    BasicTextCompat(t.uppercase(), eyebrow.copy(color = p.ink1))

/** One object in the world, with everything the mark can say. */
@Composable
private fun Ob(
    p: Palette,
    label: String,
    sub: String,
    bytes: Long,
    largest: Long,
    tint: Color,
    selected: Boolean = false,
    illuminated: Boolean = false,
    provisional: Boolean = false,
    height: Int = 58,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .worldObject(
                p, bytes, largest, tint,
                selected = selected, illuminated = illuminated, provisional = provisional,
            ),
    ) {
        Column(Modifier.padding(start = Mass.channel + 12.dp, end = 16.dp, top = 10.dp)) {
            BasicTextCompat(
                label,
                name.copy(color = if (selected) p.selectInk else p.ink0),
                maxLines = 1,
            )
            BasicTextCompat(
                sub,
                meta.copy(color = if (selected) p.selectInk.copy(alpha = 0.74f) else p.ink2),
            )
        }
    }
}

// ===========================================================================
// BOARD A — objects
// ===========================================================================

@Composable
fun BoardObjects(p: Palette) {
    val largest = 4_812_000_000L
    Box(Modifier.fillMaxSize().environment(p.isDark)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
            Cap("a · objects", p)
            Box(Modifier.height(14.dp))

            Cap("a file · the mark is its size", p)
            Ob(p, "DSC01847.ARW", "51.3 MB · RAW · 1 wk ago", 51_275_000L, largest, p.catImage)
            Box(Modifier.height(16.dp))

            Cap("a folder · still being measured, so the mark is lit and weak", p)
            // The size is streaming in from SizeResolver. The mark grows as the
            // walk converges, at reduced intensity until it settles. The growth
            // IS the measurement - not a spinner standing in for one.
            Ob(
                p, "Screen recordings", "measuring · 2.1 GB so far · 11 of 18",
                2_100_000_000L, largest, p.catVideo,
                illuminated = true, provisional = true,
            )
            Box(Modifier.height(16.dp))

            Cap("selected · the ground inverts and the mark holds its width", p)
            Ob(
                p, "VID_20240918_final.mp4", "1.98 GB · MP4 · 2 days ago",
                1_975_000_000L, largest, p.catVideo, selected = true,
            )
            Box(Modifier.height(16.dp))

            Cap("a media field · width is magnitude, rotated", p)
            MediaField(p)
            Box(Modifier.height(8.dp))
            BasicTextCompat(
                "Photographs abut with the same hairline cut as rows, and each " +
                    "one is as wide as it is big. No text is ever drawn on a " +
                    "thumbnail, so nothing here depends on what the picture is.",
                meta.copy(color = p.ink1),
            )
        }
    }
}

/**
 * A media field.
 *
 * Images are the one thing a file manager has that other applications do not,
 * so they get to be the object rather than a 40dp square beside it. The field
 * uses the SAME grammar as the list, turned ninety degrees: extent is
 * magnitude, neighbours are separated by a hairline cut.
 *
 * The rule that keeps it safe: NO TEXT IS EVER DRAWN ON A THUMBNAIL. That is
 * the whole reason V3 Direction A and V3.1 Direction K were rejected - the
 * moment text sits on arbitrary media, contrast becomes a function of the
 * user's camera roll. Captions live in the object body underneath, on a known
 * ground, at an audited contrast.
 *
 * (Flat tints stand in for photographs here; the lab has no bitmaps.)
 */
@Composable
private fun MediaField(p: Palette) {
    val shots = listOf(
        51_275_000L to Color(0xFF6E5C4A),
        3_880_000L to Color(0xFF4A5A6E),
        49_910_000L to Color(0xFF6E4A4A),
        4_102_000L to Color(0xFF4A6E5C),
        52_004_000L to Color(0xFF5C4A6E),
        184_000L to Color(0xFF6E6A4A),
    )
    val largest = shots.maxOf { it.first }
    Row(Modifier.fillMaxWidth().height(84.dp)) {
        shots.forEach { (bytes, tone) ->
            Box(
                Modifier
                    .weight(0.35f + Mass.relative(bytes, largest))
                    .fillMaxSize()
                    .drawWithCache {
                        val cutC = World.high(p.isDark)
                        onDrawBehind {
                            drawRect(tone)
                            drawRect(
                                cutC,
                                topLeft = Offset(size.width - 1.dp.toPx(), 0f),
                                size = Size(1.dp.toPx(), size.height),
                            )
                        }
                    },
            )
        }
    }
}

// ===========================================================================
// BOARD B — storage as mass, not as a chart
// ===========================================================================

/**
 * Storage intelligence with no chart in it.
 *
 * The question "what does 42 GB of photos look like" has an answer the system
 * already owns: it looks like an object whose mark is that wide. So the
 * storage screen is the browse screen one level up - categories are objects,
 * the mark is the magnitude, and the grammar is identical.
 *
 * That is worth more than a donut. A donut teaches nothing transferable and
 * cannot be acted on; this is the same list the user already knows how to
 * read, and every row is a place to go. It also means FILISH has ONE visual
 * language instead of a browsing one and a charting one.
 *
 * The insight row is the only lit thing on screen, because it is the only
 * thing the system is currently telling you.
 */
@Composable
fun BoardStorage(p: Palette) {
    val total = 256_000_000_000L
    val largest = 42_100_000_000L
    Box(Modifier.fillMaxSize().environment(p.isDark)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
            Cap("b · storage as mass", p)
            Box(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextCompat("38.4", huge.copy(color = p.ink0, fontSize = 52.sp))
                Box(Modifier.width(8.dp))
                Box(Modifier.padding(bottom = 9.dp)) {
                    BasicTextCompat("GB free of 256 GB", meta.copy(color = p.ink1, fontSize = 14.sp))
                }
            }
            Box(Modifier.height(18.dp))
            Ob(p, "Photographs", "42.1 GB · 12,840 items", largest, largest, p.catImage)
            Ob(p, "Video", "31.7 GB · 412 items", 31_700_000_000L, largest, p.catVideo)
            Ob(p, "Audio", "12.2 GB · 3,109 items", 12_200_000_000L, largest, p.catAudio)
            Ob(p, "Applications", "9.4 GB · 148 items", 9_400_000_000L, largest, p.catApp)
            Ob(p, "Documents", "1.8 GB · 2,044 items", 1_800_000_000L, largest, p.catDocument)
            Ob(p, "Archives", "840 MB · 26 items", 840_000_000L, largest, p.catArchive)
            Box(Modifier.height(18.dp))

            Cap("an insight · the only lit thing on the screen", p)
            Ob(
                p, "1,284 duplicates", "3.2 GB reclaimable · scanning, 61%",
                3_200_000_000L, largest, p.signal, illuminated = true, provisional = true,
            )
            Ob(
                p, "17 files over 1 GB", "24.6 GB · largest is 4.8 GB",
                24_600_000_000L, largest, p.warn,
            )
            Box(Modifier.height(10.dp))
            BasicTextCompat(
                "The mark on an insight is the bytes it can give back, on the " +
                    "same scale as everything above it - so how much a cleanup " +
                    "is worth is legible without reading a number.",
                meta.copy(color = p.ink1),
            )
            // Unused but kept honest: the figure above is derived from total.
            if (total <= 0) BasicTextCompat("", meta)
        }
    }
}

// ===========================================================================
// BOARD C — operations: things moving through and leaving the world
// ===========================================================================

/**
 * Transfer, deletion and restore, in the world's own terms.
 *
 * V2's Conduit rule survives intact - source drains, destination accumulates,
 * segments travel, completion is arrival - and it now has a native expression,
 * because both ends of a transfer are objects with a mark. The source's mark
 * narrows, the destination's widens, and what passes between them in the
 * gutter is the same material. The transfer is literally mass moving from one
 * object to another.
 *
 * A move empties the source. A copy leaves it at full width. That difference
 * is visible without a label, which is exactly the distinction users most
 * often get wrong.
 */
@Composable
fun BoardOperations(p: Palette) {
    val largest = 4_812_000_000L
    val skin = skinFor(p)
    Box(Modifier.fillMaxSize().environment(p.isDark)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
            Cap("c · operations", p)
            Box(Modifier.height(14.dp))

            Cap("a move in flight · source drains, destination accumulates", p)
            Ob(p, "DCIM/Camera", "1.2 GB of 2.3 GB remaining", 1_200_000_000L, largest, p.catImage, illuminated = true)
            Conduit(p)
            Ob(p, "SD card / Photos", "1.1 GB arrived · 214 of 428", 1_100_000_000L, largest, p.catImage, illuminated = true)
            Box(Modifier.height(16.dp))

            Cap("a copy · the source keeps its mass", p)
            Ob(p, "DCIM/Camera", "2.3 GB · unchanged", 2_297_000_000L, largest, p.catImage)
            Box(Modifier.height(16.dp))

            Cap("departing to trash · recoverable, so it shrinks toward a place", p)
            Ob(p, "VID_20240918_final.mp4", "moving to trash · restorable until 14 Oct", 1_975_000_000L, largest, p.catVideo, height = 34)
            Box(Modifier.height(16.dp))

            Cap("destroyed · no destination, and the mark goes first", p)
            Ob(p, "IMG_4417.HEIC", "deleted permanently", 3_880_000L, largest, p.ink2, height = 22)
            Box(Modifier.height(16.dp))

            Cap("interrupted · movement stops, and stays stopped", p)
            Ob(p, "Archive.zip", "stalled · destination is full · 418 MB left", 418_000_000L, largest, p.danger)
            Box(Modifier.height(14.dp))
            BasicTextCompat(
                "A stalled transfer holds its position rather than pulsing. " +
                    "Motion means work; a thing that has stopped working must " +
                    "stop moving, or the interface is lying.",
                meta.copy(color = p.ink1),
            )
            Box(Modifier.height(14.dp))

            // A lens: the one place V3's glass survives. Chrome, not content.
            Cap("a lens · chrome is still glass; content never is", p)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .lens(skin, Material.Tier.Lifted, Facet.slab),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 9.dp)) {
                    Column {
                        BasicTextCompat("4 selected", name.copy(color = p.ink0))
                        BasicTextCompat("6.21 GB · Trash · Copy · Move", meta.copy(color = p.ink2))
                    }
                }
            }
        }
    }
}

/** Segments crossing the gutter between two objects. */
@Composable
private fun Conduit(p: Palette) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .drawWithCache {
                val tint = p.catImage
                val x = Mass.channel.toPx() / 2f
                onDrawBehind {
                    // The channel the mass travels down - same gutter the marks
                    // live in, so the path is where the material already was.
                    drawRect(
                        tint.copy(alpha = Light.BLOOM_ALPHA),
                        topLeft = Offset(x - 1.dp.toPx(), 0f),
                        size = Size(2.dp.toPx(), size.height),
                    )
                    listOf(0.12f, 0.44f, 0.78f).forEach { t ->
                        drawRect(
                            tint.copy(alpha = Light.ATTENTION),
                            topLeft = Offset(x - 2.5.dp.toPx(), size.height * t),
                            size = Size(5.dp.toPx(), 5.dp.toPx()),
                        )
                    }
                }
            },
    )
}

// ===========================================================================
// BOARD D — resolution in place, and depth
// ===========================================================================

/**
 * An object resolving in place, and what entering a folder does.
 *
 * Direction J's best idea was that detail and actions appear where the object
 * already is, rather than in a sheet that covers the list. Its mistake was
 * putting the instrument in a fixed place and asking content to scroll under
 * it, which fights every scrolling habit on the platform and guarantees the
 * thing you want is never where your finger is.
 *
 * Keeping the idea and dropping the mechanism: the object you tapped expands
 * where it is. FILISH therefore has no per-file bottom sheet at all - no
 * context menu, no overflow, no dialog for the common case - and the object
 * never loses its place in the list, so closing it costs no re-orientation.
 *
 * NAVIGATION is the same move at the scale of the screen: entering a folder is
 * not screen A replacing screen B, it is this directory receding and the
 * child's contents arriving in the space it leaves. The path you came from
 * stays visible above as compressed index lines, so depth is legible as
 * accumulated context rather than as a breadcrumb you have to read.
 */
@Composable
fun BoardResolution(p: Palette) {
    val largest = 4_812_000_000L
    Box(Modifier.fillMaxSize().environment(p.isDark)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
            Cap("d · resolution in place", p)
            Box(Modifier.height(14.dp))

            Ob(p, "DSC01846.ARW", "48.2 MB · RAW · 1 wk ago", 48_200_000L, largest, p.catImage)

            // The resolved object. Same row, more of it - no sheet, no dialog.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .worldObject(p, 51_275_000L, largest, p.catImage, illuminated = true),
            ) {
                Column(Modifier.padding(start = Mass.channel + 12.dp, end = 16.dp, top = 12.dp)) {
                    BasicTextCompat("DSC01847.ARW", name.copy(color = p.ink0, fontSize = 17.sp))
                    Box(Modifier.height(8.dp))
                    BasicTextCompat("51.3 MB", figure.copy(color = p.ink0))
                    Box(Modifier.height(6.dp))
                    BasicTextCompat("Sony ARW · 6048 × 4024 · /DCIM/100MSDCF", meta.copy(color = p.ink2))
                    Box(Modifier.height(12.dp))
                    Row {
                        listOf("open", "move", "copy", "trash").forEach {
                            BasicTextCompat(it.uppercase(), eyebrow.copy(color = p.signal))
                            Box(Modifier.width(16.dp))
                        }
                    }
                }
            }

            Ob(p, "DSC01848.ARW", "49.9 MB · RAW · 1 wk ago", 49_910_000L, largest, p.catImage)
            Box(Modifier.height(22.dp))

            Cap("depth · where you came from stays, compressed", p)
            Box(Modifier.height(8.dp))
            listOf(
                "Internal storage" to 256_000_000_000L,
                "DCIM" to 44_300_000_000L,
                "Camera" to 2_297_000_000L,
            ).forEachIndexed { i, (n, b) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(Mass.channel)
                            .height(14.dp)
                            .drawWithCache {
                                val w = Mass.markWidth(b, 256_000_000_000L).toPx()
                                onDrawBehind {
                                    drawRect(
                                        p.catFolder.copy(alpha = 0.45f + i * 0.18f),
                                        size = Size(w, size.height),
                                    )
                                }
                            },
                    )
                    Box(Modifier.width(12.dp))
                    BasicTextCompat(n, meta.copy(color = p.ink1), Modifier.weight(1f))
                }
            }
            Box(Modifier.height(10.dp))
            BasicTextCompat(
                "Each level you enter leaves one compressed line, and its mark " +
                    "is still its size - so the route in is also a record of " +
                    "where the space went.",
                meta.copy(color = p.ink1),
            )
        }
    }
}

val Night: Palette get() = DarkPalette
val Day: Palette get() = LightPalette
