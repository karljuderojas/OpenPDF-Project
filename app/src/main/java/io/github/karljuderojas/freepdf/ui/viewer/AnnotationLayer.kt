package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.text.PageWord

/**
 * The Annotate tools that are wired up, and how each one is used on the page. Each tool keeps its
 * own colour from [palette] and, where [widths] offers a choice, its own line thickness in points.
 */
enum class AnnotateTool(
    val label: Int,
    val gesture: Gesture,
    val palette: List<Color>,
    val widths: List<Float>,
    val defaultStyle: ToolStyle,
) {
    Highlight(R.string.tool_highlight, Gesture.Box, HighlightColors, emptyList(), ToolStyle(HighlightColors[0], 0f)),
    Underline(R.string.tool_underline, Gesture.Box, InkColors, LineWidths, ToolStyle(InkColors[1], 1f)),
    StrikeOut(R.string.tool_strikeout, Gesture.Box, InkColors, LineWidths, ToolStyle(InkColors[2], 1f)),
    Pen(R.string.tool_pen, Gesture.Draw, InkColors, PenWidths, ToolStyle(InkColors[1], 2f)),
    TextBox(R.string.tool_text_box, Gesture.Tap, InkColors, FontSizes, ToolStyle(InkColors[0], 12f)),
    Shapes(R.string.tool_shapes, Gesture.Box, InkColors, PenWidths, ToolStyle(InkColors[2], 2f)),
    Note(R.string.tool_note, Gesture.Tap, HighlightColors, emptyList(), ToolStyle(HighlightColors[0], 0f)),
    Eraser(R.string.tool_eraser, Gesture.Tap, emptyList(), emptyList(), ToolStyle(Color.Gray, 0f));

    enum class Gesture { Draw, Box, Tap }

    /** Highlight, Underline and Strikeout mark text, so they snap to words. */
    val snapsToWords: Boolean get() = this == Highlight || this == Underline || this == StrikeOut

    /** True when the tool has a colour or size to choose. */
    val hasStyle: Boolean get() = palette.isNotEmpty() || widths.isNotEmpty()

    companion object {
        fun forLabel(label: Int?): AnnotateTool? = entries.firstOrNull { it.label == label }
    }
}

/**
 * Sits over one page while an Annotate tool is chosen and turns touches into marks. Points are
 * reported as fractions of the page (0..1, origin top-left), so they do not depend on zoom or
 * screen size; the view model converts them to PDF space. One-finger drags draw here instead of
 * scrolling; choosing the tool again (or Done) gives scrolling back.
 *
 * The preview is drawn in [style]'s colour, with lines as thick as they will be on a page
 * [pageWidthPt] points wide.
 *
 * Highlight, Underline and Strikeout snap to whole [words] when the drag starts on text, and
 * report one box per line through [onLines]; away from text (a scan, a picture) they mark the
 * dragged area through [onBox] instead.
 */
@Composable
fun AnnotationLayer(
    page: Int,
    tool: AnnotateTool,
    style: ToolStyle,
    pageWidthPt: Float,
    onStroke: (List<Offset>) -> Unit,
    onBox: (start: Offset, end: Offset) -> Unit,
    onTap: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    words: List<PageWord> = emptyList(),
    onLines: (List<Rect>) -> Unit = {},
) {
    // The gesture loops outlive recompositions, so always call the latest callbacks.
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentOnBox by rememberUpdatedState(onBox)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLines by rememberUpdatedState(onLines)
    val currentWords by rememberUpdatedState(words)
    // The words a markup drag has snapped to, while it lasts.
    var snapped by remember(tool) { mutableStateOf<IntRange?>(null) }
    val stroke = remember(tool) { mutableStateListOf<Offset>() }
    var box by remember(tool) { mutableStateOf<Rect?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun Offset.normalised() = Offset(
        (x / size.width).coerceIn(0f, 1f),
        (y / size.height).coerceIn(0f, 1f),
    )

    val gestures = when (tool.gesture) {
        AnnotateTool.Gesture.Tap -> Modifier.pointerInput(tool) {
            detectTapGestures { currentOnTap(it.normalised()) }
        }
        AnnotateTool.Gesture.Draw -> Modifier.pointerInput(tool) {
            detectDragGestures(
                onDragStart = { stroke.clear(); stroke.add(it) },
                onDrag = { change, _ -> change.consume(); stroke.add(change.position) },
                onDragEnd = {
                    if (stroke.isNotEmpty()) currentOnStroke(stroke.map { it.normalised() })
                    stroke.clear()
                },
                onDragCancel = { stroke.clear() },
            )
        }
        AnnotateTool.Gesture.Box -> Modifier.pointerInput(tool) {
            var start = Offset.Zero
            var anchor: Int? = null
            var aspect = 1f
            detectDragGestures(
                onDragStart = {
                    start = it
                    aspect = size.height.toFloat() / size.width
                    anchor = if (tool.snapsToWords) currentWords.nearest(it.normalised(), aspect, reach = 0.04f) else null
                    anchor?.let { word -> snapped = word..word } ?: run { box = Rect(it, it) }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val from = anchor
                    if (from != null) {
                        currentWords.nearest(change.position.normalised(), aspect)?.let { to ->
                            snapped = minOf(from, to)..maxOf(from, to)
                        }
                    } else {
                        box = Rect(start, change.position)
                    }
                },
                onDragEnd = {
                    snapped?.let { currentOnLines(currentWords.lineBoxes(it)) }
                    box?.let { currentOnBox(it.topLeft.normalised(), it.bottomRight.normalised()) }
                    box = null
                    snapped = null
                },
                onDragCancel = { box = null; snapped = null },
            )
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .testTag("annotation-layer-$page")
            .onSizeChanged { size = it }
            .then(gestures),
    ) {
        // Points to pixels at the page's current on-screen size; never thinner than a hairline.
        val lineWidth = (style.width * size.width / pageWidthPt).coerceAtLeast(1.dp.toPx())
        if (stroke.size > 1) {
            val path = Path().apply {
                moveTo(stroke[0].x, stroke[0].y)
                stroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, style.color, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        snapped?.takeIf { it.last < words.size }?.let { range ->
            words.lineBoxes(range).forEach { line ->
                val r = Rect(line.left * size.width, line.top * size.height, line.right * size.width, line.bottom * size.height)
                drawMarkupPreview(tool, style, r, lineWidth)
            }
        }
        box?.let { raw ->
            // Dragging up or left gives a flipped rectangle; draw it the right way round.
            val r = Rect(
                minOf(raw.left, raw.right), minOf(raw.top, raw.bottom),
                maxOf(raw.left, raw.right), maxOf(raw.top, raw.bottom),
            )
            drawMarkupPreview(tool, style, r, lineWidth)
        }
    }
}

private fun DrawScope.drawMarkupPreview(tool: AnnotateTool, style: ToolStyle, r: Rect, lineWidth: Float) {
    when (tool) {
        AnnotateTool.Highlight -> drawRect(style.color.copy(alpha = 0.4f), r.topLeft, r.size)
        AnnotateTool.Underline ->
            drawLine(style.color, r.bottomLeft, r.bottomRight, strokeWidth = lineWidth)
        AnnotateTool.StrikeOut ->
            drawLine(style.color, r.centerLeft, r.centerRight, strokeWidth = lineWidth)
        else -> drawRect(style.color, r.topLeft, r.size, style = Stroke(lineWidth))
    }
}
