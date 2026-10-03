package io.github.karljuderojas.freepdf.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageColorsTest {

    @Test
    fun nightTurnsWhitePaperDarkAndBlackTextLight() {
        val paper = apply(NightMatrix, 255, 255, 255)
        val ink = apply(NightMatrix, 0, 0, 0)
        assertTrue("white -> ${paper.toList()}", paper.all { it < 40 })
        assertTrue("black -> ${ink.toList()}", ink.all { it > 215 })
    }

    @Test
    fun nightKeepsRedInkReddish() {
        val (r, g, b) = apply(NightMatrix, 200, 0, 0)
        assertTrue("red -> ($r, $g, $b)", r > g && r > b)
    }

    @Test
    fun sepiaTurnsWhiteWarmOffWhiteAndKeepsBlackText() {
        val (r, g, b) = apply(SepiaMatrix, 255, 255, 255)
        assertTrue("white -> ($r, $g, $b)", r in 230..254 && r > g && g > b)
        assertEquals(listOf(0, 0, 0), apply(SepiaMatrix, 0, 0, 0).toList())
    }

    @Test
    fun alphaIsUntouched() {
        assertEquals(1f, NightMatrix[18])
        assertEquals(1f, SepiaMatrix[18])
    }

    /** Applies a 4x5 color matrix to an opaque pixel the way Android does, clamping to 0..255. */
    private fun apply(m: FloatArray, r: Int, g: Int, b: Int): IntArray = IntArray(3) { row ->
        val v = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * 255 + m[row * 5 + 4]
        Math.round(v).coerceIn(0, 255)
    }
}
