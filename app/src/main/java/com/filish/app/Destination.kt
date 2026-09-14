package com.filish.app

import com.filish.core.model.FileKind

/**
 * Where the user can be.
 *
 * ---------------------------------------------------------------------------
 * On not having a bottom navigation bar
 *
 * The reflex is four tabs across the bottom: Files, Search, Storage, Settings.
 * FILISH does not, and the reasoning is worth recording because it is the
 * kind of decision that gets quietly reverted later.
 *
 * A bottom bar spends roughly 56dp plus the gesture inset - close to a tenth
 * of a phone screen - permanently, to make three rarely-used destinations one
 * tap away. But browsing is not one of four equal activities; it is around
 * ninety per cent of what happens in a file manager. Paying a tenth of the
 * list on every screen, forever, to shorten the path to Settings is a bad
 * trade, and it is a trade made by pattern rather than by measurement.
 *
 * So the browser is the application, and the other destinations are reached
 * from a single adaptive header:
 *
 *   - The PATH RAIL is navigation. Collapsed to its root it becomes Places,
 *     which is where volumes, pinned folders and recent locations live. There
 *     is no separate "home tab"; home is simply the top of the path.
 *   - SEARCH sits in the header, always one tap away, because finding is the
 *     second most common thing after browsing.
 *   - The STORAGE GAUGE in the header is both information and door. It shows
 *     how full the volume is at all times, and tapping it opens the analysis.
 *     One element, two jobs, no permanent tab.
 *   - SETTINGS is inside Places, because it is genuinely infrequent and
 *     pretending otherwise costs every other screen space.
 *
 * The result: no permanent chrome competing with content, and navigation that
 * is spatial and continuous rather than a set of parallel worlds.
 * ---------------------------------------------------------------------------
 */
sealed interface Destination {

    /** The root: volumes, pinned locations, recent places. */
    data object Places : Destination

    /** A directory. */
    data class Folder(val path: String) : Destination

    data object Search : Destination

    /** Storage analysis for one volume. */
    data class Storage(val volumePath: String) : Destination

    /** A filtered view produced by a storage finding - the drill-down that
     *  stops analysis from being a dead end. */
    data class Investigation(
        val title: String,
        val volumePath: String,
        val kinds: Set<FileKind>,
        val minSize: Long?,
        val olderThan: Long?,
    ) : Destination

    data class Duplicates(val volumePath: String) : Destination

    data object Settings : Destination

    val isFolder: Boolean get() = this is Folder
}

/**
 * The back stack.
 *
 * Explicit and immutable rather than a navigation library. FILISH's
 * transitions depend on knowing whether the user went deeper or came back -
 * the folder transition is directional and its inverse must be exact - and
 * that is a piece of state a general-purpose navigator hides. It is also
 * three dozen lines.
 */
data class NavStack(val entries: List<Destination> = listOf(Destination.Places)) {
    val current: Destination get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1

    fun push(destination: Destination): NavStack {
        // Re-entering the place you are already in should be a no-op rather
        // than a stack entry that makes Back appear broken.
        if (destination == current) return this
        return copy(entries = entries + destination)
    }

    fun pop(): NavStack = if (canGoBack) copy(entries = entries.dropLast(1)) else this

    /** Jumps back to an ancestor already on the stack, dropping what is above
     *  it - what tapping a path-rail token does. */
    fun popTo(destination: Destination): NavStack {
        val index = entries.indexOfLast { it == destination }
        return if (index < 0) push(destination) else copy(entries = entries.take(index + 1))
    }

    fun replaceTop(destination: Destination): NavStack =
        copy(entries = entries.dropLast(1) + destination)

    fun resetTo(destination: Destination): NavStack = NavStack(listOf(destination))
}

/** Which way the user moved, so the transition can state it. */
enum class NavDirection { Deeper, Back, Lateral }
