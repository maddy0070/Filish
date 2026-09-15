package com.filish.render

import com.filish.core.fs.Listing
import com.filish.core.fs.Volume
import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.Kinds
import com.filish.core.model.MeasuredSize
import com.filish.feature.browse.BrowseRow
import com.filish.feature.browse.BrowseState
import com.filish.feature.browse.Refinements
import com.filish.feature.browse.RowFacts
import com.filish.feature.browse.Selection
import java.util.concurrent.TimeUnit

/**
 * Realistic content for the screenshots.
 *
 * Deliberately not tidy. Demo data with six short names and round sizes makes
 * every layout look correct; real storage has 40-character WhatsApp
 * filenames, mixed scripts, wildly uneven sizes and dates spread over years,
 * and that is what actually exercises truncation, alignment and the figure
 * column.
 */
object Fixtures {

    // The real clock, so relative times render as they will in use.
    val now: Long = System.currentTimeMillis()
    private fun daysAgo(d: Long) = now - TimeUnit.DAYS.toMillis(d)

    fun node(
        name: String,
        size: Long = 0,
        dir: Boolean = false,
        modified: Long = daysAgo(3),
        hidden: Boolean = false,
        parent: String = "/storage/emulated/0/DCIM/Camera",
    ) = FileNode(
        path = "$parent/$name", name = name, isDirectory = dir, size = size,
        lastModified = modified, isHidden = hidden, kind = Kinds.of(name, dir),
        extension = if (dir) "" else Kinds.extensionOf(name),
        canRead = true, canWrite = true,
    )

    val entries: List<FileNode> = listOf(
        node("Screenshots", dir = true, modified = daysAgo(1)),
        node("Edited exports", dir = true, modified = daysAgo(40)),
        node("Raw", dir = true, modified = daysAgo(9)),
        node("VID_20240918_154233_exported_final.mp4", 1_842_000_000, modified = daysAgo(2)),
        node("IMG_20240917_092210.jpg", 4_720_000, modified = daysAgo(2)),
        node("IMG_20240917_092214.jpg", 5_180_000, modified = daysAgo(2)),
        node("DSC01847.ARW", 48_900_000, modified = daysAgo(11)),
        node("DSC01848.ARW", 49_300_000, modified = daysAgo(11)),
        node("Договор аренды 2024.pdf", 2_140_000, modified = daysAgo(28)),
        node("会議メモ.txt", 8_400, modified = daysAgo(120)),
        node("podcast-episode-214-the-long-one.mp3", 96_500_000, modified = daysAgo(430)),
        node("backup-2023-11-04.tar.gz", 3_410_000_000, modified = daysAgo(380)),
        node("app-release-v2.8.1-production.apk", 84_200_000, modified = daysAgo(64)),
        node("notes.md", 1_200, modified = daysAgo(0)),
    )

    val facts: Map<String, RowFacts> = mapOf(
        entries[0].path to RowFacts(MeasuredSize(2_140_000_000, 428, 0, settled = true), 428),
        entries[1].path to RowFacts(MeasuredSize(860_000_000, 91, 3, settled = true), 94),
        // Deliberately unsettled: this is the state the settling marker exists
        // for, and it has to be visible in a screenshot to be reviewable.
        entries[2].path to RowFacts(MeasuredSize(6_200_000_000, 1_204, 4, settled = false), 128),
    )

    fun rows(items: List<FileNode> = entries): List<BrowseRow> = items.map { BrowseRow.Item(it) }

    val volume = Volume(
        id = "/storage/emulated/0",
        label = "Internal storage",
        path = "/storage/emulated/0",
        isPrimary = true,
        isRemovable = false,
        isEmulated = true,
        totalBytes = 256_000_000_000,
        availableBytes = 38_400_000_000,
        readOnly = false,
    )

    val sdCard = Volume(
        id = "/storage/1A2B-3C4D",
        label = "SD card",
        path = "/storage/1A2B-3C4D",
        isPrimary = false,
        isRemovable = true,
        isEmulated = false,
        totalBytes = 128_000_000_000,
        availableBytes = 4_100_000_000,
        readOnly = false,
    )

    fun browseState(
        selection: Selection = Selection(),
        items: List<FileNode> = entries,
    ) = BrowseState(
        path = "/storage/emulated/0/DCIM/Camera",
        loading = false,
        listing = Listing.Content(items, unreadable = 0),
        rows = rows(items),
        rawCount = items.size,
        selection = selection,
        folderFacts = facts,
        refinements = Refinements.forListing(items, now),
        volume = volume,
        massScale = com.filish.design.glass.Mass.scaleFor(
            items.map { if (it.isDirectory) facts[it.path]?.measured?.bytes ?: 0L else it.size },
        ),
    )

    /** A mixed selection with folders still resolving - the ledger's whole point. */
    fun liveSelection(): Selection = Selection()
        .add(listOf(entries[0], entries[1], entries[3], entries[6]))
        .withMeasurement(MeasuredSize(11_742_000_000, 3_908, 14, settled = false))

    fun settledSelection(): Selection = Selection()
        .add(listOf(entries[0], entries[1], entries[3], entries[6]))
        .withMeasurement(MeasuredSize(12_486_300_000, 4_211, 17, settled = true))
}
