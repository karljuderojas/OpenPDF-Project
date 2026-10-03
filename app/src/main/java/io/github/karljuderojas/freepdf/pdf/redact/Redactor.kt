package io.github.karljuderojas.freepdf.pdf.redact

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup
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
        val replaced = identitySet()
        val removedAnnotations = identitySet()
        var unreadable = false
        var characters = 0
        var pictures = 0
        var shapes = 0
        var forms = 0

        for ((index, rects) in marked) {
            val page = document.getPage(index)
            val redactor = PageRedactor(document, page, rects)
            redactor.run()
            characters += redactor.stats.glyphs
            pictures += redactor.stats.pictures
            shapes += redactor.stats.shapes
            forms += redactor.stats.forms
            fragments += redactor.fragments
            replaced += redactor.replacedObjects
            unreadable = unreadable || redactor.unreadableText

            val (kept, removed) = page.annotations.partition { annotation ->
                val box = annotation.rectangle
                box == null || rects.none {
                    box.lowerLeftX < it.right && box.upperRightX > it.left && box.lowerLeftY < it.top && box.upperRightY > it.bottom
                }
            }
            if (removed.isNotEmpty()) page.annotations = kept
            removed.forEach { removedAnnotations += it.cosObject }
            page.cosObject.removeItem(COSName.THUMB)
            page.cosObject.removeItem(COSName.getPDFName("PieceInfo"))

            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.setNonStrokingColor(0f, 0f, 0f)
                rects.forEach { stream.addRect(it.left, it.bottom, it.width, it.height) }
                stream.fill()
            }
        }

        val annotations = removedAnnotations.size
        if (removedAnnotations.isNotEmpty()) removeAnnotationLeftovers(document, removedAnnotations)
        if (replaced.isNotEmpty()) dropUnusedOriginals(document, replaced)
        scrubMetadata(document, fragments.filter { it.replace(SPACES, "").length >= MIN_FRAGMENT }, unreadable)
        return Result(marked.size, characters, pictures, shapes, forms, annotations)
    }

    /** "Lease.pdf" becomes "Lease (redacted).pdf". */
    fun suggestedName(original: String): String {
        val base = if (original.endsWith(".pdf", ignoreCase = true)) original.dropLast(4) else original
        return "$base (redacted).pdf"
    }

    /**
     * Makes sure nothing of the [removed] annotations survives elsewhere: replies to them and their
     * popups go too, a form field they belonged to is taken out of the form, and whatever still
     * points at them (a structure tree, a parent field) finds them empty of text, values and links.
     */
    private fun removeAnnotationLeftovers(document: PDDocument, removed: MutableSet<COSBase>) {
        // Replies (/IRT) and popups (/Parent) of a removed annotation, on any page, go with it.
        var grew = true
        while (grew) {
            grew = false
            for (page in document.pages) {
                val annotations = page.annotations
                val (kept, gone) = annotations.partition { annotation ->
                    val dictionary = annotation.cosObject
                    val replyTo = dictionary.getDictionaryObject(COSName.getPDFName("IRT"))
                    val parent = dictionary.getDictionaryObject(COSName.PARENT)
                    dictionary !in removed && replyTo !in removed && (annotation !is PDAnnotationPopup || parent !in removed)
                }
                if (gone.isEmpty()) continue
                page.annotations = kept
                gone.forEach { if (removed.add(it.cosObject)) grew = true }
            }
        }

        for (annotation in removed) {
            var node = annotation as? COSDictionary
            var depth = 0
            // The annotation loses everything that could show what it said; the fields above it lose their value.
            while (node != null && depth < MAX_PARENTS) {
                (if (depth == 0) ANNOTATION_TEXT else FIELD_VALUES).forEach { node.removeItem(it) }
                node = node.getDictionaryObject(COSName.PARENT) as? COSDictionary
                depth++
            }
        }

        val form = document.documentCatalog.acroForm ?: return
        // A form's XFA copy holds the filled-in values a second time.
        form.xfa = null
        val dictionary = form.cosObject
        (dictionary.getDictionaryObject(COSName.FIELDS) as? COSArray)?.let { pruneFields(it, removed) }
        (dictionary.getDictionaryObject(COSName.CO) as? COSArray)?.let { order -> prune(order) { it in removed } }
    }

    /** Takes [removed] widgets out of a field list, and any field left with no widgets at all. */
    private fun pruneFields(fields: COSArray, removed: Set<COSBase>) {
        prune(fields) { field ->
            if (field in removed) return@prune true
            val kids = (field as? COSDictionary)?.getDictionaryObject(COSName.KIDS) as? COSArray ?: return@prune false
            val before = kids.size()
            pruneFields(kids, removed)
            before > 0 && kids.size() == 0
        }
    }

    private fun prune(array: COSArray, drop: (COSBase?) -> Boolean) {
        for (i in array.size() - 1 downTo 0) if (drop(array.getObject(i))) array.remove(i)
    }

    /**
     * The pictures and forms in [replaced] were swapped for rewritten copies on the redacted pages,
     * but a page that inherits its resources from the page tree, or shares one resource dictionary
     * with other pages, would still list the originals, and the file would keep them. So each page
     * gets resources of its own, the page tree's are dropped, and an original stays only on a page
     * whose content actually draws it.
     */
    private fun dropUnusedOriginals(document: PDDocument, replaced: Set<COSBase>) {
        val pages = document.pages.toList()
        for (page in pages) {
            if (page.cosObject.getDictionaryObject(COSName.RESOURCES) !is COSDictionary) {
                page.resources = PDResources(COSDictionary(page.resources?.cosObject ?: COSDictionary()))
            }
        }
        dropInheritedResources(document.pages.cosObject, identitySet())

        for (page in pages) {
            val resources = page.cosObject.getDictionaryObject(COSName.RESOURCES) as? COSDictionary ?: continue
            val xobjects = resources.getDictionaryObject(COSName.XOBJECT) as? COSDictionary ?: continue
            val stale = xobjects.keySet().filter { xobjects.getDictionaryObject(it) in replaced }
            if (stale.isEmpty()) continue
            val drawn = drawnNames(page)
            val drop = stale.filter { it !in drawn }
            if (drop.isEmpty()) continue
            val ownXObjects = COSDictionary(xobjects).apply { drop.forEach { removeItem(it) } }
            page.resources = PDResources(COSDictionary(resources).apply { setItem(COSName.XOBJECT, ownXObjects) })
        }
    }

    private fun dropInheritedResources(node: COSDictionary, seen: MutableSet<COSBase>) {
        if (!seen.add(node)) return
        node.removeItem(COSName.RESOURCES)
        val kids = node.getDictionaryObject(COSName.KIDS) as? COSArray ?: return
        for (i in 0 until kids.size()) {
            val kid = kids.getObject(i) as? COSDictionary ?: continue
            if (kid.getCOSName(COSName.TYPE) == COSName.PAGES) dropInheritedResources(kid, seen)
        }
    }

    /** The XObject names [page]'s own content draws with Do. */
    private fun drawnNames(page: PDPage): Set<COSName> {
        if (!page.hasContents()) return emptySet()
        val tokens = PDFStreamParser(page).apply { parse() }.tokens
        return tokens.indices.mapNotNullTo(HashSet()) { i ->
            val token = tokens[i]
            if (token is Operator && token.name == "Do") tokens.getOrNull(i - 1) as? COSName else null
        }
    }

    /**
     * Takes the removed words out of the document's metadata. Matching ignores case and spaces, since
     * removed text often has no spaces in it. When some removed glyphs could not be read as
     * letters ([unreadable]), the words are not known, so the title, subject, keywords and XMP go anyway.
     */
    private fun scrubMetadata(document: PDDocument, fragments: List<String>, unreadable: Boolean) {
        if (fragments.isEmpty() && !unreadable) return
        val squeezed = fragments.map { it.replace(SPACES, "") }
        fun mentions(text: String?) = text != null && text.lowercase().replace(SPACES, "").let { t -> squeezed.any { t.contains(it) } }

        if (unreadable) {
            document.documentInformation?.let { info ->
                info.title = null
                info.subject = null
                info.keywords = null
            }
            document.documentCatalog.metadata = null
        }

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
    private val SPACES = Regex("\\s+")

    private fun identitySet(): MutableSet<COSBase> = Collections.newSetFromMap(IdentityHashMap())

    // What an annotation or widget can say: its look, text, value, choice, link and names.
    private val ANNOTATION_TEXT = listOf(
        COSName.AP, COSName.AS, COSName.CONTENTS, COSName.V, COSName.DV, COSName.getPDFName("RC"), COSName.getPDFName("DS"),
        COSName.I, COSName.OPT, COSName.A, COSName.AA, COSName.TU, COSName.T, COSName.TM, COSName.getPDFName("Subj"),
        COSName.MK, COSName.getPDFName("Popup"),
    )

    // What a field above a removed widget holds of its value.
    private val FIELD_VALUES = listOf(COSName.V, COSName.DV, COSName.I, COSName.getPDFName("RV"))
    private val STRUCTURE_TEXT = listOf(COSName.getPDFName("Alt"), COSName.getPDFName("ActualText"), COSName.getPDFName("E"))
}
