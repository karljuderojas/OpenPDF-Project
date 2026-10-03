package io.github.karljuderojas.freepdf.pdf.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflowTest {

    /** A line of words at [top], each 0.05 high, laid out across [from]..[to] of the page width. */
    private fun line(index: Int, top: Float, text: String, from: Float = 0.1f, to: Float = 0.9f): List<PageWord> {
        val words = text.split(" ")
        val step = (to - from) / words.size
        return words.mapIndexed { i, w -> PageWord(w, index, from + i * step, top, from + (i + 1) * step, top + 0.02f) }
    }

    @Test
    fun noWordsGiveNoParagraphs() {
        assertTrue(Reflow.paragraphs(emptyList()).isEmpty())
    }

    @Test
    fun linesOfOneParagraphJoinWithSpaces() {
        val words = line(0, 0.10f, "The quick brown fox") + line(1, 0.125f, "jumps over the lazy dog")
        assertEquals(listOf("The quick brown fox jumps over the lazy dog"), Reflow.paragraphs(words))
    }

    @Test
    fun aGapBetweenLinesStartsANewParagraph() {
        val words = line(0, 0.10f, "First paragraph text") + line(1, 0.20f, "Second paragraph text")
        assertEquals(listOf("First paragraph text", "Second paragraph text"), Reflow.paragraphs(words))
    }

    @Test
    fun aShortLineThatEndsASentenceStartsANewParagraph() {
        val words = line(0, 0.10f, "A full line of the first paragraph") +
            line(1, 0.125f, "Ends here.", to = 0.4f) +
            line(2, 0.15f, "Then the next one starts")
        assertEquals(
            listOf("A full line of the first paragraph Ends here.", "Then the next one starts"),
            Reflow.paragraphs(words),
        )
    }

    @Test
    fun aWordSplitByAHyphenIsMadeWhole() {
        val words = line(0, 0.10f, "an inter-") + line(1, 0.125f, "national agreement")
        assertEquals(listOf("an international agreement"), Reflow.paragraphs(words))
    }

    @Test
    fun aHyphenBeforeACapitalIsKept() {
        val words = line(0, 0.10f, "the Anglo-") + line(1, 0.125f, "Saxon period")
        assertEquals(listOf("the Anglo- Saxon period"), Reflow.paragraphs(words))
    }
}
