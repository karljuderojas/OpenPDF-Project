package io.github.karljuderojas.freepdf.ui.viewer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import io.github.karljuderojas.freepdf.FreePdfApp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.Documents
import io.github.karljuderojas.freepdf.files.SafeWrite
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.annotate.Mark
import io.github.karljuderojas.freepdf.pdf.annotate.Marks
import io.github.karljuderojas.freepdf.pdf.annotate.Stamps
import io.github.karljuderojas.freepdf.pdf.annotate.TextBoxes
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.edit.EditSession
import io.github.karljuderojas.freepdf.pdf.edit.Flattener
import io.github.karljuderojas.freepdf.pdf.edit.CropMargins
import io.github.karljuderojas.freepdf.pdf.edit.PageCrop
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor
import io.github.karljuderojas.freepdf.pdf.edit.PdfDocuments
import io.github.karljuderojas.freepdf.pdf.edit.PdfText
import io.github.karljuderojas.freepdf.pdf.form.FormField
import io.github.karljuderojas.freepdf.pdf.form.FormFiller
import io.github.karljuderojas.freepdf.pdf.edit.Splitting
import io.github.karljuderojas.freepdf.pdf.links.LinkTarget
import io.github.karljuderojas.freepdf.pdf.links.PageLink
import io.github.karljuderojas.freepdf.pdf.links.PageLinks
import io.github.karljuderojas.freepdf.pdf.info.DocumentInfo
import io.github.karljuderojas.freepdf.pdf.render.OutlineItem
import io.github.karljuderojas.freepdf.pdf.render.PageBox
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.text.PageText
import io.github.karljuderojas.freepdf.pdf.text.PageWord
import io.github.karljuderojas.freepdf.pdf.render.PdfRenderer
import io.github.karljuderojas.freepdf.pdf.sign.AuditEvent
import io.github.karljuderojas.freepdf.pdf.sign.AuditTrail
import io.github.karljuderojas.freepdf.pdf.sign.CertificateInfo
import io.github.karljuderojas.freepdf.pdf.sign.CertificateStore
import io.github.karljuderojas.freepdf.pdf.sign.DocumentHash
import io.github.karljuderojas.freepdf.pdf.sign.SignField
import io.github.karljuderojas.freepdf.pdf.sign.SignatureFields
import io.github.karljuderojas.freepdf.pdf.sign.SignatureMethod
import io.github.karljuderojas.freepdf.pdf.sign.SignatureReport
import io.github.karljuderojas.freepdf.pdf.sign.SignatureAnnotation
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.pdf.sign.SignatureVerifier
import io.github.karljuderojas.freepdf.pdf.sign.SignedCopy
import io.github.karljuderojas.freepdf.pdf.sign.SignerRecord
import io.github.karljuderojas.freepdf.pdf.sign.SigningIdentity
import io.github.karljuderojas.freepdf.pdf.sign.TimestampClient
import io.github.karljuderojas.freepdf.pdf.sign.info
import io.github.karljuderojas.freepdf.print.Printing
import io.github.karljuderojas.freepdf.share.Sharing
import io.github.karljuderojas.freepdf.ui.sign.FinishOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

sealed interface ViewerState {
    data object Loading : ViewerState

    /**
     * [revision] changes after every edit, so pages already on screen are rendered again.
     * [hasSignature] is true once a signature or initials are placed, which offers Finish.
     * [signatures] are the digital signatures the file had when it was opened.
     * [formFields] are the PDF's own fillable fields, for Fill form.
     * [isProtected] is true while the PDF needs a password to open.
     * [outline] is the PDF's table of contents, empty if it has none.
     * [signFields] are the places the document asks to be signed, and [signedFields] the
     * indexes of those signed so far (signatures still being placed are not counted here).
     * [failedPageEdits] counts the page edits (moves, deletions, rotations, inserts, merges)
     * that left the document as it was, so Pages mode can put its selection back after one.
     */
    data class Ready(
        val pageSizes: List<PageSize>,
        val revision: Int = 0,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val hasUnsavedChanges: Boolean = false,
        val hasSignature: Boolean = false,
        val outline: List<OutlineItem> = emptyList(),
        val isProtected: Boolean = false,
        val formFields: List<FormField> = emptyList(),
        val signFields: List<SignField> = emptyList(),
        val signedFields: Set<Int> = emptySet(),
        val signatures: List<SignatureReport> = emptyList(),
        val failedPageEdits: Int = 0,
        /** The links the PDF has, which Read mode makes tappable. */
        val links: List<PageLink> = emptyList(),
    ) : ViewerState

    /** The PDF is password protected; [wrongPassword] after a password that did not open it. */
    data class Locked(val wrongPassword: Boolean = false) : ViewerState

    data class Failed(val message: String?) : ViewerState
}

/** One place the search text was found: its page, and boxes covering it on that page. */
data class TextMatch(val page: Int, val boxes: List<PageBox>)

/** Matches for [query] so far, in page order; [finished] once every page has been searched. */
data class SearchResults(val query: String = "", val matches: List<TextMatch> = emptyList(), val finished: Boolean = true)

/** One-off things the screen does for the view model: messages, the Save As picker, leaving. */
sealed interface ViewerEffect {
    data class Message(@StringRes val text: Int) : ViewerEffect

    /** A message that names a number, such as "Saved 3 pages as a new PDF". */
    data class CountMessage(@PluralsRes val text: Int, val count: Int) : ViewerEffect
    data class SaveAs(val suggestedName: String) : ViewerEffect
    data object Close : ViewerEffect
    data class Share(val file: File) : ViewerEffect

    /** Share page pictures; [title] names the document they came from. */
    data class ShareImages(val files: List<File>, val title: String) : ViewerEffect

    /** Open the print dialog for [file], a printable copy named [name]. */
    data class Print(val file: File, val name: String, val pageCount: Int) : ViewerEffect

    /** Ask where to save the signed copy; see [ViewerViewModel.saveSignedCopy]. */
    data class SaveSigned(val suggestedName: String) : ViewerEffect

    /** Ask where to save extracted pages; see [ViewerViewModel.saveExtract]. */
    data class SaveExtract(val suggestedName: String) : ViewerEffect

