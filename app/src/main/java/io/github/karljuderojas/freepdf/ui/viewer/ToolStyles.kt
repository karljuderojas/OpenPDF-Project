package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.annotate.Stamps

/** A tool's colour and line thickness in PDF points (0 for tools without a thickness). */
data class ToolStyle(val color: Color, val width: Float) {
    val rgb: Annotator.Rgb get() = Annotator.Rgb(color.red, color.green, color.blue)
}

/** Light colours that leave text readable underneath: yellow, green, blue, pink, orange. */
internal val HighlightColors = listOf(
    Color(0xFFFFEB3B), Color(0xFF8BE37A), Color(0xFF7FD4FF), Color(0xFFFF99D6), Color(0xFFFFB74D),
)

/** Strong colours for pen, lines and shapes: black, blue, red, green, purple, orange. */
internal val InkColors = listOf(
    Color(0xFF212121), Color(0xFF2166E5), Color(0xFFE52929), Color(0xFF2E9E44), Color(0xFF8E24AA), Color(0xFFF57C00),
)

internal val PenWidths = listOf(1f, 2f, 4f, 8f)
internal val LineWidths = listOf(1f, 2f, 3f)

/** Text box font sizes, in points. */
internal val FontSizes = listOf(10f, 12f, 16f, 24f)

/**
 * Above the Annotate tool strip while a tool is chosen: its colours, then its sizes. The choice
 * is kept per tool, so the pen can stay thin and black while highlights stay yellow.
 */
@Composable
fun StyleBar(tool: AnnotateTool, style: ToolStyle, onStyleChange: (ToolStyle) -> Unit) =
    StyleBar(tool.palette, tool.widths, style, onStyleChange)

/** [StyleBar] for any choice of colours and sizes, such as those a picked mark can change to. */
@Composable
fun StyleBar(palette: List<Color>, widths: List<Float>, style: ToolStyle, onStyleChange: (ToolStyle) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("style-bar")
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            // Sized so six colours and four sizes fit across a phone without scrolling.
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            palette.forEachIndexed { index, color ->
                val label = stringResource(R.string.style_colour, index + 1)
                Swatch(selected = color == style.color, label = label, onClick = { onStyleChange(style.copy(color = color)) }) {
                    Box(Modifier.size(22.dp).background(color, CircleShape).border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape))
                }
            }
            if (widths.isNotEmpty()) {
                VerticalDivider(Modifier.height(28.dp).padding(horizontal = 4.dp))
                widths.forEachIndexed { index, width ->
                    val label = stringResource(R.string.style_size, index + 1)
                    Swatch(selected = width == style.width, label = label, onClick = { onStyleChange(style.copy(width = width)) }) {
                        // Dots grow with the line they draw, from 5dp for the thinnest to 20dp.
                        val dot = 5.dp + 15.dp * index / (widths.size - 1).coerceAtLeast(1)
                        Box(Modifier.size(dot).background(style.color, CircleShape))
                    }
                }
            }
        }
    }
}

/** Above the tool strip while Stamp is chosen: which stamp a tap places, each in its own colour. */
@Composable
fun StampBar(selected: Stamps.Kind, onSelect: (Stamps.Kind) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stamp-bar")
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Stamps.Kind.entries.forEach { kind ->
                val color = Color(kind.color.r, kind.color.g, kind.color.b)
                FilterChip(
                    selected = kind == selected,
                    onClick = { onSelect(kind) },
                    label = { Text(stringResource(kind.labelRes).uppercase(), color = color, fontWeight = FontWeight.Bold) },
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = kind == selected, borderColor = color, selectedBorderColor = color,
                        borderWidth = 1.5.dp, selectedBorderWidth = 2.5.dp,
                    ),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.12f)),
                )
            }
        }
    }
}

/** Above the tool strip while Shapes is chosen: what a drag draws, each with a small picture of it. */
@Composable
fun ShapeBar(selected: Annotator.Shape, onSelect: (Annotator.Shape) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shape-bar")
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val ink = MaterialTheme.colorScheme.onSurface
            Annotator.Shape.entries.forEach { shape ->
                FilterChip(
                    selected = shape == selected,
                    onClick = { onSelect(shape) },
                    label = { Text(stringResource(shape.labelRes)) },
                    leadingIcon = { Canvas(Modifier.size(18.dp)) { drawShapeIcon(shape, ink) } },
                    modifier = Modifier.testTag("shape-${shape.name.lowercase()}"),
                )
            }
        }
    }
}

/** A small picture of [shape]: lines and arrows run corner to corner, up to the right. */
private fun DrawScope.drawShapeIcon(shape: Annotator.Shape, ink: Color) {
    val line = 1.5.dp.toPx()
    val pad = 2.dp.toPx()
    val box = Rect(pad, 4.dp.toPx(), size.width - pad, size.height - 4.dp.toPx())
    val start = Offset(pad, size.height - pad)
    val end = Offset(size.width - pad, pad)
    when (shape) {
        Annotator.Shape.Rectangle -> drawRect(ink, box.topLeft, box.size, style = Stroke(line))
        Annotator.Shape.Ellipse -> drawOval(ink, box.topLeft, box.size, style = Stroke(line))
        Annotator.Shape.Line -> drawLine(ink, start, end, strokeWidth = line, cap = StrokeCap.Round)
        Annotator.Shape.Arrow -> {
            drawLine(ink, start, end, strokeWidth = line, cap = StrokeCap.Round)
            arrowWings(start, end, 6.dp.toPx()).forEach { drawLine(ink, end, it, strokeWidth = line, cap = StrokeCap.Round) }
        }
    }
}

/** The shape's name in the app's language. */
val Annotator.Shape.labelRes: Int
    get() = when (this) {
        Annotator.Shape.Rectangle -> R.string.mark_rectangle
        Annotator.Shape.Ellipse -> R.string.mark_ellipse
        Annotator.Shape.Line -> R.string.mark_line
        Annotator.Shape.Arrow -> R.string.mark_arrow
    }

/** The stamp's name in the app's language; the PDF itself keeps the English label. */
val Stamps.Kind.labelRes: Int
    get() = when (this) {
        Stamps.Kind.Approved -> R.string.stamp_approved
        Stamps.Kind.NotApproved -> R.string.stamp_not_approved
        Stamps.Kind.Draft -> R.string.stamp_draft
        Stamps.Kind.Final -> R.string.stamp_final
        Stamps.Kind.Confidential -> R.string.stamp_confidential
        Stamps.Kind.ForComment -> R.string.stamp_for_comment
        Stamps.Kind.Void -> R.string.stamp_void
    }

/** A 32dp round target with a ring when selected. */
@Composable
private fun Swatch(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier
            .size(32.dp)
            .border(2.dp, ring, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Material's undo and redo arrows. The core icon set the app uses does not include them. */
internal object EditIcons {
    val Undo: ImageVector = icon(
        "Undo",
        "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
            "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
    )
    val Redo: ImageVector = icon(
        "Redo",
        "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 " +
            "4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z",
    )

    private fun icon(name: String, path: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = true,
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
