package io.github.karljuderojas.freepdf.pdf.annotate

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.annotate.Appearances.stamp
import io.github.karljuderojas.freepdf.pdf.annotate.Appearances.toPdRectangle
import io.github.karljuderojas.freepdf.pdf.annotate.Appearances.toPdfRect
import io.github.karljuderojas.freepdf.pdf.edit.PdfText
import java.util.Calendar
import java.util.Locale

/**
 * Text boxes as standard FreeText annotations, so they stay editable here and in other viewers
 * (unlike PageEditor.addText, which burns text into the page).
 *
 * The appearance is drawn here rather than by PdfBox's FreeText handler, which only knows the
 * standard Helvetica and so cannot show Polish or Cyrillic text.
 */
object TextBoxes {

    /** Space between the text and the edge of the box, in points. */
    private const val PADDING = 4f
    private const val LINE_SPACING = 1.2f

    /** The text style stored on a box, for prefilling the editor when the user taps it. */
    data class Style(val color: Annotator.Rgb, val fontSize: Float)

    /**
     * Adds a text box whose top-left corner, as the page is shown, is at [at]. The box grows
     * to fit the text; line breaks start new lines.
     */
    fun add(
        document: PDDocument,
        pageIndex: Int,
        at: PdfPoint,
        text: String,
        color: Annotator.Rgb,
        fontSize: Float = 12f,
        author: String? = null,
    ): PDAnnotationMarkup {
        val page = document.getPage(pageIndex)
        val rotation = Appearances.normalizedRotation(page.rotation)
        val annotation = PDAnnotationMarkup().apply {
            cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_FREETEXT)
            // No border: viewers that redraw the box from /DA would otherwise add one.
            borderStyle = PDBorderStyleDictionary().apply { width = 0f }
            // Remembered so edit() keeps the box upright even if the page is turned later.
            if (rotation != 0) cosObject.setInt(COSName.ROTATE, rotation)
            stamp(author)
        }
        draw(document, annotation, at, text, color, fontSize, rotation)
        page.annotations.add(annotation)
        return annotation
    }

    /** Replaces the text and style of an existing box, keeping its top-left corner where it is. */
    fun edit(document: PDDocument, annotation: PDAnnotationMarkup, text: String, color: Annotator.Rgb, fontSize: Float) {
        val rotation = Appearances.normalizedRotation(annotation.cosObject.getInt(COSName.ROTATE, 0))
        val topLeft = Appearances.topLeftOf(annotation.rectangle.toPdfRect(), rotation)
        draw(document, annotation, topLeft, text, color, fontSize, rotation)
        annotation.setModifiedDate(Calendar.getInstance())
    }

    /**
     * Reads back the colour and size from a box's /DA, as written by [add]. Null when the
     * string is missing or in a form this does not understand (e.g. a box made elsewhere).
     */
    fun styleOf(annotation: PDAnnotationMarkup): Style? {
        val parts = annotation.defaultAppearance?.trim()?.split(Regex("\\s+")) ?: return null
        val tf = parts.indexOf("Tf")
        val rg = parts.indexOf("rg")
        if (tf < 1 || rg < 3) return null
        val size = parts[tf - 1].toFloatOrNull() ?: return null
        val (r, g, b) = (rg - 3 until rg).map { parts[it].toFloatOrNull() ?: return null }
        return Style(Annotator.Rgb(r, g, b), size)
    }

    private fun draw(
        document: PDDocument,
        annotation: PDAnnotationMarkup,
        topLeft: PdfPoint,
        text: String,
        color: Annotator.Rgb,
        fontSize: Float,
        rotation: Int,
    ) {
        val font = PdfText.fontFor(document, text)
        val lines = PdfText.lines(PdfText.printable(text, font))
        val leading = fontSize * LINE_SPACING
        val textWidth = lines.maxOf { font.getStringWidth(it) } / 1000f * fontSize
        val width = maxOf(textWidth, fontSize) + 2 * PADDING
        val height = lines.size * leading + 2 * PADDING

        annotation.contents = text
        // /DA and /DS let other viewers redraw the box with the same look if the user edits it
        // there. Helv is the conventional AcroForm name for Helvetica.
        annotation.defaultAppearance = String.format(
            Locale.US, "/Helv %.2f Tf %.3f %.3f %.3f rg", fontSize, color.r, color.g, color.b,
        )
        annotation.defaultStyleString = String.format(
            Locale.US, "font: Helvetica %.2fpt; text-align:left; color:#%02X%02X%02X",
            fontSize, (color.r * 255).toInt(), (color.g * 255).toInt(), (color.b * 255).toInt(),
        )
        annotation.rectangle = Appearances.rectFromTopLeft(topLeft, width, height, rotation).toPdRectangle()

        Appearances.set(document, annotation, width, height, rotation) {
            beginText()
            setFont(font, fontSize)
            setNonStrokingColor(color.r, color.g, color.b)
            setLeading(leading)
            // The first baseline sits one font size below the top padding; with 1.2 line
            // spacing that leaves room for descenders on the last line.
            newLineAtOffset(PADDING, height - PADDING - fontSize)
            lines.forEachIndexed { i, line ->
                if (i > 0) newLine()
                showText(line)
            }
            endText()
        }
    }
}
