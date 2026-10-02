package io.github.karljuderojas.freepdf.pdf.annotate

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import java.util.Calendar

/**
 * Standard PDF annotations, so marks made here show up in Acrobat, Xodo, browsers and others.
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

    enum class TextMarkup(val subtype: String) {
        Highlight(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT),
        Underline(PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE),
        StrikeOut(PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT),
    }

    /** Highlights, underlines or strikes out one or more line rectangles (one per text line). */
    fun markText(
        document: PDDocument,
        pageIndex: Int,
        lines: List<PdfRect>,
        kind: TextMarkup = TextMarkup.Highlight,
        color: Rgb = Rgb.Yellow,
        author: String? = null,
    ) {
        require(lines.isNotEmpty())
        val annotation = PDAnnotationTextMarkup(kind.subtype).apply {
            rectangle = lines.bounds().toPdRectangle()
            // Quad points per line: top-left, top-right, bottom-left, bottom-right.
            quadPoints = lines.flatMap {
                listOf(it.left, it.top, it.right, it.top, it.left, it.bottom, it.right, it.bottom)
            }.toFloatArray()
            this.color = color.toPdColor()
            stamp(author)
        }
        document.getPage(pageIndex).annotations.add(annotation)
        annotation.constructAppearances(document)
    }

    /** Freehand drawing. Each stroke is a list of points in PDF user space. */
    fun ink(
        document: PDDocument,
        pageIndex: Int,
        strokes: List<List<PdfPoint>>,
        color: Rgb = Rgb.Blue,
        lineWidth: Float = 2f,
        author: String? = null,
    ) {
        val points = strokes.flatten()
        require(points.isNotEmpty())
        val annotation = PDAnnotationMarkup().apply {
            cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
            inkList = strokes.map { stroke -> stroke.flatMap { listOf(it.x, it.y) }.toFloatArray() }.toTypedArray()
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
    fun note(document: PDDocument, pageIndex: Int, at: PdfPoint, text: String, author: String? = null) {
        val annotation = PDAnnotationText().apply {
            rectangle = PdfRect(at.x, at.y - 20f, at.x + 20f, at.y).toPdRectangle()
            contents = text
            name = PDAnnotationText.NAME_COMMENT
            color = Rgb.Yellow.toPdColor()
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
