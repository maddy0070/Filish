package com.filish.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filish.design.component.BasicTextCompat

/**
 * A specimen sheet.
 *
 * Each visual direction is rendered as the same six things, so the comparison
 * is between the materials rather than between the layouts:
 *
 *   the ground · a file row · a selected row · a primary action ·
 *   a chip · the leading edge of a floating surface
 *
 * These are deliberately NOT screens. The question this answers is "what is
 * this made of", not "where do things go".
 */
@Composable
fun SpecimenSheet(
    name: String,
    note: String,
    labelInk: Color,
    ground: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ground(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicTextCompat(
                name.uppercase(),
                TextStyle(
                    color = labelInk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
                ),
            )
            BasicTextCompat(
                note,
                TextStyle(color = labelInk.copy(alpha = 0.72f), fontSize = 12.sp),
                Modifier.fillMaxWidth(),
            )
            content()
        }
    }
}

/** The four file names every specimen uses, so type is compared fairly. */
val SpecimenRows = listOf(
    Triple("DSC01847.ARW", "48.9 MB · RAW · 1 wk ago", false),
    Triple("Screenshots", "428 items · 2.14 GB", false),
    Triple("VID_20240918_final.mp4", "1.84 GB · MP4 · 2 days ago", true),
    Triple("Договор аренды.pdf", "2.14 MB · PDF · 4 wk ago", false),
)
