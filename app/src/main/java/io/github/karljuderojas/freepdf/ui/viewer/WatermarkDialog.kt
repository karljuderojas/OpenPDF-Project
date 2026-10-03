package io.github.karljuderojas.freepdf.ui.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.edit.PageRanges
import io.github.karljuderojas.freepdf.pdf.edit.WatermarkStyle
import kotlin.math.roundToInt

private val Gray = Annotator.Rgb(0.5f, 0.5f, 0.5f)
private val Black = Annotator.Rgb(0f, 0f, 0f)

private data class Swatch(val color: Annotator.Rgb, val name: Int)

private val swatches = listOf(
    Swatch(Gray, R.string.watermark_color_gray),
    Swatch(Annotator.Rgb.Red, R.string.watermark_color_red),
    Swatch(Annotator.Rgb.Blue, R.string.watermark_color_blue),
    Swatch(Black, R.string.watermark_color_black),
)

private val suggestions = listOf("CONFIDENTIAL", "DRAFT", "COPY", "DO NOT COPY")

/**
 * Asks what to stamp across the pages: text, or a picture from [image] (chosen with
 * [onChooseImage]), how strong, how big and at what angle, and whether for the selected pages or
 * all of them. A sketch of the page shows the text as it will come out. [onWatermark] gets the
 * zero-based pages, the text, the picture if that is what was chosen, and the look.
 */
@Composable
fun WatermarkDialog(
    pageCount: Int,
    selectedPages: List<Int>,
    pageAspect: Float,
    image: Uri?,
    onChooseImage: () -> Unit,
    onDismiss: () -> Unit,
    onWatermark: (Set<Int>, String, Uri?, WatermarkStyle) -> Unit,
) {
    var picture by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("CONFIDENTIAL") }
    var opacity by rememberSaveable { mutableFloatStateOf(0.3f) }
    var angle by rememberSaveable { mutableFloatStateOf(45f) }
    var size by rememberSaveable { mutableFloatStateOf(0.7f) }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var allPages by rememberSaveable { mutableStateOf(false) }
    val style = WatermarkStyle(opacity, angle.roundToInt().toFloat(), size, swatches[colorIndex].color)
    val pages = if (allPages) (0 until pageCount).toSet() else selectedPages.toSet()
    val ready = if (picture) image != null else text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.watermark_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    ChoiceRow(!picture, stringResource(R.string.watermark_text_option), "watermark-text-option", Modifier.weight(1f)) { picture = false }
                    ChoiceRow(picture, stringResource(R.string.watermark_picture_option), "watermark-picture-option", Modifier.weight(1f)) { picture = true }
                }
                if (picture) {
                    OutlinedButton(onClick = onChooseImage, modifier = Modifier.fillMaxWidth().testTag("watermark-choose-picture")) {
                        Text(stringResource(if (image == null) R.string.watermark_choose_picture else R.string.watermark_change_picture))
                    }
                    Text(
                        stringResource(if (image == null) R.string.watermark_no_picture else R.string.watermark_picture_chosen),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(MAX_TEXT) },
                        label = { Text(stringResource(R.string.watermark_text_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("watermark-text"),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { AssistChip(onClick = { text = it }, label = { Text(it) }) }
                    }
                }
                Sketch(if (picture) "" else text, style, pageAspect, Modifier.padding(vertical = 8.dp))
                LevelSlider(R.string.watermark_opacity, opacity, 0.05f..1f, "${(opacity * 100).roundToInt()}%", "watermark-opacity") { opacity = it }
                LevelSlider(R.string.watermark_size, size, 0.1f..1f, "${(size * 100).roundToInt()}%", "watermark-size") { size = it }
                LevelSlider(
                    R.string.watermark_angle, angle, -90f..90f,
                    stringResource(R.string.watermark_angle_value, angle.roundToInt()), "watermark-angle",
                ) { angle = it }
                if (!picture) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.watermark_color), Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
                        swatches.forEachIndexed { i, swatch ->
                            val name = stringResource(swatch.name)
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(swatch.color.toColor())
                                    .border(if (i == colorIndex) 3.dp else 1.dp, if (i == colorIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                    .selectable(selected = i == colorIndex, role = Role.RadioButton, onClick = { colorIndex = i })
                                    .testTag("watermark-color-$name"),
                            )
                        }
                    }
                }
                if (pageCount > 1) {
                    ChoiceRow(!allPages, stringResource(R.string.crop_apply_selected, PageRanges.format(selectedPages)), "watermark-selected", Modifier) { allPages = false }
                    ChoiceRow(allPages, stringResource(R.string.crop_apply_all, pageCount), "watermark-all", Modifier) { allPages = true }
                }
                Text(stringResource(R.string.watermark_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onWatermark(pages, text.trim(), if (picture) image else null, style) },
                enabled = ready && style.isValid,
            ) { Text(stringResource(R.string.watermark_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The page as a pale sheet with [text] across it, drawn at the chosen strength, size and angle. */
@Composable
private fun Sketch(text: String, style: WatermarkStyle, pageAspect: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .height(SKETCH_HEIGHT)
                .aspectRatio(pageAspect.coerceIn(0.3f, 3f))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("watermark-sketch"),
            contentAlignment = Alignment.Center,
        ) {
            if (text.isNotBlank()) {
                // About half an em per capital: a size of 1 spans the sketch's width.
                val fontSize = (style.size * SKETCH_HEIGHT.value * pageAspect.coerceIn(0.3f, 3f) / (text.length * 0.6f)).coerceIn(6f, 40f)
                Text(
                    text,
                    Modifier.alpha(style.opacity).rotate(-style.angle),
                    color = style.color.toColor(),
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun LevelSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, tag: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).testTag(tag))
        Text(shown, Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .testTag(tag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Annotator.Rgb.toColor() = Color(r, g, b)

private val SKETCH_HEIGHT = 140.dp
private const val MAX_TEXT = 60
