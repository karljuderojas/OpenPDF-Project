package io.github.karljuderojas.freepdf.pdf.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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

    override fun close() {
        document.close()
    }

    companion object {
        private val core by lazy { PdfiumCoreKt(Dispatchers.IO) }

        /** Opens [uri], unlocking it with [password] if it is protected. */
        suspend fun open(context: Context, uri: Uri, password: String? = null): PdfRenderer {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open $uri")
            // The PDFium document owns the descriptor from here on and closes it with the document.
            val document = if (password == null) core.newDocument(descriptor) else core.newDocument(descriptor, password)
            val sizes = (0 until document.getPageCount()).map { index ->
                val page = document.openPage(index) ?: error("Page $index could not be opened")
                page.use { PageSize(it.getPageWidthPoint().toFloat(), it.getPageHeightPoint().toFloat()) }
            }
            return PdfRenderer(document, sizes)
        }
    }
}
