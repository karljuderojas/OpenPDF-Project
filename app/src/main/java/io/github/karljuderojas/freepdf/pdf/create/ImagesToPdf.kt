package io.github.karljuderojas.freepdf.pdf.create

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory

/** How big each page is when pictures are turned into a PDF. */
enum class PageFit {
    /** Each page takes its picture's shape, so nothing is cropped or padded. */
    Picture,

    /** Every page is A4, turned to suit the picture, with the picture centred inside a margin. */
    A4,
}

/** A picture's place on its page, in PDF points. */
data class ImagePlacement(val pageWidth: Float, val pageHeight: Float, val x: Float, val y: Float, val width: Float, val height: Float)

/** Builds PDFs out of pictures: photos from the gallery, or pages the scanner captured. */
object ImagesToPdf {

    /** The long side of a page that takes its picture's shape: the same as A4's, about 29.7 cm. */
    const val LONG_SIDE = 842f

    private const val A4_SHORT = 595f
    private const val A4_LONG = 842f
    private const val MARGIN = 24f
    private const val JPEG_QUALITY = 0.85f

    /** Where a [pictureWidth] by [pictureHeight] picture goes on its page under [fit]. */
    fun place(pictureWidth: Int, pictureHeight: Int, fit: PageFit): ImagePlacement {
        val w = pictureWidth.coerceAtLeast(1).toFloat()
        val h = pictureHeight.coerceAtLeast(1).toFloat()
        return when (fit) {
            PageFit.Picture -> {
                val scale = LONG_SIDE / maxOf(w, h)
                val pw = (w * scale).coerceAtLeast(1f)
                val ph = (h * scale).coerceAtLeast(1f)
                ImagePlacement(pw, ph, 0f, 0f, pw, ph)
            }
            PageFit.A4 -> {
                val landscape = w > h
                val pageW = if (landscape) A4_LONG else A4_SHORT
                val pageH = if (landscape) A4_SHORT else A4_LONG
                val scale = minOf((pageW - 2 * MARGIN) / w, (pageH - 2 * MARGIN) / h)
                val pw = w * scale
                val ph = h * scale
                ImagePlacement(pageW, pageH, (pageW - pw) / 2f, (pageH - ph) / 2f, pw, ph)
            }
        }
    }

    /** Adds [picture] to the end of [document] as a page of its own. */
    fun addPage(document: PDDocument, picture: Bitmap, fit: PageFit) {
        val placement = place(picture.width, picture.height, fit)
        val page = PDPage(PDRectangle(placement.pageWidth, placement.pageHeight))
        document.addPage(page)
        // Pictures with see-through parts are kept lossless so the white page shows through;
        // photos are stored as JPEG to keep the PDF small.
        val xObject = if (picture.hasAlpha()) {
            LosslessFactory.createFromImage(document, picture)
        } else {
            JPEGFactory.createFromImage(document, picture, JPEG_QUALITY)
        }
        PDPageContentStream(document, page).use { stream ->
            stream.drawImage(xObject, placement.x, placement.y, placement.width, placement.height)
        }
    }

    /**
     * Builds a PDF with one page per picture, in order. [load] fetches the picture at an index and
     * is called once for each, so only one full-size picture is in memory at a time; it is
     * recycled as soon as its page is made. [onProgress] hears how many pages are done.
     */
    fun build(count: Int, fit: PageFit, onProgress: (Int) -> Unit = {}, load: (Int) -> Bitmap): PDDocument {
        val document = PDDocument()
        try {
            for (i in 0 until count) {
                val picture = load(i)
                try {
                    addPage(document, picture, fit)
                } finally {
                    picture.recycle()
                }
                onProgress(i + 1)
            }
        } catch (e: Throwable) {
            document.close()
            throw e
        }
        return document
    }
}
