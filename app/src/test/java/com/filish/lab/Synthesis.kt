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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
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
 * SUBSTRATE & LENS — the synthesis.
 *
 * Material model from B (contact physics, no blur), signature from D (the
 * refraction band, precise geometry), meaning from E (the ground is the
 * liquid, and it has a level).
 *
 * Three materials, and the rule that keeps it usable:
 *
 *   SUBSTRATE  the ground. Storage as a volume with depth and a waterline.
 *              This is the only thing that is liquid.
 *   LENS       thin glass in contact with the substrate. Tints, catches an
 *              edge light, refracts at its leading rim. Never blurs.
 *   INK        the content. Solid, opaque, maximum contrast. Information is
 *              never made of glass.
 *
 * The signature is the leading refraction band, and it earns its place by
 * doing three jobs at once: it is the thing that makes a FILISH row
 * identifiable, it is the selection indicator, and it carries the file-kind
 * tint. One device, three meanings, no decoration.
 */
private val nameStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val metaStyle = TextStyle(fontSize = 12.sp)

data class Skin(
    val airHigh: Color,
    val airLow: Color,
    val volumeHigh: Color,
    val volumeLow: Color,
    val meniscus: Color,
    val lensHigh: Color,
    val lensLow: Color,
    val rim: Color,
    val ink: Color,
    val inkQuiet: Color,
    val signal: Color,
    val selectGround: Color,
    val selectInk: Color,
)

val DaySkin = Skin(
    airHigh = Color(0xFFF6F4EF), airLow = Color(0xFFEFEDE7),
    volumeHigh = Color(0xFFDCE2DC), volumeLow = Color(0xFFC6D0C9),
    meniscus = Color(0xE6FFFFFF),
    lensHigh = Color(0x8CFFFFFF), lensLow = Color(0x33FFFFFF),
    rim = Color(0xA6FFFFFF),
    ink = Color(0xFF15171A), inkQuiet = Color(0xFF5E625F),
    signal = Color(0xFF1E6F62),
    selectGround = Color(0xFF1B1D1B), selectInk = Color(0xFFF4F6F2),
)

val NightSkin = Skin(
    airHigh = Color(0xFF0D0F11), airLow = Color(0xFF101315),
    volumeHigh = Color(0xFF16201F), volumeLow = Color(0xFF1B2A27),
    meniscus = Color(0x4D6FD9C4),
    lensHigh = Color(0x21FFFFFF), lensLow = Color(0x0FFFFFFF),
    rim = Color(0x33FFFFFF),
    ink = Color(0xFFEFF2EF), inkQuiet = Color(0xFFA7B0AA),
    signal = Color(0xFF4FC3AC),
    selectGround = Color(0xFFE9EDE8), selectInk = Color(0xFF14171A),
)

/** The substrate: air above, material below, one bright meniscus between. */
@Composable
fun Substrate(skin: Skin, fullness: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(skin.airHigh, skin.airLow)))
    }
}

/**
 * The volume — storage shown as material with a level.
 *
 * This is the one genuinely liquid element, and after testing it belongs
 * ONLY where storage is the subject: the header gauge, the storage screen,
 * the delete confirmation. Behind a scrolling list it is wallpaper, and worse,
 * every row cuts the waterline into fragments so it reads as a rendering
 * fault rather than as a level. Removing it from the browsing ground was the
 * single biggest improvement in this exploration.
 */
@Composable
fun Volume(skin: Skin, fullness: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(skin.airHigh, skin.airLow)))
        val level = size.height * (1f - fullness)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(skin.volumeHigh, skin.volumeLow),
                startY = level,
                endY = size.height,
            ),
            topLeft = Offset(0f, level),
            size = Size(size.width, size.height - level),
        )
        drawLine(skin.meniscus, Offset(0f, level), Offset(size.width, level), 1.6f)
    }
}

/**
 * One lens.
 *
 * Fill is a vertical gradient from a brighter top edge (the light it catches)
 * to a weaker base, so the substrate reads through the lower half. A hairline
 * rim. A tight contact shadow — it is touching the ground, not hovering. And
 * the leading refraction band.
 */
