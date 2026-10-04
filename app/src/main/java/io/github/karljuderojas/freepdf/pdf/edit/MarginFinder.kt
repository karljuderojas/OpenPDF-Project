package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import kotlin.math.ceil

/**
 * Finds the blank margins around what is printed on a page, so they can be trimmed away with
 * [PageCrop]. It looks at a small picture of the page as it is shown, so a turned page or one that
 * is already cropped needs no special care: the answer is in the same terms as [CropMargins].
 *
 * The paper's colour is read off the page's own border, so cream paper, a scan's grey and the
 * grain of its noise all count as paper; ink is what is clearly darker than that. A lone dark
 * pixel is dust, not ink, and the outermost sliver of the page, where a scanner leaves its edge
 * line, is not looked at.
 */
object MarginFinder {

    /** How wide the picture of the page should be: enough to catch thin lines, cheap to scan. */
    const val PICTURE_WIDTH_PX = 300

    /** A little breathing room left around the content, as a fraction of the page's width or height. */
    const val PADDING = 0.015f

    /** Margins smaller than this are not worth a crop. */
    private const val MIN_WORTH_TRIMMING = 0.01f

    /** The outer part of the page, as a fraction of each side, that shows what the paper looks like. */
    private const val BORDER = 0.05f

    /** The paper is as light as most of the border; its grain is what the darkest part of the border still is. */
    private const val PAPER_PERCENTILE = 0.90f
    private const val GRAIN_PERCENTILE = 0.05f

    /** The grain is never taken to be more than this much darker than the paper, in case the border holds print. */
    private const val MAX_GRAIN = 40

    /** Ink is at least this much darker than the paper's grain. */
    private const val INK_CONTRAST = 12

    /** The outermost sliver of the page, as a fraction of each side, where a scanner's edge line falls. */
    private const val EDGE = 0.01f

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
        if (width < 1 || height < 1) return null
        val brightness = IntArray(width * height) { brightnessOf(pixels[it]) }

        // What the paper looks like, from the border of the page.
        val histogram = IntArray(256)
        var counted = 0
        val borderX = maxOf(1, (width * BORDER).toInt())
        val borderY = maxOf(1, (height * BORDER).toInt())
        for (y in 0 until height) {
            val edgeRow = y < borderY || y >= height - borderY
            for (x in 0 until width) {
                if (edgeRow || x < borderX || x >= width - borderX) {
                    histogram[brightness[y * width + x]]++
                    counted++
                }
            }
        }
        val paper = percentile(histogram, counted, PAPER_PERCENTILE)
        val grain = maxOf(percentile(histogram, counted, GRAIN_PERCENTILE), paper - MAX_GRAIN)
        val inkBelow = grain - INK_CONTRAST
        if (inkBelow <= 0) return null // Dark paper, or a page printed to its edges: nothing to find.

        val ink = BooleanArray(width * height) { brightness[it] < inkBelow }
        val edgeX = (width * EDGE).toInt()
        val edgeY = (height * EDGE).toInt()
        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        for (y in edgeY until height - edgeY) {
            for (x in edgeX until width - edgeX) {
                if (ink[y * width + x] && hasInkBeside(ink, width, height, x, y)) {
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
        return margins.takeIf { isWorthTrimming(it) && it.isValid }
    }

    /**
     * One crop for several pages whose margins were [found]: the smallest margin on each edge, so
     * that pages keep one size and a page with little on it is not cut down to a patch. Null when
     * nothing was found or when a page is printed to its edge, which leaves nothing to trim by.
     */
    fun shared(found: Collection<CropMargins>): CropMargins? {
        if (found.isEmpty()) return null
        val margins = CropMargins(
            left = found.minOf { it.left },
            top = found.minOf { it.top },
            right = found.minOf { it.right },
            bottom = found.minOf { it.bottom },
        )
        return margins.takeIf { isWorthTrimming(it) && it.isValid }
    }

    private fun isWorthTrimming(margins: CropMargins) =
        listOf(margins.left, margins.top, margins.right, margins.bottom).any { it >= MIN_WORTH_TRIMMING }

    /** How light a pixel is: its darkest channel, so coloured print counts as ink; see-through is paper. */
    private fun brightnessOf(argb: Int): Int {
        if (argb.alpha < SEE_THROUGH) return 255
        return minOf(argb.red, argb.green, argb.blue)
    }

    /** The brightness that [fraction] of the [counted] pixels in [histogram] are at or below. */
    private fun percentile(histogram: IntArray, counted: Int, fraction: Float): Int {
        val target = ceil(counted * fraction).toInt()
        var seen = 0
        for (value in 0..255) {
            seen += histogram[value]
            if (seen >= target) return value
        }
        return 255
    }

    private fun hasInkBeside(ink: BooleanArray, width: Int, height: Int, x: Int, y: Int): Boolean {
        for (dy in -1..1) {
            val yy = y + dy
            if (yy < 0 || yy >= height) continue
            for (dx in -1..1) {
                val xx = x + dx
                if ((dx == 0 && dy == 0) || xx < 0 || xx >= width) continue
                if (ink[yy * width + xx]) return true
            }
        }
        return false
    }
}
