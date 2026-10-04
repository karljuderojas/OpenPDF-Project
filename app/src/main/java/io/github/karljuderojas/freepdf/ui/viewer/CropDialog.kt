package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.edit.CropMargins
import io.github.karljuderojas.freepdf.pdf.edit.MarginFinder
import io.github.karljuderojas.freepdf.pdf.edit.PageRanges
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Asks how much to trim from each edge of the page, with a sketch of what stays, and whether that
 * is for the selected pages or all of them. [onCrop] gets the zero-based pages and the margins, or
 * null margins to show the pages in full again. [pageAspect] is the shape (width over height) of
 * the page as shown, for the sketch.
 */
@Composable
fun CropPagesDialog(
    pageCount: Int,
    selectedPages: List<Int>,
    pageAspect: Float,
    onDismiss: () -> Unit,
    onCrop: (Set<Int>, CropMargins?) -> Unit,
    previewPage: Int = 0,
    loadPage: (suspend (index: Int, widthPx: Int) -> Bitmap?)? = null,
    onTrim: (Map<Int, CropMargins>) -> Unit = {},
    /** Margins already found, to show straight away (for a screenshot). Pages not in it are blank. */
    initialTrim: Map<Int, CropMargins>? = null,
) {
    var left by rememberSaveable { mutableFloatStateOf(0f) }
    var top by rememberSaveable { mutableFloatStateOf(0f) }
    var right by rememberSaveable { mutableFloatStateOf(0f) }
    var bottom by rememberSaveable { mutableFloatStateOf(0f) }
    var allPages by rememberSaveable { mutableStateOf(false) }
    val pages = if (allPages) (0 until pageCount).toSet() else selectedPages.toSet()
    // Margins found for each page by Trim margins; any change by hand or to the pages drops them.
    var trim by remember { mutableStateOf(initialTrim) }
    var looking by remember { mutableStateOf(false) }
    var lookedAt by remember { mutableStateOf(initialTrim != null) }
    val scope = rememberCoroutineScope()
    val shown = trim?.get(previewPage)
    val margins = shown ?: CropMargins(left, top, right, bottom)
    fun adjust(change: () -> Unit) {
        // Start from what the sketch shows, so one slider moved does not reset the others.
        shown?.let { left = it.left; top = it.top; right = it.right; bottom = it.bottom }
        trim = null
        lookedAt = false
        change()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crop_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.crop_body), style = MaterialTheme.typography.bodyMedium)
                CropSketch(margins, pageAspect, Modifier.padding(vertical = 8.dp))
                MarginSlider(R.string.crop_left, margins.left, "crop-left") { v -> adjust { left = v.coerceAtMost(CropMargins.MAX_TOTAL - right) } }
                MarginSlider(R.string.crop_top, margins.top, "crop-top") { v -> adjust { top = v.coerceAtMost(CropMargins.MAX_TOTAL - bottom) } }
                MarginSlider(R.string.crop_right, margins.right, "crop-right") { v -> adjust { right = v.coerceAtMost(CropMargins.MAX_TOTAL - left) } }
                MarginSlider(R.string.crop_bottom, margins.bottom, "crop-bottom") { v -> adjust { bottom = v.coerceAtMost(CropMargins.MAX_TOTAL - top) } }
                if (pageCount > 1) {
                    ApplyRow(
                        selected = !allPages,
                        label = stringResource(R.string.crop_apply_selected, PageRanges.format(selectedPages)),
                        tag = "crop-selected",
                    ) { allPages = false; trim = null; lookedAt = false }
                    ApplyRow(selected = allPages, label = stringResource(R.string.crop_apply_all, pageCount), tag = "crop-all") {
                        allPages = true; trim = null; lookedAt = false
                    }
                }
                if (loadPage != null || initialTrim != null) {
                    OutlinedButton(
                        onClick = {
                            if (loadPage == null) return@OutlinedButton
                            looking = true
                            scope.launch {
                                val found = withContext(Dispatchers.Default) {
                                    pages.sorted().mapNotNull { page ->
                                        val picture = loadPage(page, MarginFinder.PICTURE_WIDTH_PX) ?: return@mapNotNull null
                                        MarginFinder.find(picture)?.let { page to it }
                                    }.toMap()
                                }
                                trim = found
                                lookedAt = true
                                looking = false
                            }
                        },
                        enabled = !looking,
                        modifier = Modifier.fillMaxWidth().testTag("crop-trim"),
                    ) { Text(stringResource(R.string.crop_trim)) }
                    val found = trim
                    if (looking || lookedAt) {
                        val note = when {
                            looking -> stringResource(R.string.crop_trim_working)
                            found.isNullOrEmpty() -> stringResource(R.string.crop_trim_none)
                            pages.size > 1 -> stringResource(R.string.crop_trim_found_pages, found.size, pages.size)
                            else -> stringResource(R.string.crop_trim_found)
                        }
                        Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("crop-trim-note"))
                    }
                }
                TextButton(onClick = { onCrop(pages, null) }, modifier = Modifier.testTag("crop-reset")) {
                    Text(stringResource(R.string.crop_reset))
                }
            }
        },
        confirmButton = {
            val found = trim
            TextButton(
                onClick = { if (found != null) onTrim(found) else onCrop(pages, margins) },
                enabled = if (found != null) found.isNotEmpty() else !margins.isEmpty && margins.isValid,
            ) {
                Text(stringResource(R.string.tool_crop))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The page as a pale sheet with what is kept drawn as a framed box and the rest shaded. */
@Composable
private fun CropSketch(margins: CropMargins, pageAspect: Float, modifier: Modifier = Modifier) {
    val sheet = MaterialTheme.colorScheme.surfaceVariant
    val shade = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val frame = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .height(SKETCH_HEIGHT)
                .aspectRatio(pageAspect.coerceIn(0.3f, 3f))
                .testTag("crop-sketch"),
        ) {
            drawRect(sheet)
            val keep = Offset(size.width * margins.left, size.height * margins.top)
            val keepSize = Size(
                size.width * (1f - margins.left - margins.right),
                size.height * (1f - margins.top - margins.bottom),
            )
            // Four bands around the kept box, rather than a cut-out, to stay simple to draw.
            drawRect(shade, Offset.Zero, Size(size.width, keep.y))
            drawRect(shade, Offset(0f, keep.y + keepSize.height), Size(size.width, size.height - keep.y - keepSize.height))
            drawRect(shade, Offset(0f, keep.y), Size(keep.x, keepSize.height))
            drawRect(shade, Offset(keep.x + keepSize.width, keep.y), Size(size.width - keep.x - keepSize.width, keepSize.height))
            drawRect(frame, keep, keepSize, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun MarginSlider(label: Int, value: Float, tag: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..CropMargins.MAX_EDGE,
            modifier = Modifier.weight(1f).testTag(tag),
        )
        Text(
            stringResource(R.string.crop_percent, (value * 100).roundToInt()),
            Modifier.width(44.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ApplyRow(selected: Boolean, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private val SKETCH_HEIGHT = 140.dp
