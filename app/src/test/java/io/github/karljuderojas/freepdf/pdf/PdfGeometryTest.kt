package io.github.karljuderojas.freepdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfGeometryTest {
    private val letter = PdfRect(0f, 0f, 612f, 792f)

    @Test
    fun bitmapTopLeftMapsToPdfTopLeft() {
        val p = bitmapToPdf(0f, 0f, bitmapWidth = 1000, bitmapHeight = 1294, pageWidthPt = 612f, pageHeightPt = 792f)
        assertEquals(0f, p.x, 0.001f)
        assertEquals(792f, p.y, 0.001f)
    }

    @Test
    fun bitmapCentreMapsToPageCentre() {
        val p = bitmapToPdf(500f, 647f, bitmapWidth = 1000, bitmapHeight = 1294, pageWidthPt = 612f, pageHeightPt = 792f)
        assertEquals(306f, p.x, 0.001f)
        assertEquals(396f, p.y, 0.001f)
    }

    @Test
    fun unrotatedDisplayTopLeftIsPdfTopLeft() {
        assertPoint(0f, 792f, displayToPdf(0f, 0f, 0, letter))
        assertPoint(612f, 0f, displayToPdf(1f, 1f, 0, letter))
    }

    @Test
    fun rotatedPagesMapCornersBack() {
        // Turned 90° clockwise, the page's bottom-left corner is shown at the top-left.
        assertPoint(0f, 0f, displayToPdf(0f, 0f, 90, letter))
        assertPoint(612f, 0f, displayToPdf(0f, 0f, 180, letter))
        assertPoint(612f, 792f, displayToPdf(0f, 0f, 270, letter))
        assertPoint(0f, 792f, displayToPdf(1f, 0f, 90, letter))
    }

    @Test
    fun cropBoxOffsetIsApplied() {
        assertPoint(10f, 800f, displayToPdf(0f, 0f, 0, PdfRect(10f, 8f, 622f, 800f)))
    }

    private fun assertPoint(x: Float, y: Float, p: PdfPoint) {
        assertEquals(x, p.x, 0.001f)
        assertEquals(y, p.y, 0.001f)
    }
}
