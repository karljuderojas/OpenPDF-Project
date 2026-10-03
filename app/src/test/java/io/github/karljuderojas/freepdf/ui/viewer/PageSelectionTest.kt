package io.github.karljuderojas.freepdf.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pages mode's selection after an edit lands, fails, or leaves fewer pages than it names. */
class PageSelectionTest {

    @Test
    fun selectionWithinThePagesIsLeftAlone() {
        assertEquals(setOf(0, 2, 3), clampSelection(setOf(0, 2, 3), 6))
    }

    @Test
    fun pagesPastTheEndAreDropped() {
        assertEquals(setOf(1), clampSelection(setOf(1, 4, 5), 3))
    }

    @Test
    fun theLastPageIsSelectedWhenNothingSelectedIsLeft() {
        assertEquals(setOf(2), clampSelection(setOf(4, 5), 3))
    }

    @Test
    fun aDocumentWithoutPagesKeepsTheSelectionAsItIs() {
        assertEquals(setOf(1), clampSelection(setOf(1), 0))
    }

    @Test
    fun aLandedEditKeepsTheSelectionTheScreenMovedTo() {
        // Move later from {1, 3}: the screen showed {2, 4} at once, and the edit landed.
        assertEquals(setOf(2, 4), settleSelection(setOf(2, 4), before = setOf(1, 3), failed = false, pageCount = 6))
    }

    @Test
    fun aFailedEditPutsTheSelectionBack() {
        assertEquals(setOf(1, 3), settleSelection(setOf(2, 4), before = setOf(1, 3), failed = true, pageCount = 6))
    }

    @Test
    fun aFailedEditWhoseStartIsUnknownOnlyClampsTheSelection() {
        // The screen was recreated meanwhile, so it no longer knows what the selection was before.
        assertEquals(setOf(5), settleSelection(setOf(6), before = null, failed = true, pageCount = 6))
    }

    @Test
    fun aFailedDeleteGoesBackToTheDeletedPagesIfTheyAreStillThere() {
        // Deleting {4, 5} of six showed {4}; the delete failed, so all six pages remain.
        assertEquals(setOf(4, 5), settleSelection(setOf(4), before = setOf(4, 5), failed = true, pageCount = 6))
    }

    @Test
    fun aLandedDeleteOfTheLastPagesSelectsTheNewLastPage() {
        assertEquals(setOf(3), settleSelection(setOf(4), before = setOf(4, 5), failed = false, pageCount = 4))
    }
}
