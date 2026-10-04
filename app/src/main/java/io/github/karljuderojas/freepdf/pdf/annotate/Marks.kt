package io.github.karljuderojas.freepdf.pdf.annotate

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.util.DateConverter
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.onPage
import io.github.karljuderojas.freepdf.pdf.sign.SignatureAnnotation
import io.github.karljuderojas.freepdf.pdf.pdfToDisplay
import io.github.karljuderojas.freepdf.pdf.text.PageText
import io.github.karljuderojas.freepdf.pdf.text.PageWord
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One mark on a page (a highlight, drawing, note and so on, made here or in another app), as the
 * viewer shows it: where it is as fractions of the displayed page, and what it says.
 * [index] is its place in the page's annotation list, which is how [Marks.edit] finds it again.
 */
data class Mark(
    val page: Int,
    val index: Int,
    val kind: Kind,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val color: Annotator.Rgb?,
    val width: Float,
    val comment: String,
    /** The text a highlight, underline or strikeout covers. */
    val markedText: String,
    val author: String?,
    val modified: Long?,
    /** For a line or arrow, the strokes it is really drawn with; a tap must land on one of them. */
    val lines: LineShape? = null,
) {
    enum class Kind { Highlight, Underline, StrikeOut, Ink, Square, Circle, Line, Arrow, Note, TextBox, Stamp, Other }

    /** One straight stroke, as fractions of the displayed page. */
    data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    /**
     * The line itself and the two sides of each arrowhead, with how far from them the pen
     * reaches ([reach], half the line width as a fraction of the displayed page's width).
     */
    data class LineShape(val segments: List<Segment>, val reach: Float)
}

/** How far ([px], [py]) is from the segment ([x1], [y1])-([x2], [y2]). */
internal fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val length2 = dx * dx + dy * dy
    val t = if (length2 == 0f) 0f else (((px - x1) * dx + (py - y1) * dy) / length2).coerceIn(0f, 1f)
    return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
}

/** Lists, restyles, comments on and deletes the marks in a document. */
object Marks {

