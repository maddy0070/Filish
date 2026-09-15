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
 * S · THE SPINE, converged.
 *
 * P won. This adds what Q and R were worth taking, and fixes what the first
 * render of P got wrong.
 *
 * THE RULE THE ROUND PRODUCED:
 *
 *     THE MARK SHOWS THE MOST SPECIFIC THING AVAILABLE.
 *
 * A photograph's mark is a sliver of that photograph. A video's is a sliver of
 * its poster frame. A file with no visual content falls back to its kind tint.
 * A folder's is composed from what is inside it.
 *
 * The no-media control proved this is load-bearing rather than decorative:
 * with flat category tints, a run of seven photographs becomes seven identical
 * orange bars and the spine says nothing except "these are images", which the
 * extension already said. With slivers the spine is a record of the actual
 * pictures and you find a shot by its tone before you read a filename.
 *
 * The cost, stated: hue stops carrying KIND for media files. That is fine and
 * it is the same discipline as everywhere else in FILISH - kind is in the
 * metadata line, always, in words. The mark is for the glance; the ink is for
 * the answer.
 */

private val envHigh = Color(0xFF131418)
private val envLow = Color(0xFF0A0B0D)
private val ink = Color(0xFFF2F3F4)
private val ink1 = Color(0xFFA9AEB3)
private val ink2 = Color(0xFF82878C)
private val selGround = Color(0xFFE9E7E2)
private val selInk = Color(0xFF15171A)

private val GUTTER = 18.dp
private val CHANNEL = 26.dp
private val TEXT_X = 60.dp
private val ROW = 54.dp
private val MARK_MIN = 3.dp
private val MARK_MAX = 26.dp
private val MARK_GAP = 2.dp

private fun Modifier.env() = drawWithCache {
    val b = Brush.verticalGradient(listOf(envHigh, envLow))
    onDrawBehind { drawRect(b) }
}

/** A folder's mark is composed from its contents - see [Spine]. */
private val folderComposites = mapOf(
    "Camera" to listOf(Color(0xFF8A5A32), Color(0xFFC08A5A), Color(0xFF3E5A48), Color(0xFF7EAEC8)),
    "Screen recordings" to listOf(Color(0xFF2E3A46), Color(0xFF52707E), Color(0xFF1E2E3A)),
)

@Composable
fun Spine(
    selectedIndex: Int = -1,
    breathe: Boolean = true,
    media: Boolean = true,
    gaps: Boolean = true,
    gradientEnv: Boolean = true,
    label: String = "S · the spine",
) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (gradientEnv) Modifier.env() else Modifier.drawWithCache { onDrawBehind { drawRect(Color(0xFF101114)) } })
            .clipToBounds(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Threshold(label)
            val folders = Listing.filter { it.folder }
            val files = Listing.filterNot { it.folder }
            folders.forEachIndexed { i, it ->
                Object(it, selected = i == selectedIndex, media = media, gaps = gaps)
            }
            if (breathe) Breath("21 files · 4.8 GB in one of them")
            files.forEachIndexed { i, it ->
                Object(it, selected = (i + folders.size) == selectedIndex, media = media, gaps = gaps)
            }
        }
    }
}

/**
 * THE THRESHOLD — the only place the browse composition breathes.
 *
 * Negative space is not distributed evenly; it is concentrated where the user
 * arrives. The figure is the largest thing on the screen and it is set in the
 * lightest weight available, which is what stops "big" from becoming "loud".
 * It bleeds past the content gutter to the frame, so the type reads as
 * architecture rather than as a heading with padding.
 */
