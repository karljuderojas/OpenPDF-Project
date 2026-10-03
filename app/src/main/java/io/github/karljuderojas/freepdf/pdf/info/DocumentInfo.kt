package io.github.karljuderojas.freepdf.pdf.info

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/**
 * What More > Document info shows: the PDF's own metadata plus a few facts about the file.
 * Metadata the PDF leaves blank is null, so the dialog can skip those rows.
 *
 * [pageSize] is the size every page is shown at (rotation applied), or null when pages differ.
 */
data class DocumentInfo(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    /** The app the document was made in, such as a word processor. */
    val creator: String? = null,
    /** The library or app that wrote the PDF itself. */
    val producer: String? = null,
    val created: Instant? = null,
    val modified: Instant? = null,
    val pageCount: Int,
    val pageSize: PageSize?,
    val pdfVersion: String,
    val encrypted: Boolean = false,
    val hasFormFields: Boolean = false,
    val signatureCount: Int = 0,
    val fileSizeBytes: Long,
) {
    companion object {

        /** Reads [file] with PdfBox, unlocking it with [password]. Throws if PdfBox cannot open it. */
        fun read(file: File, password: String = ""): DocumentInfo =
            PDDocument.load(file, password).use { read(it, file.length()) }

        fun read(document: PDDocument, fileSizeBytes: Long): DocumentInfo {
            val info = document.documentInformation
            return DocumentInfo(
                title = info.title.orNullIfBlank(),
                author = info.author.orNullIfBlank(),
                subject = info.subject.orNullIfBlank(),
                keywords = info.keywords.orNullIfBlank(),
                creator = info.creator.orNullIfBlank(),
                producer = info.producer.orNullIfBlank(),
                created = info.creationDate?.toInstant(),
                modified = info.modificationDate?.toInstant(),
                pageCount = document.numberOfPages,
                pageSize = commonSize(document.pages.map { shownSize(it) }),
                pdfVersion = String.format(Locale.ROOT, "%.1f", document.version),
                encrypted = document.isEncrypted,
                hasFormFields = document.documentCatalog.acroForm?.fields?.isNotEmpty() == true,
                // A damaged signature dictionary should not stop the rest from showing.
                signatureCount = runCatching { document.signatureDictionaries.size }.getOrDefault(0),
                fileSizeBytes = fileSizeBytes,
            )
        }

        /** The page as shown: its crop box, with width and height swapped when turned 90 or 270 degrees. */
        fun shownSize(page: PDPage): PageSize {
            val box = page.cropBox
            val turns = Math.floorMod(page.rotation, 360)
            return if (turns == 90 || turns == 270)PageSize(box.height, box.width) else PageSize(box.width, box.height)
        }

        /** The one size all [sizes] share, within [TOLERANCE_PT]; null when they differ or there are none. */
        fun commonSize(sizes: List<PageSize>): PageSize? {
            val first = sizes.firstOrNull() ?: return null
            return first.takeIf { sizes.all { it.matches(first.widthPt, first.heightPt) } }
        }

        /**
         * "Letter, 8.5 × 11 in" or "A4, 210 × 297 mm" for standard paper, either way up; otherwise
         * the size in millimetres when [metric], or inches.
         */
        fun sizeLabel(size: PageSize, metric: Boolean, locale: Locale = Locale.getDefault()): String {
            val paper = Paper.entries.firstOrNull { size.matches(it.widthPt, it.heightPt) || size.matches(it.heightPt, it.widthPt) }
            val inches = paper?.let { !it.metric } ?: !metric
            val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (inches) 2 else 0 }
            val dimensions = if (inches) {
                "${format.format(size.widthPt / 72.0)} × ${format.format(size.heightPt / 72.0)} in"
            } else {
                "${format.format(size.widthPt / 72.0 * 25.4)} × ${format.format(size.heightPt / 72.0 * 25.4)} mm"
            }
            return if (paper != null) "${paper.label}, $dimensions" else dimensions
        }

        /** Points of slack when comparing sizes; PDF writers round A4 to 595 × 842 and similar. */
        private const val TOLERANCE_PT = 2.5f

        private fun PageSize.matches(width: Float, height: Float) =
            abs(widthPt - width) <= TOLERANCE_PT && abs(heightPt - height) <= TOLERANCE_PT

        private fun String?.orNullIfBlank() = this?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Common paper sizes, portrait, in points. */
    private enum class Paper(val label: String, val widthPt: Float, val heightPt: Float, val metric: Boolean) {
        Letter("Letter", 612f, 792f, metric = false),
        Legal("Legal", 612f, 1008f, metric = false),
        Tabloid("Tabloid", 792f, 1224f, metric = false),
        A3("A3", 841.89f, 1190.55f, metric = true),
        A4("A4", 595.28f, 841.89f, metric = true),
        A5("A5", 419.53f, 595.28f, metric = true),
    }
}
