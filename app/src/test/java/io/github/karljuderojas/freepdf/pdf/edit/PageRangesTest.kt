package io.github.karljuderojas.freepdf.pdf.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageRangesTest {

    @Test
    fun parsesSinglePagesRangesAndOpenRanges() {
        assertEquals(listOf(0, 1, 2, 4), PageRanges.parse("1-3, 5", 10))
        assertEquals(listOf(7, 8, 9), PageRanges.parse("8-", 10))
        assertEquals(listOf(1), PageRanges.parse(" 2 ", 10))
        // Repeats and out-of-order parts come back once, in document order.
        assertEquals(listOf(0, 1, 2), PageRanges.parse("3, 1-2, 2", 10))
        assertEquals(listOf(3, 4), PageRanges.parse("4–5", 10))
    }

    @Test
    fun rejectsPagesThatDoNotExist() {
        assertNull(PageRanges.parse("", 10))
        assertNull(PageRanges.parse("0", 10))
        assertNull(PageRanges.parse("11", 10))
        assertNull(PageRanges.parse("5-3", 10))
        assertNull(PageRanges.parse("a", 10))
        assertNull(PageRanges.parse("1-2-3", 10))
    }

    @Test
    fun formatsRuns() {
        assertEquals("1-3, 5", PageRanges.format(listOf(0, 1, 2, 4)))
        assertEquals("2", PageRanges.format(listOf(1)))
        assertEquals("1, 3-4", PageRanges.format(listOf(3, 0, 2)))
    }
}
