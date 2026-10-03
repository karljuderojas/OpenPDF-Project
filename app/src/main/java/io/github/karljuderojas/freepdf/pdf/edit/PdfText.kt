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

    const val LIBERATION_SANS = "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"

    fun fontFor(document: PDDocument, text: String): PDFont {
        // Line breaks and tabs are handled by the callers, so they must not force the fallback font.
        if (PDType1Font.HELVETICA.canShow(text.replace("\n", "").replace('\t', ' '))) return PDType1Font.HELVETICA
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

    /** True when every letter in [text] is one Liberation Sans can show (digits and punctuation included). */
    fun isLatinGreekOrCyrillic(text: String): Boolean = text.codePoints().allMatch { cp ->
        !Character.isLetter(cp) || Character.UnicodeScript.of(cp) in SUPPORTED_SCRIPTS
    } && text.codePoints().allMatch { !Character.isDigit(it) || Character.UnicodeBlock.of(it) == Character.UnicodeBlock.BASIC_LATIN }

    private val SUPPORTED_SCRIPTS = setOf(
        Character.UnicodeScript.LATIN, Character.UnicodeScript.GREEK, Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.COMMON,
    )

    /** Splits on line breaks and drops a trailing empty line left by a final Enter. */
    fun lines(text: String): List<String> = text.split('\n').dropLastWhile { it.isBlank() }.ifEmpty { listOf("") }

    private fun PDFont.canShow(text: String) = runCatching { encode(text) }.isSuccess
}
