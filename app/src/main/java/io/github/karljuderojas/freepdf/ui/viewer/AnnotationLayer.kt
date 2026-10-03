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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R

/** The Annotate tools that are wired up, and how each one is used on the page. */
enum class AnnotateTool(val label: Int, val gesture: Gesture, val color: Color) {
    Highlight(R.string.tool_highlight, Gesture.Box, Color(0xFFFFEB3B)),
    Underline(R.string.tool_underline, Gesture.Box, Color(0xFF2166E5)),
    StrikeOut(R.string.tool_strikeout, Gesture.Box, Color(0xFFE52929)),
    Pen(R.string.tool_pen, Gesture.Draw, Color(0xFF2166E5)),
    Shapes(R.string.tool_shapes, Gesture.Box, Color(0xFFE52929)),
    Note(R.string.tool_note, Gesture.Tap, Color(0xFFFFEB3B)),
    Eraser(R.string.tool_eraser, Gesture.Tap, Color.Gray);

    enum class Gesture { Draw, Box, Tap }

    companion object {
        fun forLabel(label: Int?): AnnotateTool? = entries.firstOrNull { it.label == label }
    }
}

/**
 * Sits over one page while an Annotate tool is chosen and turns touches into marks. Points are
 * reported as fractions of the page (0..1, origin top-left), so they do not depend on zoom or
 * screen size; the view model converts them to PDF space. One-finger drags draw here instead of
 * scrolling; choosing the tool again (or Done) gives scrolling back.
 */
@Composable
fun AnnotationLayer(
    page: Int,
    tool: AnnotateTool,
    onStroke: (List<Offset>) -> Unit,
    onBox: (start: Offset, end: Offset) -> Unit,
    onTap: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stroke = remember(tool) { mutableStateListOf<Offset>() }
    var box by remember(tool) { mutableStateOf<Rect?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun Offset.normalised() = Offset(
        (x / size.width).coerceIn(0f, 1f),
        (y / size.height).coerceIn(0f, 1f),
    )

    val gestures = when (tool.gesture) {
        AnnotateTool.Gesture.Tap -> Modifier.pointerInput(tool) {
            detectTapGestures { onTap(it.normalised()) }
        }
        AnnotateTool.Gesture.Draw -> Modifier.pointerInput(tool) {
            detectDragGestures(
                onDragStart = { stroke.clear(); stroke.add(it) },
                onDrag = { change, _ -> change.consume(); stroke.add(change.position) },
                onDragEnd = {
                    if (stroke.isNotEmpty()) onStroke(stroke.map { it.normalised() })
                    stroke.clear()
                },
                onDragCancel = { stroke.clear() },
            )
        }
        AnnotateTool.Gesture.Box -> Modifier.pointerInput(tool) {
            var start = Offset.Zero
            detectDragGestures(
                onDragStart = { start = it; box = Rect(it, it) },
                onDrag = { change, _ ->
                    change.consume()
                    box = Rect(start, change.position)
                },
                onDragEnd = {
                    box?.let { onBox(it.topLeft.normalised(), it.bottomRight.normalised()) }
                    box = null
                },
                onDragCancel = { box = null },
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
        if (stroke.size > 1) {
            val path = Path().apply {
                moveTo(stroke[0].x, stroke[0].y)
                stroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, tool.color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        box?.let { raw ->
            // Dragging up or left gives a flipped rectangle; draw it the right way round.
            val r = Rect(
                minOf(raw.left, raw.right), minOf(raw.top, raw.bottom),
                maxOf(raw.left, raw.right), maxOf(raw.top, raw.bottom),
            )
            when (tool) {
                AnnotateTool.Highlight -> drawRect(tool.color.copy(alpha = 0.4f), r.topLeft, r.size)
                AnnotateTool.Underline ->
                    drawLine(tool.color, r.bottomLeft, r.bottomRight, strokeWidth = 2.dp.toPx())
                AnnotateTool.StrikeOut ->
                    drawLine(tool.color, r.centerLeft, r.centerRight, strokeWidth = 2.dp.toPx())
                else -> drawRect(tool.color, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
            }
        }
    }
}
