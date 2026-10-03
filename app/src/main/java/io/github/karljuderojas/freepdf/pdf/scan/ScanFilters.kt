package io.github.karljuderojas.freepdf.pdf.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/** How a scanned page looks: as photographed with a little more punch, grey, or pure black on white. */
enum class ScanFilter { Color, Grayscale, BlackWhite }

object ScanFilters {

    /** A new opaque bitmap with [filter] applied to [source]. [source] is left alone. */
    fun apply(source: Bitmap, filter: ScanFilter): Bitmap = when (filter) {
        ScanFilter.Color -> boost(source, saturation = 1f)
        ScanFilter.Grayscale -> boost(source, saturation = 0f)
        ScanFilter.BlackWhite -> blackAndWhite(source)
    }

    // A touch more contrast and brightness lifts the grey cast that paper gets in indoor light.
    private const val CONTRAST = 1.25f
    private const val LIFT = 12f

    private fun boost(source: Bitmap, saturation: Float): Bitmap {
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val offset = (-0.5f * CONTRAST + 0.5f) * 255f + LIFT
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    CONTRAST, 0f, 0f, 0f, offset,
                    0f, CONTRAST, 0f, 0f, offset,
                    0f, 0f, CONTRAST, 0f, offset,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        out.setHasAlpha(false)
        return out
    }

    /**
     * Each pixel turns black if it is clearly darker than the average of the pixels around it, else
     * white. That keeps text crisp and wipes shadows and uneven light, which one fixed cut-off cannot.
     */
    private fun blackAndWhite(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = IntArray(w * h) { i ->
            val p = pixels[i]
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        // integral[y * (w + 1) + x] is the sum of luma above and left of (x, y).
        val stride = w + 1
        val integral = LongArray(stride * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += luma[y * w + x]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row
            }
        }
        val radius = max(8, min(w, h) / 32)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        for (y in 0 until h) {
            val y0 = max(0, y - radius)
            val y1 = min(h, y + radius + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - radius)
                val x1 = min(w, x + radius + 1)
                val sum = integral[y1 * stride + x1] - integral[y0 * stride + x1] - integral[y1 * stride + x0] + integral[y0 * stride + x0]
                val mean = sum / ((x1 - x0).toLong() * (y1 - y0))
                pixels[y * w + x] = if (luma[y * w + x] * 100L < mean * 90L) black else white
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        out.setHasAlpha(false)
        return out
    }
}
