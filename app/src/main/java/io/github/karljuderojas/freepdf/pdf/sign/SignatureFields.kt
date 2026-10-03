package io.github.karljuderojas.freepdf.pdf.sign

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.form.FormFiller
import io.github.karljuderojas.freepdf.pdf.pdfToDisplay
import kotlin.math.abs

/**
 * A place the document asks to be signed. [box] is where the signature goes, as fractions of the
 * page as displayed (0..1, origin top-left), like the taps from [io.github.karljuderojas.freepdf.ui.viewer.TapLayer].
 */
data class SignField(val page: Int, val box: DisplayRect, val source: Source) {

    /** An empty signature field in the PDF's form, or a "Signature" or "Sign here" label in its text. */
    enum class Source { FormField, TextCue }

    /** Whether a signature placed at ([x], [y]) on [page] fills this field. A little below counts: that is the line. */
    fun covers(page: Int, x: Float, y: Float): Boolean =
        page == this.page && x in box.left..box.right && y in box.top..box.bottom + (box.bottom - box.top) / 2
}

/** Finds where a document wants signatures, for the "2 places to sign" banner and Next field. */
object SignatureFields {

    /** Every place to sign, by page and then top to bottom, left to right. */
    fun find(document: PDDocument): List<SignField> {
        val formFields = FormFiller.unsignedSignatureBoxes(document).map { (page, box) -> SignField(page, box, SignField.Source.FormField) }
        val cues = textCues(document).filter { cue ->
            formFields.none { it.page == cue.page && (it.box overlaps cue.box || it.box overlaps cue.label) }
        }
        return (formFields + cues.map { SignField(it.page, it.box, SignField.Source.TextCue) })
            .sortedWith(compareBy({ it.page }, { it.box.top }, { it.box.left }))
    }

    private class Cue(val page: Int, val box: DisplayRect, val label: DisplayRect)

