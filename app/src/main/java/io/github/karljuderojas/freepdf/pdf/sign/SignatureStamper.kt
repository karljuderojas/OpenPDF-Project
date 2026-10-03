package io.github.karljuderojas.freepdf.pdf.sign

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor.uprightAt
import io.github.karljuderojas.freepdf.pdf.edit.PdfText

/**
 * Draws the visible signature (a drawn, typed or uploaded image) into the page content, with a
 * small caption underneath. This is the "looks signed" half; [DigitalSigner] is the tamper-evident half.
 */
object SignatureStamper {

    /**
     * Fits [signature] into [maxWidth] by [maxHeight] points, keeping its aspect ratio, centred
     * over [at] with its bottom on [at]. Sizes are as the page is shown, so on a rotated page the
     * signature still sits upright on the line the user tapped.
     */
    fun stamp(
        document: PDDocument,
        pageIndex: Int,
        signature: Bitmap,
        at: PdfPoint,
        maxWidth: Float,
        maxHeight: Float,
        caption: String? = null,
    ) {
        val page = document.getPage(pageIndex)
        val image = LosslessFactory.createFromImage(document, signature)
        val scale = minOf(maxWidth / image.width, maxHeight / image.height)
        val width = image.width * scale
        val height = image.height * scale
        val x = -width / 2
        val y = 0f

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.uprightAt(page, at) {
                drawImage(image, x, y, width, height)
                if (caption != null) {
                    val font = PdfText.fontFor(document, caption)
                    beginText()
                    setFont(font, 6f)
                    newLineAtOffset(x, -8f)
                    showText(PdfText.printable(caption, font).replace('\n', ' '))
                    endText()
                }
            }
        }
    }
}
