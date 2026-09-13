package com.filish.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.filish.core.fs.ops.ConflictPolicy
import com.filish.core.fs.ops.DeleteEngine
import com.filish.core.fs.ops.OperationKind
import com.filish.core.fs.ops.OperationProgress
import com.filish.core.fs.ops.OperationState
import com.filish.core.model.ViewMode
import com.filish.core.settings.FilishSettings
import com.filish.design.Density
import com.filish.feature.browse.BrowseScreen
import com.filish.feature.browse.Clipboard
import com.filish.feature.operations.DeleteSheet
import com.filish.feature.operations.TransferBar
import com.filish.feature.permission.AccessGate
import com.filish.feature.permission.AccessLevel
import org.junit.Test

class MoreScreensTest : ScreenshotTest() {

    @Test
    fun accessGate() {
        shoot("20-access-gate") {
            AccessGate(
                level = AccessLevel.None,
                allFilesAvailable = true,
                onRequestAllFiles = {}, onRequestMedia = {}, onContinueLimited = {},
            )
        }
    }

    @Test
    fun deleteRecoverable() {
        shoot("21-delete-recoverable") {
            Box(Modifier.fillMaxSize()) {
                DeleteSheet(
                    visible = true,
                    plan = DeleteEngine.Plan(
                        nodes = Fixtures.entries.take(3),
                        route = DeleteEngine.Route.SystemTrash,
                        permanentReason = DeleteEngine.PermanentReason.None,
                        trashableUris = listOf(android.net.Uri.parse("content://media/external/file/1")),
                        unindexedPaths = emptyList(),
                        permanentPaths = emptyList(),
                        totalBytes = 1_852_000_000,
                        fileCount = 3,
                        folderCount = 0,
                    ),
                    onConfirm = {}, onDismiss = {},
                )
            }
        }
    }

    @Test
    fun deletePermanent() {
        shoot("22-delete-permanent") {
            Box(Modifier.fillMaxSize()) {
                DeleteSheet(
                    visible = true,
                    plan = DeleteEngine.Plan(
                        nodes = Fixtures.entries.take(4),
                        route = DeleteEngine.Route.Permanent,
                        permanentReason = DeleteEngine.PermanentReason.IsDirectory,
                        trashableUris = emptyList(),
                        unindexedPaths = emptyList(),
                        permanentPaths = Fixtures.entries.take(4).map { it.path },
                        totalBytes = 9_240_000_000,
                        fileCount = 1,
                        folderCount = 3,
                    ),
                    onConfirm = {}, onDismiss = {},
                )
            }
        }
    }

    @Test
    fun transferInFlight() {
        shoot("23-transfer", heightDp = 320) {
            Box(Modifier.fillMaxSize()) {
                TransferBar(
                    progress = OperationProgress(
                        id = 1,
                        kind = OperationKind.Move,
                        state = OperationState.Running,
                        currentItemName = "VID_20240918_154233_exported_final.mp4",
                        itemsDone = 184,
                        itemsTotal = 402,
                        bytesDone = 4_820_000_000,
                        bytesTotal = 11_400_000_000,
                        bytesPerSecond = 128_000_000,
                        destinationLabel = "SD card",
                        sourceLabel = "Camera",
                        startedAt = System.currentTimeMillis() - 40_000,
                    ),
                    onCancel = {},
                )
            }
        }
    }

    @Test
    fun gridView() {
        shoot("24-grid") {
            BrowseScreen(
                state = Fixtures.browseState().copy(viewMode = ViewMode.Grid),
                clipboard = Clipboard(),
                showThumbnails = true, showExtensions = true,
                volumes = listOf(Fixtures.volume),
                onOpen = {}, onSelect = {}, onRefine = {}, onClearSelection = {},
                onTokenClick = {}, onStorage = {}, onSearch = {}, onSortTap = {},
                onFilterTap = {}, onViewToggle = {}, onNewFolder = {}, onCopy = {},
                onMove = {}, onDelete = {}, onShare = {}, onMore = {}, onPaste = {},
                onCancelPaste = {},
            )
        }
    }

    /** A small phone at the largest system font scale - where layouts break. */
    @Test
    fun denseOnASmallScreen() {
        shoot("25-small-dense", widthDp = 320, heightDp = 640, density = Density.Dense) {
            BrowseScreen(
                state = Fixtures.browseState(),
                clipboard = Clipboard(),
                showThumbnails = true, showExtensions = true,
                volumes = listOf(Fixtures.volume),
                onOpen = {}, onSelect = {}, onRefine = {}, onClearSelection = {},
                onTokenClick = {}, onStorage = {}, onSearch = {}, onSortTap = {},
                onFilterTap = {}, onViewToggle = {}, onNewFolder = {}, onCopy = {},
                onMove = {}, onDelete = {}, onShare = {}, onMore = {}, onPaste = {},
                onCancelPaste = {},
            )
        }
    }

    @Test
    fun highContrast() {
        shoot("26-high-contrast", highContrast = true) {
            BrowseScreen(
                state = Fixtures.browseState(),
                clipboard = Clipboard(),
                showThumbnails = true, showExtensions = true,
                volumes = listOf(Fixtures.volume),
                onOpen = {}, onSelect = {}, onRefine = {}, onClearSelection = {},
                onTokenClick = {}, onStorage = {}, onSearch = {}, onSortTap = {},
                onFilterTap = {}, onViewToggle = {}, onNewFolder = {}, onCopy = {},
                onMove = {}, onDelete = {}, onShare = {}, onMore = {}, onPaste = {},
                onCancelPaste = {},
            )
        }
    }

    @Test
    fun emptyFolder() {
        shoot("27-empty") {
            BrowseScreen(
                state = Fixtures.browseState(items = emptyList()),
                clipboard = Clipboard(),
                showThumbnails = true, showExtensions = true,
                volumes = listOf(Fixtures.volume),
                onOpen = {}, onSelect = {}, onRefine = {}, onClearSelection = {},
                onTokenClick = {}, onStorage = {}, onSearch = {}, onSortTap = {},
                onFilterTap = {}, onViewToggle = {}, onNewFolder = {}, onCopy = {},
                onMove = {}, onDelete = {}, onShare = {}, onMore = {}, onPaste = {},
                onCancelPaste = {},
            )
        }
    }

    @Test
    fun settings() {
        shoot("28-settings") {
            com.filish.feature.settings.SettingsScreen(
                settings = FilishSettings(),
                store = com.filish.core.settings.SettingsStore(
                    androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                ),
                displayFaceAvailable = true,
                onBack = {},
            )
        }
    }
}
