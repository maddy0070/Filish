package com.filish.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filish.core.fs.Volume
import com.filish.core.fs.ops.ConflictPolicy
import com.filish.core.fs.ops.DeleteController
import com.filish.core.fs.ops.OperationKind
import com.filish.core.intel.FindingAction
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.core.model.ViewMode
import com.filish.core.settings.FilishSettings
import com.filish.design.Filish
import com.filish.design.Motion
import com.filish.design.Space
import com.filish.feature.browse.BrowseScreen
import com.filish.feature.browse.BrowseViewModel
import com.filish.feature.browse.Departure
import com.filish.feature.browse.ArrangeSheet
import com.filish.feature.browse.CreateSheet
import com.filish.feature.browse.NameSheet
import com.filish.feature.browse.PLACES_TOKEN
import com.filish.feature.operations.ConflictSheet
import com.filish.feature.operations.DeleteSheet
import com.filish.feature.operations.TransferBar
import com.filish.feature.permission.Access
import com.filish.feature.permission.AccessGate
import com.filish.feature.permission.AccessLevel
import com.filish.feature.places.PlacesScreen
import com.filish.feature.properties.PropertiesSheet
import com.filish.design.component.Report
import com.filish.design.component.Severity
import com.filish.filish
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Which transient surface, if any, is up. */
private enum class Overlay {
    None,
    /** Layout, order, grouping and filtering - one question, one door. */
    Arrange,
    /** What can arrive in this folder. */
    Create,
    NewFolder, NewTextFile, Rename, Properties, Delete, More,
}

