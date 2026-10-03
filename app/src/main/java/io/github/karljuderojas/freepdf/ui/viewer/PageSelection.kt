package io.github.karljuderojas.freepdf.ui.viewer

/**
 * Pages mode's selection kept to the pages there are: [selected] without any index past
 * [pageCount], or the last page when none is left. Never empty while there are pages, so the
 * tools always have something to act on; left alone when there are none.
 */
fun clampSelection(selected: Set<Int>, pageCount: Int): Set<Int> {
    if (pageCount <= 0) return selected
    return selected.filterTo(LinkedHashSet()) { it in 0 until pageCount }.ifEmpty { setOf(pageCount - 1) }
}

/**
 * The selection once a page edit has settled. The screen moves the selection as soon as it asks
 * for an edit, so the tools can be tapped again right away; when the edit [failed] (it threw,
 * changed nothing, or had to be undone) the selection goes back to what it was [before], if that
 * is known. Either way it is kept within [pageCount] with [clampSelection].
 */
fun settleSelection(selected: Set<Int>, before: Set<Int>?, failed: Boolean, pageCount: Int): Set<Int> =
    clampSelection(if (failed && before != null) before else selected, pageCount)
