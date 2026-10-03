package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.share.Sharing
import io.github.karljuderojas.freepdf.ui.sign.FinishSigningDialog
import io.github.karljuderojas.freepdf.ui.sign.SignaturePadDialog
import kotlinx.coroutines.launch

/** What the viewer asks its view model to do. Page numbers are zero-based. */
sealed interface ViewerAction {
    data object Undo : ViewerAction
    data object Save : ViewerAction
    data object SaveAndClose : ViewerAction
    data class Rotate(val pages: Set<Int>) : ViewerAction
    data class Delete(val pages: Set<Int>) : ViewerAction
    data class InsertBlank(val afterPage: Int) : ViewerAction
    data class Move(val from: Int, val to: Int) : ViewerAction
    /** Moves the pages [by] places together; negative is earlier. */
    data class Shift(val pages: Set<Int>, val by: Int) : ViewerAction
    data object Merge : ViewerAction
    data object Share : ViewerAction

    /** Annotate actions. Points are fractions of the displayed page; see [AnnotationLayer]. */
    data class Stroke(val page: Int, val tool: AnnotateTool, val points: List<Offset>) : ViewerAction
    data class Box(val page: Int, val tool: AnnotateTool, val start: Offset, val end: Offset) : ViewerAction
    data class Note(val page: Int, val at: Offset, val text: String) : ViewerAction
    data class Erase(val page: Int, val at: Offset) : ViewerAction

    /** Sign actions. */
    data class SaveSignature(val kind: SignatureStore.Kind, val image: Bitmap) : ViewerAction
    data class PlaceSignature(val page: Int, val at: Offset, val kind: SignatureStore.Kind) : ViewerAction
    data class AddDate(val page: Int, val at: Offset) : ViewerAction
    data class AddText(val page: Int, val at: Offset, val text: String) : ViewerAction
    data class AddCheckmark(val page: Int, val at: Offset) : ViewerAction
    data class FinishSigning(val name: String, val consentText: String, val seal: Boolean) : ViewerAction
}

