package io.github.karljuderojas.freepdf.pdf.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint

/** A made-up photo of [page] lying turned a few degrees on a dark wooden desk, in a letter page's proportions, and where its corners really are. */
object SyntheticPhoto {

    const val WIDTH = 1200
    const val HEIGHT = 900

    /** The page's corners in the photo, as fractions of it. */
    val corners = Quad(
        Corner(377f / WIDTH, 88f / HEIGHT),
        Corner(896f / WIDTH, 138f / HEIGHT),
        Corner(824f / WIDTH, 812f / HEIGHT),
        Corner(306f / WIDTH, 758f / HEIGHT),
    )

    fun make(page: Bitmap): Bitmap {
        val photo = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(photo)
        canvas.drawColor(Color.rgb(84, 62, 46))
        // Grain, so the background is not a flat colour.
        val grain = Paint().apply { color = Color.rgb(70, 50, 36); strokeWidth = 3f }
        for (y in 0 until HEIGHT step 14) canvas.drawLine(0f, y.toFloat(), WIDTH.toFloat(), y + 6f, grain)
        val source = floatArrayOf(0f, 0f, page.width.toFloat(), 0f, page.width.toFloat(), page.height.toFloat(), 0f, page.height.toFloat())
        val target = FloatArray(8)
        corners.corners.forEachIndexed { i, c ->
            target[i * 2] = c.x * WIDTH
            target[i * 2 + 1] = c.y * HEIGHT
        }
        val matrix = Matrix().apply { setPolyToPoly(source, 0, target, 0, 4) }
        canvas.drawBitmap(page, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return photo
    }
}
