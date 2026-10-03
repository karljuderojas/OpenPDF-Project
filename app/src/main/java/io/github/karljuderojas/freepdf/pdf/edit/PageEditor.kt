package io.github.karljuderojas.freepdf.pdf.edit

import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
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

    /** Draws plain text onto a page, on top of the existing content. */
    fun addText(
        document: PDDocument,
        pageIndex: Int,
        text: String,
        at: PdfPoint,
        fontSize: Float = 12f,
    ) {
        val page = document.getPage(pageIndex)
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, fontSize)
            stream.newLineAtOffset(at.x, at.y)
            stream.showText(text)
            stream.endText()
        }
    }

    /** Draws a checkmark whose bottom point sits at [at]. [size] is its height in points. */
    fun addCheckmark(document: PDDocument, pageIndex: Int, at: PdfPoint, size: Float = 10f) {
        val page = document.getPage(pageIndex)
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.setLineWidth(size / 6)
            stream.setLineCapStyle(1)
            stream.setLineJoinStyle(1)
            stream.moveTo(at.x - size * 0.4f, at.y + size * 0.4f)
            stream.lineTo(at.x, at.y)
            stream.lineTo(at.x + size * 0.6f, at.y + size)
            stream.stroke()
        }
    }
}