/**
 * The application shell.
 *
 * Owns the back stack, the transitions between destinations, and the surfaces
 * that can appear over any of them. Screens themselves are stateless given
 * their inputs, which keeps them previewable and keeps the wiring in one
 * readable place.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FilishRoot(
    settings: FilishSettings,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val reduceMotion = Filish.a11y.reduceMotion
    val context = LocalContext.current
    val graph = context.filish
    val scope = rememberCoroutineScope()

    val browse: BrowseViewModel = viewModel()
    val state by browse.state.collectAsStateWithLifecycle()
    val clipboard by browse.clipboard.collectAsStateWithLifecycle()
    val operation by graph.operations.active.collectAsStateWithLifecycle()
    val conflict by graph.operations.conflict.collectAsStateWithLifecycle()

    var nav by remember { mutableStateOf(NavStack()) }
    var direction by remember { mutableStateOf(NavDirection.Deeper) }
    var volumes by remember { mutableStateOf<List<Volume>>(emptyList()) }
    var access by remember { mutableStateOf(Access.level(context)) }
    var opened by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }

    var overlay by remember { mutableStateOf(Overlay.None) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var propertiesTarget by remember { mutableStateOf<FileNode?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<ReportState?>(null) }

    val deletes = graph.deleteController
    val deleteStage by deletes.stage.collectAsStateWithLifecycle()

    // System consent for trashing. FILISH holds all-files access, so this is
    // the fallback path rather than the normal one - and it now only carries a
    // verdict back to the controller, which owns the operation.
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        deletes.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { access = Access.level(context) }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { access = Access.level(context) }

    LaunchedEffect(Unit) {
        volumes = browse.refreshVolumes()
        ready = true
    }

    // Re-check on every resume: the user may have granted access in Settings
    // and come back, and finding the app still refusing would be maddening.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        access = Access.level(context)
        onPauseOrDispose { }
    }

    // The controller asks; the composable launches. Keyed on the batch index
    // so a multi-batch consent sequence fires once per batch.
    LaunchedEffect(deleteStage) {
        val awaiting = deleteStage as? DeleteController.Stage.AwaitingConsent ?: return@LaunchedEffect
        runCatching {
            trashLauncher.launch(IntentSenderRequest.Builder(awaiting.sender).build())
        }.onFailure { deletes.onConsentResult(false) }
    }

    // A finished delete updates the browser and reports, exactly once.
    LaunchedEffect(deleteStage) {
        val finished = deleteStage as? DeleteController.Stage.Finished ?: return@LaunchedEffect
        overlay = Overlay.None
        browse.clearSelection()
        // Departing, not dropping: the rows animate out first, in the manner
        // that matches what actually happened to them.
        browse.departPaths(finished.result.trashedPaths, Departure.ToTrash)
        browse.departPaths(finished.result.destroyedPaths, Departure.Destroyed)
        browse.refresh()
        report = reportForDelete(finished.result)
        deletes.acknowledge()
    }

    LaunchedEffect(report) {
        if (report != null) {
            delay(5_000)
            report = null
        }
    }

    fun go(destination: Destination) {
        direction = NavDirection.Deeper
        nav = nav.push(destination)
    }

    /**
     * Starting a deletion, from wherever the user is.
     *
     * Shared by the browser, investigations and the duplicate screen so all
     * three get the same routing, the same honest consequence text, and the
     * same progress reporting.
     */
    fun beginDelete(nodes: List<FileNode>) {
        deletes.begin(nodes, settings.skipConfirmWhenRecoverable)
    }

    /** Brings staged files into the current folder. */
    fun paste() {
        val staged = clipboard
        if (!staged.isActive) return
        val destination = state.path
        browse.clearClipboard()
        scope.launch {
            val kind = if (staged.move) OperationKind.Move else OperationKind.Copy
            val refusal = graph.operations.check(staged.nodes, destination, kind, volumes)
            if (refusal != null) {
                report = ReportState(
                    "Cannot ${kind.verb.lowercase()} here",
                    refusal.explanation,
                    Severity.Problem,
                )
                return@launch
            }
            val result = graph.operations.run(
                sources = staged.nodes,
                destinationDir = destination,
                kind = kind,
                volumes = volumes,
                defaultPolicy = if (settings.defaultConflictAsk) {
                    ConflictPolicy.Ask
                } else {
                    ConflictPolicy.KeepBoth
                },
                sourceLabel = staged.originLabel,
            )
            browse.refresh()
            report = reportFor(result)
        }
    }

    fun back() {
        if (nav.canGoBack) {
            direction = NavDirection.Back
            nav = nav.pop()
        }
    }

    BackHandler(enabled = state.selection.isActive || overlay != Overlay.None || nav.canGoBack) {
        when {
            overlay != Overlay.None -> overlay = Overlay.None
            state.selection.isActive -> browse.clearSelection()
            else -> back()
        }
    }

    // Keep the browser pointed at whatever folder destination is on top.
    LaunchedEffect(nav.current) {
        (nav.current as? Destination.Folder)?.let { browse.open(it.path) }
    }

    Box(modifier.fillMaxSize().background(palette.ground0)) {
        if (access == AccessLevel.None ||
            (access == AccessLevel.Media && !Access.hasAllFiles() && nav.current == Destination.Places && volumes.isEmpty())
        ) {
            AccessGate(
                level = access,
                allFilesAvailable = Access.allFilesAvailable(),
                onRequestAllFiles = {
                    Access.allFilesIntent(context)?.let { allFilesLauncher.launch(it) }
                },
                onRequestMedia = { mediaPermissionLauncher.launch(Access.mediaPermissions()) },
                onContinueLimited = { access = AccessLevel.Media },
            )
        } else {
            AnimatedContent(
                targetState = nav.current,
                transitionSpec = { transitionFor(direction, reduceMotion) },
                label = "destination",
            ) { destination ->
                when (destination) {
                    is Destination.Places -> PlacesScreen(
                        volumes = volumes,
                        pinned = settings.pinnedPaths.toList(),
                        recents = settings.recentPaths,
                        onOpen = { go(Destination.Folder(it)) },
                        onStorage = { go(Destination.Storage(it.path)) },
                        onUnpin = { scope.launch { graph.settings.togglePinned(it) } },
                        onSettings = { go(Destination.Settings) },
                        onSearch = { go(Destination.Search) },
                        onClearRecents = { scope.launch { graph.settings.clearRecents() } },
                    )

                    is Destination.Folder -> BrowseScreen(
                        state = state,
                        clipboard = clipboard,
                        showThumbnails = settings.showThumbnails,
                        spineMedia = settings.spineMedia,
                        showExtensions = settings.showExtensions,
                        volumes = volumes,
                        onOpen = { node ->
                            if (node.isDirectory) {
                                go(Destination.Folder(node.path))
                            } else {
                                openFile(context, node)
                            }
                        },
                        onSelect = { browse.toggleSelection(it) },
                        onRefine = { browse.refine(it) },
                        onClearSelection = { browse.clearSelection() },
                        onTokenClick = { token ->
                            direction = NavDirection.Back
                            nav = if (token.path == PLACES_TOKEN) {
                                nav.popTo(Destination.Places)
                            } else {
                                nav.popTo(Destination.Folder(token.path))
                            }
                        },
                        onStorage = {
                            state.volume?.let { go(Destination.Storage(it.path)) }
                        },
                        onSearch = { go(Destination.Search) },
                        onArrange = { overlay = Overlay.Arrange },
                        onSelectMode = {
                            if (state.selectionMode || state.selection.isActive) {
                                browse.exitSelectionMode()
                            } else {
                                browse.enterSelectionMode()
                            }
                        },
                        onNew = { overlay = Overlay.Create },
                        onCopy = { browse.stage(move = false) },
                        onMove = { browse.stage(move = true) },
                        onDelete = { beginDelete(state.selection.selectedNodes) },
                        onShare = { shareNodes(context, state.selection.selectedNodes) },
                        onMore = { overlay = Overlay.More },
                        onPaste = { paste() },
                        onCancelPaste = { browse.clearClipboard() },
                    )

                    is Destination.Search -> com.filish.feature.search.SearchScreen(
                        roots = volumes.map { it.path },
                        showHidden = settings.showHidden,
                        onOpen = { node ->
                            if (node.isDirectory) go(Destination.Folder(node.path))
                            else openFile(context, node)
                        },
                        onReveal = { go(Destination.Folder(File(it.path).parent ?: it.path)) },
                        onBack = { back() },
                    )

                    is Destination.Storage -> com.filish.feature.storage.StorageScreen(
                        volume = volumes.firstOrNull { it.path == destination.volumePath },
                        includeHidden = settings.analysisIncludesHidden,
                        onOpenFolder = { go(Destination.Folder(it)) },
                        onOpenFile = { openFile(context, it) },
                        onFinding = { finding ->
                            when (val action = finding.action) {
                                is FindingAction.OpenFolder -> go(Destination.Folder(action.path))
                                is FindingAction.OpenDuplicates ->
                                    go(Destination.Duplicates(destination.volumePath))
                                is FindingAction.OpenFiltered -> go(
                                    Destination.Investigation(
                                        title = finding.headline,
                                        volumePath = destination.volumePath,
                                        kinds = action.kinds,
                                        minSize = action.minSize,
                                        olderThan = action.olderThan,
                                    ),
                                )
                                is FindingAction.OpenLargest -> go(
                                    Destination.Investigation(
                                        title = "Largest files",
                                        volumePath = destination.volumePath,
                                        kinds = emptySet(),
                                        minSize = 50_000_000,
                                        olderThan = null,
                                    ),
                                )
                            }
                        },
                        onBack = { back() },
                    )

                    is Destination.Investigation ->
                        com.filish.feature.storage.InvestigationScreen(
                            title = destination.title,
                            roots = listOf(destination.volumePath),
                            kinds = destination.kinds,
                            minSize = destination.minSize,
                            olderThan = destination.olderThan,
                            includeHidden = settings.analysisIncludesHidden,
                            onOpenFile = { openFile(context, it) },
                            onReveal = { go(Destination.Folder(File(it.path).parent ?: it.path)) },
                            onDelete = { nodes -> beginDelete(nodes) },
                            onShare = { shareNodes(context, it) },
                            onBack = { back() },
                        )

                    is Destination.Duplicates ->
                        com.filish.feature.storage.DuplicatesScreen(
                            volumePath = destination.volumePath,
                            includeHidden = settings.analysisIncludesHidden,
                            onOpenFile = { openFile(context, it) },
                            onDelete = { nodes -> beginDelete(nodes) },
                            onBack = { back() },
                        )

                    is Destination.Settings -> com.filish.feature.settings.SettingsScreen(
                        settings = settings,
                        store = graph.settings,
                        displayFaceAvailable = Filish.type.displayFaceAvailable,
                        onBack = { back() },
                    )

                }
            }
        }

        // Surfaces that can appear over any destination.
        TransferBar(
            progress = operation,
            onCancel = { graph.operations.cancelActive() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        report?.let { r ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Space.near)
                    .padding(bottom = if (operation != null) 96.dp else 0.dp),
            ) {
                Report(
                    message = r.message,
                    detail = r.detail,
                    severity = r.severity,
                    actionLabel = r.actionLabel,
                    onAction = r.onAction,
                )
            }
        }

        ArrangeSheet(
            visible = overlay == Overlay.Arrange,
            sort = state.sort,
            group = state.group,
            filter = state.filter,
            viewMode = state.viewMode,
            items = state.items,
            onSort = { browse.setSort(it) },
            onGroup = { browse.setGroup(it) },
            onFilter = { browse.setFilter(it) },
            onViewMode = { browse.setViewMode(it) },
            onDismiss = { overlay = Overlay.None },
        )

        CreateSheet(
            visible = overlay == Overlay.Create,
            folderName = File(state.path).name,
            clipboard = clipboard,
            onNewFolder = { nameError = null; overlay = Overlay.NewFolder },
            onNewTextFile = { nameError = null; overlay = Overlay.NewTextFile },
            onPaste = { paste() },
            onDismiss = { overlay = Overlay.None },
        )

        NameSheet(
            visible = overlay == Overlay.NewTextFile,
            title = "New text file",
            initial = "",
            actionLabel = "Create",
            selectStemOnly = false,
            error = nameError,
            onSubmit = { name ->
                browse.createTextFile(name) { error ->
                    nameError = error
                    if (error == null) overlay = Overlay.None
                }
            },
            onDismiss = { overlay = Overlay.None; nameError = null },
        )

        NameSheet(
            visible = overlay == Overlay.NewFolder,
            title = "New folder",
            initial = "",
            actionLabel = "Create",
            selectStemOnly = false,
            error = nameError,
            onSubmit = { name ->
                browse.createFolder(name) { error ->
                    nameError = error
                    if (error == null) overlay = Overlay.None
                }
            },
            onDismiss = { overlay = Overlay.None; nameError = null },
        )

        NameSheet(
            visible = overlay == Overlay.Rename,
            title = "Rename",
            initial = renameTarget?.name.orEmpty(),
            actionLabel = "Rename",
            selectStemOnly = true,
            error = nameError,
            onSubmit = { name ->
                renameTarget?.let { target ->
                    browse.rename(target, name) { error ->
                        nameError = error
                        if (error == null) {
                            overlay = Overlay.None
                            browse.clearSelection()
                        }
                    }
                }
            },
            onDismiss = { overlay = Overlay.None; nameError = null },
        )

        PropertiesSheet(
            visible = overlay == Overlay.Properties,
            node = propertiesTarget,
            onDismiss = { overlay = Overlay.None },
            onOpenLocation = { path ->
                overlay = Overlay.None
                go(Destination.Folder(path))
            },
        )

        DeleteSheet(
            visible = deleteStage is DeleteController.Stage.Confirming,
            plan = (deleteStage as? DeleteController.Stage.Confirming)?.plan,
            onConfirm = { deletes.confirm() },
            onDismiss = { deletes.dismiss() },
        )

        // Deletion is no longer instantaneous-or-invisible. A large selection
        // reports what it is doing, which is the difference between "working"
        // and "broken".
        (deleteStage as? DeleteController.Stage.Working)?.let { working ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Space.near)
                    .padding(bottom = if (operation != null) 96.dp else 0.dp),
            ) {
                com.filish.feature.operations.DeleteProgress(working)
            }
        }

        ConflictSheet(
            pending = conflict,
            onAnswer = { policy, applyToRest ->
                conflict?.answer?.complete(
                    com.filish.core.fs.ops.OperationEngine.ConflictAnswer(policy, applyToRest),
                )
            },
            onCancel = {
                conflict?.answer?.complete(
                    com.filish.core.fs.ops.OperationEngine.ConflictAnswer(
                        ConflictPolicy.Skip, applyToRest = true,
                    ),
                )
            },
        )

        // A single-selection long-press menu, raised from the ledger's More.
        com.filish.feature.browse.MoreSheet(
            visible = overlay == Overlay.More,
            nodes = state.selection.selectedNodes,
            pinned = settings.pinnedPaths,
            onRename = {
                renameTarget = state.selection.selectedNodes.firstOrNull()
                nameError = null
                overlay = Overlay.Rename
            },
            onProperties = {
                propertiesTarget = state.selection.selectedNodes.firstOrNull()
                overlay = Overlay.Properties
            },
            onPin = { path -> scope.launch { graph.settings.togglePinned(path) } },
            onDismiss = { overlay = Overlay.None },
        )

        if (!opened) {
            com.filish.feature.splash.Opening(ready = ready, onFinished = { opened = true })
        }
    }
}

