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

    /** [fontFor] in bold where the standard bold Helvetica can show [text]; other scripts stay regular. */
    fun boldFontFor(document: PDDocument, text: String): PDFont =
        PDType1Font.HELVETICA_BOLD.takeIf { it.canShow(text.replace("\n", "").replace('\t', ' ')) } ?: fontFor(document, text)

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

    /** The width of [text] set in [font] at [fontSize], in points. */
    fun widthOf(text: String, font: PDFont, fontSize: Float): Float = font.getStringWidth(text) / 1000f * fontSize

    /**
     * [lines] of [text], each then wrapped at spaces so no line is wider than [maxWidth] points
     * in [font] at [fontSize]. A single word wider than that is broken between letters. [text]
     * must already be [printable] in [font].
     */
    fun wrap(text: String, font: PDFont, fontSize: Float, maxWidth: Float): List<String> = lines(text).flatMap { line ->
        wrapLine(line, font, fontSize, maxWidth)
    }

    private fun wrapLine(line: String, font: PDFont, fontSize: Float, maxWidth: Float): List<String> {
        if (line.isEmpty() || widthOf(line, font, fontSize) <= maxWidth) return listOf(line)
        val out = ArrayList<String>()
        var current = ""
        fun flush() {
            if (current.isNotEmpty()) out += current
            current = ""
        }
        line.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            when {
                widthOf(candidate, font, fontSize) <= maxWidth -> current = candidate
                widthOf(word, font, fontSize) <= maxWidth -> {
                    flush()
                    current = word
                }
                else -> {
                    // Too long for a line on its own: take as many letters as fit, line by line.
                    flush()
                    word.codePoints().forEach { cp ->
                        val letter = String(Character.toChars(cp))
                        if (current.isNotEmpty() && widthOf(current + letter, font, fontSize) > maxWidth) flush()
                        current += letter
                    }
                }
            }
        }
        flush()
        return out.ifEmpty { listOf("") }
    }

    private fun PDFont.canShow(text: String) = runCatching { encode(text) }.isSuccess
}
