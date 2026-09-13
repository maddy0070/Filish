package com.filish.feature.viewer.audio

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.filish.core.media.MediaFacts
import com.filish.core.media.MediaMetadata
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.GlyphButton
import com.filish.design.component.ProblemState
import com.filish.design.component.pressable
import kotlinx.coroutines.delay
import java.io.File

/**
 * The audio player.
 *
 * ---------------------------------------------------------------------------
 * Not a music app
 *
 * The obvious move is to imitate a streaming player: big artwork, a library,
 * playlists, recommendations. That is the wrong model, because this is not a
 * library - it is one file the user tapped in a folder. They are usually
 * checking what it is, not settling in for an album.
 *
 * So the screen leads with what identifies *this file*: its name, the
 * metadata it actually carries, and its duration. Embedded artwork is shown
 * when it exists and is simply absent when it does not - no generic vinyl
 * record placeholder standing in for information the file does not have. A
 * voice memo has no cover art, and pretending otherwise wastes the screen's
 * best space on a picture that means nothing.
 *
 * In place of artwork FILISH draws a quiet amplitude figure derived from the
 * playback position. It is honest about what it is - a position indicator,
 * not a rendered waveform, which would require decoding the whole file - and
 * it gives the eye something that moves with the sound.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AudioViewer(
    node: FileNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val context = LocalContext.current

    var facts by remember(node.path) { mutableStateOf(MediaFacts()) }
    var artwork by remember(node.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var failure by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }

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
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) {
                failure = error.localizedMessage ?: "This file could not be played."
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(node.path) {
        facts = MediaMetadata.read(node.path, node.kind)
        artwork = runCatching {
            val r = android.media.MediaMetadataRetriever()
            r.setDataSource(node.path)
            val bytes = r.embeddedPicture
            r.release()
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }.getOrNull()
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (duration <= 0) duration = player.duration.coerceAtLeast(0L)
            delay(180)
        }
    }

    Box(modifier.fillMaxSize().background(palette.ground0)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = Space.gutter, top = Space.near),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphButton(Glyphs.ChevronLeft, "Close", onClose, glyphSize = 22.dp)
                Gap(Space.bond)
                BasicTextCompat(
                    node.kind.label,
                    type.eyebrow.copy(color = palette.ink2),
                    Modifier.weight(1f),
                )
            }

            if (failure != null) {
                ProblemState(
                    what = "This audio will not play",
                    why = failure,
                    whatNext = "The file may be damaged, or use a codec this device lacks.",
                )
                return@Column
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(horizontal = Space.zone),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork!!.asImageBitmap(),
                            contentDescription = "Album artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(Corner.surface)),
                        )
                    } else {
                        // A position figure, not a fake waveform.
                        Amplitude(
                            fraction = if (duration > 0) position.toFloat() / duration else 0f,
                            playing = playing,
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                        )
                    }

                    Gap(Space.zone - 10.dp)
                    BasicTextCompat(
                        facts.title ?: node.stem,
                        type.title.copy(color = palette.ink0, textAlign = TextAlign.Center),
                        Modifier.fillMaxWidth(),
                        maxLines = 2,
                    )
                    val credits = listOfNotNull(facts.artist, facts.album).joinToString("  ·  ")
                    if (credits.isNotBlank()) {
                        Gap(Space.near)
                        BasicTextCompat(
                            credits,
                            type.body.copy(color = palette.ink1, textAlign = TextAlign.Center),
                            Modifier.fillMaxWidth(),
                            maxLines = 2,
                        )
                    }
                    Gap(Space.near)
                    BasicTextCompat(
                        listOfNotNull(
                            node.extension.uppercase().ifEmpty { null },
                            facts.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
                            Format.size(node.size),
                        ).joinToString("  ·  "),
                        type.meta.copy(color = palette.ink2, textAlign = TextAlign.Center),
                        Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.gutter, vertical = Space.apart),
            ) {
                AudioScrubber(
                    position = position,
                    duration = duration,
                    onSeek = { player.seekTo(it) },
                )
                Gap(Space.near)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicTextCompat(
                        Format.duration(position),
                        type.metaStrong.copy(color = palette.ink0),
                    )
                    BasicTextCompat(
                        Format.duration(duration),
                        type.meta.copy(color = palette.ink2),
                    )
                }
                Gap(Space.apart)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphButton(
                        Glyphs.SkipPrevious, "Back 15 seconds",
                        { player.seekTo((position - 15_000).coerceAtLeast(0)) },
                        glyphSize = 24.dp,
                    )
                    Gap(Space.group)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(34.dp))
                            .background(palette.signal)
                            .pressable(
                                onClick = { if (playing) player.pause() else player.play() },
                                contentDescription = if (playing) "Pause" else "Play",
                                shape = RoundedCornerShape(34.dp),
                            )
                            .padding(18.dp),
                    ) {
                        Glyph(
                            if (playing) Glyphs.Pause else Glyphs.Play, null,
                            size = 30.dp, tint = palette.inkOn,
                        )
                    }
                    Gap(Space.group)
                    GlyphButton(
                        Glyphs.SkipNext, "Forward 15 seconds",
                        { player.seekTo((position + 15_000).coerceAtMost(duration)) },
                        glyphSize = 24.dp,
                    )
                }
                Gap(Space.group)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                        Box(
                            Modifier.pressable(
                                onClick = { speed = rate; player.setPlaybackSpeed(rate) },
                                contentDescription = "Speed ${rate}x",
                            ),
                        ) {
                            BasicTextCompat(
                                if (rate == 1f) "1x" else "${rate}x",
                                type.action.copy(
                                    color = if (speed == rate) palette.signal else palette.ink2,
                                ),
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A position figure for files with no artwork.
 *
 * Bars whose heights come from a fixed pseudo-random sequence, with the played
 * portion filled. It is explicitly *not* a waveform - rendering a real one
 * means decoding the whole file, which for a two-hour recording is minutes of
 * work for a decoration. What it genuinely conveys is position and motion,
 * and it does not pretend to convey more.
 */
@Composable
private fun Amplitude(fraction: Float, playing: Boolean, modifier: Modifier = Modifier) {
    val palette = Filish.palette
    val bars = remember {
        // Deterministic, so the figure is stable across recompositions rather
        // than reshuffling while the user watches.
        val rng = java.util.Random(41)
        FloatArray(56) { 0.22f + rng.nextFloat() * 0.78f }
    }
    Canvas(modifier) {
        val gap = size.width / bars.size * 0.36f
        val barW = size.width / bars.size - gap
        bars.forEachIndexed { index, amplitude ->
            val x = index * (barW + gap)
            val h = size.height * amplitude
            val played = index.toFloat() / bars.size <= fraction
            drawRoundRect(
                color = if (played) palette.signal else palette.ground2,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

@Composable
private fun AudioScrubber(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    val palette = Filish.palette
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (dragging) dragFraction
    else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f)
    else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        dragFraction = (it.x / size.width).coerceIn(0f, 1f)
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
                detectTapGestures {
                    if (duration > 0) {
                        onSeek(((it.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(5.dp)) {
            val h = size.height
            val r = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(palette.ground2, cornerRadius = r)
            drawRoundRect(palette.signal, size = Size(size.width * fraction, h), cornerRadius = r)
            drawCircle(
                palette.signal,
                if (dragging) h * 2.1f else h * 1.5f,
                Offset(size.width * fraction, h / 2f),
            )
        }
    }
}
