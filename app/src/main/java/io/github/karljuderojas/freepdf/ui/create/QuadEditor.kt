package io.github.karljuderojas.freepdf.ui.create

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.pdf.scan.Corner
import io.github.karljuderojas.freepdf.pdf.scan.Quad
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shows a photo with the page outline over it and a round handle on each corner to drag. Touching
 * near a corner picks it up; the rest of the photo is dimmed so the page stands out.
 */
@Composable
fun QuadEditor(image: ImageBitmap, quad: Quad, onQuad: (Quad) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(quad)
    val onQuadNow by rememberUpdatedState(onQuad)
    val outline = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier.testTag("quad-editor")) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / image.width, boxH / image.height)
        val dw = image.width * scale
        val dh = image.height * scale
        val left = (boxW - dw) / 2f
        val top = (boxH - dh) / 2f

        Canvas(
            Modifier.fillMaxSize().pointerInput(image, dw, dh, left, top) {
                val grab = 56.dp.toPx()
                var active = -1
                var current = latest
                detectDragGestures(
                    onDragStart = { start ->
                        current = latest
                        val nearest = current.corners.withIndex().minByOrNull { (_, c) ->
                            (Offset(left + c.x * dw, top + c.y * dh) - start).getDistance()
                        }
                        active = nearest?.takeIf { (_, c) -> (Offset(left + c.x * dw, top + c.y * dh) - start).getDistance() <= grab }?.index ?: -1
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onDrag = { change, delta ->
                        if (active >= 0) {
                            change.consume()
                            val c = current.corners[active]
                            current = current.withCorner(active, Corner(c.x + delta.x / dw, c.y + delta.y / dh))
                            onQuadNow(current)
                        }
                    },
                )
            },
        ) {
            drawImage(
                image,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            )
            val points = quad.corners.map { Offset(left + it.x * dw, top + it.y * dh) }
            val shade = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset(left, top), Size(dw, dh)))
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(shade, Color.Black.copy(alpha = 0.45f))
            val edge = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(edge, outline, style = Stroke(width = 3.dp.toPx()))
            points.forEach {
                drawCircle(Color.White, radius = 14.dp.toPx(), center = it)
                drawCircle(outline, radius = 11.dp.toPx(), center = it)
            }
        }
    }
}
