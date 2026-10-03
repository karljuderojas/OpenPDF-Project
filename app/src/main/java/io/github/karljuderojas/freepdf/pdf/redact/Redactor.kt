package io.github.karljuderojas.freepdf.pdf.redact

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import io.github.karljuderojas.freepdf.pdf.PdfRect
import java.util.Collections
import java.util.IdentityHashMap

/**
 * True redaction: the marked areas are permanently emptied of text, pictures and line art, then
 * painted black. Nothing is merely covered, so copying, searching or extracting text from the
 * saved file finds nothing under the black boxes.
 *
 * Per page, [PageRedactor] rewrites the content. Here, around it:
 * - notes, highlights, form fields and other annotations that touch an area are removed along
 *   with their text and appearance;
 * - the page's thumbnail and private application data go, since they may show the old page;
 * - words that were removed are also taken out of the document's title, author, subject and
 *   keywords, its XMP metadata, its bookmarks and the alternate text of a tagged PDF.
 *
 * Call it on a copy of the document and save with PdfBox's own save, which writes the file out
 * afresh; an incremental save would keep the old content in the file.
 */
object Redactor {

    /** What was removed, for telling the user. */
    data class Result(
        val pages: Int,
        val textCharacters: Int,
        val pictures: Int,
        val shapes: Int,
        val forms: Int,
        val annotations: Int,
    ) {
        /** True when the marked areas held nothing to remove (blank space), though they are still painted black. */
        val foundNothing: Boolean get() = textCharacters + pictures + shapes + forms + annotations == 0
    }

    /**
     * Redacts [areas], given per zero-based page index in the page's own PDF space (unrotated,
     * origin bottom-left; see PdfGeometry).
     */
    fun redact(document: PDDocument, areas: Map<Int, List<PdfRect>>): Result {
        val marked = areas.filterValues { it.isNotEmpty() }.toSortedMap()
        require(marked.isNotEmpty()) { "Nothing is marked" }
        val fragments = LinkedHashSet<String>()
        var characters = 0
        var pictures = 0
        var shapes = 0
        var forms = 0
        var annotations = 0

        for ((index, rects) in marked) {
            val page = document.getPage(index)
            val redactor = PageRedactor(document, page, rects)
            redactor.run()
            characters += redactor.stats.glyphs
            pictures += redactor.stats.pictures
            shapes += redactor.stats.shapes
            forms += redactor.stats.forms
            fragments += redactor.fragments

            annotations += removeAnnotations(page.annotations, rects).let { (kept, removed) ->
                if (removed > 0) page.annotations = kept
                removed
            }
            page.cosObject.removeItem(COSName.THUMB)
            page.cosObject.removeItem(COSName.getPDFName("PieceInfo"))

            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.setNonStrokingColor(0f, 0f, 0f)
                rects.forEach { stream.addRect(it.left, it.bottom, it.width, it.height) }
                stream.fill()
            }
        }

        // A form's XFA copy holds the filled-in values a second time.
        document.documentCatalog.acroForm?.let { if (annotations > 0) it.xfa = null }
        scrubMetadata(document, fragments.filter { it.length >= MIN_FRAGMENT })
        return Result(marked.size, characters, pictures, shapes, forms, annotations)
    }

    /** "Lease.pdf" becomes "Lease (redacted).pdf". */
    fun suggestedName(original: String): String {
        val base = if (original.endsWith(".pdf", ignoreCase = true)) original.dropLast(4) else original
        return "$base (redacted).pdf"
    }

    private fun removeAnnotations(all: List<PDAnnotation>, areas: List<PdfRect>): Pair<List<PDAnnotation>, Int> {
        val kept = ArrayList<PDAnnotation>()
        var removed = 0
        for (annotation in all) {
            val box = annotation.rectangle
            val hit = box != null && areas.any {
                box.lowerLeftX < it.right && box.upperRightX > it.left && box.lowerLeftY < it.top && box.upperRightY > it.bottom
            }
            if (!hit) {
                kept += annotation
                continue
            }
            removed++
            // Anything still pointing at the annotation (a form's field list) must not keep its text.
            var node: COSDictionary? = annotation.cosObject
            var depth = 0
            while (node != null && depth++ < MAX_PARENTS) {
                LEFTOVERS.forEach { node.removeItem(it) }
                node = node.getDictionaryObject(COSName.PARENT) as? COSDictionary
            }
        }
        return kept to removed
    }

    private fun scrubMetadata(document: PDDocument, fragments: List<String>) {
        if (fragments.isEmpty()) return
        fun mentions(text: String?) = text != null && fragments.any { text.lowercase().contains(it) }

        document.documentInformation?.let { info ->
            if (mentions(info.title)) info.title = null
            if (mentions(info.author)) info.author = null
            if (mentions(info.subject)) info.subject = null
            if (mentions(info.keywords)) info.keywords = null
            if (mentions(info.creator)) info.creator = null
            if (mentions(info.producer)) info.producer = null
        }

        val catalog = document.documentCatalog
        catalog.metadata?.let { xmp ->
            val text = runCatching { String(xmp.toByteArray(), Charsets.UTF_8) }.getOrDefault("")
            if (mentions(text)) catalog.metadata = null
        }

        catalog.documentOutline?.let { scrubOutline(it, ::mentions) }

        val seen = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
        catalog.cosObject.getDictionaryObject(COSName.getPDFName("StructTreeRoot"))?.let { scrubStructure(it, seen, ::mentions) }
    }

    private fun scrubOutline(node: PDOutlineNode, mentions: (String?) -> Boolean) {
        for (item in node.children()) {
            if (mentions(item.title)) item.title = REMOVED
            scrubOutline(item, mentions)
        }
    }

    /** Removes alternate text and replacement text of a tagged PDF's structure tree that repeat removed words. */
    private fun scrubStructure(node: COSBase, seen: MutableSet<COSBase>, mentions: (String?) -> Boolean) {
        if (!seen.add(node)) return
        when (node) {
            is COSDictionary -> {
                STRUCTURE_TEXT.forEach { key ->
                    val value = node.getDictionaryObject(key)
                    if (value is COSString && mentions(value.string)) node.removeItem(key)
                }
                // Children only; /P (the parent) and /Pg (the page) lead back up and out of the tree.
                node.getDictionaryObject(COSName.K)?.let { scrubStructure(it, seen, mentions) }
            }
            is COSArray -> for (i in 0 until node.size()) node.getObject(i)?.let { scrubStructure(it, seen, mentions) }
            else -> Unit
        }
    }

    // Words shorter than this would match too much ("a", "of") to be worth hunting for in metadata.
    private const val MIN_FRAGMENT = 3
    private const val MAX_PARENTS = 16
    private const val REMOVED = "[removed]"

    private val LEFTOVERS = listOf(
        COSName.AP, COSName.CONTENTS, COSName.V, COSName.getPDFName("DV"), COSName.getPDFName("RC"), COSName.getPDFName("DS"),
    )
    private val STRUCTURE_TEXT = listOf(COSName.getPDFName("Alt"), COSName.getPDFName("ActualText"), COSName.getPDFName("E"))
}
