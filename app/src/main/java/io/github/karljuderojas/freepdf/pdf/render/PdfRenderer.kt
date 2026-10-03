package io.github.karljuderojas.freepdf.pdf.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import io.legere.pdfiumandroid.api.Bookmark
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

    /** The PDF's outline, flattened in reading order. Entries that point nowhere are dropped. */
    suspend fun outline(): List<OutlineItem> {
        fun flatten(items: List<Bookmark>, depth: Int): List<OutlineItem> = items.flatMap { item ->
            listOf(OutlineItem(item.title.orEmpty().trim(), item.pageIdx.toInt(), depth)) + flatten(item.children.orEmpty(), depth + 1)
        }
        return flatten(document.getTableOfContents(), 0).filter { it.title.isNotEmpty() && it.page in 0 until pageCount }
    }

    /** Each place [query] occurs on [pageIndex], as the boxes covering its characters. */
    suspend fun find(pageIndex: Int, query: String): List<List<PageBox>> {
        val page = document.openPage(pageIndex) ?: return emptyList()
        return page.use {
            page.openTextPage().use { text ->
                val count = text.textPageCountChars()
                val ranges = if (count > 0) TextSearch.find(text.textPageGetText(0, count).orEmpty(), query) else emptyList()
                // PDFium maps page space to a bitmap of any size; a 1000-pixel-wide one gives fractions.
                val width = 1000
                val height = (width / pageSizes[pageIndex].aspectRatio).roundToInt().coerceAtLeast(1)
                ranges.map { range ->
                    (0 until text.textPageCountRects(range.first, range.last - range.first + 1)).mapNotNull { i ->
                        text.textPageGetRect(i)?.let { rect ->
                            val device = page.mapRectToDevice(0, 0, width, height, 0, rect)
                            PageBox(
                                left = minOf(device.left, device.right) / width.toFloat(),
                                top = minOf(device.top, device.bottom) / height.toFloat(),
                                right = maxOf(device.left, device.right) / width.toFloat(),
                                bottom = maxOf(device.top, device.bottom) / height.toFloat(),
                            )
                        }
                    }
                }
            }
        }
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
