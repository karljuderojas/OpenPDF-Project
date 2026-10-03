package io.github.karljuderojas.freepdf.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import io.github.karljuderojas.freepdf.FreePdfApp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.annotate.Mark
import io.github.karljuderojas.freepdf.pdf.annotate.Stamps
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.text.PageWord
import io.github.karljuderojas.freepdf.pdf.sign.SignatureMethod
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.settings.Tip
import io.github.karljuderojas.freepdf.print.Printing
import io.github.karljuderojas.freepdf.settings.PageColors
import io.github.karljuderojas.freepdf.share.Sharing
import io.github.karljuderojas.freepdf.ui.rememberPdfPicker
import io.github.karljuderojas.freepdf.ui.sign.FinishSigningDialog
import io.github.karljuderojas.freepdf.ui.sign.SignaturePadDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** What the viewer asks its view model to do. Page numbers are zero-based. */
sealed interface ViewerAction {
    data object Undo : ViewerAction
    data object Redo : ViewerAction
    data object Save : ViewerAction

    /** Saves, then runs [then] once the save lands: back out, or on to another open document. */
    data class SaveAndLeave(val then: () -> Unit) : ViewerAction

    data class Rotate(val page: Int) : ViewerAction
    data class Delete(val page: Int) : ViewerAction
    data class InsertBlank(val afterPage: Int) : ViewerAction
    data class Move(val from: Int, val to: Int) : ViewerAction
    data object Merge : ViewerAction
    data class Share(val option: ShareOption, val pages: List<Int>) : ViewerAction
    data class Extract(val pages: List<Int>) : ViewerAction
    data class Split(val parts: List<List<Int>>) : ViewerAction
    data object ShowInfo : ViewerAction
    data object Print : ViewerAction
    data class Search(val query: String) : ViewerAction
    data class Unlock(val password: String) : ViewerAction

    /** Locks the PDF with [password], or takes its password off when it is empty. */
    data class SetPassword(val password: String) : ViewerAction

    /** Annotate actions. Points are fractions of the displayed page; see [AnnotationLayer]. */
    data class Stroke(val page: Int, val tool: AnnotateTool, val style: ToolStyle, val points: List<Offset>) : ViewerAction
    data class Box(val page: Int, val tool: AnnotateTool, val style: ToolStyle, val start: Offset, val end: Offset) : ViewerAction
    data class Note(val page: Int, val style: ToolStyle, val at: Offset, val text: String) : ViewerAction
    data class AddStamp(val page: Int, val at: Offset, val kind: Stamps.Kind) : ViewerAction
    data class AddTextBox(val page: Int, val style: ToolStyle, val at: Offset, val text: String) : ViewerAction
    data class SetToolStyle(val tool: AnnotateTool, val style: ToolStyle) : ViewerAction

    /** Marks text with a markup [tool], one box per line; see [TextSelectionLayer]. */
    data class MarkLines(
        val page: Int,
        val tool: AnnotateTool,
        val style: ToolStyle,
        val lines: List<Rect>,
        val comment: String? = null,
    ) : ViewerAction
    data class Copy(val text: String) : ViewerAction

