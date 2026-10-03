package io.github.karljuderojas.freepdf.pdf.text

import kotlin.math.abs

/**
 * Puts the words of a page set in columns into column order. PdfBox's position sort groups words
 * into lines by baseline across the whole page, so on a two-column page each "line" is a piece of
 * the left column followed by a piece of the right one. When enough lines break at the same
 * horizontal gap (a gutter), each line is split there and, within each run of column lines, the
 * left column's lines are given out before the right column's; full-width lines (a title, an
 * abstract) stay where they are and end such a run. Each piece gets its own line number, so the
 * result reads as the page does. A page with no gutter comes back unchanged.
 */
internal object Columns {

    fun order(words: List<PageWord>): List<PageWord> {
        val lines = words.groupBy { it.line }.values.map { line -> line.sortedBy { it.left } }
        if (lines.size < MIN_LINES) return words

        val gaps = lines.flatMap { line -> line.zipWithNext { a, b -> b.left - a.right } }.filter { it > 0f }.sorted()
        if (gaps.isEmpty()) return words
        val bigGap = maxOf(gaps[gaps.size / 2] * BIG_GAP_FACTOR, MIN_GUTTER)

        val gutters = findGutters(lines, bigGap)
        if (gutters.isEmpty()) return words

        // Each line becomes one piece per column, or one spanning piece; spanning pieces flush
        // the pending block so that what comes before stays before.
        val out = ArrayList<PageWord>(words.size)
        val block = ArrayList<Pair<Int, List<PageWord>>>()
        var number = 0
        fun flush() {
            block.sortedBy { it.first }.forEach { (_, run) ->
                run.forEach { out += it.copy(line = number) }
                number++
            }
            block.clear()
        }
        for (line in lines) {
            val runs = split(line, gutters, bigGap)
            if (runs == null) {
                flush()
                line.forEach { out += it.copy(line = number) }
                number++
            } else {
                block.addAll(runs)
            }
        }
        flush()
        return out
    }

    /** The x ranges of the gutters, left to right; empty when the page is not set in columns. */
    private fun findGutters(lines: List<List<PageWord>>, bigGap: Float): List<ClosedFloatingPointRange<Float>> {
        // Every big gap on the page, as (line, left edge, right edge).
        val candidates = lines.flatMapIndexed { index, line ->
            line.zipWithNext { a, b -> Triple(index, a.right, b.left) }.filter { (_, l, r) -> r - l > bigGap }
        }.sortedBy { it.third }
        val gutters = ArrayList<ClosedFloatingPointRange<Float>>()
        var i = 0
        while (i < candidates.size) {
            // Gaps whose right edges lie within a small band share one gutter.
            var j = i
            while (j + 1 < candidates.size && candidates[j + 1].third - candidates[i].third < GUTTER_BAND) j++
            val cluster = candidates.subList(i, j + 1)
            val onLines = cluster.map { it.first }.distinct().size
            if (onLines >= MIN_LINES && onLines >= lines.size * MIN_SHARE) {
                val left = cluster.map { it.second }.sorted().let { it[it.size / 2] }
                val right = cluster.map { it.third }.sorted().let { it[it.size / 2] }
                if (gutters.none { abs(it.start - left) < GUTTER_BAND }) gutters += left..right
            }
            i = j + 1
        }
        return gutters
    }

    /** The column a point is in: the number of gutters whose middle lies to its left. */
    private fun columnOf(x: Float, gutters: List<ClosedFloatingPointRange<Float>>): Int =
        gutters.count { x > (it.start + it.endInclusive) / 2 }

    /**
     * The line cut at each gutter it crosses with a big gap there, as (column, words) runs; null
     * when some part of it runs across a gutter without a gap, which makes it a full-width line.
     */
    private fun split(line: List<PageWord>, gutters: List<ClosedFloatingPointRange<Float>>, bigGap: Float): List<Pair<Int, List<PageWord>>>? {
        val runs = ArrayList<Pair<Int, List<PageWord>>>()
        var start = 0
        var column = columnOf((line[0].left + line[0].right) / 2, gutters)
        for (k in 1 until line.size) {
            val word = line[k]
            val next = columnOf((word.left + word.right) / 2, gutters)
            if (next == column) continue
            if (word.left - line[k - 1].right <= bigGap) return null
            runs += column to line.subList(start, k)
            start = k
            column = next
        }
        runs += column to line.subList(start, line.size)
        return runs
    }

    /** How many lines must share a gutter, and what share of the page's lines. */
    private const val MIN_LINES = 3
    private const val MIN_SHARE = 0.3f

    /** A gap counts as a column break when it is this many times the typical word gap, and at least [MIN_GUTTER] of the page width. */
    private const val BIG_GAP_FACTOR = 3f
    private const val MIN_GUTTER = 0.02f

    /** Gaps whose right edges are within this (page widths) of each other are the same gutter. */
    private const val GUTTER_BAND = 0.04f
}
