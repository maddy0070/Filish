package com.filish.feature.viewer.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.filish.core.media.MediaFacts
import com.filish.core.media.MediaMetadata
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Motion
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Indeterminate
import com.filish.design.component.ProblemState
import com.filish.feature.viewer.ViewerChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * The image viewer.
 *
 * ---------------------------------------------------------------------------
 * Decoding
 *
 * Loaded at roughly screen resolution rather than at full size. A modern phone
 * photograph is 50 megapixels; as an ARGB bitmap that is 200 MB, which is an
 * out-of-memory kill on most devices, to fill a screen that holds about two
 * megapixels. The decode is sampled down to the display and a second, sharper
 * decode is only worth doing if the user zooms - which is why zoom is capped
 * at a level the sampled bitmap still supports.
 *
 * ---------------------------------------------------------------------------
 * Gestures
 *
 * Pinch to zoom, drag to pan, double-tap to toggle between fit and a useful
 * magnification. Double-tap zooms *to the point tapped* rather than to the
 * centre, because the thing the user wants a closer look at is under their
 * finger, not in the middle of the picture.
 *
 * Panning is clamped so the image cannot be flung off screen and lost. A
 * single tap hides the chrome, because the picture is the point.
 */
@Composable
fun ImageViewer(
    node: FileNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = Filish.type
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    var bitmap by remember(node.path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(node.path) { mutableStateOf(false) }
    var facts by remember(node.path) { mutableStateOf(MediaFacts()) }
    var chromeVisible by remember { mutableStateOf(true) }

    var scale by remember(node.path) { mutableFloatStateOf(1f) }
    var offset by remember(node.path) { mutableStateOf(Offset.Zero) }

    val targetPx = with(density) {
        maxOf(configuration.screenWidthDp.dp.roundToPx(), configuration.screenHeightDp.dp.roundToPx())
    }

    LaunchedEffect(node.path, targetPx) {
        facts = MediaMetadata.read(node.path, node.kind)
        val decoded = withContext(Dispatchers.IO) { decodeForScreen(node.path, targetPx) }
        if (decoded == null) failed = true else bitmap = decoded
    }

    val animatedScale by animateFloatAsState(
        scale,
        if (Filish.a11y.reduceMotion) Motion.reduced() else Motion.base(),
        label = "zoom",
    )

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(node.path) {
                detectTapGestures(
                    onTap = { chromeVisible = !chromeVisible },
                    onDoubleTap = { tap ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.6f
                            // Zoom toward the point under the finger.
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            offset = (centre - tap) * (scale - 1f)
                            chromeVisible = false
                        }
                    },
                )
            }
            .pointerInput(node.path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 6f)
                    // Below fit scale there is nothing to pan to, so panning
                    // is disabled rather than letting the image drift.
                    if (next <= 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = next
                        val maxX = size.width * (next - 1f) / 2f
                        val maxY = size.height * (next - 1f) / 2f
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                    }
                    if (abs(zoom - 1f) > 0.01f) chromeVisible = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            failed -> ProblemState(
                what = "This image cannot be shown",
                why = "The file may be incomplete, or in a format this device's decoder " +
                    "does not support.",
                whatNext = "Its details are still readable from the file list.",
            )

            bitmap == null -> Box(Modifier.fillMaxWidth()) { Indeterminate(active = true) }

            else -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = node.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }

        ViewerChrome(
            visible = chromeVisible,
            title = node.name,
            subtitle = listOfNotNull(
                facts.dimensions,
                Format.size(node.size),
                facts.megapixels,
            ).joinToString("  ·  "),
            onClose = onClose,
            bottom = if (scale > 1.05f) {
                {
                    Column(Modifier.fillMaxWidth()) {
                        BasicTextCompat(
                            "${(animatedScale * 100).toInt()}%  ·  double-tap to fit",
                            type.meta.copy(color = Color.White.copy(alpha = 0.75f)),
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}

/**
 * Decodes at about display resolution.
 *
 * The bounds pass reads the header only. inSampleSize then reduces by the
 * largest power of two that still leaves the image at or above the target,
 * which is the only reduction BitmapFactory does without allocating the full
 * image first. ARGB_8888 rather than RGB_565 here - unlike a thumbnail, this
 * is the picture itself, and banding in a sky is visible at full screen.
 */
private fun decodeForScreen(path: String, targetPx: Int): Bitmap? {
    val file = File(path)
    if (!file.exists() || file.length() == 0L) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeFile(path, bounds) }

    val opts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sample = 1
            var w = bounds.outWidth
            var h = bounds.outHeight
            while (w / 2 >= targetPx && h / 2 >= targetPx) {
                w /= 2; h /= 2; sample *= 2
            }
            inSampleSize = sample
        }
    }

    val raw = runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
        ?: runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.media.ThumbnailUtils.createImageThumbnail(
                    file, android.util.Size(targetPx, targetPx), null,
                )
            } else {
                null
            }
        }.getOrNull()
        ?: return null

    return applyRotation(path, raw)
}

private fun applyRotation(path: String, bitmap: Bitmap): Bitmap {
    val degrees = runCatching {
        when (
            androidx.exifinterface.media.ExifInterface(path).getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return bitmap
    return runCatching {
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }.getOrDefault(bitmap)
}
