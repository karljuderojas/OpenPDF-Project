package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.pdf.text.PageWord

/**
 * Edit text's layer over one page: a faint outline around each line of text, so it is clear what
 * can be changed, and taps (page fractions) reported to [onTap]. [words] is empty on scans.
 */
@Composable
fun EditTextLayer(page: Int, words: List<PageWord>, onTap: (Offset) -> Unit) {
    val currentOnTap by rememberUpdatedState(onTap)
    val outline = MaterialTheme.colorScheme.primary
    val lines = words.groupBy { it.line }.values
    Box(
        Modifier
            .fillMaxSize()
            .testTag("edit-text-layer-$page")
            .pointerInput(Unit) { detectTapGestures { currentOnTap(Offset(it.x / size.width, it.y / size.height)) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 2.dp.toPx()
            lines.forEach { line ->
                val left = line.minOf { it.left } * size.width - pad
                val top = line.minOf { it.top } * size.height - pad
                val width = line.maxOf { it.right } * size.width - left + pad
                val height = line.maxOf { it.bottom } * size.height - top + pad
                drawRoundRect(outline.copy(alpha = 0.10f), Offset(left, top), Size(width, height), CornerRadius(3.dp.toPx()))
                drawRoundRect(outline.copy(alpha = 0.55f), Offset(left, top), Size(width, height), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
        }
    }
}
