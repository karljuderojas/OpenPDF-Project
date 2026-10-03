package io.github.karljuderojas.freepdf.ui.viewer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import io.github.karljuderojas.freepdf.FreePdfApp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.edit.EditSession
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor
import io.github.karljuderojas.freepdf.pdf.edit.PdfDocuments
import io.github.karljuderojas.freepdf.pdf.edit.PdfText
import io.github.karljuderojas.freepdf.pdf.edit.Splitting
import io.github.karljuderojas.freepdf.pdf.info.DocumentInfo
import io.github.karljuderojas.freepdf.pdf.render.OutlineItem
import io.github.karljuderojas.freepdf.pdf.render.PageBox
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.text.PageText
import io.github.karljuderojas.freepdf.pdf.text.PageWord
import io.github.karljuderojas.freepdf.pdf.render.PdfRenderer
import io.github.karljuderojas.freepdf.pdf.sign.AuditEvent
import io.github.karljuderojas.freepdf.pdf.sign.AuditTrail
import io.github.karljuderojas.freepdf.pdf.sign.DocumentHash
import io.github.karljuderojas.freepdf.pdf.sign.SignatureMethod
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStamper
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.pdf.sign.SignedCopy
import io.github.karljuderojas.freepdf.pdf.sign.SignerRecord
import io.github.karljuderojas.freepdf.pdf.sign.SigningIdentity
import io.github.karljuderojas.freepdf.print.Printing
import io.github.karljuderojas.freepdf.share.Sharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface ViewerState {
    data object Loading : ViewerState

    /**
     * [revision] changes after every edit, so pages already on screen are rendered again.
     * [hasSignature] is true once a signature or initials are placed, which offers Finish.
     * [isProtected] is true while the PDF needs a password to open.
     * [outline] is the PDF's table of contents, empty if it has none.
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
    private var session: EditSession? = null
    private var openedUri: Uri? = null
    private var revision = 0
    private var closeAfterSave = false

    // For the audit page: the original's fingerprint, when it was opened, and one entry per
    // edit (null for edits that are not part of signing), so undo can drop the matching entry.
    private var originalSha256 = ""
    private var openedAt = Instant.now()
    private val editLog = ArrayList<AuditEvent?>()
    private val redoLog = ArrayList<AuditEvent?>()

    /** The finished signed copy, in the cache, waiting for the user to pick where it goes. */
    private var pendingSignedCopy: File? = null

    /** Pages to extract, or the parts to split into, waiting for the user to pick where they go. */
    private var pendingExtract: List<Int>? = null
    private var pendingSplit: List<List<Int>>? = null

    private val signingPrefs = application.getSharedPreferences("signing", Context.MODE_PRIVATE)
    private val _signerName = MutableStateFlow(signingPrefs.getString(KEY_SIGNER_NAME, "").orEmpty())

    /** The name typed at the last Finish, to fill in next time. */
    val signerName: StateFlow<String> = _signerName.asStateFlow()

    // Guards the renderer and the working copy: PDFium renders one page at a time, and an edit
    // swaps both the file and the renderer underneath any page that is mid-render.
    private val lock = Mutex()

    // Roughly a dozen screen-sized pages; bitmaps are the bulk of the app's memory.
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val context get() = getApplication<Application>()

    // Text positions per page for the current revision, read with PdfBox from the working copy.
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

    // Stamps already on their way into the PDF, so a second commit does not draw them twice.
    private var committing = emptySet<Long>()

    /** Sign-mode stamps that can still be moved or resized; see [commitStamps]. */
    val stamps: StateFlow<List<PlacedStamp>> = _stamps.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _savedSignatures.value = SignatureStore.Kind.entries.mapNotNull { kind ->
                runCatching { signatureStore.load(kind) }.getOrNull()?.let { kind to it }
            }.toMap()
        }
    }

    fun open(uri: Uri) {
        if (uri == openedUri) return
        openedUri = uri
        _state.value = ViewerState.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                lock.withLock {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read $uri")
                        val dir = File(context.cacheDir, "edit/${UUID.randomUUID()}")
                        val newSession = input.use { EditSession(dir, it) }
                        session?.close()
                        session = newSession
                        originalSha256 = newSession.workingFile.inputStream().use { DocumentHash.sha256(it) }
                        openedAt = Instant.now()
                        editLog.clear()
                        redoLog.clear()
                    }
                    _stamps.value = emptyList()
                    firstLoadLocked()
                }
            }.onSuccess { remember(uri) }.getOrElse { ViewerState.Failed(it.message) }
        }
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
            cache.get(key) ?: current.renderPage(index, widthPx).also { cache.put(key, it) }
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

    fun rotatePage(index: Int) = edit { PageEditor.rotate(it, index, 90) }

    fun deletePage(index: Int) {
        if ((_state.value as? ViewerState.Ready)?.pageSizes?.size == 1) {
            _effects.trySend(ViewerEffect.Message(R.string.cannot_delete_last_page))
            return
        }
        edit { PageEditor.delete(it, index) }
    }

    fun insertBlankPage(afterIndex: Int) = edit { document ->
        val neighbour = document.getPage(afterIndex)
        PageEditor.insertBlank(document, afterIndex + 1, neighbour.mediaBox)
        // A blank next to a rotated scan should face the same way as its neighbour.
        document.getPage(afterIndex + 1).rotation = neighbour.rotation
    }

    fun movePage(from: Int, to: Int) = edit { PageEditor.move(it, from, to) }

    /** Appends every page of [other] to the end of the open document. */
    fun merge(other: Uri) = edit { document ->
        PdfDocuments.load(context, other).use { PageEditor.append(document, it) }
    }

    fun ink(page: Int, strokes: List<List<Offset>>, style: ToolStyle) = edit { document ->
        val toPdf = displayMapper(document, page)
        Annotator.ink(document, page, strokes.map { stroke -> stroke.map(toPdf) }, style.rgb, style.width)
    }

    fun markText(page: Int, start: Offset, end: Offset, kind: Annotator.TextMarkup, style: ToolStyle) =
        edit { document ->
            Annotator.markText(document, page, listOf(boxOf(document, page, start, end)), kind, style.rgb, style.width)
        }

    /**
     * Marks text along [lines] (one box per line, as page fractions; see [PageText]). [comment]
     * becomes the mark's note.
     */
    fun markLines(page: Int, lines: List<Rect>, kind: Annotator.TextMarkup, style: ToolStyle, comment: String? = null) =
        edit { document ->
            val boxes = lines.map { boxOf(document, page, it.topLeft, it.bottomRight) }
            Annotator.markText(document, page, boxes, kind, style.rgb, style.width, comment)
        }

    /** The words on [page] and where they are, for selecting text. Empty for scanned pages. */
    suspend fun words(page: Int): List<PageWord> = lock.withLock {
        wordCache[page] ?: withContext(Dispatchers.IO) {
            runCatching {
                val document = textDocument ?: (session?.let { PDDocument.load(it.workingFile, it.password) } ?: error("Nothing is open")).also { textDocument = it }
                PageText.words(document, page)
            }.getOrDefault(emptyList())
        }.also { wordCache[page] = it }
    }

    fun shape(page: Int, start: Offset, end: Offset, style: ToolStyle) = edit { document ->
        Annotator.shape(document, page, boxOf(document, page, start, end), color = style.rgb, lineWidth = style.width)
    }

    fun note(page: Int, at: Offset, text: String, style: ToolStyle) = edit { document ->
        Annotator.note(document, page, displayMapper(document, page)(at), text, style.rgb)
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

    /** Removes the topmost mark under [at]. Links and form fields are left alone. */
    fun erase(page: Int, at: Offset) {
        edit(onNoChange = R.string.nothing_to_erase) { document ->
            val point = displayMapper(document, page)(at)
            val pdfPage = document.getPage(page)
            val annotations = pdfPage.annotations
            val target = annotations.lastOrNull { annotation ->
                annotation !is PDAnnotationLink && annotation !is PDAnnotationWidget &&
                    annotation.rectangle?.contains(point.x, point.y) == true
            } ?: throw NothingChanged()
            pdfPage.annotations = annotations.filter { it !== target }
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

    fun addDate(page: Int, at: Offset) {
        // The phone's own date format, unless it uses a script the bundled fonts cannot show
        // (Arabic, Devanagari, CJK), in which case the English form is placed instead of "?".
        val today = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()).takeIf { PdfText.isLatinGreekOrCyrillic(it) }
            ?: DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US).format(Date())
        placeText(page, at, today, "date")
    }

    fun addText(page: Int, at: Offset, text: String) = placeText(page, at, text, "text")

    private fun placeText(page: Int, at: Offset, text: String, what: String) {
        val size = pageSize(page) ?: return
        val content = StampContent.Text(text, what)
        addStamp(page, content, StampGeometry.textBox(at, content.lines, size, ::emWidth))
    }

    fun addCheckmark(page: Int, at: Offset) {
        val size = pageSize(page) ?: return
        addStamp(page, StampContent.Checkmark, StampGeometry.checkmarkBox(at, size))
    }

    fun moveStamp(id: Long, dx: Float, dy: Float) = updateStamp(id) { it.copy(box = it.box.moved(dx, dy)) }

    fun resizeStamp(id: Long, factor: Float) = updateStamp(id) { stamp ->
        pageSize(stamp.page)?.let { stamp.copy(box = stamp.box.scaled(factor, it)) } ?: stamp
    }

    fun deleteStamp(id: Long) = _stamps.update { stamps -> stamps.filter { it.id != id } }

    /**
     * Writes every placed stamp into the PDF where it now sits, one undo step each. Called on
     * leaving Sign mode and before finishing, after which they are part of the page.
     */
    fun commitStamps() {
        val placed = _stamps.value.filter { it.id !in committing }
        if (placed.isEmpty()) return
        committing = committing + placed.map { it.id }
        viewModelScope.launch {
            lock.withLock {
                var failed = false
                withContext(Dispatchers.IO) {
                    val current = session ?: return@withContext
                    placed.forEach { stamp ->
                        runCatching { current.edit { document -> drawStamp(document, stamp) } }
                            .onSuccess { editLog += auditEventFor(stamp).copy(at = Instant.now()) }
                            .onFailure { failed = true }
                    }
                }
                _state.value = reloadLocked()
                // Taken off only now, so they stay on screen until the page shows them drawn in.
                val done = placed.map { it.id }.toSet()
                _stamps.update { stamps -> stamps.filter { it.id !in done } }
                committing = committing - done
                if (failed) _effects.send(ViewerEffect.Message(R.string.edit_failed))
            }
        }
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
            is StampContent.Signature -> SignatureStamper.stamp(
                document, stamp.page, content.image, toPdf(StampGeometry.signatureAnchor(box)),
                maxWidth = box.width * size.widthPt, maxHeight = box.height * size.heightPt,
            )
            is StampContent.Text -> {
                val fontSize = StampGeometry.fontSize(box, content.lines.size, size)
                PageEditor.addText(document, stamp.page, content.text, toPdf(StampGeometry.textAnchor(box, fontSize, size)), fontSize)
            }
            StampContent.Checkmark -> PageEditor.addCheckmark(
                document, stamp.page, toPdf(StampGeometry.checkmarkAnchor(box, size)), StampGeometry.checkmarkSize(box, size),
            )
        }
    }

    private fun auditEventFor(stamp: PlacedStamp): AuditEvent {
        val where = "on page ${stamp.page + 1}"
        return when (val content = stamp.content) {
            is StampContent.Signature -> {
                val what = if (content.kind == SignatureStore.Kind.Initials) "initials" else "signature"
                AuditEvent(AuditEvent.Type.Signed, SIGNER, detail = "$what $where")
            }
            is StampContent.Text -> AuditEvent(AuditEvent.Type.FieldFilled, SIGNER, detail = "${content.what} $where")
            StampContent.Checkmark -> AuditEvent(AuditEvent.Type.FieldFilled, SIGNER, detail = "checkmark $where")
        }
    }

    /** The page's size in points as it is shown, that is turned by its /Rotate. */
    private fun displaySize(document: PDDocument, page: Int): PageSize {
        val pdfPage = document.getPage(page)
        val crop = pdfPage.cropBox
        return if (pdfPage.rotation % 180 == 0) PageSize(crop.width, crop.height) else PageSize(crop.height, crop.width)
    }

    /**
     * Builds the signed copy: everything placed so far, a signing certificate page naming
     * [name] with the [consentText] they agreed to, and, if [seal], a digital signature from a
     * key kept in this phone's Keystore. The user then picks where to save it.
     */
    fun finishSigning(name: String, consentText: String, seal: Boolean) {
        val uri = openedUri ?: return
        // Queued ahead of the signed copy on the lock, so everything placed is in it.
        commitStamps()
        _signerName.value = name
        signingPrefs.edit().putString(KEY_SIGNER_NAME, name).apply()
        viewModelScope.launch {
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
                        // One key per name, so the certificate always names whoever is signing.
                        val identity = if (seal) {
                            SigningIdentity.deviceIdentity(name, "freepdf-signing-" + DocumentHash.sha256(name.toByteArray()).take(16))
                        } else {
                            null
                        }
                        val out = File(context.cacheDir, "signed/${UUID.randomUUID()}.pdf").apply { parentFile?.mkdirs() }
                        out.outputStream().use { output ->
                            SignedCopy.write(current.workingFile, trail, name, identity, output, File(out.path + ".tmp"), current.password)
                        }
                        pendingSignedCopy?.delete()
                        pendingSignedCopy = out
                        SignedCopy.suggestedName(documentName)
                    }
                }
            }
            copy.onSuccess { _effects.send(ViewerEffect.SaveSigned(it)) }
                .onFailure { _effects.send(ViewerEffect.Message(R.string.sign_failed)) }
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
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(target, "wt") ?: error("Cannot write $target")
                    output.use { out -> copy.inputStream().use { it.copyTo(out) } }
                    copy.delete()
                }
            }.isSuccess
            if (saved) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                open(target)
                _effects.send(ViewerEffect.Message(R.string.signed_copy_saved))
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

    /** Shares the PDF as it is now, unsaved changes included, under the file's own name. */
    fun share() {
        val uri = openedUri ?: return
        viewModelScope.launch {
            val copy = runCatching {
                lock.withLock {
                    withContext(Dispatchers.IO) {
                        val current = session ?: error("Nothing is open")
                        Sharing.sharedCopy(context, displayName(uri)).also { current.workingFile.copyTo(it, overwrite = true) }
                    }
                }
            }
            copy.onSuccess { _effects.send(ViewerEffect.Share(it)) }
                .onFailure { _effects.send(ViewerEffect.Message(R.string.share_failed)) }
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
                        val output = context.contentResolver.openOutputStream(target, "wt") ?: error("Cannot write $target")
                        output.use { Splitting.writePart(current.workingFile, pages, it, current.password) }
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
                            val output = resolver.openOutputStream(file, "wt") ?: error("Cannot write $file")
                            output.use { Splitting.writePart(current.workingFile, pages, it, current.password) }
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
                _state.value = reloadLocked()
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
                _state.value = reloadLocked()
            }
        }
    }

    /** Saves over the opened file, or asks for a new location when that file is read-only. */
    fun save(thenClose: Boolean = false) {
        val uri = openedUri ?: return
        closeAfterSave = thenClose
        viewModelScope.launch {
            val saved = lock.withLock { runCatching { writeLocked(uri) }.isSuccess }
            if (saved) finishSave() else _effects.send(ViewerEffect.SaveAs(displayName(uri)))
        }
    }

    /** Called with the location the user picked after [ViewerEffect.SaveAs]. */
    fun saveAs(target: Uri) {
        viewModelScope.launch {
            val saved = lock.withLock { runCatching { writeLocked(target) }.isSuccess }
            if (saved) {
                // Later saves go to the new copy, which the user can write to.
                openedUri = target
                val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
                runCatching { context.contentResolver.takePersistableUriPermission(target, read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    .recoverCatching { context.contentResolver.takePersistableUriPermission(target, read) }
                remember(target)
                finishSave()
            } else {
                closeAfterSave = false
                _effects.send(ViewerEffect.Message(R.string.save_failed))
            }
        }
    }

    fun cancelSaveAs() {
        closeAfterSave = false
    }

    private suspend fun finishSave() {
        _effects.send(ViewerEffect.Message(R.string.saved))
        (_state.value as? ViewerState.Ready)?.let { _state.value = it.copy(hasUnsavedChanges = false) }
        if (closeAfterSave) _effects.send(ViewerEffect.Close)
        closeAfterSave = false
    }

    private suspend fun writeLocked(uri: Uri) = withContext(Dispatchers.IO) {
        val current = session ?: error("Nothing is open")
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write $uri")
        output.use { current.writeTo(it) }
    }

    /** Thrown from an edit that turns out to have nothing to do, so no undo step is recorded. */
    private class NothingChanged : Exception()

    /** Applies [change] as one undo step. [event] goes on the audit page if the user finishes signing. */
    private fun edit(@StringRes onNoChange: Int? = null, event: AuditEvent? = null, change: (PDDocument) -> Unit) =
        update(onNoChange, event) { it.edit(change) }

    /** Makes one undo step through [step], like [edit] does, then shows [done] if given. */
    private fun update(
        @StringRes onNoChange: Int? = null,
        event: AuditEvent? = null,
        @StringRes done: Int? = null,
        step: (EditSession) -> Unit,
    ) {
        viewModelScope.launch {
            lock.withLock {
                val result = runCatching { withContext(Dispatchers.IO) { session?.let(step) } }
                when (val error = result.exceptionOrNull()) {
                    null -> {
                        editLog += event?.copy(at = Instant.now())
                        redoLog.clear()
                        _state.value = reloadLocked()
                        done?.let { _effects.send(ViewerEffect.Message(it)) }
                    }
                    is NothingChanged -> onNoChange?.let { _effects.send(ViewerEffect.Message(it)) }
                    else -> _effects.send(ViewerEffect.Message(R.string.edit_failed))
                }
            }
        }
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

    /** Re-opens the working copy in PDFium. Call with [lock] held. */
    private suspend fun reloadLocked(): ViewerState {
        val current = session ?: return ViewerState.Failed(null)
        renderer?.close()
        renderer = null
        cache.evictAll()
        textDocument?.close()
        textDocument = null
        wordCache.clear()
        searchJob?.cancel()
        _search.value = SearchResults()
        revision++
        val next = PdfRenderer.open(context, Uri.fromFile(current.workingFile), current.password.ifEmpty { null })
        renderer = next
        val hasSignature = editLog.any { it?.type == AuditEvent.Type.Signed }
        val outline = runCatching { next.outline() }.getOrDefault(emptyList())
        return ViewerState.Ready(
            next.pageSizes, revision, current.canUndo, current.canRedo, current.hasUnsavedChanges, hasSignature, outline,
            isProtected = current.password.isNotEmpty(),
        )
    }

    /**
     * Lists [uri] in the Files tab; in the history too if the app can reopen it later and history
     * is not paused in Settings.
     */
    private suspend fun remember(uri: Uri) {
        val name = withContext(Dispatchers.IO) { displayName(uri) }
        val lasting = uri.scheme == "file" ||
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
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
        renderer?.close()
        textDocument?.close()
        session?.close()
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
