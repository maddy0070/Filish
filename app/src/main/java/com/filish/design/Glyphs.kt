package com.filish.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The FILISH glyph language.
 *
 * Drawn rather than imported, and drawn from a single geometric vocabulary so
 * that thirty marks read as one family instead of thirty decisions.
 *
 * THE RULES
 *
 *   - Everything is constructed on a 24-unit square and scaled at draw time,
 *     so a glyph is crisp at 16dp and at 64dp with no second asset.
 *   - One stroke weight, proportional to the glyph (24:1.9). Mixed weights
 *     are the fastest way to make an icon set look assembled from three
 *     different sets.
 *   - Round caps and round joins everywhere. The ends of strokes are the most
 *     visible part of a small mark and inconsistency there is what the eye
 *     notices first.
 *   - Content marks share one silhouette. Every file kind is the same
 *     rectangle with a different interior, so a folder of mixed content reads
 *     as a column of files that differ, not as a scatter of unrelated shapes.
 *     Identity comes from the interior, reinforced - never replaced - by a
 *     muted category tint.
 *   - Nothing is skeuomorphic. There is no folded page corner, no 3.5-inch
 *     floppy disk, no manila folder tab. Those referred to physical objects
 *     that a phone user has quite possibly never handled.
 */
object Glyphs {

    private const val GRID = 24f
    private const val WEIGHT = 1.9f

    /** A drawing instruction on a 24-unit grid, scaled and coloured at use. */
    fun interface Glyph {
        fun DrawScope.draw(scale: Float, color: Color, stroke: Stroke)
    }

    fun DrawScope.paint(glyph: Glyph, color: Color, sizePx: Float) {
        val scale = sizePx / GRID
        val stroke = Stroke(
            width = WEIGHT * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        with(glyph) { draw(scale, color, stroke) }
    }

    // ---- helpers -----------------------------------------------------------

    private fun DrawScope.line(s: Float, x1: Float, y1: Float, x2: Float, y2: Float, c: Color, st: Stroke) =
        drawLine(c, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), st.width, st.cap)

    private fun DrawScope.box(
        s: Float, x: Float, y: Float, w: Float, h: Float, r: Float, c: Color, st: Stroke,
    ) = drawRoundRect(
        color = c,
        topLeft = Offset(x * s, y * s),
        size = Size(w * s, h * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * s, r * s),
        style = st,
    )

    private fun DrawScope.solidBox(
        s: Float, x: Float, y: Float, w: Float, h: Float, r: Float, c: Color,
    ) = drawRoundRect(
        color = c,
        topLeft = Offset(x * s, y * s),
        size = Size(w * s, h * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * s, r * s),
    )

    private fun DrawScope.dot(s: Float, x: Float, y: Float, r: Float, c: Color) =
        drawCircle(c, r * s, Offset(x * s, y * s))

    private fun DrawScope.ring(s: Float, x: Float, y: Float, r: Float, c: Color, st: Stroke) =
        drawCircle(c, r * s, Offset(x * s, y * s), style = st)

    private fun DrawScope.poly(s: Float, pts: List<Pair<Float, Float>>, c: Color, st: Stroke, close: Boolean = false) {
        val p = Path()
        pts.forEachIndexed { i, (x, y) ->
            if (i == 0) p.moveTo(x * s, y * s) else p.lineTo(x * s, y * s)
        }
        if (close) p.close()
        drawPath(p, c, style = st)
    }

    private fun DrawScope.fillPoly(s: Float, pts: List<Pair<Float, Float>>, c: Color) {
        val p = Path()
        pts.forEachIndexed { i, (x, y) ->
            if (i == 0) p.moveTo(x * s, y * s) else p.lineTo(x * s, y * s)
        }
        p.close()
        drawPath(p, c)
    }

    /** The shared silhouette every file kind is built inside. */
    private fun DrawScope.sheet(s: Float, c: Color, st: Stroke) =
        box(s, 5f, 3f, 14f, 18f, 2.5f, c, st)

    // ---- content marks -----------------------------------------------------

