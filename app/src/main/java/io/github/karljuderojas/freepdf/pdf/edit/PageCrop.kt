package io.github.karljuderojas.freepdf.pdf.edit

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlin.math.abs
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.displayToPdf

/**
 * How much to trim from each edge of a page as it is shown, as fractions of its width (left and
 * right) and height (top and bottom). So 0.1 on the left takes off a tenth of the page's width.
 */
data class CropMargins(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    val isEmpty: Boolean get() = left <= 0f && top <= 0f && right <= 0f && bottom <= 0f

    /** True when every edge is between 0 and [MAX_EDGE] and what is left of the page is not tiny. */
    val isValid: Boolean
        get() = listOf(left, top, right, bottom).all { it in 0f..MAX_EDGE } &&
            left + right <= MAX_TOTAL && top + bottom <= MAX_TOTAL

    companion object {
        const val MAX_EDGE = 0.45f
        const val MAX_TOTAL = 0.9f
    }
}

/**
 * Crops pages by setting their /CropBox, which is what every viewer shows. The page's content is
 * not removed, so a crop can be undone with [reset]; it is not a way to hide something for good.
 */
object PageCrop {

    /**
     * Trims each of [pageIndexes] by [margins], measured from the page as it is shown now (turned
     * by its /Rotate, and already cropped if it was). Cropping twice trims twice.
     */
    fun crop(document: PDDocument, pageIndexes: Collection<Int>, margins: CropMargins) {
        require(margins.isValid) { "Cannot crop by $margins" }
        pageIndexes.toSet().forEach { index ->
            val page = document.getPage(index)
            val box = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
            // Two opposite corners of what is kept, back in the page's own (unrotated) space.
            val a = displayToPdf(margins.left, margins.top, page.rotation, box)
            val b = displayToPdf(1f - margins.right, 1f - margins.bottom, page.rotation, box)
            val left = minOf(a.x, b.x)
            val bottom = minOf(a.y, b.y)
            val crop = PDRectangle(left, bottom, maxOf(a.x, b.x) - left, maxOf(a.y, b.y) - bottom)
            page.cropBox = crop
            // The print boxes, when the page has them, must not reach outside what is shown.
            PRINT_BOXES.forEach { key ->
                val printBox = page.cosObject.getCOSArray(key) ?: return@forEach
                page.cosObject.setItem(key, intersect(PDRectangle(printBox), crop).cosArray)
            }
        }
    }

    /**
     * Shows [pageIndexes] in full again, as far as the page itself goes (its /MediaBox), and drops
     * the print boxes that [crop] shrank with it. Returns false when none of the pages was cropped,
     * so the caller can skip the undo step.
     */
    fun reset(document: PDDocument, pageIndexes: Collection<Int>): Boolean {
        var changed = false
        pageIndexes.toSet().forEach { index ->
            val page = document.getPage(index)
            if (!isCropped(page)) return@forEach
            val media = page.mediaBox
            page.cropBox = PDRectangle(media.lowerLeftX, media.lowerLeftY, media.width, media.height)
            PRINT_BOXES.forEach { page.cosObject.removeItem(it) }
            changed = true
        }
        return changed
    }

    /** True when [page] shows less than its /MediaBox. */
    fun isCropped(page: PDPage): Boolean {
        val crop = page.cropBox
        val media = page.mediaBox
        return listOf(
            crop.lowerLeftX - media.lowerLeftX, crop.lowerLeftY - media.lowerLeftY,
            crop.upperRightX - media.upperRightX, crop.upperRightY - media.upperRightY,
        ).any { abs(it) > TOLERANCE }
    }

    private fun intersect(a: PDRectangle, b: PDRectangle): PDRectangle {
        val left = maxOf(a.lowerLeftX, b.lowerLeftX)
        val bottom = maxOf(a.lowerLeftY, b.lowerLeftY)
        val right = minOf(a.upperRightX, b.upperRightX).coerceAtLeast(left)
        val top = minOf(a.upperRightY, b.upperRightY).coerceAtLeast(bottom)
        return PDRectangle(left, bottom, right - left, top - bottom)
    }

    private val PRINT_BOXES = listOf(COSName.TRIM_BOX, COSName.BLEED_BOX, COSName.ART_BOX)
    private const val TOLERANCE = 0.01f
}
