package io.github.karljuderojas.freepdf.pdf.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.unit.IntRect
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import java.io.Closeable
import kotlin.math.roundToInt

/** Page size in PDF points. */
data class PageSize(val widthPt: Float, val heightPt: Float) {
    val aspectRatio: Float get() = widthPt / heightPt
}

/**
 * Read-only rendering with PDFium. Fast and faithful, so every on-screen page goes through here.
 * Changes to the file go through PdfBox (see the edit, annotate and sign packages); after a save
 * the viewer reopens the document with a new [PdfRenderer].
 */
class PdfRenderer private constructor(
    private val document: PdfDocumentKt,
    val pageSizes: List<PageSize>,
) : Closeable {

    val pageCount: Int get() = pageSizes.size

    /** Renders [pageIndex] at [targetWidthPx] wide, keeping the page's aspect ratio. */
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val size = pageSizes[pageIndex]
        val width = targetWidthPx.coerceAtLeast(1)
        val height = (width / size.aspectRatio).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val page = document.openPage(pageIndex) ?: error("Page $pageIndex could not be opened")
        page.use {
            it.renderPageBitmap(bitmap, 0, 0, width, height, renderAnnot = true)
        }
        return bitmap
    }

    /**
     * Renders only [region] of [pageIndex], as it would appear if the whole page were rendered
     * [fullWidthPx] wide. Zoomed pages use this so the part on screen is sharp without
     * rendering the whole page at the zoomed size.
     */
    suspend fun renderRegion(pageIndex: Int, fullWidthPx: Int, region: IntRect): Bitmap {
        val size = pageSizes[pageIndex]
        val fullHeight = (fullWidthPx / size.aspectRatio).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(region.width.coerceAtLeast(1), region.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val page = document.openPage(pageIndex) ?: error("Page $pageIndex could not be opened")
        page.use {
            // PDFium places the page's top-left corner at (startX, startY), so a negative offset
            // shifts the wanted region into the bitmap and everything outside it is clipped.
            it.renderPageBitmap(bitmap, -region.left, -region.top, fullWidthPx, fullHeight, renderAnnot = true)
        }
        return bitmap
    }

    override fun close() {
        document.close()
    }

    companion object {
        private val core by lazy { PdfiumCoreKt(Dispatchers.IO) }

        suspend fun open(context: Context, uri: Uri): PdfRenderer {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open $uri")
            // The PDFium document owns the descriptor from here on and closes it with the document.
            val document = core.newDocument(descriptor)
            val sizes = (0 until document.getPageCount()).map { index ->
                val page = document.openPage(index) ?: error("Page $index could not be opened")
                page.use { PageSize(it.getPageWidthPoint().toFloat(), it.getPageHeightPoint().toFloat()) }
            }
            return PdfRenderer(document, sizes)
        }
    }
}
