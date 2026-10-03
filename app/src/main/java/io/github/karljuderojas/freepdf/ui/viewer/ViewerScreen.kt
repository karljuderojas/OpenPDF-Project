package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import kotlinx.coroutines.launch

/** What the viewer asks its view model to do. Page numbers are zero-based. */
sealed interface ViewerAction {
    data object Undo : ViewerAction
    data object Save : ViewerAction
    data object SaveAndClose : ViewerAction
    data class Rotate(val page: Int) : ViewerAction
    data class Delete(val page: Int) : ViewerAction
    data class InsertBlank(val afterPage: Int) : ViewerAction
    data class Move(val from: Int, val to: Int) : ViewerAction
    data object Merge : ViewerAction
}

@Composable
fun ViewerScreen(uri: Uri, onBack: () -> Unit, viewModel: ViewerViewModel = viewModel()) {
    LaunchedEffect(uri) { viewModel.open(uri) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    val saveAsPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it != null) viewModel.saveAs(it) else viewModel.cancelSaveAs()
    }
    val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) viewModel.merge(it)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ViewerEffect.Message -> launch { snackbarHostState.showSnackbar(resources.getString(effect.text)) }
                is ViewerEffect.SaveAs -> saveAsPicker.launch(effect.suggestedName)
                ViewerEffect.Close -> onBack()
            }
        }
    }

    ViewerContent(
        state = state,
        onBack = onBack,
        loadPage = viewModel::page,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                ViewerAction.Undo -> viewModel.undo()
                ViewerAction.Save -> viewModel.save()
                ViewerAction.SaveAndClose -> viewModel.save(thenClose = true)
                is ViewerAction.Rotate -> viewModel.rotatePage(action.page)
                is ViewerAction.Delete -> viewModel.deletePage(action.page)
                is ViewerAction.InsertBlank -> viewModel.insertBlankPage(action.afterPage)
                is ViewerAction.Move -> viewModel.movePage(action.from, action.to)
                ViewerAction.Merge -> mergePicker.launch(arrayOf("application/pdf"))
            }
        },
    )
}

/** Stateless viewer UI, so it can be previewed and screenshot-tested without a real PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerContent(
    state: ViewerState,
    onBack: () -> Unit,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    initialMode: ViewerMode = ViewerMode.Read,
    initialSelectedPage: Int = 0,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (ViewerAction) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var selectedTool by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedPage by rememberSaveable { mutableIntStateOf(initialSelectedPage) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    val ready = state as? ViewerState.Ready
    val pageCount = ready?.pageSizes?.size ?: 0
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && selectedPage >= pageCount) selectedPage = pageCount - 1
    }

    fun comingSoon() {
        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.coming_soon)) }
    }

    fun leave() {
        if (ready?.hasUnsavedChanges == true) confirmLeave = true else onBack()
    }

    fun onPagesTool(tool: Int) {
        when (tool) {
            R.string.tool_rotate -> onAction(ViewerAction.Rotate(selectedPage))
            R.string.tool_move_earlier -> if (selectedPage > 0) {
                onAction(ViewerAction.Move(selectedPage, selectedPage - 1))
                selectedPage--
            }
            R.string.tool_move_later -> if (selectedPage < pageCount - 1) {
                onAction(ViewerAction.Move(selectedPage, selectedPage + 1))
                selectedPage++
            }
            R.string.tool_insert -> {
                onAction(ViewerAction.InsertBlank(selectedPage))
                selectedPage++
            }
            R.string.tool_delete -> confirmDelete = true
            R.string.tool_merge -> onAction(ViewerAction.Merge)
            else -> comingSoon()
        }
    }

    // Leaving Pages lands on the page that was selected there.
    var returnToPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(returnToPage) {
        returnToPage?.let { listState.scrollToItem(it) }
        returnToPage = null
    }

    fun backToReading() {
        if (mode == ViewerMode.Pages) returnToPage = selectedPage
        mode = ViewerMode.Read
        selectedTool = null
    }

    BackHandler(enabled = mode != ViewerMode.Read || ready?.hasUnsavedChanges == true) {
        if (mode != ViewerMode.Read) backToReading() else leave()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            mode != ViewerMode.Read -> stringResource(mode.label)
                            ready != null -> stringResource(R.string.page_of, currentPage + 1, pageCount)
                            else -> stringResource(R.string.app_name)
                        },
                    )
                },
                navigationIcon = {
                    if (mode == ViewerMode.Read) {
                        IconButton(onClick = { leave() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (mode == ViewerMode.Read) {
                        if (ready?.hasUnsavedChanges == true) {
                            TextButton(onClick = { onAction(ViewerAction.Save) }) { Text(stringResource(R.string.save)) }
                        }
                    } else {
                        if (ready?.canUndo == true) {
                            TextButton(onClick = { onAction(ViewerAction.Undo) }) { Text(stringResource(R.string.undo)) }
                        }
                        TextButton(onClick = { backToReading() }) {
                            Text(stringResource(R.string.done))
                        }
                    }
                },
            )
        },
        bottomBar = {
            when {
                ready == null -> Unit
                mode == ViewerMode.Read -> ModeBar(onModeSelected = {
                    mode = it
                    if (it == ViewerMode.Pages) selectedPage = currentPage
                })
                // Page tools act once on the selected page, so none stays highlighted.
                mode == ViewerMode.Pages -> ToolStrip(mode, selectedTool = null, onToolSelected = { onPagesTool(it) })
                else -> ToolStrip(mode, selectedTool, onToolSelected = {
                    selectedTool = it
                    comingSoon()
                })
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state is ViewerState.Failed -> Text(stringResource(R.string.error_open))
                ready == null -> CircularProgressIndicator()
                mode == ViewerMode.Pages -> PageGrid(
                    pageSizes = ready.pageSizes,
                    revision = ready.revision,
                    selectedPage = selectedPage,
                    onPageSelected = { selectedPage = it },
                    loadPage = loadPage,
                )
                else -> PageList(ready.pageSizes, ready.revision, loadPage, listState)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_page_title, selectedPage + 1)) },
            text = { Text(stringResource(R.string.delete_page_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onAction(ViewerAction.Delete(selectedPage))
                }) { Text(stringResource(R.string.tool_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onAction(ViewerAction.SaveAndClose)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        confirmLeave = false
                        onBack()
                    }) { Text(stringResource(R.string.discard)) }
                }
            },
        )
    }
}

@Composable
private fun PageList(
    pageSizes: List<PageSize>,
    revision: Int,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    listState: LazyListState,
) {
    var zoom by remember { mutableFloatStateOf(1f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Pinch to zoom; single-finger drags fall through to the list's scrolling.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer { scaleX = zoom; scaleY = zoom },
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(pageSizes) { index, size ->
                PageImage(index, size, revision, widthPx, loadPage, Modifier.fillMaxWidth())
            }
        }
    }
}

/** One page, rendered at [widthPx]. Re-renders when the document's [revision] changes. */
@Composable
internal fun PageImage(
    index: Int,
    size: PageSize,
    revision: Int,
    widthPx: Int,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(null, index, widthPx, revision) {
        value = loadPage(index, widthPx)
    }
    Box(
        modifier.aspectRatio(size.aspectRatio).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}
