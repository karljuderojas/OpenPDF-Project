package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingInkTest {

    private val pen = AnnotateTool.Pen.defaultStyle
    private val line = listOf(Offset(0.1f, 0.1f), Offset(0.4f, 0.2f))

    private fun PendingInk.ids() = strokes.value.map { it.id }

    @Test
    fun aStrokeShowsAsSoonAsItIsDrawn() {
        val ink = PendingInk()
        val id = ink.add(0, line, pen)
        assertEquals(listOf(PendingStroke(id, 0, line, pen)), ink.strokes.value)
    }

    @Test
    fun aRenderingFromBeforeTheStrokeLandedKeepsIt() {
        val ink = PendingInk()
        val id = ink.add(0, line, pen)
        // The page is rendered again (scrolled, zoomed) while the save is still running.
        ink.rendered(0, 3)
        assertEquals(listOf(id), ink.ids())
        ink.landed(id, 4)
        // A rendering asked for before the save finished does not have the stroke either.
        ink.rendered(0, 3)
        assertEquals(listOf(id), ink.ids())
    }

    @Test
    fun theStrokeGoesOnceItsPageIsRenderedWithIt() {
        val ink = PendingInk()
        val id = ink.add(0, line, pen)
        ink.landed(id, 4)
        ink.rendered(0, 4)
        assertTrue(ink.strokes.value.isEmpty())
    }

    @Test
    fun anotherPageBeingRenderedLeavesTheStroke() {
        val ink = PendingInk()
        val id = ink.add(1, line, pen)
        ink.landed(id, 4)
        ink.rendered(0, 4)
        assertEquals(listOf(id), ink.ids())
    }

    @Test
    fun strokesDrawnQuicklyEachWaitForTheirOwnSave() {
        val ink = PendingInk()
        val first = ink.add(0, line, pen)
        val second = ink.add(0, line, pen)
        ink.landed(first, 4)
        ink.rendered(0, 4)
        assertEquals(listOf(second), ink.ids())
        ink.landed(second, 5)
        ink.rendered(0, 5)
        assertTrue(ink.strokes.value.isEmpty())
    }

    @Test
    fun aStrokeThatCouldNotBeSavedGoes() {
        val ink = PendingInk()
        val kept = ink.add(0, line, pen)
        val failed = ink.add(0, line, pen)
        ink.dropped(failed)
        assertEquals(listOf(kept), ink.ids())
    }

    @Test
    fun openingAnotherDocumentForgetsTheStrokes() {
        val ink = PendingInk()
        ink.add(0, line, pen)
        ink.clear()
        assertTrue(ink.strokes.value.isEmpty())
    }
}
