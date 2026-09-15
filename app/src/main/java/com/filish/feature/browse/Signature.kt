package com.filish.feature.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.filish.core.model.FileNode
import com.filish.filish

/**
 * The media signature for one object, if it can be had cheaply.
 *
 * ===========================================================================
 * The decode budget
 * ===========================================================================
 *
 * This is the ONLY part of the browse list that costs a decode, which is why
 * it sits behind its own setting and is off by default.
 *
 * [SIGNATURE_PX] is 32. That is not a thumbnail - it is far too small to look
 * at - and that is the point: BitmapFactory's sampleSize means a 32px target
 * reads roughly 1/64th as many pixels as a 256px one for a typical camera
 * JPEG, and the three averages taken from it are indistinguishable from the
 * three averages of the full image. The mark is 26dp wide; nothing finer than
 * a tonal cast can survive into it anyway.
 *
 * Once derived, a signature is three ints and lives in [MediaSignature]'s
 * cache. The bitmap is not retained by this code and may be evicted
 * immediately - a signature never needs to be computed twice for the same
 * file, so scrolling back over a row costs nothing at all.
 *
 * ===========================================================================
 * The fallback is a design, not a degradation
 * ===========================================================================
 *
 * Returning null is completely normal: a PDF has no signature, a folder has
 * none today, and a photograph has none until its signature has been derived.
 * The mark then draws its kind tint, which is a finished answer. Nothing in
 * the row waits, shows a placeholder, or reserves space for something that may
 * not arrive.
 */
const val SIGNATURE_PX = 32

@Composable
fun rememberSignature(node: FileNode, enabled: Boolean): List<Color>? {
    val context = LocalContext.current
    val app = remember(context) { context.filish }
    val wants = enabled && node.kind.isMedia && !node.isDirectory

    var tones by remember(node.path, node.lastModified) {
        mutableStateOf(
            if (wants) app.signatures.cached(node.path, node.lastModified) else null,
        )
    }

    LaunchedEffect(node.path, node.lastModified, wants) {
        if (!wants || tones != null) return@LaunchedEffect
        // Ask the loader for a signature-sized bitmap, then reduce it. The
        // coroutine is cancelled with the composition, so scrolling past a row
        // before its decode starts costs nothing.
        app.thumbnails.load(node.path, node.kind, SIGNATURE_PX, node.lastModified)
        tones = app.signatures.derive(node.path, node.lastModified, SIGNATURE_PX)
    }

    return tones?.asColors()
}