    /**
     * A folder is the only content mark that is NOT the sheet silhouette - it
     * is deliberately a different shape, because a folder is a different kind
     * of thing: a place you enter, not an object you act on. That distinction
     * is worth one exception to the family rule.
     */
    val Folder = Glyph { s, c, st ->
        // One closed silhouette rather than a rectangle with a line drawn
        // inside it. The first version layered a tab path over a box and read
        // as a plain container with a stray diagonal - a folder has to be one
        // shape whose outline steps up on the left, or it reads as a card.
        poly(
            s,
            listOf(
                3f to 19f,
                3f to 6.5f,
                9.2f to 6.5f,
                11.4f to 9.2f,
                21f to 9.2f,
                21f to 19f,
            ),
            c, st, close = true,
        )
    }

    val FolderOpen = Glyph { s, c, st ->
        poly(s, listOf(3f to 19f, 3f to 7f, 9f to 7f, 11f to 9.5f, 19f to 9.5f, 19f to 11.5f), c, st)
        poly(s, listOf(3f to 19f, 6.5f to 11.5f, 22f to 11.5f, 18.5f to 19f), c, st, close = true)
    }

    val FileGeneric = Glyph { s, c, st -> sheet(s, c, st) }

    /** Image: a horizon and a sun inside the sheet. */
    val Image = Glyph { s, c, st ->
        sheet(s, c, st)
        dot(s, 9.5f, 9f, 1.3f, c)
        poly(s, listOf(6.5f to 17.5f, 11f to 12.5f, 14f to 15.5f, 16f to 13.5f, 17.5f to 15.2f), c, st)
    }

    /** RAW: the image mark with a bracket, marking it as unprocessed data. */
    val RawImage = Glyph { s, c, st ->
        sheet(s, c, st)
        dot(s, 9f, 8.5f, 1.2f, c)
        poly(s, listOf(6.5f to 15.5f, 10f to 11.5f, 12.5f to 14f), c, st)
        line(s, 14.5f, 17.5f, 17.5f, 17.5f, c, st)
        line(s, 14.5f, 17.5f, 14.5f, 14.5f, c, st)
    }

    /** Video: a play triangle. */
    val Video = Glyph { s, c, st ->
        sheet(s, c, st)
        fillPoly(s, listOf(10f to 8.5f, 16f to 12f, 10f to 15.5f), c)
    }

    /** Audio: a waveform, not a musical note - files hold sound, not notation. */
    val Audio = Glyph { s, c, st ->
        sheet(s, c, st)
        line(s, 8.5f, 10f, 8.5f, 14f, c, st)
        line(s, 11f, 8f, 11f, 16f, c, st)
        line(s, 13.5f, 10.5f, 13.5f, 13.5f, c, st)
        line(s, 16f, 9f, 16f, 15f, c, st)
    }

    /** Document: lines of text. */
    val Document = Glyph { s, c, st ->
        sheet(s, c, st)
        line(s, 8.5f, 9f, 15.5f, 9f, c, st)
        line(s, 8.5f, 12f, 15.5f, 12f, c, st)
        line(s, 8.5f, 15f, 12.5f, 15f, c, st)
    }

    /** PDF: text lines with a marked block - a page with fixed layout. */
    val Pdf = Glyph { s, c, st ->
        sheet(s, c, st)
        solidBox(s, 8.5f, 8.5f, 7f, 1.6f, 0.8f, c)
        line(s, 8.5f, 12.5f, 15.5f, 12.5f, c, st)
        line(s, 8.5f, 15.5f, 13f, 15.5f, c, st)
    }

    val Text = Glyph { s, c, st ->
        sheet(s, c, st)
        line(s, 8.5f, 9.5f, 15.5f, 9.5f, c, st)
        line(s, 12f, 9.5f, 12f, 16f, c, st)
    }

    /** Code: angle brackets. */
    val Code = Glyph { s, c, st ->
        sheet(s, c, st)
        poly(s, listOf(10.5f to 9.5f, 8f to 12f, 10.5f to 14.5f), c, st)
        poly(s, listOf(13.5f to 9.5f, 16f to 12f, 13.5f to 14.5f), c, st)
    }

    /** Archive: stacked strata inside the sheet - compressed layers. */
    val Archive = Glyph { s, c, st ->
        sheet(s, c, st)
        line(s, 8.5f, 9f, 15.5f, 9f, c, st)
        line(s, 8.5f, 12f, 15.5f, 12f, c, st)
        line(s, 8.5f, 15f, 15.5f, 15f, c, st)
        solidBox(s, 11.2f, 7.5f, 1.6f, 9f, 0.8f, c)
    }

