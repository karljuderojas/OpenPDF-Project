package io.github.karljuderojas.freepdf.pdf.sign

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a photo of a signature on paper into a signature image with a see-through background, with
 * no outside libraries. Each pixel's ink amount comes from how much darker it is than the paper
 * around it, so shadows and uneven light do not leave a grey box, and the ink is then drawn in the
 * colour the user picked.
 */
object SignatureCutout {

    /** The longest side the work is done at; a signature does not need more. */
    const val MAX_SIDE = 1200

    // Darker than this share of the paper's brightness starts to count as ink, fully ink by HIGH.
    // Ruled lines, paper grain and the edge of a shadow are at most about a fifth darker than the
    // paper around them; pen ink is at least half darker.
    private const val LOW = 0.22f
    private const val HIGH = 0.42f

    /** Pixels fainter than this are not ink at all: dropped, not kept as a grey haze around the signature. */
    private const val INK_ALPHA = 96

    /** Ink pixels needed to call it a signature: between this share of the picture... */
    private const val MIN_INK = 0.002f

    /** ...and this share (more means the crop is mostly dark, not paper). */
    private const val MAX_INK = 0.45f

    /** Paper is light: a crop whose middle brightness is below this is a dark desk or a shadow, not paper. */
    private const val MIN_PAPER = 110

    /**
     * Cuts the ink out of [photo] and draws it in [argb] on a transparent bitmap trimmed to the
     * ink, with a small margin. Returns null when no signature can be told apart from the paper:
     * nothing dark enough, or so much dark that it is not paper.
     */
    fun extract(photo: Bitmap, argb: Int): Bitmap? {
        val scale = min(1f, MAX_SIDE.toFloat() / max(photo.width, photo.height))
        val w = max(1, (photo.width * scale).toInt())
        val h = max(1, (photo.height * scale).toInt())
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(photo, w, h, true) else photo
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== photo) scaled.recycle()
        val luma = IntArray(w * h) { i ->
            val p = pixels[i]
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }

        // Paper brightness near each pixel: the average over a wide window, never below most of
        // the picture's median, so a dense signature does not pull its own paper level down.
        val median = luma.sortedArray()[luma.size / 2]
        if (median < MIN_PAPER) return null
        val stride = w + 1
        val integral = LongArray(stride * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += luma[y * w + x]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row
            }
        }
        val radius = max(12, min(w, h) / 5)
        val alpha = IntArray(w * h)
        var ink = 0
        var left = w; var top = h; var right = -1; var bottom = -1
        val floor = median * 0.95f
        for (y in 0 until h) {
            val y0 = max(0, y - radius)
            val y1 = min(h, y + radius + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - radius)
                val x1 = min(w, x + radius + 1)
                val sum = integral[y1 * stride + x1] - integral[y0 * stride + x1] - integral[y1 * stride + x0] + integral[y0 * stride + x0]
                val mean = sum.toFloat() / ((x1 - x0).toLong() * (y1 - y0))
                val paper = max(mean, floor).coerceAtLeast(1f)
                val darkness = (paper - luma[y * w + x]) / paper
                val t = ((darkness - LOW) / (HIGH - LOW)).coerceIn(0f, 1f)
                val a = (t * t * (3f - 2f * t) * 255f).toInt()
                if (a > INK_ALPHA) {
                    alpha[y * w + x] = a
                    ink++
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        val share = ink.toFloat() / (w * h)
        if (right < left || share < MIN_INK || share > MAX_INK) return null

        val margin = max(4, (max(right - left, bottom - top) * 0.04f).toInt())
        val x0 = max(0, left - margin)
        val y0 = max(0, top - margin)
        val x1 = min(w - 1, right + margin)
        val y1 = min(h - 1, bottom + margin)
        val outW = x1 - x0 + 1
        val outH = y1 - y0 + 1
        val rgb = argb and 0x00FFFFFF
        val out = IntArray(outW * outH) { i ->
            val a = alpha[(y0 + i / outW) * w + x0 + i % outW]
            (a shl 24) or rgb
        }
        return Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888)
    }
}
