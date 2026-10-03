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
    fun pdfBoxesMapBackToWhereTheyAreShown() {
        val box = PdfRect(72f, 640f, 540f, 662f)
        listOf(0, 90, 180, 270).forEach { rotation ->
            val shown = pdfToDisplay(box, rotation, letter)
            // Both corners shown on screen map back to the box's corners.
            val a = displayToPdf(shown.left, shown.top, rotation, letter)
            val b = displayToPdf(shown.right, shown.bottom, rotation, letter)
            assertEquals("rotation $rotation", 72f, minOf(a.x, b.x), 0.01f)
            assertEquals("rotation $rotation", 540f, maxOf(a.x, b.x), 0.01f)
            assertEquals("rotation $rotation", 640f, minOf(a.y, b.y), 0.01f)
            assertEquals("rotation $rotation", 662f, maxOf(a.y, b.y), 0.01f)
        }
        // Unrotated, a box near the top of the page is shown near the top.
        val shown = pdfToDisplay(box, 0, letter)
        assertEquals(72f / 612f, shown.left, 0.001f)
        assertEquals(130f / 792f, shown.top, 0.001f)
    }

    @Test
    fun cropBoxOffsetIsApplied() {
        assertPoint(10f, 800f, displayToPdf(0f, 0f, 0, PdfRect(10f, 8f, 622f, 800f)))
    }

    @Test
    fun pdfToDisplayUndoesDisplayToPdf() {
        val crop = PdfRect(10f, 8f, 622f, 800f)
        for (rotation in listOf(0, 90, 180, 270)) {
            for ((nx, ny) in listOf(0f to 0f, 1f to 1f, 0.25f to 0.7f, 0.9f to 0.1f)) {
                val p = displayToPdf(nx, ny, rotation, crop)
                val (bx, by) = pdfToDisplay(p.x, p.y, rotation, crop)
                assertEquals("x at $rotation°", nx, bx, 0.0001f)
                assertEquals("y at $rotation°", ny, by, 0.0001f)
            }
        }
    }

    private fun assertPoint(x: Float, y: Float, p: PdfPoint) {
        assertEquals(x, p.x, 0.001f)
        assertEquals(y, p.y, 0.001f)
    }
}
