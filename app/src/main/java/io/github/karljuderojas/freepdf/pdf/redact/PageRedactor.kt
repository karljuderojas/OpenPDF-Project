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
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import io.github.karljuderojas.freepdf.pdf.PdfRect
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
 * - Pictures: the pixels inside the area are painted black and the picture is stored again. A
 *   picture that cannot be re-stored this way (a stencil mask, an unsupported format) is removed whole.
 * - Line art: a drawn or filled shape with an edge in an area is removed whole. A fill that
 *   merely lies under the area (a page or table-cell background) is left alone: it holds only a colour.
 * - Forms (reusable chunks of page content): one that reaches an area is copied and rewritten the
 *   same way, so other pages that share the original are not touched. The copy replaces it.
 *
 * What was swapped out is also taken out of the page's resources, so the old picture or form is
 * not left in the file. Things that cannot be placed inside a Tj (shadings, pattern fills) are
 * left as they are.
 */
internal class PageRedactor(
    private val document: PDDocument,
    private val page: PDPage,
    private val areas: List<PdfRect>,
) : PDFGraphicsStreamEngine(page) {

    /** What the rewrite took out of the page. */
    class Stats {
        var glyphs = 0
        var pictures = 0
        var shapes = 0
        var forms = 0
    }

    val stats = Stats()

    /** Text that was removed, as runs and words, lower case; used to find the same words in metadata. */
    val fragments = LinkedHashSet<String>()

    /** The stream being written (the page's, or a form copy's), where new resources go and what was replaced. */
    private class Frame(val tokens: MutableList<Any>, val target: PDResources) {
        val replaced = ArrayList<COSName>()
    }

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
        val resources = copyOf(page.resources)
        page.resources = resources
        frames += Frame(ArrayList(), resources)
        processPage(page)
        val done = frames.removeAt(frames.lastIndex)
        dropReplaced(done)
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
                if (inlineHit) stats.pictures++ else emit(operands, operator)
            }
            "BDC" -> {
                super.processOperator(operator, operands)
                emit(operands.map { if (it is COSDictionary) withoutAlternateText(it) else it }, operator)
            }
            else -> {
                super.processOperator(operator, operands)
                emit(operands, operator)
            }
        }
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
        val font = graphicsState.textState.font
        if (font == null || glyphs.none { it.hit }) {
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
        val removed = StringBuilder()

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
                            removed.append(glyph.unicode ?: "")
                            stats.glyphs++
                        } else {
                            flushGap()
                            kept.write(bytes, span.start, span.length)
                        }
                    }
                }
                is COSNumber -> {
                    flushText()
                    gap += item.floatValue()
                }
                else -> Unit
            }
        }
        check(next == glyphs.size) { "Glyph count differs from the string's codes" }
        flushText()
        flushGap()
        rememberFragments(removed.toString())
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

    private fun rememberFragments(text: String) {
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
                frame.replaced += name
                val blanked = blank(xobject)
                if (blanked == null) {
                    stats.pictures++
                    return
                }
                stats.pictures++
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
    override fun showForm(form: PDFormXObject) {
        val box = formBox(form)
        if (box == null || areas.none { overlaps(box, it) }) {
            formCopy = null
            return
        }
        val resources = copyOf(form.resources)
        frames += Frame(ArrayList(), resources)
        super.showForm(form)
        val done = frames.removeAt(frames.lastIndex)
        dropReplaced(done)

        val contents = PDStream(document)
        contents.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(done.tokens) }
        val dictionary = contents.cosObject
        form.cosObject.entrySet().forEach { (key, value) ->
            if (key !in NOT_COPIED) dictionary.setItem(key, value)
        }
        dictionary.setItem(COSName.RESOURCES, resources.cosObject)
        formCopy = PDFormXObject(dictionary)
    }

    /** Where [form] can paint, in page space, or null if its matrix cannot be inverted or applied. */
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
     * [image] with every pixel inside an area painted black, stored as a new picture; null when that
     * cannot be done safely and the picture has to go.
     */
    private fun blank(image: PDImageXObject): PDImageXObject? = try {
        if (image.isStencil) return null
        val source = image.image
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

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
        for (area in areas) {
            val us = ArrayList<Float>()
            val vs = ArrayList<Float>()
            listOf(area.left to area.bottom, area.right to area.bottom, area.right to area.top, area.left to area.top)
                .forEach { (x, y) ->
                    val dx = x - e
                    val dy = y - f
                    us += (d * dx - c * dy) / determinant
                    vs += (-b * dx + a * dy) / determinant
                }
            val left = floor(us.min() * width).toInt().coerceIn(0, width)
            val right = ceil(us.max() * width).toInt().coerceIn(0, width)
            val top = floor((1f - vs.max()) * height).toInt().coerceIn(0, height)
            val bottom = ceil((1f - vs.min()) * height).toInt().coerceIn(0, height)
            for (row in top until bottom) pixels.fill(BLACK, row * width + left, row * width + right)
        }
        val blanked = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        blanked.setPixels(pixels, 0, width, 0, 0, width, height)
        // image.image has any mask applied as alpha, which the new picture stores again as its own mask.
        if (image.suffix == "jpg") JPEGFactory.createFromImage(document, blanked, 0.92f)
        else LosslessFactory.createFromImage(document, blanked)
    } catch (_: Exception) {
        null
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

    /** Takes the XObjects that were swapped for a new one out of the resources, unless something still draws them. */
    private fun dropReplaced(done: Frame) {
        if (done.replaced.isEmpty()) return
        val drawn = HashSet<COSName>()
        done.tokens.forEachIndexed { index, token ->
            if (token is Operator && token.name == "Do") (done.tokens.getOrNull(index - 1) as? COSName)?.let { drawn += it }
        }
        val xobjects = done.target.cosObject.getDictionaryObject(COSName.XOBJECT) as? COSDictionary ?: return
        done.replaced.filter { it !in drawn }.forEach { xobjects.removeItem(it) }
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
        val ALTERNATE_TEXT = listOf(COSName.getPDFName("ActualText"), COSName.getPDFName("Alt"), COSName.getPDFName("E"))

        /**
         * True when [area] covers a real part of a glyph's [box]: a tenth of its width at least, so a
         * glyph that only touches the area's edge (the letter after a marked word) is left alone.
         */
        fun cuts(box: PdfRect, area: PdfRect): Boolean {
            val across = minOf(box.right, area.right) - maxOf(box.left, area.left)
            val down = minOf(box.top, area.top) - maxOf(box.bottom, area.bottom)
            return down > 0f && across > 0f && across >= box.width * GLYPH_SHARE
        }

        const val GLYPH_SHARE = 0.1f

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
