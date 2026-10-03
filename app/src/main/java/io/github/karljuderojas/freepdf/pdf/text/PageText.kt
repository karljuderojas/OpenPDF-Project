package io.github.karljuderojas.freepdf.pdf.text

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.onPage
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
 * Text that reads upright as the page is shown is boxed, which on a page with a /Rotate (a scan,
 * a landscape export) is text drawn turned by that angle. Text along the page's own x axis is
 * boxed too, turned along with the page; text at any other angle is skipped. Text in a part of
 * the page a crop has taken away is skipped as well, and a word crossing the edge is cut to it,
 * so nothing off the page can be selected.
 */
object PageText {

    fun words(document: PDDocument, pageIndex: Int): List<PageWord> {
        val page = document.getPage(pageIndex)
        val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
        val rotation = (((page.rotation % 360) + 360) % 360).toFloat()
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
                if (text.isBlank()) return null
                val upright = glyphs.filter { it.dir == rotation }
                if (upright.isNotEmpty()) return uprightBoxOf(text, upright)
                val horizontal = glyphs.filter { it.dir == 0f }
                if (horizontal.isEmpty()) return null
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

            /**
             * Glyphs that read upright on the displayed page. PdfBox's direction-adjusted
             * coordinates are already in that frame: crop-relative, turned by the page's rotation,
             * origin top-left, so they only need dividing by the displayed page size.
             */
            private fun uprightBoxOf(text: String, glyphs: List<TextPosition>): PageWord {
                val left = glyphs.minOf { it.xDirAdj }
                val right = glyphs.maxOf { it.xDirAdj + it.widthDirAdj }
                val baseline = glyphs.maxOf { it.yDirAdj }
                val size = glyphs.maxOf { glyph -> glyph.fontSizeInPt.takeIf { it > 0f } ?: (glyph.heightDir * 1.4f) }
                val top = baseline - size * 0.8f
                val bottom = baseline + size * 0.22f
                val turned = rotation == 90f || rotation == 270f
                val shownWidth = if (turned) crop.height else crop.width
                val shownHeight = if (turned) crop.width else crop.height
                return PageWord(text, line, left / shownWidth, top / shownHeight, right / shownWidth, bottom / shownHeight)
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(document)
        return words.mapNotNull { it.onPage() }
    }

    private fun PageWord.onPage(): PageWord? = DisplayRect(left, top, right, bottom).onPage()?.let {
        copy(left = it.left, top = it.top, right = it.right, bottom = it.bottom)
    }
}
