package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.karljuderojas.freepdf.pdf.redact.RedactFill
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RedactFillTest {

    /** A white page with a dark navy band across its top quarter, and a little black text on both. */
    private fun page(): Bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(0f, 0f, 200f, 50f, Paint().apply { color = Color.rgb(20, 30, 70) })
        val ink = Paint().apply { color = Color.BLACK }
        canvas.drawRect(20f, 20f, 60f, 30f, ink)
        canvas.drawRect(20f, 120f, 60f, 130f, ink)
    }

    @Test
    fun aMarkOnALightPageGetsABlackBox() {
        assertEquals(RedactFill.Black, contrastingFill(page(), Rect(0.05f, 0.55f, 0.4f, 0.7f)))
    }

    @Test
    fun aMarkOnADarkBandGetsAWhiteBox() {
        assertEquals(RedactFill.White, contrastingFill(page(), Rect(0.05f, 0.05f, 0.4f, 0.2f)))
    }

    @Test
    fun withoutAPictureOfThePageTheBoxIsBlack() {
        assertEquals(RedactFill.Black, contrastingFill(null, Rect(0.1f, 0.1f, 0.2f, 0.2f)))
    }

    @Test
    fun theBoxColourSurvivesSavedState() {
        val boxes = listOf(RedactBox(0, Rect(0.1f, 0.2f, 0.3f, 0.4f)), RedactBox(2, Rect(0.5f, 0.5f, 0.6f, 0.6f), RedactFill.White))
        assertEquals(boxes, unflattenRedactBoxes(flattenRedactBoxes(boxes)))
    }
}
