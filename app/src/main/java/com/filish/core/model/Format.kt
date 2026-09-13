package com.filish.core.model

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * How FILISH writes numbers.
 *
 * Storage figures are the most consequential text in this application, so the
 * rules are stricter than "call Formatter.formatFileSize and move on".
 *
 * PRECISION FOLLOWS MAGNITUDE. "1.4 GB" and "1.41 GB" differ by 10 MB, which
 * is a real amount of storage; "947 KB" and "947.3 KB" differ by nothing the
 * user will ever act on. Significant digits are therefore held roughly
 * constant instead of decimal places, so every figure carries about the same
 * amount of information regardless of size.
 *
 * UNITS ARE DECIMAL. 1 GB = 1000 MB, matching what the device's own settings
 * screen and the storage label on the box say. Being technically pure about
 * gibibytes would make FILISH disagree with Android about how full the phone
 * is, and the user would be right to trust Android.
 *
 * VALUE AND UNIT ARE SEPARABLE, because the display face sets the number
 * large and the unit small beside it, and that is only possible if the
 * formatter hands back the two parts rather than a string.
 */
object Format {

    data class Sized(val value: String, val unit: String) {
        override fun toString() = "$value $unit"
    }

    private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun sizeParts(bytes: Long): Sized {
        if (bytes < 0) return Sized("-", "")
        if (bytes < 1000) return Sized(bytes.toString(), "B")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1000 && unit < units.lastIndex) {
            value /= 1000.0
            unit++
        }
        // Hold ~3 significant digits: 9.87 GB, 98.7 GB, 987 GB.
        val text = when {
            value >= 100 -> String.format(Locale.US, "%.0f", value)
            value >= 10 -> String.format(Locale.US, "%.1f", value)
            else -> String.format(Locale.US, "%.2f", value)
        }
        return Sized(text.trimTrailingZeros(), units[unit])
    }

    fun size(bytes: Long): String = sizeParts(bytes).toString()

    private fun String.trimTrailingZeros(): String =
        if (!contains('.')) this else trimEnd('0').trimEnd('.')

    /** Exact byte count, for properties. Grouped so the eye can read it. */
    fun exactBytes(bytes: Long): String =
        String.format(Locale.US, "%,d", bytes) + if (bytes == 1L) " byte" else " bytes"

    fun count(n: Int): String = String.format(Locale.US, "%,d", n)
    fun count(n: Long): String = String.format(Locale.US, "%,d", n)

    /** "3 files", "1 file" - pluralisation the UI should never hand-roll. */
    fun plural(n: Int, one: String, many: String): String =
        "${count(n)} ${if (n == 1) one else many}"

    fun duration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    /**
     * Relative time, because "when did I last touch this" is almost always
     * the actual question, and an absolute timestamp makes the reader do
     * arithmetic to answer it. Falls back to a date once relative time stops
     * being more informative than the date itself.
     */
    fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0) return "Unknown"
        val delta = now - epochMs
        if (delta < 0) return absoluteDate(epochMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hr ago"
            days < 7 -> if (days == 1L) "Yesterday" else "$days days ago"
            days < 30 -> "${days / 7} wk ago"
            days < 365 -> absoluteDate(epochMs, withYear = false)
            else -> absoluteDate(epochMs)
        }
    }

    fun absoluteDate(epochMs: Long, withYear: Boolean = true): String {
        if (epochMs <= 0) return "Unknown"
        val pattern = if (withYear) "d MMM yyyy" else "d MMM"
        return android.text.format.DateFormat.format(pattern, epochMs).toString()
    }

    fun absoluteDateTime(epochMs: Long): String {
        if (epochMs <= 0) return "Unknown"
        return android.text.format.DateFormat.format("d MMM yyyy, HH:mm", epochMs).toString()
    }

    /** Bytes per second, for transfers. */
    fun throughput(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0) "-" else "${size(bytesPerSecond)}/s"

    /** Remaining time on an operation. Deliberately coarse: a precise ETA
     *  that changes every frame is noise, and an ETA that claims accuracy it
     *  does not have is a lie the user will remember. */
    fun eta(ms: Long): String = when {
        ms <= 0 -> ""
        ms < 5_000 -> "a moment"
        ms < 60_000 -> "${(ms / 1000 / 5 + 1) * 5} sec"
        ms < 3_600_000 -> "${ms / 60_000 + 1} min"
        else -> "over an hour"
    }
}
