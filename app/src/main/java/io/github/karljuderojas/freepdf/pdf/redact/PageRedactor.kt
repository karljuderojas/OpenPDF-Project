package io.github.karljuderojas.freepdf.pdf.redact

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.graphics.AppendRectangleToPath
import com.tom_roush.pdfbox.contentstream.operator.graphics.BeginInlineImage
import com.tom_roush.pdfbox.contentstream.operator.graphics.ClipEvenOddRule
import com.tom_roush.pdfbox.contentstream.operator.graphics.ClipNonZeroRule
import com.tom_roush.pdfbox.contentstream.operator.graphics.CloseAndStrokePath
import com.tom_roush.pdfbox.contentstream.operator.graphics.CloseFillEvenOddAndStrokePath
import com.tom_roush.pdfbox.contentstream.operator.graphics.CloseFillNonZeroAndStrokePath
import com.tom_roush.pdfbox.contentstream.operator.graphics.ClosePath
import com.tom_roush.pdfbox.contentstream.operator.graphics.CurveTo
import com.tom_roush.pdfbox.contentstream.operator.graphics.CurveToReplicateFinalPoint
import com.tom_roush.pdfbox.contentstream.operator.graphics.CurveToReplicateInitialPoint
import com.tom_roush.pdfbox.contentstream.operator.graphics.EndPath
import com.tom_roush.pdfbox.contentstream.operator.graphics.FillEvenOddAndStrokePath
import com.tom_roush.pdfbox.contentstream.operator.graphics.FillEvenOddRule
import com.tom_roush.pdfbox.contentstream.operator.graphics.FillNonZeroAndStrokePath
import com.tom_roush.pdfbox.contentstream.operator.graphics.FillNonZeroRule
import com.tom_roush.pdfbox.contentstream.operator.graphics.LegacyFillNonZeroRule
import com.tom_roush.pdfbox.contentstream.operator.graphics.LineTo
import com.tom_roush.pdfbox.contentstream.operator.graphics.MoveTo
import com.tom_roush.pdfbox.contentstream.operator.graphics.ShadingFill
import com.tom_roush.pdfbox.contentstream.operator.graphics.StrokePath
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetLineWidth
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.contentstream.operator.text.BeginText
import com.tom_roush.pdfbox.contentstream.operator.text.EndText
import com.tom_roush.pdfbox.contentstream.operator.text.MoveText
import com.tom_roush.pdfbox.contentstream.operator.text.MoveTextSetLeading
import com.tom_roush.pdfbox.contentstream.operator.text.NextLine
import com.tom_roush.pdfbox.contentstream.operator.text.SetCharSpacing
import com.tom_roush.pdfbox.contentstream.operator.text.SetFontAndSize
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextHorizontalScaling
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextLeading
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextRenderingMode
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextRise
import com.tom_roush.pdfbox.contentstream.operator.text.SetWordSpacing
import com.tom_roush.pdfbox.contentstream.operator.text.ShowText
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextAdjusted
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextLine
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextLineAndSpace
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDTransparencyGroup
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import io.github.karljuderojas.freepdf.pdf.PdfRect
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Rewrites one page so that nothing inside [areas] is left in its content, not just covered:
 * the page's content stream is replayed and every piece that touches an area is dropped or cut
 * back as it is written out again.
 *
 * - Text: each glyph whose box touches an area is removed from its Tj/TJ string, and the width it
 *   took is kept as a TJ gap so the text after it stays where it was. That includes invisible
 *   text, such as the layer an OCR tool puts under a scan.
 * - Pictures: the samples inside the area are set to black (or, for a stencil mask, to unpainted)
 *   row by row in the picture's own sample data, which is stored again with the same colour space
 *   and depth. A picture whose samples cannot be edited that way (an indexed palette, a colour-key
 *   mask, JPEG) is decoded to pixels and blanked there instead; one that cannot be decoded at all
 *   (JPX, JBIG2) or is too big to decode is removed whole.
 * - Line art: a drawn or filled shape with an edge in an area is removed whole. A fill that
 *   merely lies under the area (a page or table-cell background) is left alone: it holds only a colour.
 * - Forms (reusable chunks of page content): one that reaches an area is copied and rewritten the
 *   same way, so other pages that share the original are not touched. The copy replaces it.
 *
 * What was swapped out is also taken out of the page's resources, so the old picture or form is
 * not left in the file. Things that cannot be placed inside a Tj (shadings, pattern fills) are
 * left as they are.
 *
 * With [probe] set, nothing is rewritten: the page is only replayed to fill in [stats], so the
 * pictures that would have to be removed whole can be known before the user commits.
 */
