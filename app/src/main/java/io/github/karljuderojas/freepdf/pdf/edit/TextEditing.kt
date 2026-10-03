package io.github.karljuderojas.freepdf.pdf.edit

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor.uprightAt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Changes the words of text that is already in a PDF.
 *
 * A PDF has no paragraphs, only strings that a page's content stream draws one after another, so
 * the unit here is a *line*: neighbouring strings on one baseline, whatever their font. [lineAt]
 * finds the line under a tap and [replace] swaps its words.
 *
 * The old words are taken out of the content stream, not painted over. Strings before and after
 * the words that differ are left as they are, so each keeps its own font; the string where the
 * change begins takes the new words in its font when that font has a glyph for every letter:
 * fonts that are not subsets always do, and a subset has the letters the page already uses, so a
 * letter it never drew sends the words to the bundled font instead (see [PdfText.fontFor]) at the
 * same place, size and colour, and [replace] reports [Outcome.OtherFont]. A font without a space
 * glyph (TeX, Ghostscript) gets its spaces as gaps in a TJ array, the way it draws them itself.
 *
 * Only text that reads upright as the page is shown can be edited (like [io.github.karljuderojas.freepdf.pdf.text.PageText]),
 * and not text inside form XObjects, invisible text on scans, or Type 3 fonts.
 */
object TextEditing {

    /** A line of text that can be replaced: its words as read and where it sits on the page as shown. */
    data class EditableLine(val text: String, val box: DisplayRect)

    enum class Outcome {
        /** The new words are set in the font the line already had. */
        SameFont,

        /** The line's font lacked some letters, so the bundled font was used for the changed words. */
        OtherFont,
    }

    /** The line under the point ([x], [y] are fractions of the displayed page), or null if no editable text is there. */
    fun lineAt(document: PDDocument, pageIndex: Int, x: Float, y: Float): EditableLine? =
        analyze(document, pageIndex).lineAt(x, y)?.let { EditableLine(it.text, it.box) }

    /** Every line [lineAt] could find on the page, for showing what can be edited. */
    fun lines(document: PDDocument, pageIndex: Int): List<EditableLine> =
        analyze(document, pageIndex).lines().map { EditableLine(it.text, it.box) }

    /**
     * Replaces the line at ([x], [y]) with [newText]. [oldText] is what [lineAt] returned, so an
     * edit made to a page that has changed since is refused. Fails with [IllegalStateException]
     * when there is no such line there any more.
     */
    fun replace(document: PDDocument, pageIndex: Int, x: Float, y: Float, oldText: String, newText: String): Outcome {
        val analysis = analyze(document, pageIndex)
        val line = analysis.lineAt(x, y)?.takeIf { it.text == oldText } ?: error("The text there has changed")
        val page = document.getPage(pageIndex)
        val tokens = PDFStreamParser(page).also { it.parse() }.tokens
        val operatorTokens = tokens.indices.filter { tokens[it] is Operator }
        val indexOf = line.runs.associateWith { run ->
            val index = operatorTokens.getOrNull(run.op)?.takeIf { (tokens[it] as Operator).name in SHOWING } ?: error("The page has changed")
            val operand = tokens.getOrNull(index - 1)
            check(operand is COSArray || operand is COSString) { "The page has changed" }
            index
        }

        // The words up to the first difference and from the last one stay; the strings that hold
        // them keep their text, font and place. The string where the change begins takes the rest.
        val old = line.text
        var prefix = 0
        while (prefix < old.length && prefix < newText.length && old[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (suffix < old.length - prefix && suffix < newText.length - prefix && old[old.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
        val changedEnd = old.length - suffix
        val spans = line.spans
        var from = spans.indexOfFirst { it.start < changedEnd && it.end > prefix }
        var to = spans.indexOfLast { it.start < changedEnd && it.end > prefix }
        if (from < 0) {
            // Nothing of the old words goes: an insertion, into the string that ends there, else the one after.
            from = spans.indexOfLast { it.start < prefix && it.end >= prefix }.takeIf { it >= 0 }
                ?: spans.indexOfFirst { it.start >= prefix }.takeIf { it >= 0 } ?: spans.lastIndex
            to = from
        }
        val host = line.runs[from]
        val hostSpan = spans[from]
        val seen = analysis.codes[host.font.cosObject]
        // The host's words before the change, the new words, then the old words after the change
        // up to the end of the last string that joins the host.
        fun textFor(lastRun: Int): String {
            val end = spans[lastRun].end
            return old.substring(hostSpan.start, prefix.coerceIn(hostSpan.start, hostSpan.end)) +
                newText.substring(prefix, newText.length - suffix) +
                old.substring(changedEnd.coerceIn(hostSpan.start, end), end)
        }
        var text = textFor(to)
        var pieces = if (text.isEmpty()) emptyList() else encode(text, host.font, seen)
        if (to < line.runs.lastIndex) {
            // Strings after the change move with it only when the stream places them by advance. Ones
            // put at a fixed place would be drawn over (or left a gap), so they join the changed string,
            // as they all do when it falls back to the bundled font and is blanked in the stream.
            val after = line.runs.subList(to + 1, line.runs.size)
            val placed = analysis.positioned.any { it in (host.op + 1)..after.maxOf { run -> run.op } }
            val absorb = pieces?.let { encoded ->
                val oldWidth = (from..to).sumOf { i -> widthOf(line.runs[i].font, tokens[indexOf.getValue(line.runs[i]) - 1]).toDouble() }.toFloat()
                placed && abs(widthOf(host.font, encoded) - oldWidth) > 30f
            } ?: true
            if (absorb) {
                to = line.runs.lastIndex
                text = textFor(to)
                pieces = if (text.isEmpty()) emptyList() else encode(text, host.font, seen)
            }
        }

        // Blank the other changed strings first: the host may need more tokens, which shifts later indices.
        val changed = line.runs.subList(from, to + 1)
        changed.forEach { run -> stripActualText(tokens, indexOf.getValue(run), page.resources?.cosObject) }
        changed.drop(1).forEach { run ->
            val last = indexOf.getValue(run) - 1
            tokens[last] = if (tokens[last] is COSArray) COSArray() else COSString(ByteArray(0))
        }
        val hostIndex = indexOf.getValue(host)
        val operand = tokens[hostIndex - 1]
        val leading = (operand as? COSArray)?.toList()?.takeWhile { it is COSNumber }.orEmpty()
        when {
            pieces == null -> tokens[hostIndex - 1] = if (operand is COSArray) COSArray() else COSString(ByteArray(0))
            operand is COSArray -> tokens[hostIndex - 1] = tjArrayOf(leading, pieces)
            pieces.all { it is Piece.Glyphs } -> tokens[hostIndex - 1] = COSString(bytesOf(pieces))
            else -> showWithGaps(tokens, hostIndex, tjArrayOf(leading, pieces))
        }
        val contents = PDStream(document)
        contents.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(tokens) }
        page.setContents(contents)
        if (pieces != null) return Outcome.SameFont

        val font = PdfText.fontFor(document, text)
        val shown = PdfText.printable(text, font).replace("\n", " ")
        val at = displayToPdf(
            host.left / analysis.shownWidth, host.baseline / analysis.shownHeight, page.rotation, analysis.crop,
        )
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.uprightAt(page, PdfPoint(at.x, at.y)) {
                beginText()
                setFont(font, host.size)
                setNonStrokingColor(host.color)
                newLineAtOffset(0f, 0f)
                showText(shown)
                endText()
            }
        }
        return Outcome.OtherFont
    }

    /** A part of a re-encoded string: glyph codes, or a gap of so many thousandths of the font size where a space would be. */
    private sealed class Piece {
        class Glyphs(val bytes: ByteArray) : Piece()
        class Gap(val thousandths: Float) : Piece()
    }

    /**
     * [text] as pieces for [font], or null when the font cannot show all of it. Letters the page
     * already drew with [font] reuse their codes from [seen], which holds for subset fonts whose
     * other glyphs are gone, and for CID fonts that are not embedded, whose codes depend on a
     * substitute font this device happens to have; any other font can encode what it has a glyph
     * for. A space the font has no glyph for becomes a [Piece.Gap].
     */
    private fun encode(text: String, font: PDFont, seen: Map<String, ByteArray>?): List<Piece>? {
        if (font is PDType3Font || font.isVertical) return null
        val reuseOnly = SUBSET_NAME.matches(font.name ?: "") || (font is PDType0Font && !font.isEmbedded)
        val pieces = ArrayList<Piece>()
        val out = ByteArrayOutputStream()
        fun flush() {
            if (out.size() > 0) pieces += Piece.Glyphs(out.toByteArray())
            out.reset()
        }
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val letter = String(Character.toChars(cp))
            val bytes = seen?.get(letter) ?: (if (reuseOnly) null else runCatching { font.encode(letter) }.getOrNull())
            when {
                bytes != null -> out.write(bytes)
                letter == " " -> {
                    flush()
                    val gap = spaceGapOf(font)
                    val previous = pieces.lastOrNull() as? Piece.Gap
                    if (previous != null) pieces[pieces.lastIndex] = Piece.Gap(previous.thousandths + gap) else pieces += Piece.Gap(gap)
                }
                else -> return null
            }
        }
        flush()
        return pieces
    }

    /** About a space, for a font that has none: half its average glyph width, like Helvetica's. */
    private fun spaceGapOf(font: PDFont): Float =
        (runCatching { font.averageFontWidth }.getOrDefault(0f).takeIf { it > 0f } ?: 556f) / 2f

    private fun bytesOf(pieces: List<Piece>): ByteArray {
        val out = ByteArrayOutputStream()
        pieces.forEach { if (it is Piece.Glyphs) out.write(it.bytes) }
        return out.toByteArray()
    }

    /** A TJ operand: [leading] adjustments the old array began with, then [pieces], gaps as negative adjustments. */
    private fun tjArrayOf(leading: List<COSBase>, pieces: List<Piece>) = COSArray().apply {
        leading.forEach { add(it) }
        pieces.forEach { piece ->
            when (piece) {
                is Piece.Glyphs -> add(COSString(piece.bytes))
                is Piece.Gap -> add(COSFloat(-piece.thousandths))
            }
        }
    }

    /**
     * Makes the string-showing operator at [index] draw [array] with TJ: Tj becomes TJ, and the
     * next-line forms ' and " become the T* (and Tw, Tc) they stand for, then TJ.
     */
    private fun showWithGaps(tokens: MutableList<Any>, index: Int, array: COSArray) {
        when ((tokens[index] as Operator).name) {
            "Tj" -> {
                tokens[index - 1] = array
                tokens[index] = Operator.getOperator("TJ")
            }
            "'" -> {
                tokens[index - 1] = Operator.getOperator("T*")
                tokens[index] = array
                tokens.add(index + 1, Operator.getOperator("TJ"))
            }
            else -> {
                val charSpacing = tokens[index - 2]
                tokens[index - 2] = Operator.getOperator("Tw")
                tokens[index - 1] = charSpacing
                tokens[index] = Operator.getOperator("Tc")
                tokens.addAll(index + 1, listOf(Operator.getOperator("T*"), array, Operator.getOperator("TJ")))
            }
        }
    }

    /** The advance of [operand] (a string or TJ array) in [font], in thousandths of the font size, or 0 if it cannot be read. */
    private fun widthOf(font: PDFont, operand: Any?): Float = when (operand) {
        is COSString -> glyphWidth(font, operand.bytes)
        is COSArray -> operand.toList().sumOf { element ->
            when (element) {
                is COSString -> glyphWidth(font, element.bytes).toDouble()
                is COSNumber -> -element.floatValue().toDouble()
                else -> 0.0
            }
        }.toFloat()
        else -> 0f
    }

    private fun widthOf(font: PDFont, pieces: List<Piece>): Float = pieces.sumOf { piece ->
        when (piece) {
            is Piece.Glyphs -> glyphWidth(font, piece.bytes).toDouble()
            is Piece.Gap -> piece.thousandths.toDouble()
        }
    }.toFloat()

    private fun glyphWidth(font: PDFont, bytes: ByteArray): Float = runCatching {
        var width = 0f
        val input = ByteArrayInputStream(bytes)
        while (input.available() > 0) width += font.getWidth(font.readCode(input))
        width
    }.getOrDefault(0f)

    /**
     * Drops /ActualText and /Alt from every marked-content span around the operator at [index]:
     * they repeat the words the span draws, and the old ones must not stay in the file.
     */
    private fun stripActualText(tokens: List<Any>, index: Int, resources: COSDictionary?) {
        var depth = 0
        for (i in index - 1 downTo 0) {
            val name = (tokens[i] as? Operator)?.name ?: continue
            when (name) {
                "EMC" -> depth++
                "BMC" -> if (depth > 0) depth--
                "BDC" -> if (depth > 0) depth-- else {
                    val properties = when (val operand = tokens.getOrNull(i - 1)) {
                        is COSDictionary -> operand
                        is COSName -> resources?.getCOSDictionary(COSName.PROPERTIES)?.getCOSDictionary(operand)
                        else -> null
                    }
                    properties?.removeItem(COSName.ACTUAL_TEXT)
                    properties?.removeItem(COSName.ALT)
                }
            }
        }
    }

    private val SHOWING = setOf("Tj", "TJ", "'", "\"")

    /** Operators after which a string is no longer placed by the advance of the string before it. */
    private val POSITIONING = setOf("Td", "TD", "Tm", "T*", "BT", "ET", "'", "\"", "cm", "q", "Q")
    private val SUBSET_NAME = Regex("^[A-Z]{6}\\+.*")

    /** One string-showing operator of the page: where it draws and in what. */
    private class Run(val op: Int, val font: PDFont, val size: Float, val color: PDColor) {
        val text = StringBuilder()
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var baseline = 0f
    }

    /** Where one run's words sit in a line's text: [start] inclusive, [end] exclusive. */
    private class Span(val start: Int, val end: Int)

    /** [runs] left to right; [spans] is where each one's words sit in [text]. */
    private class Line(val runs: List<Run>, val spans: List<Span>, val text: String, val box: DisplayRect)

    private class Analysis(
        val runs: List<Run>,
        /** Per font: the codes the page already uses for each letter. */
        val codes: Map<COSDictionary, Map<String, ByteArray>>,
        /** Operator indices (as [Run.op] counts them) that set where the next string goes. */
        val positioned: Set<Int>,
        val shownWidth: Float,
        val shownHeight: Float,
        val crop: PdfRect,
    ) {
        fun lineAt(x: Float, y: Float): Line? {
            val px = x * shownWidth
            val py = y * shownHeight
            val hit = runs.filter { run ->
                val slop = run.size * 0.3f
                px in run.left - slop..run.right + slop &&
                    py in run.baseline - run.size * 0.8f - slop..run.baseline + run.size * 0.22f + slop
            }.minByOrNull { run ->
                hypot((run.left + run.right) / 2 - px, run.baseline - run.size * 0.3f - py)
            } ?: return null
            return lineOf(hit)
        }

        fun lines(): List<Line> {
            val taken = HashSet<Run>()
            return runs.mapNotNull { run -> if (run in taken) null else lineOf(run).also { taken += it.runs } }
        }

        private fun lineOf(tapped: Run): Line {
            // Strings on the tapped one's baseline, in any font: a bold word in a sentence is part of it.
            val peers = runs.filter { run ->
                abs(run.baseline - tapped.baseline) < tapped.size * 0.3f && run.size in tapped.size * 0.5f..tapped.size * 2f
            }.sortedBy { it.left }
            var first = peers.indexOf(tapped)
            var last = first
            // Strings further apart than a word or two (table columns) are separate lines.
            val maxGap = tapped.size * 1.2f
            while (first > 0 && peers[first].left - peers[first - 1].right <= maxGap) first--
            while (last < peers.lastIndex && peers[last + 1].left - peers[last].right <= maxGap) last++
            val chosen = peers.subList(first, last + 1)
            val spans = ArrayList<Span>()
            val joined = buildString {
                chosen.forEachIndexed { i, run ->
                    if (i > 0 && !endsWith(" ") && !run.text.startsWith(" ") && run.left - chosen[i - 1].right > run.size * 0.15f) append(' ')
                    val start = length
                    append(run.text)
                    spans += Span(start, length)
                }
            }
            val lead = joined.length - joined.trimStart().length
            val text = joined.trim()
            val box = DisplayRect(
                chosen.minOf { it.left } / shownWidth, (tapped.baseline - tapped.size * 0.8f) / shownHeight,
                chosen.maxOf { it.right } / shownWidth, (tapped.baseline + tapped.size * 0.22f) / shownHeight,
            )
            return Line(
                chosen,
                spans.map { Span((it.start - lead).coerceIn(0, text.length), (it.end - lead).coerceIn(0, text.length)) },
                text,
                box,
            )
        }
    }

    private fun analyze(document: PDDocument, pageIndex: Int): Analysis {
        val page = document.getPage(pageIndex)
        val box = page.cropBox
        val crop = PdfRect(box.lowerLeftX, box.lowerLeftY, box.upperRightX, box.upperRightY)
        val rotation = (((page.rotation % 360) + 360) % 360).toFloat()
        val turned = rotation == 90f || rotation == 270f
        val shownWidth = if (turned) crop.height else crop.width
        val shownHeight = if (turned) crop.width else crop.height

        val runs = LinkedHashMap<Int, Run>()
        val unusable = HashSet<Int>()
        val positioned = HashSet<Int>()
        val codes = IdentityHashMap<COSDictionary, MutableMap<String, ByteArray>>()
        val stripper = object : PDFTextStripper() {
            private var operators = 0
            private var depth = 0
            private var nesting = 0
            private var current = -1

            // ' and " run as T* (with null operands) and Tj from inside their own processing:
            // those nested calls count as the operator that made them, so indices match the stream.
            override fun processOperator(operator: Operator, operands: MutableList<COSBase>?) {
                // Inside a form the operators arrive nested under the page's Do, so they never count.
                if (depth > 0) current = -1 else if (nesting == 0) current = operators++
                if (current >= 0 && operator.name in SHOWING && operands != null) rememberCodes(operands)
                if (current >= 0 && operator.name in POSITIONING) positioned += current
                nesting++
                try {
                    super.processOperator(operator, operands)
                } finally {
                    nesting--
                }
            }

            // Text drawn by a form XObject is not in the page's own content stream, so it is left alone.
            override fun showForm(form: PDFormXObject) {
                depth++
                try {
                    super.showForm(form)
                } finally {
                    depth--
                }
            }

            private fun rememberCodes(operands: List<COSBase>) {
                val font = graphicsState.textState.font ?: return
                val strings = operands.flatMap { if (it is COSArray) it.toList() else listOf(it) }.filterIsInstance<COSString>()
                val seen = codes.getOrPut(font.cosObject) { HashMap() }
                runCatching {
                    strings.forEach { string ->
                        val bytes = string.bytes
                        val input = ByteArrayInputStream(bytes)
                        while (input.available() > 0) {
                            val start = bytes.size - input.available()
                            val code = font.readCode(input)
                            val unicode = font.toUnicode(code) ?: continue
                            seen.putIfAbsent(unicode, bytes.copyOfRange(start, bytes.size - input.available()))
                        }
                    }
                }
            }

            override fun processTextPosition(text: TextPosition) {
                val op = current
                if (op < 0 || op in unusable) return
                val font = graphicsState.textState.font
                // Negative on a page turned upside down (/Rotate 180); a Type 3 font can leave it 0.
                val size = abs(text.fontSizeInPt).takeIf { it > 0f } ?: (text.heightDir * 1.4f)
                val upright = text.dir == rotation
                val mode = graphicsState.textState.renderingMode
                val visible = mode.isFill || mode.isStroke
                if (font == null || size <= 0f || !upright || !visible) {
                    unusable += op
                    runs.remove(op)
                    return
                }
                val run = runs.getOrPut(op) { Run(op, font, size, graphicsState.nonStrokingColor) }
                // A gap wider than PdfBox's word tolerance (half a space) is a space the string draws
                // as a TJ offset. A subset with no space glyph reports a made-up space width, so a
                // fifth of the size, narrower than any word gap TeX sets, caps it.
                val space = text.widthOfSpace
                val tolerance = if (space > 0f && !space.isNaN()) minOf(space * 0.5f, size * 0.2f) else size * 0.2f
                if (run.text.isNotEmpty() && !run.text.endsWith(' ') && !text.unicode.startsWith(' ') && text.xDirAdj - run.right > tolerance) {
                    run.text.append(' ')
                }
                run.text.append(text.unicode)
                run.left = minOf(run.left, text.xDirAdj)
                run.right = maxOf(run.right, text.xDirAdj + text.widthDirAdj)
                run.baseline = text.yDirAdj
            }
        }
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(document)
        return Analysis(runs.values.filter { it.text.isNotBlank() }, codes, positioned, shownWidth, shownHeight, crop)
    }
}
