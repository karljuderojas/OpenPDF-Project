package io.github.karljuderojas.freepdf.pdf.annotate

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.annotate.Appearances.stamp
import io.github.karljuderojas.freepdf.pdf.annotate.Appearances.toPdRectangle
import java.util.Locale

/**
 * "Approved", "Draft" and similar rubber stamps, as standard Stamp annotations.
 *
 * Other viewers only draw a stamp from its appearance stream (the /Name is a hint few of them
 * render), so the full look is drawn here: a rounded outline, a faint fill and the label.
 */
object Stamps {

    private val Green = Annotator.Rgb(0.13f, 0.55f, 0.25f)

    enum class Kind(val label: String, val pdfName: String, val color: Annotator.Rgb) {
        Approved("Approved", PDAnnotationRubberStamp.NAME_APPROVED, Green),
        NotApproved("Not approved", PDAnnotationRubberStamp.NAME_NOT_APPROVED, Annotator.Rgb.Red),
        Draft("Draft", PDAnnotationRubberStamp.NAME_DRAFT, Annotator.Rgb.Blue),
        Final("Final", PDAnnotationRubberStamp.NAME_FINAL, Green),
        Confidential("Confidential", PDAnnotationRubberStamp.NAME_CONFIDENTIAL, Annotator.Rgb.Red),
        ForComment("For comment", PDAnnotationRubberStamp.NAME_FOR_COMMENT, Annotator.Rgb.Blue),
        // Not one of the standard names; viewers fall back to the appearance stream.
        Void("Void", "Void", Annotator.Rgb.Red),
    }

    private val FONT = PDType1Font.HELVETICA_BOLD
    private const val FONT_SIZE = 20f
    /** Helvetica-Bold's cap height (718/1000); labels are all capitals, so this is their height. */
    private const val CAP_HEIGHT = 0.718f * FONT_SIZE
    private const val PADDING_X = 14f
    private const val PADDING_Y = 10f
    private const val BORDER = 2.5f
    private const val CORNER = 6f
    private const val FILL_ALPHA = 0.1f

    /** Adds a stamp centred on [center], upright as the page is shown. */
    fun add(
        document: PDDocument,
        pageIndex: Int,
        center: PdfPoint,
        kind: Kind,
        author: String? = null,
    ): PDAnnotationRubberStamp {
        val page = document.getPage(pageIndex)
        val rotation = Appearances.normalizedRotation(page.rotation)
        val (width, height) = sizeOf(kind)

        val annotation = PDAnnotationRubberStamp().apply {
            name = kind.pdfName
            contents = kind.label
            rectangle = Appearances.rectAround(center, width, height, rotation).toPdRectangle()
            stamp(author)
        }
        draw(document, annotation, kind, kind.color, rotation)
        page.annotations.add(annotation)
        return annotation
    }

    /**
     * The kind of stamp [annotation] is, when it is one made by [add]; null for any other stamp.
     *
     * Only the /Name is checked: a comment added to a stamp later replaces its /Contents, and the
     * stamp must still be recognised (and recolourable) afterwards.
     */
    fun kindOf(annotation: PDAnnotation): Kind? {
        if (annotation.subtype != PDAnnotationRubberStamp.SUB_TYPE) return null
        val name = annotation.cosObject.getNameAsString(COSName.NAME)
        return Kind.entries.firstOrNull { it.pdfName == name }
    }

    /**
     * Redraws a stamp made by [add] in [color], keeping its place, size and turn. Returns false
     * and changes nothing for a stamp made elsewhere, whose look this cannot reproduce.
     */
    fun restyle(document: PDDocument, annotation: PDAnnotation, color: Annotator.Rgb): Boolean {
        val kind = kindOf(annotation) ?: return false
        draw(document, annotation, kind, color, Appearances.rotationOf(annotation))
        return true
    }

    private fun sizeOf(kind: Kind): Pair<Float, Float> {
        val text = kind.label.uppercase(Locale.ROOT)
        return (FONT.getStringWidth(text) / 1000f * FONT_SIZE + 2 * PADDING_X) to (CAP_HEIGHT + 2 * PADDING_Y)
    }

    private fun draw(document: PDDocument, annotation: PDAnnotation, kind: Kind, color: Annotator.Rgb, rotation: Int) {
        val text = kind.label.uppercase(Locale.ROOT)
        val (width, height) = sizeOf(kind)
        annotation.color = color.toPdColor()
        Appearances.set(document, annotation, width, height, rotation) {
            // Faint fill, then the outline at full strength. The outline is inset by half its
            // width so it is not clipped by the BBox.
            saveGraphicsState()
            setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = FILL_ALPHA })
            setNonStrokingColor(color.r, color.g, color.b)
            roundedRect(BORDER / 2, BORDER / 2, width - BORDER, height - BORDER, CORNER)
            fill()
            restoreGraphicsState()

            setStrokingColor(color.r, color.g, color.b)
            setLineWidth(BORDER)
            roundedRect(BORDER / 2, BORDER / 2, width - BORDER, height - BORDER, CORNER)
            stroke()

            beginText()
            setFont(FONT, FONT_SIZE)
            setNonStrokingColor(color.r, color.g, color.b)
            newLineAtOffset(PADDING_X, PADDING_Y)
            showText(text)
            endText()
        }
    }

    /** A closed rectangle path with corners of radius [r], drawn with Bézier quarter circles. */
    private fun PDPageContentStream.roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        val k = r * 0.5523f // control-point distance for a circular arc
        val right = x + w
        val top = y + h
        moveTo(x + r, y)
        lineTo(right - r, y)
        curveTo(right - r + k, y, right, y + r - k, right, y + r)
        lineTo(right, top - r)
        curveTo(right, top - r + k, right - r + k, top, right - r, top)
        lineTo(x + r, top)
        curveTo(x + r - k, top, x, top - r + k, x, top - r)
        lineTo(x, y + r)
        curveTo(x, y + r - k, x + r - k, y, x + r, y)
        closePath()
    }
}
