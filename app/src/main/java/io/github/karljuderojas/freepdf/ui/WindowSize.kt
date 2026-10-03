package io.github.karljuderojas.freepdf.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The size of the window in dp, and what the layouts do with it. Follows Material's window size
 * classes (compact under 600 dp wide, medium to 840, expanded beyond) without the extra library.
 */
data class WindowSize(val widthDp: Int, val heightDp: Int) {

    val landscape: Boolean get() = widthDp > heightDp

    /** A tablet or a foldable opened out, not a phone turned on its side: both sides are at least 600 dp. */
    val tablet: Boolean get() = minOf(widthDp, heightDp) >= TABLET_DP

    /** Wide enough that the tabs go down the side instead of along the bottom. */
    val navigationRail: Boolean get() = widthDp >= TABLET_DP

    /** The viewer shows its pages, contents and comments beside the document: room to spare. */
    val sidePanel: Boolean get() = tablet && widthDp >= EXPANDED_DP

    /** Two pages side by side while reading: a tablet held wide. */
    val twoPages: Boolean get() = tablet && landscape

    private companion object {
        const val TABLET_DP = 600
        const val EXPANDED_DP = 840
    }
}

@Composable
fun rememberWindowSize(): WindowSize {
    val configuration = LocalConfiguration.current
    return WindowSize(configuration.screenWidthDp, configuration.screenHeightDp)
}
