package io.github.karljuderojas.freepdf.ui.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropBoxTest {

    @Test
    fun draggingACornerMovesOnlyItsOwnEdges() {
        val box = CropBox(0.2f, 0.2f, 0.8f, 0.8f)
        val moved = box.dragged(2, 0.1f, -0.05f)
        assertEquals(0.2f, moved.left, 0.0001f)
        assertEquals(0.2f, moved.top, 0.0001f)
        assertEquals(0.9f, moved.right, 0.0001f)
        assertEquals(0.75f, moved.bottom, 0.0001f)
        val other = box.dragged(0, -0.1f, 0.1f)
        assertEquals(0.1f, other.left, 0.0001f)
        assertEquals(0.3f, other.top, 0.0001f)
        assertEquals(0.8f, other.right, 0.0001f)
    }

    @Test
    fun theBoxStaysInsideThePictureAndNeverCollapses() {
        val box = CropBox(0.2f, 0.2f, 0.8f, 0.8f)
        val out = box.dragged(1, 5f, -5f)
        assertEquals(1f, out.right, 0f)
        assertEquals(0f, out.top, 0f)
        val squeezed = box.dragged(0, 5f, 5f)
        assertTrue(squeezed.right - squeezed.left >= CropBox.MIN - 0.0001f)
        assertTrue(squeezed.bottom - squeezed.top >= CropBox.MIN - 0.0001f)
    }
}
