package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.text.PageWord
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Selected words on one page: indices into that page's word list, [first] <= [last]. */
data class TextSelection(val page: Int, val first: Int, val last: Int) {
    val range: IntRange get() = first..last

    companion object {
        /** The words between [a] and [b] inclusive, whichever way round they are. */
        fun between(page: Int, a: Int, b: Int) = TextSelection(page, minOf(a, b), maxOf(a, b))
    }
}

/** What the selection popup offers. */
enum class SelectionAction(val label: Int) {
    Highlight(R.string.tool_highlight),
    Underline(R.string.tool_underline),
    StrikeOut(R.string.selection_strike),
    Note(R.string.tool_note),
    Copy(R.string.selection_copy),
}

/** The selected words' text, a space between words and a line break between lines. */
fun List<PageWord>.textOf(range: IntRange): String = buildString {
    for (i in range) {
        if (i > range.first) append(if (this@textOf[i].line != this@textOf[i - 1].line) '\n' else ' ')
        append(this@textOf[i].text)
    }
}

/** One box per line covering the words in [range], as page fractions. These become the marks' quads. */
fun List<PageWord>.lineBoxes(range: IntRange): List<Rect> =
    range.map { this[it] }.groupBy { it.line }.values.map { line ->
        Rect(line.minOf { it.left }, line.minOf { it.top }, line.maxOf { it.right }, line.maxOf { it.bottom })
    }

/**
 * The word nearest [point] (page fractions), or null if none is within [reach] (also a page
 * fraction of the width). Vertical distance counts double, so a finger between two lines picks
 * the line it is on rather than a nearby word on the next one.
 */
fun List<PageWord>.nearest(point: Offset, aspect: Float, reach: Float = Float.MAX_VALUE): Int? {
    var best: Int? = null
    var bestDistance = reach
    forEachIndexed { i, word ->
        val dx = maxOf(word.left - point.x, 0f, point.x - word.right)
        // Fractions down the page are shorter than fractions across by the page's aspect ratio.
        val dy = maxOf(word.top - point.y, 0f, point.y - word.bottom) / aspect
        val distance = hypot(dx, dy * 2f)
        if (distance <= bestDistance) {
            best = i
            bestDistance = distance
        }
    }
    return best
}

/**
 * Lets the reader pick words on a page: press and hold on a word, then drag to take in more.
 * Once something is selected its ends get handles to adjust it, a tap elsewhere clears it, and
 * [onAction] runs the popup's choice. Scrolling and zooming work as usual around it.
 */
