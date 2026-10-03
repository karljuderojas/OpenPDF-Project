package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.links.LinkTarget
import io.github.karljuderojas.freepdf.pdf.links.PageLink
import io.github.karljuderojas.freepdf.pdf.links.PageLinks

/**
 * Makes the links of one page tappable while reading. The areas draw nothing, as in other PDF
 * readers. [content] (the mark and text selection layers) sits inside, so it sees each touch
 * first: a press held long enough to select text, or a tap a mark takes, never opens a link,
 * and a tap that lands on no link still reaches the page underneath.
 */
@Composable
fun LinkLayer(page: Int, links: List<PageLink>, onOpen: (PageLink) -> Unit, content: @Composable () -> Unit) {
    val description = stringResource(R.string.link_description)
    val currentLinks by rememberUpdatedState(links)
    val currentOnOpen by rememberUpdatedState(onOpen)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(page) { detectLinkTaps({ currentLinks }) { currentOnOpen(it) } },
    ) {
        content()
        links.forEachIndexed { i, link ->
            // Nothing to draw and no touch handling of its own: this only names the link for
            // accessibility and tests, so a tap on it goes to the layer like any other.
            val box = link.box
            Box(
                Modifier
                    .offset(maxWidth * box.left, maxHeight * box.top)
                    .size(maxWidth * (box.right - box.left), maxHeight * (box.bottom - box.top))
                    .testTag("link-$page-$i")
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                        onClick { onOpen(link); true }
                    },
            )
        }
    }
}

/**
 * A quick tap on one of [links] (read when the tap lands, so the list may change) calls [onHit]
 * with it and takes the release; anything longer, moved or already taken by a layer inside passes.
 */
private suspend fun PointerInputScope.detectLinkTaps(links: () -> List<PageLink>, onHit: (PageLink) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        // A press held long enough to select text is not a tap.
        if (up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
        val at = Offset(up.position.x / size.width, up.position.y / size.height)
        val hit = links().lastOrNull { it.box.contains(at) } ?: return@awaitEachGesture
        up.consume()
        onHit(hit)
    }
}

private fun DisplayRect.contains(point: Offset) = point.x in left..right && point.y in top..bottom

/**
 * Sits over one page while Edit's Add link is chosen: drag a box over the words or area to link.
 * [onBox] gets it as fractions of the page as shown (origin top-left); tiny drags are ignored, so
 * a stray touch adds nothing. One-finger drags draw here instead of scrolling. The page's
 * [links] are outlined, and a tap on one gives it to [onPick] to change or remove.
 */
