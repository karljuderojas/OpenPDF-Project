package io.github.karljuderojas.freepdf.pdf.edit

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

/**
 * Picks a font that can show [text]. The standard Helvetica only covers Western European
 * characters, so a Polish or Russian date, or a name with other letters, throws on showText.
 * For those, PdfBox-Android's bundled Liberation Sans (Latin, Greek, Cyrillic) is embedded instead.
 */
object PdfText {

    private const val LIBERATION_SANS = "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"

    fun fontFor(document: PDDocument, text: String): PDFont {
        if (PDType1Font.HELVETICA.canShow(text)) return PDType1Font.HELVETICA
        return PDType0Font.load(document, PDFBoxResourceLoader.getStream(LIBERATION_SANS))
    }

    /**
     * [text] with tabs turned into spaces and any character [font] has no glyph for replaced by
     * "?", so showText never throws. Line breaks are kept: callers draw one line at a time.
     */
    fun printable(text: String, font: PDFont): String {
        val tabsToSpaces = text.replace('\t', ' ')
        if (font.canShow(tabsToSpaces.replace("\n", ""))) return tabsToSpaces
        return buildString {
            tabsToSpaces.codePoints().forEach { cp ->
                val s = String(Character.toChars(cp))
                append(if (cp == '\n'.code || font.canShow(s)) s else "?")
            }
        }
    }

    /** Splits on line breaks and drops a trailing empty line left by a final Enter. */
    fun lines(text: String): List<String> = text.split('\n').dropLastWhile { it.isBlank() }.ifEmpty { listOf("") }

    private fun PDFont.canShow(text: String) = runCatching { encode(text) }.isSuccess
}
