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
import kotlin.math.ln

/**
 * V3.1 ART DIRECTION — four worlds.
 *
 * V3 answered "what is this made of". These ask a bigger question: "what IS
 * this place, and what carries hierarchy inside it?"
 *
 * Each is a genuinely different hypothesis, not a colour variation. They are
 * deliberately rendered with the SAME six objects, including a 12 KB text file
 * that exists purely to break the ones that assume everything is big, and a
 * Cyrillic filename that exists to break the ones that assume Latin.
 */

// ---- The specimen content, identical across all four ----------------------

data class Obj(
    val name: String,
    val meta: String,
    val bytes: Long,
    val tint: Color,
    val selected: Boolean = false,
)

val Objects = listOf(
    Obj("Camera", "428 items", 2_297_000_000L, Color(0xFFD08A5A)),
    Obj("VID_20240918_final.mp4", "MP4 · 2 days ago", 1_975_000_000L, Color(0xFF9A87C9), selected = true),
    Obj("DSC01847.ARW", "RAW · 1 wk ago", 51_275_000L, Color(0xFFD9A05A)),
    Obj("Договор аренды.pdf", "PDF · 4 wk ago", 2_243_000L, Color(0xFF7CA876)),
    Obj("notes.txt", "TXT · today", 12_288L, Color(0xFF8397B8)),
    Obj("Archive.zip", "ZIP · 3 mo ago", 418_000_000L, Color(0xFFB59560)),
)

fun sizeLabel(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1e9)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1e3)
    else -> "$bytes B"
}

/**
 * Magnitude as a normalised 0..1, log2-compressed.
 *
 * Linear is unusable: a 12 KB file beside a 2 GB folder is 0.0006% of it, so
 * linear mass makes every small file literally invisible. Log2 means each
 * doubling is a constant step, which is also how people actually reason about
 * file sizes - "twice as big", not "180 megabytes bigger".
 */
fun magnitude(bytes: Long): Float {
    val lo = 10.0 // 1 KB
    val hi = 36.0 // ~64 GB
    val l = if (bytes <= 1024L) lo else ln(bytes.toDouble()) / ln(2.0)
    return (((l - lo) / (hi - lo)).coerceIn(0.0, 1.0)).toFloat()
}

private val nameStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val metaStyle = TextStyle(fontSize = 12.sp)
private val eyebrow = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)

@Composable
private fun Label(text: String, colour: Color, modifier: Modifier = Modifier) {
    BasicTextCompat(text.uppercase(), eyebrow.copy(color = colour), modifier)
}

// ===========================================================================
// H — DEEP FIELD
// Hypothesis: LIGHT carries hierarchy. The substrate is a void; objects are
// revealed by their proximity to a light source, and what is far is dim.
// ===========================================================================

@Composable
fun DeepField() {
    val ink = Color(0xFFF0F2F4)
    val quiet = Color(0xFF8A9096)
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val ground = Brush.verticalGradient(
                    listOf(Color(0xFF0A0B0D), Color(0xFF050506)),
                )
                val pool = Brush.radialGradient(
                    colors = listOf(Color(0x24FFFFFF), Color(0x00FFFFFF)),
                    center = Offset(size.width * 0.34f, size.height * 0.20f),
                    radius = size.width * 0.95f,
                )
                onDrawBehind {
                    drawRect(ground)
                    drawRect(pool)
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp)) {
            Label("H · deep field", quiet)
            Box(Modifier.height(10.dp))
            BasicTextCompat(
                "Camera",
                TextStyle(color = ink.copy(alpha = 0.30f), fontSize = 52.sp, fontWeight = FontWeight.Light),
            )
            Box(Modifier.height(22.dp))
            Objects.forEachIndexed { i, o ->
                // Distance from the light. Everything below the pool falls away.
                val lit = (1f - i * 0.16f).coerceIn(0.26f, 1f)
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(if (o.selected) 34.dp else 26.dp)
                            .drawWithCache {
                                val c = if (o.selected) Color.White else o.tint
                                onDrawBehind { drawRect(c.copy(alpha = lit)) }
                            },
                    )
                    Box(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        BasicTextCompat(o.name, nameStyle.copy(color = ink.copy(alpha = lit)), maxLines = 1)
                        BasicTextCompat(
                            "${sizeLabel(o.bytes)} · ${o.meta}",
                            metaStyle.copy(color = quiet.copy(alpha = lit)),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f))
            BasicTextCompat(
                "38.4",
                TextStyle(color = ink, fontSize = 40.sp, fontWeight = FontWeight.Medium),
            )
            BasicTextCompat("GB free of 256 GB", metaStyle.copy(color = quiet))
        }
    }
}

// ===========================================================================
// I — CORE SAMPLE
// Hypothesis: MASS carries hierarchy. The directory is one continuous body
// drilled through; each object's vertical extent IS its size. No gaps, no
// cards - only cuts in the material.
// ===========================================================================

