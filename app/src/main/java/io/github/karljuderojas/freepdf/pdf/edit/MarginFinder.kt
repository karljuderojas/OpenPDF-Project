package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

/**
 * Finds the blank margins around what is printed on a page, so they can be trimmed away with
 * [PageCrop]. It looks at a small picture of the page as it is shown, so a turned page or one that
 * is already cropped needs no special care: the answer is in the same terms as [CropMargins].
 */
object MarginFinder {

    /** How wide the picture of the page should be: enough to catch thin lines, cheap to scan. */
    const val PICTURE_WIDTH_PX = 300

    /** A little breathing room left around the content, as a fraction of the page's width or height. */
    const val PADDING = 0.015f

    /** Margins smaller than this are not worth a crop. */
    private const val MIN_WORTH_TRIMMING = 0.01f

    /** Pixels lighter than this (or see-through) count as paper. */
    private const val PAPER = 245
    private const val SEE_THROUGH = 24

    /**
     * The margins to trim from [bitmap], a picture of a whole page, or null when the page is blank
     * or has no margin worth trimming.
     */
    fun find(bitmap: Bitmap, padding: Float = PADDING): CropMargins? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 1 || height < 1) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return find(pixels, width, height, padding)
    }

    /** As above, for [pixels] in ARGB, row by row, [width] by [height]. */
    fun find(pixels: IntArray, width: Int, height: Int, padding: Float = PADDING): CropMargins? {
        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isInk(pixels[y * width + x])) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null // Nothing printed: a blank page has no margins to find.

        fun edge(blank: Int, size: Int) = (blank.toFloat() / size - padding).coerceIn(0f, CropMargins.MAX_EDGE)
        val margins = CropMargins(
            left = edge(minX, width),
            top = edge(minY, height),
            right = edge(width - 1 - maxX, width),
            bottom = edge(height - 1 - maxY, height),
        )
        val worth = listOf(margins.left, margins.top, margins.right, margins.bottom).any { it >= MIN_WORTH_TRIMMING }
        return margins.takeIf { worth && it.isValid }
    }

    private fun isInk(argb: Int): Boolean {
        if (argb.alpha < SEE_THROUGH) return false
        return minOf(argb.red, argb.green, argb.blue) < PAPER
    }
}
