package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A pen stroke on [page], through [points] (fractions of the page, as [AnnotationLayer] reports
 * them), that the page on screen may not show yet. [landedAt] is the revision whose rendering
 * first has it, or null while it is still being written into the PDF.
 */
data class PendingStroke(
    val id: Long,
    val page: Int,
    val points: List<Offset>,
    val style: ToolStyle,
    val landedAt: Int? = null,
)

/**
 * Keeps pen strokes on screen from the moment the finger lifts until the page is rendered with
 * them. Each stroke is a whole save of the PDF, and strokes drawn one after another wait for the
 * ones before, so without this a stroke would vanish when the finger lifts and only come back
 * once every stroke drawn since had been saved too.
 */
class PendingInk {
    private val _strokes = MutableStateFlow<List<PendingStroke>>(emptyList())
    val strokes: StateFlow<List<PendingStroke>> = _strokes.asStateFlow()
    private var nextId = 0L

    /** A stroke just drawn, whose edit has been asked for; returns its id for [landed] or [dropped]. */
    fun add(page: Int, points: List<Offset>, style: ToolStyle): Long {
        val id = nextId++
        _strokes.update { it + PendingStroke(id, page, points, style) }
        return id
    }

    /** The stroke is in the PDF from [revision] on; it stays drawn until its page is rendered at that revision. */
    fun landed(id: Long, revision: Int) {
        _strokes.update { strokes -> strokes.map { if (it.id == id) it.copy(landedAt = revision) else it } }
    }

    /** The stroke could not be added, so it goes. */
    fun dropped(id: Long) {
        _strokes.update { strokes -> strokes.filterNot { it.id == id } }
    }

    /** [page] has been rendered at [revision]: the strokes that had landed by then are in that picture. */
    fun rendered(page: Int, revision: Int) {
        val shown = { stroke: PendingStroke -> stroke.page == page && (stroke.landedAt ?: Int.MAX_VALUE) <= revision }
        if (_strokes.value.any(shown)) _strokes.update { strokes -> strokes.filterNot(shown) }
    }

    /** Forgets every stroke, as when another document is opened. */
    fun clear() {
        _strokes.value = emptyList()
    }
}
