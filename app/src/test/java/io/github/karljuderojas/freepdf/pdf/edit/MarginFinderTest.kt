package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarginFinderTest {

    /** A 200 by 400 white page with a black block from (40, 100) to (159, 299). */
    private fun page(): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(40f, 100f, 160f, 300f, Paint().apply { color = Color.BLACK })
        return bitmap
    }

    @Test
    fun findsTheWhiteAroundThePrintedBlock() {
        val m = MarginFinder.find(page(), padding = 0f)!!
        assertEquals(40f / 200, m.left, 0.001f)
        assertEquals(100f / 400, m.top, 0.001f)
        assertEquals(40f / 200, m.right, 0.001f)
        assertEquals(100f / 400, m.bottom, 0.001f)
    }

    @Test
    fun leavesPaddingAroundTheContent() {
        val m = MarginFinder.find(page(), padding = 0.02f)!!
        assertEquals(40f / 200 - 0.02f, m.left, 0.001f)
        assertEquals(100f / 400 - 0.02f, m.top, 0.001f)
    }

    @Test
    fun aBlankPageHasNothingToTrim() {
        val blank = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.WHITE) }
        assertNull(MarginFinder.find(blank))
    }

    @Test
    fun aSeeThroughPageCountsAsBlank() {
        assertNull(MarginFinder.find(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)))
    }

    @Test
    fun aPageThatFillsItsEdgesHasNoMargin() {
        val full = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.BLACK) }
        assertNull(MarginFinder.find(full))
    }

    @Test
    fun contentInOneCornerLeavesAValidCrop() {
        val pixels = IntArray(100 * 100) { Color.WHITE }
        pixels[5 * 100 + 5] = Color.BLACK
        val m = MarginFinder.find(pixels, 100, 100, padding = 0f)!!
        assertEquals(true, m.isValid)
        assertEquals(CropMargins.MAX_EDGE, m.right, 0f)
    }

    @Test
    fun theFoundMarginsCropARotatedPageAsItIsShown() {
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf")).use { document ->
            val page = document.getPage(0)
            page.rotation = 90
            val before = page.cropBox
            // Content found hugging the shown page's left: the page's own bottom edge is kept.
            PageCrop.crop(document, listOf(0), CropMargins(left = 0.1f))
            val after = page.cropBox
            assertEquals(before.lowerLeftY + before.height * 0.1f, after.lowerLeftY, 0.01f)
            assertEquals(before.upperRightY, after.upperRightY, 0.01f)
        }
    }
}
