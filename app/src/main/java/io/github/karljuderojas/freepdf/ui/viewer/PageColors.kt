package io.github.karljuderojas.freepdf.ui.viewer

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.settings.PageColors

/*
 * Reading modes are a color matrix over the rendered page, so the PDF is never changed.
 * Matrices use the Android/Compose 4x5 row-major layout: each output channel is
 * r*m0 + g*m1 + b*m2 + a*m3 + m4, with channels and the offset m4 in 0..255.
 */

/** Night: white paper becomes this dark grey and black text this soft white. */
private const val NIGHT_PAPER = 30f
private const val NIGHT_INK = 230f

/**
 * A 180-degree hue rotation (the standard luminance-preserving one). Inverting alone turns red
 * ink cyan; rotating the hue afterwards brings it back to roughly red, only lighter.
 */
private val HUE_180 = floatArrayOf(
    -0.574f, 1.430f, 0.144f,
    0.426f, 0.430f, 0.144f,
    0.426f, 1.430f, -0.856f,
)

/** The classic sepia matrix. */
private val SEPIA = floatArrayOf(
    0.393f, 0.769f, 0.189f,
    0.349f, 0.686f, 0.168f,
    0.272f, 0.534f, 0.131f,
)

/** How much of [SEPIA] to use; the rest keeps the original color, so text stays crisp. */
private const val SEPIA_AMOUNT = 0.8f

/** Sepia paper: white maps exactly to this warm cream. */
private val SEPIA_PAPER = floatArrayOf(244f, 236f, 216f)

/** Invert, then rotate the hue, then squeeze into [NIGHT_PAPER]..[NIGHT_INK]. */
internal val NightMatrix: FloatArray = FloatArray(20).also { m ->
    val range = (NIGHT_INK - NIGHT_PAPER) / 255f
    for (row in 0..2) {
        for (col in 0..2) m[row * 5 + col] = -HUE_180[row * 3 + col] * range
        // Each HUE_180 row sums to 1, so black lands on NIGHT_INK and white on NIGHT_PAPER.
        m[row * 5 + 4] = NIGHT_INK
    }
    m[18] = 1f
}

/** Mostly sepia, with each row scaled so white lands on [SEPIA_PAPER] and black stays black. */
internal val SepiaMatrix: FloatArray = FloatArray(20).also { m ->
    for (row in 0..2) {
        val mixed = FloatArray(3) { col ->
            SEPIA_AMOUNT * SEPIA[row * 3 + col] + (if (col == row) 1f - SEPIA_AMOUNT else 0f)
        }
        val scale = SEPIA_PAPER[row] / 255f / mixed.sum()
        for (col in 0..2) m[row * 5 + col] = mixed[col] * scale
    }
    m[18] = 1f
}

/** The matrix for this mode, or null when pages show as they are. */
internal val PageColors.matrix: FloatArray?
    get() = when (this) {
        PageColors.Normal -> null
        PageColors.Night -> NightMatrix
        PageColors.Sepia -> SepiaMatrix
    }

/** The page's background before its image loads, matching what the matrix does to white. */
internal val PageColors.paper: Color
    get() = when (this) {
        PageColors.Normal -> Color.White
        PageColors.Night -> Color(0xFF1E1E1E)
        PageColors.Sepia -> Color(0xFFF4ECD8)
    }

@get:StringRes
val PageColors.label: Int
    get() = when (this) {
        PageColors.Normal -> R.string.page_colors_normal
        PageColors.Night -> R.string.page_colors_night
        PageColors.Sepia -> R.string.page_colors_sepia
    }
