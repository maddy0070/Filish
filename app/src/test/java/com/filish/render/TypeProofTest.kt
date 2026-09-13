package com.filish.render

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filish.design.Filish
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import org.junit.Test

/**
 * A side-by-side proof that the bundled face is genuinely being used.
 *
 * Two identical strings, one in FILISH's display style and one forced to the
 * system sans. If they render identically, the intended typeface is silently
 * falling back and the whole typographic argument is decoration.
 */
class TypeProofTest : ScreenshotTest() {

    @Test
    fun proof() {
        shoot("01-type-proof", heightDp = 420) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                BasicTextCompat(
                    "CLASH DISPLAY",
                    Filish.type.eyebrow.copy(color = Filish.palette.ink2),
                )
                BasicTextCompat(
                    "Filish 4.2 GB",
                    Filish.type.wordmark.copy(color = Filish.palette.ink0),
                )
                Gap(24.dp)
                BasicTextCompat(
                    "SYSTEM SANS",
                    Filish.type.eyebrow.copy(color = Filish.palette.ink2),
                )
                BasicTextCompat(
                    "Filish 4.2 GB",
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight(600),
                        fontSize = 34.sp,
                        color = Filish.palette.ink0,
                    ),
                )
            }
        }
    }
}
