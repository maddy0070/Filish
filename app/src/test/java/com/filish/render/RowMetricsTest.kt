package com.filish.render

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import com.filish.design.Accessibility
import com.filish.design.FilishTheme
import com.filish.design.ThemeChoice
import com.filish.design.spine.Spine
import com.filish.feature.browse.FileRow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Row height, measured rather than assumed.
 *
 * The hard blocker for V4 was that the prototypes used a fixed row height. A
 * fixed height is the single most common way an interface breaks for users who
 * have turned their font up: the text grows, the box does not, and the
 * metadata line is silently clipped off the bottom - which in a file manager
 * means the size and date disappear.
 *
 * So the production row specifies no height at all. These tests compose real
 * rows through the real layout system at three font scales and assert the two
 * properties that matter:
 *
 *   1. never shorter than the touch floor
 *   2. always TALLER when the type is larger - which is the proof that the
 *      height is content-driven rather than clamped
 *
 * Font scale is injected through LocalDensity rather than through a
 * Robolectric qualifier because that is the same path the platform uses, and
 * it is what Compose actually reads.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class RowMetricsTest {

    private fun measureRowsDp(
        fontScale: Float,
        dark: Boolean = true,
        content: @Composable () -> Unit,
    ): Float {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
            .create().start().resume().visible()
        val activity = controller.get()
        val root = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                FilishTheme(
                    themeChoice = if (dark) ThemeChoice.Dark else ThemeChoice.Light,
                    accessibility = Accessibility(),
                ) {
                    val base = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(base.density, fontScale),
                    ) {
                        Column(Modifier.fillMaxWidth()) { content() }
                    }
                }
            }
        }
        activity.setContentView(root)
        val scale = activity.resources.displayMetrics.density
        val widthPx = (411 * scale).toInt()

        repeat(8) { ShadowLooper.idleMainLooper() }
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        repeat(8) { ShadowLooper.idleMainLooper() }
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val heightDp = root.measuredHeight / scale
        controller.pause().stop().destroy()
        return heightDp
    }

    @Composable
    private fun Row(node: com.filish.core.model.FileNode) {
        FileRow(
            node = node,
            selected = false,
            selectionActive = false,
            facts = null,
            showThumbnails = false,
            showExtensions = true,
            density = 1f,
            onClick = {}, onLongClick = {},
            massScale = 4_000_000_000L,
        )
    }

    private val short = Fixtures.node("a.txt", 1_200)
    private val long = Fixtures.node(
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.pdf",
        4_200_000,
    )

    @Test
    fun `a row is never shorter than the touch floor`() {
        for (scale in listOf(1.0f, 1.3f, 2.0f)) {
            val h = measureRowsDp(scale) { Row(short) }
            assertTrue(
                "a row at font scale $scale measured ${h}dp, below the ${Spine.minHeight.value}dp floor",
                h >= Spine.minHeight.value - 0.5f,
            )
        }
    }

    @Test
    fun `a row grows with the font scale instead of clipping`() {
        val normal = measureRowsDp(1.0f) { Row(short) }
        val large = measureRowsDp(1.3f) { Row(short) }
        val huge = measureRowsDp(2.0f) { Row(short) }
        assertTrue("1.3x did not grow the row ($normal -> $large)", large > normal)
        assertTrue("2.0x did not grow the row ($large -> $huge)", huge > large)
    }

    /**
     * A name long enough to wrap must make the row taller, not disappear.
     * Real filesystems are full of these.
     */
    @Test
    fun `a wrapping name makes the row taller`() {
        val one = measureRowsDp(1.0f) { Row(short) }
        val two = measureRowsDp(1.0f) { Row(long) }
        assertTrue("a two-line name did not grow the row ($one -> $two)", two > one)
    }

    /**
     * Ten rows must be exactly ten rows tall. If a row were laid out with a
     * fixed height this would still pass; what it actually guards is that no
     * row silently gains a margin, divider or container that would break the
     * continuous body a selection run depends on.
     */
    @Test
    fun `rows abut with no gap between them`() {
        val one = measureRowsDp(1.0f) { Row(short) }
        val ten = measureRowsDp(1.0f) { repeat(10) { Row(short) } }
        val drift = kotlin.math.abs(ten - one * 10)
        assertTrue("ten rows drifted ${drift}dp from ten times one row", drift < 1.5f)
    }
}