private data class ReportState(
    val message: String,
    val detail: String? = null,
    val severity: Severity? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * What the user is told after a delete.
 *
 * Every branch says something. The case that matters most is the last one:
 * an operation that achieved nothing now says so, with the platform's reason,
 * instead of leaving the interface unchanged and the user guessing.
 */
private fun reportForDelete(r: DeleteController.Result): ReportState = when {
    r.blockedReason != null -> ReportState(
        "Nothing was deleted",
        r.blockedReason,
        Severity.Problem,
    )

    r.cancelled && r.total == 0 -> ReportState(
        "Delete cancelled",
        "Nothing was moved or removed.",
        Severity.Caution,
    )

    r.cancelled -> ReportState(
        "Delete stopped early",
        "${Format.plural(r.total, "item", "items")} had already gone to the trash.",
        Severity.Caution,
    )

    r.didNothing && r.failed.isNotEmpty() -> ReportState(
        "Nothing could be deleted",
        r.failed.first().reason,
        Severity.Problem,
    )

    r.failed.isEmpty() && r.trashed > 0 -> ReportState(
        "${Format.plural(r.trashed, "item", "items")} moved to trash",
        if (r.expiresAtMillis > 0) {
            "${Format.size(r.bytesFreed)} freed. Your device removes them permanently on " +
                Format.absoluteDate(r.expiresAtMillis) + "."
        } else {
            "${Format.size(r.bytesFreed)} freed. They can be restored from your device's trash."
        },
    )

    r.failed.isEmpty() -> ReportState(
        "${Format.plural(r.deleted, "item", "items")} deleted",
        "${Format.size(r.bytesFreed)} freed.",
    )

    else -> ReportState(
        "${r.total} of ${r.total + r.failed.size} deleted",
        r.failed.first().reason,
        Severity.Problem,
    )
}

private fun reportFor(result: com.filish.core.fs.ops.OperationResult): ReportState = when {
    result.cancelled -> ReportState(
        "${result.kind.verb} stopped",
        "${Format.plural(result.succeeded, "item", "items")} had already been " +
            "${result.kind.past.lowercase()}.",
        Severity.Caution,
    )
    result.isClean && result.skipped == 0 -> ReportState(
        "${result.kind.past} ${Format.plural(result.succeeded, "item", "items")}",
        result.destination?.let { "To ${File(it).name}." },
    )
    result.failed.isEmpty() -> ReportState(
        "${result.kind.past} ${result.succeeded}, skipped ${result.skipped}",
        null,
    )
    else -> ReportState(
        "${result.kind.past} ${result.succeeded} of ${result.total}",
        result.failed.firstOrNull()?.let { "${File(it.path).name}: ${it.reason}" },
        Severity.Problem,
    )
}

/**
 * Hands a file to whatever can open it.
 *
 * FILISH opens images, video, audio, PDFs and text itself; this is the path
 * for everything else. A content URI through FileProvider rather than a
 * file:// URI, which Android has refused since Nougat.
 */
private fun openFile(context: android.content.Context, node: FileNode) {
    val viewer = com.filish.feature.viewer.ViewerRoute.of(node)
    if (viewer != null) {
        com.filish.feature.viewer.ViewerHost.open(context, node)
        return
    }
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.shareprovider", File(node.path),
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, com.filish.core.model.Kinds.mimeOf(node.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${node.name}"))
    }
}

private fun shareNodes(context: android.content.Context, nodes: List<FileNode>) {
    val files = nodes.filter { !it.isDirectory }
    if (files.isEmpty()) return
    runCatching {
        val uris = ArrayList(
            files.map {
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.shareprovider", File(it.path),
                )
            },
        )
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = com.filish.core.model.Kinds.mimeOf(files.first().name)
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share"))
    }
}