    /** Changes a mark found by its page and its [index] in that page's marks; null leaves that part alone. */
    data class EditMark(
        val page: Int,
        val index: Int,
        val color: Annotator.Rgb? = null,
        val width: Float? = null,
        val comment: String? = null,
    ) : ViewerAction
    data class DeleteMark(val page: Int, val index: Int) : ViewerAction
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

/**
 * Opens [uri] in [initialMode] (Read unless a Home shortcut or the Tools tab asked otherwise).
 * [onSwitchTo] replaces this viewer with another PDF, from the open-documents switcher.
 */
@Composable
fun ViewerScreen(
    uri: Uri,
    onBack: () -> Unit,
    initialMode: ViewerMode = ViewerMode.Read,
    initialTool: Int? = null,
    onSwitchTo: (Uri) -> Unit = {},
    viewModel: ViewerViewModel = viewModel(),
) {
    LaunchedEffect(uri) { viewModel.open(uri) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val signerName by viewModel.signerName.collectAsStateWithLifecycle()
    val toolStyles by viewModel.toolStyles.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val stamps by viewModel.stamps.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val context = LocalContext.current
    val settings = (context.applicationContext as FreePdfApp).settings
    val pageColors by settings.pageColors.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var shownInfo by remember { mutableStateOf<ViewerEffect.ShowInfo?>(null) }
    val scope = rememberCoroutineScope()
    fun launchMessage(@StringRes text: Int) {
        scope.launch { snackbarHostState.showSnackbar(resources.getString(text)) }
    }
    val documents = (context.applicationContext as FreePdfApp).documents
    val openDocuments by documents.open.collectAsStateWithLifecycle()
    val pickAnother = rememberPdfPicker(onSwitchTo)

    // Where to go once a save before leaving lands: back, or to another document.
    var afterSave by remember { mutableStateOf(onBack) }

    // The tip for the mode just entered, if it was not shown before; see Tips for the rules.
    val tips = remember(context) { (context.applicationContext as FreePdfApp).tips }
    var tip by rememberSaveable { mutableStateOf<Tip?>(null) }

    val saveAsPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it != null) viewModel.saveAs(it) else viewModel.cancelSaveAs()
    }
    val signedCopyPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it != null) viewModel.saveSignedCopy(it) else viewModel.cancelSignedCopy()
    }
    val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) viewModel.merge(it)
    }
    val extractPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it != null) viewModel.saveExtract(it) else viewModel.cancelExtract()
    }
    val splitFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) viewModel.splitInto(it) else viewModel.cancelSplit()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ViewerEffect.Message -> launch { snackbarHostState.showSnackbar(resources.getString(effect.text)) }
                is ViewerEffect.CountMessage -> launch {
                    snackbarHostState.showSnackbar(resources.getQuantityString(effect.text, effect.count, effect.count))
                }
                is ViewerEffect.SaveAs -> saveAsPicker.launch(effect.suggestedName)
                ViewerEffect.Close -> afterSave()
                is ViewerEffect.Share -> Sharing.shareFile(context, effect.file)
                is ViewerEffect.ShareImages -> Sharing.shareImages(context, effect.files, effect.title)
                is ViewerEffect.Print -> Printing.print(context, effect.file, effect.name, effect.pageCount)
                is ViewerEffect.SaveSigned -> signedCopyPicker.launch(effect.suggestedName)
                is ViewerEffect.SaveExtract -> extractPicker.launch(effect.suggestedName)
                ViewerEffect.PickSplitFolder -> splitFolderPicker.launch(null)
                is ViewerEffect.ShowInfo -> shownInfo = effect
            }
        }
    }

    ViewerContent(
        state = state,
        onBack = onBack,
        loadPage = viewModel::page,
        loadWords = viewModel::words,
        loadRegion = viewModel::pageRegion,
        initialMode = initialMode,
        initialTool = initialTool,
        savedSignatures = savedSignatures,
        signerName = signerName,
        toolStyles = toolStyles,
        marks = marks,
        search = search,
        stamps = stamps,
        snackbarHostState = snackbarHostState,
        openDocuments = openDocuments,
        currentUri = uri.toString(),
        onSwitchTo = { onSwitchTo(Uri.parse(it.uri)) },
        onCloseDocument = { documents.close(it.uri) },
        onCloseAll = documents::closeAll,
        onOpenAnother = pickAnother,
        pageColors = pageColors,
        onPageColors = settings::setPageColors,
        tip = tip?.text,
        onTipDismissed = { tip = null },
        onModeEntered = { mode ->
            // A tip on screen stays when its mode is entered again, as after rotating the phone.
            if (mode.tip != tip) tip = mode.tip?.takeIf { tips.shouldShow(it) }?.also { tips.markSeen(it) }
        },
        onAction = { action ->
            when (action) {
                ViewerAction.Undo -> viewModel.undo()
                ViewerAction.Redo -> viewModel.redo()
                ViewerAction.Save -> viewModel.save()
                is ViewerAction.SaveAndLeave -> {
                    afterSave = action.then
                    viewModel.save(thenClose = true)
                }
                is ViewerAction.Rotate -> viewModel.rotatePage(action.page)
                is ViewerAction.Delete -> viewModel.deletePage(action.page)
                is ViewerAction.InsertBlank -> viewModel.insertBlankPage(action.afterPage)
                is ViewerAction.Move -> viewModel.movePage(action.from, action.to)
                ViewerAction.Merge -> mergePicker.launch(arrayOf("application/pdf"))
                is ViewerAction.Share -> viewModel.share(action.option, action.pages)
                is ViewerAction.Extract -> viewModel.extract(action.pages)
                is ViewerAction.Split -> viewModel.split(action.parts)
                ViewerAction.ShowInfo -> viewModel.documentInfo()
                ViewerAction.Print -> viewModel.print()
                is ViewerAction.Search -> viewModel.search(action.query)
                is ViewerAction.Unlock -> viewModel.unlock(action.password)
                is ViewerAction.SetPassword -> viewModel.setPassword(action.password)
                is ViewerAction.Stroke -> viewModel.ink(action.page, listOf(action.points), action.style)
                is ViewerAction.Box -> action.tool.markup.let { kind ->
                    if (kind != null) {
                        viewModel.markText(action.page, action.start, action.end, kind, action.style)
                    } else {
                        viewModel.shape(action.page, action.start, action.end, action.style)
                    }
                }
                is ViewerAction.Note -> viewModel.note(action.page, action.at, action.text, action.style)
                is ViewerAction.AddStamp -> viewModel.stamp(action.page, action.at, action.kind)
                is ViewerAction.AddTextBox -> viewModel.textBox(action.page, action.at, action.text, action.style)
                is ViewerAction.SetToolStyle -> viewModel.setToolStyle(action.tool, action.style)
                is ViewerAction.MarkLines -> action.tool.markup?.let {
                    viewModel.markLines(action.page, action.lines, it, action.style, action.comment)
                }
                is ViewerAction.EditMark ->
                    viewModel.editMark(action.page, action.index, action.color, action.width, action.comment)
                is ViewerAction.DeleteMark -> viewModel.deleteMark(action.page, action.index)
                is ViewerAction.Copy -> {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.selection_copy), action.text))
                    // Android 13 and later confirm a copy themselves.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        launchMessage(R.string.copied)
                    }
                }
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

    shownInfo?.let { DocumentInfoDialog(it.name, it.info, onDismiss = { shownInfo = null }) }
}

