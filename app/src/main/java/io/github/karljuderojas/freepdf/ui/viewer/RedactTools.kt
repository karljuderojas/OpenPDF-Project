package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import io.github.karljuderojas.freepdf.pdf.redact.RedactFill
import io.github.karljuderojas.freepdf.pdf.text.PageWord

/**
 * An area marked for redaction on [page]. [rect] is in fractions of the displayed page (0..1
 * across and down, origin top-left), like every overlay, so it does not depend on zoom. [fill]
 * is the colour of the box painted over it in the redacted copy; null while Auto has still to
 * look at the page under it (see [contrastingFill]), which shows and applies as black meanwhile.
 */
data class RedactBox(val page: Int, val rect: Rect, val fill: RedactFill? = RedactFill.Black) {
    /** The colour the box has for now: [fill], or black until Auto has decided. */
    val shownFill: RedactFill get() = fill ?: RedactFill.Black
}

/** [boxes] as six floats each, for saved state: page, left, top, right, bottom, fill (-1 while undecided). */
fun flattenRedactBoxes(boxes: List<RedactBox>): List<Float> =
    boxes.flatMap { listOf(it.page.toFloat(), it.rect.left, it.rect.top, it.rect.right, it.rect.bottom, it.fill?.ordinal?.toFloat() ?: -1f) }

/** The boxes [flattenRedactBoxes] wrote. An undecided fill stays undecided, so Auto looks again after a rotation. */
fun unflattenRedactBoxes(flat: List<Float>): List<RedactBox> =
    flat.chunked(6).filter { it.size == 6 }.map { (page, left, top, right, bottom, fill) ->
        RedactBox(page.toInt(), Rect(left, top, right, bottom), if (fill < 0f) null else RedactFill.entries.getOrElse(fill.toInt()) { RedactFill.Black })
    }

private operator fun <T> List<T>.component6() = this[5]

/** Keeps the marked areas across a rotation or other recreation of the screen. */
val RedactBoxesSaver = listSaver<List<RedactBox>, Float>(save = { flattenRedactBoxes(it) }, restore = { unflattenRedactBoxes(it) })

/**
 * The box colour that stands out against [page] under [rect] (fractions of the page, as in
 * [RedactBox]): white where the page there is dark, black otherwise or when there is no picture.
 */
fun contrastingFill(page: Bitmap?, rect: Rect): RedactFill {
    if (page == null || page.width == 0 || page.height == 0) return RedactFill.Black
    val left = (rect.left * page.width).toInt().coerceIn(0, page.width - 1)
    val top = (rect.top * page.height).toInt().coerceIn(0, page.height - 1)
    val width = ((rect.right * page.width).toInt() - left).coerceIn(1, page.width - left)
    val height = ((rect.bottom * page.height).toInt() - top).coerceIn(1, page.height - top)
    val pixels = IntArray(width * height)
    if (runCatching { page.getPixels(pixels, 0, width, left, top, width, height) }.isFailure) return RedactFill.Black
    // The middle brightness, not the average: text is a part of a word's box, and the rest is the background.
    val brightness = IntArray(pixels.size) { i ->
        val pixel = pixels[i]
        // A transparent pixel is the page's white.
        if (pixel ushr 24 == 0) 255 else (299 * (pixel shr 16 and 0xFF) + 587 * (pixel shr 8 and 0xFF) + 114 * (pixel and 0xFF)) / 1000
    }
    brightness.sort()
    val light = brightness[brightness.size / 2]
    return if (light < 128) RedactFill.White else RedactFill.Black
}

private fun RedactFill.color() = if (this == RedactFill.White) Color.White else Color.Black

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
                // The box the copy will get, faded so the marked words can still be checked.
                drawRect(box.shownFill.color().copy(alpha = 0.6f), topLeft, boxSize)
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

/**
 * Above the Edit tool strip while Redact is chosen: the colour of the boxes for new marks, how
 * many areas are marked, and the way to apply them. [fill] null is Auto: each mark gets the
 * colour that stands out against the page under it.
 */
@Composable
fun RedactBar(count: Int, fill: RedactFill?, onFill: (RedactFill?) -> Unit, onRemoveLast: () -> Unit, onApply: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.redact_fill),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                listOf(null to R.string.redact_fill_auto, RedactFill.Black to R.string.redact_fill_black, RedactFill.White to R.string.redact_fill_white)
                    .forEach { (choice, label) ->
                        FilterChip(
                            selected = fill == choice,
                            onClick = { onFill(choice) },
                            label = { Text(stringResource(label)) },
                            leadingIcon = if (choice != null) { { Swatch(choice) } } else null,
                            modifier = Modifier.testTag("redact-fill-${choice?.name?.lowercase() ?: "auto"}"),
                        )
                    }
            }
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
}

/** A small square of [fill]'s colour, outlined so white shows on a light chip. */
@Composable
private fun Swatch(fill: RedactFill) {
    Canvas(Modifier.size(16.dp)) {
        drawRect(fill.color())
        drawRect(Color.Gray, style = Stroke(1.dp.toPx()))
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
