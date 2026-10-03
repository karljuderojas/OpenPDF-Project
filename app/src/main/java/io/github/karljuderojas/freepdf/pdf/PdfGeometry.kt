package io.github.karljuderojas.freepdf.pdf

/**
 * Geometry in PDF user space: points (1/72 inch), origin at the bottom-left of the page.
 * The UI converts touch coordinates into this space before calling the PDF layer.
 */
data class PdfPoint(val x: Float, val y: Float)

data class PdfRect(val left: Float, val bottom: Float, val right: Float, val top: Float) {
    val width: Float get() = right - left
    val height: Float get() = top - bottom
}

/** Converts a point in a rendered page bitmap (origin top-left) to PDF user space. */
fun bitmapToPdf(
    x: Float,
    y: Float,
    bitmapWidth: Int,
    bitmapHeight: Int,
    pageWidthPt: Float,
    pageHeightPt: Float,
): PdfPoint = PdfPoint(
    x = x / bitmapWidth * pageWidthPt,
    y = pageHeightPt - y / bitmapHeight * pageHeightPt,
)

/**
 * Converts a point on a page as displayed (0..1 across and down, origin top-left) to PDF user
 * space. Viewers show a page turned by its /Rotate value (clockwise), while annotations are
 * stored in the page's unrotated space, so the point is turned back first.
 */
fun displayToPdf(nx: Float, ny: Float, rotation: Int, cropBox: PdfRect): PdfPoint {
    // (u, v): fractions across and down the unrotated page.
    val (u, v) = when (((rotation % 360) + 360) % 360) {
        90 -> ny to 1f - nx
        180 -> 1f - nx to 1f - ny
        270 -> 1f - ny to nx
        else -> nx to ny
    }
    return PdfPoint(cropBox.left + u * cropBox.width, cropBox.top - v * cropBox.height)
}

/** A box on a page as displayed, in fractions (0..1 across and down, origin top-left). */
data class DisplayRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Where [rect], in the page's unrotated PDF space, appears on the page as displayed. The inverse of [displayToPdf]. */
fun pdfToDisplay(rect: PdfRect, rotation: Int, cropBox: PdfRect): DisplayRect {
    fun corner(x: Float, y: Float): Pair<Float, Float> {
        val u = (x - cropBox.left) / cropBox.width
        val v = (cropBox.top - y) / cropBox.height
        return when (((rotation % 360) + 360) % 360) {
            90 -> 1f - v to u
            180 -> 1f - u to 1f - v
            270 -> v to 1f - u
            else -> u to v
        }
    }
    val a = corner(rect.left, rect.bottom)
    val b = corner(rect.right, rect.top)
    return DisplayRect(minOf(a.first, b.first), minOf(a.second, b.second), maxOf(a.first, b.first), maxOf(a.second, b.second))
}
