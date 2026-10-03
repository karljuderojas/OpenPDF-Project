package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.ui.files.PdfBadge

/** The top bar's count of open documents, drawn as a small outlined square. Tapping it shows the switcher. */
@Composable
internal fun OpenDocumentsButton(count: Int, onClick: () -> Unit) {
    val description = stringResource(R.string.open_documents_switch, count)
    IconButton(onClick = onClick, modifier = Modifier.testTag("open-documents").semantics { contentDescription = description }) {
        Box(
            Modifier
                .size(22.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(5.dp))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Lists the open [documents] so the reader can jump between them. The one on screen, [currentUri],
 * is outlined, and those in [unsaved] say they have changes not saved yet. Each keeps its own
 * session, so switching loses nothing; closing one goes through the caller, which asks about
 * unsaved changes first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenDocumentsSheet(
    documents: List<DocumentEntry>,
    currentUri: String?,
    unsaved: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onSwitchTo: (DocumentEntry) -> Unit,
    onClose: (DocumentEntry) -> Unit,
    onCloseAll: () -> Unit,
    onOpenAnother: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.open_documents_title, documents.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(documents, key = { it.uri }) { entry ->
                OpenDocumentCard(entry, current = entry.uri == currentUri, unsaved = entry.uri in unsaved, onSwitchTo = onSwitchTo, onClose = onClose)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCloseAll, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.open_documents_close_all))
            }
            Button(onClick = onOpenAnother, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.open_documents_open_another))
            }
        }
    }
}

@Composable
private fun OpenDocumentCard(
    entry: DocumentEntry,
    current: Boolean,
    unsaved: Boolean,
    onSwitchTo: (DocumentEntry) -> Unit,
    onClose: (DocumentEntry) -> Unit,
) {
    Card(
        onClick = { onSwitchTo(entry) },
        modifier = Modifier.fillMaxWidth(),
        border = if (current) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PdfBadge(Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = listOfNotNull(
                    stringResource(R.string.open_documents_viewing).takeIf { current },
                    stringResource(R.string.open_documents_unsaved).takeIf { unsaved },
                )
                if (status.isNotEmpty()) {
                    Text(
                        status.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { onClose(entry) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_document, entry.name))
            }
        }
    }
}
