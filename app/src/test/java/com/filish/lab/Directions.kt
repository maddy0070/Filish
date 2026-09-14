package com.filish.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filish.design.component.BasicTextCompat

private val nameStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val metaStyle = TextStyle(fontSize = 12.sp)

// ---------------------------------------------------------------------------
// A — VAPOUR. Generic glassmorphism, included as the control.
// Backdrop blur, 12% white fill, 1px white border, large radius, floating.
// This is the thing to beat, and the thing NOT to ship.
// ---------------------------------------------------------------------------

@Composable
fun DirectionVapour() {
    SpecimenSheet(
        name = "A · Vapour",
        note = "The control. Backdrop blur + 12% white + hairline + big radius. " +
            "Everything floats. This is what 'liquid glass' usually means.",
        labelInk = Color.White,
        ground = { m ->
            Box(m) {
                // The colourful backdrop this style depends on.
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF2B1D4A))
                    drawCircle(Color(0xFF6D3FA8), size.minDimension * 0.5f, Offset(size.width * 0.2f, size.height * 0.2f))
                    drawCircle(Color(0xFF2E7D8F), size.minDimension * 0.42f, Offset(size.width * 0.85f, size.height * 0.45f))
                    drawCircle(Color(0xFFB0476B), size.minDimension * 0.35f, Offset(size.width * 0.4f, size.height * 0.85f))
                }
                Box(Modifier.fillMaxSize().blur(58.dp))
            }
        },
    ) {
        SpecimenRows.forEach { (name, meta, selected) ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = if (selected) 0.26f else 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Column {
                    BasicTextCompat(name, nameStyle.copy(color = Color.White), maxLines = 1)
                    BasicTextCompat(meta, metaStyle.copy(color = Color.White.copy(alpha = 0.66f)))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) { BasicTextCompat("Move to trash", nameStyle.copy(color = Color.White)) }
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { BasicTextCompat("Videos", metaStyle.copy(color = Color.White.copy(alpha = 0.8f))) }
        }
    }
}

// ---------------------------------------------------------------------------
// B — CONTACT GLASS. Thin glass resting ON a ground, not floating above it.
// Physics: a thin sheet in contact with a surface barely diffuses what is
// under it. It tints, it catches light on its edges, it casts a tight contact
// shadow. No blur required - which is the whole point on Android.
// ---------------------------------------------------------------------------

@Composable
fun DirectionContact() {
    val ground = Color(0xFFEDEAE3)
    val ink = Color(0xFF17171A)
    SpecimenSheet(
        name = "B · Contact glass",
        note = "Thin glass in contact with the ground. Tint + edge light + contact " +
            "shadow. No blur anywhere. Identical on API 26 and 35.",
        labelInk = ink.copy(alpha = 0.55f),
        ground = { m ->
            Canvas(m) {
                drawRect(ground)
                // The substrate has depth: very slightly darker toward the base.
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x0D000000)),
                    ),
                )
            }
        },
    ) {
        SpecimenRows.forEach { (name, meta, selected) ->
            ContactPane(selected = selected, ink = ink) {
                Column {
                    BasicTextCompat(name, nameStyle.copy(color = if (selected) Color(0xFFF7F5F1) else ink), maxLines = 1)
                    BasicTextCompat(
                        meta,
                        metaStyle.copy(
                            color = if (selected) Color(0xFFF7F5F1).copy(alpha = 0.7f) else ink.copy(alpha = 0.55f),
                        ),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFF1E6F62))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) { BasicTextCompat("Move to trash", nameStyle.copy(color = Color(0xFFF7F5F1))) }
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(11.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { BasicTextCompat("Videos", metaStyle.copy(color = ink.copy(alpha = 0.75f))) }
        }
    }
}

/**
 * One pane of contact glass.
 *
 * Four cheap ingredients, no offscreen buffer: a translucent fill, a bright
 * top edge and dark bottom edge (the light the sheet catches), a hairline rim,
 * and a tight shadow where it meets the ground.
 */
@Composable
private fun ContactPane(selected: Boolean, ink: Color, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Box(Modifier.fillMaxWidth()) {
        // Contact shadow: tight and close, because the sheet is touching.
        Canvas(Modifier.fillMaxWidth().height(58.dp)) {
            drawRoundRect(
                Color(0x14000000),
                topLeft = Offset(0f, 3.dp.toPx()),
                size = Size(size.width, size.height - 3.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(13.dp.toPx(), 13.dp.toPx()),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (selected) {
                        Brush.verticalGradient(listOf(Color(0xFF2A2A26), Color(0xFF1C1C19)))
                    } else {
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.44f)),
                        )
                    },
                )
                .border(
                    1.dp,
                    if (selected) Color(0xFF3FA791).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.85f),
                    shape,
                )
                .padding(horizontal = 15.dp, vertical = 12.dp),
        ) { content() }
    }
}

// ---------------------------------------------------------------------------
// C — DEEP SPATIAL. Strong z-separation, blurred backdrop, parallax, drama.
// ---------------------------------------------------------------------------

