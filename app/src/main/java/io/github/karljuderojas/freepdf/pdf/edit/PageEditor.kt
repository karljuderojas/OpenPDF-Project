package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import io.github.karljuderojas.freepdf.pdf.PdfPoint

/** Page-level and content edits. All indexes are zero-based. */
object PageEditor {

    fun rotate(document: PDDocument, pageIndex: Int, degrees: Int) {
        require(degrees % 90 == 0) { "Rotation must be a multiple of 90 degrees" }
        val page = document.getPage(pageIndex)
        page.rotation = ((page.rotation + degrees) % 360 + 360) % 360
    }

    fun delete(document: PDDocument, pageIndex: Int) {
        document.removePage(pageIndex)
    }

    /**
     * Removes every page not in [pages], so the rest keep their document order. Used to make a
     * new PDF from some of the pages; [pages] must name at least one page that exists.
     */
    fun keepOnly(document: PDDocument, pages: List<Int>) {
        val keep = pages.toSet()
        require(keep.isNotEmpty() && keep.all { it in 0 until document.numberOfPages }) { "No such pages: $pages" }
        for (index in document.numberOfPages - 1 downTo 0) {
            if (index !in keep) document.removePage(index)
        }
    }

    fun move(document: PDDocument, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val pages = document.pages
        val page = pages.get(fromIndex)
        pages.remove(fromIndex)
        if (toIndex >= pages.count) pages.add(page) else pages.insertBefore(page, pages.get(toIndex))
    }

    fun insertBlank(document: PDDocument, atIndex: Int, size: PDRectangle = PDRectangle.LETTER) {
        val page = PDPage(size)
        val pages = document.pages
        if (atIndex >= pages.count) pages.add(page) else pages.insertBefore(page, pages.get(atIndex))
    }

    /** Appends every page of [other] to the end of [target]. */
    fun append(target: PDDocument, other: PDDocument) {
        PDFMergerUtility().appendDocument(target, other)
    }

    /**
     * Draws plain text onto a page, on top of the existing content, upright as the page is shown.
     * Line breaks start a new line below; characters no font here can show become "?".
     */
    fun addText(
        document: PDDocument,
        pageIndex: Int,
        text: String,
        at: PdfPoint,
        fontSize: Float = 12f,
    ) {
        val page = document.getPage(pageIndex)
        val font = PdfText.fontFor(document, text)
        val lines = PdfText.lines(PdfText.printable(text, font))
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.uprightAt(page, at) {
                beginText()
                setFont(font, fontSize)
                setLeading(fontSize * 1.2f)
                newLineAtOffset(0f, 0f)
                lines.forEachIndexed { i, line ->
                    if (i > 0) newLine()
                    showText(line)
                }
                endText()
            }
        }
    }

    /** Draws a checkmark whose bottom point sits at [at]. [size] is its height in points. */
    fun addCheckmark(document: PDDocument, pageIndex: Int, at: PdfPoint, size: Float = 10f) {
        val page = document.getPage(pageIndex)
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.uprightAt(page, at) {
                setLineWidth(size / 6)
                setLineCapStyle(1)
                setLineJoinStyle(1)
                moveTo(-size * 0.4f, size * 0.4f)
                lineTo(0f, 0f)
                lineTo(size * 0.6f, size)
                stroke()
            }
        }
    }

    /**
     * Draws [image] [width] by [height] points with its bottom-left corner at [at], upright as the
     * page is shown. Photos are stored as JPEG to keep the file small; pictures with transparency
     * (logos, screenshots with cut-outs) are stored losslessly so the page shows through.
     */
    fun addImage(document: PDDocument, pageIndex: Int, image: Bitmap, at: PdfPoint, width: Float, height: Float) {
        val page = document.getPage(pageIndex)
        val xObject = if (image.hasAlpha()) {
            LosslessFactory.createFromImage(document, image)
        } else {
            JPEGFactory.createFromImage(document, image, JPEG_QUALITY)
        }
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.uprightAt(page, at) { drawImage(xObject, 0f, 0f, width, height) }
        }
    }

    private const val JPEG_QUALITY = 0.85f

    /**
     * Runs [draw] with the origin at [at] and the axes turned so that what it draws is upright
     * when the page is shown. Content is stored in the page's unrotated space, so on a page with
     * /Rotate 90 (common for scans) text drawn the plain way comes out sideways.
     */
    internal fun PDPageContentStream.uprightAt(page: PDPage, at: PdfPoint, draw: PDPageContentStream.() -> Unit) {
        saveGraphicsState()
        transform(Matrix.getTranslateInstance(at.x, at.y))
        val rotation = ((page.rotation % 360) + 360) % 360
        if (rotation != 0) transform(Matrix.getRotateInstance(Math.toRadians(rotation.toDouble()), 0f, 0f))
        draw()
        restoreGraphicsState()
    }
}
