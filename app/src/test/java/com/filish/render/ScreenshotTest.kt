package com.filish.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.filish.design.Accessibility
import com.filish.design.DarkPalette
import com.filish.design.Density
import com.filish.design.FilishTheme
import com.filish.design.LightPalette
import com.filish.design.ThemeChoice
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Renders FILISH's screens to PNG files on the JVM.
 *
 * This exists because an interface has to be *looked at*. Compiling proves
 * nothing about alignment, density, contrast, truncation, or whether a screen
 * reads as designed rather than as assembled. The build environment has no
 * KVM, so a hardware emulator is not an option; Robolectric in native
 * graphics mode runs the real Compose tree through the real Skia pipeline,
 * which is close enough to do genuine visual QA against.
 *
 * The composition is drawn straight to a bitmap through View.draw rather than
 * through captureToImage - the latter goes via PixelCopy on the window, which
 * Robolectric cannot drive.
 *
 * Output lands in build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
abstract class ScreenshotTest {

    protected fun shoot(
        name: String,
        dark: Boolean = false,
        reduceMotion: Boolean = false,
        highContrast: Boolean = false,
        density: Density = Density.Comfortable,
        widthDp: Int = 411,
        heightDp: Int = 891,
        content: @Composable () -> Unit,
    ) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
            .create().start().resume().visible()
        val activity = controller.get()

        val root = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                FilishTheme(
                    themeChoice = if (dark) ThemeChoice.Dark else ThemeChoice.Light,
                    density = density,
                    accessibility = Accessibility(
                        reduceMotion = reduceMotion,
                        highContrast = highContrast,
                    ),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(if (dark) DarkPalette.ground0 else LightPalette.ground0),
                    ) { content() }
                }
            }
        }
        activity.setContentView(root)

        val scale = activity.resources.displayMetrics.density
        val widthPx = (widthDp * scale).toInt()
        val heightPx = (heightDp * scale).toInt()

        // Let composition, measurement and any launched effects settle.
        repeat(12) { ShadowLooper.idleMainLooper() }

        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, widthPx, heightPx)
        repeat(12) { ShadowLooper.idleMainLooper() }
        // Measure again: effects that ran on the first pass frequently change
        // content height (a settling figure appearing, a list filling in).
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, widthPx, heightPx)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        println("SHOT build/screenshots/$name.png  ${bitmap.width}x${bitmap.height}")

        (root.parent as? ViewGroup)?.removeView(root)
        controller.pause().stop().destroy()
    }
}
