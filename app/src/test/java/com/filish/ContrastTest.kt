package com.filish

import androidx.compose.ui.graphics.Color
import com.filish.design.DarkPalette
import com.filish.design.LightPalette
import com.filish.design.Palette
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contrast, measured rather than eyeballed.
 *
 * Custom palettes fail here quietly. A secondary grey that looks "nicely
 * recessive" on a designer's bright monitor is frequently unreadable in
 * sunlight, and nothing in the build catches it - the app compiles, the
 * screenshots look fine, and the text is simply illegible for a portion of
 * users.
 *
 * So every ink-on-ground pair FILISH actually uses is asserted against WCAG
 * 2.1 AA here: 4.5:1 for body text, 3:1 for large text and for non-text
 * elements that carry meaning (the selection marker, gauges, the Conduit).
 *
 * These are the palette's real constraints. If a colour is changed for
 * aesthetic reasons and this fails, the aesthetic change was wrong.
 */
class ContrastTest {

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun ratio(fg: Color, bg: Color): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertAtLeast(minimum: Double, fg: Color, bg: Color, what: String) {
        val r = ratio(fg, bg)
        val rounded = Math.round(r * 100) / 100.0
        if (r < minimum) {
            throw AssertionError("$what has contrast $rounded:1, needs $minimum:1")
        }
    }

    private fun auditBodyText(p: Palette, theme: String) {
        // Text a user is expected to read, on every ground it appears on.
        for ((groundName, ground) in listOf(
            "ground0" to p.ground0, "ground1" to p.ground1,
            "ground2" to p.ground2, "raised" to p.raised,
        )) {
            assertAtLeast(4.5, p.ink0, ground, "$theme ink0 on $groundName")
            assertAtLeast(4.5, p.ink1, ground, "$theme ink1 on $groundName")
            // ink2 carries file sizes, dates and counts - metadata is not
            // decoration in a file manager, it is the content.
            assertAtLeast(4.5, p.ink2, ground, "$theme ink2 on $groundName")
        }
    }

    private fun auditSemantic(p: Palette, theme: String) {
        assertAtLeast(4.5, p.danger, p.ground0, "$theme danger on ground0")
        assertAtLeast(4.5, p.danger, p.raised, "$theme danger on raised")
        assertAtLeast(4.5, p.warn, p.ground0, "$theme warn on ground0")
        assertAtLeast(4.5, p.ok, p.ground0, "$theme ok on ground0")
        assertAtLeast(4.5, p.signal, p.ground0, "$theme signal on ground0")
        assertAtLeast(4.5, p.signal, p.raised, "$theme signal on raised")

        // Washes exist to carry text of their own colour.
        assertAtLeast(4.5, p.danger, p.dangerWash, "$theme danger on its wash")
        assertAtLeast(4.5, p.ink0, p.signalWash, "$theme ink0 on signal wash")
        assertAtLeast(4.5, p.ink1, p.warnWash, "$theme ink1 on warn wash")
    }

    private fun auditSelection(p: Palette, theme: String) {
        // Selection inverts the ground, so the inverted ink must clear the bar
        // on the inverted ground too.
        assertAtLeast(4.5, p.selectInk, p.selectGround, "$theme selected ink")
        assertAtLeast(
            4.5, p.selectInk.copy(alpha = 1f), p.selectGround,
            "$theme selected secondary ink",
        )
        // The marker is a non-text element that carries meaning, so 3:1.
        assertAtLeast(3.0, p.selectMark, p.selectGround, "$theme selection marker")
        // And the selected row must be distinguishable from an unselected one.
        assertAtLeast(3.0, p.selectGround, p.ground0, "$theme selected row against the list")
    }

    private fun auditStructure(p: Palette, theme: String) {
        // Text drawn on the signal colour - primary actions.
        assertAtLeast(4.5, p.inkOn, p.signal, "$theme ink on signal")
        assertAtLeast(4.5, p.inkOn, p.danger, "$theme ink on danger")
        // Gauges, meters and the Conduit: meaningful non-text.
        assertAtLeast(3.0, p.signal, p.ground2, "$theme signal against its track")
        assertAtLeast(3.0, p.warn, p.ground2, "$theme warn against its track")
        assertAtLeast(3.0, p.danger, p.ground2, "$theme danger against its track")
    }

    @Test
    fun `light palette meets AA everywhere text is read`() {
        auditBodyText(LightPalette, "light")
    }

    @Test
    fun `dark palette meets AA everywhere text is read`() {
        auditBodyText(DarkPalette, "dark")
    }

    @Test
    fun `semantic colours are legible in both themes`() {
        auditSemantic(LightPalette, "light")
        auditSemantic(DarkPalette, "dark")
    }

    @Test
    fun `selection is legible and distinguishable in both themes`() {
        auditSelection(LightPalette, "light")
        auditSelection(DarkPalette, "dark")
    }

    @Test
    fun `structural elements meet the non-text threshold`() {
        auditStructure(LightPalette, "light")
        auditStructure(DarkPalette, "dark")
    }

    @Test
    fun `category tints are distinguishable from the ground they sit on`() {
        // Category glyphs are non-text carriers of meaning.
        for ((theme, p) in listOf("light" to LightPalette, "dark" to DarkPalette)) {
            val tints = listOf(
                "image" to p.catImage, "video" to p.catVideo, "audio" to p.catAudio,
                "document" to p.catDocument, "archive" to p.catArchive, "app" to p.catApp,
                "code" to p.catCode, "other" to p.catOther, "folder" to p.catFolder,
            )
            for ((name, tint) in tints) {
                assertAtLeast(3.0, tint, p.ground0, "$theme $name tint on ground0")
            }
        }
    }
}
