package com.filish.core.intel

import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import java.util.concurrent.TimeUnit

/** Bytes and count for one category. The pair travels together everywhere,
 *  because "4.2 GB" and "4.2 GB across 11,400 files" mean different things. */
data class Bucket(val bytes: Long, val count: Int) {
    operator fun plus(other: Bucket) = Bucket(bytes + other.bytes, count + other.count)
    companion object { val Zero = Bucket(0, 0) }
}

/** How old files are. Age is the strongest available proxy for "you are
 *  probably done with this", which is the question cleanup really asks. */
enum class AgeBand(val label: String, val maxDays: Long) {
    Week("This week", 7),
    Month("This month", 30),
    Quarter("Last 3 months", 90),
    Year("This year", 365),
    Older("Over a year old", Long.MAX_VALUE);

    companion object {
        fun of(lastModified: Long, now: Long): AgeBand {
            if (lastModified <= 0) return Older
            val days = TimeUnit.MILLISECONDS.toDays(now - lastModified)
            return entries.first { days <= it.maxDays }
        }
    }
}

/** Where the bytes are, by size of individual file. Distinguishes "10,000
 *  small files" from "four enormous ones" - two very different problems with
 *  two very different remedies. */
enum class SizeBand(val label: String, val upperBytes: Long) {
    Tiny("Under 1 MB", 1_000_000),
    Small("1 - 10 MB", 10_000_000),
    Medium("10 - 100 MB", 100_000_000),
    Large("100 MB - 1 GB", 1_000_000_000),
    Huge("Over 1 GB", Long.MAX_VALUE);

    companion object {
        fun of(bytes: Long): SizeBand = entries.first { bytes < it.upperBytes }
    }
}

/**
 * A directory that is worth the user's attention, with its recursive weight.
 */
data class HeavyFolder(
    val path: String,
    val name: String,
    val bytes: Long,
    val fileCount: Int,
    val depth: Int,
)

/**
 * The result of analysing a volume.
 *
 * Everything here exists to support an *action*. A number the user cannot do
 * anything about is not intelligence, it is trivia, and it was left out. Each
 * field below answers a question that leads somewhere: which category to open,
 * which folder to inspect, which file to delete.
 */
data class StorageReport(
    val volumePath: String,
    val totalBytes: Long,
    val availableBytes: Long,
    /** What FILISH could actually see and measure. Always less than
     *  usedBytes - the OS, other apps' sandboxes and system partitions are
     *  not readable, and pretending otherwise would make the numbers lie. */
    val accountedBytes: Long,
    val byKind: Map<FileKind, Bucket>,
    val byAge: Map<AgeBand, Bucket>,
    val bySize: Map<SizeBand, Bucket>,
    val largestFiles: List<FileNode>,
    val heaviestFolders: List<HeavyFolder>,
    val emptyFolders: Int,
    val hiddenBytes: Long,
    /** Files sharing an exact byte length - the cheap first pass of duplicate
     *  detection. A size collision is not a duplicate, but a non-collision is
     *  proof of non-duplication, which is what makes the pass worth doing. */
    val duplicateCandidates: Int,
    val duplicateCandidateBytes: Long,
    val filesScanned: Int,
    val foldersScanned: Int,
    val unreadable: Int,
    val complete: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)

    /** The portion of used space FILISH cannot attribute. Shown honestly as
     *  "System and other apps" rather than being folded into "Other". */
    val unaccountedBytes: Long get() = (usedBytes - accountedBytes).coerceAtLeast(0)

    val usedFraction: Float
        get() = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    /** Categories ordered by weight, which is the order the user cares about. */
    fun kindsByWeight(): List<Pair<FileKind, Bucket>> =
        byKind.entries.filter { it.value.bytes > 0 }
            .sortedByDescending { it.value.bytes }
            .map { it.key to it.value }

    companion object {
        fun empty(volumePath: String, total: Long, available: Long) = StorageReport(
            volumePath, total, available, 0, emptyMap(), emptyMap(), emptyMap(),
            emptyList(), emptyList(), 0, 0, 0, 0, 0, 0, 0, complete = false,
        )
    }
}

/**
 * A lead worth following, derived from a report.
 *
 * This is the part that makes the analysis not a dead end. A dashboard that
 * reports "Videos: 24 GB" and stops has told the user something they could
 * have guessed. A dashboard that says "9 videos over 1 GB haven't been opened
 * in a year - 14 GB" has given them a decision.
 */
