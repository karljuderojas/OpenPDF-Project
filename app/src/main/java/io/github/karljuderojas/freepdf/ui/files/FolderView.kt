package io.github.karljuderojas.freepdf.ui.files

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import io.github.karljuderojas.freepdf.files.DeleteResult
import io.github.karljuderojas.freepdf.files.FileActions
import io.github.karljuderojas.freepdf.files.RenameResult
import io.github.karljuderojas.freepdf.files.Support
import io.github.karljuderojas.freepdf.files.documentAlias
import io.github.karljuderojas.freepdf.share.Sharing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.github.karljuderojas.freepdf.FreePdfApp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.FolderFile
import io.github.karljuderojas.freepdf.files.FolderListing
import io.github.karljuderojas.freepdf.files.FolderReader
import io.github.karljuderojas.freepdf.files.FolderSort
import io.github.karljuderojas.freepdf.files.sortFolderFiles
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Folder view of the Files tab: the PDFs in the folder the user chose, read when shown and on pull to refresh. */
@Composable
fun FolderScreen(onOpenPdf: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = (context.applicationContext as FreePdfApp).folders
    val folder by store.folder.collectAsState()
    var listing by remember { mutableStateOf<FolderListing?>(null) }
    var unreadable by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableStateOf(FolderSort.Name) }
    val scope = rememberCoroutineScope()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) store.choose(context, tree)
    }

    LaunchedEffect(folder, reload) {
        val tree = folder
        if (tree == null) {
            listing = null
            unreadable = false
            return@LaunchedEffect
        }
        refreshing = true
        val read = withContext(Dispatchers.IO) { FolderReader.read(context.contentResolver, Uri.parse(tree)) }
        listing = read
        unreadable = read == null
        refreshing = false
    }

    val documents = (context.applicationContext as FreePdfApp).documents
    val sessions = (context.applicationContext as FreePdfApp).sessions
    // Renaming and deleting need the write grant; with a read-only one they are not offered.
    val writable = remember(folder) { store.canWrite(context) }
    // The file the rename or delete question is about, by URI, found again in the listing so the
    // dialog survives a turn (the listing is read again after it) and goes if the file does.
    var renamingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var forgetting by rememberSaveable { mutableStateOf(false) }
    val renaming = renamingUri?.let { uri -> listing?.files?.firstOrNull { it.uri == uri } }
    val deleting = deletingUri?.let { uri -> listing?.files?.firstOrNull { it.uri == uri } }
    // The same file may be open under the URI the file picker gave it, so both spellings are looked at.
    fun keysOf(file: FolderFile): List<String> = listOfNotNull(file.uri, documentAlias(Uri.parse(file.uri))?.toString())
    // A file open here with unsaved changes is left alone, like in the Recent view.
    fun blockedByUnsaved(file: FolderFile): Boolean {
        val blocked = keysOf(file).any { sessions.get(it)?.hasUnsavedChanges == true }
        if (blocked) Toast.makeText(context, resources.getString(R.string.file_has_unsaved_changes, file.name), Toast.LENGTH_LONG).show()
        return blocked
    }

    FolderContent(
        folderName = listing?.name,
        files = listing?.files,
        unreadable = unreadable,
        // Right after a turn the chosen folder is read again: the spinner, not the Choose screen, meanwhile.
        refreshing = refreshing || (folder != null && listing == null && !unreadable),
        sort = sort,
        onSort = { sort = it },
        onRefresh = { scope.launch { reload++ } },
        onChoose = { pick.launch(null) },
        onForget = { forgetting = true },
        onOpen = { file ->
            // Already open as the picker's URI: go to that session rather than opening the file a second time.
            val alias = documentAlias(Uri.parse(file.uri))?.toString()
            val open = alias?.takeIf { a -> documents.open.value.any { it.uri == a } }
            onOpenPdf(Uri.parse(open ?: file.uri))
        },
        onShare = { file ->
            runCatching { Sharing.shareUri(context, Uri.parse(file.uri), file.name) }
                .onFailure { Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show() }
        },
        onRename = if (writable) { it -> if (!blockedByUnsaved(it)) renamingUri = it.uri } else null,
        onDelete = if (writable) { it -> if (!blockedByUnsaved(it)) deletingUri = it.uri } else null,
        modifier = modifier,
    )

    if (forgetting) {
        ForgetFolderDialog(
            folderName = listing?.name ?: stringResource(R.string.folder_title),
            onForget = {
                forgetting = false
                store.forget(context)
            },
            onCancel = { forgetting = false },
        )
    }

    renaming?.let { file ->
        var error by remember(file.uri) { mutableStateOf<Int?>(null) }
        RenameDialog(
            currentName = file.name,
            error = error,
            onConfirm = { typed ->
                val name = FileActions.cleanName(typed)
                when {
                    name == null -> error = R.string.rename_invalid
                    name == file.name -> renamingUri = null
                    blockedByUnsaved(file) -> renamingUri = null
                    else -> scope.launch {
                        when (val result = withContext(Dispatchers.IO) { FileActions.rename(context, Uri.parse(file.uri), name) }) {
                            is RenameResult.Renamed -> {
                                renamingUri = null
                                keysOf(file).forEach { key ->
                                    sessions.close(key)
                                    documents.renamed(key, result.uri.toString(), result.name)
                                }
                                if (!result.persisted) documents.forget(result.uri.toString())
                                Toast.makeText(context, resources.getString(R.string.renamed_toast, result.name), Toast.LENGTH_SHORT).show()
                                reload++
                            }
                            RenameResult.Unsupported -> error = R.string.rename_unsupported
                            RenameResult.Gone -> error = R.string.rename_gone
                            RenameResult.Failed -> error = R.string.rename_failed
                        }
                    }
                }
            },
            onCancel = { renamingUri = null },
        )
    }

    deleting?.let { file ->
        val uri = Uri.parse(file.uri)
        // Asking the provider can be slow, so it runs off the main thread; the dialog waits with Delete disabled.
        var support by remember(file.uri) { mutableStateOf<Support?>(null) }
        LaunchedEffect(file.uri) { support = withContext(Dispatchers.IO) { FileActions.deleteSupport(context, uri) } }
        DeleteDialog(
            name = file.name,
            support = support,
            onDelete = {
                deletingUri = null
                if (blockedByUnsaved(file)) return@DeleteDialog
                scope.launch {
                    when (withContext(Dispatchers.IO) { FileActions.delete(context, uri) }) {
                        DeleteResult.Deleted -> {
                            keysOf(file).forEach { documents.close(it); documents.forget(it) }
                            Toast.makeText(context, resources.getString(R.string.deleted_toast, file.name), Toast.LENGTH_SHORT).show()
                            reload++
                        }
                        DeleteResult.Gone -> {
                            keysOf(file).forEach { documents.close(it); documents.forget(it) }
                            Toast.makeText(context, resources.getString(R.string.delete_gone_toast, file.name), Toast.LENGTH_LONG).show()
                            reload++
                        }
                        DeleteResult.Unsupported, DeleteResult.Failed ->
                            Toast.makeText(context, R.string.delete_failed, Toast.LENGTH_LONG).show()
                    }
                }
            },
            // There is no list to take it off here: the folder shows whatever is in it, so it is read again.
            onRemoveFromList = { deletingUri = null; reload++ },
            onCancel = { deletingUri = null },
        )
    }
}

