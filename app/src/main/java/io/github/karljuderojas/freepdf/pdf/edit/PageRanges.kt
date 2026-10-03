package io.github.karljuderojas.freepdf.pdf.edit

/**
 * Page lists typed the way people write them: "1-3, 5, 8-". Numbers are one-based on screen and
 * zero-based in code. An open range ("8-") runs to the last page.
 */
object PageRanges {

    /**
     * The zero-based pages [text] names, in document order without repeats, or null if it names a
     * page outside 1..[pageCount], has a backwards range, or names nothing.
     */
    fun parse(text: String, pageCount: Int): List<Int>? {
        val pages = sortedSetOf<Int>()
        for (part in text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val bounds = part.split('-', '–', '—').map { it.trim() }
            val (first, last) = when (bounds.size) {
                1 -> bounds[0].toIntOrNull().let { it to it }
                2 -> bounds[0].toIntOrNull() to (if (bounds[1].isEmpty()) pageCount else bounds[1].toIntOrNull())
                else -> null to null
            }
            if (first == null || last == null || first < 1 || last > pageCount || first > last) return null
            (first..last).forEach { pages += it - 1 }
        }
        return pages.toList().ifEmpty { null }
    }

    /** Writes zero-based [pages] back as text, joining runs: [0, 1, 2, 4] is "1-3, 5". */
    fun format(pages: List<Int>): String {
        val sorted = pages.distinct().sorted()
        val runs = ArrayList<IntRange>()
        for (page in sorted) {
            val last = runs.lastOrNull()
            if (last != null && last.last == page - 1) runs[runs.size - 1] = last.first..page else runs += page..page
        }
        return runs.joinToString(", ") { if (it.first == it.last) "${it.first + 1}" else "${it.first + 1}-${it.last + 1}" }
    }
}