/**
 * How one destination replaces another.
 *
 * Direction is the whole point. Going deeper, the new screen enters from the
 * trailing edge while the old one recedes slightly and fades - the outgoing
 * screen moves less than the incoming one, which reads as depth rather than
 * as a carousel. Coming back is the exact inverse, so the spatial claim stays
 * consistent: back really does undo the movement forward.
 *
 * Under reduced motion both become a cross-fade. The positional claim is
 * exactly what reduced motion exists to remove.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun transitionFor(
    direction: NavDirection,
    reduceMotion: Boolean,
): androidx.compose.animation.ContentTransform {
    if (reduceMotion) {
        return fadeIn(tween(Motion.REDUCED)) togetherWith fadeOut(tween(Motion.REDUCED))
    }
    return when (direction) {
        NavDirection.Deeper ->
            (
                slideInHorizontally(tween(Motion.BASE, easing = Motion.enter)) { it / 3 } +
                    fadeIn(tween(Motion.BASE, easing = Motion.enter))
                ) togetherWith (
                scaleOut(tween(Motion.BASE, easing = Motion.enter), targetScale = 0.97f) +
                    fadeOut(tween(Motion.QUICK))
                )

        NavDirection.Back ->
            (
                scaleIn(tween(Motion.BASE, easing = Motion.enter), initialScale = 0.97f) +
                    fadeIn(tween(Motion.BASE, easing = Motion.enter))
                ) togetherWith (
                slideOutHorizontally(tween(Motion.BASE, easing = Motion.exit)) { it / 3 } +
                    fadeOut(tween(Motion.QUICK))
                )

        NavDirection.Lateral ->
            fadeIn(tween(Motion.BASE)) togetherWith fadeOut(tween(Motion.QUICK))
    }
}
