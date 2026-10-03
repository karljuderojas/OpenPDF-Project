package io.github.karljuderojas.freepdf.pdf.create

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ImagesToPdfTest {

    private fun picture(width: Int, height: Int, color: Int = Color.RED, alpha: Boolean = false): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            setHasAlpha(alpha)
        }

    @Test
    fun aPagePicturedAfterItsPictureKeepsTheShapeWithAnA4LongSide() {
        val tall = ImagesToPdf.place(1000, 2000, PageFit.Picture)
        assertEquals(421f, tall.pageWidth, 0.01f)
        assertEquals(842f, tall.pageHeight, 0.01f)
        assertEquals(tall.pageWidth, tall.width, 0.01f)
        assertEquals(0f, tall.x, 0f)
        val wide = ImagesToPdf.place(4000, 1000, PageFit.Picture)
        assertEquals(842f, wide.pageWidth, 0.01f)
        assertEquals(210.5f, wide.pageHeight, 0.01f)
    }

    @Test
    fun a4TurnsToSuitThePictureAndCentresItInsideAMargin() {
        val portrait = ImagesToPdf.place(300, 400, PageFit.A4)
        assertEquals(595f, portrait.pageWidth, 0f)
        assertEquals(842f, portrait.pageHeight, 0f)
        val landscape = ImagesToPdf.place(400, 300, PageFit.A4)
        assertEquals(842f, landscape.pageWidth, 0f)
        assertEquals(595f, landscape.pageHeight, 0f)
        for (p in listOf(portrait, landscape)) {
            assertTrue(p.x >= 24f - 0.01f && p.y >= 24f - 0.01f)
            assertTrue(p.x + p.width <= p.pageWidth - 24f + 0.01f)
            assertTrue(p.y + p.height <= p.pageHeight - 24f + 0.01f)
            assertEquals(p.pageWidth - p.x - p.width, p.x, 0.01f)
            assertEquals(p.pageHeight - p.y - p.height, p.y, 0.01f)
        }
        // The picture's shape is kept.
        assertEquals(300f / 400f, portrait.width / portrait.height, 0.001f)
    }

    @Test
    fun oneJpegPagePerPictureInOrder() {
        val sizes = listOf(100 to 200, 300 to 150, 120 to 120)
        val progress = mutableListOf<Int>()
        val document = ImagesToPdf.build(sizes.size, PageFit.Picture, progress::add) { sizes[it].let { (w, h) -> picture(w, h) } }
        val bytes = ByteArrayOutputStream().also { out -> document.use { it.save(out) } }.toByteArray()
        assertEquals(listOf(1, 2, 3), progress)
        PDDocument.load(ByteArrayInputStream(bytes)).use { saved ->
            assertEquals(3, saved.numberOfPages)
            val first = saved.getPage(0).mediaBox
            val second = saved.getPage(1).mediaBox
            assertTrue("page 1 should be taller than wide", first.height > first.width)
            assertTrue("page 2 should be wider than tall", second.width > second.height)
            val names = saved.getPage(0).resources.xObjectNames.toList()
            assertEquals(1, names.size)
        }
    }

    @Test
    fun aPictureThatFailsToLoadClosesTheDocumentAndThrows() {
        var failed = false
        try {
            ImagesToPdf.build(2, PageFit.A4) { i -> if (i == 1) error("gone") else picture(50, 50) }
        } catch (e: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun transparentPicturesAreKept() {
        val document = ImagesToPdf.build(1, PageFit.A4) { picture(40, 40, Color.TRANSPARENT, alpha = true) }
        document.use { assertEquals(1, it.numberOfPages) }
    }
}