/** Stateless viewer UI, so it can be previewed and screenshot-tested without a real PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerContent(
    state: ViewerState,
    onBack: () -> Unit,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    loadWords: suspend (page: Int) -> List<PageWord> = { emptyList() },
    loadRegion: LoadRegion = { _, _, _ -> null },
    initialMode: ViewerMode = ViewerMode.Read,
    initialSelectedPage: Int = 0,
    initialTool: Int? = null,
    savedSignatures: Map<SignatureStore.Kind, Bitmap> = emptyMap(),
    signerName: String = "",
    toolStyles: Map<AnnotateTool, ToolStyle> = emptyMap(),
    marks: List<Mark> = emptyList(),
    search: SearchResults = SearchResults(),
    initialSearchQuery: String? = null,
    stamps: List<PlacedStamp> = emptyList(),
    initialSelectedStamp: Long? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    openDocuments: List<DocumentEntry> = emptyList(),
    currentUri: String? = null,
    onSwitchTo: (DocumentEntry) -> Unit = {},
    onCloseDocument: (DocumentEntry) -> Unit = {},
    onCloseAll: () -> Unit = {},
    onOpenAnother: () -> Unit = {},
    pageColors: PageColors = PageColors.Normal,
    onPageColors: (PageColors) -> Unit = {},
    @StringRes tip: Int? = null,
    onTipDismissed: () -> Unit = {},
    onModeEntered: (ViewerMode) -> Unit = {},
    onAction: (ViewerAction) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var selectedTool by rememberSaveable { mutableStateOf(initialTool) }
    var selectedPage by rememberSaveable { mutableIntStateOf(initialSelectedPage) }
    var confirmDelete by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf(false) }
    // What to do once the reader settles unsaved changes; non-null while the dialog shows.
    var leaveThen by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSwitcher by rememberSaveable { mutableStateOf(false) }
    var pendingNote by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var pendingTextBox by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var stampKind by rememberSaveable { mutableStateOf(Stamps.Kind.Approved) }
    var pendingText by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var padFor by remember { mutableStateOf<SignatureStore.Kind?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var choosingPassword by remember { mutableStateOf(false) }
    // Chosen here so the page preview follows at once; the view model remembers them for next time.
    var styles by remember { mutableStateOf(toolStyles) }
    LaunchedEffect(toolStyles) { styles = styles + toolStyles }
    fun styleOf(tool: AnnotateTool) = styles[tool] ?: tool.defaultStyle

    // Selected text, and the lines of a selection waiting for its note to be typed.
    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var pendingTextNote by remember { mutableStateOf<Pair<Int, List<Rect>>?>(null) }

    // The mark picked for editing, by page and index, which stay the same while it is restyled.
    var pickedMark by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val selectedMark = pickedMark?.let { (page, index) -> marks.firstOrNull { it.page == page && it.index == index } }
        ?.takeIf { mode == ViewerMode.Read || (mode == ViewerMode.Annotate && AnnotateTool.forLabel(selectedTool) == null) }
    var showComments by remember { mutableStateOf(false) }
    var commentFor by remember { mutableStateOf<Mark?>(null) }
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
    LaunchedEffect(mode, selectedTool, ready?.revision) { selection = null }
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && selectedPage >= pageCount) selectedPage = pageCount - 1
    }

    // Tells the caller which mode is on screen, so it can pick a tip for it.
    val isReady = ready != null
    val currentOnModeEntered by rememberUpdatedState(onModeEntered)
    LaunchedEffect(mode, isReady) {
        if (isReady) currentOnModeEntered(mode)
    }

    fun leave(then: () -> Unit = onBack) {
        if (ready?.hasUnsavedChanges == true) leaveThen = then else then()
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
            R.string.tool_extract -> extracting = true
            R.string.tool_merge -> onAction(ViewerAction.Merge)
            R.string.tool_split -> splitting = true
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
        pickedMark = null
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

    BackHandler(enabled = selectedMark != null || mode != ViewerMode.Read || searching || ready?.hasUnsavedChanges == true) {
        when {
            selectedMark != null -> pickedMark = null
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
                            IconButton(onClick = { sharing = true }) {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.tool_share))
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
                        if (openDocuments.isNotEmpty()) {
                            OpenDocumentsButton(openDocuments.size, onClick = { showSwitcher = true })
                        }
                        if (ready != null) PageColorsButton(pageColors, onPageColors)
                    } else {
                        IconButton(onClick = { onAction(ViewerAction.Undo) }, enabled = canUndo) {
                            Icon(EditIcons.Undo, contentDescription = stringResource(R.string.undo))
                        }
                        IconButton(onClick = { onAction(ViewerAction.Redo) }, enabled = ready?.canRedo == true) {
                            Icon(EditIcons.Redo, contentDescription = stringResource(R.string.redo))
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
                selectedMark != null -> MarkEditBar(
                    mark = selectedMark,
                    onStyle = { style ->
                        val tool = selectedMark.kind.tool
                        onAction(
                            ViewerAction.EditMark(
                                selectedMark.page, selectedMark.index,
                                color = style.rgb.takeIf { style.color != selectedMark.displayColor },
                                width = style.width.takeIf { tool?.widths?.isNotEmpty() == true && it != selectedMark.width },
                            ),
                        )
                    },
                    onComment = { commentFor = selectedMark },
                    onDelete = {
                        onAction(ViewerAction.DeleteMark(selectedMark.page, selectedMark.index))
                        pickedMark = null
                    },
                    onDone = { pickedMark = null },
                )
                mode == ViewerMode.Read && searching -> Unit
                mode == ViewerMode.Read -> ModeBar(onModeSelected = {
                    mode = it
                    if (it == ViewerMode.Pages) selectedPage = currentPage
                })
                // Page tools act once on the selected page, so none stays highlighted.
                mode == ViewerMode.Pages -> ToolStrip(mode, selectedTool = null, onToolSelected = { onPagesTool(it) })
                // Choosing the active Annotate tool again puts it down, so one finger scrolls again.
                mode == ViewerMode.Annotate -> Column {
                    if (AnnotateTool.forLabel(selectedTool) == AnnotateTool.Stamp) StampBar(stampKind, onSelect = { stampKind = it })
                    AnnotateTool.forLabel(selectedTool)?.takeIf { it.hasStyle }?.let { tool ->
                        StyleBar(tool, styleOf(tool), onStyleChange = {
                            styles = styles + (tool to it)
                            onAction(ViewerAction.SetToolStyle(tool, it))
                        })
                    }
                    ToolStrip(mode, selectedTool, onToolSelected = {
                        when {
                            it == R.string.tool_comments -> showComments = true
                            selectedTool == it -> selectedTool = null
                            else -> selectedTool = it
                        }
                    })
                }
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
                    when (it) {
                        R.string.tool_share -> sharing = true
                        R.string.tool_password -> choosingPassword = true
                        R.string.tool_info -> onAction(ViewerAction.ShowInfo)
                        R.string.tool_print -> onAction(ViewerAction.Print)
                        R.string.tool_comments -> showComments = true
                    }
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
                    PageList(ready.pageSizes, ready.revision, loadPage, loadRegion, listState, pageColors) { page ->
                        if (mode == ViewerMode.Read && searching) {
                            val onPage = search.matches.withIndex().filter { it.value.page == page }
                            if (onPage.isNotEmpty()) SearchHighlights(onPage, currentMatch)
                        }
                        val pageStamps = if (mode == ViewerMode.Sign) stamps.filter { it.page == page } else emptyList()
                        val words by produceState(emptyList<PageWord>(), page, ready.revision) { value = loadWords(page) }
                        // Text can be selected while reading, or in Annotate before a tool is picked.
                        if (mode == ViewerMode.Read || (mode == ViewerMode.Annotate && tool == null)) {
                            MarkTapLayer(page, marks, selectedMark, onSelect = { pickedMark = it?.let { m -> m.page to m.index } }) {
                            TextSelectionLayer(
                                page = page,
                                words = words,
                                selection = selection,
                                onSelect = { selection = it },
                                onAction = { action ->
                                    val range = selection?.range ?: return@TextSelectionLayer
                                    val lines = words.lineBoxes(range)
                                    fun mark(markTool: AnnotateTool) = onAction(ViewerAction.MarkLines(page, markTool, styleOf(markTool), lines))
                                    when (action) {
                                        SelectionAction.Highlight -> mark(AnnotateTool.Highlight)
                                        SelectionAction.Underline -> mark(AnnotateTool.Underline)
                                        SelectionAction.StrikeOut -> mark(AnnotateTool.StrikeOut)
                                        SelectionAction.Note -> pendingTextNote = page to lines
                                        SelectionAction.Copy -> onAction(ViewerAction.Copy(words.textOf(range)))
                                    }
                                    selection = null
                                },
                            )
                            }
                        }
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
                            val style = styleOf(tool)
                            AnnotationLayer(
                                page = page,
                                tool = tool,
                                style = style,
                                pageWidthPt = ready.pageSizes[page].widthPt,
                                onStroke = { onAction(ViewerAction.Stroke(page, tool, style, it)) },
                                onBox = { start, end -> onAction(ViewerAction.Box(page, tool, style, start, end)) },
                                words = words,
                                onLines = { onAction(ViewerAction.MarkLines(page, tool, style, it)) },
                                onTap = { at ->
                                    when (tool) {
                                        AnnotateTool.Note -> pendingNote = page to at
                                        AnnotateTool.TextBox -> pendingTextBox = page to at
                                        AnnotateTool.Stamp -> onAction(ViewerAction.AddStamp(page, at, stampKind))
                                        else -> onAction(ViewerAction.Erase(page, at))
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (tip != null && ready != null) {
                TipCard(tip, onTipDismissed, Modifier.align(Alignment.BottomCenter).padding(16.dp))
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

    if (extracting) {
        ExtractPagesDialog(
            pageCount = pageCount,
            selectedPage = selectedPage,
            onDismiss = { extracting = false },
            onExtract = {
                extracting = false
                onAction(ViewerAction.Extract(it))
            },
        )
    }

    if (splitting) {
        SplitDialog(
            pageCount = pageCount,
            selectedPage = selectedPage,
            onDismiss = { splitting = false },
            onSplit = {
                splitting = false
                onAction(ViewerAction.Split(it))
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

    if (sharing && ready != null) {
        ShareSheet(
            pageCount = pageCount,
            currentPage = currentPage,
            onDismiss = { sharing = false },
            onShare = { option, pages ->
                sharing = false
                onAction(ViewerAction.Share(option, pages))
            },
        )
    }

    if (choosingPassword) {
        PasswordDialog(
            isProtected = ready?.isProtected == true,
            onDismiss = { choosingPassword = false },
            onSetPassword = {
                choosingPassword = false
                onAction(ViewerAction.SetPassword(it))
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
                onAction(ViewerAction.Note(page, styleOf(AnnotateTool.Note), at, text))
            },
        )
    }

    pendingTextNote?.let { (page, lines) ->
        TextEntryDialog(
            title = R.string.note_title,
            hint = R.string.note_hint,
            onDismiss = { pendingTextNote = null },
            onAdd = { text ->
                pendingTextNote = null
                // A note on text is a highlight carrying the note, as Acrobat and others make it.
                onAction(ViewerAction.MarkLines(page, AnnotateTool.Highlight, styleOf(AnnotateTool.Highlight), lines, text))
            },
        )
    }

    if (showComments) {
        CommentsSheet(
            marks = marks,
            onDismiss = { showComments = false },
            onOpen = { mark ->
                showComments = false
                // Mark editing happens while reading, or in Annotate with no tool picked.
                if (mode != ViewerMode.Annotate) mode = ViewerMode.Read
                selectedTool = null
                returnToPage = mark.page
                pickedMark = mark.page to mark.index
            },
        )
    }

    if (showSwitcher) {
        OpenDocumentsSheet(
            documents = openDocuments,
            currentUri = currentUri,
            onDismiss = { showSwitcher = false },
            onSwitchTo = { entry ->
                showSwitcher = false
                if (entry.uri != currentUri) leave { onSwitchTo(entry) }
            },
            onClose = { entry ->
                if (entry.uri == currentUri) {
                    showSwitcher = false
                    leave { onCloseDocument(entry); onBack() }
                } else {
                    onCloseDocument(entry)
                }
            },
            onCloseAll = {
                showSwitcher = false
                leave { onCloseAll(); onBack() }
            },
            onOpenAnother = {
                showSwitcher = false
                leave(onOpenAnother)
            },
        )
    }

    pendingTextBox?.let { (page, at) ->
        TextEntryDialog(
            title = R.string.text_box_title,
            hint = R.string.text_hint,
            onDismiss = { pendingTextBox = null },
            onAdd = { text ->
                pendingTextBox = null
                onAction(ViewerAction.AddTextBox(page, styleOf(AnnotateTool.TextBox), at, text))
            },
        )
    }

    commentFor?.let { mark ->
        val isTextBox = mark.kind == Mark.Kind.TextBox
        TextEntryDialog(
            title = if (isTextBox) R.string.mark_edit_text else R.string.comment_title,
            hint = if (isTextBox) R.string.text_hint else R.string.comment_hint,
            initial = mark.comment,
            confirm = R.string.save,
            // Clearing a comment removes it; a text box needs some text (Delete removes the box).
            allowBlank = !isTextBox,
            onDismiss = { commentFor = null },
            onAdd = { text ->
                commentFor = null
                onAction(ViewerAction.EditMark(mark.page, mark.index, comment = text))
            },
        )
    }

    leaveThen?.let { then ->
        AlertDialog(
            onDismissRequest = { leaveThen = null },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_body)) },
            confirmButton = {
                TextButton(onClick = {
                    leaveThen = null
                    onAction(ViewerAction.SaveAndLeave(then))
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { leaveThen = null }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        leaveThen = null
                        then()
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
    pageColors: PageColors,
    overlay: @Composable BoxScope.(page: Int) -> Unit = {},
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // The sharp zoomed tiles get the same night or sepia colors as the page under them.
    val detailColors = remember(pageColors) { pageColors.matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
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
                PageImage(index, size, revision, widthPx, loadPage, Modifier.fillMaxWidth(), pageColors) {
                    ZoomDetailLayer(index, revision, detail, detailColors)
                    overlay(index)
                }
            }
        }
    }
}

/**
 * One page, rendered at [widthPx]. Re-renders when the document's [revision] changes.
 * [pageColors] tints only what is drawn on screen (see PageColors.kt).
 */
