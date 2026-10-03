package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor.unshareContents
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor.uprightAt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

/**
 * Text or a picture drawn across the middle of pages, on top of what is already there. Each
 * watermark goes in its own content stream, marked `/Artifact <</Subtype /Watermark>> BDC ... EMC`,
 * so [remove] can find and drop it again later, in another session too.
 */
object Watermarks {

    /**
     * Draws [text] (one line per line break) centred on each of [pageIndexes]. [text] must pass
     * [PdfText.isLatinGreekOrCyrillic]: other letters have no glyph in the fonts at hand and would
     * come out as "?", so they are refused instead.
     */
    fun addText(document: PDDocument, pageIndexes: Collection<Int>, text: String, style: WatermarkStyle) {
        require(style.isValid && text.isNotBlank()) { "Nothing to draw" }
        require(PdfText.isLatinGreekOrCyrillic(text)) { "No font here can show this text" }
        val font = PdfText.boldFontFor(document, text)
        val lines = PdfText.lines(PdfText.printable(text, font))
        val widest = lines.maxOf { PdfText.widthOf(it, font, 1f) }.coerceAtLeast(0.001f)
        val blockHeight = 1f + (lines.size - 1) * LEADING
        forEachPage(document, pageIndexes) { page, center, displayWidth, displayHeight ->
            // [size] says how much of the width the text spans before turning; the turned block
            // must also stay inside the page both ways, or steep angles run off wide pages.
            val fontSize = minOf(style.size * displayWidth / widest, fitting(widest, blockHeight, style.angle, displayWidth, displayHeight))
                .coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
            val lead = fontSize * LEADING
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.asWatermark {
                    uprightAt(page, center) {
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
    }

    /** Draws [image] centred on each of [pageIndexes], as wide as [WatermarkStyle.size] says. */
    fun addImage(document: PDDocument, pageIndexes: Collection<Int>, image: Bitmap, style: WatermarkStyle) {
        require(style.isValid && image.width > 0 && image.height > 0) { "Nothing to draw" }
        val xObject = if (image.hasAlpha()) LosslessFactory.createFromImage(document, image) else JPEGFactory.createFromImage(document, image, 0.85f)
        val aspect = image.height.toFloat() / image.width
        forEachPage(document, pageIndexes) { page, center, displayWidth, displayHeight ->
            val width = minOf(style.size * displayWidth, fitting(1f, aspect, style.angle, displayWidth, displayHeight))
            val height = width * aspect
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.asWatermark {
                    uprightAt(page, center) {
                        applyLook(style)
                        drawImage(xObject, -width / 2, -height / 2, width, height)
                    }
                }
            }
        }
    }

    /**
     * Takes every watermark this app added off each of [pageIndexes]. Returns false when none of
     * the pages had one, so the caller can skip the undo step.
     */
    fun remove(document: PDDocument, pageIndexes: Collection<Int>): Boolean {
        var removed = false
        pageIndexes.toSortedSet().forEach { index ->
            val page = document.getPage(index)
            val dictionary = page.cosObject
            when (val contents = dictionary.getDictionaryObject(COSName.CONTENTS)) {
                is COSStream -> {
                    val resets = watermarkResets(contents, page.resources) ?: return@forEach
                    val stub = resetStream(document, resets)
                    if (stub == null) dictionary.removeItem(COSName.CONTENTS) else dictionary.setItem(COSName.CONTENTS, stub)
                    removed = true
                }
                is COSArray -> {
                    val kept = COSArray()
                    for (i in 0 until contents.size()) {
                        val item = contents.getObject(i)
                        val resets = (item as? COSStream)?.let { watermarkResets(it, page.resources) }
                        if (resets == null) {
                            kept.add(contents.get(i))
                        } else {
                            removed = true
                            // The Q that undoes the q PdfBox put in front of the page stays, so the page's own state is still reset.
                            resetStream(document, resets)?.let { kept.add(it) }
                        }
                    }
                    if (removed) dictionary.setItem(COSName.CONTENTS, kept)
                }
                else -> Unit
            }
        }
        return removed
    }

    /**
     * When [stream] is nothing but a watermark this app added, the number of `Q` operators in
     * front of it; null otherwise. Apart from those `Q`s, the first operator must open an
     * `/Artifact` whose properties carry [APP_KEY] with `/Subtype /Watermark`, and the matching
     * `EMC` must be the stream's last operator, so a stream that also holds the page's own
     * content, or another tool's watermark, is never touched.
     */
    private fun watermarkResets(stream: COSStream, resources: PDResources?): Int? {
        val tokens = runCatching { PDFStreamParser(stream).also { it.parse() }.tokens }.getOrNull() ?: return null
        val operators = tokens.indices.filter { tokens[it] is Operator }
        val first = operators.firstOrNull { (tokens[it] as Operator).name != "Q" } ?: return null
        val resets = operators.indexOf(first)
        if ((tokens[first] as Operator).name != "BDC") return null
        var depth = 0
        for (i in operators.drop(resets)) {
            when ((tokens[i] as Operator).name) {
                "BDC", "BMC" -> depth++
                "EMC" -> depth--
            }
            // The section that opened first closes before the end: what follows is not the watermark.
            if (depth == 0 && i != operators.last()) return null
        }
        if (depth != 0) return null
        val tag = tokens.getOrNull(first - 2) as? COSName
        val properties = when (val operand = tokens.getOrNull(first - 1)) {
            is COSDictionary -> operand
            is COSName -> resources?.getProperties(operand)?.cosObject
            else -> null
        }
        if (tag != COSName.ARTIFACT || properties == null) return null
        return if (properties.getCOSName(COSName.SUBTYPE) == WATERMARK && properties.getBoolean(APP_KEY, false)) resets else null
    }

    /** A stream holding only [count] `Q` operators, or null when there is nothing to keep. */
    private fun resetStream(document: PDDocument, count: Int): COSStream? {
        if (count == 0) return null
        val stream = document.document.createCOSStream()
        stream.createOutputStream(COSName.FLATE_DECODE).use { it.write("Q\n".repeat(count).toByteArray(Charsets.US_ASCII)) }
        return stream
    }

    /** Wraps what [draw] writes in the marked-content section that [remove] looks for. */
    private fun PDPageContentStream.asWatermark(draw: PDPageContentStream.() -> Unit) {
        val properties = PDPropertyList.create(
            COSDictionary().apply {
                setItem(COSName.SUBTYPE, WATERMARK)
                setBoolean(APP_KEY, true)
            },
        )
        beginMarkedContent(COSName.ARTIFACT, properties)
        draw()
        endMarkedContent()
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

    /**
     * The largest scale at which a [width] by [height] box, turned by [angle] degrees, still fits
     * inside [pageWidth] by [pageHeight]: its turned bounding box is measured against both.
     */
    private fun fitting(width: Float, height: Float, angle: Float, pageWidth: Float, pageHeight: Float): Float {
        val radians = Math.toRadians(angle.toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        val turnedWidth = (width * c + height * s).coerceAtLeast(0.001f)
        val turnedHeight = (width * s + height * c).coerceAtLeast(0.001f)
        return minOf(pageWidth / turnedWidth, pageHeight / turnedHeight)
    }

    /** Runs [draw] for each page with its middle (in the page's own space) and shown size in points. */
    private fun forEachPage(
        document: PDDocument,
        pageIndexes: Collection<Int>,
        draw: (page: PDPage, center: PdfPoint, displayWidth: Float, displayHeight: Float) -> Unit,
    ) {
        pageIndexes.toSortedSet().forEach { index ->
            val page = document.getPage(index)
            page.unshareContents()
            val box = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
            val upright = page.rotation % 180 == 0
            val displayWidth = if (upright) box.width else box.height
            val displayHeight = if (upright) box.height else box.width
            draw(page, displayToPdf(0.5f, 0.5f, page.rotation, box), displayWidth, displayHeight)
        }
    }

    private val WATERMARK: COSName = COSName.getPDFName("Watermark")

    /** Marks a watermark as this app's, so [remove] leaves other tools' watermarks alone. */
    private val APP_KEY: COSName = COSName.getPDFName("FreePDF")
    private const val LEADING = 1.2f
    private const val MIN_FONT_SIZE = 6f
    private const val MAX_FONT_SIZE = 400f
}
