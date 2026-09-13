package com.filish.feature.viewer

import android.content.Context
import android.content.Intent
import com.filish.core.model.FileKind
import com.filish.core.model.FileNode

/** Which built-in viewer opens a file, if any. */
enum class ViewerRoute {
    Image, Video, Audio, Document, Text;

    companion object {
        fun of(node: FileNode): ViewerRoute? = when (node.kind) {
            FileKind.Image, FileKind.RawImage -> Image
            FileKind.Video -> Video
            FileKind.Audio -> Audio
            FileKind.Pdf -> Document
            FileKind.Text, FileKind.Code -> Text
            else -> null
        }
    }
}

/**
 * Opening a file inside FILISH.
 *
 * A file manager that hands every photograph to another application is not
 * really a file manager - it is a launcher with a directory listing. Opening
 * a picture, playing a video, reading a PDF are all things people do *while
 * managing files*, usually to decide whether to keep them, and bouncing out
 * to another app loses the context that made the decision possible.
 *
 * The viewers run in their own activity rather than as a screen in the
 * browser's stack. They need a different window entirely - immersive, no
 * system bars, their own orientation behaviour - and the system back stack
 * then does the right thing for free.
 */
object ViewerHost {

    const val EXTRA_PATH = "com.filish.viewer.path"
    const val EXTRA_ROUTE = "com.filish.viewer.route"

    fun open(context: Context, node: FileNode) {
        val route = ViewerRoute.of(node) ?: return
        val intent = Intent(context, ViewerActivity::class.java).apply {
            putExtra(EXTRA_PATH, node.path)
            putExtra(EXTRA_ROUTE, route.name)
        }
        runCatching { context.startActivity(intent) }
    }
}
