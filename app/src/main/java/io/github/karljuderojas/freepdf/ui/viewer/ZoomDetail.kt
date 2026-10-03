package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Renders [region] of page [index] as if the whole page were [fullWidthPx] wide. */
typealias LoadRegion = suspend (index: Int, fullWidthPx: Int, region: IntRect) -> Bitmap?

/**
 * Keeps zoomed pages sharp. The page list zooms by scaling pages already rendered at screen
 * width, which looks soft past 1x. Once a pinch, pan or scroll settles, each page on screen
 * renders just its visible part at the zoomed size and draws it over the soft page. However far
 * in the user zooms, that is about one screenful of pixels, so memory stays flat on small phones.
 */
@Stable
internal class ZoomDetail {
    var loadRegion: LoadRegion = { _, _, _ -> null }

    /** The page list's box, whose bounds are what the user can see. */
    var viewport: LayoutCoordinates? = null

    /** Ticks each time the view comes to rest at a new zoom or position. */
    var settled by mutableIntStateOf(0)
}

/** Where the page is laid out; read when the view settles, so not state. */
private class PageBox {
    var coordinates: LayoutCoordinates? = null
}

/** A sharp rendering of part of a page, [scale] screen pixels per page layout pixel. */
private class Tile(val image: ImageBitmap, val region: IntRect, val scale: Float)

/** Draws the sharp part of page [index] over its soft rendering; see [ZoomDetail]. */
@Composable
internal fun BoxScope.ZoomDetailLayer(index: Int, revision: Int, detail: ZoomDetail) {
    val page = remember { PageBox() }
    var tile by remember(revision) { mutableStateOf<Tile?>(null) }

    LaunchedEffect(detail.settled, revision) {
        // Let a page that has just scrolled in be laid out before measuring it.
        withFrameNanos { }
        val request = visibleRegion(detail.viewport, page.coordinates)
        if (request == null) {
            tile = null
            return@LaunchedEffect
        }
        val (fullWidth, region, scale) = request
        tile = try {
            detail.loadRegion(index, fullWidth, region)?.let { Tile(it.asImageBitmap(), region, scale) }
        } catch (e: OutOfMemoryError) {
            // The soft page is still there; better than crashing on a phone short of memory.
            null
        }
    }

    Spacer(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { page.coordinates = it }
            .drawBehind {
                val current = tile ?: return@drawBehind
                // Back into layout pixels; the list's zoom then maps each one to a screen pixel.
                translate(current.region.left / current.scale, current.region.top / current.scale) {
                    scale(1 / current.scale, pivot = Offset.Zero) { drawImage(current.image) }
                }
            },
    )
}

private data class RegionRequest(val fullWidthPx: Int, val region: IntRect, val scale: Float)

/**
 * The on-screen part of [page] in zoomed pixels, or null when the page is off screen or not
 * zoomed enough for a sharper rendering to show.
 */
private fun visibleRegion(viewport: LayoutCoordinates?, page: LayoutCoordinates?): RegionRequest? {
    if (viewport == null || page == null || !viewport.isAttached || !page.isAttached) return null
    val onScreen = viewport.localBoundingBoxOf(page, clipBounds = false)
    val pageWidth = page.size.width
    if (pageWidth == 0) return null
    val scale = onScreen.width / pageWidth
    if (scale < MIN_SCALE) return null
    val visible = onScreen.intersect(Rect(Offset.Zero, viewport.size.toSize()))
    if (visible.width <= 0f || visible.height <= 0f) return null
    // Measured from the page's own corner, in zoomed pixels.
    val left = (visible.left - onScreen.left).coerceAtLeast(0f)
    val top = (visible.top - onScreen.top).coerceAtLeast(0f)
    val region = IntRect(
        floor(left).toInt(),
        floor(top).toInt(),
        ceil(left + visible.width).toInt(),
        ceil(top + visible.height).toInt(),
    )
    return RegionRequest((pageWidth * scale).roundToInt(), region, scale)
}

/** Below this zoom the screen-width rendering is already sharp enough. */
private const val MIN_SCALE = 1.15f