    /** App package: a sealed container. */
    val App = Glyph { s, c, st ->
        sheet(s, c, st)
        box(s, 8.5f, 9f, 7f, 7f, 1.5f, c, st)
        line(s, 8.5f, 12.5f, 15.5f, 12.5f, c, st)
    }

    val Font = Glyph { s, c, st ->
        sheet(s, c, st)
        line(s, 9f, 9.5f, 15f, 9.5f, c, st)
        line(s, 12f, 9.5f, 12f, 16f, c, st)
        line(s, 10.5f, 16f, 13.5f, 16f, c, st)
    }

    // ---- navigation and action --------------------------------------------

    val ChevronRight = Glyph { s, c, st -> poly(s, listOf(9.5f to 5f, 16f to 12f, 9.5f to 19f), c, st) }
    val ChevronLeft = Glyph { s, c, st -> poly(s, listOf(14.5f to 5f, 8f to 12f, 14.5f to 19f), c, st) }
    val ChevronDown = Glyph { s, c, st -> poly(s, listOf(5f to 9.5f, 12f to 16f, 19f to 9.5f), c, st) }
    val ChevronUp = Glyph { s, c, st -> poly(s, listOf(5f to 14.5f, 12f to 8f, 19f to 14.5f), c, st) }

    val ArrowUp = Glyph { s, c, st ->
        line(s, 12f, 19f, 12f, 5f, c, st)
        poly(s, listOf(6.5f to 10.5f, 12f to 5f, 17.5f to 10.5f), c, st)
    }

    val ArrowDown = Glyph { s, c, st ->
        line(s, 12f, 5f, 12f, 19f, c, st)
        poly(s, listOf(6.5f to 13.5f, 12f to 19f, 17.5f to 13.5f), c, st)
    }

    val Search = Glyph { s, c, st ->
        ring(s, 10.5f, 10.5f, 6f, c, st)
        line(s, 15f, 15f, 19.5f, 19.5f, c, st)
    }

    val Close = Glyph { s, c, st ->
        line(s, 6f, 6f, 18f, 18f, c, st)
        line(s, 18f, 6f, 6f, 18f, c, st)
    }

    val Check = Glyph { s, c, st -> poly(s, listOf(5.5f to 12.5f, 10f to 17f, 18.5f to 7f), c, st) }

    val Plus = Glyph { s, c, st ->
        line(s, 12f, 5.5f, 12f, 18.5f, c, st)
        line(s, 5.5f, 12f, 18.5f, 12f, c, st)
    }

    val More = Glyph { s, c, _ ->
        dot(s, 12f, 6f, 1.5f, c); dot(s, 12f, 12f, 1.5f, c); dot(s, 12f, 18f, 1.5f, c)
    }

    /**
     * Sort: three descending bars plus a direction arrow. The bars say
     * "ordering", the arrow says "which way" - the two facts the user needs,
     * shown rather than hidden behind a menu.
     */
    val Sort = Glyph { s, c, st ->
        line(s, 4f, 7f, 13f, 7f, c, st)
        line(s, 4f, 12f, 10f, 12f, c, st)
        line(s, 4f, 17f, 7f, 17f, c, st)
        line(s, 17.5f, 5.5f, 17.5f, 18.5f, c, st)
        poly(s, listOf(14.5f to 15.5f, 17.5f to 18.5f, 20.5f to 15.5f), c, st)
    }

    /** Filter: a funnel, built from straight strokes to match the family. */
    val Filter = Glyph { s, c, st ->
        poly(s, listOf(4f to 5.5f, 20f to 5.5f, 13.5f to 13f, 13.5f to 19f, 10.5f to 17f, 10.5f to 13f), c, st, close = true)
    }

    val ViewList = Glyph { s, c, st ->
        line(s, 4.5f, 7f, 19.5f, 7f, c, st)
        line(s, 4.5f, 12f, 19.5f, 12f, c, st)
        line(s, 4.5f, 17f, 19.5f, 17f, c, st)
    }

    val ViewGrid = Glyph { s, c, st ->
        box(s, 4.5f, 4.5f, 6f, 6f, 1.2f, c, st)
        box(s, 13.5f, 4.5f, 6f, 6f, 1.2f, c, st)
        box(s, 4.5f, 13.5f, 6f, 6f, 1.2f, c, st)
        box(s, 13.5f, 13.5f, 6f, 6f, 1.2f, c, st)
    }

