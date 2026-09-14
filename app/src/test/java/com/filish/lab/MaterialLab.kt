package com.filish.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filish.design.DarkPalette
import com.filish.design.LightPalette
import com.filish.design.Palette
import com.filish.design.component.BasicTextCompat
import com.filish.design.glass.DaySkin
import com.filish.design.glass.Facet
import com.filish.design.glass.Material
import com.filish.design.glass.NightSkin
import com.filish.design.glass.Response
import com.filish.design.glass.Skin
import com.filish.design.glass.lens
import com.filish.design.glass.substrate
import com.filish.design.glass.volume

/**
 * The lab, pointed at the PRODUCTION tokens.
 *
 * Directions.kt and Synthesis.kt explored; this proves. Everything rendered
 * here imports from com.filish.design.glass, so what comes out of it is what
 * the application will actually draw - if a token drifts, these pictures
 * change, which is the only way a design system stays true to itself once
 * people start editing numbers.
 *
 * Deliberately still NOT screens. The questions here are "what is this made
 * of", "what does it do when touched", and "is any of this doing nothing".
 */

private val nameStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val metaStyle = TextStyle(fontSize = 12.sp)
private val labelStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)

private fun tintsFor(p: Palette) = listOf(p.catImage, p.catFolder, p.catVideo, p.catDocument)

@Composable
private fun Caption(text: String, palette: Palette) {
    BasicTextCompat(text.uppercase(), labelStyle.copy(color = palette.ink2))
}

/** One object, made of the real material. */
@Composable
private fun Specimen(
    skin: Skin,
    palette: Palette,
    tier: Material.Tier,
    name: String,
    meta: String,
    band: Color?,
    state: Response = Response.Rest,
    shape: Shape = Facet.lens,
) {
    val selected = state.invertsGround
    val ink = if (selected) palette.selectInk else state.ink(palette, palette.ink0)
    val quiet = if (selected) palette.selectInk.copy(alpha = 0.74f) else state.ink(palette, palette.ink2)
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.clip(shape).background(palette.selectGround)
                } else {
                    Modifier.lens(
                        skin = skin,
                        tier = state.tier(tier),
                        shape = shape,
                        press = state.press(),
                        hover = state.hover(),
                        band = band?.let { state.band(it, palette) },
                        selected = false,
                        rimScale = state.rimMultiplier,
                    )
                },
            )
            .padding(start = Facet.band + Facet.bandGutter, end = 14.dp)
            .padding(vertical = 11.dp),
    ) {
        Column {
            BasicTextCompat(name, nameStyle.copy(color = ink), maxLines = 1)
            BasicTextCompat(meta, metaStyle.copy(color = quiet))
        }
    }
}

/**
 * The five tiers, on the one ground, in order.
 *
 * The thing to look for is whether the ladder is READABLE AS A LADDER without
 * the labels. A material system with five surface types that all look like
 * "some kind of glass" has no hierarchy, it just has texture.
 */
@Composable
fun TierLadder(skin: Skin, palette: Palette) {
    Box(Modifier.fillMaxSize().substrate(skin)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Caption(if (skin.isDark) "tiers · night" else "tiers · day", palette)
            BasicTextCompat(
                "Substrate is the ground. Resting is most of the interface. " +
                    "Lifted holds still while content moves under it. Modal " +
                    "occludes. Carried is in the hand.",
                metaStyle.copy(color = palette.ink1),
            )
            Box(Modifier.height(2.dp))

            Caption("substrate · no edge, no shadow, never a surface", palette)
            Specimen(skin, palette, Material.Tier.Substrate, "Nothing rests on nothing", "the ground itself", null)

            Caption("resting · rows, chips, tokens", palette)
            tintsFor(palette).take(2).forEachIndexed { i, tint ->
                Specimen(
                    skin, palette, Material.Tier.Resting,
                    SpecimenRows[i].first, SpecimenRows[i].second, tint,
                )
            }

            Caption("lifted · action bar, ledger, transfer", palette)
            Specimen(skin, palette, Material.Tier.Lifted, "4 selected", "6.21 GB · Trash · Copy · Move", null, shape = Facet.slab)

            Caption("modal · confirmations and sheets", palette)
            Specimen(skin, palette, Material.Tier.Modal, "Move 4 items to trash?", "Recoverable until 14 Oct", null, shape = Facet.slab)

            Caption("carried · in the hand", palette)
            Specimen(skin, palette, Material.Tier.Carried, "VID_20240918_final.mp4", "1.84 GB", tintsFor(palette)[2])

            Caption("well · cut into the substrate, so symmetric", palette)
            Specimen(skin, palette, Material.Tier.Well, "Search this folder", "name, kind, size, age", null, shape = Facet.well)
        }
    }
}

/**
 * Every interaction state, side by side.
 *
 * The test is whether each one is identifiable with the colour drained out of
 * it. Press sinks, focus thickens, selection inverts, disabled dissolves - all
 * four are structural, so all four survive greyscale and sunlight.
 */
