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
import com.filish.core.fs.ops.DeleteEngine
import com.filish.core.fs.ops.OperationKind
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.core.model.ViewMode
import com.filish.core.settings.FilishSettings
import com.filish.design.Filish
import com.filish.design.Motion
import com.filish.design.Space
import com.filish.feature.browse.BrowseScreen
import com.filish.feature.browse.BrowseViewModel
import com.filish.feature.browse.FilterSheet
import com.filish.feature.browse.NameSheet
import com.filish.feature.browse.OrderSheet
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
private enum class Overlay { None, Order, Filter, NewFolder, Rename, Properties, Delete, More }

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
    var deletePlan by remember { mutableStateOf<DeleteEngine.Plan?>(null) }
    var report by remember { mutableStateOf<ReportState?>(null) }

    // System consent for trashing, which MediaProvider requires and FILISH
    // cannot bypass.
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val plan = deletePlan
        deletePlan = null
        overlay = Overlay.None
        if (result.resultCode == android.app.Activity.RESULT_OK && plan != null) {
            scope.launch {
                val (uris, _) = graph.deletes.resolveTrashable(plan)
                val confirmed = graph.deletes.verifyTrashed(uris)
                val expiry = graph.deletes.expiryOf(uris)
                browse.clearSelection()
                browse.refresh()
                report = ReportState(
                    message = "${Format.plural(confirmed, "item", "items")} moved to trash",
                    detail = if (expiry > 0) {
                        "Your device will remove them permanently on " +
                            Format.absoluteDate(expiry) + "."
                    } else {
                        "They can be restored from your device's trash."
                    },
                )
            }
        }
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
                        onSortTap = { overlay = Overlay.Order },
                        onFilterTap = { overlay = Overlay.Filter },
                        onViewToggle = {
                            browse.setViewMode(
                                if (state.viewMode == ViewMode.List) ViewMode.Grid else ViewMode.List,
                            )
                        },
                        onNewFolder = { nameError = null; overlay = Overlay.NewFolder },
                        onCopy = { browse.stage(move = false) },
                        onMove = { browse.stage(move = true) },
                        onDelete = {
                            scope.launch {
                                deletePlan = graph.deletes.plan(state.selection.selectedNodes)
                                val plan = deletePlan ?: return@launch
                                // A provably recoverable delete goes straight
                                // to the platform's own consent dialog rather
                                // than stacking ours in front of it.
                                if (plan.isFullyRecoverable && settings.skipConfirmWhenRecoverable) {
                                    launchTrash(graph, plan, trashLauncher) { overlay = Overlay.Delete }
                                } else {
                                    overlay = Overlay.Delete
                                }
                            }
                        },
                        onShare = { shareNodes(context, state.selection.selectedNodes) },
                        onMore = { overlay = Overlay.More },
                        onPaste = {
                            val staged = clipboard
                            val destination = state.path
                            browse.clearClipboard()
                            scope.launch {
                                val kind = if (staged.move) OperationKind.Move else OperationKind.Copy
                                val refusal = graph.operations.check(
                                    staged.nodes, destination, kind, volumes,
                                )
                                if (refusal != null) {
                                    report = ReportState(
                                        message = "Cannot ${kind.verb.lowercase()} here",
                                        detail = refusal.explanation,
                                        severity = Severity.Problem,
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
                        },
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
                        onBack = { back() },
                    )

                    is Destination.Settings -> com.filish.feature.settings.SettingsScreen(
                        settings = settings,
                        store = graph.settings,
                        displayFaceAvailable = Filish.type.displayFaceAvailable,
                        onBack = { back() },
                    )

                    else -> Box(Modifier.fillMaxSize())
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

        OrderSheet(
            visible = overlay == Overlay.Order,
            sort = state.sort,
            group = state.group,
            onSort = { browse.setSort(it) },
            onGroup = { browse.setGroup(it) },
            onDismiss = { overlay = Overlay.None },
        )

        FilterSheet(
            visible = overlay == Overlay.Filter,
            filter = state.filter,
            items = state.items,
            onFilter = { browse.setFilter(it) },
            onDismiss = { overlay = Overlay.None },
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
            visible = overlay == Overlay.Delete,
            plan = deletePlan,
            onConfirm = {
                val plan = deletePlan
                if (plan == null) { overlay = Overlay.None; return@DeleteSheet }
                scope.launch {
                    if (plan.trashableUris.isNotEmpty() || plan.unindexedPaths.isNotEmpty()) {
                        launchTrash(graph, plan, trashLauncher) { }
                    } else {
                        overlay = Overlay.None
                        val outcome = graph.deletes.deletePermanently(plan.permanentPaths)
                        browse.clearSelection()
                        browse.refresh()
                        deletePlan = null
                        report = if (outcome.allSucceeded) {
                            ReportState(
                                "${Format.plural(outcome.deleted, "item", "items")} deleted",
                                "${Format.size(plan.totalBytes)} freed.",
                            )
                        } else {
                            ReportState(
                                "${outcome.deleted} deleted, ${outcome.failed.size} could not be",
                                outcome.failed.firstOrNull()?.let {
                                    "${File(it.path).name}: ${it.reason}"
                                },
                                Severity.Problem,
                            )
                        }
                    }
                }
            },
            onDismiss = { overlay = Overlay.None; deletePlan = null },
        )

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

private suspend fun launchTrash(
    graph: com.filish.FilishApp.Graph,
    plan: DeleteEngine.Plan,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    onUnavailable: () -> Unit,
) {
    val (uris, _) = graph.deletes.resolveTrashable(plan)
    val sender = graph.deletes.trashRequest(uris)
    if (sender == null) {
        onUnavailable()
        return
    }
    runCatching { launcher.launch(IntentSenderRequest.Builder(sender).build()) }
        .onFailure { onUnavailable() }
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
