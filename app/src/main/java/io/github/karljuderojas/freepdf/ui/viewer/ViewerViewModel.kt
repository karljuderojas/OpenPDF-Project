package io.github.karljuderojas.freepdf.ui.viewer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
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
import io.github.karljuderojas.freepdf.pdf.render.PageSize
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
     */
    data class Ready(
        val pageSizes: List<PageSize>,
        val revision: Int = 0,
        val canUndo: Boolean = false,
        val hasUnsavedChanges: Boolean = false,
        val hasSignature: Boolean = false,
    ) : ViewerState

    /** The PDF is password protected; [wrongPassword] after a password that did not open it. */
    data class Locked(val wrongPassword: Boolean = false) : ViewerState

    data class Failed(val message: String?) : ViewerState
}

/** One-off things the screen does for the view model: messages, the Save As picker, leaving. */
sealed interface ViewerEffect {
    data class Message(@StringRes val text: Int) : ViewerEffect
    data class SaveAs(val suggestedName: String) : ViewerEffect
    data object Close : ViewerEffect
    data class Share(val file: File) : ViewerEffect

    /** Open the print dialog for [file], a printable copy named [name]. */
    data class Print(val file: File, val name: String, val pageCount: Int) : ViewerEffect

    /** Ask where to save the signed copy; see [ViewerViewModel.saveSignedCopy]. */
    data class SaveSigned(val suggestedName: String) : ViewerEffect
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

    /** The finished signed copy, in the cache, waiting for the user to pick where it goes. */
    private var pendingSignedCopy: File? = null

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

    private val signatureStore = SignatureStore(File(application.filesDir, "signatures"))
    private val _savedSignatures = MutableStateFlow<Map<SignatureStore.Kind, Bitmap>>(emptyMap())

    /** The user's saved signature and initials, if they have drawn them. */
    val savedSignatures: StateFlow<Map<SignatureStore.Kind, Bitmap>> = _savedSignatures.asStateFlow()

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
                    }
                    firstLoadLocked()
                }
            }.onSuccess { remember(uri) }.getOrElse { ViewerState.Failed(it.message) }
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

    fun ink(page: Int, strokes: List<List<Offset>>, color: Annotator.Rgb) = edit { document ->
        val toPdf = displayMapper(document, page)
        Annotator.ink(document, page, strokes.map { stroke -> stroke.map(toPdf) }, color)
    }

    fun markText(page: Int, start: Offset, end: Offset, kind: Annotator.TextMarkup, color: Annotator.Rgb) =
        edit { document ->
            Annotator.markText(document, page, listOf(boxOf(document, page, start, end)), kind, color)
        }

    fun shape(page: Int, start: Offset, end: Offset, color: Annotator.Rgb) = edit { document ->
        Annotator.shape(document, page, boxOf(document, page, start, end), color = color)
    }

    fun note(page: Int, at: Offset, text: String) = edit { document ->
        Annotator.note(document, page, displayMapper(document, page)(at), text)
    }

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

    fun saveSignature(kind: SignatureStore.Kind, image: Bitmap) {
        _savedSignatures.value += kind to image
        viewModelScope.launch(Dispatchers.IO) {
            // Still usable in this session if the Keystore refuses; it just is not remembered.
            runCatching { signatureStore.save(kind, image) }
        }
    }

    /** Stamps the saved signature or initials so they sit on the line the user tapped. */
    fun placeSignature(page: Int, at: Offset, kind: SignatureStore.Kind) {
        val image = _savedSignatures.value[kind] ?: return
        val what = if (kind == SignatureStore.Kind.Initials) "initials" else "signature"
        edit(event = AuditEvent(AuditEvent.Type.Signed, SIGNER, detail = "$what on page ${page + 1}")) { document ->
            val point = displayMapper(document, page)(at)
            val maxWidth = if (kind == SignatureStore.Kind.Initials) 60f else 160f
            val maxHeight = if (kind == SignatureStore.Kind.Initials) 32f else 56f
            SignatureStamper.stamp(document, page, image, point, maxWidth, maxHeight)
        }
    }

    fun addDate(page: Int, at: Offset) {
        // The phone's own date format, unless it uses a script the bundled fonts cannot show
        // (Arabic, Devanagari, CJK), in which case the English form is placed instead of "?".
        val today = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()).takeIf { PdfText.isLatinGreekOrCyrillic(it) }
            ?: DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US).format(Date())
        placeText(page, at, today, "date")
    }

    fun addText(page: Int, at: Offset, text: String) = placeText(page, at, text, "text")

    private fun placeText(page: Int, at: Offset, text: String, what: String) = edit(event = filled(what, page)) { document ->
        PageEditor.addText(document, page, text, displayMapper(document, page)(at), fontSize = 11f)
    }

    fun addCheckmark(page: Int, at: Offset) = edit(event = filled("checkmark", page)) { document ->
        PageEditor.addCheckmark(document, page, displayMapper(document, page)(at))
    }

    private fun filled(what: String, page: Int) =
        AuditEvent(AuditEvent.Type.FieldFilled, SIGNER, detail = "$what on page ${page + 1}")

    /**
     * Builds the signed copy: everything placed so far, a signing certificate page naming
     * [name] with the [consentText] they agreed to, and, if [seal], a digital signature from a
     * key kept in this phone's Keystore. The user then picks where to save it.
     */
    fun finishSigning(name: String, consentText: String, seal: Boolean) {
        val uri = openedUri ?: return
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
                                method = SignatureMethod.Drawn,
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

    fun undo() {
        viewModelScope.launch {
            lock.withLock {
                withContext(Dispatchers.IO) {
                    session?.takeIf { it.canUndo }?.let {
                        it.undo()
                        editLog.removeLastOrNull()
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
    private fun edit(@StringRes onNoChange: Int? = null, event: AuditEvent? = null, change: (PDDocument) -> Unit) {
        viewModelScope.launch {
            lock.withLock {
                val result = runCatching { withContext(Dispatchers.IO) { session?.edit(change) } }
                when (val error = result.exceptionOrNull()) {
                    null -> {
                        editLog += event?.copy(at = Instant.now())
                        _state.value = reloadLocked()
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
        revision++
        val next = PdfRenderer.open(context, Uri.fromFile(current.workingFile), current.password.ifEmpty { null })
        renderer = next
        val hasSignature = editLog.any { it?.type == AuditEvent.Type.Signed }
        return ViewerState.Ready(next.pageSizes, revision, current.canUndo, current.hasUnsavedChanges, hasSignature)
    }

    /** Lists [uri] in the Files tab; in the history too if the app can reopen it later. */
    private suspend fun remember(uri: Uri) {
        val name = withContext(Dispatchers.IO) { displayName(uri) }
        val lasting = uri.scheme == "file" ||
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        getApplication<FreePdfApp>().documents.opened(uri.toString(), name, remember = lasting)
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
        session?.close()
        cache.evictAll()
        pendingSignedCopy?.delete()
    }

    private companion object {
        // A quarter of the heap, capped: bitmaps are the bulk of the app's memory, and a fixed
        // 96 MB is more than low-end phones with a 128 MB heap can give without an OutOfMemoryError.
        val CACHE_BYTES = (Runtime.getRuntime().maxMemory() / 4).coerceAtMost(96L * 1024 * 1024).toInt()
        const val KEY_SIGNER_NAME = "name"

        // Who placed things is only known at Finish, where this is replaced by the typed name.
        const val SIGNER = "signer"
    }
}