    /**
     * Every mark in [document], page by page in drawing order. Links, form fields and pop-ups are
     * left out, as is a mark in a part of the page a crop has taken away. [wordsOn] gives the words on a page, for the text a highlight covers; it is only
     * asked for pages that have marked text, and a caller that keeps the words from one listing
     * to the next (the pages' text does not change when a mark does) can hand them back here.
     */
    fun list(
        document: PDDocument,
        wordsOn: (pageIndex: Int) -> List<PageWord> = { runCatching { PageText.words(document, it) }.getOrDefault(emptyList()) },
    ): List<Mark> = document.pages.flatMapIndexed { pageIndex, page ->
        val annotations = page.annotations
        // Only pages with marked text need their words.
        val words by lazy { wordsOn(pageIndex) }
        annotations.mapIndexedNotNull { index, annotation ->
            val kind = kindOf(annotation) ?: return@mapIndexedNotNull null
            // A line's /Rect is padded well past its ends for the arrowhead (PdfBox grows it by ten
            // line widths every side), so its box comes from the strokes themselves.
            val strokes = (annotation as? PDAnnotationLine)?.let { strokesOf(it) }
            val box = strokes?.bounds ?: annotation.rectangle?.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
                ?: return@mapIndexedNotNull null
            val (x1, y1) = page.toDisplay(box.left, box.bottom)
            val (x2, y2) = page.toDisplay(box.right, box.top)
            val shown = DisplayRect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2)).onPage()
                ?: return@mapIndexedNotNull null
            val markup = annotation as? PDAnnotationMarkup
            // A text box keeps its colour and font size in /DA rather than /C and /BS.
            val textStyle = if (kind == Mark.Kind.TextBox) markup?.let { TextBoxes.styleOf(it) } else null
            Mark(
                page = pageIndex,
                index = index,
                kind = kind,
                left = shown.left, top = shown.top, right = shown.right, bottom = shown.bottom,
                color = textStyle?.color
                    ?: annotation.color?.components?.takeIf { it.size == 3 }?.let { Annotator.Rgb(it[0], it[1], it[2]) },
                width = textStyle?.fontSize ?: markup?.borderStyle?.width ?: 1f,
                comment = annotation.contents.orEmpty(),
                markedText = (annotation as? PDAnnotationTextMarkup)?.let { coveredText(page, it, words) }.orEmpty(),
                author = markup?.titlePopup?.takeIf { it.isNotBlank() },
                modified = (annotation.modifiedDate?.let { runCatching { DateConverter.toCalendar(it) }.getOrNull() }
                    ?: markup?.creationDate)?.timeInMillis,
                lines = strokes?.let { drawn ->
                    Mark.LineShape(
                        drawn.segments.map { (from, to) ->
                            val (ax, ay) = page.toDisplay(from.x, from.y)
                            val (bx, by) = page.toDisplay(to.x, to.y)
                            Mark.Segment(ax, ay, bx, by)
                        },
                        reach = drawn.halfWidth / page.displayedWidth(),
                    )
                },
            )
        }
    }

    /**
     * Changes the mark at [index] on [pageIndex]: its colour, its line width (for marks drawn with
     * a line), and its comment. Null leaves that part as it was. The mark is redrawn so other
     * viewers show the change. For a text box, [width] is the font size and [comment] its text.
     */
    fun edit(
        document: PDDocument,
        pageIndex: Int,
        index: Int,
        color: Annotator.Rgb? = null,
        width: Float? = null,
        comment: String? = null,
    ) {
        val annotation = document.getPage(pageIndex).annotations[index]
        require(kindOf(annotation) != null) { "Not a mark" }
        if (kindOf(annotation) == Mark.Kind.TextBox && annotation is PDAnnotationMarkup) {
            val style = TextBoxes.styleOf(annotation)
            TextBoxes.edit(
                document, annotation,
                text = comment?.takeIf { it.isNotBlank() } ?: annotation.contents.orEmpty(),
                color = color ?: style?.color ?: Annotator.Rgb(0f, 0f, 0f),
                fontSize = width ?: style?.fontSize ?: 12f,
            )
            return
        }
        if (comment != null) annotation.contents = comment.ifBlank { null }
        when (kindOf(annotation)) {
            // A stamp's look is all in the appearance this app drew, so it is drawn again in the new colour.
            Mark.Kind.Stamp -> if (color != null) Stamps.restyle(document, annotation, color)
            // Marks made elsewhere keep their own appearance; a colour that would not show is not written either.
            Mark.Kind.Other -> Unit
            else -> {
                if (color != null) annotation.color = color.toPdColor()
                if (width != null && annotation is PDAnnotationMarkup) {
                    annotation.borderStyle = (annotation.borderStyle ?: PDBorderStyleDictionary()).apply { this.width = width }
                }
                if (color != null || width != null) annotation.constructAppearances(document)
            }
        }
        annotation.setModifiedDate(Calendar.getInstance())
    }

    /** Removes the mark at [index] on [pageIndex], and the pop-up window that belongs to it, if any. */
    fun delete(document: PDDocument, pageIndex: Int, index: Int) {
        val page = document.getPage(pageIndex)
        val annotations = page.annotations
        val target = annotations[index]
        require(kindOf(target) != null) { "Not a mark" }
        val popup = (target as? PDAnnotationMarkup)?.popup?.cosObject
        page.annotations = annotations.filterIndexed { i, it -> i != index && (popup == null || it.cosObject !== popup) }
    }

    /**
     * The index of the topmost mark under ([x], [y]) in PDF space on [pageIndex], or null. Links,
     * form fields, pop-up windows and placed signatures are not marks and are passed over.
     */
    fun indexAt(document: PDDocument, pageIndex: Int, x: Float, y: Float): Int? =
        document.getPage(pageIndex).annotations.indexOfLast { annotation ->
            if (kindOf(annotation) == null) return@indexOfLast false
            val strokes = (annotation as? PDAnnotationLine)?.let { strokesOf(it) }
            // A line is hit along its strokes, not anywhere in its padded box.
            if (strokes != null) strokes.hits(x, y, slop = TAP_SLOP_PT) else annotation.rectangle?.contains(x, y) == true
        }.takeIf { it >= 0 }

    /** How far from a line's strokes a tap still counts, in points: about a fingertip's width. */
    private const val TAP_SLOP_PT = 8f

    /** The strokes PdfBox draws for a Line annotation, in PDF space. */
    private class Strokes(val segments: List<Pair<PdfPoint, PdfPoint>>, val halfWidth: Float) {
        /** The strokes' bounds plus the pen, like the box of any other mark. */
        val bounds: PdfRect
            get() {
                val points = segments.flatMap { listOf(it.first, it.second) }
                val pad = halfWidth + 1f
                return PdfRect(
                    points.minOf { it.x } - pad, points.minOf { it.y } - pad,
                    points.maxOf { it.x } + pad, points.maxOf { it.y } + pad,
                )
            }

        fun hits(x: Float, y: Float, slop: Float) =
            segments.any { (a, b) -> distanceToSegment(x, y, a.x, a.y, b.x, b.y) <= halfWidth + slop }
    }

    /**
     * The line from /L and, at each end that has an arrowhead, its two sides: [Annotator.ARROW_LENGTH]
     * line widths long, 30 degrees off the line, the way PdfBox draws them. Null without a usable /L.
     */
    private fun strokesOf(line: PDAnnotationLine): Strokes? {
        val l = line.line?.takeIf { it.size >= 4 } ?: return null
        val start = PdfPoint(l[0], l[1])
        val end = PdfPoint(l[2], l[3])
        // PdfBox draws a hairline as one point wide.
        val width = (line.borderStyle?.width ?: 1f).let { if (it < 1e-5f) 1f else it }
        val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val segments = mutableListOf(start to end)
        if (line.endPointEndingStyle in ArrowEndings) segments += arrowhead(end, angle, width)
        if (line.startPointEndingStyle in ArrowEndings) segments += arrowhead(start, angle + Math.PI, width)
        return Strokes(segments, width / 2)
    }

    /** The two sides of an arrowhead whose point is [tip], on a line coming in at [angle]. */
    private fun arrowhead(tip: PdfPoint, angle: Double, width: Float): List<Pair<PdfPoint, PdfPoint>> {
        val length = Annotator.ARROW_LENGTH * width
        return listOf(ARROW_ANGLE, -ARROW_ANGLE).map { side ->
            val back = angle + Math.PI + side
            tip to PdfPoint(tip.x + (length * cos(back)).toFloat(), tip.y + (length * sin(back)).toFloat())
        }
    }

    private val ARROW_ANGLE = Math.toRadians(30.0)

    private fun kindOf(annotation: PDAnnotation): Mark.Kind? = when (annotation.subtype) {
        PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT -> Mark.Kind.Highlight
        PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE -> Mark.Kind.Underline
        PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT -> Mark.Kind.StrikeOut
        PDAnnotationMarkup.SUB_TYPE_INK -> Mark.Kind.Ink
        PDAnnotationSquareCircle.SUB_TYPE_SQUARE -> Mark.Kind.Square
        PDAnnotationSquareCircle.SUB_TYPE_CIRCLE -> Mark.Kind.Circle
        // An arrowhead at either end makes it an arrow.
        PDAnnotationLine.SUB_TYPE -> {
            val line = annotation as? PDAnnotationLine
            val ends = listOfNotNull(line?.startPointEndingStyle, line?.endPointEndingStyle)
            if (ends.any { it in ArrowEndings }) Mark.Kind.Arrow else Mark.Kind.Line
        }
        PDAnnotationText.SUB_TYPE -> Mark.Kind.Note
        PDAnnotationMarkup.SUB_TYPE_FREETEXT -> Mark.Kind.TextBox
        // A signature placed in Sign mode is a stamp too, but it is managed there, not as a mark.
        // A stamp from another app has a look this cannot redraw, so it is listed like any other mark.
        "Stamp" -> when {
            SignatureAnnotation.isSignature(annotation) -> null
            Stamps.kindOf(annotation) != null -> Mark.Kind.Stamp
            else -> Mark.Kind.Other
        }
        "Link", "Widget", "Popup" -> null
        // Polygons, carets and the like still show in the list, under a general name.
        else -> if (annotation is PDAnnotationMarkup) Mark.Kind.Other else null
    }

    private val ArrowEndings = setOf(
        PDAnnotationLine.LE_OPEN_ARROW, PDAnnotationLine.LE_CLOSED_ARROW,
        PDAnnotationLine.LE_R_OPEN_ARROW, PDAnnotationLine.LE_R_CLOSED_ARROW,
    )

    /** The words whose middles fall inside the mark's line boxes. */
    private fun coveredText(page: PDPage, mark: PDAnnotationTextMarkup, words: List<PageWord>): String {
        val quads = mark.quadPoints ?: return ""
        // Each quad's bounds, as fractions of the displayed page: left, top, right, bottom.
        val lines = (0 until quads.size / 8).map { q ->
            val xs = (0 until 4).map { quads[q * 8 + it * 2] }
            val ys = (0 until 4).map { quads[q * 8 + it * 2 + 1] }
            val (x1, y1) = page.toDisplay(xs.min(), ys.min())
            val (x2, y2) = page.toDisplay(xs.max(), ys.max())
            floatArrayOf(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
        }
        return words.filter { word ->
            val cx = (word.left + word.right) / 2
            val cy = (word.top + word.bottom) / 2
            lines.any { (left, top, right, bottom) -> cx in left..right && cy in top..bottom }
        }.joinToString(" ") { it.text }
    }

    private fun PDPage.toDisplay(x: Float, y: Float): Pair<Float, Float> {
        val crop = cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
        return pdfToDisplay(x, y, rotation, crop)
    }

    /** The width of the page as shown, in points: its crop box's height when it is turned on its side. */
    private fun PDPage.displayedWidth(): Float =
        if (((rotation % 360) + 360) % 360 % 180 == 90) cropBox.height else cropBox.width
}
