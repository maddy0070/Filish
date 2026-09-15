package com.filish.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filish.core.model.FileNode
import com.filish.design.Filish
import com.filish.design.component.BasicTextCompat
import com.filish.design.glass.Mass
import com.filish.design.glass.environment
import com.filish.design.spine.Spine
import com.filish.feature.browse.FileRow
import org.junit.Test

/**
 * THE RELATIVE-SCALE QUESTION, rendered.
 *
 * Marks are scaled against the largest object in the CURRENT listing, so the
 * same file draws a different mark in different folders. That has been the
 * open design risk since the channel was designed, and it has been answered
 * only by reasoning so far.
 *
 * These renders put the identical 40 MB file in two directories - one of tiny
 * neighbours, one of huge ones - so the difference can be looked at instead of
 * argued about. The device dataset builds the same two folders
 * (`07-relative-a`, `08-relative-b`) so a tester sees exactly this on hardware.
 *
 * This is a JVM render. It shows what the pixels do; it cannot tell you what a
 * user concludes from them.
 */
class ScaleContextTest : ScreenshotTest() {

    private val subject = Fixtures.node("THE-SAME-FILE-40MB.bin", 40_000_000)

    private val tinyNeighbours = listOf(
        Fixtures.node("note_001.txt", 2_400),
        Fixtures.node("note_002.txt", 8_100),
        Fixtures.node("config.json", 1_200),
        Fixtures.node("draft.md", 15_600),
        subject,
        Fixtures.node("todo.txt", 940),
        Fixtures.node("log.txt", 62_000),
    )

    private val hugeNeighbours = listOf(
        Fixtures.node("huge_00.mp4", 6_800_000_000),
        Fixtures.node("huge_01.mp4", 4_100_000_000),
        Fixtures.node("huge_02.mp4", 3_200_000_000),
        Fixtures.node("huge_03.mp4", 2_400_000_000),
        subject,
        Fixtures.node("huge_04.mp4", 2_000_000_000),
        Fixtures.node("huge_05.mp4", 5_900_000_000),
    )

    @Composable
    private fun Listing(items: List<FileNode>, caption: String) {
        val palette = Filish.palette
        val scale = Mass.scaleFor(items.map { it.size })
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(start = Spine.gutter, top = 14.dp, bottom = 10.dp)) {
                BasicTextCompat(
                    caption.uppercase(),
                    TextStyle(color = palette.ink2, fontSize = 9.5.sp, letterSpacing = 1.6.sp),
                )
            }
            items.forEach { node ->
                FileRow(
                    node = node,
                    selected = false,
                    selectionActive = false,
                    facts = null,
                    showThumbnails = false,
                    showExtensions = true,
                    density = 1f,
                    onClick = {}, onLongClick = {},
                    massScale = scale,
                )
            }
        }
    }

    /**
     * The same file, twice. Its mark is near the ceiling among tiny files and
     * near the floor among huge ones - both times correctly, because the
     * question the mark answers is "what is taking up the room IN HERE".
     *
     * The figure in the metadata line reads 40.0 MB in both, which is the only
     * thing that keeps the difference honest.
     */
    @Test
    fun sameFileTwoContextsNight() = shoot("v41-relative-scale-night", dark = true, heightDp = 1000) {
        val palette = Filish.palette
        Box(Modifier.fillMaxSize().environment(palette.isDark)) {
            Column(Modifier.fillMaxSize()) {
                Listing(tinyNeighbours, "A · among tiny files · scale = 64 KB")
                Box(Modifier.height(20.dp))
                Listing(hugeNeighbours, "B · among huge files · scale = 8 GB")
                Box(Modifier.height(16.dp))
                Row(Modifier.padding(horizontal = Spine.gutter)) {
                    BasicTextCompat(
                        "THE-SAME-FILE-40MB.bin appears in both listings. Its mark is wide in " +
                            "A and narrow in B. Its figure reads 40.0 MB in both.",
                        TextStyle(color = Filish.palette.ink1, fontSize = 12.sp),
                    )
                }
            }
        }
    }

    @Test
    fun sameFileTwoContextsDay() = shoot("v41-relative-scale-day", heightDp = 1000) {
        val palette = Filish.palette
        Box(Modifier.fillMaxSize().environment(palette.isDark)) {
            Column(Modifier.fillMaxSize()) {
                Listing(tinyNeighbours, "A · among tiny files")
                Box(Modifier.height(20.dp))
                Listing(hugeNeighbours, "B · among huge files")
            }
        }
    }

    /**
     * §4B — a wall of small files. The failure mode to look for is every mark
     * collapsing to the floor, which would make the spine a flat stripe that
     * says nothing.
     */
    @Test
    fun manySmallFiles() = shoot("v41-small-files", dark = true, heightDp = 1000) {
        val palette = Filish.palette
        val items = List(16) { i ->
            Fixtures.node("note_%03d.txt".format(i), (400L + i * i * 900L))
        }
        Box(Modifier.fillMaxSize().environment(palette.isDark)) {
            Listing(items, "many small files · 400 B to 200 KB")
        }
    }

    /**
     * §4C — a few enormous files. The failure mode is the opposite: one 8 GB
     * file crushing everything else against the floor.
     */
    @Test
    fun fewHugeFiles() = shoot("v41-huge-files", dark = true, heightDp = 1000) {
        val palette = Filish.palette
        val items = listOf(
            Fixtures.node("backup-full.img", 8_000_000_000),
            Fixtures.node("archive-2024.tar", 6_400_000_000),
            Fixtures.node("footage-a.mov", 4_100_000_000),
            Fixtures.node("footage-b.mov", 2_000_000_000),
            Fixtures.node("export.mp4", 900_000_000),
            Fixtures.node("preview.mp4", 120_000_000),
            Fixtures.node("notes.txt", 4_000),
            Fixtures.node("manifest.json", 900),
        )
        Box(Modifier.fillMaxSize().environment(palette.isDark)) {
            Listing(items, "a few huge files · 900 B to 8 GB")
        }
    }
}
