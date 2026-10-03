package io.github.karljuderojas.freepdf.pdf.sign

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor.uprightAt
import io.github.karljuderojas.freepdf.pdf.pdfToDisplay
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * A placed signature kept as a stamp annotation rather than page content, so it can still be
 * moved or deleted, here or in another PDF app. Finish with "Lock signatures" calls [lock], which
 * draws each one into its page for good before the copy is sealed.
 */
object SignatureAnnotation {

    // Marks the stamps FreePDF made, so locking leaves other apps' stamps alone. Initials carry
    // their own marker, so only full signatures count towards the places to sign.
    private const val NAME_PREFIX = "freepdf-signature-"
    private const val INITIALS_PREFIX = NAME_PREFIX + "initials-"

    /** A full signature placed in the document: its page and centre as displayed (fractions, origin top-left). */
    data class Placed(val page: Int, val x: Float, val y: Float)

    /**
     * Adds [signature] to the page the same way [SignatureStamper.stamp] draws it: fitted into
     * [maxWidth] by [maxHeight] points, centred over [at] with its bottom on [at], and upright as
     * the page is shown. [initials] marks it as initials rather than a full signature.
     */
    fun add(
        document: PDDocument,
        pageIndex: Int,
        signature: Bitmap,
        at: PdfPoint,
        maxWidth: Float,
        maxHeight: Float,
        initials: Boolean = false,
    ) {
        val page = document.getPage(pageIndex)
        val image = LosslessFactory.createFromImage(document, signature)
        val scale = minOf(maxWidth / image.width, maxHeight / image.height)
        val width = image.width * scale
        val height = image.height * scale

        // The appearance is drawn in page space, so its box is the annotation's own rectangle and
        // readers place it without any further scaling.
        val rect = boundsOf(listOf(-width / 2 to 0f, width / 2 to 0f, -width / 2 to height, width / 2 to height), at, page.rotation)
        val look = PDAppearanceStream(document).apply {
            bBox = rect
            resources = PDResources()
        }
        PDPageContentStream(document, look).use { stream ->
            stream.uprightAt(page, at) { drawImage(image, -width / 2, 0f, width, height) }
        }
        val annotation = PDAnnotationRubberStamp().apply {
            rectangle = rect
            annotationName = (if (initials) INITIALS_PREFIX else NAME_PREFIX) + UUID.randomUUID()
            isPrinted = true
            appearance = PDAppearanceDictionary().apply { setNormalAppearance(look) }
        }
        page.annotations = page.annotations + annotation
    }

    fun isSignature(annotation: PDAnnotation): Boolean =
        annotation is PDAnnotationRubberStamp && annotation.annotationName?.startsWith(NAME_PREFIX) == true

    fun isInitials(annotation: PDAnnotation): Boolean =
        isSignature(annotation) && annotation.annotationName?.startsWith(INITIALS_PREFIX) == true

    /**
     * Where the full signatures still in [document] sit, read from the pages themselves, so the
     * answer stays right after pages are moved, deleted, rotated, or an edit is undone.
     */
    fun placed(document: PDDocument): List<Placed> = document.pages.flatMapIndexed { index, page ->
        val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
        page.annotations.filter { isSignature(it) && !isInitials(it) }.map { annotation ->
            val rect = annotation.rectangle
            val (x, y) = pdfToDisplay((rect.lowerLeftX + rect.upperRightX) / 2, (rect.lowerLeftY + rect.upperRightY) / 2, page.rotation, crop)
            Placed(index, x, y)
        }
    }

    /** Draws every FreePDF signature into its page's content and removes the annotations. Returns how many. */
    fun lock(document: PDDocument): Int {
        var locked = 0
        document.pages.forEach { page ->
            val annotations = page.annotations
            val signatures = annotations.filter(::isSignature)
            if (signatures.isEmpty()) return@forEach
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                signatures.forEach { annotation -> annotation.normalAppearanceStream?.let { stream.drawForm(it) } }
            }
            page.annotations = annotations.filterNot(::isSignature)
            locked += signatures.size
        }
        return locked
    }

    /** The page-space box around [corners], given relative to [at] on a page turned by [rotation] degrees. */
    private fun boundsOf(corners: List<Pair<Float, Float>>, at: PdfPoint, rotation: Int): PDRectangle {
        // Same turn as uprightAt: rotate about the origin, then move to [at].
        val radians = Math.toRadians((((rotation % 360) + 360) % 360).toDouble())
        val points = corners.map { (x, y) ->
            (at.x + x * cos(radians) - y * sin(radians)).toFloat() to (at.y + x * sin(radians) + y * cos(radians)).toFloat()
        }
        val left = points.minOf { it.first }
        val bottom = points.minOf { it.second }
        return PDRectangle(left, bottom, points.maxOf { it.first } - left, points.maxOf { it.second } - bottom)
    }
}
