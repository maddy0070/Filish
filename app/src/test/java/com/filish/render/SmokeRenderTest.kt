package com.filish.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.component.BasicTextCompat
import org.junit.Test

/** Proves the rendering harness itself works before anything depends on it. */
class SmokeRenderTest : ScreenshotTest() {

    @Test
    fun smoke() {
        shoot("00-smoke") {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Filish.palette.ground0)
                    .padding(20.dp),
            ) {
                BasicTextCompat("Filish", Filish.type.wordmark.copy(color = Filish.palette.ink0))
                BasicTextCompat("4.2 GB", Filish.type.figureHuge.copy(color = Filish.palette.signal))
            }
        }
    }
}
