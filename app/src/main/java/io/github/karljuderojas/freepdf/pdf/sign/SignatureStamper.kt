package io.github.karljuderojas.freepdf.pdf.sign

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import io.github.karljuderojas.freepdf.pdf.PdfRect

/**
 * Draws the visible signature (a drawn, typed or uploaded image) into the page content, with a
 * small caption underneath. This is the "looks signed" half; [DigitalSigner] is the tamper-evident half.
 */
object SignatureStamper {

    fun stamp(
        document: PDDocument,
        pageIndex: Int,
        signature: Bitmap,
        box: PdfRect,
        caption: String? = null,
    ) {
        val page = document.getPage(pageIndex)
        val image = LosslessFactory.createFromImage(document, signature)
        // Fit inside the box, keeping the image's aspect ratio.
        val scale = minOf(box.width / image.width, box.height / image.height)
        val width = image.width * scale
        val height = image.height * scale
        val x = box.left + (box.width - width) / 2
        val y = box.bottom + (box.height - height) / 2

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.drawImage(image, x, y, width, height)
            if (caption != null) {
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 6f)
                stream.newLineAtOffset(box.left, box.bottom - 8f)
                stream.showText(caption)
                stream.endText()
            }
        }
    }
}