    /**
     * Lines that say "sign here" or mention a signature. The signature goes on a run of
     * underscores in the line if there is one, otherwise just above the label, where the
     * line to sign on usually is.
     *
     * PDFTextStripper reports glyphs in the page's unrotated space, measured from the crop box's
     * top-left (x across, y down to the baseline; getXDirAdj and getYDirAdj for upright text).
     * Each box is worked out there, turned into PDF space and then into display fractions with
     * [pdfToDisplay], the same way as the form's own fields. Sideways text is skipped.
     */
    private fun textCues(document: PDDocument): List<Cue> {
        val glyphs = ArrayList<TextPosition>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String?, textPositions: List<TextPosition>?) {
                textPositions?.filterTo(glyphs) { it.dir == 0f }
            }
        }
        val cues = ArrayList<Cue>()
        for (pageIndex in 0 until document.numberOfPages) {
            glyphs.clear()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            stripper.getText(document)
            val page = document.getPage(pageIndex)
            val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
            fun display(left: Float, top: Float, right: Float, bottom: Float) = pdfToDisplay(
                PdfRect(crop.left + left, crop.top - bottom, crop.left + right, crop.top - top), page.rotation, crop,
            )
            for (line in lines(glyphs)) {
                val label = line.cue() ?: continue
                val first = line.glyphs.first()
                val labelTop = line.baseline - maxOf(first.heightDir, first.fontSizeInPt * 0.7f)
                val labelRight = line.glyphs.last().let { it.xDirAdj + it.widthDirAdj }
                val underscores = line.underscoresAfter(label)
                val box = if (underscores != null) {
                    val bottom = line.baseline + 2f
                    display(underscores.first, bottom - FIELD_HEIGHT, underscores.second, bottom)
                } else {
                    val bottom = labelTop - GAP
                    val left = first.xDirAdj
                    display(left, maxOf(0f, bottom - FIELD_HEIGHT), minOf(left + FIELD_WIDTH, crop.width), bottom)
                }
                cues += Cue(pageIndex, box, display(first.xDirAdj, labelTop, labelRight, line.baseline))
            }
        }
        return cues
    }

    /** One run of text on a baseline, with the glyph behind each character (null for an added space). */
    private class Line(val glyphs: List<TextPosition>) {
        val baseline = glyphs.first().yDirAdj
        val text: String
        val glyphAt: List<TextPosition?>

        init {
            val text = StringBuilder()
            val glyphAt = ArrayList<TextPosition?>()
            glyphs.forEachIndexed { i, glyph ->
                val previous = glyphs.getOrNull(i - 1)
                // Words placed apart without a space character between them still read as two words.
                if (previous != null && glyph.xDirAdj - (previous.xDirAdj + previous.widthDirAdj) > spaceWidth(previous) / 2 &&
                    !previous.unicode.isNullOrBlank() && !glyph.unicode.isNullOrBlank()
                ) {
                    text.append(' ')
                    glyphAt += null
                }
                val chars = glyph.unicode.orEmpty()
                text.append(chars)
                repeat(chars.length) { glyphAt += glyph }
            }
            this.text = text.toString()
            this.glyphAt = glyphAt
        }

        /** Where the cue is in [text], or null if this line does not ask for a signature. */
        fun cue(): IntRange? {
            val lower = text.lowercase()
            val match = SIGN_HERE.find(lower) ?: SIGNATURE.find(lower) ?: return null
            if (UNDERSCORES.containsMatchIn(text)) return match.range
            // A heading such as "Signature" on its own line names a section rather than a place to
            // sign, and a long line is a sentence that mentions signatures, not a label.
            val trimmed = lower.trim()
            return if (trimmed.trimEnd('.') == "signature" || trimmed.length > MAX_LABEL_LENGTH) null else match.range
        }

        /** The left and right of the first run of underscores after [cue], or before it if none follows. */
        fun underscoresAfter(cue: IntRange): Pair<Float, Float>? {
            val runs = UNDERSCORES.findAll(text).toList()
            val run = runs.firstOrNull { it.range.first > cue.last } ?: runs.lastOrNull() ?: return null
            val start = glyphAt[run.range.first] ?: return null
            val end = glyphAt[run.range.last] ?: return null
            return start.xDirAdj to end.xDirAdj + end.widthDirAdj
        }
    }

    /** Groups glyphs into lines by baseline, split where a wide gap leaves separate columns or labels. */
    private fun lines(glyphs: List<TextPosition>): List<Line> {
        val rows = ArrayList<MutableList<TextPosition>>()
        for (glyph in glyphs.sortedWith(compareBy({ it.yDirAdj }, { it.xDirAdj }))) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().yDirAdj - glyph.yDirAdj) <= BASELINE_TOLERANCE) row += glyph else rows += mutableListOf(glyph)
        }
        val lines = ArrayList<Line>()
        for (row in rows) {
            row.sortBy { it.xDirAdj }
            var start = 0
            for (i in 1..row.size) {
                val gap = if (i < row.size) row[i].xDirAdj - (row[i - 1].xDirAdj + row[i - 1].widthDirAdj) else Float.MAX_VALUE
                if (gap > maxOf(COLUMN_GAP, row[i - 1].fontSizeInPt * 3)) {
                    lines += Line(row.subList(start, i).toList())
                    start = i
                }
            }
        }
        return lines
    }

    private fun spaceWidth(glyph: TextPosition): Float =
        glyph.widthOfSpace.takeIf { it.isFinite() && it > 0f } ?: (glyph.fontSizeInPt * 0.25f)

    private infix fun DisplayRect.overlaps(other: DisplayRect) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    // In points: about the size of a handwritten signature, and the space left above a label.
    private const val FIELD_WIDTH = 200f
    private const val FIELD_HEIGHT = 36f
    private const val GAP = 3f
    private const val BASELINE_TOLERANCE = 2f
    private const val COLUMN_GAP = 24f
    private const val MAX_LABEL_LENGTH = 60

    private val SIGN_HERE = Regex("""\bsign\s+here\b""")
    private val SIGNATURE = Regex("""\bsignature\b""")
    private val UNDERSCORES = Regex("_{5,}")
}
