package io.github.karljuderojas.freepdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfGeometryTest {
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
}
