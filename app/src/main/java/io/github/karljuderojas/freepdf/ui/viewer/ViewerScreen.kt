package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Surface
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.sign.SignatureMethod
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.share.Sharing
import io.github.karljuderojas.freepdf.ui.sign.FinishSigningDialog
import io.github.karljuderojas.freepdf.ui.sign.SignaturePadDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    data object Share : ViewerAction
    data class Search(val query: String) : ViewerAction
    data class Unlock(val password: String) : ViewerAction

    /** Annotate actions. Points are fractions of the displayed page; see [AnnotationLayer]. */
    data class Stroke(val page: Int, val tool: AnnotateTool, val points: List<Offset>) : ViewerAction
    data class Box(val page: Int, val tool: AnnotateTool, val start: Offset, val end: Offset) : ViewerAction
    data class Note(val page: Int, val at: Offset, val text: String) : ViewerAction
    data class Erase(val page: Int, val at: Offset) : ViewerAction

    /** Sign actions. */
    data class SaveSignature(val kind: SignatureStore.Kind, val image: Bitmap, val method: SignatureMethod) : ViewerAction
    data class PlaceSignature(val page: Int, val at: Offset, val kind: SignatureStore.Kind) : ViewerAction
    data class AddDate(val page: Int, val at: Offset) : ViewerAction
    data class AddText(val page: Int, val at: Offset, val text: String) : ViewerAction
    data class AddCheckmark(val page: Int, val at: Offset) : ViewerAction
    data class FinishSigning(val name: String, val consentText: String, val seal: Boolean) : ViewerAction

    /** Placed stamps; see [StampLayer]. Moves are fractions of the page. */
    data class MoveStamp(val id: Long, val delta: Offset) : ViewerAction
    data class ResizeStamp(val id: Long, val factor: Float) : ViewerAction
    data class DeleteStamp(val id: Long) : ViewerAction
    data object CommitStamps : ViewerAction
}