@Composable
fun TextSelectionLayer(
    page: Int,
    words: List<PageWord>,
    selection: TextSelection?,
    onSelect: (TextSelection?) -> Unit,
    onAction: (SelectionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentWords by rememberUpdatedState(words)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelection by rememberUpdatedState(selection?.takeIf { it.page == page && it.last < words.size })
    // Where the page is in the window, zoom and scrolling included, to place the popup.
    var inWindow by remember { mutableStateOf(Rect.Zero) }
    val handleRadius = with(LocalDensity.current) { 10.dp.toPx() }
    val handleReach = with(LocalDensity.current) { 28.dp.toPx() }

    fun Offset.fraction(size: IntSize) = Offset(x / size.width, y / size.height)
    fun wordAt(position: Offset, size: IntSize, reach: Float = Float.MAX_VALUE) =
        currentWords.nearest(position.fraction(size), aspect = size.height.toFloat() / size.width, reach)

    val mine = selection?.takeIf { it.page == page && it.last < words.size }

    Box(
        modifier
            .fillMaxSize()
            .testTag("text-layer-$page")
            .onGloballyPositioned {
                inWindow = Rect(
                    it.localToWindow(Offset.Zero),
                    it.localToWindow(Offset(it.size.width.toFloat(), it.size.height.toFloat())),
                )
            }
            // Press and hold, then drag: select from the held word to the word under the finger.
            .pointerInput(page) {
                var anchor: Int? = null
                detectDragGesturesAfterLongPress(
                    onDragStart = { at ->
                        // Within a short reach only, so holding on blank space selects nothing.
                        anchor = wordAt(at, size, reach = 0.04f)
                        anchor?.let { currentOnSelect(TextSelection(page, it, it)) }
                    },
                    onDrag = { change, _ ->
                        anchor?.let { from ->
                            change.consume()
                            wordAt(change.position, size)?.let { currentOnSelect(TextSelection.between(page, from, it)) }
                        }
                    },
                    onDragEnd = { anchor = null },
                    onDragCancel = { anchor = null },
                )
            }
            // Dragging a handle moves that end of the selection.
            .pointerInput(page) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val current = currentSelection ?: return@awaitEachGesture
                    val boxes = currentWords.lineBoxes(current.range)
                    val start = boxes.first().let { Offset(it.left * size.width, it.bottom * size.height + handleRadius) }
                    val end = boxes.last().let { Offset(it.right * size.width, it.bottom * size.height + handleRadius) }
                    val fixed = when {
                        (down.position - start).getDistance() < handleReach -> current.last
                        (down.position - end).getDistance() < handleReach -> current.first
                        else -> return@awaitEachGesture
                    }
                    down.consume()
                    drag(down.id) { change ->
                        change.consume()
                        wordAt(change.position, size)?.let { currentOnSelect(TextSelection.between(page, fixed, it)) }
                    }
                }
            }
            .then(
                // Only while something is selected, so double-tap to reset zoom still reaches the page list.
                if (mine != null) {
                    Modifier.pointerInput(page) { detectTapGestures { currentOnSelect(null) } }
                } else {
                    Modifier
                },
            ),
    ) {
        if (mine == null) return@Box
        val boxes = words.lineBoxes(mine.range)
        val selectionColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) {
            boxes.forEach { box ->
                drawRect(
                    selectionColor.copy(alpha = 0.28f),
                    Offset(box.left * size.width, box.top * size.height),
                    androidx.compose.ui.geometry.Size(box.width * size.width, box.height * size.height),
                )
            }
            // Teardrop-ish handles under the first and last word.
            val first = boxes.first()
            val last = boxes.last()
            listOf(
                Offset(first.left * size.width, first.bottom * size.height),
                Offset(last.right * size.width, last.bottom * size.height),
            ).forEach { foot ->
                drawLine(selectionColor, foot, foot + Offset(0f, handleRadius), strokeWidth = 2.dp.toPx())
                drawCircle(selectionColor, handleRadius, foot + Offset(0f, handleRadius * 1.6f))
            }
        }
        if (inWindow.width > 0f) {
            // The selection in window space, its handles included, so the menu follows zoom and scrolling.
            fun at(x: Float, y: Float) = Offset(inWindow.left + x * inWindow.width, inWindow.top + y * inWindow.height)
            val topLeft = at(boxes.minOf { it.left }, boxes.minOf { it.top })
            val bottomRight = at(boxes.maxOf { it.right }, boxes.maxOf { it.bottom }) + Offset(0f, handleRadius * 3f)
            SelectionMenu(IntRect(topLeft.round(), bottomRight.round()), onAction)
        }
    }
}

@Composable
private fun SelectionMenu(anchor: IntRect, onAction: (SelectionAction) -> Unit) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val provider = remember(anchor, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = (anchor.center.x - popupContentSize.width / 2)
                    .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                // Above the selection if there is room, otherwise below its handles.
                val above = anchor.top - gap - popupContentSize.height
                val y = if (above >= 0) above else (anchor.bottom + gap).coerceAtMost(windowSize.height - popupContentSize.height)
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = provider) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
            tonalElevation = 3.dp,
        ) {
            Row(Modifier.padding(horizontal = 4.dp).testTag("selection-menu")) {
                SelectionAction.entries.forEach { action ->
                    TextButton(onClick = { onAction(action) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text(stringResource(action.label), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

private fun Offset.round() = IntOffset(x.roundToInt(), y.roundToInt())
