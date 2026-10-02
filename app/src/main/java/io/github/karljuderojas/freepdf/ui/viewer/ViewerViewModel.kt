package io.github.karljuderojas.freepdf.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.render.PdfRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ViewerState {
    data object Loading : ViewerState
    data class Ready(val pageSizes: List<PageSize>) : ViewerState
    data class Failed(val message: String?) : ViewerState
}

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Loading)
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private var renderer: PdfRenderer? = null
    private var openedUri: Uri? = null
    private val renderLock = Mutex()

    // Roughly a dozen screen-sized pages; bitmaps are the bulk of the app's memory.
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun open(uri: Uri) {
        if (uri == openedUri) return
        openedUri = uri
        _state.value = ViewerState.Loading
        viewModelScope.launch {
            _state.value = runCatching { PdfRenderer.open(getApplication(), uri) }
                .onSuccess { renderer?.close(); renderer = it; cache.evictAll() }
                .fold({ ViewerState.Ready(it.pageSizes) }, { ViewerState.Failed(it.message) })
        }
    }

    suspend fun page(index: Int, widthPx: Int): Bitmap? {
        val key = "$index@$widthPx"
        cache.get(key)?.let { return it }
        val current = renderer ?: return null
        // PDFium renders one page at a time; serialising avoids contention while scrolling fast.
        return renderLock.withLock {
            cache.get(key) ?: current.renderPage(index, widthPx).also { cache.put(key, it) }
        }
    }

    override fun onCleared() {
        renderer?.close()
        cache.evictAll()
    }

    private companion object {
        const val CACHE_BYTES = 96 * 1024 * 1024
    }
}