    /** Ask which folder the split PDFs go in; see [ViewerViewModel.splitInto]. */
    data object PickSplitFolder : ViewerEffect
    /** Show [info] about the open PDF, titled with its file [name]. */
    data class ShowInfo(val name: String, val info: DocumentInfo) : ViewerEffect
}

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Loading)
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private val _effects = Channel<ViewerEffect>(Channel.BUFFERED)
    val effects: Flow<ViewerEffect> = _effects.receiveAsFlow()

    private var renderer: PdfRenderer? = null

    // The open documents' sessions live in the application, so they survive this view model
    // (switching documents replaces it) and switching back finds edits, undo and place as left.
    private val sessions = getApplication<FreePdfApp>().sessions

    /** The session this view model shows and edits; null until [open] has attached one. */
    private var doc: DocumentSession? = null
    private val session: EditSession? get() = doc?.session

    /** Where saving goes: the opened file, or the copy Save As made. */
    private val openedUri: Uri? get() = doc?.uri

    /** The URI the last [open] was asked for, which may differ from [openedUri] after Save As. */
    private var requestedUri: Uri? = null
    private var revision = 0
    private var closeAfterSave = false

    /** Another open document being saved from the switcher; null while the one on screen is. */
    private var savingOther: DocumentSession? = null

    // For the audit page: the original's fingerprint, when it was opened, and one entry per
    // edit (null for edits that are not part of signing), so undo can drop the matching entry.
    // All kept in the session; before one is attached there is nothing to edit, so the logs
    // written then are throwaways.
    private val originalSha256: String get() = doc?.originalSha256.orEmpty()
    private val openedAt: Instant get() = doc?.openedAt ?: Instant.now()
    private val editLog: MutableList<AuditEvent?> get() = doc?.editLog ?: ArrayList()
    private val redoLog: MutableList<AuditEvent?> get() = doc?.redoLog ?: ArrayList()

    /** What [SignatureVerifier] found in the file as opened. */
    private var signatures: List<SignatureReport>
        get() = doc?.signatures.orEmpty()
        set(value) {
            doc?.signatures = value
        }

    // Finding the places to sign reads all the text, so it is done again only when pages change.
    private var signFields: List<SignField>?
        get() = doc?.signFields
        set(value) {
            doc?.signFields = value
        }

    private val _position = MutableStateFlow<ViewPosition?>(null)

    /** Where the reader was in the document when it was last on screen; null until it is open. */
    val position: StateFlow<ViewPosition?> = _position.asStateFlow()

    /** See [ViewerState.Ready.failedPageEdits]. */
    private var failedPageEdits = 0

    /** The finished signed copy, in the cache, waiting for the user to pick where it goes. */
    private var pendingSignedCopy: File? = null

    /** Whether Finish asked to share the signed copy once it is saved. */
    private var shareSignedCopy = false
    /** Pages to extract, or the parts to split into, waiting for the user to pick where they go. */
    private var pendingExtract: List<Int>? = null
    private var pendingSplit: List<List<Int>>? = null

    private val signingPrefs = application.getSharedPreferences("signing", Context.MODE_PRIVATE)
    private val _signerName = MutableStateFlow(signingPrefs.getString(KEY_SIGNER_NAME, "").orEmpty())

    /** The name typed at the last Finish, to fill in next time. */
    val signerName: StateFlow<String> = _signerName.asStateFlow()

    private val certificates = CertificateStore(signingPrefs)
    private val _certificate = MutableStateFlow<CertificateInfo?>(null)

    /** The imported certificate that seals signed copies; null for this phone's own. */
    val certificate: StateFlow<CertificateInfo?> = _certificate.asStateFlow()

    private val _timestampsOn = MutableStateFlow(certificates.timestampsOn)
    val timestampsOn: StateFlow<Boolean> = _timestampsOn.asStateFlow()

    // Guards the renderer and the working copy: PDFium renders one page at a time, and an edit
    // swaps both the file and the renderer underneath any page that is mid-render.
    private val lock = Mutex()

    // Roughly a dozen screen-sized pages; bitmaps are the bulk of the app's memory.
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val context get() = getApplication<Application>()

    // Text positions per page, read with PdfBox from the working copy, for selecting text and for
    // the text a highlight covers. Reading them is slow, so they are kept across edits that only
    // add, change or remove marks (the pages' own text stays as it was) and dropped on any other.
    private val wordCache = HashMap<Int, List<PageWord>>()
    private var textDocument: PDDocument? = null

    private val _search = MutableStateFlow(SearchResults())

    /** Results of the last [search]. Cleared whenever the document changes. */
    val search: StateFlow<SearchResults> = _search.asStateFlow()
    private var searchJob: Job? = null

    private val signatureStore = SignatureStore(File(application.filesDir, "signatures"))
    private val _savedSignatures = MutableStateFlow<Map<SignatureStore.Kind, Bitmap>>(emptyMap())

    /** The user's saved signature and initials, if they have drawn them. */
    val savedSignatures: StateFlow<Map<SignatureStore.Kind, Bitmap>> = _savedSignatures.asStateFlow()

    private val annotatePrefs = application.getSharedPreferences("annotate", Context.MODE_PRIVATE)
    private val _toolStyles = MutableStateFlow(loadToolStyles())

    /** The colour and size last chosen for each Annotate tool. */
    val toolStyles: StateFlow<Map<AnnotateTool, ToolStyle>> = _toolStyles.asStateFlow()

    private val _stamps = MutableStateFlow<List<PlacedStamp>>(emptyList())
    private var nextStampId = 1L

    // Stamps already on their way into the PDF, so a second commit does not draw them twice, and
    // those already drawn in, so a switch of document mid-commit does not keep them as placed.
    private var committing = emptySet<Long>()
    private var committed = emptySet<Long>()

    // Commits still running, each answering whether every stamp of it was drawn. Finish waits
    // for all of them, so a signed copy is never made without a signature the user placed.
    private val commits = ArrayList<Deferred<Boolean>>()

    private val _finishing = MutableStateFlow<SignedCopy.Step?>(null)

    /** What Finish is busy with while the signed copy is being made, or null when it is not. */
    val finishing: StateFlow<SignedCopy.Step?> = _finishing.asStateFlow()

    /** Sign and Edit mode stamps that can still be moved or resized; see [commitStamps]. */
    val stamps: StateFlow<List<PlacedStamp>> = _stamps.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _savedSignatures.value = SignatureStore.Kind.entries.mapNotNull { kind ->
                runCatching { signatureStore.load(kind) }.getOrNull()?.let { kind to it }
            }.toMap()
            _certificate.value = certificates.imported()?.info()
        }
    }

    fun open(uri: Uri) {
        // Compared with what the screen asked for, not with [openedUri]: Save As and a signed copy
        // move openedUri to the new file, and the screen's route still names the old one, so a
        // configuration change would otherwise reopen the original and throw the session away.
        if (uri == requestedUri) return
        requestedUri = uri
        _state.value = ViewerState.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                lock.withLock {
                    val entry = withContext(Dispatchers.IO) {
                        detachLocked()
                        val entry = sessions.attach(uri.toString()) {
                            context.contentResolver.openInputStream(uri) ?: error("Cannot read $uri")
                        }
                        // A session made just now, rather than one left by an earlier visit.
                        if (entry.originalSha256.isEmpty()) {
                            entry.originalSha256 = entry.session.workingFile.inputStream().use { DocumentHash.sha256(it) }
                            entry.openedAt = Instant.now()
                            entry.signatures = SignatureVerifier().verify(entry.session.workingFile)
                        }
                        entry
                    }
                    doc = entry
                    _stamps.value = entry.stamps
                    nextStampId = entry.nextStampId
                    _position.value = entry.position
                    firstLoadLocked()
                }
            }.onSuccess { remember(uri) }.getOrElse {
                if (it is CancellationException) throw it
                // A document that could not be opened is not in the open list, so nothing is kept for it.
                doc = null
                sessions.close(uri.toString())
                ViewerState.Failed(it.message)
            }
        }
    }

    /**
     * Gives the attached session what only this view model knew, stamps still being placed and
     * where the reader was, so the next view model to attach finds them. Call with [lock] held,
     * or from [onCleared], when nothing else runs.
     */
    private fun detachLocked() {
        val current = doc ?: return
        current.stamps = _stamps.value.filter { it.id !in committed }
        current.nextStampId = nextStampId
        sessions.refresh()
    }

    /** Called as the reader scrolls, so switching back to this document lands at the same place. */
    fun viewPositionChanged(position: ViewPosition) {
        doc?.position = position
    }

    /**
     * Throws away this document's unsaved changes: its session ends, and it is opened fresh
     * from its file next time. The document stays in the open list.
     */
    fun discardChanges() {
        val current = doc ?: return
        doc = null
        _stamps.value = emptyList()
        sessions.close(current.key)
    }

    /** Searches every page for [query], publishing matches page by page. A blank query clears. */
    fun search(query: String) {
        searchJob?.cancel()
        val text = query.trim()
        if (text.isEmpty()) {
            _search.value = SearchResults()
            return
        }
        _search.value = SearchResults(text, finished = false)
        searchJob = viewModelScope.launch {
            val found = ArrayList<TextMatch>()
            var index = 0
            while (true) {
                // One page per turn of the lock, so pages keep rendering while a long PDF is searched.
                val onPage = lock.withLock {
                    val current = renderer ?: return@withLock null
                    if (index >= current.pageCount) null else runCatching { current.find(index, text) }.getOrDefault(emptyList())
                } ?: break
                if (onPage.isNotEmpty()) {
                    onPage.forEach { found += TextMatch(index, it) }
                    _search.value = SearchResults(text, found.toList(), finished = false)
                }
                index++
            }
            _search.value = SearchResults(text, found.toList(), finished = true)
        }
    }

    /** Tries [password] on a [ViewerState.Locked] PDF. */
    fun unlock(password: String) {
        viewModelScope.launch {
            _state.value = lock.withLock {
                val current = session ?: return@withLock ViewerState.Failed(null)
                if (withContext(Dispatchers.IO) { current.unlock(password) }) {
                    // Opening could not read a locked file's signatures; check them now it is open.
                    withContext(Dispatchers.IO) { signatures = SignatureVerifier().verify(current.workingFile, password) }
                    runCatching { reloadLocked() }.getOrElse { ViewerState.Failed(it.message) }
                } else {
                    ViewerState.Locked(wrongPassword = true)
                }
            }
        }
    }

    suspend fun page(index: Int, widthPx: Int): Bitmap? {
        val key = "$revision/$index@$widthPx"
        cache.get(key)?.let { return it }
        return lock.withLock {
            val current = renderer ?: return@withLock null
            if (index >= current.pageCount) return@withLock null
            cache.get(key) ?: runCatching { current.renderPage(index, widthPx) }
                .onSuccess { cache.put(key, it) }
                // A page too big for the heap stays blank rather than taking the app down.
                .getOrElse { if (it is OutOfMemoryError) null else throw it }
        }
    }

    /**
     * The part of page [index] in [region], rendered as if the page were [fullWidthPx] wide.
     * Not cached: it is only good for one zoom and scroll position, and it is at most a
     * screenful of pixels, so a zoomed screen never holds much more than two screenfuls.
     */
    suspend fun pageRegion(index: Int, fullWidthPx: Int, region: IntRect): Bitmap? = lock.withLock {
        val current = renderer ?: return@withLock null
        if (index >= current.pageCount) return@withLock null
        current.renderRegion(index, fullWidthPx, region)
    }

    fun rotatePages(pages: Set<Int>) = edit(movesPages = true) { PageEditor.rotate(it, pages, 90) }

    /** Trims [pages] by [margins], or shows them in full again when [margins] is null. */
    fun cropPages(pages: Set<Int>, margins: CropMargins?) = edit(movesPages = true) {
        if (margins == null) PageCrop.reset(it, pages) else PageCrop.crop(it, pages, margins)
    }

    /** Adds a link over [box] on [page] (fractions of the page as shown) that leads to [target]. */
    fun addLink(page: Int, box: DisplayRect, target: LinkTarget) = edit { PageLinks.add(it, page, box, target) }

    fun deletePages(pages: Set<Int>) {
        val pageCount = (_state.value as? ViewerState.Ready)?.pageSizes?.size ?: return
        if (pages.size >= pageCount) {
            _effects.trySend(ViewerEffect.Message(R.string.cannot_delete_last_page))
            failedPageEdit()
            return
        }
        edit(movesPages = true) { PageEditor.delete(it, pages) }
    }

    fun insertBlankPage(afterIndex: Int) = edit(movesPages = true) { document ->
        val neighbour = document.getPage(afterIndex)
        PageEditor.insertBlank(document, afterIndex + 1, neighbour.mediaBox)
        // A blank next to a rotated scan should face the same way as its neighbour.
        document.getPage(afterIndex + 1).rotation = neighbour.rotation
    }

    fun movePage(from: Int, to: Int) = edit(movesPages = true) { PageEditor.move(it, from, to) }

    /** Moves the selected pages [by] places together, as one undo step. */
    fun shiftPages(pages: Set<Int>, by: Int) = edit(movesPages = true) { PageEditor.shift(it, pages, by) }

    /** Appends every page of [other] to the end of the open document. */
    fun merge(other: Uri) = edit(movesPages = true) { document ->
        PdfDocuments.load(context, other).use { PageEditor.append(document, it) }
    }

    fun ink(page: Int, strokes: List<List<Offset>>, style: ToolStyle) = mark { document ->
        val toPdf = displayMapper(document, page)
        Annotator.ink(document, page, strokes.map { stroke -> stroke.map(toPdf) }, style.rgb, style.width)
    }

    fun markText(page: Int, start: Offset, end: Offset, kind: Annotator.TextMarkup, style: ToolStyle) =
        mark { document ->
            Annotator.markText(document, page, listOf(boxOf(document, page, start, end)), kind, style.rgb, style.width)
        }

    /**
     * Marks text along [lines] (one box per line, as page fractions; see [PageText]). [comment]
     * becomes the mark's note.
     */
    fun markLines(page: Int, lines: List<Rect>, kind: Annotator.TextMarkup, style: ToolStyle, comment: String? = null) =
        mark { document ->
            val boxes = lines.map { boxOf(document, page, it.topLeft, it.bottomRight) }
            Annotator.markText(document, page, boxes, kind, style.rgb, style.width, comment)
        }

    private val _marks = MutableStateFlow<List<Mark>>(emptyList())

    /** Every mark in the document as it is now, for tapping to edit and the Comments list. */
    val marks: StateFlow<List<Mark>> = _marks.asStateFlow()

    /** Changes a mark's colour, line width or comment; null leaves that part alone. */
    fun editMark(page: Int, index: Int, color: Annotator.Rgb?, width: Float?, comment: String?) = mark { document ->
        Marks.edit(document, page, index, color, width, comment)
    }

    fun deleteMark(page: Int, index: Int) = mark { document -> Marks.delete(document, page, index) }

    /** The words on [page] and where they are, for selecting text. Empty for scanned pages. */
    suspend fun words(page: Int): List<PageWord> = lock.withLock {
        wordCache[page] ?: withContext(Dispatchers.IO) {
            runCatching {
                val document = textDocument ?: (session?.let { PDDocument.load(it.workingFile, it.password) } ?: error("Nothing is open")).also { textDocument = it }
                PageText.words(document, page)
            }.getOrDefault(emptyList())
        }.also { wordCache[page] = it }
    }

    fun shape(page: Int, start: Offset, end: Offset, style: ToolStyle) = mark { document ->
        Annotator.shape(document, page, boxOf(document, page, start, end), color = style.rgb, lineWidth = style.width)
    }

    fun note(page: Int, at: Offset, text: String, style: ToolStyle) = mark { document ->
        Annotator.note(document, page, displayMapper(document, page)(at), text, style.rgb)
    }

    /** Places a [kind] stamp centred where the user tapped. */
    fun stamp(page: Int, at: Offset, kind: Stamps.Kind) = mark { document ->
        Stamps.add(document, page, displayMapper(document, page)(at), kind)
    }

    /** Adds a text box whose top-left corner is at [at]; [style]'s width is the font size. */
    fun textBox(page: Int, at: Offset, text: String, style: ToolStyle) = mark { document ->
        TextBoxes.add(document, page, displayMapper(document, page)(at), text, style.rgb, fontSize = style.width)
    }

    /** Remembers [style] for [tool], here and the next time the app opens. */
    fun setToolStyle(tool: AnnotateTool, style: ToolStyle) {
        _toolStyles.value += tool to style
        annotatePrefs.edit().putString(tool.name, "${style.color.toArgb()},${style.width}").apply()
    }

    private fun loadToolStyles(): Map<AnnotateTool, ToolStyle> = AnnotateTool.entries.mapNotNull { tool ->
        val saved = annotatePrefs.getString(tool.name, null)?.split(',') ?: return@mapNotNull null
        val color = saved.getOrNull(0)?.toIntOrNull()?.let { Color(it) } ?: return@mapNotNull null
        val width = saved.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        // A colour or size this version no longer offers falls back to the tool's default.
        val default = tool.defaultStyle
        tool to ToolStyle(
            color.takeIf { it in tool.palette } ?: default.color,
            width.takeIf { it in tool.widths } ?: default.width,
        )
    }.toMap()

    /**
     * Removes the topmost mark under [at], with its pop-up. Links, form fields and signatures
     * placed in Sign mode are left alone (signatures are taken back with Undo there).
     */
    fun erase(page: Int, at: Offset) {
        mark(onNoChange = R.string.nothing_to_erase) { document ->
            val point = displayMapper(document, page)(at)
            val index = Marks.indexAt(document, page, point.x, point.y) ?: throw NothingChanged()
            Marks.delete(document, page, index)
        }
    }

    fun saveSignature(kind: SignatureStore.Kind, image: Bitmap, method: SignatureMethod) {
        _savedSignatures.value += kind to image
        signingPrefs.edit().putString(methodKey(kind), method.name).apply()
        viewModelScope.launch(Dispatchers.IO) {
            // Still usable in this session if the Keystore refuses; it just is not remembered.
            runCatching { signatureStore.save(kind, image) }
        }
    }

    /**
     * Puts the saved signature or initials on the line the user tapped. Like the other Sign
     * stamps, it can be moved and resized until [commitStamps] writes it into the PDF.
     */
    fun placeSignature(page: Int, at: Offset, kind: SignatureStore.Kind) {
        val image = _savedSignatures.value[kind] ?: return
        val size = pageSize(page) ?: return
        addStamp(page, StampContent.Signature(kind, image), StampGeometry.signatureBox(at, image.width, image.height, kind, size))
    }

    /**
     * Puts the saved signature in [requested], one of [ViewerState.Ready.signFields], fitted to it.
     * It can be moved and resized like any other placed signature. The place is looked up again
     * by where it is, in case the fields were found afresh since the screen saw them.
     */
    fun signField(requested: SignField) {
        val fields = (_state.value as? ViewerState.Ready)?.signFields ?: return
        val field = SignatureFields.indexOf(fields, requested)?.let { fields[it] } ?: return
        val image = _savedSignatures.value[SignatureStore.Kind.Signature] ?: return
        val size = pageSize(field.page) ?: return
        addStamp(field.page, StampContent.Signature(SignatureStore.Kind.Signature, image), StampGeometry.fieldBox(field.box, image.width, image.height, size))
    }

    fun addDate(page: Int, at: Offset) {
        // The phone's own date format, unless it uses a script the bundled fonts cannot show
        // (Arabic, Devanagari, CJK), in which case the English form is placed instead of "?".
        val today = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()).takeIf { PdfText.isLatinGreekOrCyrillic(it) }
            ?: DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US).format(Date())
        placeText(page, at, today, "date")
    }

    fun addText(page: Int, at: Offset, text: String) = placeText(page, at, text, "text")

    private fun placeText(page: Int, at: Offset, text: String, what: String?, fontSize: Float = StampGeometry.TEXT_SIZE) {
        val size = pageSize(page) ?: return
        val content = StampContent.Text(text, what)
        addStamp(page, content, StampGeometry.textBox(at, content.lines, size, fontSize, ::emWidth))
    }

    /** Edit mode's Add text: like Sign's Text, but larger and kept off the audit page. */
    fun addEditText(page: Int, at: Offset, text: String) = placeText(page, at, text, what = null, StampGeometry.EDIT_TEXT_SIZE)

    /** Edit mode's Add image: the picture lands in the middle of [page], ready to move and resize. */
    fun addImage(page: Int, uri: Uri) {
        viewModelScope.launch {
            val image = runCatching { withContext(Dispatchers.IO) { PickedImage.load(context, uri) } }.getOrNull()
            val size = pageSize(page)
            if (image == null || size == null) {
                _effects.send(ViewerEffect.Message(R.string.image_failed))
                return@launch
            }
            addStamp(page, StampContent.Image(image), StampGeometry.imageBox(Offset(0.5f, 0.5f), image.width, image.height, size))
        }
    }

    fun addCheckmark(page: Int, at: Offset) {
        val size = pageSize(page) ?: return
        addStamp(page, StampContent.Checkmark, StampGeometry.checkmarkBox(at, size))
    }

    /** Sets one of the PDF's own form fields; see [FormFiller.fill] for what [value] means. */
    fun fillField(field: FormField, value: String?) {
        val event = AuditEvent(AuditEvent.Type.FieldFilled, SIGNER, detail = "form field \"${field.label}\" on page ${field.page + 1}")
        edit(event = event) { document -> FormFiller.fill(document, field.name, value) }
    }

    fun moveStamp(id: Long, dx: Float, dy: Float) = updateStamp(id) { it.copy(box = it.box.moved(dx, dy)) }

    fun resizeStamp(id: Long, factor: Float) = updateStamp(id) { stamp ->
        pageSize(stamp.page)?.let { stamp.copy(box = stamp.box.scaled(factor, it)) } ?: stamp
    }

    fun deleteStamp(id: Long) = _stamps.update { stamps -> stamps.filter { it.id != id } }

    /**
     * Writes every placed stamp into the PDF where it now sits, one undo step each. Called on
     * leaving Sign or Edit mode and before finishing, after which they are part of the page.
     */
    fun commitStamps() {
        commitStampsAsync()
    }

    /**
     * Starts [commitStamps] and answers whether every stamp it took on was drawn, true when
     * there was nothing to draw. With [notify], a failure is also shown as a message.
     */
    private fun commitStampsAsync(notify: Boolean = true): Deferred<Boolean> {
        val placed = _stamps.value.filter { it.id !in committing }
        if (placed.isEmpty()) return CompletableDeferred(true)
        committing = committing + placed.map { it.id }
        val commit = viewModelScope.async {
            lock.withLock {
                var failed = false
                withContext(Dispatchers.IO) {
                    val current = session ?: return@withContext
                    placed.forEach { stamp ->
                        runCatching { current.edit { document -> drawStamp(document, stamp) } }
                            .onSuccess {
                                editLog += auditEventFor(stamp)?.copy(at = Instant.now())
                                redoLog.clear()
                                committed = committed + stamp.id
                            }
                            .onFailure { failed = true }
                    }
                }
                _state.value = reloadOrFail()
                // Taken off only now, so they stay on screen until the page shows them drawn in.
                val done = placed.map { it.id }.toSet()
                _stamps.update { stamps -> stamps.filter { it.id !in done } }
                committing = committing - done
                committed = committed - done
                if (failed && notify) _effects.send(ViewerEffect.Message(R.string.edit_failed))
                !failed
            }
        }
        commits += commit
        commit.invokeOnCompletion { viewModelScope.launch { commits -= commit } }
        return commit
    }

    private fun addStamp(page: Int, content: StampContent, box: StampBox) {
        _stamps.update { it + PlacedStamp(nextStampId++, page, content, box) }
    }

    private fun updateStamp(id: Long, change: (PlacedStamp) -> PlacedStamp) =
        _stamps.update { stamps -> stamps.map { if (it.id == id) change(it) else it } }

    private fun pageSize(page: Int) = (_state.value as? ViewerState.Ready)?.pageSizes?.getOrNull(page)

    private fun drawStamp(document: PDDocument, stamp: PlacedStamp) {
        val size = displaySize(document, stamp.page)
        val toPdf = displayMapper(document, stamp.page)
        val box = stamp.box
        when (val content = stamp.content) {
            // An annotation until Finish draws it into the page, unless the signer keeps it editable.
            is StampContent.Signature -> SignatureAnnotation.add(
                document, stamp.page, content.image, toPdf(StampGeometry.signatureAnchor(box)),
                maxWidth = box.width * size.widthPt, maxHeight = box.height * size.heightPt,
                initials = content.kind == SignatureStore.Kind.Initials,
            )
            is StampContent.Text -> {
                val fontSize = StampGeometry.fontSize(box, content.lines.size, size)
                PageEditor.addText(document, stamp.page, content.text, toPdf(StampGeometry.textAnchor(box, fontSize, size)), fontSize)
            }
            StampContent.Checkmark -> PageEditor.addCheckmark(
                document, stamp.page, toPdf(StampGeometry.checkmarkAnchor(box, size)), StampGeometry.checkmarkSize(box, size),
            )
            is StampContent.Image -> PageEditor.addImage(
                document, stamp.page, content.image, toPdf(StampGeometry.imageAnchor(box)),
                width = box.width * size.widthPt, height = box.height * size.heightPt,
            )
        }
    }

    /** What the audit page records for [stamp], or null for Edit-mode content that is not part of signing. */
    private fun auditEventFor(stamp: PlacedStamp): AuditEvent? {
        val where = "on page ${stamp.page + 1}"
        return when (val content = stamp.content) {
            is StampContent.Signature -> {
                val what = if (content.kind == SignatureStore.Kind.Initials) "initials" else "signature"
                AuditEvent(AuditEvent.Type.Signed, SIGNER, detail = "$what $where")
            }
            is StampContent.Text -> content.what?.let { AuditEvent(AuditEvent.Type.FieldFilled, SIGNER, detail = "$it $where") }
            StampContent.Checkmark -> AuditEvent(AuditEvent.Type.FieldFilled, SIGNER, detail = "checkmark $where")
            is StampContent.Image -> null
        }
    }

    /** The page's size in points as it is shown, that is turned by its /Rotate. */
    private fun displaySize(document: PDDocument, page: Int): PageSize {
        val pdfPage = document.getPage(page)
        val crop = pdfPage.cropBox
        return if (pdfPage.rotation % 180 == 0) PageSize(crop.width, crop.height) else PageSize(crop.height, crop.width)
    }

    /** Imports a .p12/.pfx certificate to seal signed copies with, replacing any earlier one. */
    fun importCertificate(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read $uri")
                    certificates.import(bytes, password).info()
                }
            }
            password.fill(' ')
            result.onSuccess { _certificate.value = it }
            _effects.send(
                ViewerEffect.Message(
                    when (result.exceptionOrNull()) {
                        null -> R.string.certificate_imported
                        is CertificateExpiredException, is CertificateNotYetValidException -> R.string.certificate_expired
                        else -> R.string.certificate_import_failed
                    },
                ),
            )
        }
    }

    fun removeCertificate() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { certificates.remove() }
            _certificate.value = null
            _effects.send(ViewerEffect.Message(R.string.certificate_removed))
        }
    }

    fun setTimestamps(on: Boolean) {
        certificates.timestampsOn = on
        _timestampsOn.value = on
    }

    /**
     * Builds the signed copy: everything placed so far, with signatures locked into the page if
     * [FinishOptions.lock], a signing certificate page naming [name] with the [consentText] they
     * agreed to, and, if [FinishOptions.seal], a digital signature from the imported certificate or
     * else a key made in this phone's Keystore, timestamped if the user turned that on. The user
     * then picks where to save it, and shares it too if [FinishOptions.share].
     */
    fun finishSigning(name: String, consentText: String, options: FinishOptions) {
        val uri = openedUri ?: return
        if (_finishing.value != null) return
        // Queued ahead of the signed copy on the lock, so everything placed is in it. Commits
        // already under way (Done on the way out, say) are waited for too, as they may still fail.
        commitStampsAsync(notify = false)
        val pending = commits.toList()
        _signerName.value = name
        signingPrefs.edit().putString(KEY_SIGNER_NAME, name).apply()
        _finishing.value = SignedCopy.Step.Signing
        viewModelScope.launch {
            // A commit that failed outright counts as a failed stamp too.
            if (!pending.all { runCatching { it.await() }.getOrDefault(false) }) {
                _finishing.value = null
                _effects.send(ViewerEffect.Message(R.string.sign_failed_stamp))
                return@launch
            }
            val copy = runCatching {
                lock.withLock {
                    withContext(Dispatchers.IO) {
                        val current = session ?: error("Nothing is open")
                        val now = Instant.now()
                        val documentName = displayName(uri)
                        val trail = AuditTrail(
                            documentName = documentName,
                            originalSha256 = originalSha256,
                            events = buildList {
                                add(AuditEvent(AuditEvent.Type.Opened, name, openedAt))
                                editLog.filterNotNull().forEach { add(it.copy(actor = name)) }
                                add(AuditEvent(AuditEvent.Type.Completed, name, now))
                            },
                        ).withSigner(
                            SignerRecord(
                                name = name,
                                email = null,
                                signedAt = now,
                                method = signatureMethod(),
                                reason = null,
                                device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
                                consentText = consentText,
                            ),
                        )
                        // One device key per name, so its certificate always names whoever is signing.
                        val identity = if (options.seal) {
                            certificates.imported()
                                ?: SigningIdentity.deviceIdentity(name, "freepdf-signing-" + DocumentHash.sha256(name.toByteArray()).take(16))
                        } else {
                            null
                        }
                        val timestamps = TimestampClient().takeIf { options.seal && certificates.timestampsOn }
                        val out = File(context.cacheDir, "signed/${UUID.randomUUID()}.pdf").apply { parentFile?.mkdirs() }
                        val timestamped = out.outputStream().use { output ->
                            SignedCopy.write(
                                current.workingFile, trail, name, identity, output, File(out.path + ".tmp"),
                                password = current.password, lock = options.lock, timestamps = timestamps,
                                onStep = { _finishing.value = it },
                            )
                        }
                        pendingSignedCopy?.delete()
                        pendingSignedCopy = out
                        shareSignedCopy = options.share
                        SignedCopy.suggestedName(documentName) to (timestamps != null && !timestamped)
                    }
                }
            }
            _finishing.value = null
            copy.onSuccess { (suggestedName, timestampMissing) ->
                if (timestampMissing) _effects.send(ViewerEffect.Message(R.string.timestamp_skipped))
                _effects.send(ViewerEffect.SaveSigned(suggestedName))
            }.onFailure { _effects.send(ViewerEffect.Message(R.string.sign_failed)) }
        }
    }

    /**
     * Writes the signed copy to [target] and opens it in place of the original, which is left
     * as it was. Further edits would break the digital signature, so the viewer starts clean.
     */
    fun saveSignedCopy(target: Uri) {
        val copy = pendingSignedCopy ?: return
        pendingSignedCopy = null
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    SafeWrite.write(context, target, copy)
                    // Shared under the name it was saved as, from a copy the share sheet can read.
                    val shared = if (shareSignedCopy) copy.copyTo(Sharing.sharedCopy(context, displayName(target)), overwrite = true) else null
                    copy.delete()
                    shared
                }
            }
            val saved = result.isSuccess
            if (saved) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                open(target)
                _effects.send(ViewerEffect.Message(R.string.signed_copy_saved))
                result.getOrNull()?.let { _effects.send(ViewerEffect.Share(it)) }
            } else {
                _effects.send(ViewerEffect.Message(R.string.save_failed))
            }
        }
    }

    /** How the saved signature (or, failing that, the initials) was made, for the audit page. */
    private fun signatureMethod(): SignatureMethod =
        listOf(SignatureStore.Kind.Signature, SignatureStore.Kind.Initials)
            .firstNotNullOfOrNull { kind ->
                signingPrefs.getString(methodKey(kind), null)?.let { name -> SignatureMethod.entries.firstOrNull { it.name == name } }
            } ?: SignatureMethod.Drawn

    private fun methodKey(kind: SignatureStore.Kind) = "method_${kind.name}"

    fun cancelSignedCopy() {
        pendingSignedCopy?.delete()
        pendingSignedCopy = null
    }

    /**
     * Shares the PDF as it is now, unsaved changes included, in the form [option] asks for.
     * [pages] (zero-based) are the pages to send for [ShareOption.SomePages] and [ShareOption.Images].
     */
    fun share(option: ShareOption = ShareOption.WithChanges, pages: List<Int> = emptyList()) {
        val uri = openedUri ?: return
        viewModelScope.launch {
            val effect = runCatching {
                lock.withLock {
                    val current = session ?: error("Nothing is open")
                    val name = withContext(Dispatchers.IO) { displayName(uri) }
                    when (option) {
                        ShareOption.WithChanges -> withContext(Dispatchers.IO) {
                            ViewerEffect.Share(Sharing.sharedCopy(context, name).also { current.workingFile.copyTo(it, overwrite = true) })
                        }
                        ShareOption.Locked -> sharedPdf(current, name) { Flattener.flatten(it) }
                        ShareOption.SomePages -> {
                            require(pages.isNotEmpty())
                            sharedPdf(current, Splitting.extractName(name, pages)) { PageEditor.keepOnly(it, pages) }
                        }
                        ShareOption.Images -> ViewerEffect.ShareImages(pageImagesLocked(pages, name), name)
                    }
                }
            }.getOrElse { ViewerEffect.Message(R.string.share_failed) }
            _effects.send(effect)
        }
    }

    /**
     * A changed copy of the working file to share as [name]. A locked PDF stays locked with its
     * password. Call with [lock] held.
     */
    private suspend fun sharedPdf(current: EditSession, name: String, change: (PDDocument) -> Unit) =
        withContext(Dispatchers.IO) {
            val out = Sharing.sharedCopy(context, name)
            PDDocument.load(current.workingFile, current.password).use { document ->
                change(document)
                PdfDocuments.keepProtection(document, current.password)
                document.save(out)
            }
            ViewerEffect.Share(out)
        }

    /**
     * Renders [pages] as JPEGs at 150 dpi (enough to read and print a page, small enough for chat
     * apps), named after the document. Call with [lock] held.
     */
    private suspend fun pageImagesLocked(pages: List<Int>, name: String): List<File> {
        require(pages.isNotEmpty() && pages.size <= MAX_SHARED_IMAGES)
        val current = renderer ?: error("Nothing is open")
        val dir = withContext(Dispatchers.IO) { Sharing.sharedFolder(context) }
        val base = Sharing.safeName(name.removeSuffix(".pdf").removeSuffix(".PDF")).ifBlank { "page" }
        return pages.map { index ->
            val widthPx = (current.pageSizes[index].widthPt * 150f / 72f).roundToInt().coerceIn(1, 2400)
            val page = current.renderPage(index, widthPx)
            withContext(Dispatchers.IO) {
                // Pages render onto a transparent bitmap, which JPEG would turn black.
                val image = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                Canvas(image).apply {
                    drawColor(android.graphics.Color.WHITE)
                    drawBitmap(page, 0f, 0f, null)
                }
                page.recycle()
                File(dir, "$base page ${index + 1}.jpg").also { file ->
                    file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    image.recycle()
                }
            }
        }
    }

    /** Asks where to save [pages] as a new PDF; [saveExtract] then writes them. */
    fun extract(pages: List<Int>) {
        val uri = openedUri ?: return
        pendingExtract = pages
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { displayName(uri) }
            _effects.send(ViewerEffect.SaveExtract(Splitting.extractName(name, pages)))
        }
    }

    /** Writes the pages chosen in [extract], unsaved changes included, to [target]. */
    fun saveExtract(target: Uri) {
        val pages = pendingExtract ?: return
        pendingExtract = null
        viewModelScope.launch {
            val saved = runCatching {
                lock.withLock {
                    withContext(Dispatchers.IO) {
                        val current = session ?: error("Nothing is open")
                        SafeWrite.write(context, target) { Splitting.writePart(current.workingFile, pages, it, current.password) }
                    }
                }
            }.isSuccess
            _effects.send(
                if (saved) ViewerEffect.CountMessage(R.plurals.extract_saved, pages.size)
                else ViewerEffect.Message(R.string.extract_failed),
            )
        }
    }

    fun cancelExtract() {
        pendingExtract = null
    }

    /** Asks which folder to split into; [splitInto] then writes one PDF per part. */
    fun split(parts: List<List<Int>>) {
        pendingSplit = parts
        _effects.trySend(ViewerEffect.PickSplitFolder)
    }

    /**
     * Writes each part chosen in [split], unsaved changes included, as a new PDF in the folder
     * [tree] (from the system folder picker), named like "Lease (part 1).pdf".
     */
    fun splitInto(tree: Uri) {
        val parts = pendingSplit ?: return
        val uri = openedUri ?: return
        pendingSplit = null
        viewModelScope.launch {
            val saved = runCatching {
                lock.withLock {
                    withContext(Dispatchers.IO) {
                        val current = session ?: error("Nothing is open")
                        val name = displayName(uri)
                        val resolver = context.contentResolver
                        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                        parts.forEachIndexed { i, pages ->
                            val file = DocumentsContract.createDocument(resolver, folder, "application/pdf", Splitting.partName(name, i + 1))
                                ?: error("Cannot create a file in $tree")
                            SafeWrite.write(context, file) { Splitting.writePart(current.workingFile, pages, it, current.password) }
                        }
                    }
                }
            }.isSuccess
            _effects.send(
                if (saved) ViewerEffect.CountMessage(R.plurals.split_saved, parts.size)
                else ViewerEffect.Message(R.string.split_failed),
            )
        }
    }

    fun cancelSplit() {
        pendingSplit = null
    }

    /** Locks the PDF with [password], or takes its password off when [password] is empty. One undo step. */
    fun setPassword(password: String) {
        val done = when {
            password.isEmpty() -> R.string.password_removed
            (_state.value as? ViewerState.Ready)?.isProtected == true -> R.string.password_changed
            else -> R.string.password_added
        }
        update(done = done) { it.setPassword(password) }
    }

    /** Reads the details of the PDF as it is now, unsaved changes included, for Document info. */
    fun documentInfo() {
        val uri = openedUri ?: return
        viewModelScope.launch {
            val shown: Result<ViewerEffect> = runCatching {
                lock.withLock {
                    withContext(Dispatchers.IO) {
                        val current = session ?: error("Nothing is open")
                        ViewerEffect.ShowInfo(displayName(uri), DocumentInfo.read(current.workingFile, current.password))
                    }
                }
            }
            _effects.send(shown.getOrElse { ViewerEffect.Message(R.string.info_failed) })
        }
    }

    /** Prints the PDF as it is now, unsaved changes included. */
    fun print() {
        val uri = openedUri ?: return
        viewModelScope.launch {
            val effect = runCatching {
                lock.withLock {
                    withContext(Dispatchers.IO) {
                        val current = session ?: error("Nothing is open")
                        val copy = File(context.cacheDir, "print/${UUID.randomUUID()}.pdf")
                        Printing.printableCopy(current.workingFile, copy, current.password)
                        ViewerEffect.Print(copy, displayName(uri), renderer?.pageCount ?: 0)
                    }
                }
            }.getOrElse { ViewerEffect.Message(if (it is Printing.NotAllowed) R.string.print_not_allowed else R.string.print_failed) }
            _effects.send(effect)
        }
    }

    /** Takes back the last stamp still being placed, or else the last edit. */
    fun undo() {
        if (_stamps.value.isNotEmpty()) {
            _stamps.update { it.dropLast(1) }
            return
        }
        viewModelScope.launch {
            lock.withLock {
                withContext(Dispatchers.IO) {
                    session?.takeIf { it.canUndo }?.let {
                        it.undo()
                        // An undone edit that was not logged leaves the log as it is.
                        if (editLog.isNotEmpty()) redoLog += editLog.removeAt(editLog.lastIndex)
                    }
                }
                // The undone edit may have moved pages around.
                signFields = null
                _state.value = reloadOrFail()
            }
        }
    }

    fun redo() {
        viewModelScope.launch {
            lock.withLock {
                withContext(Dispatchers.IO) {
                    session?.takeIf { it.canRedo }?.let {
                        it.redo()
                        if (redoLog.isNotEmpty()) editLog += redoLog.removeAt(redoLog.lastIndex)
                    }
                }
                signFields = null
                _state.value = reloadOrFail()
            }
        }
    }

    /**
     * Saves over the opened file, or asks for a new location when that file is read-only.
     * [document] names another open document to save instead, from the switcher; null, or the
     * one on screen, saves this one.
     */
    fun save(thenClose: Boolean = false, document: String? = null) {
        val other = document?.takeIf { it != doc?.key }?.let { sessions.get(it) ?: return }
        val target = other ?: doc ?: return
        savingOther = other
        closeAfterSave = thenClose
        viewModelScope.launch {
            val uri = target.uri
            val saved = lock.withLock { runCatching { writeLocked(uri, target) }.isSuccess }
            if (saved) finishSave() else _effects.send(ViewerEffect.SaveAs(displayName(uri)))
        }
    }

    /** Called with the location the user picked after [ViewerEffect.SaveAs]. */
    fun saveAs(target: Uri) {
        val saving = savingOther ?: doc ?: return
        viewModelScope.launch {
            val saved = lock.withLock { runCatching { writeLocked(target, saving) }.isSuccess }
            if (saved) {
                // Later saves go to the new copy, which the user can write to.
                saving.uri = target
                val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
                runCatching { context.contentResolver.takePersistableUriPermission(target, read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    .recoverCatching { context.contentResolver.takePersistableUriPermission(target, read) }
                if (saving === doc) remember(target)
                finishSave()
            } else {
                closeAfterSave = false
                savingOther = null
                _effects.send(ViewerEffect.Message(R.string.save_failed))
            }
        }
    }

    fun cancelSaveAs() {
        closeAfterSave = false
        savingOther = null
    }

    private suspend fun finishSave() {
        _effects.send(ViewerEffect.Message(R.string.saved))
        if (savingOther == null) {
            (_state.value as? ViewerState.Ready)?.let { _state.value = it.copy(hasUnsavedChanges = false) }
        }
        sessions.refresh()
        if (closeAfterSave) _effects.send(ViewerEffect.Close)
        closeAfterSave = false
        savingOther = null
    }

    private suspend fun writeLocked(uri: Uri, document: DocumentSession) = withContext(Dispatchers.IO) {
        // The working copy is complete, so it goes out as a whole; only a finished write counts as saved.
        SafeWrite.write(context, uri, document.session.workingFile)
        document.session.markSaved()
    }

    /** Thrown from an edit that turns out to have nothing to do, so no undo step is recorded. */
    private class NothingChanged : Exception()

    /**
     * Applies [change] as one undo step. [event] goes on the audit page if the user finishes signing.
     * [movesPages] says the places to sign must be found again.
     */
    private fun edit(
        @StringRes onNoChange: Int? = null,
        event: AuditEvent? = null,
        movesPages: Boolean = false,
        change: (PDDocument) -> Unit,
    ) = update(onNoChange, event, movesPages = movesPages) { it.edit(change) }

    /**
     * [edit] for a change that only adds, alters or removes marks, so the pages' text is as it
     * was and the words already read from them need not be read again.
     */
    private fun mark(@StringRes onNoChange: Int? = null, change: (PDDocument) -> Unit) =
        update(onNoChange, keepsText = true) { it.edit(change) }

    /** Makes one undo step through [step], like [edit] does, then shows [done] if given. */
    private fun update(
        @StringRes onNoChange: Int? = null,
        event: AuditEvent? = null,
        @StringRes done: Int? = null,
        movesPages: Boolean = false,
        keepsText: Boolean = false,
        step: (EditSession) -> Unit,
    ) {
        viewModelScope.launch {
            lock.withLock {
                val result = runCatching { withContext(Dispatchers.IO) { session?.let(step) } }
                when (val error = result.exceptionOrNull()) {
                    null -> {
                        editLog += event?.copy(at = Instant.now())
                        redoLog.clear()
                        if (movesPages) signFields = null
                        _state.value = reloadOrFail(pageEdit = movesPages, keepText = keepsText)
                        done?.let { _effects.send(ViewerEffect.Message(it)) }
                    }
                    is NothingChanged -> {
                        if (movesPages) failedPageEdit()
                        onNoChange?.let { _effects.send(ViewerEffect.Message(it)) }
                    }
                    else -> {
                        if (movesPages) failedPageEdit()
                        _effects.send(ViewerEffect.Message(R.string.edit_failed))
                    }
                }
            }
        }
    }

    /** Notes a page edit that left the document as it was, so the screen's selection and drag order go back to it. */
    private fun failedPageEdit() {
        failedPageEdits++
        _state.update { if (it is ViewerState.Ready) it.copy(failedPageEdits = failedPageEdits) else it }
    }

    /** Maps display fractions on [page] (see [AnnotationLayer]) to PDF space. */
    private fun displayMapper(document: PDDocument, page: Int): (Offset) -> PdfPoint {
        val pdfPage = document.getPage(page)
        val crop = pdfPage.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
        return { displayToPdf(it.x, it.y, pdfPage.rotation, crop) }
    }

    private fun boxOf(document: PDDocument, page: Int, start: Offset, end: Offset): PdfRect {
        val toPdf = displayMapper(document, page)
        val a = toPdf(start)
        val b = toPdf(end)
        return PdfRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
    }

    /** Loads a newly opened PDF, or asks for its password if it is locked. Call with [lock] held. */
    private suspend fun firstLoadLocked(): ViewerState = try {
        reloadLocked()
    } catch (e: Exception) {
        // PDFium only says it could not open the file; PdfBox can tell whether a password is why.
        val locked = session?.let { withContext(Dispatchers.IO) { runCatching { it.needsPassword() }.getOrDefault(false) } }
        if (locked == true) ViewerState.Locked() else throw e
    }

    /**
     * [reloadLocked], but a working copy PDFium refuses (a merged file it cannot parse, say) is
     * taken back with undo instead of crashing the app from inside a coroutine. [pageEdit] says
     * the edit being shown moved pages, so taking it back counts as a failed page edit. [keepText]
     * is passed on to [reloadLocked]. Call with [lock] held.
     */
    private suspend fun reloadOrFail(pageEdit: Boolean = false, keepText: Boolean = false): ViewerState =
        runCatching { reloadLocked(keepText) }.getOrElse { first ->
            val current = session
            if (current?.canUndo == true) {
                runCatching {
                    withContext(Dispatchers.IO) { current.undo() }
                    if (editLog.isNotEmpty()) editLog.removeAt(editLog.lastIndex)
                    if (pageEdit) failedPageEdits++
                    _effects.send(ViewerEffect.Message(R.string.edit_failed))
                    reloadLocked()
                }.getOrElse { ViewerState.Failed(first.message) }
            } else {
                ViewerState.Failed(first.message)
            }
        }

    /**
     * Re-opens the working copy in PDFium. Call with [lock] held. [keepText] is true after an edit
     * that left the pages' text alone, so the words already read from them are still good.
     */
    private suspend fun reloadLocked(keepText: Boolean = false): ViewerState {
        val current = session ?: return ViewerState.Failed(null)
        renderer?.close()
        renderer = null
        cache.evictAll()
        textDocument?.close()
        textDocument = null
        if (!keepText) wordCache.clear()
        searchJob?.cancel()
        _search.value = SearchResults()
        revision++
        val next = PdfRenderer.open(context, Uri.fromFile(current.workingFile), current.password.ifEmpty { null })
        _marks.value = withContext(Dispatchers.IO) {
            runCatching {
                PDDocument.load(current.workingFile, current.password).use { document ->
                    // Only pages with marked text are asked for, and each is read at most once per text change.
                    Marks.list(document) { page ->
                        wordCache.getOrPut(page) { runCatching { PageText.words(document, page) }.getOrDefault(emptyList()) }
                    }
                }
            }.getOrDefault(emptyList())
        }
        renderer = next
        sessions.refresh()
        val hasSignature = editLog.any { it?.type == AuditEvent.Type.Signed }
        // A form that PdfBox cannot read just offers nothing to fill. The signatures placed so far
        // are read from the pages too, so they follow the pages they are on through moves,
        // deletions, rotations and undo.
        val (formFields, placedSignatures, links) = withContext(Dispatchers.IO) {
            runCatching {
                PDDocument.load(current.workingFile, current.password).use {
                    Triple(FormFiller.fields(it), SignatureAnnotation.placed(it), PageLinks.read(it))
                }
            }.getOrDefault(Triple(emptyList<FormField>(), emptyList<SignatureAnnotation.Placed>(), emptyList<PageLink>()))
        }
        val outline = runCatching { next.outline() }.getOrDefault(emptyList())
        // Likewise, a document whose text cannot be read just has no places to sign.
        val places = signFields ?: withContext(Dispatchers.IO) {
            runCatching { PDDocument.load(current.workingFile, current.password).use { SignatureFields.find(it) } }.getOrDefault(emptyList())
        }.also { signFields = it }
        val signed = places.indices.filter { i -> placedSignatures.any { places[i].covers(it.page, it.x, it.y) } }.toSet()
        return ViewerState.Ready(
            next.pageSizes, revision, current.canUndo, current.canRedo, current.hasUnsavedChanges, hasSignature, outline,
            isProtected = current.password.isNotEmpty(), formFields = formFields, signFields = places, signedFields = signed,
            signatures = signatures, failedPageEdits = failedPageEdits, links = links,
        )
    }

    /**
     * Lists [uri] in the Files tab; in the history too if the app can reopen it later and history
     * is not paused in Settings.
     */
    private suspend fun remember(uri: Uri) {
        val name = withContext(Dispatchers.IO) { displayName(uri) }
        val lasting = Documents.lasting(uri, context.filesDir) {
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        }
        val app = getApplication<FreePdfApp>()
        app.documents.opened(uri.toString(), name, remember = lasting && app.settings.rememberHistory.value)
    }

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
        return name?.takeIf { it.isNotBlank() } ?: "document.pdf"
    }

    override fun onCleared() {
        // The session stays with the application for the next visit; only this screen's
        // renderer and bitmaps go, so a document that is not on screen holds no PDFium memory.
        detachLocked()
        renderer?.close()
        renderer = null
        textDocument?.close()
        cache.evictAll()
        pendingSignedCopy?.delete()
    }

    /** The width of one line of text in ems, as Helvetica sets it; a rough guess for other scripts. */
    private fun emWidth(line: String): Float =
        runCatching { PDType1Font.HELVETICA.getStringWidth(line) / 1000f }.getOrElse { line.length * 0.55f }

    private companion object {
        // A quarter of the heap, capped: bitmaps are the bulk of the app's memory, and a fixed
        // 96 MB is more than low-end phones with a 128 MB heap can give without an OutOfMemoryError.
        val CACHE_BYTES = (Runtime.getRuntime().maxMemory() / 4).coerceAtMost(96L * 1024 * 1024).toInt()
        const val KEY_SIGNER_NAME = "name"

        // Who placed things is only known at Finish, where this is replaced by the typed name.
        const val SIGNER = "signer"
    }
}
