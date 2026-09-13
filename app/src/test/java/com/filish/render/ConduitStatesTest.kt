package com.filish.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Conduit
import com.filish.design.component.Gap
import com.filish.design.component.SectionLabel
import org.junit.Test

/**
 * The Conduit across its whole range.
 *
 * Progress components are almost always reviewed at one flattering value.
 * The interesting failures are at the ends - a transfer that has barely
 * started, one nearly finished where the travelling segments have almost no
 * room left, and a stalled one that has to look different from a finished one.
 */
class ConduitStatesTest : ScreenshotTest() {

    @Test
    fun states() {
        shoot("30-conduit-states", heightDp = 520) {
            Column(
                Modifier.fillMaxSize().background(Filish.palette.ground0).padding(20.dp),
            ) {
                listOf(
                    "Just started" to 0.02f,
                    "A quarter through" to 0.25f,
                    "Half" to 0.5f,
                    "Nearly done" to 0.93f,
                    "Complete" to 1f,
                ).forEach { (label, fraction) ->
                    SectionLabel(label)
                    Conduit(fraction = fraction, bytesPerSecond = 90_000_000)
                    Gap(10.dp)
                }
                SectionLabel("Stalled")
                Conduit(fraction = 0.4f, bytesPerSecond = 0, stalled = true)
            }
        }
    }
}
