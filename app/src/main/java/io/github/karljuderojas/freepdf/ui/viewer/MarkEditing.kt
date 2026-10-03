package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.annotate.Mark
import java.text.DateFormat
import java.util.Date

/** The Annotate tool whose colours and sizes suit a mark of this kind, or null if it has none to change. */
val Mark.Kind.tool: AnnotateTool?
    get() = when (this) {
        Mark.Kind.Highlight -> AnnotateTool.Highlight
        Mark.Kind.Underline -> AnnotateTool.Underline
        Mark.Kind.StrikeOut -> AnnotateTool.StrikeOut
        Mark.Kind.Ink -> AnnotateTool.Pen
        Mark.Kind.Square, Mark.Kind.Circle -> AnnotateTool.Shapes
        Mark.Kind.Note -> AnnotateTool.Note
        Mark.Kind.TextBox -> AnnotateTool.TextBox
        Mark.Kind.Stamp, Mark.Kind.Other -> null
    }

/**
 * The colours a mark of this kind can be changed to. A stamp this app placed is redrawn in the
 * new colour; marks made elsewhere ([Mark.Kind.Other]) keep the look they came with, so none.
 */
val Mark.Kind.palette: List<Color>
    get() = when (this) {
        Mark.Kind.Stamp -> InkColors
        else -> tool?.palette.orEmpty()
    }

/** The line widths or font sizes a mark of this kind can be changed to; empty when it has none. */
val Mark.Kind.widths: List<Float> get() = tool?.widths.orEmpty()

val Mark.Kind.label: Int
    get() = when (this) {
        Mark.Kind.Highlight -> R.string.tool_highlight
        Mark.Kind.Underline -> R.string.tool_underline
        Mark.Kind.StrikeOut -> R.string.tool_strikeout
        Mark.Kind.Ink -> R.string.mark_drawing
        Mark.Kind.Square -> R.string.mark_rectangle
        Mark.Kind.Circle -> R.string.mark_ellipse
        Mark.Kind.Note -> R.string.tool_note
        Mark.Kind.TextBox -> R.string.tool_text_box
        Mark.Kind.Stamp -> R.string.tool_stamp
        Mark.Kind.Other -> R.string.mark_other
    }

/** The mark's colour on screen, or grey for marks without one. */
val Mark.displayColor: Color get() = color?.let { Color(it.r, it.g, it.b) } ?: Color.Gray

/** The mark's current look, in the shape the style bar takes. */
val Mark.style: ToolStyle get() = ToolStyle(displayColor, width)

private fun Mark.contains(point: Offset, slop: Float) =
    point.x in left - slop..right + slop && point.y in top - slop..bottom + slop

/**
 * Over one page while reading or annotating without a tool: a tap on a mark picks it for
 * editing, and a tap anywhere else lets go of it. Taps that miss every mark pass through, so
 * double-tap to reset zoom still works. The picked mark gets a dashed outline.
 *
 * [content] (the text selection layer) sits inside, so it sees each touch first: while text
 * is selected, a tap clears that selection rather than picking a mark.
 */
@Composable
fun MarkTapLayer(
    page: Int,
    marks: List<Mark>,
    selected: Mark?,
    onSelect: (Mark?) -> Unit,
    content: @Composable () -> Unit,
) {
    val currentMarks by rememberUpdatedState(marks.filter { it.page == page })
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val outline = MaterialTheme.colorScheme.primary
    val pad = with(LocalDensity.current) { 4.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .testTag("mark-layer-$page")
            .pointerInput(page) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    // A press held long enough to select text is not a tap.
                    if (up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                    val at = Offset(up.position.x / size.width, up.position.y / size.height)
                    // Topmost first, a few pixels of give around small marks.
                    val hit = currentMarks.lastOrNull { it.contains(at, slop = 0.01f) }
                    when {
                        hit != null -> {
                            up.consume()
                            currentOnSelect(hit)
                        }
                        currentSelected?.page == page -> currentOnSelect(null)
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val mark = selected?.takeIf { it.page == page } ?: return@drawWithContent
                drawRect(
                    outline,
                    Offset(mark.left * size.width - pad, mark.top * size.height - pad),
                    Size((mark.right - mark.left) * size.width + 2 * pad, (mark.bottom - mark.top) * size.height + 2 * pad),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            },
    ) {
        content()
    }
}

/**
 * Replaces the bottom bar while a mark is picked: its colours and sizes (applied at once, each
 * change one undo step), a comment, Delete, and Done to let go of it.
 */
@Composable
fun MarkEditBar(
    mark: Mark,
    onStyle: (ToolStyle) -> Unit,
    onComment: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.testTag("mark-edit-bar")) {
        if (mark.kind.palette.isNotEmpty() || mark.kind.widths.isNotEmpty()) {
            StyleBar(mark.kind.palette, mark.kind.widths, mark.style, onStyle)
        }
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(mark.kind.label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onComment) {
                    Text(
                        stringResource(
                            when {
                                // A text box's comment is its text.
                                mark.kind == Mark.Kind.TextBox -> R.string.mark_edit_text
                                mark.comment.isBlank() -> R.string.mark_add_comment
                                else -> R.string.mark_edit_comment
                            },
                        ),
                    )
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.tool_delete)) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

/** Every mark in the document, page by page; tapping one goes to it and picks it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(marks: List<Mark>, onDismiss: () -> Unit, onOpen: (Mark) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.comments_title, marks.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (marks.isEmpty()) {
            Text(
                stringResource(R.string.comments_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().testTag("comments-list")) {
            marks.groupBy { it.page }.forEach { (page, onPage) ->
                item(key = "page-$page") {
                    Text(
                        stringResource(R.string.comments_page, page + 1),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(onPage, key = { "${it.page}/${it.index}" }) { mark ->
                    CommentRow(mark, onClick = { onOpen(mark) })
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentRow(mark: Mark, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.padding(top = 4.dp).size(16.dp).background(mark.displayColor, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(stringResource(mark.kind.label), style = MaterialTheme.typography.titleSmall)
            if (mark.markedText.isNotBlank()) {
                Text(
                    "“${mark.markedText}”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (mark.comment.isNotBlank()) {
                Text(mark.comment, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            val byline = listOfNotNull(
                mark.author,
                mark.modified?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) },
            ).joinToString(" · ")
            if (byline.isNotEmpty()) {
                Text(byline, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
