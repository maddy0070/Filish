package com.filish.core.model

/**
 * Sorting, grouping and filtering are three different questions, and FILISH
 * keeps them apart because conflating them is why file managers end up with a
 * single "Sort by" menu that cannot express what the user wants.
 *
 *   SORT   answers "in what order?"          - it is a total ordering.
 *   GROUP  answers "under what headings?"    - it partitions the order.
 *   FILTER answers "which ones at all?"      - it removes rows.
 *
 * They compose. "Largest first, grouped by type, videos only" is three
 * independent choices, and every combination is legal.
 */
enum class SortKey(val label: String) {
    Name("Name"),
    Size("Size"),
    Modified("Modified"),
    Kind("Type"),
    Extension("Extension"),
    Count("Items"),
    ;
}

enum class SortDirection { Ascending, Descending }

data class SortSpec(
    val key: SortKey = SortKey.Name,
    val direction: SortDirection = SortDirection.Ascending,
    /**
     * Folders above files regardless of the sort key.
     *
     * On by default, because a folder and a file are different kinds of
     * object: a folder is a place you go, a file is a thing you act on, and
     * interleaving them by size makes the user re-read the glyph on every row
     * to know which is which. It is a preference rather than a law because
     * "what is biggest in here, whatever it is" is a legitimate question -
     * and turning it off is exactly what storage analysis wants.
     */
    val foldersFirst: Boolean = true,
) {
    val descending: Boolean get() = direction == SortDirection.Descending

    fun toggled(newKey: SortKey): SortSpec = when {
        newKey != key -> copy(key = newKey, direction = defaultDirection(newKey))
        direction == SortDirection.Ascending -> copy(direction = SortDirection.Descending)
        else -> copy(direction = SortDirection.Ascending)
    }

    /**
     * The first tap on a key should give the answer the user was looking for.
     * Nobody sorts by size to find the smallest file, or by date to find the
     * oldest - so size and time start descending, and names start A to Z.
     */
    private fun defaultDirection(k: SortKey) = when (k) {
        SortKey.Size, SortKey.Modified, SortKey.Count -> SortDirection.Descending
        else -> SortDirection.Ascending
    }

    /** Human-readable statement of the current order, shown in the browser so
     *  the ordering is never a mystery the user has to open a menu to learn. */
    val statement: String
        get() {
            val d = when (key) {
                SortKey.Name -> if (descending) "Z to A" else "A to Z"
                SortKey.Size -> if (descending) "largest first" else "smallest first"
                SortKey.Modified -> if (descending) "newest first" else "oldest first"
                SortKey.Kind -> if (descending) "type, reversed" else "by type"
                SortKey.Extension -> if (descending) "extension, reversed" else "by extension"
                SortKey.Count -> if (descending) "most items first" else "fewest items first"
            }
            return d
        }
}

enum class GroupKey(val label: String) {
    None("No grouping"),
    Kind("Type"),
    FirstLetter("Initial"),
    SizeBand("Size"),
    TimeBand("Age"),
}

/**
 * Which rows exist at all.
 *
 * Everything here is an *addition* to the listing's default view except
 * [showHidden], which is a visibility decision the platform itself makes.
 */
data class FilterSpec(
    val kinds: Set<FileKind> = emptySet(),
    val showHidden: Boolean = false,
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val modifiedAfter: Long? = null,
    val modifiedBefore: Long? = null,
    val nameContains: String? = null,
) {
    val isActive: Boolean
        get() = kinds.isNotEmpty() || minSize != null || maxSize != null ||
            modifiedAfter != null || modifiedBefore != null || !nameContains.isNullOrBlank()

    /** How many independent constraints are in play. Shown as a count on the
     *  filter control so an active filter can never be invisible - a filtered
     *  list that looks unfiltered is how users conclude their files are gone. */
    val activeCount: Int
        get() = listOf(
            kinds.isNotEmpty(), minSize != null, maxSize != null,
            modifiedAfter != null, modifiedBefore != null, !nameContains.isNullOrBlank(),
        ).count { it }

    fun matches(node: FileNode): Boolean {
        if (!showHidden && node.isHidden) return false
        if (kinds.isNotEmpty() && node.kind !in kinds) return false
        nameContains?.takeIf { it.isNotBlank() }?.let {
            if (!node.name.contains(it, ignoreCase = true)) return false
        }
        // Size and time constraints are meaningless for a directory whose
        // recursive size has not been measured; directories pass through so a
        // size filter never hides the route to the matching files inside.
        if (!node.isDirectory) {
            minSize?.let { if (node.size < it) return false }
            maxSize?.let { if (node.size > it) return false }
        }
        modifiedAfter?.let { if (node.lastModified < it) return false }
        modifiedBefore?.let { if (node.lastModified > it) return false }
        return true
    }
}

enum class ViewMode(val label: String) {
    /** One row per entry: name, kind, size, time. Maximum information. */
    List("List"),
    /** Thumbnail-led grid. For directories whose content is visual. */
    Grid("Grid"),
}

/**
 * A natural-language ordering comparator.
 *
 * "file10.txt" must sort after "file9.txt". Plain lexicographic comparison
 * puts it before, which is wrong in the only way users reliably notice,
 * because numbered sequences - photos, screenshots, episodes, scans - are
 * most of what is actually in a phone's storage.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var si = i
                var sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                // Skip leading zeros so "007" and "7" compare as equal magnitude.
                while (si < i - 1 && a[si] == '0') si++
                while (sj < j - 1 && b[sj] == '0') sj++
                val la = i - si
                val lb = j - sj
                if (la != lb) return la - lb
                for (k in 0 until la) {
                    val d = a[si + k] - b[sj + k]
                    if (d != 0) return d
                }
            } else {
                val d = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (d != 0) return d
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
