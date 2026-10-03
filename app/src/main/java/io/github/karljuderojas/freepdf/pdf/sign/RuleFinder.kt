package io.github.karljuderojas.freepdf.pdf.sign

import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.graphics.AppendRectangleToPath
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
import com.tom_roush.pdfbox.contentstream.operator.graphics.StrokePath
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The horizontal lines a page draws, such as the line after "Signature:" on a form: stroked
 * straight lines, and filled rectangles thin enough to read as a line. Each is in PDF page space
 * (y up), as [left], [right] and [y].
 */
internal class RuleFinder(page: PDPage) : PDFGraphicsStreamEngine(page) {

    class Rule(val left: Float, val right: Float, val y: Float)

    val rules = ArrayList<Rule>()

    // The path being built: straight segments [x0, y0, x1, y1], and the thin rectangles in it.
    private val segments = ArrayList<FloatArray>()
    private val rectangles = ArrayList<Rule>()
    private var currentX = 0f
    private var currentY = 0f
    private var startX = 0f
    private var startY = 0f

    init {
        listOf(
            Concatenate(), Save(), Restore(), SetMatrix(), DrawObject(),
            AppendRectangleToPath(), ClipEvenOddRule(), ClipNonZeroRule(), CloseAndStrokePath(),
            CloseFillEvenOddAndStrokePath(), CloseFillNonZeroAndStrokePath(), ClosePath(), CurveTo(),
            CurveToReplicateFinalPoint(), CurveToReplicateInitialPoint(), EndPath(),
            FillEvenOddAndStrokePath(), FillEvenOddRule(), FillNonZeroAndStrokePath(), FillNonZeroRule(),
            LegacyFillNonZeroRule(), LineTo(), MoveTo(), StrokePath(),
        ).forEach { addOperator(it) }
        processPage(page)
    }

    override fun moveTo(x: Float, y: Float) {
        startX = x
        startY = y
        currentX = x
        currentY = y
    }

    override fun lineTo(x: Float, y: Float) {
        segments += floatArrayOf(currentX, currentY, x, y)
        currentX = x
        currentY = y
    }

    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        // A curve is never a line to sign on.
        currentX = x3
        currentY = y3
    }

    override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
        val left = minOf(p0.x, p1.x, p2.x, p3.x)
        val right = maxOf(p0.x, p1.x, p2.x, p3.x)
        val bottom = minOf(p0.y, p1.y, p2.y, p3.y)
        val top = maxOf(p0.y, p1.y, p2.y, p3.y)
        // Only an upright rectangle that is wide and thin; a stroked box's edges are its sides.
        if (setOf(p0.y, p1.y, p2.y, p3.y).size <= 2) {
            if (top - bottom <= THIN) rectangles += Rule(left, right, (top + bottom) / 2)
            else {
                segments += floatArrayOf(left, bottom, right, bottom)
                segments += floatArrayOf(left, top, right, top)
            }
        }
        startX = p0.x
        startY = p0.y
        currentX = p0.x
        currentY = p0.y
    }

    override fun getCurrentPoint(): PointF = PointF(currentX, currentY)

    override fun closePath() {
        currentX = startX
        currentY = startY
    }

    override fun strokePath() = keep(stroked = true)
    override fun fillPath(windingRule: Path.FillType) = keep(stroked = false)
    override fun fillAndStrokePath(windingRule: Path.FillType) = keep(stroked = true)
    override fun endPath() = keep(stroked = null)
    override fun clip(windingRule: Path.FillType) = Unit
    override fun shadingFill(shadingName: COSName) = Unit
    override fun drawImage(pdImage: PDImage) = Unit

    /** Ends the path: a stroke draws its horizontal segments, a fill or stroke its thin rectangles. */
    private fun keep(stroked: Boolean?) {
        if (stroked != null) {
            rectangles.filterTo(rules) { it.right - it.left >= MIN_LENGTH }
            if (stroked) {
                for (s in segments) {
                    if (abs(s[3] - s[1]) <= THIN && abs(s[2] - s[0]) >= MIN_LENGTH) rules += Rule(min(s[0], s[2]), max(s[0], s[2]), (s[1] + s[3]) / 2)
                }
            }
        }
        segments.clear()
        rectangles.clear()
    }

    private companion object {
        // In points: a line to sign on is at least this long, and no thicker than this.
        const val MIN_LENGTH = 40f
        const val THIN = 3f
    }
}