@Composable
fun StateBoard(skin: Skin, palette: Palette) {
    val tint = palette.catVideo
    Box(Modifier.fillMaxSize().substrate(skin)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Caption(if (skin.isDark) "states · night" else "states · day", palette)
            listOf(
                Response.Rest to "at rest",
                Response.Hover to "pointer near it",
                Response.Pressed to "pressed — it sinks",
                Response.Focused to "focused — band takes the signal, rim doubles",
                Response.Selected to "selected — the ground inverts",
                Response.Disabled to "disabled — it stops being an object",
                Response.Dragging to "dragging — carried, and it left a well",
            ).forEach { (state, note) ->
                Caption(note, palette)
                Specimen(
                    skin, palette, Material.Tier.Resting,
                    "VID_20240918_final.mp4", "1.84 GB · MP4 · 2 days ago", tint, state,
                )
            }
            Box(Modifier.height(4.dp))
            Caption("the hole a dragged object leaves", palette)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .lens(skin, Material.Tier.Well, Facet.well),
            )
        }
    }
}

/** The volume, where storage is the subject. Nothing else gets a level. */
@Composable
fun VolumeBoard(skin: Skin, palette: Palette) {
    Box(Modifier.fillMaxSize().volume(skin, fullness = 0.62f)) {
        Column(Modifier.padding(22.dp)) {
            Caption("internal storage", palette)
            Box(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextCompat(
                    "38.4",
                    TextStyle(color = palette.ink0, fontSize = 44.sp, fontWeight = FontWeight.Medium),
                )
                Box(Modifier.width(6.dp))
                Box(Modifier.padding(bottom = 7.dp)) {
                    // ink1, not ink2: a tinted ground takes one step up the
                    // ramp. Measured in GlassContrastTest, not assumed.
                    BasicTextCompat("GB free of 256 GB", TextStyle(color = palette.ink1, fontSize = 14.sp))
                }
            }
        }
    }
}

/**
 * IS THIS JUST A CARD LIST?
 *
 * The honest risk with any material that gives objects an edge and a radius is
 * that a directory turns into a stack of white cards on grey, which is both
 * the most common interface in the world and the one thing V3 was explicitly
 * not allowed to become.
 *
 * The rendered answer is that it depends almost entirely on the GAP, not on
 * the material. At card spacing the rows are cards. At list spacing the same
 * tokens read as one continuous body of material with cuts in it, and the
 * bands line up into a column that carries the folder's composition at a
 * glance - which is a thing a card list cannot do at all.
 *
 * So the material is not what keeps FILISH from looking generic. The density
 * is. That is a screen-level rule, and this is the evidence for it.
 */
@Composable
fun DensityBoard(skin: Skin, palette: Palette) {
    val tints = tintsFor(palette) + tintsFor(palette)
    Box(Modifier.fillMaxSize().substrate(skin)) {
        Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Caption("card spacing", palette)
                repeat(6) { i ->
                    Specimen(
                        skin, palette, Material.Tier.Resting,
                        SpecimenRows[i % 4].first, SpecimenRows[i % 4].second, tints[i],
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Caption("list spacing", palette)
                repeat(6) { i ->
                    Specimen(
                        skin, palette, Material.Tier.Resting,
                        SpecimenRows[i % 4].first, SpecimenRows[i % 4].second, tints[i],
                    )
                }
            }
        }
    }
}

/**
 * THE REMOVE-30% TEST.
 *
 * Left: the full material. Right: the same tokens with the contact shadow, the
 * body gradient and the band gradient removed - three of the six effects in
 * the lens, so a little over a third of what it does.
 *
 * The question is not "which is prettier". It is "does the removed third do
 * any work". Anything that survives this is in the system because it is
 * load-bearing; anything that does not is decoration that was hiding behind a
 * rationale.
 */
@Composable
fun StripTest(skin: Skin, palette: Palette) {
    Box(Modifier.fillMaxSize().substrate(skin)) {
        Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Caption("full", palette)
                tintsFor(palette).forEachIndexed { i, tint ->
                    Specimen(
                        skin, palette, Material.Tier.Resting,
                        SpecimenRows[i].first, SpecimenRows[i].second, tint,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Caption("−30%", palette)
                tintsFor(palette).forEachIndexed { i, tint ->
                    Flattened(skin, palette, SpecimenRows[i].first, SpecimenRows[i].second, tint)
                }
            }
        }
    }
}

/** The stripped lens: flat fill, flat band, no contact shadow, rim kept. */
@Composable
private fun Flattened(skin: Skin, palette: Palette, name: String, meta: String, tint: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .drawWithCache {
                val outline = Facet.lens.createOutline(size, layoutDirection, this)
                val body = skin.settled(Material.Tier.Resting)
                val rim = skin.rim(Material.Tier.Resting)
                val bandWidth = Facet.band.toPx()
                onDrawBehind {
                    drawOutline(outline, body)
                    drawRect(
                        color = tint,
                        topLeft = androidx.compose.ui.geometry.Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(bandWidth, size.height),
                    )
                    drawOutline(outline, rim, style = Stroke(Facet.rim.toPx()))
                }
            }
            .padding(start = Facet.band + Facet.bandGutter, end = 14.dp)
            .padding(vertical = 11.dp),
    ) {
        Column {
            BasicTextCompat(name, nameStyle.copy(color = palette.ink0), maxLines = 1)
            BasicTextCompat(meta, metaStyle.copy(color = palette.ink2))
        }
    }
}

val DayPalette: Palette get() = LightPalette
val NightPalette: Palette get() = DarkPalette
val Day: Skin get() = DaySkin
val Night: Skin get() = NightSkin