@Composable
fun CoreSample() {
    val ink = Color(0xFFF2F1EE)
    val quiet = Color(0xFF9A978F)
    Box(Modifier.fillMaxSize().drawWithCache {
        val g = Brush.verticalGradient(listOf(Color(0xFF111215), Color(0xFF08090B)))
        onDrawBehind { drawRect(g) }
    }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp)) {
            Label("i · core sample", quiet)
            Box(Modifier.height(18.dp))
            Objects.forEach { o ->
                // Extent is magnitude. A 12 KB file gets the floor; a 2 GB
                // folder gets a visible block of material.
                val h = (44 + magnitude(o.bytes) * 92).dp
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(h)
                        .drawWithCache {
                            val body = if (o.selected) {
                                Brush.verticalGradient(listOf(Color(0xFFE9E7E2), Color(0xFFDAD7D1)))
                            } else {
                                Brush.verticalGradient(
                                    listOf(Color(0x14FFFFFF), Color(0x07FFFFFF)),
                                )
                            }
                            val cut = Color(0x33000000)
                            onDrawBehind {
                                drawRect(body)
                                drawRect(o.tint, size = Size(4.dp.toPx(), size.height))
                                drawRect(cut, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
                            }
                        },
                ) {
                    Column(Modifier.padding(start = 16.dp, top = 11.dp)) {
                        BasicTextCompat(
                            o.name,
                            nameStyle.copy(color = if (o.selected) Color(0xFF14161A) else ink),
                            maxLines = 1,
                        )
                        BasicTextCompat(
                            "${sizeLabel(o.bytes)} · ${o.meta}",
                            metaStyle.copy(color = if (o.selected) Color(0xFF4A4D52) else quiet),
                        )
                    }
                }
            }
        }
    }
}

// ===========================================================================
// J — OPTICAL BENCH
// Hypothesis: RESOLUTION carries hierarchy. The environment is a flat, evenly
// lit stage. A fixed instrument resolves whatever passes under it; everything
// else is a compressed index.
// ===========================================================================

@Composable
fun OpticalBench() {
    val ink = Color(0xFFEDEEF0)
    val quiet = Color(0xFF8C9094)
    Box(Modifier.fillMaxSize().drawWithCache {
        onDrawBehind { drawRect(Color(0xFF15171A)) }
    }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp)) {
            Label("j · optical bench", quiet)
            Box(Modifier.height(16.dp))
            Objects.take(2).forEach { o -> IndexLine(o, quiet) }

            // The instrument. Fixed position; content passes under it.
            val o = Objects[2]
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .drawWithCache {
                        val glass = Brush.verticalGradient(
                            listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF)),
                        )
                        onDrawBehind {
                            drawRect(glass)
                            drawRect(Color(0x59FFFFFF), size = Size(size.width, 1.dp.toPx()))
                            drawRect(
                                Color(0x59FFFFFF),
                                topLeft = Offset(0f, size.height - 1.dp.toPx()),
                                size = Size(size.width, 1.dp.toPx()),
                            )
                            drawRect(o.tint, size = Size(3.dp.toPx(), size.height))
                        }
                    },
            ) {
                Column(Modifier.padding(start = 18.dp, top = 16.dp)) {
                    BasicTextCompat(o.name, TextStyle(color = ink, fontSize = 19.sp, fontWeight = FontWeight.Medium))
                    Box(Modifier.height(6.dp))
                    BasicTextCompat(sizeLabel(o.bytes), TextStyle(color = ink, fontSize = 30.sp, fontWeight = FontWeight.Medium))
                    Box(Modifier.height(6.dp))
                    BasicTextCompat("Sony ARW · 6048 × 4024 · /DCIM/100MSDCF", metaStyle.copy(color = quiet))
                    Box(Modifier.height(10.dp))
                    Row {
                        Label("open", ink)
                        Box(Modifier.width(18.dp))
                        Label("move", ink)
                        Box(Modifier.width(18.dp))
                        Label("trash", ink)
                    }
                }
            }
            Objects.drop(3).forEach { o -> IndexLine(o, quiet) }
        }
    }
}

@Composable
private fun IndexLine(o: Obj, quiet: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(12.dp).drawWithCache { onDrawBehind { drawRect(o.tint.copy(alpha = 0.7f)) } })
        Box(Modifier.width(12.dp))
        BasicTextCompat(o.name, metaStyle.copy(color = quiet), Modifier.weight(1f), maxLines = 1)
        BasicTextCompat(sizeLabel(o.bytes), metaStyle.copy(color = quiet.copy(alpha = 0.7f)))
    }
}

// ===========================================================================
// K — ATMOSPHERE
// Hypothesis: CONTENT makes the environment. The ground is a field derived
// from the folder's own media, so you know where you are before reading.
// ===========================================================================

@Composable
fun Atmosphere() {
    val ink = Color(0xFFF6F3EF)
    val quiet = Color(0xFFBCB3A8)
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                // "Sampled" from a warm photo folder.
                val field = Brush.linearGradient(
                    colors = listOf(Color(0xFF3C2A20), Color(0xFF1E1A22), Color(0xFF12202A)),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                )
                val haze = Brush.radialGradient(
                    colors = listOf(Color(0x2ED9884A), Color(0x00D9884A)),
                    center = Offset(size.width * 0.72f, size.height * 0.30f),
                    radius = size.width * 0.8f,
                )
                onDrawBehind {
                    drawRect(field)
                    drawRect(haze)
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp)) {
            Label("k · atmosphere", quiet)
            Box(Modifier.height(10.dp))
            BasicTextCompat(
                "Camera",
                TextStyle(color = ink.copy(alpha = 0.92f), fontSize = 44.sp, fontWeight = FontWeight.Light),
            )
            BasicTextCompat("428 items · 2.14 GB · mostly photographs", metaStyle.copy(color = quiet))
            Box(Modifier.height(24.dp))
            Objects.forEach { o ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .drawWithCache {
                            val body = if (o.selected) {
                                Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xCCFFFFFF)))
                            } else {
                                Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0FFFFFFF)))
                            }
                            onDrawBehind {
                                drawRect(body)
                                drawRect(o.tint, size = Size(4.dp.toPx(), size.height))
                            }
                        },
                ) {
                    Column(Modifier.padding(start = 16.dp, top = 11.dp)) {
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
                Box(Modifier.height(3.dp))
            }
        }
    }
}