@Composable
fun LinkBoxLayer(page: Int, links: List<PageLink>, onBox: (DisplayRect) -> Unit, onPick: (PageLink) -> Unit) {
    val currentOnBox by rememberUpdatedState(onBox)
    val currentOnPick by rememberUpdatedState(onPick)
    val currentLinks by rememberUpdatedState(links)
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    var box by remember { mutableStateOf<Rect?>(null) }
    val colour = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.link_description)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("link-box-layer-$page")
                .onSizeChanged { layerSize = it }
                // Outside the drag handling, so a drag that starts on a link still draws a box.
                .pointerInput(page) { detectLinkTaps({ currentLinks }) { currentOnPick(it) } }
                .pointerInput(Unit) {
                    var start = Offset.Zero
                    detectDragGestures(
                        onDragStart = { start = it; box = Rect(it, it) },
                        onDrag = { change, _ ->
                            change.consume()
                            box = Rect(start, change.position)
                        },
                        onDragEnd = {
                            box?.let { raw ->
                                val w = layerSize.width.toFloat()
                                val h = layerSize.height.toFloat()
                                val rect = DisplayRect(
                                    (minOf(raw.left, raw.right) / w).coerceIn(0f, 1f),
                                    (minOf(raw.top, raw.bottom) / h).coerceIn(0f, 1f),
                                    (maxOf(raw.left, raw.right) / w).coerceIn(0f, 1f),
                                    (maxOf(raw.top, raw.bottom) / h).coerceIn(0f, 1f),
                                )
                                if (rect.right - rect.left >= MIN_SIDE && rect.bottom - rect.top >= MIN_SIDE) currentOnBox(rect)
                            }
                            box = null
                        },
                        onDragCancel = { box = null },
                    )
                },
        ) {
            links.forEach { link ->
                val topLeft = Offset(link.box.left * this.size.width, link.box.top * this.size.height)
                val extent = Size((link.box.right - link.box.left) * this.size.width, (link.box.bottom - link.box.top) * this.size.height)
                drawRect(colour.copy(alpha = 0.12f), topLeft, extent)
                drawRect(colour, topLeft, extent, style = Stroke(1.dp.toPx()))
            }
            box?.let { raw ->
                val r = Rect(minOf(raw.left, raw.right), minOf(raw.top, raw.bottom), maxOf(raw.left, raw.right), maxOf(raw.top, raw.bottom))
                drawRect(colour.copy(alpha = 0.15f), r.topLeft, r.size)
                drawRect(colour, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
            }
        }
        links.forEachIndexed { i, link ->
            // Names the link for accessibility and tests; touches go through to the canvas.
            val area = link.box
            Box(
                Modifier
                    .offset(maxWidth * area.left, maxHeight * area.top)
                    .size(maxWidth * (area.right - area.left), maxHeight * (area.bottom - area.top))
                    .testTag("edit-link-$page-$i")
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                        onClick { onPick(link); true }
                    },
            )
        }
    }
}

/** Smallest side of a new link as a fraction of the page: about the height of a line of text. */
private const val MIN_SIDE = 0.015f

/** Asks before leaving the app for [address], so a PDF cannot send the reader somewhere unseen. */
@Composable
fun OpenLinkDialog(address: String, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_open_title)) },
        text = { Text(address, Modifier.testTag("link-address"), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.link_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Asks where a new link should lead: a web address, or a page of this PDF. [onAdd] gets the
 * target, with the address tidied (see [PageLinks.normaliseAddress]) and the page zero-based.
 * With [existing], the link a tap picked, the dialog opens on where that link leads so it can
 * be changed, and offers to remove it through [onRemove].
 */
@Composable
fun AddLinkDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onAdd: (LinkTarget) -> Unit,
    existing: LinkTarget? = null,
    onRemove: () -> Unit = {},
) {
    var toPage by rememberSaveable { mutableStateOf(existing is LinkTarget.Page) }
    var address by rememberSaveable { mutableStateOf((existing as? LinkTarget.Web)?.uri.orEmpty()) }
    var pageText by rememberSaveable { mutableStateOf((existing as? LinkTarget.Page)?.let { "${it.index + 1}" }.orEmpty()) }
    val web = PageLinks.normaliseAddress(address)
    val pageNumber = pageText.toIntOrNull()?.takeIf { it in 1..pageCount }
    val target: LinkTarget? = if (toPage) pageNumber?.let { LinkTarget.Page(it - 1) } else web?.let { LinkTarget.Web(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.link_add_title else R.string.link_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinkChoice(!toPage, stringResource(R.string.link_web_option), "link-web-option") { toPage = false }
                LinkChoice(toPage, stringResource(R.string.link_page_option), "link-page-option") { toPage = true }
                if (toPage) {
                    OutlinedTextField(
                        value = pageText,
                        onValueChange = { pageText = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.link_page_label)) },
                        supportingText = { Text(stringResource(R.string.link_page_help, pageCount)) },
                        isError = pageText.isNotEmpty() && pageNumber == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("link-page"),
                    )
                } else {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.link_web_label)) },
                        supportingText = { Text(stringResource(R.string.link_web_help)) },
                        isError = address.isNotBlank() && web == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().testTag("link-web"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onAdd) }, enabled = target != null) {
                Text(stringResource(if (existing == null) R.string.link_add else R.string.link_change))
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onRemove, modifier = Modifier.testTag("link-remove")) { Text(stringResource(R.string.link_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun LinkChoice(selected: Boolean, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
