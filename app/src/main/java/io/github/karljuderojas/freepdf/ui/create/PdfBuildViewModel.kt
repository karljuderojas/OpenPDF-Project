package io.github.karljuderojas.freepdf.ui.create

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.karljuderojas.freepdf.pdf.create.ImagesToPdf
import io.github.karljuderojas.freepdf.pdf.create.PageFit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where making a PDF out of pictures has got to. */
sealed interface BuildState {
    /** Nothing is being made. */
    data object Idle : BuildState

    /** [done] of [total] pages are in the PDF so far. */
    data class Making(val done: Int, val total: Int) : BuildState

    /** The PDF was written to [uri]; the screen opens it and then calls [PdfBuildViewModel.finished]. */
    data class Done(val uri: Uri) : BuildState

    /** The PDF could not be written; stays until [PdfBuildViewModel.clearFailure] or the next build. */
    data object Failed : BuildState
}

/**
 * Makes a PDF out of pictures for the scanner and Images to PDF. It belongs to the route, not the
 * screen, so a rotation while "Making page 3 of 8" neither forgets the result nor leaves the
 * buttons enabled over a save that is still running: the recreated screen reads [state] and
 * carries on from there.
 */
class PdfBuildViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<BuildState>(BuildState.Idle)
    val state: StateFlow<BuildState> = _state.asStateFlow()

    /** Where the pictures are read and the PDF is written; tests swap it for an inline one. */
    internal var io: CoroutineDispatcher = Dispatchers.IO

    /**
     * Writes a PDF of [count] pictures, fitted with [fit], to [target]. [load] gives picture i and
     * runs off the main thread; it must not touch the screen. Does nothing while one is being made.
     */
    fun build(count: Int, fit: PageFit, target: Uri, load: (Int) -> Bitmap) {
        if (_state.value is BuildState.Making) return
        _state.value = BuildState.Making(0, count)
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(io) {
                runCatching {
                    val document = ImagesToPdf.build(count, fit, onProgress = { _state.value = BuildState.Making(it, count) }, load)
                    NewPdf.write(app, document, target)
                }
            }
            _state.value = if (result.isSuccess) BuildState.Done(target) else BuildState.Failed
        }
    }

    /** The screen has opened the finished PDF. */
    fun finished() {
        if (_state.value is BuildState.Done) _state.value = BuildState.Idle
    }

    /** The screen has shown the failure and the user is changing something. */
    fun clearFailure() {
        if (_state.value is BuildState.Failed) _state.value = BuildState.Idle
    }
}
