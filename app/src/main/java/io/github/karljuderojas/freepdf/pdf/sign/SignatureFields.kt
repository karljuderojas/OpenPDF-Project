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

    /** How much of this field's box [other] shares, as a fraction of it: 0 unless both are on the same page and overlap. */
    fun overlapWith(other: SignField): Float {
        if (page != other.page) return 0f
        val width = minOf(box.right, other.box.right) - maxOf(box.left, other.box.left)
        val height = minOf(box.bottom, other.box.bottom) - maxOf(box.top, other.box.top)
        if (width <= 0f || height <= 0f) return 0f
        val area = (box.right - box.left) * (box.bottom - box.top)
        return if (area > 0f) width * height / area else 0f
    }
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

    /**
     * Where [field] is in [fields] once they have been found again. The places to sign are found
     * afresh after pages move, when their order and number can change, so the one Next field went
     * to is kept by what it is, not by its index: the field on the same page whose box overlaps it
     * most. Null when [field] is null or no field is there any more.
     */
    fun indexOf(fields: List<SignField>, field: SignField?): Int? {
        if (field == null) return null
        return fields.withIndex()
            .map { (index, candidate) -> index to field.overlapWith(candidate) }
            .filter { (_, overlap) -> overlap > 0f }
            .maxByOrNull { (_, overlap) -> overlap }
            ?.first
    }

    /**
     * Next field: the first of [fields] not among [signed] (their indexes) after [current], going
     * round to the start, or the first unsigned one when [current] is gone. Null once all are signed.
     */
    fun nextUnsigned(fields: List<SignField>, signed: Set<Int>, current: SignField?): SignField? {
        val remaining = fields.indices.filter { it !in signed }
        val from = indexOf(fields, current) ?: -1
        val next = remaining.firstOrNull { it > from } ?: remaining.firstOrNull() ?: return null
        return fields[next]
    }

    private class Cue(val page: Int, val box: DisplayRect, val label: DisplayRect)

    /**
     * Lines that say "sign here" or mention a signature. The signature goes on the line to sign
     * on beside the label: a run of underscores in the label's own text, or one or a drawn line
     * further along the same row (forms often set "Signature:" apart from its line). With
     * neither, it goes just above the label, where a line drawn over a label such as "Client
     * signature" is, unless printed text is there; then it goes after the label on its row. A
     * box never reaches up over the text above it, down to [MIN_FIELD_HEIGHT].
     *
     * PDFTextStripper reports glyphs in the page's unrotated space, measured from the crop box's
     * top-left (x across, y down to the baseline; getXDirAdj and getYDirAdj for upright text).
     * Each box is worked out there, kept within the crop box, turned into PDF space and then into
     * display fractions with [pdfToDisplay], the same way as the form's own fields. Sideways text
     * is skipped. A page whose text or graphics cannot be replayed has no cues; the other pages
     * keep theirs.
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
            if (runCatching { stripper.getText(document) }.isFailure) continue
            val page = document.getPage(pageIndex)
            val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
            // Null for a box that would lie off the page: one above the first line, or past the right margin.
            fun display(left: Float, top: Float, right: Float, bottom: Float): DisplayRect? {
                val l = left.coerceIn(0f, crop.width)
                val r = right.coerceIn(0f, crop.width)
                val t = top.coerceIn(0f, crop.height)
                val b = bottom.coerceIn(0f, crop.height)
                if (l >= r || t >= b) return null
                return pdfToDisplay(PdfRect(crop.left + l, crop.top - b, crop.left + r, crop.top - t), page.rotation, crop)
            }
            val pageLines = lines(glyphs)
            val rules = runCatching { RuleFinder(page).rules }.getOrDefault(emptyList())
                .map { Rule(it.left - crop.left, it.right - crop.left, crop.top - it.y) }
            for (line in pageLines) {
                val mention = line.mention() ?: continue
                val first = line.glyphs.first()
                val labelRight = line.glyphs.last().let { it.xDirAdj + it.widthDirAdj }
                val cue = line.cue()
                val after = lineAfter(line, first.xDirAdj, labelRight, pageLines, rules, heading = cue == null)
                // A bare "Signature" or a long sentence is a label after all when its row has a line to sign on.
                val label = cue ?: mention.takeIf { after != null } ?: continue
                val labelTop = line.baseline - maxOf(first.heightDir, first.fontSizeInPt * 0.7f)
                val onRow = line.underscoresAfter(label)?.let { (left, right) -> Rule(left, right, line.baseline) } ?: after
                // Parenthesised so a box that is off the page skips the cue whichever branch made it.
                val box = (if (onRow != null) {
                    val bottom = maxOf(onRow.y, line.baseline) + 2f
                    val right = minOf(onRow.right, onRow.left + FIELD_WIDTH * 1.5f)
                    val height = roomAbove(glyphs, onRow.left, right, bottom, line.baseline - BASELINE_TOLERANCE).coerceAtLeast(MIN_FIELD_HEIGHT)
                    display(onRow.left, bottom - height, right, bottom)
                } else {
                    val left = first.xDirAdj
                    val right = minOf(left + FIELD_WIDTH, crop.width)
                    val bottom = labelTop - GAP
                    val room = minOf(roomAbove(glyphs, left, right, bottom, labelTop), bottom)
                    if (room >= MIN_FIELD_HEIGHT) {
                        display(left, bottom - room, right, bottom)
                    } else {
                        // Printed text is right above the label, so the blank space after it is the place.
                        val leftAfter = labelRight + spaceWidth(line.glyphs.last()) * 2
                        val bottomAfter = line.baseline + 2f
                        val rightAfter = minOf(leftAfter + FIELD_WIDTH, crop.width)
                        val height = roomAbove(glyphs, leftAfter, rightAfter, bottomAfter, line.baseline - BASELINE_TOLERANCE).coerceAtLeast(MIN_FIELD_HEIGHT)
                        display(leftAfter, bottomAfter - height, rightAfter, bottomAfter)
                    }
                }) ?: continue
                val labelBox = display(first.xDirAdj, labelTop, labelRight, line.baseline) ?: continue
                cues += Cue(pageIndex, box, labelBox)
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

        /** Where "sign here" or "signature" is in [text], or null if neither is. */
        fun mention(): IntRange? {
            val lower = text.lowercase()
            return (SIGN_HERE.find(lower) ?: SIGNATURE.find(lower))?.range
        }

        /** Where the cue is in [text], or null if this line does not ask for a signature. */
        fun cue(): IntRange? {
            val match = mention() ?: return null
            if (UNDERSCORES.containsMatchIn(text)) return match
            // A heading such as "Signature" on its own line names a section rather than a place to
            // sign, and a long line is a sentence that mentions signatures, not a label.
            val trimmed = text.lowercase().trim()
            return if (trimmed.trimEnd('.') == "signature" || trimmed.length > MAX_LABEL_LENGTH) null else match
        }

        /** The left and right of the first run of underscores after [cue], or before it if none follows. */
        fun underscoresAfter(cue: IntRange): Pair<Float, Float>? {
            val runs = UNDERSCORES.findAll(text).toList()
            val run = runs.firstOrNull { it.range.first > cue.last } ?: runs.lastOrNull() ?: return null
            return extent(run)
        }

        /**
         * The left and right of a run of underscores this line starts with (nothing but spaces
         * before it), or null. A run after other words, as in "Date: ______", is that field's line.
         */
        fun leadingUnderscores(): Pair<Float, Float>? {
            val run = UNDERSCORES.find(text) ?: return null
            if (text.substring(0, run.range.first).isNotBlank()) return null
            return extent(run)
        }

        private fun extent(run: MatchResult): Pair<Float, Float>? {
            val start = glyphAt[run.range.first] ?: return null
            val end = glyphAt[run.range.last] ?: return null
            return start.xDirAdj to end.xDirAdj + end.widthDirAdj
        }
    }

    /** A horizontal line in the stripper's space: x across, [y] down from the crop box's top. */
    private class Rule(val left: Float, val right: Float, val y: Float)

    /**
     * The line to sign on further along [label]'s row, after [labelRight]: the nearest run of
     * underscores that starts another piece of text on the same baseline (one after other words,
     * as "Date: ______" beside "Signature:", is that field's line), or the nearest drawn line
     * sitting on that baseline. For a bare [heading] such as "Signature", a drawn line does not
     * count when another drawn line at the same height runs under the heading itself, from
     * [labelLeft]: those are a table's cell borders, not a line to sign on. Null when the row
     * has nothing.
     */
    private fun lineAfter(label: Line, labelLeft: Float, labelRight: Float, lines: List<Line>, rules: List<Rule>, heading: Boolean): Rule? {
        val size = label.glyphs.first().fontSizeInPt
        val typed = lines.asSequence()
            .filter { it !== label && abs(it.baseline - label.baseline) <= BASELINE_TOLERANCE }
            .mapNotNull { other -> other.leadingUnderscores()?.let { (left, right) -> Rule(left, right, other.baseline) } }
        val onRow = rules.filter { it.y in label.baseline - size * 0.4f..label.baseline + size * 0.6f + 2f }
        val drawn = if (!heading) onRow else onRow.filter { candidate ->
            onRow.none { it !== candidate && abs(it.y - candidate.y) <= 1f && it.left < labelRight && it.right > labelLeft }
        }
        return (typed + drawn.asSequence())
            .filter { it.left >= labelRight - 2f && it.left - labelRight <= MAX_REACH }
            .minByOrNull { it.left }
    }

    /**
     * How tall a box whose bottom is at [bottom] can be, between [left] and [right], before it
     * covers text: the space up to the nearest glyph above [under] (a baseline, in the
     * stripper's space) that overlaps it, at most [FIELD_HEIGHT].
     */
    private fun roomAbove(glyphs: List<TextPosition>, left: Float, right: Float, bottom: Float, under: Float): Float {
        val nearest = glyphs.asSequence()
            .filter { !it.unicode.isNullOrBlank() && it.yDirAdj < under }
            .filter { it.xDirAdj < right && it.xDirAdj + it.widthDirAdj > left }
            .maxOfOrNull { it.yDirAdj } ?: return FIELD_HEIGHT
        return (bottom - nearest - 1f).coerceAtMost(FIELD_HEIGHT)
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
    private const val MIN_FIELD_HEIGHT = 16f
    private const val MAX_REACH = 300f
    private const val GAP = 3f
    private const val BASELINE_TOLERANCE = 2f
    private const val COLUMN_GAP = 24f
    private const val MAX_LABEL_LENGTH = 60

    private val SIGN_HERE = Regex("""\bsign\s+here\b""")
    private val SIGNATURE = Regex("""\bsignature\b""")
    private val UNDERSCORES = Regex("_{5,}")
}
