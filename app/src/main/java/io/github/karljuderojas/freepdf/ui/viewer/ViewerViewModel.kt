package io.github.karljuderojas.freepdf.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import androidx.compose.ui.geometry.Offset
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.edit.EditSession
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor
import io.github.karljuderojas.freepdf.pdf.edit.PdfDocuments
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.render.PdfRenderer
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
import java.util.UUID

sealed interface ViewerState {
    data object Loading : ViewerState

    /** [revision] changes after every edit, so pages already on screen are rendered again. */
    data class Ready(
        val pageSizes: List<PageSize>,
        val revision: Int = 0,
        val canUndo: Boolean = false,
        val hasUnsavedChanges: Boolean = false,
    ) : ViewerState

    data class Failed(val message: String?) : ViewerState
}

/** One-off things the screen does for the view model: messages, the Save As picker, leaving. */
sealed interface ViewerEffect {
    data class Message(@StringRes val text: Int) : ViewerEffect
    data class SaveAs(val suggestedName: String) : ViewerEffect
    data object Close : ViewerEffect
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

    // Guards the renderer and the working copy: PDFium renders one page at a time, and an edit
    // swaps both the file and the renderer underneath any page that is mid-render.
    private val lock = Mutex()

    // Roughly a dozen screen-sized pages; bitmaps are the bulk of the app's memory.
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val context get() = getApplication<Application>()

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
                    }
                    reloadLocked()
                }
            }.getOrElse { ViewerState.Failed(it.message) }
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
        val size = document.getPage(afterIndex).mediaBox
        PageEditor.insertBlank(document, afterIndex + 1, size)
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

    fun undo() {
        viewModelScope.launch {
            lock.withLock {
                withContext(Dispatchers.IO) { session?.undo() }
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

    private fun edit(@StringRes onNoChange: Int? = null, change: (PDDocument) -> Unit) {
        viewModelScope.launch {
            lock.withLock {
                val result = runCatching { withContext(Dispatchers.IO) { session?.edit(change) } }
                when (val error = result.exceptionOrNull()) {
                    null -> _state.value = reloadLocked()
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

    /** Re-opens the working copy in PDFium. Call with [lock] held. */
    private suspend fun reloadLocked(): ViewerState {
        val current = session ?: return ViewerState.Failed(null)
        renderer?.close()
        renderer = null
        cache.evictAll()
        revision++
        val next = PdfRenderer.open(context, Uri.fromFile(current.workingFile))
        renderer = next
        return ViewerState.Ready(next.pageSizes, revision, current.canUndo, current.hasUnsavedChanges)
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
    }

    private companion object {
        const val CACHE_BYTES = 96 * 1024 * 1024
    }
}
