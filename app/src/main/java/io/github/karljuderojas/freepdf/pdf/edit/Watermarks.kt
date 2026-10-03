package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor.uprightAt

/**
 * How a watermark looks: [opacity] from 0 (invisible) to 1, [angle] in degrees counter-clockwise
 * (45 runs from bottom left to top right), and [size] as the fraction of the page's width it spans
 * before turning. [color] only applies to text.
 */
data class WatermarkStyle(
    val opacity: Float = 0.3f,
    val angle: Float = 45f,
    val size: Float = 0.7f,
    val color: Annotator.Rgb = Annotator.Rgb(0.5f, 0.5f, 0.5f),
) {
    val isValid: Boolean get() = opacity in MIN_OPACITY..1f && size in MIN_SIZE..1f && angle in -90f..90f

    companion object {
        const val MIN_OPACITY = 0.05f
        const val MIN_SIZE = 0.1f
    }
}

/** Text or a picture drawn across the middle of pages, on top of what is already there. */
object Watermarks {

    /** Draws [text] (one line per line break) centred on each of [pageIndexes]. */
    fun addText(document: PDDocument, pageIndexes: Collection<Int>, text: String, style: WatermarkStyle) {
        require(style.isValid && text.isNotBlank()) { "Nothing to draw" }
        val font = if (PDType1Font.HELVETICA_BOLD.canShow(text.replace("\n", "").replace('\t', ' '))) {
            PDType1Font.HELVETICA_BOLD
        } else {
            PdfText.fontFor(document, text)
        }
        val lines = PdfText.lines(PdfText.printable(text, font))
        val widest = lines.maxOf { PdfText.widthOf(it, font, 1f) }.coerceAtLeast(0.001f)
        forEachPage(document, pageIndexes) { page, center, displayWidth ->
            val fontSize = (style.size * displayWidth / widest).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
            val lead = fontSize * 1.2f
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.uprightAt(page, center) {
                    applyLook(style)
                    setNonStrokingColor(style.color.r, style.color.g, style.color.b)
                    lines.forEachIndexed { i, line ->
                        val width = PdfText.widthOf(line, font, fontSize)
                        // The block of lines is centred on the origin; a line's baseline sits a
                        // third of the font size below the middle of its row, so caps look centred.
                        val y = (lines.size - 1) / 2f * lead - i * lead - fontSize * 0.33f
                        beginText()
                        setFont(font, fontSize)
                        newLineAtOffset(-width / 2, y)
                        showText(line)
                        endText()
                    }
                }
            }
        }
    }

    /** Draws [image] centred on each of [pageIndexes], as wide as [WatermarkStyle.size] says. */
    fun addImage(document: PDDocument, pageIndexes: Collection<Int>, image: Bitmap, style: WatermarkStyle) {
        require(style.isValid && image.width > 0 && image.height > 0) { "Nothing to draw" }
        val xObject = if (image.hasAlpha()) LosslessFactory.createFromImage(document, image) else JPEGFactory.createFromImage(document, image, 0.85f)
        val aspect = image.height.toFloat() / image.width
        forEachPage(document, pageIndexes) { page, center, displayWidth ->
            val width = style.size * displayWidth
            val height = width * aspect
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.uprightAt(page, center) {
                    applyLook(style)
                    drawImage(xObject, -width / 2, -height / 2, width, height)
                }
            }
        }
    }

    /** Sets the opacity and turns the axes by [WatermarkStyle.angle], about the current origin. */
    private fun PDPageContentStream.applyLook(style: WatermarkStyle) {
        setGraphicsStateParameters(
            PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = style.opacity
                strokingAlphaConstant = style.opacity
            },
        )
        if (style.angle != 0f) transform(Matrix.getRotateInstance(Math.toRadians(style.angle.toDouble()), 0f, 0f))
    }

    /** Runs [draw] for each page with its middle (in the page's own space) and shown width in points. */
    private fun forEachPage(
        document: PDDocument,
        pageIndexes: Collection<Int>,
        draw: (page: com.tom_roush.pdfbox.pdmodel.PDPage, center: PdfPoint, displayWidth: Float) -> Unit,
    ) {
        pageIndexes.toSortedSet().forEach { index ->
            val page = document.getPage(index)
            val box = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
            val displayWidth = if (page.rotation % 180 == 0) box.width else box.height
            draw(page, displayToPdf(0.5f, 0.5f, page.rotation, box), displayWidth)
        }
    }

    private const val MIN_FONT_SIZE = 6f
    private const val MAX_FONT_SIZE = 400f
}
