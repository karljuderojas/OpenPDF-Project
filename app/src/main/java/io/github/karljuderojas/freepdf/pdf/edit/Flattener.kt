package io.github.karljuderojas.freepdf.pdf.edit

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup
import com.tom_roush.pdfbox.util.Matrix

/**
 * Makes a "locked copy": highlights, drawings, notes, signatures and filled-in form fields become
 * part of the page itself, so whoever receives the file can no longer move, edit or delete them.
 * Links keep working.
 */
object Flattener {

    fun flatten(document: PDDocument) {
        document.documentCatalog.acroForm?.let { form ->
            // Fields filled in without an appearance would flatten blank, so draw them first.
            // If a field's font cannot be used, flatten what is there rather than fail.
            if (form.needAppearances) runCatching { form.refreshAppearances() }
            form.flatten()
        }
        for (page in document.pages) {
            val annotations = page.annotations
            val keep = ArrayList<PDAnnotation>()
            val draw = ArrayList<PDAnnotation>()
            for (annotation in annotations) {
                when {
                    annotation is PDAnnotationLink -> keep += annotation
                    // A popup is only the window a note opens in; the note itself is drawn.
                    annotation is PDAnnotationPopup -> Unit
                    annotation.isHidden || annotation.isNoView -> Unit
                    // Nothing to draw it with, so it stays as it is rather than vanish.
                    annotation.normalAppearanceStream == null -> keep += annotation
                    else -> draw += annotation
                }
            }
            if (draw.isNotEmpty()) {
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    draw.forEach { stream.drawAppearance(it) }
                }
            }
            if (keep.size != annotations.size) page.annotations = keep
        }
    }

    /**
     * Paints [annotation]'s appearance where a viewer would: per the PDF spec (12.5.5), the
     * appearance's bounding box, after its own matrix, is stretched to the annotation's rectangle.
     */
    private fun PDPageContentStream.drawAppearance(annotation: PDAnnotation) {
        val appearance = annotation.normalAppearanceStream ?: return
        val rect = annotation.rectangle ?: return
        val box = appearance.bBox ?: return
        val m = appearance.matrix
        val corners = listOf(box.lowerLeftX to box.lowerLeftY, box.upperRightX to box.lowerLeftY,
            box.lowerLeftX to box.upperRightY, box.upperRightX to box.upperRightY)
            .map { (x, y) -> (m.scaleX * x + m.shearX * y + m.translateX) to (m.shearY * x + m.scaleY * y + m.translateY) }
        val left = corners.minOf { it.first }
        val bottom = corners.minOf { it.second }
        val width = corners.maxOf { it.first } - left
        val height = corners.maxOf { it.second } - bottom
        if (width <= 0f || height <= 0f || rect.width <= 0f || rect.height <= 0f) return
        val sx = rect.width / width
        val sy = rect.height / height
        saveGraphicsState()
        transform(Matrix(sx, 0f, 0f, sy, rect.lowerLeftX - left * sx, rect.lowerLeftY - bottom * sy))
        drawForm(appearance)
        restoreGraphicsState()
    }
}
