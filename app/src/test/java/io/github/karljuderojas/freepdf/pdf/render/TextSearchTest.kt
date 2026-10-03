package io.github.karljuderojas.freepdf.pdf.render

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSearchTest {

    private val text = "The Client pays 40% when signing.\r\nThe client\r\napproves the quote."

    @Test
    fun findsEveryMatchIgnoringCase() {
        assertEquals(listOf(4..9, 39..44), TextSearch.find(text, "client"))
    }

    @Test
    fun aPhraseMatchesAcrossALineBreak() {
        val ranges = TextSearch.find(text, "client  approves")
        assertEquals(1, ranges.size)
        assertEquals("client\r\napproves", text.substring(ranges.single()))
    }

    @Test
    fun specialCharactersAreLiteral() {
        assertEquals(listOf(16..18), TextSearch.find(text, "40%"))
        assertEquals(emptyList<IntRange>(), TextSearch.find(text, "4.%"))
    }

    @Test
    fun aBlankQueryFindsNothing() {
        assertEquals(emptyList<IntRange>(), TextSearch.find(text, "   "))
    }
}
