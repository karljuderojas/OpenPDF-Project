package io.github.karljuderojas.freepdf.pdf.scan

import android.graphics.Bitmap
import kotlin.math.max

/**
 * Finds the sheet of paper in a photo, with no outside libraries. The photo is shrunk, split into
 * light and dark with Otsu's threshold, and the biggest blob is taken as the page: its extreme
 * points are the corners. Both polarities are tried so a white page on a dark desk and a dark page
 * on a light one both work. It is a guess; the scanner lets the user drag the corners.
 */
object PageDetector {

    private const val WORK_SIDE = 256

    /** The page's corners in [bitmap], or null if nothing page-shaped stands out from its background. */
    fun detect(bitmap: Bitmap): Quad? {
        val scale = minOf(1f, WORK_SIDE.toFloat() / max(bitmap.width, bitmap.height))
        val w = max(1, (bitmap.width * scale).toInt())
        val h = max(1, (bitmap.height * scale).toInt())
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        val gray = IntArray(w * h) { i ->
            val p = pixels[i]
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        return detect(gray, w, h)
    }

    /** As [detect], for a picture already reduced to brightness values 0 to 255. */
    fun detect(gray: IntArray, w: Int, h: Int): Quad? {
        if (w < 8 || h < 8) return null
        val threshold = otsu(gray)
        val candidates = listOf(true, false).mapNotNull { light -> candidate(gray, w, h, threshold, light) }
        // The most rectangular blob wins; among equals, the bigger one.
        return candidates.maxWithOrNull(compareBy<Candidate>({ it.fill.coerceAtMost(0.95f) }, { it.quad.area }))?.quad
    }

    private class Candidate(val quad: Quad, val fill: Float)

    private fun candidate(gray: IntArray, w: Int, h: Int, threshold: Int, light: Boolean): Candidate? {
        val inside = BooleanArray(w * h) { if (light) gray[it] > threshold else gray[it] <= threshold }
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var bestArea = 0
        var tl = 0; var br = 0; var tr = 0; var bl = 0
        for (start in inside.indices) {
            if (!inside[start] || seen[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var area = 0
            var minSum = Int.MAX_VALUE; var maxSum = Int.MIN_VALUE
            var minDiff = Int.MAX_VALUE; var maxDiff = Int.MIN_VALUE
            var pTl = 0; var pBr = 0; var pTr = 0; var pBl = 0
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                area++
                val sum = x + y
                val diff = x - y
                if (sum < minSum) { minSum = sum; pTl = i }
                if (sum > maxSum) { maxSum = sum; pBr = i }
                if (diff > maxDiff) { maxDiff = diff; pTr = i }
                if (diff < minDiff) { minDiff = diff; pBl = i }
                if (x > 0 && inside[i - 1] && !seen[i - 1]) { seen[i - 1] = true; queue[tail++] = i - 1 }
                if (x < w - 1 && inside[i + 1] && !seen[i + 1]) { seen[i + 1] = true; queue[tail++] = i + 1 }
                if (y > 0 && inside[i - w] && !seen[i - w]) { seen[i - w] = true; queue[tail++] = i - w }
                if (y < h - 1 && inside[i + w] && !seen[i + w]) { seen[i + w] = true; queue[tail++] = i + w }
            }
            if (area > bestArea) {
                bestArea = area; tl = pTl; br = pBr; tr = pTr; bl = pBl
            }
        }
        if (bestArea == 0) return null
        fun corner(i: Int) = Corner((i % w + 0.5f) / w, (i / w + 0.5f) / h)
        val quad = Quad(corner(tl), corner(tr), corner(br), corner(bl))
        if (!quad.isUsable) return null
        val blobShare = bestArea.toFloat() / (w * h)
        // A blob that is nearly the whole photo is the background, not a page on it.
        if (blobShare > 0.97f) return null
        val fill = blobShare / quad.area
        if (fill < MIN_FILL) return null
        return Candidate(quad, fill)
    }

    /** How much of its four-cornered outline a page-shaped blob has to fill. */
    private const val MIN_FILL = 0.75f

    /** The brightness that best splits [gray] into dark and light (Otsu's method). */
    internal fun otsu(gray: IntArray): Int {
        val histogram = IntArray(256)
        gray.forEach { histogram[it.coerceIn(0, 255)]++ }
        val total = gray.size.toDouble()
        var sumAll = 0.0
        for (t in 0..255) sumAll += t * histogram[t].toDouble()
        var sumBackground = 0.0
        var weightBackground = 0.0
        var bestVariance = -1.0
        var best = 127
        for (t in 0..255) {
            weightBackground += histogram[t]
            if (weightBackground == 0.0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0.0) break
            sumBackground += t * histogram[t].toDouble()
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sumAll - sumBackground) / weightForeground
            val variance = weightBackground * weightForeground * (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (variance > bestVariance) {
                bestVariance = variance
                best = t
            }
        }
        return best
    }
}
