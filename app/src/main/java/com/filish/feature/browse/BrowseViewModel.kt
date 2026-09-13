package com.filish.feature.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filish.core.fs.Listing
import com.filish.core.fs.Volume
import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.FilterSpec
import com.filish.core.model.GroupKey
import com.filish.core.model.MeasuredSize
import com.filish.core.model.NaturalOrder
import com.filish.core.model.SortKey
import com.filish.core.model.SortSpec
import com.filish.core.model.ViewMode
import com.filish.core.settings.FilishSettings
import com.filish.filish
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Files staged for a copy or move, waiting for a destination. */
data class Clipboard(
    val nodes: List<FileNode> = emptyList(),
    val move: Boolean = false,
    val originLabel: String = "",
) {
    val isActive: Boolean get() = nodes.isNotEmpty()
}

/** One row's asynchronously-resolved extras. */
data class RowFacts(
    val measured: MeasuredSize? = null,
    val childCount: Int = -1,
)

data class BrowseState(
    val path: String = "",
    val loading: Boolean = true,
    val listing: Listing? = null,
    /** Ordered, grouped and filtered - what the list actually renders. */
    val rows: List<BrowseRow> = emptyList(),
    val rawCount: Int = 0,
    val hiddenCount: Int = 0,
    val selection: Selection = Selection(),
    val sort: SortSpec = SortSpec(),
    val group: GroupKey = GroupKey.None,
    val filter: FilterSpec = FilterSpec(),
    val viewMode: ViewMode = ViewMode.List,
    val folderFacts: Map<String, RowFacts> = emptyMap(),
    val refinements: List<SelectionRefinement> = emptyList(),
    val volume: Volume? = null,
    val isRoot: Boolean = false,
) {
    val items: List<FileNode>
        get() = rows.mapNotNull { (it as? BrowseRow.Item)?.node }

    val isEmpty: Boolean get() = rows.none { it is BrowseRow.Item }
}

/** A rendered row: either a group heading or an entry. */
sealed interface BrowseRow {
    data class Heading(val label: String, val count: Int, val bytes: Long?) : BrowseRow
    data class Item(val node: FileNode) : BrowseRow
}

class BrowseViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.filish

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private val _clipboard = MutableStateFlow(Clipboard())
    val clipboard: StateFlow<Clipboard> = _clipboard.asStateFlow()

    private var listJob: Job? = null
    private var measureJob: Job? = null
    private var factsJob: Job? = null
    private var settings: FilishSettings = FilishSettings()
    private var volumes: List<Volume> = emptyList()

    init {
        viewModelScope.launch {
            graph.settings.settings.collectLatest { s ->
                val changed = s.showHidden != settings.showHidden ||
                    s.sortSpec != settings.sortSpec ||
                    s.groupBy != settings.groupBy ||
                    s.autoMeasureFolders != settings.autoMeasureFolders
                settings = s
                _state.update {
                    it.copy(
                        sort = s.sortSpec,
                        group = s.groupBy,
                        viewMode = s.viewMode,
                        filter = it.filter.copy(showHidden = s.showHidden),
                    )
                }
                if (changed && _state.value.path.isNotEmpty()) reorder()
            }
        }
        viewModelScope.launch { volumes = graph.volumes.volumes() }
    }

    fun open(path: String) {
        if (_state.value.path == path && _state.value.listing != null) return
        listJob?.cancel()
        measureJob?.cancel()
        factsJob?.cancel()

        _state.update {
            it.copy(
                path = path,
                loading = true,
                listing = null,
                rows = emptyList(),
                selection = Selection(),
                folderFacts = emptyMap(),
                isRoot = volumes.any { v -> v.path == path },
                volume = volumes.firstOrNull { v -> v.contains(path) },
            )
        }

        listJob = viewModelScope.launch {
            val result = graph.lister.list(path)
            _state.update { it.copy(listing = result, loading = false) }
            if (result is Listing.Content) {
                reorder()
                startRowFacts(result.entries)
                graph.settings.rememberVisit(path)
            }
        }
    }

    fun refresh() {
        val path = _state.value.path
        graph.sizes.invalidate(path)
        _state.update { it.copy(listing = null) }
        open(path)
    }

    // ---- ordering ----------------------------------------------------------

    private fun reorder() {
        val listing = _state.value.listing as? Listing.Content ?: return
        val s = _state.value
        val entries = listing.entries

        val visible = entries.filter { s.filter.matches(it) }
        val hidden = entries.size - visible.size

        val sorted = sortItems(visible, s.sort, s.folderFacts)
        val rows = groupRows(sorted, s.group, s.folderFacts)

        _state.update {
            it.copy(
                rows = rows,
                rawCount = entries.size,
                hiddenCount = hidden,
                refinements = Refinements.forListing(sorted),
            )
        }
    }

    private fun sortItems(
        items: List<FileNode>,
        spec: SortSpec,
        facts: Map<String, RowFacts>,
    ): List<FileNode> {
        val base: Comparator<FileNode> = when (spec.key) {
            SortKey.Name -> Comparator { a, b -> NaturalOrder.compare(a.name, b.name) }
            SortKey.Size -> compareBy {
                // A folder sorts by its measured size once known, so sorting
                // by size becomes genuinely useful in a folder of folders
                // rather than parking every directory at zero.
                if (it.isDirectory) facts[it.path]?.measured?.bytes ?: -1L else it.size
            }
            SortKey.Modified -> compareBy { it.lastModified }
            SortKey.Kind -> compareBy<FileNode> { it.kind.ordinal }
                .thenComparator { a, b -> NaturalOrder.compare(a.name, b.name) }
            SortKey.Extension -> compareBy<FileNode> { it.extension }
                .thenComparator { a, b -> NaturalOrder.compare(a.name, b.name) }
            SortKey.Count -> compareBy { facts[it.path]?.childCount ?: -1 }
        }
        val directed = if (spec.descending) base.reversed() else base
        return if (spec.foldersFirst) {
            items.sortedWith(compareByDescending<FileNode> { it.isDirectory }.then(directed))
        } else {
            items.sortedWith(directed)
        }
    }

    private fun groupRows(
        items: List<FileNode>,
        group: GroupKey,
        facts: Map<String, RowFacts>,
    ): List<BrowseRow> {
        if (group == GroupKey.None) return items.map { BrowseRow.Item(it) }

        val now = System.currentTimeMillis()
        val keyed = items.groupBy { node ->
            when (group) {
                GroupKey.Kind -> node.kind.label + if (node.kind == FileKind.Folder) "s" else "s"
                GroupKey.FirstLetter -> node.name.firstOrNull()
                    ?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
                GroupKey.SizeBand -> com.filish.core.intel.SizeBand.of(
                    if (node.isDirectory) facts[node.path]?.measured?.bytes ?: 0L else node.size,
                ).label
                GroupKey.TimeBand -> com.filish.core.intel.AgeBand.of(node.lastModified, now).label
                GroupKey.None -> ""
            }
        }

        val out = ArrayList<BrowseRow>(items.size + keyed.size)
        for ((label, group2) in keyed) {
            val bytes = group2.sumOf {
                if (it.isDirectory) facts[it.path]?.measured?.bytes ?: 0L else it.size
            }
            out.add(BrowseRow.Heading(label, group2.size, bytes.takeIf { it > 0 }))
            group2.forEach { out.add(BrowseRow.Item(it)) }
        }
        return out
    }

    fun setSort(spec: SortSpec) {
        _state.update { it.copy(sort = spec) }
        viewModelScope.launch { graph.settings.setSort(spec) }
        reorder()
    }

    fun cycleSort(key: SortKey) = setSort(_state.value.sort.toggled(key))

    fun setGroup(group: GroupKey) {
        _state.update { it.copy(group = group) }
        viewModelScope.launch { graph.settings.setGroupBy(group) }
        reorder()
    }

    fun setFilter(filter: FilterSpec) {
        _state.update { it.copy(filter = filter) }
        reorder()
    }

    fun setViewMode(mode: ViewMode) {
        _state.update { it.copy(viewMode = mode) }
        viewModelScope.launch { graph.settings.setViewMode(mode) }
    }

    fun toggleHidden() {
        val next = !_state.value.filter.showHidden
        _state.update { it.copy(filter = it.filter.copy(showHidden = next)) }
        viewModelScope.launch { graph.settings.setShowHidden(next) }
        reorder()
    }

    // ---- row facts ---------------------------------------------------------

    /**
     * Resolves the extras a folder row wants: how many items, and how big.
     *
     * Sequential and deliberately unhurried. These are nice-to-haves on rows
     * the user may scroll straight past, and they must never compete for I/O
     * with the listing itself or with an operation in flight. Child counts
     * come first because they are cheap and answer the more common question.
     */
    private fun startRowFacts(entries: List<FileNode>) {
        factsJob?.cancel()
        val folders = entries.filter { it.isDirectory }
        if (folders.isEmpty()) return

        factsJob = viewModelScope.launch {
            if (settings.showFolderItemCounts) {
                for (folder in folders) {
                    val n = graph.lister.childCount(folder.path, settings.showHidden)
                    if (n >= 0) {
                        _state.update { s ->
                            val existing = s.folderFacts[folder.path] ?: RowFacts()
                            s.copy(folderFacts = s.folderFacts + (folder.path to existing.copy(childCount = n)))
                        }
                    }
                }
                if (_state.value.sort.key == SortKey.Count) reorder()
            }

            if (settings.autoMeasureFolders) {
                for (folder in folders) {
                    val measured = graph.sizes.measureOnce(folder.path)
                    _state.update { s ->
                        val existing = s.folderFacts[folder.path] ?: RowFacts()
                        s.copy(folderFacts = s.folderFacts + (folder.path to existing.copy(measured = measured)))
                    }
                }
                if (_state.value.sort.key == SortKey.Size ||
                    _state.value.group == GroupKey.SizeBand
                ) {
                    reorder()
                }
            }
        }
    }

    /** Measures one folder on demand, for when auto-measure is off. */
    fun measureFolder(node: FileNode) {
        if (!node.isDirectory) return
        viewModelScope.launch {
            graph.sizes.measure(listOf(node.path)).collect { m ->
                _state.update { s ->
                    val existing = s.folderFacts[node.path] ?: RowFacts()
                    s.copy(folderFacts = s.folderFacts + (node.path to existing.copy(measured = m)))
                }
            }
        }
    }

    // ---- selection ---------------------------------------------------------

    fun toggleSelection(node: FileNode) = applySelection(_state.value.selection.toggle(node))

    fun beginSelection(node: FileNode) {
        if (_state.value.selection.isActive) toggleSelection(node)
        else applySelection(_state.value.selection.toggle(node))
    }

    fun refine(refinement: SelectionRefinement) =
        applySelection(Refinements.apply(refinement, _state.value.items, _state.value.selection))

    fun clearSelection() {
        measureJob?.cancel()
        _state.update { it.copy(selection = Selection()) }
    }

    /**
     * Applies a new selection and restarts its measurement.
     *
     * The measurement is cancelled and restarted on every change rather than
     * being incrementally adjusted. Incremental would be faster in principle
     * and is not worth it: the walk is cached per path, so a re-measure after
     * adding one folder to a selection of ten is nearly free, and the simpler
     * version cannot drift out of sync with what is selected.
     */
    private fun applySelection(selection: Selection) {
        measureJob?.cancel()
        _state.update { it.copy(selection = selection) }
        if (!selection.isActive) return

        if (!selection.needsMeasurement) {
            _state.update {
                it.copy(
                    selection = it.selection.withMeasurement(
                        MeasuredSize(selection.knownFileBytes, selection.fileCount, 0, settled = true),
                    ),
                )
            }
            return
        }

        measureJob = viewModelScope.launch {
            graph.sizes.measure(selection.paths).collect { m ->
                _state.update { s ->
                    if (s.selection.paths == selection.paths) {
                        s.copy(selection = s.selection.withMeasurement(m))
                    } else {
                        s
                    }
                }
            }
        }
    }

    // ---- staging -----------------------------------------------------------

    fun stage(move: Boolean) {
        val sel = _state.value.selection
        if (!sel.isActive) return
        _clipboard.value = Clipboard(
            nodes = sel.selectedNodes,
            move = move,
            originLabel = File(_state.value.path).name.ifEmpty { _state.value.path },
        )
        clearSelection()
    }

    fun clearClipboard() { _clipboard.value = Clipboard() }

    // ---- mutations ---------------------------------------------------------

    fun createFolder(name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val parent = _state.value.path
            val clean = name.trim()
            val error = validateName(clean, parent)
            if (error != null) { onResult(error); return@launch }
            val made = runCatching { File(parent, clean).mkdir() }.getOrDefault(false)
            if (made) {
                graph.sizes.invalidate(parent)
                refresh()
                onResult(null)
            } else {
                onResult("Filish could not create a folder here.")
            }
        }
    }

    fun rename(node: FileNode, newName: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val parent = File(node.path).parent ?: return@launch onResult("Unknown location")
            val clean = newName.trim()
            if (clean == node.name) return@launch onResult(null)
            val error = validateName(clean, parent)
            if (error != null) { onResult(error); return@launch }
            val target = File(parent, clean)
            val ok = runCatching { File(node.path).renameTo(target) }.getOrDefault(false)
            if (ok) {
                graph.mediaIndex.notifyChanged(node.path, target.absolutePath)
                graph.sizes.invalidate(node.path)
                refresh()
                onResult(null)
            } else {
                onResult(
                    if (!File(node.path).exists()) "It no longer exists."
                    else "The system refused the rename.",
                )
            }
        }
    }

    /**
     * Name validation, with the reason stated.
     *
     * "Invalid name" tells the user nothing. Which character, and why, tells
     * them what to type instead.
     */
    private fun validateName(name: String, parentPath: String): String? = when {
        name.isEmpty() -> "A name is needed."
        name == "." || name == ".." -> "That name is reserved by the filesystem."
        name.contains('/') -> "Names cannot contain a slash."
        name.contains('\u0000') -> "That name contains an invalid character."
        // Android's FAT-derived filesystems reject these outright; failing
        // early with a reason beats a cryptic I/O error.
        name.any { it in "\\:*?\"<>|" } ->
            "Names cannot contain \\ : * ? \" < > or |"
        name.toByteArray().size > 255 -> "That name is too long for the filesystem."
        name.endsWith(' ') || name.endsWith('.') ->
            "Names cannot end with a space or a dot."
        File(parentPath, name).exists() -> "Something here is already called that."
        else -> null
    }

    fun volumesNow(): List<Volume> = volumes

    suspend fun refreshVolumes(): List<Volume> {
        volumes = graph.volumes.volumes()
        return volumes
    }
}