    /** Trash: a vessel, no lid hinge, no skeuomorphic ribs. */
    val Trash = Glyph { s, c, st ->
        line(s, 4.5f, 7f, 19.5f, 7f, c, st)
        poly(s, listOf(6.5f to 7f, 7.5f to 19.5f, 16.5f to 19.5f, 17.5f to 7f), c, st)
        poly(s, listOf(9.5f to 7f, 9.5f to 4.5f, 14.5f to 4.5f, 14.5f to 7f), c, st)
    }

    /** Copy: one sheet behind another. */
    val Copy = Glyph { s, c, st ->
        box(s, 8f, 8f, 12f, 12f, 2f, c, st)
        poly(s, listOf(16f to 4f, 4f to 4f, 4f to 16f), c, st)
    }

    /** Move: a sheet with an arrow leaving it. */
    val Move = Glyph { s, c, st ->
        poly(s, listOf(11f to 5f, 4.5f to 5f, 4.5f to 19f, 15f to 19f, 15f to 14f), c, st)
        line(s, 10f, 12f, 19.5f, 12f, c, st)
        poly(s, listOf(16f to 8.5f, 19.5f to 12f, 16f to 15.5f), c, st)
    }

    /** Rename: a caret in text. */
    val Rename = Glyph { s, c, st ->
        line(s, 12f, 5f, 12f, 19f, c, st)
        line(s, 8.5f, 5f, 15.5f, 5f, c, st)
        line(s, 8.5f, 19f, 15.5f, 19f, c, st)
        line(s, 4f, 9f, 4f, 15f, c, st)
        line(s, 20f, 9f, 20f, 15f, c, st)
    }

    val Share = Glyph { s, c, st ->
        dot(s, 17.5f, 6f, 2.4f, c)
        dot(s, 6.5f, 12f, 2.4f, c)
        dot(s, 17.5f, 18f, 2.4f, c)
        line(s, 8.7f, 10.8f, 15.3f, 7.2f, c, st)
        line(s, 8.7f, 13.2f, 15.3f, 16.8f, c, st)
    }

    val Info = Glyph { s, c, st ->
        ring(s, 12f, 12f, 8f, c, st)
        dot(s, 12f, 7.8f, 1.1f, c)
        line(s, 12f, 11f, 12f, 16.5f, c, st)
    }

    /** Pin: a marker driven into a place. */
    val Pin = Glyph { s, c, st ->
        poly(s, listOf(12f to 21f, 12f to 14f), c, st)
        poly(s, listOf(8f to 3.5f, 16f to 3.5f, 14.5f to 9f, 17.5f to 12f, 6.5f to 12f, 9.5f to 9f), c, st, close = true)
    }

    val Play = Glyph { s, c, _ -> fillPoly(s, listOf(7.5f to 5f, 19f to 12f, 7.5f to 19f), c) }

    val Pause = Glyph { s, c, _ ->
        solidBox(s, 7.5f, 5f, 3.2f, 14f, 1.2f, c)
        solidBox(s, 13.3f, 5f, 3.2f, 14f, 1.2f, c)
    }

    val SkipNext = Glyph { s, c, st ->
        fillPoly(s, listOf(6f to 5.5f, 15f to 12f, 6f to 18.5f), c)
        line(s, 18f, 5.5f, 18f, 18.5f, c, st)
    }

    val SkipPrevious = Glyph { s, c, st ->
        fillPoly(s, listOf(18f to 5.5f, 9f to 12f, 18f to 18.5f), c)
        line(s, 6f, 5.5f, 6f, 18.5f, c, st)
    }

    /** Storage: the three strata of the FILISH mark, at icon scale. */
    val Storage = Glyph { s, c, st ->
        box(s, 3.5f, 5f, 17f, 4f, 1.4f, c, st)
        box(s, 3.5f, 10.5f, 17f, 4f, 1.4f, c, st)
        box(s, 3.5f, 16f, 17f, 4f, 1.4f, c, st)
    }

    val Settings = Glyph { s, c, st ->
        line(s, 4f, 7.5f, 20f, 7.5f, c, st)
        line(s, 4f, 16.5f, 20f, 16.5f, c, st)
        ring(s, 9f, 7.5f, 2.6f, c, st)
        ring(s, 15f, 16.5f, 2.6f, c, st)
    }