@Composable
fun DirectionDeep() {
    SpecimenSheet(
        name = "C · Deep spatial",
        note = "Strong z-separation. Blurred backdrop behind every layer, large " +
            "radii, heavy shadow. Dramatic - and an offscreen buffer per surface.",
        labelInk = Color.White,
        ground = { m ->
            Box(m) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF0A1A20))
                    drawCircle(Color(0xFF14464F), size.minDimension * 0.7f, Offset(size.width * 0.75f, size.height * 0.15f))
                    drawCircle(Color(0xFF1C2E3D), size.minDimension * 0.6f, Offset(size.width * 0.1f, size.height * 0.75f))
                }
                Box(Modifier.fillMaxSize().blur(72.dp))
            }
        },
    ) {
        SpecimenRows.forEach { (name, meta, selected) ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (selected) 0.20f else 0.09f),
                                Color.White.copy(alpha = if (selected) 0.10f else 0.04f),
                            ),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Column {
                    BasicTextCompat(name, nameStyle.copy(color = Color.White), maxLines = 1)
                    BasicTextCompat(meta, metaStyle.copy(color = Color.White.copy(alpha = 0.55f)))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF2BD9B4).copy(alpha = 0.85f))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) { BasicTextCompat("Move to trash", nameStyle.copy(color = Color(0xFF042420))) }
        }
    }
}

// ---------------------------------------------------------------------------
// D — CRYSTALLINE. Precise, faceted, instrument-like. Sharp corners,
// refraction bands at the rim, hairlines doing the structural work.
// ---------------------------------------------------------------------------

@Composable
fun DirectionCrystalline() {
    val ground = Color(0xFF0E1013)
    SpecimenSheet(
        name = "D · Crystalline",
        note = "Cut rather than poured. Near-square corners, a refraction band at " +
            "the rim, hairlines carrying the structure. Reads as an instrument.",
        labelInk = Color(0xFF9FB0B8),
        ground = { m ->
            Canvas(m) {
                drawRect(ground)
                var y = 0f
                while (y < size.height) {
                    drawLine(Color(0x0AFFFFFF), Offset(0f, y), Offset(size.width, y), 1f)
                    y += 28f
                }
            }
        },
    ) {
        SpecimenRows.forEach { (name, meta, selected) ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (selected) Color(0xFF12262B) else Color.White.copy(alpha = 0.045f),
                    )
                    .border(
                        1.dp,
                        if (selected) Color(0xFF41C8AE) else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(3.dp),
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The refraction band: a bright sliver where light bends
                    // through the cut edge.
                    Canvas(Modifier.width(3.dp).height(54.dp)) {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF41C8AE).copy(alpha = if (selected) 1f else 0.35f),
                                    Color(0xFF41C8AE).copy(alpha = 0f),
                                ),
                            ),
                        )
                    }
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        BasicTextCompat(name, nameStyle.copy(color = Color(0xFFE6EEF1)), maxLines = 1)
                        BasicTextCompat(meta, metaStyle.copy(color = Color(0xFF7D919A)))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF41C8AE))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) { BasicTextCompat("Move to trash", nameStyle.copy(color = Color(0xFF04211D))) }
            Box(
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { BasicTextCompat("Videos", metaStyle.copy(color = Color(0xFF9FB0B8))) }
        }
    }
}

// ---------------------------------------------------------------------------
// E — STRATA. The invention.
// The GROUND is liquid: storage as a volume with a visible level and depth.
// The GLASS is thin and takes its tint from the ground beneath it.
// The CONTENT is solid ink and is never made of glass.
// ---------------------------------------------------------------------------

@Composable
fun DirectionStrata() {
    val ink = Color(0xFF14161A)
    SpecimenSheet(
        name = "E · Strata",
        note = "The ground is the liquid, not the chrome. Storage has a level and a " +
            "depth; glass is a thin lens over it; information is solid ink.",
        labelInk = ink.copy(alpha = 0.5f),
        ground = { m ->
            Canvas(m) {
                // Above the waterline: clear, light - free space.
                drawRect(Color(0xFFF3F1EC))
                // The volume in use, resting at the base. This is storage.
                val level = size.height * 0.42f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFD8DED9), Color(0xFFC2CFC8)),
                        startY = level,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, level),
                    size = Size(size.width, size.height - level),
                )
                // The meniscus: one bright line where the material meets air.
                drawLine(
                    Color.White.copy(alpha = 0.85f),
                    Offset(0f, level), Offset(size.width, level), 1.5f,
                )
            }
        },
    ) {
        SpecimenRows.forEach { (name, meta, selected) ->
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        if (selected) {
                            Brush.verticalGradient(listOf(Color(0xFF1E6F62), Color(0xFF17584E)))
                        } else {
                            // A lens: brighter at the top edge where it catches
                            // light, fading to near-clear so the ground reads
                            // through it.
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.66f),
                                    Color.White.copy(alpha = 0.30f),
                                ),
                            )
                        },
                    )
                    .border(
                        1.dp,
                        if (selected) Color(0xFF4FC3AC).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f),
                        shape,
                    )
                    .padding(horizontal = 15.dp, vertical = 12.dp),
            ) {
                Column {
                    BasicTextCompat(
                        name,
                        nameStyle.copy(color = if (selected) Color(0xFFF2F7F5) else ink),
                        maxLines = 1,
                    )
                    BasicTextCompat(
                        meta,
                        metaStyle.copy(
                            color = if (selected) Color(0xFFF2F7F5).copy(alpha = 0.72f) else ink.copy(alpha = 0.56f),
                        ),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E6F62))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) { BasicTextCompat("Move to trash", nameStyle.copy(color = Color(0xFFF2F7F5))) }
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { BasicTextCompat("Videos", metaStyle.copy(color = ink.copy(alpha = 0.72f))) }
        }
    }
}
