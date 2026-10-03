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
                assertEquals(Color.rgb(17, 17, 17), Color.rgb(Color.red(p), Color.green(p), Color.blue(p)))
            }
        }
        assertTrue("solid ink pixels $solid", solid > 800)
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
