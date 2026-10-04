package io.github.karljuderojas.freepdf.pdf.annotate

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Standard PDF annotations, so marks made here show up in other PDF readers and browsers.
 * Each call also builds an appearance stream, which is what other viewers actually draw.
 */
object Annotator {

    data class Rgb(val r: Float, val g: Float, val b: Float) {
        fun toPdColor() = PDColor(floatArrayOf(r, g, b), PDDeviceRGB.INSTANCE)

        companion object {
            val Yellow = Rgb(1f, 0.92f, 0.23f)
            val Red = Rgb(0.9f, 0.16f, 0.16f)
            val Blue = Rgb(0.13f, 0.4f, 0.9f)
        }
    }

    /** The Shapes tool's choices: an outlined box or oval, or a straight line with or without an arrowhead. */
    enum class Shape { Rectangle, Ellipse, Line, Arrow }

    enum class TextMarkup(val subtype: String) {
        Highlight(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT),
        Underline(PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE),
        StrikeOut(PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT),
    }

    /**
     * Highlights, underlines or strikes out one or more line rectangles (one per text line).
     * [lineWidth] is the thickness of an underline or strikeout; highlights ignore it. [comment]
     * is shown as the mark's note in other viewers.
     */
    fun markText(
        document: PDDocument,
        pageIndex: Int,
        lines: List<PdfRect>,
        kind: TextMarkup = TextMarkup.Highlight,
        color: Rgb = Rgb.Yellow,
        lineWidth: Float = 1f,
        comment: String? = null,
        author: String? = null,
    ) {
        require(lines.isNotEmpty())
        val annotation = PDAnnotationTextMarkup(kind.subtype).apply {
            rectangle = lines.bounds().toPdRectangle()
            // Quad points per line: top-left, top-right, bottom-left, bottom-right.
            quadPoints = lines.flatMap {
                listOf(it.left, it.top, it.right, it.top, it.left, it.bottom, it.right, it.bottom)
            }.toFloatArray()
            if (kind != TextMarkup.Highlight) borderStyle = PDBorderStyleDictionary().apply { width = lineWidth }
            if (comment != null) contents = comment
            this.color = color.toPdColor()
            stamp(author)
        }
        document.getPage(pageIndex).annotations.add(annotation)
        annotation.constructAppearances(document)
    }

    /**
     * Freehand drawing. Each stroke is a list of points in PDF user space. A stroke that does
     * not go anywhere (a tap, or one or two points on the same spot) is drawn as a dot about as
     * wide as the pen, since viewers stroke ink with flat caps and would otherwise show nothing.
     */
    fun ink(
        document: PDDocument,
        pageIndex: Int,
        strokes: List<List<PdfPoint>>,
        color: Rgb = Rgb.Blue,
        lineWidth: Float = 2f,
        author: String? = null,
    ) {
        require(strokes.flatten().isNotEmpty())
        val drawn = strokes.filter { it.isNotEmpty() }.map { stroke -> if (stroke.isDot(lineWidth)) dot(stroke.first(), lineWidth) else stroke }
        val points = drawn.flatten()
        val annotation = PDAnnotationMarkup().apply {
            cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
            inkList = drawn.map { stroke -> stroke.flatMap { listOf(it.x, it.y) }.toFloatArray() }.toTypedArray()
            rectangle = PdfRect(
                points.minOf { it.x } - lineWidth, points.minOf { it.y } - lineWidth,
                points.maxOf { it.x } + lineWidth, points.maxOf { it.y } + lineWidth,
            ).toPdRectangle()
            borderStyle = PDBorderStyleDictionary().apply { width = lineWidth }
            this.color = color.toPdColor()
            stamp(author)
        }
        document.getPage(pageIndex).annotations.add(annotation)
        annotation.constructAppearances(document)
    }

    /** A sticky note icon that opens to show [text]. */
    fun note(
        document: PDDocument,
        pageIndex: Int,
        at: PdfPoint,
        text: String,
        color: Rgb = Rgb.Yellow,
        author: String? = null,
    ) {
        val annotation = PDAnnotationText().apply {
            rectangle = PdfRect(at.x, at.y - 20f, at.x + 20f, at.y).toPdRectangle()
            contents = text
            name = PDAnnotationText.NAME_COMMENT
            this.color = color.toPdColor()
            stamp(author)
        }
        document.getPage(pageIndex).annotations.add(annotation)
        annotation.constructAppearances(document)
    }

