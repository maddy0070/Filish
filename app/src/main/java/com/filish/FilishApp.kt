package com.filish

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.filish.core.fs.DirectoryLister
import com.filish.core.fs.MediaStoreIndex
import com.filish.core.fs.SizeResolver
import com.filish.core.fs.VolumeRegistry
import com.filish.core.fs.ops.DeleteEngine
import com.filish.core.fs.ops.OperationEngine
import com.filish.core.intel.StorageAnalyzer
import com.filish.core.media.ThumbnailLoader
import com.filish.core.search.SearchEngine
import com.filish.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import kotlinx.coroutines.Dispatchers

/**
 * The application's object graph.
 *
 * A hand-written service locator rather than Hilt or Koin. FILISH has around
 * a dozen singletons with a fixed, acyclic dependency graph and no need for
 * scoping, qualifiers or test substitution at the framework level. A DI
 * container would add an annotation processor, several seconds to every
 * build, and a layer of indirection in exchange for solving a problem this
 * application does not have. Premature architecture is still premature when
 * it is fashionable.
 */
class FilishApp : Application() {

    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }

    /**
     * Memory pressure means dropping thumbnails immediately.
     *
     * A file manager is frequently the app in the background while the user
     * is in a camera or a video editor. Being killed mid-operation is the
     * worst outcome available, so caches are surrendered early and without
     * argument.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ->
                graph.thumbnails.trim(aggressive = true)
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
                graph.thumbnails.trim(aggressive = false)
        }
    }

    @Deprecated("Retained for pre-34 devices, which still call it.")
    override fun onLowMemory() {
        super.onLowMemory()
        graph.thumbnails.trim(aggressive = true)
        graph.sizes.invalidateAll()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    class Graph(context: Context) {
        /** Outlives any single screen: operations must survive navigation. */
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val settings = SettingsStore(context)
        val lister = DirectoryLister()
        val sizes = SizeResolver()
        val volumes = VolumeRegistry(context)
        val mediaIndex = MediaStoreIndex(context)
        val thumbnails = ThumbnailLoader(context)
        val search = SearchEngine()
        val analyzer = StorageAnalyzer()
        val deletes = DeleteEngine(context, mediaIndex)
        val operations = OperationEngine(appScope, sizes, mediaIndex)
    }
}

/** Reaches the graph from anywhere with a Context. */
val Context.filish: FilishApp.Graph
    get() = (applicationContext as FilishApp).graph
