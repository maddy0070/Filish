package com.filish.design

import android.content.res.AssetManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * FILISH typography.
 *
 * The application is set in two faces, and the division between them is a
 * product decision rather than an aesthetic one.
 *
 * CLASH DISPLAY carries FILISH's voice: the wordmark, screen titles, section
 * headings, and - most importantly - every large number. Storage figures are
 * the most consequential text in a file manager; they are what the user is
 * actually here to understand. Clash Display is a display face with generous
 * counters and unambiguous figures, and at the sizes FILISH uses it for
 * numbers it is markedly easier to read at a glance than a UI sans.
 *
 * THE SYSTEM SANS carries the user's data: file names, paths, metadata, dense
 * controls. This is not a compromise, it is the correct call. File names are
 * arbitrary strings in unknown scripts - Cyrillic, CJK, Arabic, emoji - and
 * Clash Display is a Latin face. Setting file names in it would mean silent,
 * inconsistent fallback on exactly the content the user cares most about,
 * with a name in one script rendering in a visibly different face from the
 * name above it. The system face has the platform's full fallback chain
 * behind it and stays even. Chrome is ours; content is the user's.
 *
 * Clash Display is fetched at build time (see app/build.gradle.kts). If it is
 * absent - an offline build, or a fetch failure - [FilishType] degrades to a
 * tuned system stack rather than failing. The weights and tracking below are
 * chosen so that degradation is a loss of character, never of legibility.
 */
object ClashDisplay {

    private const val ASSET = "fonts/ClashDisplay-Variable.ttf"

    /**
     * Builds the family, or returns null when the asset was not bundled.
     * Resolved once at theme construction, never per-composition.
     */
    fun family(assets: AssetManager): FontFamily? {
        val present = runCatching {
            assets.list("fonts")?.any { it == "ClashDisplay-Variable.ttf" } == true
        }.getOrDefault(false)
        if (!present) return null

        // A variable font: one file, every weight. Declaring the axis per
        // weight lets Compose pick real interpolated instances instead of
        // synthesising a fake bold.
        fun at(weight: Int) = Font(
            path = ASSET,
            assetManager = assets,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )

        return runCatching {
            FontFamily(at(300), at(400), at(500), at(600), at(700))
        }.getOrNull()
    }
}

/**
 * The resolved type scale.
 *
 * Sizes are not a geometric ramp. Each step exists because a specific piece of
 * information needed to sit at a specific level of the hierarchy, and steps
 * that turned out to do the same job as their neighbour were removed.
 */
@Immutable
class FilishType(displayFamily: FontFamily?) {

    private val display: FontFamily = displayFamily ?: FontFamily.SansSerif
    private val ui: FontFamily = FontFamily.SansSerif

    /** True when the intended face is actually present. Surfaced in Settings
     *  so the state is inspectable rather than mysterious. */
    val displayFaceAvailable: Boolean = displayFamily != null

    private val trim = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    // ---- Voice: Clash Display ----------------------------------------------

    /** The wordmark. Used once, on the opening. */
    val wordmark = TextStyle(
        fontFamily = display, fontWeight = FontWeight(600), fontSize = 34.sp,
        letterSpacing = (-0.02).em, lineHeight = 38.sp, lineHeightStyle = trim,
    )

    /** The single largest number on a screen - total storage, selection total. */
    val figureHuge = TextStyle(
        fontFamily = display, fontWeight = FontWeight(500), fontSize = 44.sp,
        letterSpacing = (-0.025).em, lineHeight = 46.sp, lineHeightStyle = trim,
    )

    /** A number that leads a section - a category total, a result count. */
    val figureLarge = TextStyle(
        fontFamily = display, fontWeight = FontWeight(500), fontSize = 27.sp,
        letterSpacing = (-0.02).em, lineHeight = 30.sp, lineHeightStyle = trim,
    )

    /** A number inline with text, where it still needs to dominate. */
    val figure = TextStyle(
        fontFamily = display, fontWeight = FontWeight(500), fontSize = 17.sp,
        letterSpacing = (-0.01).em, lineHeight = 20.sp, lineHeightStyle = trim,
    )

    /** Screen title. */
    val title = TextStyle(
        fontFamily = display, fontWeight = FontWeight(600), fontSize = 24.sp,
        letterSpacing = (-0.02).em, lineHeight = 27.sp, lineHeightStyle = trim,
    )

    /** Section heading inside a screen. */
    val heading = TextStyle(
        fontFamily = display, fontWeight = FontWeight(600), fontSize = 17.sp,
        letterSpacing = (-0.01).em, lineHeight = 21.sp, lineHeightStyle = trim,
    )

    // ---- Content: the system face ------------------------------------------

    /** A file or folder name in a list. The most-read text in the app. */
    val name = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.Medium, fontSize = 15.sp,
        letterSpacing = (-0.006).em, lineHeight = 19.sp, lineHeightStyle = trim,
    )

    /** A name in a grid cell, where width is scarce. */
    val nameTight = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.Medium, fontSize = 12.5.sp,
        letterSpacing = 0.sp, lineHeight = 15.sp, lineHeightStyle = trim,
    )

    /** Size, date, count - the line beneath a name. */
    val meta = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.Normal, fontSize = 12.5.sp,
        letterSpacing = 0.005.em, lineHeight = 16.sp, lineHeightStyle = trim,
    )

    /** Metadata that has been promoted - the sort key currently in effect,
     *  a value that changed, a figure that matters in context. */
    val metaStrong = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
        letterSpacing = 0.005.em, lineHeight = 16.sp, lineHeightStyle = trim,
    )

    /** Action text. */
    val action = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
        letterSpacing = 0.01.em, lineHeight = 17.sp, lineHeightStyle = trim,
    )

    /** Eyebrow label above a group. Set in caps at this size only. */
    val eyebrow = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp,
        letterSpacing = 0.1.em, lineHeight = 14.sp, lineHeightStyle = trim,
    )

    /** Running text: explanations, errors, empty states, document text. */
    val body = TextStyle(
        fontFamily = ui, fontWeight = FontWeight.Normal, fontSize = 14.5.sp,
        letterSpacing = 0.sp, lineHeight = 21.sp, lineHeightStyle = trim,
    )

    /** Paths, hashes, MIME types, extensions - anything where character
     *  identity matters more than reading speed. */
    val technical = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp,
        letterSpacing = (-0.01).em, lineHeight = 17.sp, lineHeightStyle = trim,
    )
}
