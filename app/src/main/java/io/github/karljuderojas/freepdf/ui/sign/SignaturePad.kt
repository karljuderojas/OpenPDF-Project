package io.github.karljuderojas.freepdf.ui.sign

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import kotlin.math.hypot

private val InkColors = listOf(R.string.ink_black to Color(0xFF111111), R.string.ink_blue to Color(0xFF1A3FA8))

/**
 * Draw a signature or initials once; it is saved on the device for next time. Drawings that are
 * too small or too simple to identify anyone are refused with a request to draw again.
 */
@Composable
fun SignaturePadDialog(
    kind: SignatureStore.Kind,
    onDismiss: () -> Unit,
    onSave: (Bitmap) -> Unit,
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var ink by remember { mutableStateOf(InkColors.first().second) }
    var tooSimple by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(if (kind == SignatureStore.Kind.Initials) R.string.pad_title_initials else R.string.pad_title_signature),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(16.dp))
                val lineColor = MaterialTheme.colorScheme.outline
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(if (kind == SignatureStore.Kind.Initials) 160.dp else 220.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .testTag("signature-pad")
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current.clear(); current.add(it); tooSimple = false },
                                onDrag = { change, _ -> change.consume(); current.add(change.position) },
                                onDragEnd = { strokes.add(current.toList()); current.clear() },
                                onDragCancel = { current.clear() },
                            )
                        },
                ) {
                    val baseline = this.size.height * 0.72f
                    drawLine(
                        lineColor, Offset(24.dp.toPx(), baseline), Offset(this.size.width - 24.dp.toPx(), baseline),
                        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    )
                    val style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    (strokes + listOf(current.toList())).filter { it.size > 1 }.forEach { stroke ->
                        val path = Path().apply {
                            moveTo(stroke[0].x, stroke[0].y)
                            stroke.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, ink, style = style)
                    }
                }
                Text(
                    stringResource(if (tooSimple) R.string.pad_too_simple else R.string.pad_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooSimple) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkColors.forEach { (label, color) ->
                        FilterChip(selected = ink == color, onClick = { ink = color }, label = { Text(stringResource(label)) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { strokes.clear(); tooSimple = false }) { Text(stringResource(R.string.clear)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(
                        enabled = strokes.isNotEmpty(),
                        onClick = {
                            if (SignatureInk.isTooSimple(strokes, size, kind)) {
                                tooSimple = true
                            } else {
                                onSave(SignatureInk.render(strokes, ink.toArgb(), strokeWidthPx = size.width / 120f))
                            }
                        },
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

/** Turns pad strokes into a tightly cropped image with a transparent background. */
object SignatureInk {

    fun isTooSimple(strokes: List<List<Offset>>, padSize: IntSize, kind: SignatureStore.Kind): Boolean {
        val points = strokes.flatten()
        if (points.size < 8 || padSize.width == 0) return true
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        val length = strokes.sumOf { stroke ->
            stroke.zipWithNext { a, b -> hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) }.sum()
        }
        val minWidth = if (kind == SignatureStore.Kind.Initials) 0.08f else 0.18f
        return width < padSize.width * minWidth || height < padSize.height * 0.06f || length < padSize.width * 0.3
    }

    fun render(strokes: List<List<Offset>>, argb: Int, strokeWidthPx: Float): Bitmap {
        val points = strokes.flatten()
        val pad = strokeWidthPx * 2
        val left = points.minOf { it.x } - pad
        val top = points.minOf { it.y } - pad
        val width = (points.maxOf { it.x } + pad - left).toInt().coerceAtLeast(1)
        val height = (points.maxOf { it.y } + pad - top).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = argb
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val canvas = android.graphics.Canvas(bitmap)
        strokes.forEach { stroke ->
            if (stroke.size == 1) {
                canvas.drawPoint(stroke[0].x - left, stroke[0].y - top, paint)
            } else {
                val path = android.graphics.Path()
                path.moveTo(stroke[0].x - left, stroke[0].y - top)
                stroke.drop(1).forEach { path.lineTo(it.x - left, it.y - top) }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }
}
