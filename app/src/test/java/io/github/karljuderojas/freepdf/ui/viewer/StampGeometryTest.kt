package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.ui.geometry.Offset
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import org.junit.Assert.assertEquals
import org.junit.Test

class StampGeometryTest {

    private val letter = PageSize(612f, 792f)
    private val tap = Offset(0.3f, 0.4f)

    @Test
    fun signatureLandsWhereItWasTapped() {
        val box = StampGeometry.signatureBox(tap, 400, 100, SignatureStore.Kind.Signature, letter)
        // 400x100 fits 160x56 points at 160x40.
        assertEquals(160f, box.width * letter.widthPt, 0.01f)
        assertEquals(40f, box.height * letter.heightPt, 0.01f)
        assertOffset(tap, StampGeometry.signatureAnchor(box))
    }

    @Test
    fun textKeepsItsBaselineAndSize() {
        val box = StampGeometry.textBox(tap, listOf("Oct 3, 2026"), letter) { it.length * 0.5f }
        val fontSize = StampGeometry.fontSize(box, 1, letter)
        assertEquals(StampGeometry.TEXT_SIZE, fontSize, 0.01f)
        assertOffset(tap, StampGeometry.textAnchor(box, fontSize, letter))
    }

    @Test
    fun resizedTextGrowsItsFont() {
        val box = StampGeometry.textBox(tap, listOf("a", "b"), letter) { 1f }.scaled(2f, letter)
        assertEquals(StampGeometry.TEXT_SIZE * 2, StampGeometry.fontSize(box, 2, letter), 0.01f)
    }

    @Test
    fun checkmarkPointSitsOnTheTap() {
        val box = StampGeometry.checkmarkBox(tap, letter)
        assertEquals(StampGeometry.CHECKMARK_SIZE, StampGeometry.checkmarkSize(box, letter), 0.01f)
        assertOffset(tap, StampGeometry.checkmarkAnchor(box, letter))
    }

    @Test
    fun movingStopsAtThePageEdges() {
        val box = StampBox(0.5f, 0.5f, 0.2f, 0.1f)
        assertEquals(StampBox(0.8f, 0f, 0.2f, 0.1f), box.moved(0.9f, -0.9f))
        assertEquals(StampBox(0f, 0.9f, 0.2f, 0.1f), box.moved(-0.9f, 0.9f))
    }

    @Test
    fun resizingStaysOnThePageAndReadable() {
        val box = StampBox(0.5f, 0.5f, 0.2f, 0.1f)
        // Grows only until it reaches the right edge.
        assertEquals(0.5f, box.scaled(10f, letter).width, 0.0001f)
        // Shrinks only until the short side is 6 points.
        val smallest = box.scaled(0.001f, letter)
        assertEquals(StampBox.MIN_POINTS, smallest.height * letter.heightPt, 0.01f)
    }

    @Test
    fun placingNearAnEdgeKeepsTheStampOnThePage() {
        val box = StampGeometry.signatureBox(Offset(0.99f, 0.01f), 400, 100, SignatureStore.Kind.Signature, letter)
        assertEquals(1f, box.right, 0.0001f)
        assertEquals(0f, box.top, 0.0001f)
    }

    @Test
    fun editTextStartsLargerAndKeepsItsSize() {
        val box = StampGeometry.textBox(tap, listOf("Draft"), letter, StampGeometry.EDIT_TEXT_SIZE) { it.length * 0.5f }
        val fontSize = StampGeometry.fontSize(box, 1, letter)
        assertEquals(StampGeometry.EDIT_TEXT_SIZE, fontSize, 0.01f)
        assertOffset(tap, StampGeometry.textAnchor(box, fontSize, letter))
    }

    @Test
    fun pictureIsCentredAndFitsHalfThePageWidth() {
        val box = StampGeometry.imageBox(Offset(0.5f, 0.5f), 4000, 3000, letter)
        // 4000x3000 fits half of 612 points wide (306) before a third of 792 high (264).
        assertEquals(306f, box.width * letter.widthPt, 0.01f)
        assertEquals(229.5f, box.height * letter.heightPt, 0.01f)
        assertOffset(Offset(0.5f, 0.5f), Offset(box.left + box.width / 2, box.top + box.height / 2))
        assertOffset(Offset(box.left, box.bottom), StampGeometry.imageAnchor(box))
    }

    @Test
    fun smallPictureIsNotBlownUp() {
        val box = StampGeometry.imageBox(Offset(0.5f, 0.5f), 100, 50, letter)
        assertEquals(100f, box.width * letter.widthPt, 0.01f)
        assertEquals(50f, box.height * letter.heightPt, 0.01f)
    }

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
    }
}