@Composable
internal fun PageImage(
    index: Int,
    size: PageSize,
    revision: Int,
    widthPx: Int,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    modifier: Modifier = Modifier,
    pageColors: PageColors = PageColors.Normal,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val bitmap by produceState<Bitmap?>(null, index, widthPx, revision) {
        value = loadPage(index, widthPx)
    }
    val colorFilter = remember(pageColors) { pageColors.matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
    Box(
        modifier.aspectRatio(size.aspectRatio).background(pageColors.paper),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = colorFilter)
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
private fun TextEntryDialog(
    @StringRes title: Int,
    @StringRes hint: Int,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    initial: String = "",
    @StringRes confirm: Int = R.string.add,
    allowBlank: Boolean = false,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
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
            TextButton(onClick = { onAdd(text.trim()) }, enabled = allowBlank || text.isNotBlank()) {
                Text(stringResource(confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The text markup a tool makes, or null for tools that do not mark text. */
private val AnnotateTool.markup: Annotator.TextMarkup?
    get() = when (this) {
        AnnotateTool.Highlight -> Annotator.TextMarkup.Highlight
        AnnotateTool.Underline -> Annotator.TextMarkup.Underline
        AnnotateTool.StrikeOut -> Annotator.TextMarkup.StrikeOut
        else -> null
    }

private const val SEARCH_DELAY_MS = 300L

/** How long the view must be still before zoomed pages are sharpened. */
private const val SETTLE_MILLIS = 150L

/** The tip shown the first time [this] mode is entered, if any. */
private val ViewerMode.tip: Tip?
    get() = when (this) {
        ViewerMode.Read -> Tip.ReadZoom
        ViewerMode.Annotate -> Tip.Annotate
        ViewerMode.Sign -> Tip.Sign
        ViewerMode.Pages -> Tip.Pages
        else -> null
    }
