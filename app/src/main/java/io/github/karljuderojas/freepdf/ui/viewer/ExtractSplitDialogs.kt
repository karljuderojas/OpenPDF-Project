package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.edit.PageRanges
import io.github.karljuderojas.freepdf.pdf.edit.Splitting

/**
 * Asks which pages to save as a new PDF, typed like "1-3, 5" and starting with the selected
 * pages. [onExtract] gets the zero-based pages; the open document is not changed.
 */
@Composable
fun ExtractPagesDialog(pageCount: Int, selectedPages: List<Int>, onDismiss: () -> Unit, onExtract: (List<Int>) -> Unit) {
    var text by rememberSaveable { mutableStateOf(PageRanges.format(selectedPages)) }
    val pages = PageRanges.parse(text, pageCount)
    val showError = pages == null && text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extract_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.extract_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.extract_pages_label)) },
                    supportingText = {
                        Text(
                            if (showError) stringResource(R.string.extract_pages_error, pageCount)
                            else stringResource(R.string.extract_pages_help),
                        )
                    },
                    isError = showError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("extract-pages"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { pages?.let(onExtract) }, enabled = pages != null) {
                Text(stringResource(R.string.tool_extract))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Asks how to split the document: into two after the selected page, or every few pages.
 * [onSplit] gets the parts as zero-based page lists (see [Splitting]); the open document is not
 * changed. Splitting after the selected page is offered only when pages follow it.
 */
@Composable
fun SplitDialog(pageCount: Int, selectedPage: Int, onDismiss: () -> Unit, onSplit: (List<List<Int>>) -> Unit) {
    val canSplitAfterSelected = selectedPage in 0 until pageCount - 1
    var intoTwo by rememberSaveable { mutableStateOf(canSplitAfterSelected) }
    var pagesEach by rememberSaveable { mutableStateOf("1") }
    val parts = if (intoTwo && canSplitAfterSelected) {
        Splitting.intoTwo(pageCount, selectedPage)
    } else {
        pagesEach.toIntOrNull()?.takeIf { it > 0 }?.let { Splitting.everyN(pageCount, it) }
    }
    val resources = LocalResources.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.split_body), style = MaterialTheme.typography.bodyMedium)
                RadioRow(
                    selected = intoTwo && canSplitAfterSelected,
                    enabled = canSplitAfterSelected,
                    onClick = { intoTwo = true },
                    tag = "split-into-two",
                ) {
                    Text(stringResource(R.string.split_after, selectedPage + 1))
                }
                RadioRow(selected = !intoTwo || !canSplitAfterSelected, enabled = true, onClick = { intoTwo = false }, tag = "split-every") {
                    Text(stringResource(R.string.split_every))
                }
                OutlinedTextField(
                    value = pagesEach,
                    onValueChange = { value ->
                        pagesEach = value.filter { it.isDigit() }.take(4)
                        intoTwo = false
                    },
                    label = { Text(stringResource(R.string.split_pages_each)) },
                    isError = pagesEach.toIntOrNull()?.takeIf { it > 0 } == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp).testTag("split-pages-each"),
                )
                if (parts != null) {
                    Text(
                        resources.getQuantityString(R.plurals.split_makes, parts.size, parts.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parts?.let(onSplit) }, enabled = parts != null && parts.size > 1) {
                Text(stringResource(R.string.tool_split))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A radio button whose whole row, label included, selects it. */
@Composable
private fun RadioRow(selected: Boolean, enabled: Boolean, onClick: () -> Unit, tag: String, label: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        val color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
        CompositionLocalProvider(LocalContentColor provides color) {
            Column(Modifier.padding(start = 12.dp)) { label() }
        }
    }
}
