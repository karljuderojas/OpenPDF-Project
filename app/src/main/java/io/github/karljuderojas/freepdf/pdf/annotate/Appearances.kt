package io.github.karljuderojas.freepdf.pdf.annotate

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.util.Matrix
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import java.util.Calendar

/**
 * Shared plumbing for annotations whose appearance we draw ourselves (text boxes, stamps).
 *
 * Annotation appearances are drawn in the page's unrotated space, so on a page with /Rotate 90
 * a plainly drawn box comes out sideways. Instead the appearance is drawn upright in its own
 * frame (BBox 0,0 to width,height) and given a /Matrix that turns it with the page; viewers
 * then fit the turned box into /Rect, which is sized to match.
 */
internal object Appearances {

    fun normalizedRotation(degrees: Int) = ((degrees % 360) + 360) % 360

    /**
     * Builds a normal appearance of [width] x [height] (as shown, upright) and sets it on
     * [annotation]. [draw] runs with the origin at the bottom-left of the upright box.
     */
    fun set(
        document: PDDocument,
        annotation: PDAnnotation,
        width: Float,
        height: Float,
        rotation: Int,
        draw: PDPageContentStream.() -> Unit,
    ) {
        val appearance = PDAppearanceStream(document).apply {
            setBBox(PDRectangle(0f, 0f, width, height))
            // Set before opening the stream: the content stream adds fonts and graphics
            // states to whatever dictionary is here.
            resources = PDResources()
            cosObject.setItem(COSName.MATRIX, matrixFor(rotation).toCOSArray())
        }
        // PDPageContentStream rather than PDAppearanceContentStream: it knows the document,
        // so an embedded font used here is subset and written out when the document is saved.
        PDPageContentStream(document, appearance).use { it.draw() }
        annotation.appearance = PDAppearanceDictionary().apply { setNormalAppearance(appearance) }
    }

    /** Exact rotation matrices, so no 6e-17 noise ends up in the file. */
    private fun matrixFor(rotation: Int): Matrix = when (normalizedRotation(rotation)) {
        90 -> Matrix(0f, 1f, -1f, 0f, 0f, 0f)
        180 -> Matrix(-1f, 0f, 0f, -1f, 0f, 0f)
        270 -> Matrix(0f, -1f, 1f, 0f, 0f, 0f)
        else -> Matrix()
    }

    /** The turn an appearance made by [set] was given, read back from its /Matrix; 0 when there is none. */
    fun rotationOf(annotation: PDAnnotation): Int {
        val m = annotation.normalAppearanceStream?.matrix ?: return 0
        return when {
            m.shearY > 0f -> 90
            m.shearY < 0f -> 270
            m.scaleX < 0f -> 180
            else -> 0
        }
    }

    /**
     * The page-space rectangle for a box of [width] x [height] whose top-left corner, as the
     * page is displayed, is at [topLeft].
     */
    fun rectFromTopLeft(topLeft: PdfPoint, width: Float, height: Float, rotation: Int): PdfRect {
        val (x, y) = topLeft
        return when (normalizedRotation(rotation)) {
            90 -> PdfRect(x, y, x + height, y + width)
            180 -> PdfRect(x - width, y, x, y + height)
            270 -> PdfRect(x - height, y - width, x, y)
            else -> PdfRect(x, y - height, x + width, y)
        }
    }

    /** The reverse of [rectFromTopLeft]: which corner of [rect] is the displayed top-left. */
    fun topLeftOf(rect: PdfRect, rotation: Int): PdfPoint = when (normalizedRotation(rotation)) {
        90 -> PdfPoint(rect.left, rect.bottom)
        180 -> PdfPoint(rect.right, rect.bottom)
        270 -> PdfPoint(rect.right, rect.top)
        else -> PdfPoint(rect.left, rect.top)
    }

    /** The page-space rectangle for a box of [width] x [height] (as shown) centred on [center]. */
    fun rectAround(center: PdfPoint, width: Float, height: Float, rotation: Int): PdfRect {
        val sideways = normalizedRotation(rotation) % 180 != 0
        val w = if (sideways) height else width
        val h = if (sideways) width else height
        return PdfRect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
    }

    /** Author, dates and print flag, as Annotator sets them on its own marks. */
    fun PDAnnotationMarkup.stamp(author: String?) {
        if (author != null) titlePopup = author
        creationDate = Calendar.getInstance()
        setModifiedDate(Calendar.getInstance())
        isPrinted = true
    }

    fun PdfRect.toPdRectangle() = PDRectangle(left, bottom, width, height)

    fun PDRectangle.toPdfRect() = PdfRect(lowerLeftX, lowerLeftY, upperRightX, upperRightY)
}
