package io.github.karljuderojas.freepdf.pdf.edit

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.OutputStream

/**
 * Extract and Split: new PDFs made from some of a document's pages, leaving the document itself
 * as it is. A plan is a list of parts, each a list of zero-based pages in document order.
 */
object Splitting {

    /** Parts of [pagesEach] pages, the last one shorter if the pages do not divide evenly. */
    fun everyN(pageCount: Int, pagesEach: Int): List<List<Int>> {
        require(pagesEach > 0) { "Each part needs at least one page" }
        return (0 until pageCount).chunked(pagesEach)
    }

    /** Two parts: up to and including [lastOfFirst], then the rest. */
    fun intoTwo(pageCount: Int, lastOfFirst: Int): List<List<Int>> {
        require(lastOfFirst in 0 until pageCount - 1) { "Nothing would be left for the second part" }
        return listOf((0..lastOfFirst).toList(), (lastOfFirst + 1 until pageCount).toList())
    }

    /**
     * Writes a new PDF holding only [pages] of [source] to [output]. [source] is not changed. A
     * locked [source] opens with [password], and the new PDF stays locked with it.
     */
    fun writePart(source: File, pages: List<Int>, output: OutputStream, password: String = "") {
        PDDocument.load(source, password).use { document ->
            PageEditor.keepOnly(document, pages)
            PdfDocuments.keepProtection(document, password)
            document.save(output)
        }
    }

    /** "Lease.pdf" becomes "Lease (part 2).pdf" for [part] 2, counting from one. */
    fun partName(name: String, part: Int): String = "${baseName(name)} (part $part).pdf"

    /** "Lease.pdf" with pages [1, 2, 3] becomes "Lease (pages 2-4).pdf", or "(page 2)" for one. */
    fun extractName(name: String, pages: List<Int>): String {
        val which = if (pages.distinct().size == 1) "page" else "pages"
        return "${baseName(name)} ($which ${PageRanges.format(pages)}).pdf"
    }

    private fun baseName(name: String): String =
        (if (name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4) else name).ifBlank { "document" }
}