@Composable
fun ViewerScreen(uri: Uri, onBack: () -> Unit, viewModel: ViewerViewModel = viewModel()) {
    LaunchedEffect(uri) { viewModel.open(uri) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val signerName by viewModel.signerName.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val saveAsPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it != null) viewModel.saveAs(it) else viewModel.cancelSaveAs()
    }
    val signedCopyPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it != null) viewModel.saveSignedCopy(it) else viewModel.cancelSignedCopy()
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
                is ViewerEffect.Share -> Sharing.shareFile(context, effect.file)
                is ViewerEffect.SaveSigned -> signedCopyPicker.launch(effect.suggestedName)
            }
        }
    }

    ViewerContent(
        state = state,
        onBack = onBack,
        loadPage = viewModel::page,
        savedSignatures = savedSignatures,
        signerName = signerName,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                ViewerAction.Undo -> viewModel.undo()
                ViewerAction.Save -> viewModel.save()
                ViewerAction.SaveAndClose -> viewModel.save(thenClose = true)
                is ViewerAction.Rotate -> viewModel.rotatePages(action.pages)
                is ViewerAction.Delete -> viewModel.deletePages(action.pages)
                is ViewerAction.InsertBlank -> viewModel.insertBlankPage(action.afterPage)
                is ViewerAction.Move -> viewModel.movePage(action.from, action.to)
                is ViewerAction.Shift -> viewModel.shiftPages(action.pages, action.by)
                ViewerAction.Merge -> mergePicker.launch(arrayOf("application/pdf"))
                ViewerAction.Share -> viewModel.share()
                is ViewerAction.Stroke -> viewModel.ink(action.page, listOf(action.points), action.tool.rgb)
                is ViewerAction.Box -> when (action.tool) {
                    AnnotateTool.Highlight -> Annotator.TextMarkup.Highlight
                    AnnotateTool.Underline -> Annotator.TextMarkup.Underline
                    AnnotateTool.StrikeOut -> Annotator.TextMarkup.StrikeOut
                    else -> null
                }.let { kind ->
                    if (kind != null) {
                        viewModel.markText(action.page, action.start, action.end, kind, action.tool.rgb)
                    } else {
                        viewModel.shape(action.page, action.start, action.end, action.tool.rgb)
                    }
                }
                is ViewerAction.Note -> viewModel.note(action.page, action.at, action.text)
                is ViewerAction.Erase -> viewModel.erase(action.page, action.at)
                is ViewerAction.SaveSignature -> viewModel.saveSignature(action.kind, action.image)
                is ViewerAction.PlaceSignature -> viewModel.placeSignature(action.page, action.at, action.kind)
                is ViewerAction.AddDate -> viewModel.addDate(action.page, action.at)
                is ViewerAction.AddText -> viewModel.addText(action.page, action.at, action.text)
                is ViewerAction.AddCheckmark -> viewModel.addCheckmark(action.page, action.at)
                is ViewerAction.FinishSigning -> viewModel.finishSigning(action.name, action.consentText, action.seal)
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
    initialSelectedPages: Set<Int> = setOf(initialSelectedPage),
    initialTool: Int? = null,
    savedSignatures: Map<SignatureStore.Kind, Bitmap> = emptyMap(),
    signerName: String = "",
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (ViewerAction) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var selectedTool by rememberSaveable { mutableStateOf(initialTool) }
    // Pages mode's selection; never empty, so the tools always have something to act on.
    var selectedPages by rememberSaveable(stateSaver = PageSetSaver) { mutableStateOf(initialSelectedPages) }
    val selectedPage = selectedPages.minOrNull() ?: 0
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var pendingNote by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var pendingText by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var padFor by remember { mutableStateOf<SignatureStore.Kind?>(null) }
    var finishing by remember { mutableStateOf(false) }

    val ready = state as? ViewerState.Ready
    val pageCount = ready?.pageSizes?.size ?: 0
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && selectedPages.any { it >= pageCount }) {
            selectedPages = selectedPages.filter { it < pageCount }.toSet().ifEmpty { setOf(pageCount - 1) }
        }
    }

    fun leave() {
        if (ready?.hasUnsavedChanges == true) confirmLeave = true else onBack()
    }

    fun onPagesTool(tool: Int) {
        when (tool) {
            R.string.tool_rotate -> onAction(ViewerAction.Rotate(selectedPages))
            // Several selected pages move together, keeping the gaps between them.
            R.string.tool_move_earlier -> if (selectedPage > 0) {
                onAction(ViewerAction.Shift(selectedPages, -1))
                selectedPages = selectedPages.map { it - 1 }.toSet()
            }
            R.string.tool_move_later -> if (selectedPages.max() < pageCount - 1) {
                onAction(ViewerAction.Shift(selectedPages, 1))
                selectedPages = selectedPages.map { it + 1 }.toSet()
            }
            R.string.tool_insert -> {
                val after = selectedPages.max()
                onAction(ViewerAction.InsertBlank(after))
                selectedPages = setOf(after + 1)
            }
            R.string.tool_delete -> confirmDelete = true
            R.string.tool_merge -> onAction(ViewerAction.Merge)
            R.string.tool_select_all -> selectedPages = (0 until pageCount).toSet()
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
                            mode == ViewerMode.Pages && selectedPages.size > 1 ->
                                pluralStringResource(R.plurals.pages_selected, selectedPages.size, selectedPages.size)
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
                    } else if (mode == ViewerMode.Pages && selectedPages.size > 1) {
                        IconButton(onClick = { selectedPages = setOf(selectedPage) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_selection))
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
                        // Once something is signed, Finish turns it into a signed copy.
                        if (mode == ViewerMode.Sign && ready?.hasSignature == true) {
                            Button(onClick = { finishing = true }, modifier = Modifier.padding(end = 8.dp)) {
                                Text(stringResource(R.string.finish))
                            }
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
                    if (it == ViewerMode.Pages) selectedPages = setOf(currentPage)
                })
                // Page tools act once on the selected page, so none stays highlighted.
                mode == ViewerMode.Pages -> ToolStrip(mode, selectedTool = null, onToolSelected = { onPagesTool(it) })
                // Choosing the active Annotate tool again puts it down, so one finger scrolls again.
                mode == ViewerMode.Annotate -> ToolStrip(mode, selectedTool, onToolSelected = {
                    when {
                        selectedTool == it -> selectedTool = null
                        else -> selectedTool = it
                    }
                })
                mode == ViewerMode.Sign -> Column {
                    SignTool.forLabel(selectedTool)?.let { tool ->
                        SignHint(
                            tool = tool,
                            savedImage = tool.signatureKind?.let { savedSignatures[it] },
                            onRedraw = { padFor = tool.signatureKind },
                        )
                    }
                    ToolStrip(mode, selectedTool, onToolSelected = { label ->
                        val tool = SignTool.forLabel(label)
                        when {
                            tool == null -> Unit
                            selectedTool == label -> selectedTool = null
                            else -> {
                                selectedTool = label
                                // First use: draw the signature before placing it.
                                tool.signatureKind?.takeIf { savedSignatures[it] == null }?.let { padFor = it }
                            }
                        }
                    })
                }
                else -> ToolStrip(mode, selectedTool = null, onToolSelected = {
                    if (it == R.string.tool_share) onAction(ViewerAction.Share)
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
                    selectedPages = selectedPages,
                    // With several pages picked, a tap adds or removes one; otherwise it picks just that page.
                    onPageTapped = { page ->
                        selectedPages = when {
                            selectedPages.size < 2 -> setOf(page)
                            else -> selectedPages.toggle(page)
                        }
                    },
                    onSelectionToggled = { page -> selectedPages = selectedPages.toggle(page) },
                    onPageMoved = { from, to ->
                        onAction(ViewerAction.Move(from, to))
                        selectedPages = setOf(to)
                    },
                    loadPage = loadPage,
                )
                else -> {
                    val tool = AnnotateTool.forLabel(selectedTool).takeIf { mode == ViewerMode.Annotate }
                    val signTool = SignTool.forLabel(selectedTool).takeIf { mode == ViewerMode.Sign }
                    PageList(ready.pageSizes, ready.revision, loadPage, listState) { page ->
                        if (signTool != null) {
                            TapLayer(page) { at ->
                                val kind = signTool.signatureKind
                                when {
                                    kind != null && savedSignatures[kind] == null -> padFor = kind
                                    kind != null -> onAction(ViewerAction.PlaceSignature(page, at, kind))
                                    signTool == SignTool.Date -> onAction(ViewerAction.AddDate(page, at))
                                    signTool == SignTool.Text -> pendingText = page to at
                                    else -> onAction(ViewerAction.AddCheckmark(page, at))
                                }
                            }
                        }
                        if (tool != null) {
                            AnnotationLayer(
                                page = page,
                                tool = tool,
                                onStroke = { onAction(ViewerAction.Stroke(page, tool, it)) },
                                onBox = { start, end -> onAction(ViewerAction.Box(page, tool, start, end)) },
                                onTap = { at ->
                                    if (tool == AnnotateTool.Note) pendingNote = page to at
                                    else onAction(ViewerAction.Erase(page, at))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    if (selectedPages.size == 1) stringResource(R.string.delete_page_title, selectedPage + 1)
                    else pluralStringResource(R.plurals.delete_pages_title, selectedPages.size, selectedPages.size),
                )
            },
            text = { Text(stringResource(R.string.delete_page_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onAction(ViewerAction.Delete(selectedPages))
                    selectedPages = setOf(selectedPage)
                }) { Text(stringResource(R.string.tool_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    padFor?.let { kind ->
        SignaturePadDialog(
            kind = kind,
            onDismiss = { padFor = null },
            onSave = {
                padFor = null
                onAction(ViewerAction.SaveSignature(kind, it))
            },
        )
    }

    if (finishing) {
        FinishSigningDialog(
            initialName = signerName,
            onDismiss = { finishing = false },
            onFinish = { name, consentText, seal ->
                finishing = false
                backToReading()
                onAction(ViewerAction.FinishSigning(name, consentText, seal))
            },
        )
    }

    pendingText?.let { (page, at) ->
        TextEntryDialog(
            title = R.string.text_title,
            hint = R.string.text_hint,
            onDismiss = { pendingText = null },
            onAdd = { text ->
                pendingText = null
                onAction(ViewerAction.AddText(page, at, text))
            },
        )
    }

    pendingNote?.let { (page, at) ->
        TextEntryDialog(
            title = R.string.note_title,
            hint = R.string.note_hint,
            onDismiss = { pendingNote = null },
            onAdd = { text ->
                pendingNote = null
                onAction(ViewerAction.Note(page, at, text))
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
    overlay: @Composable BoxScope.(page: Int) -> Unit = {},
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("page-list")
                // Pinch to zoom and drag with two fingers to pan; single-finger drags fall through
                // to the list's scrolling. The scaled content is kept inside the screen, so no part
                // of a zoomed page is ever out of reach.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                                val reach = Offset(size.width * (zoom - 1) / 2, size.height * (zoom - 1) / 2)
                                pan = (pan + event.calculatePan()).let {
                                    Offset(it.x.coerceIn(-reach.x, reach.x), it.y.coerceIn(-reach.y, reach.y))
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                // Double-tap brings the page back to its normal size.
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero })
                }
                .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(pageSizes) { index, size ->
                PageImage(index, size, revision, widthPx, loadPage, Modifier.fillMaxWidth()) { overlay(index) }
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
    overlay: @Composable BoxScope.() -> Unit = {},
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
        overlay()
    }
}

@Composable
private fun TextEntryDialog(@StringRes title: Int, @StringRes hint: Int, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val AnnotateTool.rgb get() = Annotator.Rgb(color.red, color.green, color.blue)

/** Adds [page] to the selection, or takes it out unless it is the only one left. */
private fun Set<Int>.toggle(page: Int): Set<Int> = when {
    page !in this -> this + page
    size > 1 -> this - page
    else -> this
}

private val PageSetSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })
