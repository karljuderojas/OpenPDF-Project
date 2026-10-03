package io.github.karljuderojas.freepdf.pdf.text

/**
 * Turns a page's words (see [PageText]) into paragraphs of plain text that can be set in any
 * width: the page's lines are joined, a word split by a hyphen at a line end is made whole, and a
 * new paragraph starts after a gap between lines or after a short line that ended a sentence.
 * A scanned page has no words and gives no paragraphs.
 */
object Reflow {

    fun paragraphs(words: List<PageWord>): List<String> {
        val lines = words.groupConsecutive { it.line }
        if (lines.isEmpty()) return emptyList()
        val heights = lines.map { line -> line.maxOf { it.bottom } - line.minOf { it.top } }.sorted()
        val typicalHeight = heights[heights.size / 2]
        val pageRight = lines.maxOf { line -> line.maxOf { it.right } }
        val pageLeft = lines.minOf { line -> line.minOf { it.left } }
        val measure = pageRight - pageLeft

        val paragraphs = ArrayList<String>()
        val current = StringBuilder()
        var previous: List<PageWord>? = null
        for (line in lines) {
            val text = line.joinToString(" ") { it.text }
            if (previous != null && current.isNotEmpty()) {
                val gap = line.minOf { it.top } - previous.maxOf { it.bottom }
                val previousShort = previous.maxOf { it.right } < pageLeft + measure * SHORT_LINE
                val endsSentence = previous.last().text.last() in SENTENCE_END
                if (gap > typicalHeight * PARAGRAPH_GAP || (previousShort && endsSentence)) {
                    paragraphs += current.toString()
                    current.clear()
                }
            }
            if (current.isNotEmpty()) {
                val last = current.last()
                // "inter-" at a line end and "national" on the next: one word, unless it is a real dash.
                if (last == '-' && current.length >= 2 && current[current.length - 2].isLetter() && text.first().isLowerCase()) {
                    current.setLength(current.length - 1)
                } else {
                    current.append(' ')
                }
            }
            current.append(text)
            previous = line
        }
        if (current.isNotEmpty()) paragraphs += current.toString()
        return paragraphs
    }

    /**
     * The sentences of [text] as ranges of it, for reading aloud and for highlighting the one being
     * read. A sentence ends at . ! or ? (with any closing quote or bracket) before a space or the
     * end, so "3.5" stays whole; a very long run with no ending is cut at a space.
     */
    fun sentenceRanges(text: String): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        for (match in SENTENCE.findAll(text)) {
            var start = match.range.first
            val end = match.range.last
            while (start <= end && text[start].isWhitespace()) start++
            while (start <= end) {
                var stop = minOf(end, start + MAX_SENTENCE - 1)
                if (stop < end) {
                    val space = text.lastIndexOf(' ', stop)
                    if (space > start) stop = space - 1
                }
                ranges += start..stop
                start = stop + 1
                while (start <= end && text[start].isWhitespace()) start++
            }
        }
        return ranges
    }

    /** All the sentences of [paragraphs], in order; [sentenceRanges] counted over each paragraph in turn. */
    fun sentences(paragraphs: List<String>): List<String> =
        paragraphs.flatMap { p -> sentenceRanges(p).map { p.substring(it.first, it.last + 1) } }

    private inline fun <T, K> List<T>.groupConsecutive(key: (T) -> K): List<List<T>> {
        val groups = ArrayList<MutableList<T>>()
        var lastKey: K? = null
        for (item in this) {
            val k = key(item)
            if (groups.isEmpty() || k != lastKey) groups += mutableListOf(item) else groups.last() += item
            lastKey = k
        }
        return groups
    }

    private const val PARAGRAPH_GAP = 0.6f
    private const val SHORT_LINE = 0.75f
    private const val SENTENCE_END = ".!?:"
    private const val MAX_SENTENCE = 400
    private val SENTENCE = Regex("""[\s\S]+?(?:[.!?]+["')\]]*(?=\s|$)|$)""")
}
