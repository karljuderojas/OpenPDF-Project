package io.github.karljuderojas.freepdf.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which row of the page list holds which pages: one page per row on a phone, a cover alone then pairs on a tablet. */
class PageRowsTest {

    @Test
    fun oneColumnMapsPagesToRowsOneToOne() {
        (0 until 5).forEach { page ->
            assertEquals(page, rowOf(page, 1))
            assertEquals(page, firstPageOf(page, 1))
        }
        assertEquals(5, rowCount(5, 1))
        assertEquals(0, rowCount(0, 1))
    }

    @Test
    fun twoColumnsKeepTheCoverAloneAndFacingPagesTogether() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3), (0 until 6).map { rowOf(it, 2) })
        assertEquals(listOf(0, 1, 3, 5, 7), (0 until 5).map { firstPageOf(it, 2) })
        // Pages 0 | 1 2 | 3 4 | 5 take four rows; 0 | 1 2 | 3 4 take three.
        assertEquals(4, rowCount(6, 2))
        assertEquals(3, rowCount(5, 2))
        assertEquals(1, rowCount(1, 2))
        assertEquals(0, rowCount(0, 2))
    }

    @Test
    fun everyPageIsInTheRowItsFirstPageStarts() {
        for (columns in 1..2) for (count in 1..9) for (page in 0 until count) {
            val row = rowOf(page, columns)
            assertTrue("page $page, $columns columns", page >= firstPageOf(row, columns) && page < firstPageOf(row + 1, columns))
            assertTrue(row < rowCount(count, columns))
        }
    }
}
