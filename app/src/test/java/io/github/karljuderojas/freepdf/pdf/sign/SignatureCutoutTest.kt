package io.github.karljuderojas.freepdf.pdf.sign

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignatureCutoutTest {

    @Test
    fun keepsTheInkInTheChosenColourAndDropsThePaperAndTheShadow() {
        val ink = Color.rgb(17, 17, 17)
        val cut = SignatureCutout.extract(SignaturePhotos.make(), ink)
        assertNotNull(cut)
        cut!!
        // Trimmed to the signature: smaller than the 900 x 400 photo, but all of it kept.
        assertTrue("width ${cut.width}", cut.width in 480..620)
        assertTrue("height ${cut.height}", cut.height in 150..280)
        // The corners are paper (and, on the left, shadow): see-through.
        listOf(0 to 0, cut.width - 1 to 0, 0 to cut.height - 1, cut.width - 1 to cut.height - 1).forEach { (x, y) ->
            assertEquals("corner $x,$y", 0, Color.alpha(cut.getPixel(x, y)))
        }
        // Something solid is ink in the colour asked for, whatever colour the pen really was.
        var solid = 0
        for (y in 0 until cut.height) for (x in 0 until cut.width) {
            val p = cut.getPixel(x, y)
            if (Color.alpha(p) > 200) {
                solid++
                // Partly see-through pixels are stored premultiplied, so allow for rounding.
                assertTrue("pixel $x,$y was ${Integer.toHexString(p)}", listOf(Color.red(p), Color.green(p), Color.blue(p)).all { kotlin.math.abs(it - 17) <= 3 })
            }
        }
        assertTrue("solid ink pixels $solid", solid > 800)
    }

    @Test
    fun ruledLinesAndTheMarginLineAreNotKeptAsGreyInk() {
        val cut = SignatureCutout.extract(SignaturePhotos.ruled(), Color.BLACK)
        assertNotNull(cut)
        cut!!
        // Trimmed to the signature, not to the lines that run across the whole sheet.
        assertTrue("width ${cut.width}", cut.width in 480..620)
        assertTrue("height ${cut.height}", cut.height in 150..280)
        // The lines cross the margin columns of the cut-out, where there is no ink: nothing there.
        for (y in 0 until cut.height) {
            assertEquals("left edge row $y", 0, Color.alpha(cut.getPixel(0, y)))
            assertEquals("right edge row $y", 0, Color.alpha(cut.getPixel(cut.width - 1, y)))
        }
        // Whatever is kept is ink or the soft edge of a stroke, not a faint haze on its own.
        for (y in 0 until cut.height) for (x in 0 until cut.width) {
            val a = Color.alpha(cut.getPixel(x, y))
            if (a == 0 || a > 90) continue
            var touchesInk = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until cut.width && ny in 0 until cut.height && Color.alpha(cut.getPixel(nx, ny)) > 96) touchesInk = true
            }
            assertTrue("pixel $x,$y alpha $a stands alone", touchesInk)
        }
    }

    @Test
    fun aBlankPageHasNoSignature() {
        val blank = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(230, 230, 225)) }
        assertNull(SignatureCutout.extract(blank, Color.BLACK))
    }

    @Test
    fun aMostlyDarkPictureIsNotPaper() {
        val dark = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 30, 30)) }
        val canvas = Canvas(dark)
        canvas.drawRect(0f, 0f, 600f, 40f, Paint().apply { color = Color.rgb(230, 230, 225) })
        assertNull(SignatureCutout.extract(dark, Color.BLACK))
    }

    @Test
    fun hugePhotosAreWorkedAtASensibleSize() {
        val cut = SignatureCutout.extract(Bitmap.createScaledBitmap(SignaturePhotos.make(), 3600, 1600, true), Color.BLACK)
        assertNotNull(cut)
        assertTrue(maxOf(cut!!.width, cut.height) <= SignatureCutout.MAX_SIDE)
    }
}
