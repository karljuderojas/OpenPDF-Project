package io.github.karljuderojas.freepdf.pdf.links

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.pdfToDisplay

/** Where a link leads: out to a web address (or mail or phone link), or to a page of this PDF. */
sealed interface LinkTarget {
    data class Web(val uri: String) : LinkTarget

    /** [index] is zero-based. */
    data class Page(val index: Int) : LinkTarget
}

/** A tappable area of [page] (zero-based), as it is shown, with where it leads. */
data class PageLink(val page: Int, val box: DisplayRect, val target: LinkTarget)

/** Reading the links a PDF already has, and adding new ones. */
object PageLinks {

    /** The only kinds of address a tap will open. A PDF cannot make the app open files or run scripts. */
    private val OPENABLE_SCHEMES = setOf("http", "https", "mailto", "tel")

    /**
     * What to open for an address someone typed: "example.com" becomes "https://example.com", and
     * a bare email address a mailto link. Null when it is empty or would not be safe to open.
     */
    fun normaliseAddress(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(text)?.groupValues?.get(1)?.lowercase()
        val full = when {
            scheme != null -> text
            Regex("^[^@/]+@[^@/]+\\.[^@/]+$").matches(text) -> "mailto:$text"
            else -> "https://$text"
        }
        return full.takeIf { isOpenable(it) }
    }

    /** True for web, mail and phone addresses that have something after the scheme. */
    fun isOpenable(uri: String): Boolean {
        val scheme = uri.substringBefore(':', "").lowercase()
        if (scheme !in OPENABLE_SCHEMES) return false
        val rest = uri.substringAfter(':')
        return if (scheme == "http" || scheme == "https") rest.removePrefix("//").isNotBlank() else rest.isNotBlank()
    }

    /** Every link in [document] that goes somewhere the app can open or show, in page order. */
    fun read(document: PDDocument): List<PageLink> = document.pages.flatMapIndexed { index, page ->
        val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
        page.annotations.filterIsInstance<PDAnnotationLink>().mapNotNull { link ->
            val rect = link.rectangle ?: return@mapNotNull null
            val target = targetOf(document, link) ?: return@mapNotNull null
            val area = PdfRect(rect.lowerLeftX, rect.lowerLeftY, rect.upperRightX, rect.upperRightY)
            PageLink(index, pdfToDisplay(area, page.rotation, crop), target)
        }
    }

    /**
     * Adds a link over [box], an area of page [pageIndex] as it is shown (fractions across and
     * down). A web [LinkTarget] must pass [isOpenable]; a page target must name a page that exists.
     */
    fun add(document: PDDocument, pageIndex: Int, box: DisplayRect, target: LinkTarget) {
        when (target) {
            is LinkTarget.Web -> require(isOpenable(target.uri)) { "Cannot link to ${target.uri}" }
            is LinkTarget.Page -> require(target.index in 0 until document.numberOfPages) { "No page ${target.index}" }
        }
        val page = document.getPage(pageIndex)
        val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
        val a = displayToPdf(box.left, box.top, page.rotation, crop)
        val b = displayToPdf(box.right, box.bottom, page.rotation, crop)
        val left = minOf(a.x, b.x)
        val bottom = minOf(a.y, b.y)
        val link = PDAnnotationLink().apply {
            rectangle = PDRectangle(left, bottom, maxOf(a.x, b.x) - left, maxOf(a.y, b.y) - bottom)
            // No frame around it: the link is the words under it.
            borderStyle = PDBorderStyleDictionary().apply { width = 0f }
            action = when (target) {
                is LinkTarget.Web -> PDActionURI().apply { uri = target.uri }
                is LinkTarget.Page -> PDActionGoTo().apply {
                    destination = PDPageFitDestination().apply { this.page = document.getPage(target.index) }
                }
            }
        }
        page.annotations = page.annotations + link
    }

    private fun targetOf(document: PDDocument, link: PDAnnotationLink): LinkTarget? {
        when (val action = link.action) {
            is PDActionURI -> return action.uri?.takeIf(::isOpenable)?.let { LinkTarget.Web(it) }
            is PDActionGoTo -> return pageOf(document, action.destination)?.let { LinkTarget.Page(it) }
            else -> Unit
        }
        return pageOf(document, link.destination)?.let { LinkTarget.Page(it) }
    }

    private fun pageOf(document: PDDocument, destination: PDDestination?): Int? {
        val resolved = when (destination) {
            is PDNamedDestination -> document.documentCatalog.findNamedDestinationPage(destination)
            is PDPageDestination -> destination
            else -> null
        }
        return resolved?.retrievePageNumber()?.takeIf { it in 0 until document.numberOfPages }
    }
}