/** Asks before the folder's access is given back, since its PDFs leave the Recent list with it. */
@Composable
fun ForgetFolderDialog(folderName: String, onForget: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.folder_forget_title)) },
        text = { Text(stringResource(R.string.folder_forget_body, folderName)) },
        confirmButton = { TextButton(onClick = onForget) { Text(stringResource(R.string.folder_forget_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Stateless Folder view, for screenshot tests. [files] is null before a folder is chosen (or
 * while it is read for the first time) and [unreadable] says the chosen folder can no longer be
 * read. [menuFor] opens a file's menu, for screenshots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContent(
    folderName: String?,
    files: List<FolderFile>?,
    unreadable: Boolean,
    refreshing: Boolean,
    sort: FolderSort,
    onSort: (FolderSort) -> Unit,
    onRefresh: () -> Unit,
    onChoose: () -> Unit,
    onForget: () -> Unit,
    onOpen: (FolderFile) -> Unit,
    modifier: Modifier = Modifier,
    onShare: (FolderFile) -> Unit = {},
    onRename: ((FolderFile) -> Unit)? = {},
    onDelete: ((FolderFile) -> Unit)? = {},
    menuFor: String? = null,
) {
    if (files == null && !unreadable && folderName == null && !refreshing) {
        NoFolder(onChoose, modifier)
        return
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    folderName ?: stringResource(R.string.folder_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onChoose) { Text(stringResource(R.string.folder_change)) }
            TextButton(onClick = onForget) { Text(stringResource(R.string.folder_forget)) }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sort == FolderSort.Name, onClick = { onSort(FolderSort.Name) }, label = { Text(stringResource(R.string.folder_sort_name)) })
            FilterChip(selected = sort == FolderSort.Date, onClick = { onSort(FolderSort.Date) }, label = { Text(stringResource(R.string.folder_sort_date)) })
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            val sorted = remember(files, sort) { files?.let { sortFolderFiles(it, sort) }.orEmpty() }
            when {
                unreadable -> Message(stringResource(R.string.folder_unreadable))
                files != null && sorted.isEmpty() -> Message(stringResource(R.string.folder_no_pdfs))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(sorted, key = { it.uri }) { FolderRow(it, onOpen, onShare, onRename, onDelete, menuOpen = it.uri == menuFor) }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp)) {
        item { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun NoFolder(onChoose: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.folder_empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.folder_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onChoose) { Text(stringResource(R.string.folder_choose)) }
    }
}

@Composable
private fun FolderRow(
    file: FolderFile,
    onOpen: (FolderFile) -> Unit,
    onShare: (FolderFile) -> Unit,
    onRename: ((FolderFile) -> Unit)?,
    onDelete: ((FolderFile) -> Unit)?,
    menuOpen: Boolean = false,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(menuOpen) }
    val details = listOfNotNull(
        file.modified.takeIf { it > 0 }?.let { DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR) },
        file.size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(file) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdfBadge(Modifier.size(40.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_actions, file.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.tool_share)) }, onClick = { menu = false; onShare(file) })
                onRename?.let { rename ->
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename_file)) }, onClick = { menu = false; rename(file) })
                }
                onDelete?.let { delete ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_file), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; delete(file) },
                    )
                }
            }
        }
    }
}
