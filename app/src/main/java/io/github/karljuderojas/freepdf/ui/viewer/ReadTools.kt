package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.render.OutlineItem

/** The search field that replaces Read mode's title while searching. */
@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, focusRequester: FocusRequester) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("search-field"),
    )
}

/** "3 of 12" with previous and next, or why there is nothing to step through. */
@Composable
internal fun SearchStepper(results: SearchResults, current: Int, onStep: (Int) -> Unit) {
    if (results.query.isEmpty()) return
    val count = results.matches.size
    Text(
        when {
            count > 0 -> stringResource(R.string.search_count, current + 1, count)
            results.finished -> stringResource(R.string.search_none)
            else -> stringResource(R.string.search_searching)
        },
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
    IconButton(onClick = { onStep(-1) }, enabled = count > 0) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.search_previous))
    }
    IconButton(onClick = { onStep(1) }, enabled = count > 0) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.search_next))
    }
}

/** Marks every match on one page, the [current] one more strongly. */
@Composable
internal fun SearchHighlights(matches: List<IndexedValue<TextMatch>>, current: Int) {
    Canvas(Modifier.fillMaxSize()) {
        matches.forEach { (index, match) ->
            val color = if (index == current) CURRENT_MATCH else OTHER_MATCH
            match.boxes.forEach { box ->
                // A little room around the letters, which PDFium boxes tightly.
                val pad = 1.5.dp.toPx()
                drawRect(
                    color = color,
                    topLeft = Offset(box.left * size.width - pad, box.top * size.height - pad),
                    size = Size((box.right - box.left) * size.width + 2 * pad, (box.bottom - box.top) * size.height + 2 * pad),
                )
            }
        }
    }
}

private val CURRENT_MATCH = Color(0x80FF8F00)
private val OTHER_MATCH = Color(0x66FFD600)

/** Asks for a page number and goes there. */
@Composable
internal fun GoToPageDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (page: Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.page_number_label)) },
                supportingText = { Text(stringResource(R.string.go_to_page_range, pageCount)) },
                isError = text.isNotEmpty() && page == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { page?.let { onGo(it - 1) } }),
                modifier = Modifier.fillMaxWidth().testTag("page-number-field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { page?.let { onGo(it - 1) } }, enabled = page != null) { Text(stringResource(R.string.go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The PDF's table of contents. Choosing an entry goes to its page. */
@Composable
internal fun OutlineDialog(outline: List<OutlineItem>, onDismiss: () -> Unit, onGo: (page: Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contents)) },
        text = { OutlineList(outline, onGo, Modifier.heightIn(max = 420.dp)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** The entries of the table of contents, indented by depth, each with its page number. */
@Composable
internal fun OutlineList(outline: List<OutlineItem>, onGo: (page: Int) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier) {
        items(outline) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGo(item.page) }
                    .padding(start = (item.depth * 16).dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.title,
                    style = if (item.depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    (item.page + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
