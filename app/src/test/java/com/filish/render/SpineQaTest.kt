package com.filish.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filish.core.model.FileNode
import com.filish.core.model.MeasuredSize
import com.filish.design.Filish
import com.filish.design.component.BasicTextCompat
import com.filish.design.glass.Mass
import com.filish.design.glass.environment
import com.filish.design.spine.Spine
import com.filish.feature.browse.Departure
import com.filish.feature.browse.FileRow
import com.filish.feature.browse.RowFacts
import org.junit.Test

/**
 * Visual QA for the production browse list.
 *
 * Unit tests prove the row is never too short and the mark never inverts. They
 * cannot tell you whether the screen looks authored, whether the spine reads as
 * a landscape, or whether a directory of ugly real filenames still looks
 * intentional. These renders are for looking at.
 *
 * They compose the PRODUCTION [FileRow], so they fail the moment the row stops
 * meaning what the design record says it means.
 */
class SpineQaTest : ScreenshotTest() {

    // A hostile, realistic directory. Ugly names, extreme size spread, folders
    // in three measurement states, and a name long enough to wrap.
    private val ugly: List<FileNode> = listOf(
        Fixtures.node("DCIM", dir = true),
        Fixtures.node("WhatsApp Media", dir = true),
        Fixtures.node("Screen recordings", dir = true),
        Fixtures.node("IMG_20260914_173829.RAW", 94_300_000),
        Fixtures.node("Screenshot_2026-09-14-23-41-02.png", 1_840_000),
        Fixtures.node("WhatsApp Image 2024-11-02 at 19.44.07 (1).jpeg", 184_000),
        Fixtures.node("document (47).pdf", 2_440_000),
        Fixtures.node("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.pdf", 640_000),
        Fixtures.node(".final.final2.REALLYFINAL.docx", 92_000),
        Fixtures.node("VID_20230817_190233.mp4", 2_940_000_000),
        Fixtures.node("Договор аренды 2024.pdf", 2_140_000),
        Fixtures.node("会議メモ.txt", 8_400),
        Fixtures.node("report — final (copy) [2].xlsx", 41_000),
        Fixtures.node("no-extension", 12),
        Fixtures.node("..oddly.named..", 0),
        Fixtures.node("backup-2023-11-04.tar.gz", 3_410_000_000),
        Fixtures.node("podcast-episode-214-the-long-one.mp3", 96_500_000),
        Fixtures.node("app-release-v2.8.1-production.apk", 84_200_000),
        Fixtures.node("notes.md", 1_200),
    )

    private val facts = mapOf(
        ugly[0].path to RowFacts(MeasuredSize(6_240_000_000, 4_180, 22, settled = true), 4180),
        ugly[1].path to RowFacts(MeasuredSize(880_000_000, 2_104, 6, settled = true), 2104),
        // Still walking: provisional mark, lit, and it says so in words.
        ugly[2].path to RowFacts(MeasuredSize(1_900_000_000, 12, 0, settled = false), 18),
    )

    private fun scaleOf(items: List<FileNode>) = Mass.scaleFor(
        items.map { if (it.isDirectory) facts[it.path]?.measured?.bytes ?: 0L else it.size },
    )

    @Composable
    private fun Spine(
        items: List<FileNode>,
        selected: Set<String> = emptySet(),
        fontScale: Float = 1f,
        label: String,
        departing: Map<String, Departure> = emptyMap(),
    ) {
        val palette = Filish.palette
        val scale = scaleOf(items)
        Box(Modifier.fillMaxSize().environment(palette.isDark)) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(start = Spine.gutter, top = 18.dp, bottom = 16.dp)) {
                        BasicTextCompat(
                            label.uppercase(),
                            androidx.compose.ui.text.TextStyle(
                                color = palette.ink2, fontSize = 10.sp, letterSpacing = 2.sp,
                            ),
                        )
                    }
                    items.forEach { node ->
                        FileRow(
                            node = node,
                            selected = node.path in selected,
                            selectionActive = selected.isNotEmpty(),
                            facts = facts[node.path],
                            showThumbnails = false,
                            showExtensions = true,
                            density = 1f,
                            onClick = {}, onLongClick = {},
                            massScale = scale,
                            departing = departing[node.path],
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    /** The resting state. No bodies anywhere - substrate, mark, ink. */
    @Test
    fun restingNight() = shoot("v4-browse-night", dark = true, heightDp = 1180) {
        Spine(ugly, label = "at rest · night")
    }

    @Test
    fun restingDay() = shoot("v4-browse-day", heightDp = 1180) {
        Spine(ugly, label = "at rest · day")
    }

    /**
     * A contiguous run selected. The bodies must merge into one mass with the
     * unselected rows cut out of it, and each selected mark must keep both its
     * width and its hue.
     */
    @Test
    fun selectionRun() = shoot("v4-selection-run", dark = true, heightDp = 1180) {
        Spine(
            ugly,
            selected = setOf(ugly[3].path, ugly[4].path, ugly[5].path, ugly[6].path, ugly[9].path),
            label = "a run selected · night",
        )
    }

    /** The accessibility blocker: the row must grow, never clip. */
    @Test
    fun largeFont() = shoot("v4-large-font", dark = true, heightDp = 1400) {
        Spine(ugly.take(10), fontScale = 1.6f, label = "font scale 1.6")
    }

    @Test
    fun veryLargeFont() = shoot("v4-very-large-font", dark = true, heightDp = 1400) {
        Spine(ugly.take(7), fontScale = 2.0f, label = "font scale 2.0")
    }

    /** An empty folder: substrate and typography, no card, and no spine at all. */
    @Test
    fun emptyFolder() = shoot("v4-empty", dark = true, heightDp = 420) {
        Spine(emptyList(), label = "nothing here")
    }

    /** Departure, preserved from V2: to trash withdraws, destroyed collapses. */
    @Test
    fun departing() = shoot("v4-departing", dark = true, heightDp = 800) {
        Spine(
            ugly.take(8),
            label = "departing",
            departing = mapOf(ugly[4].path to Departure.ToTrash),
        )
    }
}
