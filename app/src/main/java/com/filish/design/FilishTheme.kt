package com.filish.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** How FILISH resolves light/dark. Exposed in Settings. */
enum class ThemeChoice { System, Light, Dark }

/**
 * Interface density. This is a real product control, not decoration: a file
 * manager is used both for glancing at one folder and for working through a
 * thousand files, and those want different amounts of air.
 */
enum class Density(val rowScale: Float, val label: String) {
    Comfortable(1.0f, "Comfortable"),
    Compact(0.84f, "Compact"),
    Dense(0.72f, "Dense"),
}

@Immutable
data class Accessibility(
    /** Replaces positional motion with cross-fades throughout. */
    val reduceMotion: Boolean = false,
    /** Raises hairline and secondary-ink contrast. */
    val highContrast: Boolean = false,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalType = staticCompositionLocalOf { FilishType(null) }
val LocalDensitySetting = compositionLocalOf { Density.Comfortable }
val LocalAccessibility = compositionLocalOf { Accessibility() }

/**
 * The single entry point for FILISH's visual language.
 *
 * Note what is absent: there is no MaterialTheme anywhere in this
 * application. FILISH uses Compose Foundation - layout, gestures, drawing -
 * and builds every visible component itself. Pulling in Material would have
 * meant inheriting its component shapes, its ripple, its elevation model and
 * its type ramp, and then fighting all four.
 */
@Composable
fun FilishTheme(
    themeChoice: ThemeChoice = ThemeChoice.System,
    density: Density = Density.Comfortable,
    accessibility: Accessibility = Accessibility(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeChoice) {
        ThemeChoice.System -> systemDark
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }

    val base = if (dark) DarkPalette else LightPalette
    val palette = remember(base, accessibility.highContrast) {
        if (!accessibility.highContrast) base else base.copy(
            // High contrast does not mean "more colour". It means the two
            // things that carry structure - secondary ink and hairlines -
            // stop being polite.
            ink1 = if (base.isDark) base.ink0 else base.ink0,
            ink2 = if (base.isDark) base.ink1 else base.ink1,
            line = base.lineStrong,
            lineStrong = if (base.isDark) base.ink2 else base.ink1,
        )
    }

    val assets = LocalContext.current.assets
    val type = remember(assets) { FilishType(ClashDisplay.family(assets)) }

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalType provides type,
        LocalDensitySetting provides density,
        LocalAccessibility provides accessibility,
        content = content,
    )
}

/** Shorthands. `Filish.palette.signal` reads better than a chain of locals. */
object Filish {
    val palette: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
    val type: FilishType
        @Composable @ReadOnlyComposable get() = LocalType.current
    val density: Density
        @Composable @ReadOnlyComposable get() = LocalDensitySetting.current
    val a11y: Accessibility
        @Composable @ReadOnlyComposable get() = LocalAccessibility.current
}

/** Applies a colour to a style without the caller restating the family. */
fun TextStyle.on(color: androidx.compose.ui.graphics.Color): TextStyle = copy(color = color)

internal val FallbackFamily: FontFamily = FontFamily.SansSerif
