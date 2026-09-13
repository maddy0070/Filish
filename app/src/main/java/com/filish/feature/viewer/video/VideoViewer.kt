package com.filish.feature.viewer.video

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishChip
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.GlyphButton
import com.filish.design.component.ProblemState
import com.filish.design.component.pressable
import com.filish.feature.viewer.ViewerChrome
import kotlinx.coroutines.delay
import java.io.File

/**
 * The video player.
 *
 * ExoPlayer supplies decoding and a surface; every control is FILISH's. The
 * stock PlayerView controller is disabled outright - it is a generic media
 * UI with its own type, its own icons and its own timing, and inheriting it
 * would make the one screen that is mostly chrome look like a different
 * application.
 *
 * ---------------------------------------------------------------------------
 * Decisions worth stating
 *
 * PLAYBACK STOPS WHEN THE SCREEN DOES. A video paused on lifecycle stop, not
 * merely muted. Audio continuing from a video the user navigated away from is
 * a bug in every case where it was not explicitly asked for.
 *
 * THE SCRUBBER IS THE WHOLE WIDTH. Seeking accuracy is limited by the width of
 * the control, so it gets the full width and the timestamps sit beneath it
 * rather than on either side stealing a third of it.
 *
 * UNSUPPORTED CODECS ARE EXPLAINED. Android's decoder support varies by
 * device, and "cannot play this video" is useless. FILISH distinguishes a
 * missing decoder from a damaged file and says which, because one means try
 * another app and the other means the file is gone.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoViewer(
    node: FileNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var chromeVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableStateOf(1f) }

    val player = remember(node.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(File(node.path).toURI().toString()))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) {
                failure = describe(error)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Playback follows the lifecycle. Audio must not continue from a video the
    // user has navigated away from.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (duration <= 0) duration = player.duration.coerceAtLeast(0L)
            delay(180)
        }
    }

    // Chrome retreats on its own during playback, so the video is not framed
    // by controls the user has stopped needing.
    LaunchedEffect(playing, chromeVisible) {
        if (playing && chromeVisible) {
            delay(3_200)
            chromeVisible = false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(node.path) {
                detectTapGestures(onTap = { chromeVisible = !chromeVisible })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (failure == null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ProblemState(
                what = "This video will not play",
                why = failure,
                whatNext = "You can still copy, move or share the file.",
            )
        }

        if (buffering && failure == null) {
            Box(Modifier.fillMaxWidth().align(Alignment.Center)) {
                com.filish.design.component.Indeterminate(active = true)
            }
        }

        ViewerChrome(
            visible = chromeVisible || failure != null,
            title = node.name,
            subtitle = listOfNotNull(
                Format.size(node.size),
                duration.takeIf { it > 0 }?.let { Format.duration(it) },
            ).joinToString("  ·  "),
            onClose = onClose,
            bottom = if (failure != null) {
                null
            } else {
                {
                    Column(Modifier.fillMaxWidth()) {
                        Scrubber(
                            position = position,
                            duration = duration,
                            onSeek = { player.seekTo(it) },
                        )
                        Gap(Space.near)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            BasicTextCompat(
                                Format.duration(position),
                                type.metaStrong.copy(color = Color.White),
                            )
                            BasicTextCompat(
                                Format.duration(duration),
                                type.meta.copy(color = Color.White.copy(alpha = 0.65f)),
                            )
                        }
                        Gap(Space.group)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlyphButton(
                                Glyphs.SkipPrevious, "Back 10 seconds",
                                { player.seekTo((position - 10_000).coerceAtLeast(0)) },
                                glyphSize = 24.dp, tint = Color.White,
                            )
                            Gap(Space.group)
                            GlyphButton(
                                if (playing) Glyphs.Pause else Glyphs.Play,
                                if (playing) "Pause" else "Play",
                                { if (playing) player.pause() else player.play() },
                                glyphSize = 34.dp, touchSize = 64.dp, tint = Color.White,
                            )
                            Gap(Space.group)
                            GlyphButton(
                                Glyphs.SkipNext, "Forward 10 seconds",
                                { player.seekTo((position + 10_000).coerceAtMost(duration)) },
                                glyphSize = 24.dp, tint = Color.White,
                            )
                        }
                        Gap(Space.near)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            listOf(0.5f, 1f, 1.5f, 2f).forEach { rate ->
                                Box(Modifier.pressable(
                                    onClick = {
                                        speed = rate
                                        player.setPlaybackSpeed(rate)
                                    },
                                    contentDescription = "Playback speed ${rate}x",
                                )) {
                                    BasicTextCompat(
                                        if (rate == 1f) "1x" else "${rate}x",
                                        type.action.copy(
                                            color = if (speed == rate) {
                                                Color.White
                                            } else {
                                                Color.White.copy(alpha = 0.45f)
                                            },
                                        ),
                                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

/**
 * The scrubber.
 *
 * Drawn rather than assembled from a slider, so the track, the buffered
 * region and the played region share the FILISH progress language exactly.
 * The touch target extends well above and below the visible track - a 4dp bar
 * is impossible to grab, and a scrubber that is hard to grab is the single
 * most-felt flaw in a video player.
 */
@Composable
private fun Scrubber(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    val palette = Filish.palette
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    val fraction = if (dragging) {
        dragFraction
    } else if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        if (duration > 0) onSeek((dragFraction * duration).toLong())
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0) {
                        onSeek(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(5.dp)) {
            val h = size.height
            val r = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.28f), cornerRadius = r)
            drawRoundRect(
                Color.White,
                size = Size(size.width * fraction, h),
                cornerRadius = r,
            )
            // The handle grows while dragging - confirmation that the gesture
            // was caught, at the moment the user needs it.
            val handleR = if (dragging) h * 2.1f else h * 1.5f
            drawCircle(Color.White, handleR, Offset(size.width * fraction, h / 2f))
        }
    }
}

/**
 * Turns a playback failure into something actionable.
 *
 * The distinction that matters: a decoder this device does not have (try
 * another player) versus a file that is damaged or gone (do not bother).
 */
private fun describe(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    ->
        "This device has no decoder for the format inside this file. The file itself is " +
            "probably fine - another player with its own decoders may handle it."

    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        "The file is no longer there."

    PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
        "Filish is not allowed to read this file."

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    ->
        "The file is damaged or incomplete - often the result of an interrupted download " +
            "or transfer."

    else -> error.localizedMessage ?: "Playback stopped unexpectedly."
}
