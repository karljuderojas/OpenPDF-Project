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
    fun bulletedAndNumberedItemsAreTheirOwnParagraphs() {
        val words = line(0, 0.10f, "Bring the following items", to = 0.6f) +
            line(1, 0.125f, "• a torch", to = 0.4f) +
            line(2, 0.15f, "• warm clothes", to = 0.4f) +
            line(3, 0.175f, "1. First step", to = 0.4f) +
            line(4, 0.20f, "2) Second step", to = 0.4f) +
            line(5, 0.225f, "(3) Third step", to = 0.4f)
        assertEquals(
            listOf("Bring the following items", "• a torch", "• warm clothes", "1. First step", "2) Second step", "(3) Third step"),
            Reflow.paragraphs(words),
        )
    }

    @Test
    fun aNumberInsideTheTextIsNotAListItem() {
        val words = line(0, 0.10f, "The cost rose to") + line(1, 0.125f, "3.5 million last year")
        assertEquals(listOf("The cost rose to 3.5 million last year"), Reflow.paragraphs(words))
    }

    @Test
    fun aChangeOfTextSizeStartsANewParagraph() {
        // A heading half as tall again as the body, with tight leading and no punctuation.
        val heading = listOf(PageWord("Heading", 0, 0.1f, 0.10f, 0.3f, 0.13f))
        val words = heading + line(1, 0.135f, "Body text that follows the heading") + line(2, 0.16f, "and goes on to a second line")
        assertEquals(
            listOf("Heading", "Body text that follows the heading and goes on to a second line"),
            Reflow.paragraphs(words),
        )
    }

    @Test
    fun columnLinesAreNotShortJustBecauseThePageIsWider() {
        // Two columns, column order: the left column's lines end in sentences but fill their column.
        val left = line(0, 0.10f, "This line ends here.", from = 0.1f, to = 0.45f) +
            line(1, 0.125f, "And this one carries on", from = 0.1f, to = 0.45f)
        val right = line(2, 0.10f, "into the right column and", from = 0.55f, to = 0.9f) +
            line(3, 0.125f, "ends there.", from = 0.55f, to = 0.75f)
        assertEquals(
            listOf("This line ends here. And this one carries on into the right column and ends there."),
            Reflow.paragraphs(left + right),
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

    @Test
    fun sentencesSplitAtEndingsButNotInsideNumbers() {
        val text = "It costs 3.5 million. Is that right? Yes!"
        assertEquals(
            listOf("It costs 3.5 million.", "Is that right?", "Yes!"),
            Reflow.sentences(listOf(text)),
        )
    }

    @Test
    fun sentencesKeepClosingQuotesAndTextWithNoEnding() {
        assertEquals(listOf("He said \"stop.\"", "Then left"), Reflow.sentences(listOf("He said \"stop.\" Then left")))
    }

    @Test
    fun rangesPointAtTheSentencesInTheirParagraph() {
        val text = "First one. Second one."
        assertEquals(listOf(0..9, 11..21), Reflow.sentenceRanges(text))
    }

    @Test
    fun aVeryLongRunIsCutAtASpace() {
        val text = "word ".repeat(200).trim()
        val parts = Reflow.sentences(listOf(text))
        assertTrue(parts.size >= 2)
        assertTrue(parts.all { it.length <= 400 && !it.startsWith(" ") })
        assertEquals(text, parts.joinToString(" "))
    }
}
