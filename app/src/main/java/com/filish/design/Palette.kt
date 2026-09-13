package com.filish.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * FILISH colour.
 *
 * Two rules govern every colour in this application.
 *
 * RULE 1 - State is saturated, category is muted.
 * Saturated colour is reserved for things that are *happening*: an operation
 * running, an error, a destructive confirmation, focus. File categories are
 * identified primarily by their glyph and only secondarily by a low-chroma
 * tint, so a folder full of mixed content never turns into a fruit salad.
 *
 * RULE 2 - Selection is not a colour.
 * Conventional file managers tint selected rows blue. FILISH inverts the
 * row's ground and adds a marker on the leading edge instead. Selection
 * therefore survives greyscale, colour-blindness and glare, and it cannot be
 * confused with the "something is happening" colours of rule 1.
 *
 * The signal hue is verdigris rather than the usual system blue. It was
 * chosen for hue distance: it sits far from red (danger) and amber (warning),
 * so the three can never be mistaken for one another, and it is cool against
 * the warm paper ground, which makes it read as an instrument marking rather
 * than as decoration.
 *
 * Dark is designed, not inverted. The dark ground is a near-black with a
 * slight blue cast rather than #000 - pure black removes the tonal headroom
 * that separation depends on, and smears on OLED panels during scroll. The
 * signal and semantic hues are lifted in lightness and dropped in chroma,
 * because a colour that reads correctly on paper glares on a dark ground.
 */
@Immutable
data class Palette(
    /** Plane 0. The ground the application rests on. */
    val ground0: Color,
    /** Recessed ground: wells, grouped regions, input fields. */
    val ground1: Color,
    /** Rails, chips, and the tonal lift that replaces shadow in dark mode. */
    val ground2: Color,
    /** Transient surfaces that genuinely leave the ground (sheets, overlays). */
    val raised: Color,
    /** Hairlines. Never a "divider everywhere" - see Spatial. */
    val line: Color,
    /** Strong hairline, for a boundary that carries real meaning. */
    val lineStrong: Color,

    /** Primary text. */
    val ink0: Color,
    /** Supporting text. */
    val ink1: Color,
    /** Metadata, counts, units - present but recessive. */
    val ink2: Color,
    /** Text drawn on top of [signal] or on an inverted ground. */
    val inkOn: Color,

    /** Brand, primary action, progress, focus. */
    val signal: Color,
    /** A wash of signal for fills that must stay quiet. */
    val signalWash: Color,

    /** Selection ground - a tonal inversion, not a hue. */
    val selectGround: Color,
    /** Ink on a selected row. */
    val selectInk: Color,
    /** The leading-edge marker that makes selection colour-independent. */
    val selectMark: Color,

    val danger: Color,
    val dangerWash: Color,
    val warn: Color,
    val warnWash: Color,
    val ok: Color,
    val okWash: Color,

    /** Low-chroma category tints. Identity comes from the glyph first. */
    val catImage: Color,
    val catVideo: Color,
    val catAudio: Color,
    val catDocument: Color,
    val catArchive: Color,
    val catApp: Color,
    val catCode: Color,
    val catOther: Color,
    val catFolder: Color,

    val isDark: Boolean,
) {
    /** Scrim behind a raised surface. Heavier in light mode, where the ground
     *  is bright enough to compete with the surface on top of it. */
    val scrim: Color get() = if (isDark) Color(0x99000000) else Color(0x66201C16)
}

val LightPalette = Palette(
    ground0 = Color(0xFFFBF9F6),
    ground1 = Color(0xFFF4F1EC),
    ground2 = Color(0xFFEBE7E0),
    raised = Color(0xFFFFFDFA),
    line = Color(0xFFE3DED5),
    lineStrong = Color(0xFFCEC7BA),

    ink0 = Color(0xFF16161A),
    ink1 = Color(0xFF57544D),
    ink2 = Color(0xFF8A867E),
    inkOn = Color(0xFFFBF9F6),

    signal = Color(0xFF2E7D6E),
    signalWash = Color(0xFFDCEAE6),

    selectGround = Color(0xFF23231F),
    selectInk = Color(0xFFFAF8F4),
    selectMark = Color(0xFF3FA791),

    danger = Color(0xFFB3403A),
    dangerWash = Color(0xFFF6E2E0),
    warn = Color(0xFF9A6B12),
    warnWash = Color(0xFFF6EBD6),
    ok = Color(0xFF3F6B4C),
    okWash = Color(0xFFE0EBE1),

    catImage = Color(0xFFB4703F),
    catVideo = Color(0xFF6D5B9E),
    catAudio = Color(0xFF3F7A9E),
    catDocument = Color(0xFF4E7A4A),
    catArchive = Color(0xFF8A6A3B),
    catApp = Color(0xFF8E5A72),
    catCode = Color(0xFF566B8C),
    catOther = Color(0xFF7D7A74),
    catFolder = Color(0xFF57544D),

    isDark = false,
)

val DarkPalette = Palette(
    ground0 = Color(0xFF0E0E11),
    ground1 = Color(0xFF17171B),
    ground2 = Color(0xFF212127),
    raised = Color(0xFF1C1C21),
    line = Color(0xFF2C2C33),
    lineStrong = Color(0xFF3E3E47),

    ink0 = Color(0xFFF2F0EC),
    ink1 = Color(0xFFA8A49C),
    ink2 = Color(0xFF76736D),
    inkOn = Color(0xFF0E0E11),

    signal = Color(0xFF4FB3A0),
    signalWash = Color(0xFF16302C),

    // In dark mode inverting to "darker" is impossible, so selection inverts
    // upward instead: the selected row becomes the brightest thing in the list.
    selectGround = Color(0xFFE8E5DF),
    selectInk = Color(0xFF16161A),
    selectMark = Color(0xFF2E7D6E),

    danger = Color(0xFFE07068),
    dangerWash = Color(0xFF3A1D1C),
    warn = Color(0xFFDCAE55),
    warnWash = Color(0xFF332714),
    ok = Color(0xFF74AE85),
    okWash = Color(0xFF1A2C1F),

    catImage = Color(0xFFD08A5A),
    catVideo = Color(0xFF9A87C9),
    catAudio = Color(0xFF6BA6C7),
    catDocument = Color(0xFF7CA876),
    catArchive = Color(0xFFB59560),
    catApp = Color(0xFFBE879C),
    catCode = Color(0xFF8397B8),
    catOther = Color(0xFF9A968E),
    catFolder = Color(0xFFA8A49C),

    isDark = true,
)