data class Finding(
    val id: String,
    val headline: String,
    val detail: String,
    val bytes: Long,
    val weight: Int,
    val action: FindingAction,
)

sealed interface FindingAction {
    data class OpenFiltered(val kinds: Set<FileKind>, val minSize: Long?, val olderThan: Long?) : FindingAction
    data class OpenFolder(val path: String) : FindingAction
    data object OpenDuplicates : FindingAction
    data object OpenLargest : FindingAction
}

object Findings {

    /**
     * Derives leads from a report.
     *
     * Ordered by how much storage the user could plausibly reclaim, not by
     * category size. "You have 40 GB of photos" is not a finding - photos are
     * the point of the phone. "You have 12 GB of RAW files alongside their
     * JPEGs" is.
     */
    fun from(report: StorageReport, now: Long = System.currentTimeMillis()): List<Finding> {
        val out = ArrayList<Finding>()

        report.byKind[FileKind.RawImage]?.takeIf { it.bytes > 500_000_000 }?.let { raw ->
            out.add(
                Finding(
                    id = "raw",
                    headline = "RAW photos are taking up real space",
                    detail = "${raw.count} RAW files. Most phones and cameras keep a JPEG " +
                        "beside each one, so this may be a second copy of pictures you already have.",
                    bytes = raw.bytes,
                    weight = 90,
                    action = FindingAction.OpenFiltered(setOf(FileKind.RawImage), null, null),
                ),
            )
        }

        val oldLarge = report.largestFiles.filter {
            AgeBand.of(it.lastModified, now) == AgeBand.Older && it.size > 100_000_000
        }
        if (oldLarge.size >= 3) {
            out.add(
                Finding(
                    id = "old-large",
                    headline = "Large files you haven't touched in over a year",
                    detail = "${oldLarge.size} files over 100 MB, none modified in the last year.",
                    bytes = oldLarge.sumOf { it.size },
                    weight = 95,
                    action = FindingAction.OpenFiltered(
                        emptySet(), 100_000_000, now - TimeUnit.DAYS.toMillis(365),
                    ),
                ),
            )
        }

        if (report.duplicateCandidates > 20) {
            out.add(
                Finding(
                    id = "dupes",
                    headline = "Possible duplicates",
                    detail = "${report.duplicateCandidates} files share an exact size with " +
                        "another file. FILISH can compare their contents to find real copies.",
                    bytes = report.duplicateCandidateBytes,
                    weight = 80,
                    action = FindingAction.OpenDuplicates,
                ),
            )
        }

        report.byKind[FileKind.App]?.takeIf { it.bytes > 200_000_000 }?.let { apk ->
            out.add(
                Finding(
                    id = "apk",
                    headline = "Installer packages left behind",
                    detail = "${apk.count} .apk files. Once an app is installed its installer " +
                        "is no longer needed.",
                    bytes = apk.bytes,
                    weight = 85,
                    action = FindingAction.OpenFiltered(setOf(FileKind.App), null, null),
                ),
            )
        }

        report.byKind[FileKind.Archive]?.takeIf { it.bytes > 500_000_000 }?.let { arc ->
            out.add(
                Finding(
                    id = "archives",
                    headline = "Archives you may have already extracted",
                    detail = "${arc.count} archive files. If you have unpacked these, the " +
                        "archive is a second copy.",
                    bytes = arc.bytes,
                    weight = 60,
                    action = FindingAction.OpenFiltered(setOf(FileKind.Archive), null, null),
                ),
            )
        }

        if (report.emptyFolders > 25) {
            out.add(
                Finding(
                    id = "empty",
                    headline = "Empty folders",
                    detail = "${report.emptyFolders} folders contain nothing. They take no " +
                        "meaningful space, but they make browsing slower.",
                    bytes = 0,
                    weight = 20,
                    action = FindingAction.OpenLargest,
                ),
            )
        }

        report.heaviestFolders.firstOrNull()?.takeIf { it.bytes > report.accountedBytes / 4 }?.let { f ->
            out.add(
                Finding(
                    id = "hotspot",
                    headline = "One folder holds most of your storage",
                    detail = "${f.name} accounts for a quarter or more of everything FILISH " +
                        "can see, across ${f.fileCount} files.",
                    bytes = f.bytes,
                    weight = 70,
                    action = FindingAction.OpenFolder(f.path),
                ),
            )
        }

        return out.sortedWith(compareByDescending<Finding> { it.weight }.thenByDescending { it.bytes })
    }
}
