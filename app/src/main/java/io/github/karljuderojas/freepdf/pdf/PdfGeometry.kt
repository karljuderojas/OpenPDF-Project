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
