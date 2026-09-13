package com.filish.feature.browse

import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.MeasuredSize

/**
 * What the user has picked, and what that adds up to.
 *
 * ---------------------------------------------------------------------------
 * The problem this exists to solve
 *
 * Select four folders and two videos in any conventional file manager and it
 * will tell you "6 selected". That is the one fact you already knew. What you
 * wanted to know is how much space those six things occupy - and the standard
 * way to find out is to create a temporary folder, move everything into it,
 * open its properties, wait, read the number, and then move everything back.
 * People genuinely do this. It is absurd, and it is absurd because the file
 * manager decided that computing it was too slow, and therefore declined to
 * compute it at all.
 *
 * Slow is not the same as impossible. The correct response to "this takes
 * three seconds" is to show the answer as it arrives, not to withhold it.
 *
 * ---------------------------------------------------------------------------
 * How it works
 *
 * Files contribute their size immediately - it came free with the listing.
 * Folders are measured by a walk that streams a running total (see
 * SizeResolver), so the figure is present from the first frame and climbs
 * until it settles. The interface distinguishes the two states, so an
 * in-progress total is never mistaken for a final one.
 *
 * Everything else here - the kind breakdown, the file/folder split, which
 * actions are even applicable - falls out of the same object, because the
 * question "what do I have selected" and the question "what can I do with it"
 * are the same question.
 */
data class Selection(
    val paths: Set<String> = emptySet(),
    /** The nodes themselves, kept so aggregates survive a directory refresh
     *  and so a selection can span directories in future. */
    val nodes: Map<String, FileNode> = emptyMap(),
    /** Live total, streaming. */
    val measured: MeasuredSize = MeasuredSize.Unknown,
    /** True while a selection gesture is being dragged across rows. */
    val isDragging: Boolean = false,
) {
    val isActive: Boolean get() = paths.isNotEmpty()
    val count: Int get() = paths.size

    val selectedNodes: List<FileNode> get() = paths.mapNotNull { nodes[it] }

    val fileCount: Int get() = selectedNodes.count { !it.isDirectory }
    val folderCount: Int get() = selectedNodes.count { it.isDirectory }

    /** Sizes of plain files are known without any walk at all. */
    val knownFileBytes: Long get() = selectedNodes.filter { !it.isDirectory }.sumOf { it.size }

    val needsMeasurement: Boolean get() = folderCount > 0

    /** The total to display: measured when folders are involved, exact
     *  otherwise. */
    val displayBytes: Long
        get() = if (needsMeasurement) measured.bytes else knownFileBytes

    val isSettled: Boolean get() = !needsMeasurement || measured.settled

    /** Kinds present, ordered by how many of each - used to offer
     *  "select all videos" style refinements only when they would do
     *  something. */
    fun kindBreakdown(): List<Pair<FileKind, Int>> =
        selectedNodes.groupingBy { it.kind }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

    /**
     * The one-line summary shown in the ledger.
     *
     * Written so the two halves - what is selected, how big it is - stay
     * separately readable, because they are separately useful.
     */
    fun describe(): String {
        val parts = ArrayList<String>(2)
        if (fileCount > 0) {
            parts.add(com.filish.core.model.Format.plural(fileCount, "file", "files"))
        }
        if (folderCount > 0) {
            parts.add(com.filish.core.model.Format.plural(folderCount, "folder", "folders"))
        }
        return parts.joinToString(", ")
    }

    /** What is still being worked out, stated rather than implied. */
    fun measurementNote(): String? = when {
        !needsMeasurement -> null
        measured.settled && measured.unreadable > 0 ->
            "${measured.unreadable} items could not be read"
        measured.settled -> null
        measured.folders > 0 ->
            "measuring ${com.filish.core.model.Format.count(measured.files)} files"
        else -> "measuring"
    }

    fun contains(path: String) = path in paths

    fun toggle(node: FileNode): Selection = if (node.path in paths) {
        copy(paths = paths - node.path, nodes = nodes - node.path, measured = MeasuredSize.Unknown)
    } else {
        copy(
            paths = paths + node.path,
            nodes = nodes + (node.path to node),
            measured = MeasuredSize.Unknown,
        )
    }

    fun add(items: List<FileNode>): Selection = copy(
        paths = paths + items.map { it.path },
        nodes = nodes + items.associateBy { it.path },
        measured = MeasuredSize.Unknown,
    )

    fun setTo(items: List<FileNode>): Selection = Selection(
        paths = items.map { it.path }.toSet(),
        nodes = items.associateBy { it.path },
    )

    fun clear(): Selection = Selection()

    /** Everything in the current listing that is not currently selected. */
    fun inverted(all: List<FileNode>): Selection = setTo(all.filter { it.path !in paths })

    fun withMeasurement(size: MeasuredSize): Selection = copy(measured = size)
}