    val Warning = Glyph { s, c, st ->
        poly(s, listOf(12f to 4f, 21f to 19.5f, 3f to 19.5f), c, st, close = true)
        line(s, 12f, 10f, 12f, 14.5f, c, st)
        dot(s, 12f, 17f, 1.1f, c)
    }

    val Lock = Glyph { s, c, st ->
        box(s, 5f, 10.5f, 14f, 9.5f, 2.2f, c, st)
        poly(s, listOf(8.5f to 10.5f, 8.5f to 7.5f, 15.5f to 7.5f, 15.5f to 10.5f), c, st)
    }

    val Rotate = Glyph { s, c, st ->
        val p = Path().apply {
            addArc(Rect(4f * s, 4f * s, 20f * s, 20f * s), -40f, 290f)
        }
        drawPath(p, c, style = st)
        poly(s, listOf(14f to 3f, 18.5f to 6.5f, 14f to 9.5f), c, st)
    }

    val Zoom = Glyph { s, c, st ->
        ring(s, 10.5f, 10.5f, 6f, c, st)
        line(s, 15f, 15f, 19.5f, 19.5f, c, st)
        line(s, 7.5f, 10.5f, 13.5f, 10.5f, c, st)
        line(s, 10.5f, 7.5f, 10.5f, 13.5f, c, st)
    }

    val Clock = Glyph { s, c, st ->
        ring(s, 12f, 12f, 8f, c, st)
        poly(s, listOf(12f to 7f, 12f to 12f, 15.5f to 14f), c, st)
    }

    val Select = Glyph { s, c, st ->
        box(s, 4f, 4f, 16f, 16f, 3f, c, st)
        poly(s, listOf(8f to 12f, 11f to 15f, 16f to 9f), c, st)
    }

    val Extract = Glyph { s, c, st ->
        box(s, 4.5f, 4.5f, 8f, 8f, 1.6f, c, st)
        line(s, 12f, 16f, 20f, 16f, c, st)
        poly(s, listOf(16.5f to 12.5f, 20f to 16f, 16.5f to 19.5f), c, st)
    }

    val Compress = Glyph { s, c, st ->
        box(s, 5f, 5f, 14f, 14f, 2f, c, st)
        line(s, 12f, 8f, 12f, 13.5f, c, st)
        poly(s, listOf(9.5f to 11f, 12f to 13.5f, 14.5f to 11f), c, st)
        line(s, 8.5f, 16f, 15.5f, 16f, c, st)
    }

    /** Chosen for a file kind, so a listing needs no lookup table at the call site. */
    fun forKind(kind: com.filish.core.model.FileKind): Glyph = when (kind) {
        com.filish.core.model.FileKind.Folder -> Folder
        com.filish.core.model.FileKind.Image -> Image
        com.filish.core.model.FileKind.RawImage -> RawImage
        com.filish.core.model.FileKind.Video -> Video
        com.filish.core.model.FileKind.Audio -> Audio
        com.filish.core.model.FileKind.Document -> Document
        com.filish.core.model.FileKind.Pdf -> Pdf
        com.filish.core.model.FileKind.Text -> Text
        com.filish.core.model.FileKind.Code -> Code
        com.filish.core.model.FileKind.Archive -> Archive
        com.filish.core.model.FileKind.App -> App
        com.filish.core.model.FileKind.Font -> Font
        com.filish.core.model.FileKind.Other -> FileGeneric
    }

    fun tintFor(kind: com.filish.core.model.FileKind, palette: Palette): Color = when (kind) {
        com.filish.core.model.FileKind.Folder -> palette.catFolder
        com.filish.core.model.FileKind.Image -> palette.catImage
        com.filish.core.model.FileKind.RawImage -> palette.catImage
        com.filish.core.model.FileKind.Video -> palette.catVideo
        com.filish.core.model.FileKind.Audio -> palette.catAudio
        com.filish.core.model.FileKind.Document, com.filish.core.model.FileKind.Pdf,
        com.filish.core.model.FileKind.Text,
        -> palette.catDocument
        com.filish.core.model.FileKind.Code -> palette.catCode
        com.filish.core.model.FileKind.Archive -> palette.catArchive
        com.filish.core.model.FileKind.App -> palette.catApp
        com.filish.core.model.FileKind.Font -> palette.catOther
        com.filish.core.model.FileKind.Other -> palette.catOther
    }
}
