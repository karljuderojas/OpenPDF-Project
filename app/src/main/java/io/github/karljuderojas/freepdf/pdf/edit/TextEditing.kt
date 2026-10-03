package io.github.karljuderojas.freepdf.pdf.edit

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
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
 * the unit here is a *line*: neighbouring strings on one baseline that share font, size and
 * colour. [lineAt] finds the line under a tap and [replace] swaps its words.
 *
 * The old words are taken out of the content stream, not painted over. The new words are written
 * in the line's own font when that font has a glyph for every letter: fonts that are not
 * subsets always do, and a subset has the letters the page already uses, so a letter it never
 * drew sends the line to the bundled font instead (see [PdfText.fontFor]) at the same place,
 * size and colour, and [replace] reports [Outcome.OtherFont].
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

        /** The line's font lacked some letters, so the bundled font was used for the whole line. */
        OtherFont,
    }

    /** The line under the point ([x], [y] are fractions of the displayed page), or null if no editable text is there. */
    fun lineAt(document: PDDocument, pageIndex: Int, x: Float, y: Float): EditableLine? =
        analyze(document, pageIndex).lineAt(x, y)?.let { EditableLine(it.text, it.box) }

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
        fun tokenOf(run: Run) = operatorTokens.getOrNull(run.op)?.takeIf { (tokens[it] as Operator).name in SHOWING }
            ?: error("The page has changed")

        val host = line.runs.first()
        val inOwnFont = if (newText.isEmpty()) ByteArray(0) else encode(newText, host.font, analysis.codes[host.font.cosObject])
        line.runs.forEach { run ->
            val index = tokenOf(run)
            val last = index - 1
            val operand = tokens.getOrNull(last)
            val isArray = operand is COSArray
            check(isArray || operand is COSString) { "The page has changed" }
            tokens[last] = when {
                run === host && inOwnFont != null -> if (isArray) COSArray().apply { add(COSString(inOwnFont)) } else COSString(inOwnFont)
                isArray -> COSArray()
                else -> COSString(ByteArray(0))
            }
        }
        val contents = PDStream(document)
        contents.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(tokens) }
        page.setContents(contents)
        if (inOwnFont != null) return Outcome.SameFont

        val font = PdfText.fontFor(document, newText)
        val text = PdfText.printable(newText, font).replace("\n", " ")
        val at = displayToPdf(
            host.left / analysis.shownWidth, host.baseline / analysis.shownHeight, page.rotation, analysis.crop,
        )
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.uprightAt(page, PdfPoint(at.x, at.y)) {
                beginText()
                setFont(font, host.size)
                setNonStrokingColor(host.color)
                newLineAtOffset(0f, 0f)
                showText(text)
                endText()
            }
        }
        return Outcome.OtherFont
    }

    /**
     * [text] as bytes of [font], or null when the font cannot show all of it. Letters the page
     * already drew with [font] reuse their codes from [seen], which holds for subset fonts whose
     * other glyphs are gone; a font that is not a subset can encode anything it has a glyph for.
     */
    private fun encode(text: String, font: PDFont, seen: Map<String, ByteArray>?): ByteArray? {
        if (font is PDType3Font || font.isVertical) return null
        val subset = SUBSET_NAME.matches(font.name ?: "")
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val letter = String(Character.toChars(cp))
            val bytes = seen?.get(letter) ?: (if (subset) null else runCatching { font.encode(letter) }.getOrNull()) ?: return null
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private val SHOWING = setOf("Tj", "TJ", "'", "\"")
    private val SUBSET_NAME = Regex("^[A-Z]{6}\\+.*")

    /** One string-showing operator of the page: where it draws and in what. */
    private class Run(val op: Int, val font: PDFont, val size: Float, val color: PDColor) {
        val text = StringBuilder()
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var baseline = 0f
    }

    private class Line(val runs: List<Run>, val text: String, val box: DisplayRect)

    private class Analysis(
        val runs: List<Run>,
        /** Per font: the codes the page already uses for each letter. */
        val codes: Map<COSDictionary, Map<String, ByteArray>>,
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

        private fun lineOf(tapped: Run): Line {
            val peers = runs.filter { run ->
                run.font.cosObject === tapped.font.cosObject && abs(run.size - tapped.size) < 0.5f &&
                    abs(run.baseline - tapped.baseline) < tapped.size * 0.3f && sameColor(run.color, tapped.color)
            }.sortedBy { it.left }
            var first = peers.indexOf(tapped)
            var last = first
            // Strings further apart than a word or two (table columns) are separate lines.
            val maxGap = tapped.size * 1.2f
            while (first > 0 && peers[first].left - peers[first - 1].right <= maxGap) first--
            while (last < peers.lastIndex && peers[last + 1].left - peers[last].right <= maxGap) last++
            val chosen = peers.subList(first, last + 1)
            val text = buildString {
                chosen.forEachIndexed { i, run ->
                    if (i > 0 && !endsWith(" ") && !run.text.startsWith(" ") && run.left - chosen[i - 1].right > run.size * 0.15f) append(' ')
                    append(run.text)
                }
            }.trim()
            val box = DisplayRect(
                chosen.minOf { it.left } / shownWidth, (tapped.baseline - tapped.size * 0.8f) / shownHeight,
                chosen.maxOf { it.right } / shownWidth, (tapped.baseline + tapped.size * 0.22f) / shownHeight,
            )
            return Line(chosen, text, box)
        }

        private fun sameColor(a: PDColor, b: PDColor) =
            a.colorSpace?.name == b.colorSpace?.name && a.components.contentEquals(b.components)
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
        val codes = IdentityHashMap<COSDictionary, MutableMap<String, ByteArray>>()
        val stripper = object : PDFTextStripper() {
            private var operators = 0
            private var depth = 0
            private var current = -1

            override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
                current = if (depth == 0) operators++ else -1
                if (current >= 0 && operator.name in SHOWING) rememberCodes(operands)
                super.processOperator(operator, operands)
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
                val size = text.fontSizeInPt
                val upright = text.dir == rotation
                val visible = graphicsState.textState.renderingMode != RenderingMode.NEITHER
                if (font == null || size <= 0f || !upright || !visible) {
                    unusable += op
                    runs.remove(op)
                    return
                }
                val run = runs.getOrPut(op) { Run(op, font, size, graphicsState.nonStrokingColor) }
                run.text.append(text.unicode)
                run.left = minOf(run.left, text.xDirAdj)
                run.right = maxOf(run.right, text.xDirAdj + text.widthDirAdj)
                run.baseline = text.yDirAdj
            }
        }
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(document)
        return Analysis(runs.values.filter { it.text.isNotBlank() }, codes, shownWidth, shownHeight, crop)
    }
}
