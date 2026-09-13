package com.filish.feature.viewer.doc

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.filish.core.model.FileKind
import com.filish.core.model.FileNode
import com.filish.core.model.Format
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.GlyphButton
import com.filish.design.component.Indeterminate
import com.filish.design.component.ProblemState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Documents.
 *
 * ---------------------------------------------------------------------------
 * Which formats, and why only these
 *
 * PDF and plain text, and nothing else claimed.
 *
 * PDF is rendered with Android's own PdfRenderer - part of the platform since
 * API 21, no dependency, no network, and it is the same engine the system
 * uses. Text is read directly with encoding detection.
 *
 * FILISH does *not* offer to open .docx, .xlsx or .pptx. Rendering those with
 * fidelity means embedding an office engine - tens of megabytes, and a
 * rendering that is subtly wrong in ways that matter when someone is reading
 * a contract. A viewer that shows a document incorrectly is worse than one
 * that honestly hands it to an app built for it, so FILISH classifies them,
 * shows their metadata, and passes them on.
 *
 * ---------------------------------------------------------------------------
 * Rendering pages
 *
 * Pages are rendered on demand as they scroll into view and released as they
 * leave. A 400-page PDF rendered eagerly at screen resolution is several
 * gigabytes of bitmap; rendering the two or three pages actually visible is a
 * few megabytes. PdfRenderer permits only one open page at a time, so access
 * is serialised through a mutex - concurrent renders return corrupt bitmaps
 * or throw, and it is not obvious from the API that this is so.
 */
@Composable
fun DocumentViewer(
    node: FileNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette

    Box(modifier.fillMaxSize().background(palette.ground0)) {
        when (node.kind) {
            FileKind.Pdf -> PdfBody(node, onClose)
            else -> TextBody(node, onClose)
        }
    }
}

