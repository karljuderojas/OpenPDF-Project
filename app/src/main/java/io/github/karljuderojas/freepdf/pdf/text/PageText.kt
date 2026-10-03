package io.github.karljuderojas.freepdf.pdf.text

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.pdfToDisplay

/**
 * One word on a page as displayed: its box as fractions of the page (0..1 across and down,
 * origin top-left, the same space as the Annotate overlays), and which line it is on. Words come
 * in reading order, so a range of indices is a run of text.
 */
data class PageWord(
    val text: String,
    val line: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Finds the words on a page and where they sit, so a selection or a highlight can snap to whole
 * words. Scanned pages have no text and give an empty list.
 *
 * Only left-to-right text along the page's own x axis is boxed (that is, horizontal text, shown
 * turned along with the page when the page has a /Rotate); sideways text is skipped.
 */
object PageText {

    fun words(document: PDDocument, pageIndex: Int): List<PageWord> {
        val page = document.getPage(pageIndex)
        val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
        val words = ArrayList<PageWord>()
        val stripper = object : PDFTextStripper() {
            private var line = 0

            // Called once per run of text, which is usually a whole line with its spaces in it,
            // so the words are split out here at whitespace glyphs.
            override fun writeString(text: String, textPositions: List<TextPosition>) {
                val word = ArrayList<TextPosition>()
                fun flush() {
                    if (word.isNotEmpty()) boxOf(word.joinToString("") { it.unicode }, word)?.let { words += it }
                    word.clear()
                }
                textPositions.forEach { glyph ->
                    if (glyph.unicode.isNullOrBlank()) flush() else word += glyph
                }
                flush()
            }

            override fun writeLineSeparator() {
                line++
            }

            private fun boxOf(text: String, glyphs: List<TextPosition>): PageWord? {
                val horizontal = glyphs.filter { it.dir == 0f }
                if (text.isBlank() || horizontal.isEmpty()) return null
                // The text matrix is in user space, shifted so the crop box starts at 0,0.
                val left = horizontal.minOf { it.textMatrix.translateX } + crop.left
                val right = horizontal.maxOf { it.textMatrix.translateX + it.widthDirAdj } + crop.left
                val baseline = horizontal.maxOf { it.textMatrix.translateY } + crop.bottom
                val size = horizontal.maxOf { glyph ->
                    glyph.fontSizeInPt.takeIf { it > 0f } ?: (glyph.heightDir * 1.4f)
                }
                // Typical ascender and descender heights, so boxes cover capitals and tails.
                val top = baseline + size * 0.8f
                val bottom = baseline - size * 0.22f
                val (x1, y1) = pdfToDisplay(left, top, page.rotation, crop)
                val (x2, y2) = pdfToDisplay(right, bottom, page.rotation, crop)
                return PageWord(text, line, minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(document)
        return words
    }
}