@Composable
private fun Threshold(label: String) {
    Box(Modifier.height(18.dp))
    BasicTextCompat(
        label.uppercase(),
        TextStyle(color = ink2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
        Modifier.padding(start = GUTTER),
    )
    Box(Modifier.height(6.dp))
    Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.Bottom) {
        BasicTextCompat(
            "9.8",
            TextStyle(color = ink, fontSize = 62.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
        )
        Box(Modifier.width(8.dp))
        Box(Modifier.padding(bottom = 12.dp)) {
            BasicTextCompat(
                "GB",
                TextStyle(color = ink1, fontSize = 17.sp, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp),
            )
        }
    }
    BasicTextCompat(
        "Camera · 428 items",
        TextStyle(color = ink2, fontSize = 11.5.sp, letterSpacing = 0.3.sp),
        Modifier.padding(start = GUTTER),
    )
    Box(Modifier.height(24.dp))
}

/**
 * A BREATH — rhythm without breaking uniformity.
 *
 * The first render of the spine was monotonous: twenty-one identical 54dp
 * rows with no grouping, which is the last generic thing left in it. A breath
 * marks a change of bucket in the current sort - folders to files, one date
 * range to the next - and it is the only vertical variation permitted inside a
 * listing.
 *
 * Rows stay uniform WITHIN a group, because that is what scanning needs. The
 * space goes between groups, where it means something.
 */
@Composable
private fun Breath(text: String) {
    Box(Modifier.height(20.dp))
    BasicTextCompat(
        text.uppercase(),
        TextStyle(color = ink2, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp),
        Modifier.padding(start = GUTTER),
    )
    Box(Modifier.height(10.dp))
}

/**
 * One object.
 *
 * At rest there is NO BODY - no rectangle, no container, no surface. A file is
 * a mark in the spine and two lines of text on open ground.
 *
 * The body is a STATE. It appears under the finger and on selection, which
 * means the material that used to be the default now means "this object is
 * under your control". Selection stops being a tint on a row and becomes the
 * object materialising out of the environment.
 */
@Composable
private fun Object(item: Item, selected: Boolean, media: Boolean, gaps: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(ROW)
            .drawWithCache {
                val w = MARK_MIN.toPx() +
                    (MARK_MAX.toPx() - MARK_MIN.toPx()) * Mass.relative(item.bytes, Largest)
                val x = GUTTER.toPx()
                val gap = if (gaps) MARK_GAP.toPx() else 0f
                val top = gap
                val h = size.height - gap * 2

                val composite = folderComposites[item.name]
                val brush: Brush? = when {
                    !media -> null
                    // A folder's mark is composed from what is inside it, so a
                    // photo folder is recognisable as one before it is opened.
                    composite != null -> Brush.verticalGradient(
                        composite.map { if (selected) it.darken() else it },
                        startY = top, endY = top + h,
                    )
                    item.media != null -> Brush.verticalGradient(
                        listOf(
                            if (selected) item.media.first.darken() else item.media.first,
                            if (selected) item.media.second.darken() else item.media.second,
                        ),
                        startY = top, endY = top + h,
                    )
                    else -> null
                }
                val flat = if (selected) item.tint.darken() else item.tint

                onDrawBehind {
                    if (selected) drawRect(selGround)
                    if (brush != null) {
                        drawRect(brush, Offset(x, top), Size(w, h))
                    } else {
                        drawRect(flat, Offset(x, top), Size(w, h))
                    }
                }
            },
    ) {
        Column(Modifier.padding(start = TEXT_X, end = 18.dp, top = 9.dp)) {
            BasicTextCompat(
                item.name,
                TextStyle(
                    color = if (selected) selInk else ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                ),
                maxLines = 1,
            )
            Box(Modifier.height(2.dp))
            Row {
                // The size is promoted above the kind, because the size is what
                // the mark is a picture of and the two must agree.
                BasicTextCompat(
                    sizeOf(item.bytes),
                    TextStyle(
                        color = if (selected) Color(0xFF3E4146) else ink1,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                    ),
                )
                Box(Modifier.width(7.dp))
                BasicTextCompat(
                    item.kind,
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

/** Dark enough to hold identity against an inverted ground. */
private fun Color.darken() = Color(red * 0.34f, green * 0.34f, blue * 0.34f, 1f)

// ===========================================================================
// Selection as a collective state
// ===========================================================================

/**
 * Multiple selection.
 *
 * Selected objects materialise; the ones between them do not. A run of
 * selected files therefore reads as ONE BODY with the unselected ones cut out
 * of it, which is what "these are now a collection" should look like. No
 * checkboxes, no blue.
 *
 * The ledger is the only chrome on screen and it is the one place glass
 * survives: chrome may be optical, content never is.
 */
@Composable
fun SpineSelection() {
    val chosen = setOf(3, 4, 5, 6)
    Box(Modifier.fillMaxSize().env().clipToBounds()) {
        Column(Modifier.fillMaxSize()) {
            Threshold("4 selected")
            Listing.take(12).forEachIndexed { i, it ->
                Object(it, selected = i in chosen, media = true, gaps = true)
            }
            Box(Modifier.height(20.dp))
            // CHROME. Glass, a rim, a radius - everything content is denied.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(58.dp)
                    .drawWithCache {
                        onDrawBehind {
                            drawRect(Color(0x17FFFFFF))
                            drawRect(Color(0x26FFFFFF), size = Size(size.width, 1.dp.toPx()))
                        }
                    },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicTextCompat(
                            "201.4 MB",
                            TextStyle(color = ink, fontSize = 19.sp, fontWeight = FontWeight.Medium),
                        )
                        BasicTextCompat(
                            "4 objects held",
                            TextStyle(color = ink2, fontSize = 11.sp, letterSpacing = 0.4.sp),
                        )
                    }
                    listOf("MOVE", "COPY", "TRASH").forEach {
                        BasicTextCompat(
                            it,
                            TextStyle(
                                color = Color(0xFF4FB3A0), fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp,
                            ),
                        )
                        Box(Modifier.width(16.dp))
                    }
                }
            }
        }
    }
}

// ===========================================================================
// The root composition
// ===========================================================================

/**
 * The FILISH root, as a composition rather than a dashboard.
 *
 * Not built - sketched, to answer whether storage, places and insights can
 * coexist without becoming a grid of cards. They can, because they are all the
 * same grammar: an object with a mark whose width is its magnitude.
 *
 * This is the ONE place occlusion earns its keep. The device figure sits in a
 * far plane at display scale and the spine passes in front of it, so the root
 * has literal depth that the browse list does not need and cannot afford - the
 * browse list is dense text, and text over text is never depth, it is a
 * collision.
 */
@Composable
fun Root() {
    val cap = 256_000_000_000L
    val places = listOf(
        Triple("Photographs", 42_100_000_000L, Color(0xFFD08A5A)),
        Triple("Video", 31_700_000_000L, Color(0xFF9A87C9)),
        Triple("Audio", 12_200_000_000L, Color(0xFF6BA6C7)),
        Triple("Documents", 1_800_000_000L, Color(0xFF7CA876)),
    )
    Box(Modifier.fillMaxSize().env().clipToBounds()) {
        // FAR PLANE.
        //
        // The first render put an unlabelled "217" back here and it was
        // decoration pretending to be information - a huge number with nothing
        // saying what it counted. Pairing it with the foreground figure fixes
        // that: 38.4 free in front, 217 used behind, the two halves of one
        // fact at two depths. The DEPTH IS THE RELATIONSHIP, which is the only
        // justification for spending a whole plane on it.
        Box(Modifier.padding(start = 6.dp, top = 118.dp)) {
            Column {
                BasicTextCompat(
                    "217",
                    TextStyle(color = Color(0xFF26292E), fontSize = 190.sp, fontWeight = FontWeight.Light, letterSpacing = (-10).sp),
                )
                Box(Modifier.padding(start = 16.dp)) {
                    BasicTextCompat(
                        "GB USED",
                        TextStyle(color = Color(0xFF3A3F45), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
                    )
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.height(26.dp))
            BasicTextCompat(
                "INTERNAL STORAGE",
                TextStyle(color = ink2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
                Modifier.padding(start = GUTTER),
            )
            Box(Modifier.height(8.dp))
            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.Bottom) {
                BasicTextCompat(
                    "38.4",
                    TextStyle(color = ink, fontSize = 74.sp, fontWeight = FontWeight.Light, letterSpacing = (-3).sp),
                )
                Box(Modifier.width(9.dp))
                Box(Modifier.padding(bottom = 15.dp)) {
                    BasicTextCompat(
                        "GB FREE",
                        TextStyle(color = ink1, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp),
                    )
                }
            }
            Box(Modifier.height(64.dp))
            // NEAR PLANE — the spine crosses the figure behind it.
            places.forEach { (n, b, t) ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW)
                        .drawWithCache {
                            val w = MARK_MIN.toPx() +
                                (MARK_MAX.toPx() - MARK_MIN.toPx()) * Mass.relative(b, cap)
                            onDrawBehind {
                                drawRect(t, Offset(GUTTER.toPx(), 2.dp.toPx()), Size(w, size.height - 4.dp.toPx()))
                            }
                        },
                ) {
                    Column(Modifier.padding(start = TEXT_X, top = 9.dp)) {
                        BasicTextCompat(
                            n,
                            TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        )
                        BasicTextCompat(
                            sizeOf(b),
                            TextStyle(color = ink1, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
                        )
                    }
                }
            }
            Box(Modifier.height(22.dp))
            BasicTextCompat(
                "RECLAIMABLE",
                TextStyle(color = ink2, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp),
                Modifier.padding(start = GUTTER),
            )
            Box(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ROW)
                    .drawWithCache {
                        val w = MARK_MIN.toPx() +
                            (MARK_MAX.toPx() - MARK_MIN.toPx()) * Mass.relative(3_200_000_000L, cap)
                        onDrawBehind {
                            val c = Color(0xFF4FB3A0)
                            drawRect(
                                Brush.horizontalGradient(
                                    listOf(c.copy(alpha = 0.26f), c.copy(alpha = 0f)),
                                    startX = GUTTER.toPx() + w,
                                    endX = GUTTER.toPx() + w + 8.dp.toPx(),
                                ),
                                Offset(GUTTER.toPx() + w, 2.dp.toPx()),
                                Size(8.dp.toPx(), size.height - 4.dp.toPx()),
                            )
                            drawRect(c, Offset(GUTTER.toPx(), 2.dp.toPx()), Size(w, size.height - 4.dp.toPx()))
                        }
                    },
            ) {
                Column(Modifier.padding(start = TEXT_X, top = 9.dp)) {
                    BasicTextCompat(
                        "1,284 duplicates",
                        TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    )
                    BasicTextCompat(
                        "3.2 GB · scanning, 61%",
                        TextStyle(color = ink1, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
                    )
                }
            }
        }
    }
}