/**
 * Ways to select a lot of things at once.
 *
 * These exist because the alternative - tapping forty rows - is not a
 * workflow, it is a punishment. Each one is offered only when the current
 * listing makes it meaningful: there is no "select all videos" in a folder
 * with no videos, because an action that does nothing is worse than an action
 * that is absent.
 */
sealed interface SelectionRefinement {
    val label: String

    data object All : SelectionRefinement {
        override val label = "Everything"
    }

    data object Invert : SelectionRefinement {
        override val label = "Invert"
    }

    data class ByKind(val kind: FileKind, val count: Int) : SelectionRefinement {
        override val label = "${kind.label}s"
    }

    data class LargerThan(val bytes: Long, val count: Int) : SelectionRefinement {
        override val label = "Over ${com.filish.core.model.Format.size(bytes)}"
    }

    data class OlderThan(val epochMs: Long, val count: Int, val spoken: String) :
        SelectionRefinement {
        override val label = spoken
    }

    data object Files : SelectionRefinement {
        override val label = "Files only"
    }

    data object Folders : SelectionRefinement {
        override val label = "Folders only"
    }
}

object Refinements {

    /**
     * Derives the refinements worth offering for a listing.
     *
     * The size and age thresholds are computed from the actual content rather
     * than fixed, so "Over 100 MB" appears in a folder of videos and
     * "Over 2 MB" in a folder of documents. A fixed threshold is either
     * useless or absent in most real folders.
     */
    fun forListing(items: List<FileNode>, now: Long = System.currentTimeMillis()): List<SelectionRefinement> {
        if (items.isEmpty()) return emptyList()
        val out = ArrayList<SelectionRefinement>(6)
        out.add(SelectionRefinement.All)
        out.add(SelectionRefinement.Invert)

        val files = items.filter { !it.isDirectory }
        val folders = items.count { it.isDirectory }
        if (files.isNotEmpty() && folders > 0) {
            out.add(SelectionRefinement.Files)
            out.add(SelectionRefinement.Folders)
        }

        // Type refinements only where a type is a real subset - offering
        // "Images" in a folder that is entirely images does nothing.
        items.groupingBy { it.kind }.eachCount()
            .filter { it.value >= 2 && it.value < items.size && it.key != FileKind.Folder }
            .entries.sortedByDescending { it.value }
            .take(3)
            .forEach { out.add(SelectionRefinement.ByKind(it.key, it.value)) }

        // A size threshold at the 75th percentile: high enough to be a
        // meaningful subset, low enough to actually select something.
        if (files.size >= 4) {
            val sorted = files.map { it.size }.sorted()
            val threshold = sorted[(sorted.size * 0.75).toInt().coerceAtMost(sorted.lastIndex)]
            if (threshold > 0) {
                val n = files.count { it.size >= threshold }
                if (n in 1 until files.size) {
                    out.add(SelectionRefinement.LargerThan(threshold, n))
                }
            }
        }

        if (items.size >= 4) {
            val yearAgo = now - java.util.concurrent.TimeUnit.DAYS.toMillis(365)
            val old = items.count { it.lastModified in 1 until yearAgo }
            if (old in 1 until items.size) {
                out.add(SelectionRefinement.OlderThan(yearAgo, old, "Over a year old"))
            }
        }

        return out
    }

    fun apply(
        refinement: SelectionRefinement,
        items: List<FileNode>,
        current: Selection,
    ): Selection = when (refinement) {
        SelectionRefinement.All -> current.setTo(items)
        SelectionRefinement.Invert -> current.inverted(items)
        SelectionRefinement.Files -> current.setTo(items.filter { !it.isDirectory })
        SelectionRefinement.Folders -> current.setTo(items.filter { it.isDirectory })
        is SelectionRefinement.ByKind -> current.setTo(items.filter { it.kind == refinement.kind })
        is SelectionRefinement.LargerThan ->
            current.setTo(items.filter { !it.isDirectory && it.size >= refinement.bytes })
        is SelectionRefinement.OlderThan ->
            current.setTo(items.filter { it.lastModified in 1 until refinement.epochMs })
    }
}
