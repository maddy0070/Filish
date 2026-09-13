package com.filish

import com.filish.core.model.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Number formatting.
 *
 * Storage figures are the most consequential text in FILISH, so the rules
 * they follow are tested rather than assumed: decimal units so the app agrees
 * with the device's own settings screen, and roughly constant significant
 * digits so every figure carries about the same information.
 */
class FormatTest {

    @Test
    fun `units are decimal, matching what the device claims`() {
        // 1 GB = 1000 MB. Being pure about gibibytes would put FILISH in
        // disagreement with Android about how full the phone is.
        assertEquals("1 KB", Format.size(1_000))
        assertEquals("1 MB", Format.size(1_000_000))
        assertEquals("1 GB", Format.size(1_000_000_000))
    }

    @Test
    fun `bytes below a kilobyte are exact`() {
        assertEquals("0 B", Format.size(0))
        assertEquals("1 B", Format.size(1))
        assertEquals("999 B", Format.size(999))
    }

    @Test
    fun `significant digits stay roughly constant across magnitudes`() {
        assertEquals("9.87 GB", Format.size(9_870_000_000))
        assertEquals("98.7 GB", Format.size(98_700_000_000))
        assertEquals("987 GB", Format.size(987_000_000_000))
    }

    @Test
    fun `trailing zeros are trimmed rather than padded`() {
        // "1.00 GB" is noise; the extra digits claim precision that is not
        // being communicated.
        assertEquals("1.5 GB", Format.size(1_500_000_000))
        assertEquals("2 MB", Format.size(2_000_000))
    }

    @Test
    fun `value and unit are separable for the display face`() {
        val parts = Format.sizeParts(4_200_000_000)
        assertEquals("4.2", parts.value)
        assertEquals("GB", parts.unit)
    }

    @Test
    fun `a negative size is shown as unknown rather than as a number`() {
        assertEquals("-", Format.sizeParts(-1).value)
    }

    @Test
    fun `duration omits the hour when there is none`() {
        assertEquals("0:00", Format.duration(0))
        assertEquals("0:05", Format.duration(5_000))
        assertEquals("3:20", Format.duration(200_000))
        assertEquals("1:00:00", Format.duration(3_600_000))
        assertEquals("2:05:03", Format.duration(7_503_000))
    }

    @Test
    fun `pluralisation is handled by the formatter, not the caller`() {
        assertEquals("1 file", Format.plural(1, "file", "files"))
        assertEquals("0 files", Format.plural(0, "file", "files"))
        assertEquals("2 files", Format.plural(2, "file", "files"))
    }

    @Test
    fun `large counts are grouped so the eye can read them`() {
        assertEquals("1,234,567", Format.count(1_234_567))
    }

    @Test
    fun `relative time reads as the question the user is asking`() {
        val now = 1_700_000_000_000L
        val minute = 60_000L
        assertEquals("Just now", Format.relativeTime(now - 30_000, now))
        assertEquals("5 min ago", Format.relativeTime(now - 5 * minute, now))
        assertEquals("3 hr ago", Format.relativeTime(now - 180 * minute, now))
        assertEquals("Yesterday", Format.relativeTime(now - 24 * 60 * minute, now))
    }

    @Test
    fun `an unknown timestamp is never rendered as 1970`() {
        assertEquals("Unknown", Format.relativeTime(0, 1_700_000_000_000L))
        assertEquals("Unknown", Format.absoluteDate(0))
    }

    @Test
    fun `eta is coarse and admits when it is a guess`() {
        // A precise ETA that changes every frame is noise; one that claims
        // accuracy it does not have costs the user's trust permanently.
        assertEquals("", Format.eta(0))
        assertEquals("a moment", Format.eta(2_000))
        assertTrue(Format.eta(45_000).endsWith("sec"))
        assertTrue(Format.eta(600_000).endsWith("min"))
        assertEquals("over an hour", Format.eta(7_200_000))
    }

    @Test
    fun `exact bytes are grouped and singular at one`() {
        assertEquals("1 byte", Format.exactBytes(1))
        assertEquals("1,048,576 bytes", Format.exactBytes(1_048_576))
    }
}
