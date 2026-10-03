package io.github.karljuderojas.freepdf.pdf.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.max

/** Straightens a photographed page into a flat, upright rectangle. */
object PerspectiveWarp {

    /** The size in pixels the page in [quad] comes out at: its own proportions, at most [maxSide] on the long side. */
    fun outputSize(quad: Quad, photoWidth: Int, photoHeight: Int, maxSide: Int): Pair<Int, Int> {
        fun dist(a: Corner, b: Corner) = hypot((a.x - b.x) * photoWidth, (a.y - b.y) * photoHeight)
        val width = max(dist(quad.topLeft, quad.topRight), dist(quad.bottomLeft, quad.bottomRight))
        val height = max(dist(quad.topLeft, quad.bottomLeft), dist(quad.topRight, quad.bottomRight))
        val scale = minOf(1f, maxSide / max(width, height).coerceAtLeast(1f))
        return max(1, (width * scale).toInt()) to max(1, (height * scale).toInt())
    }

    /**
     * Cuts [quad] out of [photo] and stretches it to fill a new opaque bitmap. A quad no matrix can
     * map (collapsed or twisted; [Quad.isUsable] is false for it) gives the whole photo instead of
     * a corner of it drawn at 1:1.
     */
    fun warp(photo: Bitmap, quad: Quad, maxSide: Int): Bitmap {
        val matrix = Matrix()
        val shape = if (matrix.setPolyToPoly(polygonOf(quad, photo), 0, targetOf(quad, photo, maxSide), 0, 4)) quad else Quad.inset(0f)
        if (shape !== quad) matrix.setPolyToPoly(polygonOf(shape, photo), 0, targetOf(shape, photo, maxSide), 0, 4)
        val (w, h) = outputSize(shape, photo.width, photo.height, maxSide)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        // Opaque, so the PDF stores it as a compact JPEG rather than lossless.
        out.setHasAlpha(false)
        return out
    }

    /** [quad]'s corners in [photo]'s pixels, as x, y pairs. */
    private fun polygonOf(quad: Quad, photo: Bitmap): FloatArray {
        val source = FloatArray(8)
        quad.corners.forEachIndexed { i, c ->
            source[i * 2] = c.x * photo.width
            source[i * 2 + 1] = c.y * photo.height
        }
        return source
    }

    /** The corners of the output bitmap for [quad], as x, y pairs. */
    private fun targetOf(quad: Quad, photo: Bitmap, maxSide: Int): FloatArray {
        val (w, h) = outputSize(quad, photo.width, photo.height, maxSide)
        return floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
    }
}