    /** Rectangle or ellipse outline. */
    fun shape(
        document: PDDocument,
        pageIndex: Int,
        bounds: PdfRect,
        ellipse: Boolean = false,
        color: Rgb = Rgb.Red,
        lineWidth: Float = 2f,
    ) {
        val subtype = if (ellipse) PDAnnotationSquareCircle.SUB_TYPE_CIRCLE else PDAnnotationSquareCircle.SUB_TYPE_SQUARE
        val annotation = PDAnnotationSquareCircle(subtype).apply {
            rectangle = bounds.toPdRectangle()
            borderStyle = PDBorderStyleDictionary().apply { width = lineWidth }
            this.color = color.toPdColor()
            stamp(null)
        }
        document.getPage(pageIndex).annotations.add(annotation)
        annotation.constructAppearances(document)
    }

    /**
     * A straight line from [from] to [to], as a standard Line annotation. With [arrow] it ends in
     * an open arrowhead at [to], the way other PDF editors draw an arrow.
     */
    fun line(
        document: PDDocument,
        pageIndex: Int,
        from: PdfPoint,
        to: PdfPoint,
        arrow: Boolean = false,
        color: Rgb = Rgb.Red,
        lineWidth: Float = 2f,
    ) {
        val annotation = PDAnnotationLine().apply {
            line = floatArrayOf(from.x, from.y, to.x, to.y)
            startPointEndingStyle = PDAnnotationLine.LE_NONE
            endPointEndingStyle = if (arrow) PDAnnotationLine.LE_OPEN_ARROW else PDAnnotationLine.LE_NONE
            if (arrow) intent = PDAnnotationLine.IT_LINE_ARROW
            // Room for the pen and the arrowhead, which PdfBox draws ten line widths long; drawing
            // the appearance only ever grows this.
            val pad = lineWidth * if (arrow) ARROW_LENGTH else 1f
            rectangle = PdfRect(
                minOf(from.x, to.x) - pad, minOf(from.y, to.y) - pad,
                maxOf(from.x, to.x) + pad, maxOf(from.y, to.y) + pad,
            ).toPdRectangle()
            borderStyle = PDBorderStyleDictionary().apply { width = lineWidth }
            this.color = color.toPdColor()
            stamp(null)
        }
        document.getPage(pageIndex).annotations.add(annotation)
        annotation.constructAppearances(document)
    }

    /** How long PdfBox draws an arrowhead, in line widths; each side is 30 degrees off the line. */
    const val ARROW_LENGTH = 10f

    /** True when every point of the stroke sits within a fraction of the pen width of the first. */
    private fun List<PdfPoint>.isDot(lineWidth: Float): Boolean {
        val (x, y) = first()
        val reach = lineWidth * 0.25f
        return all { abs(it.x - x) <= reach && abs(it.y - y) <= reach }
    }

    /**
     * A small closed ring around [center]: stroked with the pen it fills in to a round blob a
     * little wider than the pen, which is how a tap with a real pen looks.
     */
    private fun dot(center: PdfPoint, lineWidth: Float): List<PdfPoint> {
        val radius = lineWidth * 0.2f
        return (0..DOT_SIDES).map { i ->
            val angle = 2 * Math.PI * i / DOT_SIDES
            PdfPoint(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
        }
    }

    private const val DOT_SIDES = 8

    private fun PDAnnotationMarkup.stamp(author: String?) {
        if (author != null) titlePopup = author
        creationDate = Calendar.getInstance()
        setModifiedDate(Calendar.getInstance())
        isPrinted = true
    }

    private fun List<PdfRect>.bounds() = PdfRect(
        minOf { it.left }, minOf { it.bottom }, maxOf { it.right }, maxOf { it.top },
    )

    private fun PdfRect.toPdRectangle() = PDRectangle(left, bottom, width, height)
}
