package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.text.PageWord

/**
 * An area marked for redaction on [page]. [rect] is in fractions of the displayed page (0..1
 * across and down, origin top-left), like every overlay, so it does not depend on zoom.
 */
data class RedactBox(val page: Int, val rect: Rect)

/** Keeps the marked areas across a rotation or other recreation of the screen. */
val RedactBoxesSaver = listSaver<List<RedactBox>, Float>(
    save = { boxes -> boxes.flatMap { listOf(it.page.toFloat(), it.rect.left, it.rect.top, it.rect.right, it.rect.bottom) } },
    restore = { flat -> flat.chunked(5).map { (page, left, top, right, bottom) -> RedactBox(page.toInt(), Rect(left, top, right, bottom)) } },
)

private val MarkColor = Color(0xFFD32F2F)

// A drag smaller than this (as a fraction of the page) is a stray touch, not an area.
private const val MIN_SIDE = 0.004f

/**
 * Over one page in Edit mode: draws the areas marked so far and, while [active], turns drags into
 * more of them. Dragging over text snaps to whole [words], one box per line; away from text (a
 * scan, a picture) it marks the dragged rectangle.
 */
@Composable
fun RedactionLayer(
    page: Int,
    boxes: List<RedactBox>,
    active: Boolean,
    pageWidthPt: Float,
    words: List<PageWord>,
    onBox: (Rect) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().testTag("redaction-marks-$page")) {
            boxes.forEach { box ->
                val topLeft = Offset(box.rect.left * size.width, box.rect.top * size.height)
                val boxSize = Size(box.rect.width * size.width, box.rect.height * size.height)
                drawRect(MarkColor.copy(alpha = 0.3f), topLeft, boxSize)
                drawRect(MarkColor, topLeft, boxSize, style = Stroke(2.dp.toPx()))
            }
        }
        if (active) {
            AnnotationLayer(
                page = page,
                tool = AnnotateTool.Highlight,
                style = ToolStyle(MarkColor, 0f),
                pageWidthPt = pageWidthPt,
                onStroke = {},
                onBox = { start, end ->
                    val rect = Rect(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y))
                    if (rect.width >= MIN_SIDE && rect.height >= MIN_SIDE) onBox(rect)
                },
                onTap = {},
                words = words,
                onLines = { lines -> lines.forEach(onBox) },
            )
        }
    }
}

/** Above the Edit tool strip while Redact is chosen: how many areas are marked, and the way to apply them. */
@Composable
fun RedactBar(count: Int, onRemoveLast: () -> Unit, onApply: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        if (count == 0) {
            Text(
                stringResource(R.string.redact_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(R.plurals.redact_marked, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemoveLast) { Text(stringResource(R.string.redact_remove_last)) }
                Button(onClick = onApply) { Text(stringResource(R.string.redact_apply)) }
            }
        }
    }
}

/**
 * What a look at the marked pages found before the redacted copy is written: the zero-based
 * [wholePicturePages] where a picture under a mark cannot be partly cleared and would go whole.
 * [boxes] are the marks it was made for, so a stale check is not shown for new marks.
 */
data class RedactCheck(val boxes: List<RedactBox>, val wholePicturePages: List<Int>)

/** Where writing the redacted copy has got to: marked page [page] (from 1) of [of]. */
data class RedactProgress(val page: Int, val of: Int)

/**
 * Asks before [onConfirm] starts saving the redacted copy, since what it removes cannot be put
 * back. Until [check] is in, the copy cannot be confirmed: a picture that would be removed whole
 * is something to know before, not after.
 */
@Composable
fun RedactConfirmDialog(count: Int, check: RedactCheck?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.redact_confirm_title, count, count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.redact_confirm_body))
                Text(stringResource(R.string.redact_confirm_limits))
                when {
                    check == null -> Text(
                        stringResource(R.string.redact_confirm_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    check.wholePicturePages.isNotEmpty() -> {
                        val pages = check.wholePicturePages.sorted().map { it + 1 }
                        Text(
                            pluralStringResource(R.plurals.redact_confirm_whole_pictures, pages.size, pages.joinToString(", ")),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("redact-whole-pictures"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = check != null) { Text(stringResource(R.string.redact_confirm_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
