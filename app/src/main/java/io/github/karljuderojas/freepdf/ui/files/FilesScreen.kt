package io.github.karljuderojas.freepdf.ui.files

import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.karljuderojas.freepdf.FreePdfApp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.files.SafeWrite
import io.github.karljuderojas.freepdf.share.Sharing
import io.github.karljuderojas.freepdf.ui.rememberPdfPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** The Files tab: PDFs open now, recent history grouped by day, and Open file. */
@Composable
fun FilesScreen(onOpenPdf: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val documents = (context.applicationContext as FreePdfApp).documents
    val open by documents.open.collectAsStateWithLifecycle()
    val recent by documents.recent.collectAsStateWithLifecycle()
    val sessions = (context.applicationContext as FreePdfApp).sessions
    val unsaved by sessions.unsaved.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf<DocumentEntry?>(null) }

    val openFile = rememberPdfPicker(onOpenPdf)

    FilesContent(
        open = open,
        recent = recent,
        onOpenFile = openFile,
        onOpen = { onOpenPdf(Uri.parse(it.uri)) },
        unsaved = unsaved,
        // Like the viewer, closing a document with unsaved changes asks first.
        onClose = { if (it.uri in unsaved) closing = it else documents.close(it.uri) },
        onShare = { entry ->
            // The file may be gone or the grant revoked since it was last opened.
            runCatching { Sharing.shareUri(context, Uri.parse(entry.uri), entry.name) }
                .onFailure { Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show() }
        },
        onForget = { documents.forget(it.uri) },
        modifier = modifier,
    )

    closing?.let { entry ->
        UnsavedCloseDialog(
            onSave = {
                closing = null
                val session = sessions.get(entry.uri)
                if (session == null) {
                    documents.close(entry.uri)
                } else if (session.stamps.isNotEmpty()) {
                    // Stamps still being placed are written into the PDF by the viewer, so they
                    // are not lost here: the document stays open to be finished there.
                    Toast.makeText(context, R.string.save_open_to_finish, Toast.LENGTH_LONG).show()
                } else {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                SafeWrite.write(context, session.uri, session.session.workingFile)
                                session.session.markSaved()
                            }.isSuccess
                        }
                        sessions.refresh()
                        // A file that cannot be written here stays open; the viewer offers Save As.
                        if (saved) {
                            Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                            documents.close(entry.uri)
                        } else {
                            Toast.makeText(context, R.string.save_failed_open_to_save_a_copy, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDiscard = {
                closing = null
                documents.close(entry.uri)
            },
            onCancel = { closing = null },
        )
    }
}

/** The save-or-discard question the viewer asks, for closing a document from the Files tab. */
@Composable
fun UnsavedCloseDialog(onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.unsaved_title)) },
        text = { Text(stringResource(R.string.unsaved_body)) },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) }
            }
        },
    )
}

/** Stateless Files UI, for screenshot tests. [now] decides the Today/Yesterday grouping. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesContent(
    open: List<DocumentEntry>,
    recent: List<DocumentEntry>,
    onOpenFile: () -> Unit,
    onOpen: (DocumentEntry) -> Unit,
    onClose: (DocumentEntry) -> Unit,
    onShare: (DocumentEntry) -> Unit,
    onForget: (DocumentEntry) -> Unit,
    modifier: Modifier = Modifier,
    unsaved: Set<String> = emptySet(),
    now: Long = System.currentTimeMillis(),
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.files_title)) }) },
        floatingActionButton = {
            if (open.isNotEmpty() || recent.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onOpenFile,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.open_file)) },
                )
            }
        },
    ) { padding ->
        if (open.isEmpty() && recent.isEmpty()) {
            EmptyFiles(onOpenFile, Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (open.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.files_open_now)) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(open, key = { it.uri }) { OpenCard(it, it.uri in unsaved, onOpen, onClose) }
                    }
                }
            }
            groupByDay(recent, now).forEach { (label, entries) ->
                item(key = "header-$label") { SectionHeader(stringResource(label)) }
                items(entries, key = { "recent-${it.uri}" }) { RecentRow(it, onOpen, onShare, onForget) }
            }
        }
    }
}

@Composable
private fun EmptyFiles(onOpenFile: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onOpenFile) { Text(stringResource(R.string.open_pdf)) }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.files_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
internal fun PdfBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun OpenCard(entry: DocumentEntry, unsaved: Boolean, onOpen: (DocumentEntry) -> Unit, onClose: (DocumentEntry) -> Unit) {
    Card(onClick = { onOpen(entry) }, modifier = Modifier.width(180.dp)) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PdfBadge(Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (unsaved) {
                    Text(
                        stringResource(R.string.open_documents_unsaved),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = { onClose(entry) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_document, entry.name))
            }
        }
    }
}

@Composable
internal fun RecentRow(
    entry: DocumentEntry,
    onOpen: (DocumentEntry) -> Unit,
    onShare: (DocumentEntry) -> Unit,
    onForget: (DocumentEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(entry) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdfBadge(Modifier.size(40.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                DateUtils.formatDateTime(context, entry.openedAt, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_actions, entry.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.tool_share)) }, onClick = { menu = false; onShare(entry) })
                DropdownMenuItem(text = { Text(stringResource(R.string.forget_recent)) }, onClick = { menu = false; onForget(entry) })
            }
        }
    }
}

/** Splits [entries] (newest first) into Today, Yesterday and Earlier, skipping empty groups. */
internal fun groupByDay(entries: List<DocumentEntry>, now: Long): List<Pair<Int, List<DocumentEntry>>> {
    val startOfToday = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfYesterday = Calendar.getInstance().apply { timeInMillis = startOfToday; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
    return listOf(
        R.string.files_today to entries.filter { it.openedAt >= startOfToday },
        R.string.files_yesterday to entries.filter { it.openedAt in startOfYesterday until startOfToday },
        R.string.files_earlier to entries.filter { it.openedAt < startOfYesterday },
    ).filter { it.second.isNotEmpty() }
}
