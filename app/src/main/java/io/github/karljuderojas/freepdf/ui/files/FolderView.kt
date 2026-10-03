package io.github.karljuderojas.freepdf.ui.files

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val store = (context.applicationContext as FreePdfApp).folders
    val folder by store.folder.collectAsState()
    var listing by remember { mutableStateOf<FolderListing?>(null) }
    var unreadable by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var sort by remember { mutableStateOf(FolderSort.Name) }
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

    FolderContent(
        folderName = listing?.name,
        files = listing?.files,
        unreadable = unreadable,
        refreshing = refreshing,
        sort = sort,
        onSort = { sort = it },
        onRefresh = { scope.launch { reload++ } },
        onChoose = { pick.launch(null) },
        onForget = { store.forget(context) },
        onOpen = { onOpenPdf(Uri.parse(it.uri)) },
        modifier = modifier,
    )
}

/**
 * Stateless Folder view, for screenshot tests. [files] is null before a folder is chosen (or
 * while it is read for the first time) and [unreadable] says the chosen folder can no longer be read.
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
                    items(sorted, key = { it.uri }) { FolderRow(it, onOpen) }
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
private fun FolderRow(file: FolderFile, onOpen: (FolderFile) -> Unit) {
    val context = LocalContext.current
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
    }
}
