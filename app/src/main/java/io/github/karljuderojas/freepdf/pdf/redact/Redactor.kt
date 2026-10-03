package io.github.karljuderojas.freepdf.pdf.redact

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDNameTreeNode
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment
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
        /** Pictures that could not be blanked in part (inline, too big to decode, or in a format with no decoder) and were removed whole. */
        val wholePictures: Int = 0,
        /** Files attached to the PDF (embedded files and file-attachment notes anywhere in it) that were removed. */
        val attachments: Int = 0,
        /**
         * True when a run of removed glyphs could not be read as letters, so the words were unknown
         * and the title, author, bookmarks, metadata and alternate text were cleared instead of searched.
         */
        val metadataCleared: Boolean = false,
    ) {
        /** True when the marked areas held nothing to remove (blank space), though they are still painted black. */
        val foundNothing: Boolean get() = textCharacters + pictures + shapes + forms + annotations == 0
    }

    /**
     * Redacts [areas], given per zero-based page index in the page's own PDF space (unrotated,
     * origin bottom-left; see PdfGeometry). [onPage] hears, before each marked page is done,
     * which one it is (from 1) of how many.
     */
    fun redact(document: PDDocument, areas: Map<Int, List<PdfRect>>, onPage: (page: Int, of: Int) -> Unit = { _, _ -> }): Result {
        val marked = areas.filterValues { it.isNotEmpty() }.toSortedMap()
        require(marked.isNotEmpty()) { "Nothing is marked" }
        val fragments = LinkedHashSet<String>()
        val replaced = identitySet()
        val removedAnnotations = identitySet()
        var unreadableRun = 0
        var characters = 0
        var pictures = 0
        var shapes = 0
        var forms = 0
        var wholePictures = 0

        var done = 0
        for ((index, rects) in marked) {
            onPage(++done, marked.size)
            val page = document.getPage(index)
            val redactor = PageRedactor(document, page, rects)
            redactor.run()
            characters += redactor.stats.glyphs
            pictures += redactor.stats.pictures
            shapes += redactor.stats.shapes
            forms += redactor.stats.forms
            wholePictures += redactor.stats.wholePictures
            fragments += redactor.fragments
            replaced += redactor.replacedObjects
            unreadableRun = maxOf(unreadableRun, redactor.unreadableRun)

            val (kept, removed) = page.annotations.partition { annotation ->
                // A /Rect that cannot be read is taken as touching the area: the annotation goes, and the save does not fail.
                val box = runCatching { annotation.rectangle }.getOrElse { return@partition false }
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
        val attachments = removeAttachments(document)
        // One unreadable glyph is a bullet or a symbol; a run of them is a word that cannot be searched for.
        val unreadable = unreadableRun >= MIN_FRAGMENT
        scrubMetadata(document, fragments.filter { it.replace(SPACES, "").length >= MIN_FRAGMENT }, unreadable)
        return Result(marked.size, characters, pictures, shapes, forms, annotations, wholePictures, attachments, unreadable)
    }

    /**
     * Without changing [document], the zero-based pages among [areas] on which a picture under an
     * area would have to be removed whole by [redact], because it cannot be blanked in part here.
     */
    fun check(document: PDDocument, areas: Map<Int, List<PdfRect>>): List<Int> =
        areas.filterValues { it.isNotEmpty() }.toSortedMap().filter { (index, rects) ->
            val redactor = PageRedactor(document, document.getPage(index), rects, probe = true)
            // A page that cannot be replayed is reported when the copy is written, not here.
            runCatching { redactor.run() }
            redactor.stats.wholePictures > 0
        }.keys.toList()

    /**
     * Takes every attached file out of the document: the embedded files in its name tree,
     * associated files (PDF/A-3, electronic invoices) on the catalog and the pages, and
     * file-attachment notes on any page. An attachment is a whole second document that the marks
     * cannot reach into. Returns how many went.
     */
    private fun removeAttachments(document: PDDocument): Int {
        val files = identitySet()
        var unknown = 0
        val catalog = document.documentCatalog
        val associated = COSName.getPDFName("AF")

        fun fileSpecsIn(array: COSArray?) {
            for (i in 0 until (array?.size() ?: 0)) array?.getObject(i)?.let { files += it }
        }

        (catalog.cosObject.getDictionaryObject(COSName.NAMES) as? COSDictionary)?.let { names ->
            val tree = names.getDictionaryObject(COSName.EMBEDDED_FILES)
            if (tree != null) {
                val read = runCatching {
                    PDEmbeddedFilesNameTreeNode(tree as COSDictionary).let { node -> allNames(node).forEach { files += it.cosObject } }
                }
                if (read.isFailure) unknown++
                names.removeItem(COSName.EMBEDDED_FILES)
                if (names.size() == 0) catalog.cosObject.removeItem(COSName.NAMES)
            }
        }
        fileSpecsIn(catalog.cosObject.getDictionaryObject(associated) as? COSArray)
        catalog.cosObject.removeItem(associated)

        for (page in document.pages) {
            fileSpecsIn(page.cosObject.getDictionaryObject(associated) as? COSArray)
            page.cosObject.removeItem(associated)
            val (kept, gone) = page.annotations.partition { it !is PDAnnotationFileAttachment }
            if (gone.isEmpty()) continue
            page.annotations = kept
            gone.forEach { files += it.cosObject.getDictionaryObject(COSName.getPDFName("FS")) ?: it.cosObject }
        }
        return files.size + unknown
    }

    private fun allNames(node: PDNameTreeNode<PDComplexFileSpecification>): List<PDComplexFileSpecification> =
        node.names?.values.orEmpty().toList() + node.kids.orEmpty().flatMap { allNames(it) }

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
            // Content that cannot be read is left with everything it may draw.
            val drawn = drawnNames(page) ?: continue
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

    /**
     * The XObject names [page] draws with Do from its own resources: in its content, and in any form
     * it draws that has no resources of its own and so looks names up in the page's. Null when the
     * content cannot be parsed.
     */
    private fun drawnNames(page: PDPage): Set<COSName>? = runCatching {
        val names = HashSet<COSName>()
        val xobjects = page.resources?.cosObject?.getDictionaryObject(COSName.XOBJECT) as? COSDictionary
        val scanned = identitySet()

        fun scan(tokens: List<Any?>) {
            for (i in tokens.indices) {
                val token = tokens[i]
                if (token !is Operator || token.name != "Do") continue
                val name = tokens.getOrNull(i - 1) as? COSName ?: continue
                names += name
                val form = xobjects?.getDictionaryObject(name) as? COSStream ?: continue
                if (form.getCOSName(COSName.SUBTYPE) != COSName.FORM || form.containsKey(COSName.RESOURCES)) continue
                if (scanned.add(form)) scan(PDFStreamParser(PDFormXObject(form)).apply { parse() }.tokens)
            }
        }

        if (page.hasContents()) scan(PDFStreamParser(page).apply { parse() }.tokens)
        names
    }.getOrNull()

    /**
     * Takes the removed words out of the document's metadata. Matching ignores case and spaces, since
     * removed text often has no spaces in it. When a run of removed glyphs could not be read as
     * letters ([unreadable]), the words are not known, so the title, subject, keywords, XMP,
     * bookmark titles and alternate text go anyway.
     */
    private fun scrubMetadata(document: PDDocument, fragments: List<String>, unreadable: Boolean) {
        if (fragments.isEmpty() && !unreadable) return
        val squeezed = fragments.map { it.replace(SPACES, "") }
        fun mentions(text: String?) = text != null && text.lowercase().replace(SPACES, "").let { t -> squeezed.any { t.contains(it) } }

        if (unreadable) {
            document.documentInformation?.let { info ->
                info.title = null
                info.author = null
                info.subject = null
                info.keywords = null
                info.creator = null
            }
            document.documentCatalog.metadata = null
        }
        // With the words unknown, any bookmark or alternate text might repeat them.
        val mentionsRemoved: (String?) -> Boolean = if (unreadable) { text -> !text.isNullOrBlank() } else ::mentions

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

        catalog.documentOutline?.let { scrubOutline(it, mentionsRemoved) }

        val seen = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
        catalog.cosObject.getDictionaryObject(COSName.getPDFName("StructTreeRoot"))?.let { scrubStructure(it, seen, mentionsRemoved) }
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
