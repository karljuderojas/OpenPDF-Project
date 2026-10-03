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
import kotlin.math.min
import kotlin.math.roundToInt

/** A crop box inside a picture, as fractions of its width and height. */
data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    /** The box with corner [index] (0 top left, then clockwise) moved by ([dx], [dy]), kept inside the picture and at least [MIN] across. */
    fun dragged(index: Int, dx: Float, dy: Float): CropBox {
        val l = if (index == 0 || index == 3) (left + dx).coerceIn(0f, right - MIN) else left
        val r = if (index == 1 || index == 2) (right + dx).coerceIn(left + MIN, 1f) else right
        val t = if (index == 0 || index == 1) (top + dy).coerceIn(0f, bottom - MIN) else top
        val b = if (index == 2 || index == 3) (bottom + dy).coerceIn(top + MIN, 1f) else bottom
        return CropBox(l, t, r, b)
    }

    /** The corners, top left first, clockwise. */
    val corners: List<Pair<Float, Float>> get() = listOf(left to top, right to top, right to bottom, left to bottom)

    companion object {
        const val MIN = 0.08f
        val Whole = CropBox(0.05f, 0.05f, 0.95f, 0.95f)
    }
}

/** Shows a picture with a crop box over it and a round handle on each corner to drag. */
@Composable
fun CropEditor(image: ImageBitmap, box: CropBox, onBox: (CropBox) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(box)
    val onBoxNow by rememberUpdatedState(onBox)
    val outline = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier.testTag("crop-editor")) {
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
                        val distances = current.corners.map { (x, y) -> (Offset(left + x * dw, top + y * dh) - start).getDistance() }
                        val nearest = distances.indices.minByOrNull { distances[it] }
                        active = if (nearest != null && distances[nearest] <= grab) nearest else -1
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onDrag = { change, delta ->
                        if (active >= 0) {
                            change.consume()
                            current = current.dragged(active, delta.x / dw, delta.y / dh)
                            onBoxNow(current)
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
            val crop = Rect(left + box.left * dw, top + box.top * dh, left + box.right * dw, top + box.bottom * dh)
            val shade = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset(left, top), Size(dw, dh)))
                addRect(crop)
            }
            drawPath(shade, Color.Black.copy(alpha = 0.45f))
            drawRect(outline, topLeft = crop.topLeft, size = crop.size, style = Stroke(width = 3.dp.toPx()))
            listOf(crop.topLeft, crop.topRight, crop.bottomRight, crop.bottomLeft).forEach {
                drawCircle(Color.White, radius = 14.dp.toPx(), center = it)
                drawCircle(outline, radius = 11.dp.toPx(), center = it)
            }
        }
    }
}