@Composable
fun Lens(
    skin: Skin,
    bandTint: Color,
    selected: Boolean,
    bandStrength: Float = 1f,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(60.dp)) {
            drawRoundRect(
                Color(0x0F000000),
                topLeft = Offset(0f, 2.5.dp.toPx()),
                size = Size(size.width, size.height - 2.5.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (selected) {
                        Brush.verticalGradient(listOf(skin.selectGround, skin.selectGround))
                    } else {
                        Brush.verticalGradient(listOf(skin.lensHigh, skin.lensLow))
                    },
                )
                .border(1.dp, if (selected) bandTint.copy(alpha = 0.5f) else skin.rim, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The refraction band. Brightest where light enters at the top,
            // falling away down the edge.
            Canvas(Modifier.width(4.dp).height(58.dp)) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            bandTint.copy(alpha = if (selected) 1f else 1f * bandStrength),
                            bandTint.copy(alpha = if (selected) 0.5f else 0.28f * bandStrength),
                        ),
                    ),
                )
            }
            Box(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) { content() }
        }
    }
}

@Composable
fun SynthesisSpecimen(skin: Skin, title: String, note: String) {
    val kindTints = if (skin == DaySkin) {
        listOf(Color(0xFFB4703F), Color(0xFF5E6B63), Color(0xFF6D5B9E), Color(0xFF4E7A4A))
    } else {
        listOf(Color(0xFFD08A5A), Color(0xFF9AA69F), Color(0xFF9A87C9), Color(0xFF7CA876))
    }
    SpecimenSheet(
        name = title,
        note = note,
        labelInk = skin.inkQuiet,
        ground = { m -> Substrate(skin, fullness = 0.55f, modifier = m) },
    ) {
        SpecimenRows.forEachIndexed { i, (name, meta, selected) ->
            Lens(skin, kindTints[i], selected) {
                Column {
                    BasicTextCompat(
                        name,
                        nameStyle.copy(color = if (selected) skin.selectInk else skin.ink),
                        maxLines = 1,
                    )
                    BasicTextCompat(
                        meta,
                        metaStyle.copy(
                            color = if (selected) skin.selectInk.copy(alpha = 0.72f) else skin.inkQuiet,
                        ),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // A primary action is SOLID, not glass. It is the one thing that
            // must never be ambiguous, so it is the one thing with no
            // transparency at all.
            Box(
                Modifier
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                    .background(skin.signal)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                BasicTextCompat(
                    "Move to trash",
                    nameStyle.copy(color = if (skin == DaySkin) Color(0xFFF2F7F5) else Color(0xFF07211C)),
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(skin.lensHigh, skin.lensLow)))
                    .border(1.dp, skin.rim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { BasicTextCompat("Videos", metaStyle.copy(color = skin.inkQuiet)) }
        }
    }
}

/**
 * The volume, in the one place it belongs.
 *
 * Storage shown as material at rest: the level is how full the device is, the
 * meniscus is the boundary, and the figure sits in the air above it rather
 * than on a card. Nothing here is a chart — the quantity IS the picture.
 */
@Composable
fun VolumeSpecimen(skin: Skin) {
    Box(Modifier.fillMaxSize()) {
        Volume(skin, fullness = 0.62f, modifier = Modifier.fillMaxSize())
        Column(Modifier.padding(22.dp)) {
            BasicTextCompat(
                "INTERNAL STORAGE",
                TextStyle(
                    color = skin.inkQuiet, fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp,
                ),
            )
            Box(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextCompat(
                    "38.4",
                    TextStyle(color = skin.ink, fontSize = 44.sp, fontWeight = FontWeight.Medium),
                )
                Box(Modifier.width(6.dp))
                Box(Modifier.padding(bottom = 7.dp)) {
                    BasicTextCompat(
                        "GB free of 256 GB",
                        TextStyle(color = skin.inkQuiet, fontSize = 14.sp),
                    )
                }
            }
        }
    }
}
