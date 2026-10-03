package io.github.karljuderojas.freepdf.pdf.render

/** One entry of a PDF's outline (bookmarks), flattened. [depth] is 0 for top-level entries. */
data class OutlineItem(val title: String, val page: Int, val depth: Int)

/** A box on a page as fractions of the displayed page: 0..1, origin top-left. */
data class PageBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Finding a query in a page's text. Kept apart from PDFium so it can be tested on the JVM. */
object TextSearch {

    /**
     * Where [query] occurs in [text], ignoring case. Runs of whitespace in the query match any
     * whitespace in the text, so a phrase still matches where the PDF breaks the line.
     */
    fun find(text: String, query: String): List<IntRange> {
        val words = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val pattern = Regex(words.joinToString("\\s+") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
        return pattern.findAll(text).map { it.range }.toList()
    }

    private val WHITESPACE = Regex("\\s+")
}
