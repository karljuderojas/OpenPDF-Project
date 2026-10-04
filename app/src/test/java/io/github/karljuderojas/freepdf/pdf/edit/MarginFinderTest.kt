package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.util.Random

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarginFinderTest {

    /** A 200 by 400 white page with a black block from (40, 100) to (159, 299). */
    private fun page(): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(40f, 100f, 160f, 300f, Paint().apply { color = Color.BLACK })
        return bitmap
    }

    /** The same page as pixels, with [paper] and [ink] in place of white and black. */
    private fun pagePixels(paper: (x: Int, y: Int) -> Int = { _, _ -> Color.WHITE }, ink: Int = Color.BLACK): IntArray =
        IntArray(200 * 400) { i ->
            val x = i % 200
            val y = i / 200
            if (x in 40..159 && y in 100..299) ink else paper(x, y)
        }

    private fun assertBlockMargins(m: CropMargins?) {
        assertNotNull("margins found", m)
        assertEquals(40f / 200, m!!.left, 0.006f)
        assertEquals(100f / 400, m.top, 0.006f)
        assertEquals(40f / 200, m.right, 0.006f)
        assertEquals(100f / 400, m.bottom, 0.006f)
    }

    @Test
    fun findsTheWhiteAroundThePrintedBlock() {
        assertBlockMargins(MarginFinder.find(page(), padding = 0f))
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
        pixels[5 * 100 + 6] = Color.BLACK
        val m = MarginFinder.find(pixels, 100, 100, padding = 0f)!!
        assertEquals(true, m.isValid)
        assertEquals(CropMargins.MAX_EDGE, m.right, 0f)
    }

    @Test
    fun aLoneSpeckOfDustIsNotContent() {
        val pixels = IntArray(100 * 100) { Color.WHITE }
        pixels[5 * 100 + 5] = Color.BLACK
        assertNull(MarginFinder.find(pixels, 100, 100, padding = 0f))
    }

    @Test
    fun theGrainOfAScanIsPaper() {
        // Paper around 250 with noise of about 4 levels on each channel, as a flatbed scan has.
        val random = Random(1)
        fun grainy() = (250 + random.nextGaussian() * 4).toInt().coerceIn(0, 255)
        val pixels = pagePixels(paper = { _, _ -> Color.rgb(grainy(), grainy(), grainy()) })
        assertBlockMargins(MarginFinder.find(pixels, 200, 400, padding = 0f))
    }

    @Test
    fun creamPaperIsPaper() {
        val sepia = Color.rgb(255, 240, 209)
        assertBlockMargins(MarginFinder.find(pagePixels(paper = { _, _ -> sepia }, ink = Color.rgb(60, 40, 20)), 200, 400, padding = 0f))
    }

    @Test
    fun aScannersEdgeLineIsNotContent() {
        val pixels = pagePixels()
        for (y in 0 until 400) pixels[y * 200 + 1] = Color.DKGRAY // A dark line down the left edge.
        for (x in 0 until 200) pixels[398 * 200 + x] = Color.DKGRAY // And one along the bottom.
        assertBlockMargins(MarginFinder.find(pixels, 200, 400, padding = 0f))
    }

    @Test
    fun specksAcrossThePageDoNotWidenTheBox() {
        val random = Random(2)
        val pixels = pagePixels()
        repeat(60) { pixels[random.nextInt(400) * 200 + random.nextInt(200)] = Color.BLACK }
        assertBlockMargins(MarginFinder.find(pixels, 200, 400, padding = 0f))
    }

    @Test
    fun theSharedCropIsTheSmallestMarginOnEachEdge() {
        val shared = MarginFinder.shared(
            listOf(CropMargins(0.1f, 0.1f, 0.1f, 0.1f), CropMargins(0.2f, 0.05f, 0.3f, 0.45f), CropMargins(0.15f, 0.2f, 0.02f, 0.2f)),
        )!!
        assertEquals(CropMargins(0.1f, 0.05f, 0.02f, 0.1f), shared)
    }

    @Test
    fun noSharedCropWhenAPageIsPrintedToItsEdges() {
        assertNull(MarginFinder.shared(emptyList()))
        assertNull(MarginFinder.shared(listOf(CropMargins(0.1f, 0.1f, 0.1f, 0.1f), CropMargins())))
    }

    @Test
    fun theFoundMarginsCropARotatedPageAsItIsShown() {
        // The sample page as PDFium shows it, and as it shows it with /Rotate 90 (turned clockwise).
        val upright = javaClass.classLoader!!.getResourceAsStream("sample/page-1.png").use { BitmapFactory.decodeStream(it) }!!
            .let { Bitmap.createScaledBitmap(it, 300, 300 * it.height / it.width, true) }
        val width = upright.width
        val height = upright.height
        val pixels = IntArray(width * height).also { upright.getPixels(it, 0, width, 0, 0, width, height) }
        val turned = IntArray(width * height) { i ->
            val x = i % height
            val y = i / height
            pixels[(height - 1 - x) * width + y]
        }
        val found = MarginFinder.find(pixels, width, height)!!
        val foundTurned = MarginFinder.find(turned, height, width)!!
        // Turned clockwise, the page's left margin is at the top, its bottom at the left, and so on.
        assertEquals(found.left, foundTurned.top, 0.005f)
        assertEquals(found.top, foundTurned.right, 0.005f)
        assertEquals(found.right, foundTurned.bottom, 0.005f)
        assertEquals(found.bottom, foundTurned.left, 0.005f)
        assertTrue(found.left > 0.05f && found.top > 0.05f)

        // Cropping the turned page by what was found on it keeps the same part of the page.
        fun cropBox(rotation: Int, margins: CropMargins) =
            PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf")).use { document ->
                document.getPage(0).rotation = rotation
                PageCrop.crop(document, listOf(0), margins)
                document.getPage(0).cropBox
            }
        val expected = cropBox(0, found)
        val actual = cropBox(90, foundTurned)
        assertEquals(expected.lowerLeftX, actual.lowerLeftX, 1f)
        assertEquals(expected.lowerLeftY, actual.lowerLeftY, 1f)
        assertEquals(expected.upperRightX, actual.upperRightX, 1f)
        assertEquals(expected.upperRightY, actual.upperRightY, 1f)
    }
}
