package com.filish.render

import com.filish.feature.browse.BrowseScreen
import com.filish.feature.browse.Clipboard
import com.filish.feature.places.PlacesScreen
import org.junit.Test

/**
 * The screens, rendered, so they can be criticised.
 */
class ScreenRenderTest : ScreenshotTest() {

    @Test
    fun places() {
        shoot("10-places") {
            PlacesScreen(
                volumes = listOf(Fixtures.volume, Fixtures.sdCard),
                pinned = listOf("/storage/emulated/0/Documents/Contracts"),
                recents = listOf(
                    "/storage/emulated/0/DCIM/Camera",
                    "/storage/emulated/0/Download",
                ),
                onOpen = {}, onStorage = {}, onUnpin = {},
                onSettings = {}, onSearch = {}, onClearRecents = {},
            )
        }
    }

    @Test
    fun browseList() {
        shoot("11-browse") {
            BrowseScreen(
                state = Fixtures.browseState(),
                clipboard = Clipboard(),
                showThumbnails = true,
                showExtensions = true,
                volumes = listOf(Fixtures.volume),
                onOpen = {}, onSelect = {}, onRefine = {}, onClearSelection = {},
                onTokenClick = {}, onStorage = {}, onSearch = {}, onArrange = {},
                onSelectMode = {}, onNew = {}, onCopy = {},
                onMove = {}, onDelete = {}, onShare = {}, onMore = {}, onPaste = {},
                onCancelPaste = {},
            )
        }
    }

    @Test
    fun browseWithLiveSelection() {
        shoot("12-selection-measuring") {
            BrowseScreen(
                state = Fixtures.browseState(selection = Fixtures.liveSelection()),
                clipboard = Clipboard(),
                showThumbnails = true,
                showExtensions = true,
                volumes = listOf(Fixtures.volume),
                onOpen = {}, onSelect = {}, onRefine = {}, onClearSelection = {},
                onTokenClick = {}, onStorage = {}, onSearch = {}, onArrange = {},
                onSelectMode = {}, onNew = {}, onCopy = {},
                onMove = {}, onDelete = {}, onShare = {}, onMore = {}, onPaste = {},
                onCancelPaste = {},
            )
        }
    }

    @Test
    fun selectionModeEmpty() {
        shoot("14-choosing") {
            BrowseScreen(
                state = Fixtures.browseState().copy(selectionMode = true),
                clipboard = Clipboard(),
                showThumbnails = true,
                showExtensions = true,
                volumes = listOf(Fixtures.volume),
                onOpen = {}, onSelect = {}, onRefine = {}, onClearSelection = {},
                onTokenClick = {}, onStorage = {}, onSearch = {}, onArrange = {},
                onSelectMode = {}, onNew = {}, onCopy = {},
                onMove = {}, onDelete = {}, onShare = {}, onMore = {}, onPaste = {},
                onCancelPaste = {},
            )
        }
    }

    @Test
    fun browseDark() {
        shoot("13-browse-dark", dark = true) {
            BrowseScreen(
                state = Fixtures.browseState(selection = Fixtures.settledSelection()),
                clipboard = Clipboard(),
                showThumbnails = true,
                showExtensions = true,
                volumes = listOf(Fixtures.volume),
                onOpen = {}, onSelect = {}, onRefine = {}, onClearSelection = {},
                onTokenClick = {}, onStorage = {}, onSearch = {}, onArrange = {},
                onSelectMode = {}, onNew = {}, onCopy = {},
                onMove = {}, onDelete = {}, onShare = {}, onMore = {}, onPaste = {},
                onCancelPaste = {},
            )
        }
    }
}
