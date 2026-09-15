package com.filish.core.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.filish.core.model.GroupKey
import com.filish.core.model.SortDirection
import com.filish.core.model.SortKey
import com.filish.core.model.SortSpec
import com.filish.core.model.ViewMode
import com.filish.design.Density
import com.filish.design.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("filish")

/**
 * Everything FILISH remembers.
 *
 * The shape of this class is the settings architecture. Rather than a flat
 * bag of switches, preferences are grouped by the *question they answer*,
 * which is how the settings screen is organised too: Appearance, Files,
 * Operations, Storage, Privacy, Accessibility. A setting that does not
 * obviously belong to one of those groups is usually a setting that should
 * not exist.
 *
 * Notably absent, on purpose:
 *
 *   - No "enable animations" master switch. Reduced motion already covers the
 *     real need, and a second control that does almost the same thing makes
 *     both harder to find.
 *   - No thumbnail-quality slider. Quality is derived from the row size and
 *     the device's heap; a user cannot reasonably predict what "medium" costs
 *     them, so asking is passing our problem to them.
 *   - No analytics, crash-reporting or telemetry toggle, because there is
 *     nothing to toggle. FILISH has no INTERNET permission.
 */
data class FilishSettings(
    // Appearance
    val theme: ThemeChoice = ThemeChoice.System,
    val density: Density = Density.Comfortable,
    val viewMode: ViewMode = ViewMode.List,
    val showThumbnails: Boolean = true,
    /**
     * Media signatures in the spine: a photograph's mark tinted with tones
     * sampled from the photograph itself.
     *
     * OFF BY DEFAULT and deliberately separate from [showThumbnails]. It is
     * the only part of the browse list that costs a decode, and the design
     * record requires it to be measured on real hardware with a real camera
     * roll before it is turned on for everyone. The fallback - kind tints - is
     * a complete design on its own, not a degraded one, so shipping with this
     * off costs nothing but richness.
     */
    val spineMedia: Boolean = false,
    /** Debug QA readout over the browse list. Only reachable in debug builds. */
    val qaOverlay: Boolean = false,

    // Files
    val showHidden: Boolean = false,
    val showExtensions: Boolean = true,
    val sortKey: SortKey = SortKey.Name,
    val sortDescending: Boolean = false,
    val foldersFirst: Boolean = true,
    val groupBy: GroupKey = GroupKey.None,
    /**
     * Measure folder sizes automatically while browsing.
     *
     * Default on, because it is the thing conventional file managers refuse to
     * do and the reason FILISH exists. Off is offered honestly: on a device
     * with very deep trees the background walks cost battery, and some people
     * would rather ask per folder.
     */
    val autoMeasureFolders: Boolean = true,
    val showFolderItemCounts: Boolean = true,

    // Operations
    val confirmDelete: Boolean = true,
    /** Skip the extra confirmation when the delete is provably reversible.
     *  Two dialogs to trash one photo is a tax, not a safeguard. */
    val skipConfirmWhenRecoverable: Boolean = true,
    val defaultConflictAsk: Boolean = true,

    // Storage
    val analysisIncludesHidden: Boolean = false,

    // Accessibility
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,

    // Locations the user pinned
    val pinnedPaths: Set<String> = emptySet(),
    /** Places the user has been, most recent first. Capped - an unbounded
     *  history is a privacy liability and a performance one. */
    val recentPaths: List<String> = emptyList(),
) {
    val sortSpec: SortSpec
        get() = SortSpec(
            key = sortKey,
            direction = if (sortDescending) SortDirection.Descending else SortDirection.Ascending,
            foldersFirst = foldersFirst,
        )
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val density = stringPreferencesKey("density")
        val viewMode = stringPreferencesKey("view_mode")
        val showThumbnails = booleanPreferencesKey("show_thumbnails")
        val spineMedia = booleanPreferencesKey("spine_media")
        val qaOverlay = booleanPreferencesKey("qa_overlay")
        val showHidden = booleanPreferencesKey("show_hidden")
        val showExtensions = booleanPreferencesKey("show_extensions")
        val sortKey = stringPreferencesKey("sort_key")
        val sortDescending = booleanPreferencesKey("sort_desc")
        val foldersFirst = booleanPreferencesKey("folders_first")
        val groupBy = stringPreferencesKey("group_by")
        val autoMeasure = booleanPreferencesKey("auto_measure")
        val folderCounts = booleanPreferencesKey("folder_counts")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val skipConfirmRecoverable = booleanPreferencesKey("skip_confirm_recoverable")
        val conflictAsk = booleanPreferencesKey("conflict_ask")
        val analysisHidden = booleanPreferencesKey("analysis_hidden")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val highContrast = booleanPreferencesKey("high_contrast")
        val pinned = stringSetPreferencesKey("pinned")
        val recents = stringPreferencesKey("recents")
        val schema = intPreferencesKey("schema")
    }

    private companion object {
        private const val RECENT_LIMIT = 12
        private const val RECENT_SEPARATOR = "\n"
    }

    val settings: Flow<FilishSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = FilishSettings(
        theme = this[Keys.theme]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() }
            ?: ThemeChoice.System,
        density = this[Keys.density]?.let { runCatching { Density.valueOf(it) }.getOrNull() }
            ?: Density.Comfortable,
        viewMode = this[Keys.viewMode]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() }
            ?: ViewMode.List,
        showThumbnails = this[Keys.showThumbnails] ?: true,
        spineMedia = this[Keys.spineMedia] ?: false,
        qaOverlay = this[Keys.qaOverlay] ?: false,
        showHidden = this[Keys.showHidden] ?: false,
        showExtensions = this[Keys.showExtensions] ?: true,
        sortKey = this[Keys.sortKey]?.let { runCatching { SortKey.valueOf(it) }.getOrNull() }
            ?: SortKey.Name,
        sortDescending = this[Keys.sortDescending] ?: false,
        foldersFirst = this[Keys.foldersFirst] ?: true,
        groupBy = this[Keys.groupBy]?.let { runCatching { GroupKey.valueOf(it) }.getOrNull() }
            ?: GroupKey.None,
        autoMeasureFolders = this[Keys.autoMeasure] ?: true,
        showFolderItemCounts = this[Keys.folderCounts] ?: true,
        confirmDelete = this[Keys.confirmDelete] ?: true,
        skipConfirmWhenRecoverable = this[Keys.skipConfirmRecoverable] ?: true,
        defaultConflictAsk = this[Keys.conflictAsk] ?: true,
        analysisIncludesHidden = this[Keys.analysisHidden] ?: false,
        reduceMotion = this[Keys.reduceMotion] ?: false,
        highContrast = this[Keys.highContrast] ?: false,
        pinnedPaths = this[Keys.pinned] ?: emptySet(),
        recentPaths = this[Keys.recents]?.split(RECENT_SEPARATOR)?.filter { it.isNotBlank() }
            ?: emptyList(),
    )

    suspend fun setTheme(v: ThemeChoice) = put { it[Keys.theme] = v.name }
    suspend fun setDensity(v: Density) = put { it[Keys.density] = v.name }
    suspend fun setViewMode(v: ViewMode) = put { it[Keys.viewMode] = v.name }
    suspend fun setShowThumbnails(v: Boolean) = put { it[Keys.showThumbnails] = v }

    suspend fun setSpineMedia(v: Boolean) = put { it[Keys.spineMedia] = v }

    suspend fun setQaOverlay(v: Boolean) = put { it[Keys.qaOverlay] = v }
    suspend fun setShowHidden(v: Boolean) = put { it[Keys.showHidden] = v }
    suspend fun setShowExtensions(v: Boolean) = put { it[Keys.showExtensions] = v }
    suspend fun setSort(spec: SortSpec) = put {
        it[Keys.sortKey] = spec.key.name
        it[Keys.sortDescending] = spec.descending
        it[Keys.foldersFirst] = spec.foldersFirst
    }
    suspend fun setGroupBy(v: GroupKey) = put { it[Keys.groupBy] = v.name }
    suspend fun setAutoMeasure(v: Boolean) = put { it[Keys.autoMeasure] = v }
    suspend fun setFolderCounts(v: Boolean) = put { it[Keys.folderCounts] = v }
    suspend fun setConfirmDelete(v: Boolean) = put { it[Keys.confirmDelete] = v }
    suspend fun setSkipConfirmRecoverable(v: Boolean) = put { it[Keys.skipConfirmRecoverable] = v }
    suspend fun setConflictAsk(v: Boolean) = put { it[Keys.conflictAsk] = v }
    suspend fun setAnalysisHidden(v: Boolean) = put { it[Keys.analysisHidden] = v }
    suspend fun setReduceMotion(v: Boolean) = put { it[Keys.reduceMotion] = v }
    suspend fun setHighContrast(v: Boolean) = put { it[Keys.highContrast] = v }

    suspend fun togglePinned(path: String) = put { prefs ->
        val current = prefs[Keys.pinned] ?: emptySet()
        prefs[Keys.pinned] = if (path in current) current - path else current + path
    }

    /** Records a visit. Most recent first, deduplicated, and capped. */
    suspend fun rememberVisit(path: String) = put { prefs ->
        val current = prefs[Keys.recents]?.split(RECENT_SEPARATOR)?.filter { it.isNotBlank() }
            ?: emptyList()
        val next = (listOf(path) + current.filter { it != path }).take(RECENT_LIMIT)
        prefs[Keys.recents] = next.joinToString(RECENT_SEPARATOR)
    }

    suspend fun clearRecents() = put { it[Keys.recents] = "" }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs ->
            prefs[Keys.schema] = 1
            block(prefs)
        }
    }
}