/** Opens [uri] in [initialMode] (Read unless a Home shortcut or the Tools tab asked otherwise). */
@Composable
fun ViewerScreen(
    uri: Uri,
    onBack: () -> Unit,
    initialMode: ViewerMode = ViewerMode.Read,
    initialTool: Int? = null,
    viewModel: ViewerViewModel = viewModel(),
) {
    LaunchedEffect(uri) { viewModel.open(uri) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val signerName by viewModel.signerName.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val stamps by viewModel.stamps.collectAsStateWithLifecycle()
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
        loadRegion = viewModel::pageRegion,
        initialMode = initialMode,
        initialTool = initialTool,
        savedSignatures = savedSignatures,
        signerName = signerName,
        search = search,
        stamps = stamps,
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
                ViewerAction.Share -> viewModel.share()
                is ViewerAction.Search -> viewModel.search(action.query)
                is ViewerAction.Unlock -> viewModel.unlock(action.password)
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
                is ViewerAction.SaveSignature -> viewModel.saveSignature(action.kind, action.image, action.method)
                is ViewerAction.PlaceSignature -> viewModel.placeSignature(action.page, action.at, action.kind)
                is ViewerAction.AddDate -> viewModel.addDate(action.page, action.at)
                is ViewerAction.AddText -> viewModel.addText(action.page, action.at, action.text)
                is ViewerAction.AddCheckmark -> viewModel.addCheckmark(action.page, action.at)
                is ViewerAction.FinishSigning -> viewModel.finishSigning(action.name, action.consentText, action.seal)
                is ViewerAction.MoveStamp -> viewModel.moveStamp(action.id, action.delta.x, action.delta.y)
                is ViewerAction.ResizeStamp -> viewModel.resizeStamp(action.id, action.factor)
                is ViewerAction.DeleteStamp -> viewModel.deleteStamp(action.id)
                ViewerAction.CommitStamps -> viewModel.commitStamps()
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
    loadRegion: LoadRegion = { _, _, _ -> null },
    initialMode: ViewerMode = ViewerMode.Read,
    initialSelectedPage: Int = 0,
    initialTool: Int? = null,
    savedSignatures: Map<SignatureStore.Kind, Bitmap> = emptyMap(),
    signerName: String = "",
    search: SearchResults = SearchResults(),
    initialSearchQuery: String? = null,
    stamps: List<PlacedStamp> = emptyList(),
    initialSelectedStamp: Long? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (ViewerAction) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var selectedTool by rememberSaveable { mutableStateOf(initialTool) }
    var selectedPage by rememberSaveable { mutableIntStateOf(initialSelectedPage) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var pendingNote by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var pendingText by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var padFor by remember { mutableStateOf<SignatureStore.Kind?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(initialSearchQuery != null) }
    var query by rememberSaveable { mutableStateOf(initialSearchQuery.orEmpty()) }
    var currentMatch by rememberSaveable { mutableIntStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    var focusSearch by remember { mutableStateOf(false) }
    var goingToPage by remember { mutableStateOf(false) }
    var showingOutline by remember { mutableStateOf(false) }
    var selectedStamp by remember { mutableStateOf(initialSelectedStamp) }
    // A newly placed stamp starts out selected, so its handles show right away.
    var newestStamp by remember { mutableStateOf(stamps.maxOfOrNull { it.id } ?: 0L) }
    LaunchedEffect(stamps) {
        val newest = stamps.maxOfOrNull { it.id } ?: return@LaunchedEffect
        if (newest > newestStamp) {
            newestStamp = newest
            selectedStamp = newest
        }
    }

    val ready = state as? ViewerState.Ready
    val pageCount = ready?.pageSizes?.size ?: 0
    // Stamps still being placed count as changes, for Undo and for offering Finish.
    val canUndo = ready?.canUndo == true || stamps.isNotEmpty()
    val hasSignature = ready?.hasSignature == true || stamps.any { it.content is StampContent.Signature }
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && selectedPage >= pageCount) selectedPage = pageCount - 1
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
        // Done keeps what was placed: it is written into the PDF on the way out.
        if (mode == ViewerMode.Sign && stamps.isNotEmpty()) onAction(ViewerAction.CommitStamps)
        selectedStamp = null
        mode = ViewerMode.Read
        selectedTool = null
    }

    fun closeSearch() {
        searching = false
        query = ""
        onAction(ViewerAction.Search(""))
    }

    // Typing pauses briefly before searching, so each keystroke does not start a new search.
    LaunchedEffect(query, searching) {
        if (!searching) return@LaunchedEffect
        if (query.isNotBlank()) delay(SEARCH_DELAY_MS)
        if (query.trim() != search.query) onAction(ViewerAction.Search(query))
    }
    LaunchedEffect(searching) {
        if (searching && focusSearch) searchFocus.requestFocus()
        focusSearch = false
    }
    LaunchedEffect(search.query) { currentMatch = 0 }
    // Bring the current match into view, about a third of the way down the screen.
    val shownMatch = search.matches.getOrNull(currentMatch)
    LaunchedEffect(shownMatch?.page, currentMatch, search.query) {
        val match = shownMatch ?: return@LaunchedEffect
        val size = ready?.pageSizes?.getOrNull(match.page) ?: return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize
        val pageHeight = viewport.width / size.aspectRatio
        val top = match.boxes.minOfOrNull { it.top } ?: 0f
        listState.animateScrollToItem(match.page, (top * pageHeight - viewport.height / 3f).toInt().coerceAtLeast(0))
    }

    BackHandler(enabled = mode != ViewerMode.Read || searching || ready?.hasUnsavedChanges == true) {
        when {
            mode != ViewerMode.Read -> backToReading()
            searching -> closeSearch()
            else -> leave()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    when {
                        mode == ViewerMode.Read && searching -> SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            focusRequester = searchFocus,
                        )
                        mode != ViewerMode.Read -> Text(stringResource(mode.label))
                        // Tapping "Page 3 of 12" asks which page to go to.
                        ready != null -> Text(
                            stringResource(R.string.page_of, currentPage + 1, pageCount),
                            modifier = Modifier
                                .clickable(onClickLabel = stringResource(R.string.go_to_page)) { goingToPage = true }
                                .testTag("page-indicator"),
                        )
                        else -> Text(stringResource(R.string.app_name))
                    }
                },
                navigationIcon = {
                    if (mode == ViewerMode.Read) {
                        IconButton(onClick = { if (searching) closeSearch() else leave() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (mode == ViewerMode.Read && searching) {
                        SearchStepper(search, currentMatch) { step ->
                            val count = search.matches.size
                            if (count > 0) currentMatch = (currentMatch + step + count) % count
                        }
                    } else if (mode == ViewerMode.Read) {
                        if (ready != null) {
                            IconButton(onClick = {
                                focusSearch = true
                                searching = true
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                            }
                            if (ready.outline.isNotEmpty()) {
                                IconButton(onClick = { showingOutline = true }) {
                                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.contents))
                                }
                            }
                        }
                        if (ready?.hasUnsavedChanges == true) {
                            TextButton(onClick = { onAction(ViewerAction.Save) }) { Text(stringResource(R.string.save)) }
                        }
                    } else {
                        if (canUndo) {
                            TextButton(onClick = { onAction(ViewerAction.Undo) }) { Text(stringResource(R.string.undo)) }
                        }
                        TextButton(onClick = { backToReading() }) {
                            Text(stringResource(R.string.done))
                        }
                        // Once something is signed, Finish turns it into a signed copy.
                        if (mode == ViewerMode.Sign && hasSignature) {
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
                mode == ViewerMode.Read && searching -> Unit
                mode == ViewerMode.Read -> ModeBar(onModeSelected = {
                    mode = it
                    if (it == ViewerMode.Pages) selectedPage = currentPage
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
                state is ViewerState.Locked -> PasswordPrompt(
                    wrongPassword = state.wrongPassword,
                    onUnlock = { onAction(ViewerAction.Unlock(it)) },
                    onCancel = onBack,
                )
                ready == null -> CircularProgressIndicator()
                mode == ViewerMode.Pages -> PageGrid(
                    pageSizes = ready.pageSizes,
                    revision = ready.revision,
                    selectedPage = selectedPage,
                    onPageSelected = { selectedPage = it },
                    loadPage = loadPage,
                )
                else -> {
                    val tool = AnnotateTool.forLabel(selectedTool).takeIf { mode == ViewerMode.Annotate }
                    val signTool = SignTool.forLabel(selectedTool).takeIf { mode == ViewerMode.Sign }
                    PageList(ready.pageSizes, ready.revision, loadPage, loadRegion, listState) { page ->
                        if (mode == ViewerMode.Read && searching) {
                            val onPage = search.matches.withIndex().filter { it.value.page == page }
                            if (onPage.isNotEmpty()) SearchHighlights(onPage, currentMatch)
                        }
                        val pageStamps = if (mode == ViewerMode.Sign) stamps.filter { it.page == page } else emptyList()
                        if (signTool != null || (selectedStamp != null && pageStamps.isNotEmpty())) {
                            TapLayer(page) { at ->
                                val kind = signTool?.signatureKind
                                when {
                                    // The first tap away from a selected stamp only lets go of it.
                                    selectedStamp != null -> selectedStamp = null
                                    signTool == null -> Unit
                                    kind != null && savedSignatures[kind] == null -> padFor = kind
                                    kind != null -> onAction(ViewerAction.PlaceSignature(page, at, kind))
                                    signTool == SignTool.Date -> onAction(ViewerAction.AddDate(page, at))
                                    signTool == SignTool.Text -> pendingText = page to at
                                    else -> onAction(ViewerAction.AddCheckmark(page, at))
                                }
                            }
                        }
                        if (pageStamps.isNotEmpty()) {
                            StampLayer(
                                stamps = pageStamps,
                                selected = selectedStamp,
                                onSelect = { selectedStamp = it },
                                onMove = { id, delta -> onAction(ViewerAction.MoveStamp(id, delta)) },
                                onResize = { id, factor -> onAction(ViewerAction.ResizeStamp(id, factor)) },
                                onDelete = { id ->
                                    selectedStamp = null
                                    onAction(ViewerAction.DeleteStamp(id))
                                },
                            )
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

    if (goingToPage) {
        GoToPageDialog(
            pageCount = pageCount,
            onDismiss = { goingToPage = false },
            onGo = {
                goingToPage = false
                returnToPage = it
            },
        )
    }

    if (showingOutline && ready != null) {
        OutlineDialog(
            outline = ready.outline,
            onDismiss = { showingOutline = false },
            onGo = {
                showingOutline = false
                returnToPage = it
            },
        )
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

    padFor?.let { kind ->
        SignaturePadDialog(
            kind = kind,
            typedName = signerName,
            onDismiss = { padFor = null },
            onSave = { image, method ->
                padFor = null
                onAction(ViewerAction.SaveSignature(kind, image, method))
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
    loadRegion: LoadRegion,
    listState: LazyListState,
    overlay: @Composable BoxScope.(page: Int) -> Unit = {},
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val detail = remember { ZoomDetail() }
    SideEffect { detail.loadRegion = loadRegion }
    // Sharpen once the view has stopped moving, not on every frame of a pinch or fling.
    LaunchedEffect(detail) {
        snapshotFlow { listOf(zoom, pan, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collectLatest {
                delay(SETTLE_MILLIS)
                detail.settled++
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { detail.viewport = it }) {
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
                PageImage(index, size, revision, widthPx, loadPage, Modifier.fillMaxWidth()) {
                    ZoomDetailLayer(index, revision, detail)
                    overlay(index)
                }
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

/** Asks for the password of a locked PDF, in place of its pages. */
@Composable
private fun PasswordPrompt(wrongPassword: Boolean, onUnlock: (String) -> Unit, onCancel: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        modifier = Modifier.padding(24.dp).widthIn(max = 440.dp),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.password_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.password_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_label)) },
                singleLine = true,
                isError = wrongPassword,
                supportingText = if (wrongPassword) {
                    { Text(stringResource(R.string.password_wrong)) }
                } else {
                    null
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onUnlock(password) }),
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) {
                        Text(stringResource(if (visible) R.string.password_hide else R.string.password_show))
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("password-field"),
            )
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onUnlock(password) }, enabled = password.isNotEmpty()) {
                    Text(stringResource(R.string.unlock))
                }
            }
        }
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

private const val SEARCH_DELAY_MS = 300L

/** How long the view must be still before zoomed pages are sharpened. */
private const val SETTLE_MILLIS = 150L

private val AnnotateTool.rgb get() = Annotator.Rgb(color.red, color.green, color.blue)