internal class PageRedactor(
    private val document: PDDocument,
    private val page: PDPage,
    private val areas: List<PdfRect>,
    private val probe: Boolean = false,
) : PDFGraphicsStreamEngine(page) {

    /** What the rewrite took out of the page. */
    class Stats {
        var glyphs = 0
        var pictures = 0
        var shapes = 0
        var forms = 0

        /** Pictures removed whole rather than blanked in part: inline pictures, and pictures too big or odd to decode. */
        var wholePictures = 0
    }

    val stats = Stats()

    /** Text that was removed, as runs and words, lower case; used to find the same words in metadata. */
    val fragments = LinkedHashSet<String>()

    /**
     * The longest run of removed glyphs in a row that had no known Unicode value. A run of a few
     * means a word [fragments] cannot know; a single one is usually a bullet or a symbol.
     */
    var unreadableRun = 0
        private set

    /** True when some removed glyphs had no known Unicode value, so [fragments] may miss words. */
    val unreadableText: Boolean get() = unreadableRun > 0

    /** The pictures and forms this page drew that were swapped for a rewritten copy, or dropped. */
    val replacedObjects: MutableSet<COSBase> = Collections.newSetFromMap(IdentityHashMap())

    // Named property lists (from a /Properties resource) that lost alternate text, each with its stripped copy.
    private val strippedProperties: MutableMap<COSBase, COSDictionary> = IdentityHashMap()

    // The run of removed text being collected; it continues across show operators until a kept glyph.
    private val removedRun = StringBuilder()
    private var unreadableStreak = 0

    /** The stream being written (the page's, or a form copy's), where new resources go and what was replaced. */
    private class Frame(val tokens: MutableList<Any>, val target: PDResources) {
        val replaced = ArrayList<COSName>()

        // Open marked-content sequences, innermost last.
        val marked = ArrayList<Marked>()
    }

    /**
     * A marked-content sequence being replayed: where its property list operand is in the frame's
     * tokens (-1 when it has none), the [properties] themselves (inline, or looked up from the
     * resources by [name]), and how much had been removed when it began.
     */
    private class Marked(val at: Int, val properties: COSDictionary?, val name: COSName?, val before: Int)

    private class Glyph(
        val hit: Boolean,
        val unicode: String?,
        val displacement: Float,
        val fontSize: Float,
        val charSpacing: Float,
        val wordSpacing: Float,
        val vertical: Boolean,
    )

    private class Span(val start: Int, val length: Int, val code: Int)

    private val frames = ArrayList<Frame>()
    private val frame get() = frames.last()

    // Frames whose stream has been written, kept for the final clean-up of their resources.
    private val finished = ArrayList<Frame>()

    // Text: glyphs seen by the show operator being replayed.
    private var collecting = false
    private val glyphs = ArrayList<Glyph>()

    // Path under construction, flattened to line segments [x0, y0, x1, y1] in page space.
    private val segments = ArrayList<FloatArray>()
    private val closing = ArrayList<FloatArray>()
    private var currentX = 0f
    private var currentY = 0f
    private var startX = 0f
    private var startY = 0f
    private var hasOpenSubpath = false

    // Set by drawImage for an inline image that touches an area, and by showForm for a form copy.
    private var inlineHit = false
    private var formCopy: PDFormXObject? = null

    init {
        listOf(
            Concatenate(), Save(), Restore(), SetGraphicsStateParameters(), SetLineWidth(), SetMatrix(),
            BeginText(), EndText(), SetFontAndSize(), ShowText(), ShowTextAdjusted(), ShowTextLine(), ShowTextLineAndSpace(),
            MoveText(), MoveTextSetLeading(), NextLine(), SetCharSpacing(), SetWordSpacing(), SetTextHorizontalScaling(),
            SetTextLeading(), SetTextRenderingMode(), SetTextRise(),
            AppendRectangleToPath(), BeginInlineImage(), ClipEvenOddRule(), ClipNonZeroRule(), CloseAndStrokePath(),
            CloseFillEvenOddAndStrokePath(), CloseFillNonZeroAndStrokePath(), ClosePath(), CurveTo(),
            CurveToReplicateFinalPoint(), CurveToReplicateInitialPoint(), DrawObject(), EndPath(),
            FillEvenOddAndStrokePath(), FillEvenOddRule(), FillNonZeroAndStrokePath(), FillNonZeroRule(),
            LegacyFillNonZeroRule(), LineTo(), MoveTo(), ShadingFill(), StrokePath(),
        ).forEach { addOperator(it) }
    }

    /** Replays the page and replaces its contents with the rewritten ones. */
    fun run() {
        // The page's resources may be shared with other pages, so it gets its own copy to change.
        val resources = if (probe) page.resources ?: PDResources() else copyOf(page.resources).also { page.resources = it }
        frames += Frame(ArrayList(), resources)
        processPage(page)
        flushRun()
        val done = frames.removeAt(frames.lastIndex)
        if (probe) return
        // Only now is everything that was swapped out known, so every resources dictionary made
        // here (a form copy's may have started from the page's) is cleaned of the originals at once.
        finished += done
        finished.forEach {
            dropReplaced(it)
            stripProperties(it)
        }
        val contents = PDStream(document)
        contents.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(done.tokens) }
        page.setContents(contents)
    }

    // ---- operators ----

    override fun processOperator(operator: Operator, operands: List<COSBase>?) {
        val operands = operands.orEmpty()
        when (operator.name) {
            "Tj", "TJ" -> show(operator, operands)
            // ' and " are run as the T*, Tw, Tc and Tj they stand for, which come back through here and are written out as such.
            "'", "\"" -> super.processOperator(operator, operands)
            "Do" -> draw(operator, operands)
            "S", "s", "f", "F", "f*", "B", "B*", "b", "b*" -> paint(operator, operands)
            "BI" -> {
                inlineHit = false
                super.processOperator(operator, operands)
                if (inlineHit) {
                    // An inline picture is small by definition and has no stream of its own to edit: it goes whole.
                    stats.pictures++
                    stats.wholePictures++
                } else {
                    emit(operands, operator)
                }
            }
            // Marked content may carry the text it shows a second time, as /ActualText or /Alt,
            // inline or in a named property list. That is taken out at EMC if something inside
            // the sequence was removed.
            "BDC", "BMC" -> {
                super.processOperator(operator, operands)
                val operand = if (operator.name == "BDC") operands.getOrNull(1) else null
                val properties = propertiesOf(operand)
                frame.marked += Marked(if (properties != null) frame.tokens.size + 1 else -1, properties, operand as? COSName, removedSoFar())
                emit(operands, operator)
            }
            "EMC" -> {
                super.processOperator(operator, operands)
                frame.marked.removeLastOrNull()?.let { marked ->
                    if (marked.at >= 0 && marked.properties != null && removedSoFar() > marked.before) {
                        val stripped = withoutAlternateText(marked.properties)
                        if (stripped !== marked.properties) {
                            // An inline list is replaced in the content; a named one in the resources it came
                            // from (every resources dictionary written here, once the page is done), since the
                            // list in the resources would otherwise keep the text.
                            if (marked.name == null) frame.tokens[marked.at] = stripped else strippedProperties[marked.properties] = stripped
                        }
                    }
                }
                emit(operands, operator)
            }
            else -> {
                super.processOperator(operator, operands)
                emit(operands, operator)
            }
        }
    }

    private fun removedSoFar() = stats.glyphs + stats.pictures + stats.shapes + stats.forms

    /** The property list a BDC names: given inline, or looked up in the resources being replayed. */
    private fun propertiesOf(operand: COSBase?): COSDictionary? = when (operand) {
        is COSDictionary -> operand
        is COSName -> runCatching {
            (resources?.cosObject?.getDictionaryObject(COSName.PROPERTIES) as? COSDictionary)?.getDictionaryObject(operand) as? COSDictionary
        }.getOrNull()
        else -> null
    }

    private fun emit(operands: List<COSBase>, operator: Operator) {
        frame.tokens.addAll(operands)
        frame.tokens.add(operator)
    }

    // ---- text ----

    private fun show(operator: Operator, operands: List<COSBase>) {
        glyphs.clear()
        collecting = true
        try {
            super.processOperator(operator, operands)
        } finally {
            collecting = false
        }
        // With no font set (or one missing from the resources), PdfBox shows the text in Helvetica,
        // and so do other readers; its codes are read the same way here.
        val font = graphicsState.textState.font ?: PDType1Font.HELVETICA
        if (glyphs.none { it.hit }) {
            if (glyphs.isNotEmpty()) flushRun()
            emit(operands, operator)
            return
        }
        val strings: List<COSBase> =
            if (operator.name == "TJ") (operands.firstOrNull() as? COSArray)?.toList().orEmpty() else listOfNotNull(operands.firstOrNull())
        frame.tokens.add(rebuild(font, strings))
        frame.tokens.add(Operator.getOperator("TJ"))
    }

    /** The shown strings with the glyphs that were hit taken out, each leaving a gap as wide as it was. */
    private fun rebuild(font: PDFont, strings: List<COSBase>): COSArray {
        val result = COSArray()
        val kept = ByteArrayOutputStream()
        var gap = 0f
        var next = 0

        fun flushText() {
            if (kept.size() > 0) {
                result.add(COSString(kept.toByteArray()))
                kept.reset()
            }
        }

        fun flushGap() {
            if (gap != 0f) {
                result.add(COSFloat(gap))
                gap = 0f
            }
        }

        for (item in strings) {
            when (item) {
                is COSString -> {
                    val bytes = item.bytes
                    for (span in spans(font, bytes)) {
                        val glyph = glyphs.getOrNull(next++) ?: error("Glyph count differs from the string's codes")
                        if (glyph.hit) {
                            flushText()
                            gap += gapFor(glyph, span)
                            if (glyph.unicode == null) {
                                unreadableStreak++
                                if (unreadableStreak > unreadableRun) unreadableRun = unreadableStreak
                            } else {
                                unreadableStreak = 0
                            }
                            removedRun.append(glyph.unicode ?: "")
                            stats.glyphs++
                        } else {
                            flushGap()
                            flushRun()
                            kept.write(bytes, span.start, span.length)
                        }
                    }
                }
                is COSNumber -> {
                    flushText()
                    gap += item.floatValue()
                    // Typesetters often leave out space glyphs and move the pen instead.
                    if (item.floatValue() < -WORD_GAP && removedRun.isNotEmpty()) removedRun.append(' ')
                }
                else -> Unit
            }
        }
        check(next == glyphs.size) { "Glyph count differs from the string's codes" }
        flushText()
        flushGap()
        return result
    }

    /** The TJ number that moves the pen on by as much as [glyph] did. */
    private fun gapFor(glyph: Glyph, span: Span): Float {
        if (glyph.vertical || glyph.fontSize == 0f) return 0f
        val space = if (span.code == 32 && span.length == 1) glyph.wordSpacing else 0f
        val advance = glyph.displacement * glyph.fontSize + glyph.charSpacing + space
        return -advance / glyph.fontSize * 1000f
    }

    private fun spans(font: PDFont, bytes: ByteArray): List<Span> {
        val input = ByteArrayInputStream(bytes)
        val spans = ArrayList<Span>()
        while (input.available() > 0) {
            val before = input.available()
            val code = font.readCode(input)
            spans += Span(bytes.size - before, before - input.available(), code)
        }
        return spans
    }

    /** Ends the run of removed text, remembering it and its words. */
    private fun flushRun() {
        unreadableStreak = 0
        val text = removedRun.toString()
        removedRun.setLength(0)
        val run = text.trim().lowercase()
        if (run.isEmpty()) return
        fragments += run
        run.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { fragments += it }
    }

    override fun showGlyph(textRenderingMatrix: Matrix, font: PDFont, code: Int, unicode: String?, displacement: Vector) {
        if (!collecting) return
        val state = graphicsState.textState
        val width = displacement.x
        val corners = listOf(0f to GLYPH_BELOW, width to GLYPH_BELOW, width to GLYPH_ABOVE, 0f to GLYPH_ABOVE)
            .map { (x, y) -> textRenderingMatrix.transformPoint(x, y) }
        val box = PdfRect(
            corners.minOf { it.x }, corners.minOf { it.y },
            corners.maxOf { it.x }, corners.maxOf { it.y },
        )
        glyphs += Glyph(
            hit = areas.any { cuts(box, it) },
            unicode = unicode,
            displacement = width,
            fontSize = state.fontSize,
            charSpacing = state.characterSpacing,
            wordSpacing = state.wordSpacing,
            vertical = font.isVertical,
        )
    }

    // ---- XObjects ----

    private fun draw(operator: Operator, operands: List<COSBase>) {
        val name = operands.lastOrNull() as? COSName
        val xobject = name?.let { runCatching { getResources().getXObject(it) }.getOrNull() }
        when (xobject) {
            is PDImageXObject -> {
                val area = imageBox()
                if (areas.none { overlaps(area, it) }) {
                    emit(operands, operator)
                    return
                }
                stats.pictures++
                if (probe) {
                    if (!canBlank(xobject)) stats.wholePictures++
                    emit(operands, operator)
                    return
                }
                frame.replaced += name
                replacedObjects += xobject.cosObject
                val blanked = blank(xobject)
                if (blanked == null) {
                    stats.wholePictures++
                    return
                }
                frame.tokens.add(frame.target.add(blanked))
                frame.tokens.add(operator)
            }
            is PDFormXObject -> {
                formCopy = null
                super.processOperator(operator, operands)
                val copy = formCopy
                formCopy = null
                if (copy == null) {
                    emit(operands, operator)
                } else {
                    frame.replaced += name
                    replacedObjects += xobject.cosObject
                    stats.forms++
                    frame.tokens.add(frame.target.add(copy))
                    frame.tokens.add(operator)
                }
            }
            else -> {
                super.processOperator(operator, operands)
                emit(operands, operator)
            }
        }
    }

    /** Called for a form that is drawn: copies and rewrites it if it reaches an area, else leaves [formCopy] empty. */
    override fun showForm(form: PDFormXObject) = rewrite(form)

    /**
     * Forms with a transparency group (how Word, Chrome and Illustrator often wrap a page's text)
     * come here instead of [showForm], and get the same treatment.
     */
    override fun showTransparencyGroup(form: PDTransparencyGroup) = rewrite(form)

    private fun rewrite(form: PDFormXObject) {
        // A form without the /BBox it should have is drawn unclipped by Pdfium and other readers,
        // so it may reach anywhere on the page and is rewritten; nothing of it is left behind.
        val box = formBox(form)
        if (box != null && areas.none { overlaps(box, it) }) {
            formCopy = null
            return
        }
        // A form without resources of its own finds its names in what encloses it, so the copy
        // starts from those, and only keeps them when something new had to be added.
        val own = form.resources
        val resources = copyOf(own ?: frame.target)
        frames += Frame(ArrayList(), resources)
        // PDFStreamEngine.showForm plays the form's stream with its matrix and clip, whatever its kind.
        super.showForm(form)
        val done = frames.removeAt(frames.lastIndex)
        if (probe) {
            formCopy = null
            return
        }
        finished += done

        val contents = PDStream(document)
        contents.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(done.tokens) }
        val dictionary = contents.cosObject
        form.cosObject.entrySet().forEach { (key, value) ->
            if (key !in NOT_COPIED) dictionary.setItem(key, value)
        }
        if (own != null || done.replaced.isNotEmpty()) dictionary.setItem(COSName.RESOURCES, resources.cosObject)
        formCopy = PDFormXObject(dictionary)
    }

    /** Where [form] can paint, in page space, or null when it has no /BBox to say. */
    private fun formBox(form: PDFormXObject): PdfRect? {
        val bbox = form.bBox ?: return null
        val matrix = form.matrix.multiply(graphicsState.currentTransformationMatrix)
        return boundsOf(
            listOf(
                bbox.lowerLeftX to bbox.lowerLeftY, bbox.upperRightX to bbox.lowerLeftY,
                bbox.upperRightX to bbox.upperRightY, bbox.lowerLeftX to bbox.upperRightY,
            ).map { (x, y) -> matrix.transformPoint(x, y) },
        )
    }

    /** Where a picture drawn now lands: the unit square under the current matrix. */
    private fun imageBox(): PdfRect {
        val ctm = graphicsState.currentTransformationMatrix
        return boundsOf(listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f).map { (x, y) -> ctm.transformPoint(x, y) })
    }

    override fun drawImage(pdImage: PDImage) {
        if (areas.any { overlaps(imageBox(), it) }) inlineHit = true
    }

    /**
     * [image] with everything inside an area blanked, stored as a new picture; null when that
     * cannot be done safely and the picture has to go. The samples are edited in place first,
     * which keeps the picture's format and needs one row of memory; decoding to pixels is the
     * fallback for formats that cannot be edited that way.
     */
    private fun blank(image: PDImageXObject): PDImageXObject? = try {
        val rects = pixelRects(image.width, image.height)
        // A picture whose samples turn out not to be editable after all (a stream that cannot be
        // read as planned) still gets the pixel fallback rather than going whole.
        if (rects == null) null else runCatching { blankSamples(image, rects) }.getOrNull() ?: blankPixels(image, rects)
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        // A picture too big to decode here is removed whole rather than kept.
        null
    }

    /** Whether [blank] would manage [image], judged from its format alone; for the probe. */
    private fun canBlank(image: PDImageXObject): Boolean = runCatching {
        samplePlan(image) != null || (!image.isStencil && image.suffix !in UNDECODABLE && fitsInMemory(image.width, image.height))
    }.getOrDefault(false)

    /**
     * The rows and columns of a [width] x [height] picture drawn now that lie under each area, as
     * [left, top, right, bottom) in pixels (empty where an area misses it); null when the
     * picture's matrix cannot be inverted.
     */
    private fun pixelRects(width: Int, height: Int): List<IntArray>? {
        // The picture's own space is the unit square, with the top row of pixels at y = 1.
        val ctm = graphicsState.currentTransformationMatrix
        val a = ctm.getValue(0, 0)
        val b = ctm.getValue(0, 1)
        val c = ctm.getValue(1, 0)
        val d = ctm.getValue(1, 1)
        val e = ctm.getValue(2, 0)
        val f = ctm.getValue(2, 1)
        val determinant = a * d - b * c
        if (abs(determinant) < 1e-9f) return null
        return areas.map { area ->
            val us = ArrayList<Float>()
            val vs = ArrayList<Float>()
            listOf(area.left to area.bottom, area.right to area.bottom, area.right to area.top, area.left to area.top)
                .forEach { (x, y) ->
                    val dx = x - e
                    val dy = y - f
                    us += (d * dx - c * dy) / determinant
                    vs += (-b * dx + a * dy) / determinant
                }
            intArrayOf(
                floor(us.min() * width).toInt().coerceIn(0, width),
                floor((1f - vs.max()) * height).toInt().coerceIn(0, height),
                ceil(us.max() * width).toInt().coerceIn(0, width),
                ceil((1f - vs.min()) * height).toInt().coerceIn(0, height),
            )
        }
    }

    /**
     * How to blank a picture in its own samples: how many there are per pixel, how many bits each
     * takes, the value that stands for black (or, for a stencil mask, for unpainted), and the soft
     * mask to blank along with it.
     */
    private class SamplePlan(val components: Int, val bits: Int, val fill: Int, val softMask: PDImageXObject?)

    /**
     * The plan for [image], or null for a picture whose samples cannot be edited that way: one
     * whose filters this platform cannot decode, an indexed or other palette-like colour space,
     * a colour-key or explicit mask, or a Decode array that remaps colours. With [opaque], the
     * fill is the brightest value instead of black, for a soft mask.
     */
    private fun samplePlan(image: PDImageXObject, opaque: Boolean = false): SamplePlan? {
        val dictionary = image.cosObject
        val filters = image.stream.filters.orEmpty()
        if (filters.any { it in IMAGE_FILTERS }) return null
        val bits = image.bitsPerComponent
        if (bits !in SAMPLE_BITS) return null
        val max = (1 shl bits) - 1
        if (dictionary.containsKey(COSName.MASK)) return null
        val decode = image.decode
        if (image.isStencil) {
            if (bits != 1) return null
            // With the default Decode, sample 0 paints; [1 0] turns that round.
            val inverted = decode != null && decode.size() > 0 && (decode.getObject(0) as? COSNumber)?.floatValue() == 1f
            return SamplePlan(1, 1, if (inverted) 0 else 1, null)
        }
        if (decode != null && decode.size() > 0) return null
        val (components, black) = colourSamples(dictionary.getDictionaryObject(COSName.COLORSPACE), max) ?: return null
        val softMask = (dictionary.getDictionaryObject(COSName.SMASK) as? COSStream)?.let { mask ->
            val maskImage = PDImageXObject(PDStream(mask), null)
            // The mask goes opaque where the picture goes black, so nothing under it shows through.
            if (samplePlan(maskImage, opaque = true) == null) return null
            maskImage
        }
        return SamplePlan(components, bits, if (opaque) max else black, softMask)
    }

    /** The samples per pixel of a colour space, and the sample value that is black at a sample [max]; null if not known here. */
    private fun colourSamples(space: COSBase?, max: Int): Pair<Int, Int>? {
        val name = when (space) {
            is COSName -> space
            is COSArray -> space.getObject(0) as? COSName
            else -> null
        }
        return when (name) {
            COSName.DEVICEGRAY, COSName.G, COSName.CALGRAY -> 1 to 0
            COSName.DEVICERGB, COSName.RGB, COSName.CALRGB -> 3 to 0
            COSName.DEVICECMYK, COSName.CMYK -> 4 to max
            COSName.ICCBASED -> when (((space as? COSArray)?.getObject(1) as? COSDictionary)?.getInt(COSName.N)) {
                1 -> 1 to 0
                3 -> 3 to 0
                4 -> 4 to max
                else -> null
            }
            else -> null
        }
    }

    /** [image] with the samples in [rects] set to the plan's fill, row by row, as a new Flate-compressed picture; null if not planned. */
    private fun blankSamples(image: PDImageXObject, rects: List<IntArray>, opaque: Boolean = false): PDImageXObject? {
        val plan = samplePlan(image, opaque) ?: return null
        val width = image.width
        val height = image.height
        val softMask = plan.softMask?.let { mask ->
            val maskRects = pixelRects(mask.width, mask.height) ?: return null
            blankSamples(mask, maskRects, opaque = true) ?: return null
        }
        val stride = ((width.toLong() * plan.components * plan.bits + 7) / 8).toInt()
        val row = ByteArray(stride)
        val stream = PDStream(document)
        image.createInputStream().use { input ->
            stream.createOutputStream(COSName.FLATE_DECODE).use { out ->
                for (y in 0 until height) {
                    var read = 0
                    while (read < stride) {
                        val n = input.read(row, read, stride - read)
                        if (n < 0) break
                        read += n
                    }
                    // A stream that ends early is padded, as readers do when drawing it.
                    if (read < stride) row.fill(0, read, stride)
                    for (rect in rects) {
                        if (y < rect[1] || y >= rect[3] || rect[0] >= rect[2]) continue
                        fillSamples(row, rect[0] * plan.components, rect[2] * plan.components, plan.bits, plan.fill)
                    }
                    out.write(row)
                }
            }
        }
        val dictionary = stream.cosObject
        // Only what describes the samples is carried over: an /Alternates copy of the picture, an
        // /OPI proxy or a /Mask would bring the old picture, or part of it, back with it.
        COPIED_IMAGE.forEach { key -> image.cosObject.getItem(key)?.let { dictionary.setItem(key, it) } }
        if (softMask != null) dictionary.setItem(COSName.SMASK, softMask.cosObject)
        return PDImageXObject(stream, null)
    }

    /** Sets samples [from] until [to] of a packed [row] to [value]. */
    private fun fillSamples(row: ByteArray, from: Int, to: Int, bits: Int, value: Int) {
        when (bits) {
            8 -> row.fill(value.toByte(), from, to)
            16 -> for (i in from until to) {
                row[i * 2] = (value shr 8).toByte()
                row[i * 2 + 1] = value.toByte()
            }
            else -> {
                val perByte = 8 / bits
                val mask = (1 shl bits) - 1
                for (i in from until to) {
                    val at = i / perByte
                    val shift = 8 - bits * (i % perByte + 1)
                    row[at] = ((row[at].toInt() and (mask shl shift).inv()) or (value shl shift)).toByte()
                }
            }
        }
    }

    /** True when a [width] x [height] picture can be decoded to pixels and stored again without running out of memory. */
    private fun fitsInMemory(width: Int, height: Int): Boolean {
        val runtime = Runtime.getRuntime()
        val free = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
        return width.toLong() * height * BYTES_PER_PIXEL < free / 2
    }

    /** [image] decoded to pixels, with those in [rects] painted black, stored as a new picture; null when that cannot be done. */
    private fun blankPixels(image: PDImageXObject, rects: List<IntArray>): PDImageXObject? {
        // A stencil mask has no pixels of its own, only the colour it is drawn with.
        if (image.isStencil) return null
        if (!fitsInMemory(image.width, image.height)) return null
        val source = image.image
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val jpeg = image.suffix == "jpg"
        // JPEG pictures decode through BitmapFactory, not PdfBox's own sample reader the probe uses.
        if (!jpeg && ChannelOrder.decodeSwaps) swapRedAndBlue(pixels)
        for (rect in rects) {
            val left = rect[0].coerceIn(0, width)
            val right = rect[2].coerceIn(0, width)
            for (row in rect[1].coerceIn(0, height) until rect[3].coerceIn(0, height)) pixels.fill(BLACK, row * width + left, row * width + right)
        }
        if (!jpeg && ChannelOrder.encodeSwaps) swapRedAndBlue(pixels)
        val blanked = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        blanked.setPixels(pixels, 0, width, 0, 0, width, height)
        // image.image has any mask applied as alpha, which the new picture stores again as its own mask.
        return if (jpeg) JPEGFactory.createFromImage(document, blanked, 0.92f)
        else LosslessFactory.createFromImage(document, blanked)
    }

    private fun swapRedAndBlue(pixels: IntArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = (p and 0xFF00FF00.toInt()) or ((p shr 16) and 0xFF) or ((p and 0xFF) shl 16)
        }
    }

    /**
     * Whether reading a picture into a Bitmap, or storing a Bitmap as a lossless picture, swaps
     * red and blue on this platform. PdfBox-Android moves pixels as raw bytes, and not every
     * graphics backend lays them out the same way (Robolectric's does not), so a blanked
     * picture would come back with its colours swapped. Each is checked once with a red pixel.
     */
    private object ChannelOrder {
        private const val RED = 0xFFFF0000.toInt()

        val decodeSwaps: Boolean by lazy {
            runCatching {
                PDDocument().use { document ->
                    val samples = ByteArrayOutputStream().also { out ->
                        java.util.zip.DeflaterOutputStream(out).use { it.write(byteArrayOf(-1, 0, 0)) }
                    }.toByteArray()
                    val picture = PDImageXObject(
                        document, ByteArrayInputStream(samples), COSName.FLATE_DECODE, 1, 1, 8,
                        com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE,
                    )
                    val pixel = IntArray(1)
                    picture.image.getPixels(pixel, 0, 1, 0, 0, 1, 1)
                    isBlue(pixel[0])
                }
            }.getOrDefault(false)
        }

        val encodeSwaps: Boolean by lazy {
            runCatching {
                PDDocument().use { document ->
                    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    bitmap.setPixels(intArrayOf(RED), 0, 1, 0, 0, 1, 1)
                    val stored = LosslessFactory.createFromImage(document, bitmap).stream.createInputStream().use { it.readBytes() }
                    (stored[0].toInt() and 0xFF) < (stored[2].toInt() and 0xFF)
                }
            }.getOrDefault(false)
        }

        private fun isBlue(pixel: Int) = (pixel shr 16 and 0xFF) < (pixel and 0xFF)
    }

    // ---- line art ----

    private fun paint(operator: Operator, operands: List<COSBase>) {
        val stroke = operator.name != "f" && operator.name != "F" && operator.name != "f*"
        val fill = operator.name != "S" && operator.name != "s"
        val hit = pathHit(stroke, fill)
        super.processOperator(operator, operands)
        if (hit) {
            stats.shapes++
            frame.tokens.add(Operator.getOperator("n"))
        } else {
            emit(operands, operator)
        }
    }

    private fun pathHit(stroke: Boolean, fill: Boolean): Boolean {
        val edges = if (fill) segments + closing + closingSegment() else segments
        if (edges.isEmpty()) return false
        val ctm = graphicsState.currentTransformationMatrix
        val scale = sqrt(abs(ctm.getValue(0, 0) * ctm.getValue(1, 1) - ctm.getValue(0, 1) * ctm.getValue(1, 0)))
        val reach = if (stroke) graphicsState.lineWidth * scale / 2f + 0.01f else 0f
        return areas.any { area ->
            edges.any { touches(it, area.left - reach, area.bottom - reach, area.right + reach, area.top + reach) }
        }
    }

    private fun closingSegment(): List<FloatArray> =
        if (hasOpenSubpath && (currentX != startX || currentY != startY)) listOf(floatArrayOf(currentX, currentY, startX, startY)) else emptyList()

    private fun forget() {
        segments.clear()
        closing.clear()
        hasOpenSubpath = false
    }

    override fun moveTo(x: Float, y: Float) {
        closingSegment().let { closing += it }
        startX = x
        startY = y
        currentX = x
        currentY = y
        hasOpenSubpath = true
    }

    override fun lineTo(x: Float, y: Float) {
        segments += floatArrayOf(currentX, currentY, x, y)
        currentX = x
        currentY = y
    }

    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        var px = currentX
        var py = currentY
        for (i in 1..CURVE_STEPS) {
            val t = i / CURVE_STEPS.toFloat()
            val u = 1f - t
            val x = u * u * u * currentX + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
            val y = u * u * u * currentY + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
            segments += floatArrayOf(px, py, x, y)
            px = x
            py = y
        }
        currentX = x3
        currentY = y3
    }

    override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
        closingSegment().let { closing += it }
        segments += floatArrayOf(p0.x, p0.y, p1.x, p1.y)
        segments += floatArrayOf(p1.x, p1.y, p2.x, p2.y)
        segments += floatArrayOf(p2.x, p2.y, p3.x, p3.y)
        segments += floatArrayOf(p3.x, p3.y, p0.x, p0.y)
        startX = p0.x
        startY = p0.y
        currentX = p0.x
        currentY = p0.y
        hasOpenSubpath = false
    }

    override fun getCurrentPoint(): PointF = PointF(currentX, currentY)

    override fun closePath() {
        if (currentX != startX || currentY != startY) segments += floatArrayOf(currentX, currentY, startX, startY)
        currentX = startX
        currentY = startY
        hasOpenSubpath = false
    }

    override fun endPath() = forget()
    override fun strokePath() = forget()
    override fun fillPath(windingRule: Path.FillType) = forget()
    override fun fillAndStrokePath(windingRule: Path.FillType) = forget()
    override fun clip(windingRule: Path.FillType) = Unit
    override fun shadingFill(shadingName: COSName) = Unit

    // ---- helpers ----

    /** A copy of [resources] that can be added to and removed from without touching what shares it. */
    private fun copyOf(resources: PDResources?): PDResources {
        val source = resources?.cosObject ?: COSDictionary()
        val copy = COSDictionary(source)
        (source.getDictionaryObject(COSName.XOBJECT) as? COSDictionary)?.let { copy.setItem(COSName.XOBJECT, COSDictionary(it)) }
        return PDResources(copy)
    }

    /**
     * Takes every picture or form that was swapped for a new one, on this page or inside a form
     * on it, out of [done]'s resources, unless its stream still draws it by that name.
     */
    private fun dropReplaced(done: Frame) {
        val xobjects = done.target.cosObject.getDictionaryObject(COSName.XOBJECT) as? COSDictionary ?: return
        val drawn = HashSet<COSName>()
        done.tokens.forEachIndexed { index, token ->
            if (token is Operator && token.name == "Do") (done.tokens.getOrNull(index - 1) as? COSName)?.let { drawn += it }
        }
        xobjects.keySet().filter { it !in drawn && xobjects.getDictionaryObject(it) in replacedObjects }.forEach { xobjects.removeItem(it) }
    }

    /**
     * Swaps, in [done]'s resources, every named property list that lost alternate text for its
     * stripped copy. The /Properties dictionary is copied first, so pages sharing the original
     * are not changed. Every frame's resources are gone through, since a form copy's may have
     * been taken from the page's before the list was stripped.
     */
    private fun stripProperties(done: Frame) {
        if (strippedProperties.isEmpty()) return
        val resources = done.target.cosObject
        val properties = resources.getDictionaryObject(COSName.PROPERTIES) as? COSDictionary ?: return
        val stale = properties.keySet().filter { properties.getDictionaryObject(it) in strippedProperties }
        if (stale.isEmpty()) return
        val own = COSDictionary(properties)
        stale.forEach { own.setItem(it, strippedProperties.getValue(properties.getDictionaryObject(it))) }
        resources.setItem(COSName.PROPERTIES, own)
    }

    /** [properties] without the alternate text and expansion a tagged PDF may attach to marked content. */
    private fun withoutAlternateText(properties: COSDictionary): COSDictionary {
        if (ALTERNATE_TEXT.none { properties.containsKey(it) }) return properties
        return COSDictionary(properties).also { copy -> ALTERNATE_TEXT.forEach { copy.removeItem(it) } }
    }

    private fun boundsOf(points: List<PointF>) = PdfRect(
        points.minOf { it.x }, points.minOf { it.y },
        points.maxOf { it.x }, points.maxOf { it.y },
    )

    private companion object {
        const val BLACK = -0x1000000
        const val CURVE_STEPS = 12

        // A glyph's box runs from a little below the baseline to a little under the ascender line.
        const val GLYPH_BELOW = -0.2f
        const val GLYPH_ABOVE = 0.9f

        val NOT_COPIED = setOf(COSName.LENGTH, COSName.FILTER, COSName.DECODE_PARMS, COSName.RESOURCES, COSName.METADATA)
        val COPIED_IMAGE = listOf(
            COSName.TYPE, COSName.SUBTYPE, COSName.WIDTH, COSName.HEIGHT, COSName.BITS_PER_COMPONENT, COSName.COLORSPACE,
            COSName.IMAGE_MASK, COSName.DECODE, COSName.INTENT, COSName.INTERPOLATE, COSName.OC, COSName.STRUCT_PARENT, COSName.NAME,
        )

        // Filters that are a picture format in themselves, not a way of packing samples.
        val IMAGE_FILTERS = setOf(COSName.DCT_DECODE, COSName.JPX_DECODE, COSName.JBIG2_DECODE)
        val SAMPLE_BITS = setOf(1, 2, 4, 8, 16)

        // Picture kinds this platform has no decoder for.
        val UNDECODABLE = setOf("jpx", "jb2")

        // Decoding a picture to pixels and storing it again holds its pixels three times over.
        const val BYTES_PER_PIXEL = 12L
        val ALTERNATE_TEXT = listOf(COSName.getPDFName("ActualText"), COSName.getPDFName("Alt"), COSName.getPDFName("E"))

        /**
         * True when [area] covers a real part of a glyph's [box]: a tenth of its width at least, so a
         * glyph that only touches the area's edge (the letter after a marked word) is left alone.
         */
        fun cuts(glyph: PdfRect, area: PdfRect): Boolean {
            // A glyph with no width (or no height) is a point or a line; give it a little so it can be hit.
            val box = PdfRect(
                if (glyph.width < MIN_GLYPH) glyph.left - MIN_GLYPH / 2 else glyph.left,
                if (glyph.height < MIN_GLYPH) glyph.bottom - MIN_GLYPH / 2 else glyph.bottom,
                if (glyph.width < MIN_GLYPH) glyph.left + MIN_GLYPH / 2 else glyph.right,
                if (glyph.height < MIN_GLYPH) glyph.bottom + MIN_GLYPH / 2 else glyph.top,
            )
            val across = minOf(box.right, area.right) - maxOf(box.left, area.left)
            val down = minOf(box.top, area.top) - maxOf(box.bottom, area.bottom)
            return down > 0f && across > 0f && across >= box.width * GLYPH_SHARE
        }

        const val GLYPH_SHARE = 0.1f
        const val MIN_GLYPH = 0.2f

        // A TJ move left of more than this (thousandths of an em) inside removed text counts as a space.
        const val WORD_GAP = 200f

        fun overlaps(a: PdfRect, b: PdfRect) = a.left < b.right && a.right > b.left && a.bottom < b.top && a.top > b.bottom

        /** Liang–Barsky: does the segment touch the box? */
        fun touches(s: FloatArray, left: Float, bottom: Float, right: Float, top: Float): Boolean {
            var t0 = 0f
            var t1 = 1f
            val dx = s[2] - s[0]
            val dy = s[3] - s[1]
            fun clip(p: Float, q: Float): Boolean {
                if (p == 0f) return q >= 0f
                val r = q / p
                if (p < 0f) {
                    if (r > t1) return false
                    if (r > t0) t0 = r
                } else {
                    if (r < t0) return false
                    if (r < t1) t1 = r
                }
                return true
            }
            return clip(-dx, s[0] - left) && clip(dx, right - s[0]) && clip(-dy, s[1] - bottom) && clip(dy, top - s[1])
        }
    }
}
