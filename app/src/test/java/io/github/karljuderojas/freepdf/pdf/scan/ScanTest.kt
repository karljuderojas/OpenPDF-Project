package io.github.karljuderojas.freepdf.pdf.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanTest {

    private fun samplePage(): Bitmap =
        javaClass.classLoader!!.getResourceAsStream("sample/page-1.png").use { BitmapFactory.decodeStream(it) }!!

    @Test
    fun quadsRoundTripThroughText() {
        val quad = Quad.inset(0.1f)
        assertEquals(quad, Quad.decode(quad.encode()))
        val page = ScanPage(42L, "/cache/scans/page-1.jpg", quad)
        assertEquals(page, ScanPage.decode(page.encode()))
        assertNull(Quad.decode("nonsense"))
        assertNull(ScanPage.decode("1|file"))
    }

    @Test
    fun aTwistedOrTinyQuadIsNotUsable() {
        assertTrue(Quad.inset(0.05f).isUsable)
        val bowTie = Quad(Corner(0.1f, 0.1f), Corner(0.9f, 0.9f), Corner(0.9f, 0.1f), Corner(0.1f, 0.9f))
        assertFalse(bowTie.isUsable)
        assertFalse(Quad.inset(0.49f).isUsable)
    }

    @Test
    fun movingACornerKeepsItInsideThePhoto() {
        val moved = Quad.inset(0.1f).withCorner(2, Corner(1.4f, -0.3f))
        assertEquals(Corner(1f, 0f), moved.bottomRight)
        assertEquals(Corner(0.1f, 0.1f), moved.topLeft)
    }

    @Test
    fun findsAPageSkewedOnADesk() {
        val photo = SyntheticPhoto.make(samplePage())
        val found = PageDetector.detect(photo)
        assertNotNull("no page found", found)
        val expected = SyntheticPhoto.corners.corners
        found!!.corners.forEachIndexed { i, c ->
            assertTrue("corner $i at $c, expected ${expected[i]}", abs(c.x - expected[i].x) < 0.03f && abs(c.y - expected[i].y) < 0.03f)
        }
    }

    @Test
    fun findsNothingInAFlatPicture() {
        val flat = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 120, 120)) }
        assertNull(PageDetector.detect(flat))
    }

    @Test
    fun straightensThePageToItsOwnProportions() {
        val page = samplePage()
        val photo = SyntheticPhoto.make(page)
        val flat = PerspectiveWarp.warp(photo, SyntheticPhoto.corners, 1600)
        assertFalse(flat.hasAlpha())
        assertTrue(maxOf(flat.width, flat.height) <= 1600)
        // The sample is a letter page and the photo keeps its proportions, give or take the slight skew.
        assertEquals(page.width.toFloat() / page.height, flat.width.toFloat() / flat.height, 0.08f)
        // The centre of the straightened page is page, not desk: light, not brown.
        val centre = flat.getPixel(flat.width / 2, flat.height / 2)
        assertTrue(Color.red(centre) > 150 && Color.green(centre) > 150)
    }

    @Test
    fun outputNeverExceedsTheMaximumSide() {
        val (w, h) = PerspectiveWarp.outputSize(Quad.inset(0f), 4000, 3000, 2000)
        assertEquals(2000, w)
        assertEquals(1500, h)
        val (sw, sh) = PerspectiveWarp.outputSize(Quad.inset(0f), 400, 300, 2000)
        assertEquals(400 to 300, sw to sh)
    }

    @Test
    fun blackAndWhiteHasOnlyThoseTwoColoursAndKeepsTheText() {
        val flat = PerspectiveWarp.warp(SyntheticPhoto.make(samplePage()), SyntheticPhoto.corners, 900)
        val bw = ScanFilters.apply(flat, ScanFilter.BlackWhite)
        val pixels = IntArray(bw.width * bw.height).also { bw.getPixels(it, 0, bw.width, 0, 0, bw.width, bw.height) }
        assertTrue(pixels.all { it == Color.BLACK || it == Color.WHITE })
        val black = pixels.count { it == Color.BLACK }.toFloat() / pixels.size
        assertTrue("black share $black", black in 0.005f..0.35f)
    }

    @Test
    fun grayscaleHasNoColour() {
        val photo = SyntheticPhoto.make(samplePage())
        val gray = ScanFilters.apply(photo, ScanFilter.Grayscale)
        for (x in 0 until gray.width step 97) for (y in 0 until gray.height step 89) {
            val p = gray.getPixel(x, y)
            assertTrue(abs(Color.red(p) - Color.green(p)) <= 1 && abs(Color.green(p) - Color.blue(p)) <= 1)
        }
    }

    @Test
    fun otsuSplitsTwoBrightnessGroups() {
        val values = IntArray(100) { if (it < 50) 40 else 200 }
        val t = PageDetector.otsu(values)
        assertTrue("threshold $t", t in 40..199)
    }
}
