package com.filish.feature.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.filish.core.model.FileNode
import com.filish.core.settings.FilishSettings
import com.filish.design.Accessibility
import com.filish.design.FilishTheme
import com.filish.filish
import java.io.File

/**
 * The window FILISH's viewers live in.
 *
 * Separate from the browser because a viewer wants a genuinely different
 * window: edge to edge with no system chrome, free to rotate, and dismissible
 * with the system back gesture without disturbing where the user was
 * browsing. Rotation is handled in-configuration rather than by recreating,
 * so turning the phone during a video does not restart playback.
 */
class ViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val path = intent.getStringExtra(ViewerHost.EXTRA_PATH)
        val route = intent.getStringExtra(ViewerHost.EXTRA_ROUTE)
            ?.let { runCatching { ViewerRoute.valueOf(it) }.getOrNull() }

        if (path == null || route == null) {
            finish()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            finish()
            return
        }

        val node = FileNode.of(file)
        val store = applicationContext.filish.settings

        setContent {
            val settings by store.settings.collectAsState(initial = FilishSettings())
            FilishTheme(
                themeChoice = settings.theme,
                density = settings.density,
                accessibility = Accessibility(
                    reduceMotion = settings.reduceMotion,
                    highContrast = settings.highContrast,
                ),
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    when (route) {
                        ViewerRoute.Image -> com.filish.feature.viewer.image.ImageViewer(
                            node = node,
                            onClose = { finish() },
                        )
                        ViewerRoute.Video -> com.filish.feature.viewer.video.VideoViewer(
                            node = node,
                            onClose = { finish() },
                        )
                        ViewerRoute.Audio -> com.filish.feature.viewer.audio.AudioViewer(
                            node = node,
                            onClose = { finish() },
                        )
                        ViewerRoute.Document, ViewerRoute.Text ->
                            com.filish.feature.viewer.doc.DocumentViewer(
                                node = node,
                                onClose = { finish() },
                            )
                    }
                }
            }
        }
    }
}
