package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import kotlin.math.roundToInt

/**
 * Draws one page's placed stamps over it. Dragging a stamp moves it, dragging the round corner
 * handle of the selected one resizes it, and Delete removes it. Moves are reported as fractions of
 * the page, and resizes as a factor, so the view model can keep each stamp in page terms.
 */
@Composable
fun StampLayer(
    stamps: List<PlacedStamp>,
    selected: Long?,
    onSelect: (Long) -> Unit,
    onMove: (id: Long, delta: Offset) -> Unit,
    onResize: (id: Long, factor: Float) -> Unit,
    onDelete: (Long) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = constraints.maxWidth.toFloat()
        val pageHeight = constraints.maxHeight.toFloat()
        stamps.forEach { stamp ->
            key(stamp.id) {
                StampFrame(
                    stamp = stamp,
                    selected = stamp.id == selected,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    onSelect = { onSelect(stamp.id) },
                    onMove = { onMove(stamp.id, it) },
                    onResize = { onResize(stamp.id, it) },
                )
            }
        }
        stamps.firstOrNull { it.id == selected }?.let { stamp ->
            DeleteChip(stamp.box, pageWidth, pageHeight, onClick = { onDelete(stamp.id) })
        }
    }
}

// Room around a stamp that still grabs it, so even a small checkmark is easy to drag.
private val GrabMargin = 18.dp
private val HandleSize = 18.dp

@Composable
private fun StampFrame(
    stamp: PlacedStamp,
    selected: Boolean,
    pageWidth: Float,
    pageHeight: Float,
    onSelect: () -> Unit,
    onMove: (Offset) -> Unit,
    onResize: (Float) -> Unit,
) {
    // The gesture loops outlive recompositions, so always read the latest values.
    val pageSize by rememberUpdatedState(Offset(pageWidth, pageHeight))
    val box by rememberUpdatedState(stamp.box)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)

    val density = LocalDensity.current
    val margin = with(density) { GrabMargin.toPx() }
    val widthPx = stamp.box.width * pageWidth
    val heightPx = stamp.box.height * pageHeight
    val outline = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .offset { IntOffset((stamp.box.left * pageWidth - margin).roundToInt(), (stamp.box.top * pageHeight - margin).roundToInt()) }
            .size(with(density) { (widthPx + 2 * margin).toDp() }, with(density) { (heightPx + 2 * margin).toDp() })
            .testTag("stamp-${stamp.id}")
            .pointerInput(Unit) { detectTapGestures { currentOnSelect() } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { currentOnSelect() }) { change, drag ->
                    change.consume()
                    currentOnMove(Offset(drag.x / pageSize.x, drag.y / pageSize.y))
                }
            },
    ) {
        Box(
            Modifier
                .padding(GrabMargin)
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        outline,
                        style = if (selected) {
                            Stroke(2.dp.toPx())
                        } else {
                            Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                        },
                    )
                },
        ) {
            StampPreview(stamp.content, heightPx)
        }
        if (selected) {
            val resize = stringResource(R.string.stamp_resize)
            // Centred on the stamp's bottom-right corner, with a touch area twice its drawn size.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = GrabMargin - HandleSize, bottom = GrabMargin - HandleSize)
                    .size(HandleSize * 2)
                    .testTag("stamp-resize-${stamp.id}")
                    .semantics { contentDescription = resize }
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val w = box.width * pageSize.x
                            val h = box.height * pageSize.y
                            // Follow the diagonal: the corner tracks the finger in both directions.
                            val grown = w + (drag.x + drag.y * w / h) / 2
                            if (w > 0f) currentOnResize(grown / w)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(HandleSize)
                        .background(Color.White, CircleShape)
                        .border(2.dp, outline, CircleShape),
                )
            }
        }
    }
}

/** What the stamp will look like in the PDF, filling its box. */
@Composable
private fun StampPreview(content: StampContent, heightPx: Float) {
    when (content) {
        is StampContent.Image -> Image(
            content.image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        is StampContent.Signature -> Image(
            content.image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        is StampContent.Text -> {
            val lines = content.lines
            val fontPx = heightPx / (lines.size * 1.2f)
            val style = with(LocalDensity.current) {
                TextStyle(
                    color = Color.Black,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = fontPx.toSp(),
                    lineHeight = (fontPx * 1.2f).toSp(),
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                )
            }
            Text(lines.joinToString("\n"), style = style, softWrap = false, maxLines = lines.size)
        }
        StampContent.Checkmark -> Canvas(Modifier.fillMaxSize()) {
            // The same strokes as PageEditor.addCheckmark, in screen coordinates.
            val s = minOf(size.width, size.height)
            val path = Path().apply {
                moveTo(0f, 0.6f * s)
                lineTo(0.4f * s, s)
                lineTo(s, 0f)
            }
            drawPath(path, Color.Black, style = Stroke(s / 6, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A labelled Delete button just above the selected stamp, or below it at the top of the page. */
@Composable
private fun DeleteChip(box: StampBox, pageWidth: Float, pageHeight: Float, onClick: () -> Unit) {
    val density = LocalDensity.current
    val gap = with(density) { (GrabMargin + 36.dp).toPx() }
    val below = box.top * pageHeight < gap
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 2.dp,
        modifier = Modifier
            .offset {
                val y = if (below) box.bottom * pageHeight + with(density) { GrabMargin.toPx() } else box.top * pageHeight - gap
                IntOffset((box.left * pageWidth).roundToInt(), y.roundToInt())
            }
            .testTag("stamp-delete"),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.stamp_delete),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