@Composable
private fun PdfBody(node: FileNode, onClose: () -> Unit) {
    val palette = Filish.palette
    val type = Filish.type
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    var session by remember(node.path) { mutableStateOf<PdfSession?>(null) }
    var failure by remember(node.path) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val widthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }

    LaunchedEffect(node.path) {
        val opened = withContext(Dispatchers.IO) { PdfSession.open(File(node.path)) }
        if (opened == null) {
            failure = "This file is not a readable PDF. It may be damaged, or encrypted - " +
                "Android's renderer cannot open password-protected documents."
        } else {
            session = opened
        }
    }

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    val currentPage by remember {
        androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = Space.gutter, top = Space.near, bottom = Space.near),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphButton(Glyphs.ChevronLeft, "Close", onClose, glyphSize = 22.dp)
            Gap(Space.bond)
            Column(Modifier.weight(1f)) {
                BasicTextCompat(node.name, type.name.copy(color = palette.ink0), maxLines = 1)
                Gap(Space.bond)
                BasicTextCompat(
                    session?.let { "Page $currentPage of ${it.pageCount}  ·  ${Format.size(node.size)}" }
                        ?: Format.size(node.size),
                    type.meta.copy(color = palette.ink2),
                )
            }
        }

        when {
            failure != null -> ProblemState(
                what = "This PDF cannot be opened",
                why = failure,
                whatNext = "You can still copy, move or share the file.",
            )

            session == null -> Indeterminate(active = true)

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.group, end = Space.group, bottom = Space.zone,
                ),
            ) {
                items(session!!.pageCount, key = { it }) { index ->
                    PdfPage(session!!, index, widthPx)
                    Gap(Space.group)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(session: PdfSession, index: Int, widthPx: Int) {
    val palette = Filish.palette
    var bitmap by remember(session, index) { mutableStateOf<Bitmap?>(null) }
    var ratio by remember(session, index) { mutableStateOf(1.414f) }

    LaunchedEffect(session, index, widthPx) {
        val rendered = session.render(index, widthPx)
        if (rendered != null) {
            bitmap = rendered
            ratio = rendered.width.toFloat() / rendered.height.coerceAtLeast(1)
        }
    }

    // Released on scroll-out so a long document does not accumulate pages.
    DisposableEffect(session, index) {
        onDispose { bitmap?.recycle(); bitmap = null }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.3f, 3f))
            .clip(RoundedCornerShape(Corner.small))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Serialised access to PdfRenderer.
 *
 * PdfRenderer allows exactly one page open at a time across the whole
 * renderer. Two coroutines rendering concurrently is not slow, it is
 * incorrect - so every render goes through one mutex.
 */
private class PdfSession(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val pageCount: Int get() = renderer.pageCount
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.lock()
        try {
            if (index !in 0 until renderer.pageCount) return@withContext null
            renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                // PdfRenderer draws onto whatever is already in the bitmap, so
                // an unfilled one leaves transparent gaps where the page is
                // blank. Paper is white.
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (t: Throwable) {
            null
        } finally {
            lock.unlock()
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun open(file: File): PdfSession? = runCatching {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfSession(fd, PdfRenderer(fd))
        }.getOrNull()
    }
}

/**
 * Plain text and code.
 *
 * ---------------------------------------------------------------------------
 * Two things this gets right that text viewers usually do not
 *
 * ENCODING IS DETECTED, not assumed. A file written on Windows in Latin-1
 * rendered as UTF-8 turns every accented character into a replacement glyph.
 * FILISH checks for a byte-order mark, then validates as UTF-8, and falls
 * back to ISO-8859-1 - which cannot fail - rather than producing mojibake.
 *
 * LARGE FILES ARE TRUNCATED, and said so. A 200 MB log file laid out as one
 * text node will hang the compositor for minutes. FILISH reads the first
 * megabyte, displays it, and states plainly that it is showing part of the
 * file. Being told is fine; freezing is not.
 */
@Composable
private fun TextBody(node: FileNode, onClose: () -> Unit) {
    val palette = Filish.palette
    val type = Filish.type

    var content by remember(node.path) { mutableStateOf<String?>(null) }
    var truncated by remember(node.path) { mutableStateOf(false) }
    var encoding by remember(node.path) { mutableStateOf("") }
    var failure by remember(node.path) { mutableStateOf<String?>(null) }

    LaunchedEffect(node.path) {
        val result = withContext(Dispatchers.IO) { readText(File(node.path)) }
        if (result == null) {
            failure = "Filish could not read this file. It may not be text, or the system " +
                "may have denied access."
        } else {
            content = result.text
            truncated = result.truncated
            encoding = result.charset.name()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = Space.gutter, top = Space.near, bottom = Space.near),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphButton(Glyphs.ChevronLeft, "Close", onClose, glyphSize = 22.dp)
            Gap(Space.bond)
            Column(Modifier.weight(1f)) {
                BasicTextCompat(node.name, type.name.copy(color = palette.ink0), maxLines = 1)
                Gap(Space.bond)
                BasicTextCompat(
                    listOfNotNull(
                        Format.size(node.size),
                        encoding.ifEmpty { null },
                        if (truncated) "showing the first 1 MB" else null,
                    ).joinToString("  ·  "),
                    type.meta.copy(color = if (truncated) palette.warn else palette.ink2),
                )
            }
        }

        when {
            failure != null -> ProblemState(
                what = "This file cannot be shown as text",
                why = failure,
                whatNext = "Its details are still readable from the file list.",
            )

            content == null -> Indeterminate(active = true)

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter, vertical = Space.group),
            ) {
                BasicTextCompat(
                    content!!,
                    // Code keeps its alignment; prose gets a reading face.
                    if (node.kind == FileKind.Code) {
                        type.technical.copy(color = palette.ink0)
                    } else {
                        type.body.copy(color = palette.ink0)
                    },
                )
                Gap(Space.zone)
                if (truncated) {
                    BasicTextCompat(
                        "This file continues beyond what is shown. Filish displays the first " +
                            "megabyte so the viewer stays responsive.",
                        type.meta.copy(color = palette.ink2),
                    )
                    Gap(Space.zone)
                }
            }
        }
    }
}

private class TextResult(val text: String, val truncated: Boolean, val charset: Charset)

private const val TEXT_LIMIT = 1_000_000

private fun readText(file: File): TextResult? = runCatching {
    val length = file.length()
    val limit = minOf(length, TEXT_LIMIT.toLong()).toInt()
    val bytes = ByteArray(limit)
    file.inputStream().use { input ->
        var read = 0
        while (read < limit) {
            val n = input.read(bytes, read, limit - read)
            if (n <= 0) break
            read += n
        }
    }
    val charset = detectCharset(bytes)
    TextResult(String(bytes, charset), length > TEXT_LIMIT, charset)
}.getOrNull()

/**
 * Byte-order mark first, then a strict UTF-8 validation, then Latin-1.
 *
 * The strict decode is the important step: UTF-8 has enough structure that
 * invalid sequences are a reliable signal the file is not UTF-8, and
 * ISO-8859-1 maps every possible byte to some character, so it can always
 * render something legible rather than a page of replacement glyphs.
 */
private fun detectCharset(bytes: ByteArray): Charset {
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    ) {
        return StandardCharsets.UTF_8
    }
    if (bytes.size >= 2) {
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return StandardCharsets.UTF_16LE
        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return StandardCharsets.UTF_16BE
    }
    val strict = StandardCharsets.UTF_8.newDecoder().apply {
        onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
    }
    return runCatching {
        strict.decode(java.nio.ByteBuffer.wrap(bytes))
        StandardCharsets.UTF_8
    }.getOrDefault(StandardCharsets.ISO_8859_1)
}
