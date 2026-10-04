package io.github.karljuderojas.freepdf.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.karljuderojas.freepdf.FreePdfApp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.redact.RedactFill
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.edit.TextEditing
import io.github.karljuderojas.freepdf.pdf.edit.WatermarkStyle
import io.github.karljuderojas.freepdf.pdf.edit.CropMargins
import io.github.karljuderojas.freepdf.pdf.links.LinkTarget
import io.github.karljuderojas.freepdf.pdf.form.FormField
import io.github.karljuderojas.freepdf.pdf.annotate.Mark
import io.github.karljuderojas.freepdf.pdf.annotate.Stamps
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.text.PageWord
import io.github.karljuderojas.freepdf.pdf.sign.SignField
import io.github.karljuderojas.freepdf.pdf.sign.SignatureFields
import io.github.karljuderojas.freepdf.pdf.sign.SignatureMethod
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.pdf.sign.SignedCopy
import io.github.karljuderojas.freepdf.settings.Tip
import io.github.karljuderojas.freepdf.print.Printing
import io.github.karljuderojas.freepdf.settings.AppSettings
import io.github.karljuderojas.freepdf.speech.ReadAloudState
import io.github.karljuderojas.freepdf.settings.PageColors
import io.github.karljuderojas.freepdf.ui.rememberWindowSize
import io.github.karljuderojas.freepdf.share.Sharing
import io.github.karljuderojas.freepdf.pdf.sign.CertificateInfo
import io.github.karljuderojas.freepdf.ui.sign.CertificateDialog
import io.github.karljuderojas.freepdf.ui.sign.CertificatePasswordDialog
import io.github.karljuderojas.freepdf.ui.rememberPdfPicker
import io.github.karljuderojas.freepdf.ui.sign.FinishOptions
import io.github.karljuderojas.freepdf.ui.sign.FinishProgressDialog
import io.github.karljuderojas.freepdf.ui.sign.FinishSigningDialog
import io.github.karljuderojas.freepdf.ui.sign.ProgressDialog
import io.github.karljuderojas.freepdf.ui.sign.SignatureBanner
import io.github.karljuderojas.freepdf.ui.sign.SignatureDetailsDialog
import io.github.karljuderojas.freepdf.ui.sign.SignaturePadDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the viewer asks its view model to do. Page numbers are zero-based. */
sealed interface ViewerAction {
    data object Undo : ViewerAction
    data object Redo : ViewerAction
    data object Save : ViewerAction

    /**
     * Saves, then runs [then] once the save lands: back out, or close a document. [document]
     * names another open document to save, from the switcher; null saves the one on screen.
     */
    data class SaveAndLeave(val then: () -> Unit, val document: String? = null) : ViewerAction

    /** Reads the document aloud from [page]; see [ReadAloudBar] for the controls. */
    data class StartReadAloud(val page: Int) : ViewerAction
    data class ControlReadAloud(val command: ReadAloudCommand) : ViewerAction

    /** Lets the unsaved changes of the document on screen go, on the way out. */
    data object DiscardChanges : ViewerAction

    data class Rotate(val pages: Set<Int>) : ViewerAction
    /** Stamps [pages] with [text], or with the picture at [image] when that is given. */
    data class Watermark(val pages: Set<Int>, val text: String, val image: Uri?, val style: WatermarkStyle) : ViewerAction
    /** Takes the watermarks added with this app off [pages]. */
    data class RemoveWatermarks(val pages: Set<Int>) : ViewerAction
    /** Trims [pages] by [margins], or shows them in full again when [margins] is null. */
    data class Crop(val pages: Set<Int>, val margins: CropMargins?) : ViewerAction

    /** Crops each page by its own margins, as found by [io.github.karljuderojas.freepdf.pdf.edit.MarginFinder]. */
    data class TrimMargins(val margins: Map<Int, CropMargins>) : ViewerAction
    /** Adds a link over [box], an area of [page] as shown, leading to [target]. */
    data class AddLink(val page: Int, val box: DisplayRect, val target: LinkTarget) : ViewerAction
    /** Points the link at [index] in [page]'s annotations (see PageLink.index) at [target] instead. */
    data class ChangeLink(val page: Int, val index: Int, val target: LinkTarget) : ViewerAction
    data class RemoveLink(val page: Int, val index: Int) : ViewerAction
    data class Delete(val pages: Set<Int>) : ViewerAction
    data class InsertBlank(val afterPage: Int) : ViewerAction
    data class Move(val from: Int, val to: Int) : ViewerAction
    /** Moves the pages [by] places together; negative is earlier. */
    data class Shift(val pages: Set<Int>, val by: Int) : ViewerAction
    data object Merge : ViewerAction
    data class Share(val option: ShareOption, val pages: List<Int>) : ViewerAction
    data class Extract(val pages: List<Int>) : ViewerAction
    data class Split(val parts: List<List<Int>>) : ViewerAction

    /** Saves a copy with everything under [boxes] removed and painted black; the open PDF is left as it is. */
    data class Redact(val boxes: List<RedactBox>) : ViewerAction

    /** Looks over what [boxes] mark before the user confirms a redaction; see [RedactCheck]. */
    data class CheckRedaction(val boxes: List<RedactBox>) : ViewerAction
    data object ShowInfo : ViewerAction
    data object Print : ViewerAction
    data class Search(val query: String) : ViewerAction
    data class Unlock(val password: String) : ViewerAction

    /** Locks the PDF with [password], or takes its password off when it is empty. */
    data class SetPassword(val password: String) : ViewerAction

    /** Lifts the PDF's restrictions if [password] is its owner password; [onResult] says whether it was. */
    data class RemoveRestrictions(val password: String, val onResult: (Boolean) -> Unit) : ViewerAction

    /** Annotate actions. Points are fractions of the displayed page; see [AnnotationLayer]. */
    data class Stroke(val page: Int, val tool: AnnotateTool, val style: ToolStyle, val points: List<Offset>) : ViewerAction
    /** A drag with a box tool from [start] to [end]; [shape] is what the Shapes tool draws. */
    data class Box(
        val page: Int,
        val tool: AnnotateTool,
        val style: ToolStyle,
        val start: Offset,
        val end: Offset,
        val shape: Annotator.Shape = Annotator.Shape.Rectangle,
    ) : ViewerAction
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
    data class FillField(val field: FormField, val value: String?) : ViewerAction

    /** Signs [field], one of [ViewerState.Ready.signFields], with the saved signature. */
    data class SignField(val field: io.github.karljuderojas.freepdf.pdf.sign.SignField) : ViewerAction
    data class FinishSigning(val name: String, val consentText: String, val options: FinishOptions) : ViewerAction

    /** Certificate actions. */
    data object ImportCertificate : ViewerAction
    data object RemoveCertificate : ViewerAction
    data class SetTimestamps(val on: Boolean) : ViewerAction
    /** Placed stamps; see [StampLayer]. Moves are fractions of the page. */
    data class MoveStamp(val id: Long, val delta: Offset) : ViewerAction
    data class ResizeStamp(val id: Long, val factor: Float) : ViewerAction
    data class DeleteStamp(val id: Long) : ViewerAction
    data object CommitStamps : ViewerAction

    /** Edit actions. The screen opens the photo picker for [PickImage], then places the picture. */
    data class AddEditText(val page: Int, val at: Offset, val text: String) : ViewerAction

    /** Edit text: swaps the words of the line at [at] on [page], which read [oldText], for [newText]. */
    data class ReplaceText(val page: Int, val at: Offset, val oldText: String, val newText: String) : ViewerAction
    data class PickImage(val page: Int) : ViewerAction
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
    // Leaving the app (Home, a call, the screen going off) pauses reading aloud; a rotation does not.
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.pauseReadAloud()
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val signerName by viewModel.signerName.collectAsStateWithLifecycle()
    val certificate by viewModel.certificate.collectAsStateWithLifecycle()
    val timestampsOn by viewModel.timestampsOn.collectAsStateWithLifecycle()
    val finishStep by viewModel.finishing.collectAsStateWithLifecycle()
    val redactProgress by viewModel.redacting.collectAsStateWithLifecycle()
    val redactCheck by viewModel.redactCheck.collectAsStateWithLifecycle()
    var certificateFile by remember { mutableStateOf<Uri?>(null) }
    val toolStyles by viewModel.toolStyles.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val pendingInk by viewModel.pendingStrokes.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val stamps by viewModel.stamps.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val context = LocalContext.current
    val settings = (context.applicationContext as FreePdfApp).settings
    val pageColors by settings.pageColors.collectAsStateWithLifecycle()
    val readingTextSize by settings.readingTextSize.collectAsStateWithLifecycle()
    val readAloud by viewModel.readAloud.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var shownInfo by remember { mutableStateOf<ViewerEffect.ShowInfo?>(null) }
    val scope = rememberCoroutineScope()
    fun launchMessage(@StringRes text: Int) {
        scope.launch { snackbarHostState.showSnackbar(resources.getString(text)) }
    }
    val documents = (context.applicationContext as FreePdfApp).documents
    val openDocuments by documents.open.collectAsStateWithLifecycle()
    val unsavedDocuments by (context.applicationContext as FreePdfApp).sessions.unsaved.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
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
    val certificatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        certificateFile = it
    }
    val extractPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it != null) viewModel.saveExtract(it) else viewModel.cancelExtract()
    }
    val redactedPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it != null) viewModel.saveRedacted(it) else viewModel.cancelRedaction()
    }
    val splitFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) viewModel.splitInto(it) else viewModel.cancelSplit()
    }
    // The page a picked image goes on, kept across the picker in case the activity is recreated.
    var imagePage by rememberSaveable { mutableIntStateOf(0) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) viewModel.addImage(imagePage, it)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ViewerEffect.Message -> launch { snackbarHostState.showSnackbar(resources.getString(effect.text)) }
                is ViewerEffect.Text -> launch { snackbarHostState.showSnackbar(effect.text) }
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
                is ViewerEffect.SaveRedacted -> redactedPicker.launch(effect.suggestedName)
                ViewerEffect.PickSplitFolder -> splitFolderPicker.launch(null)
                is ViewerEffect.ShowInfo -> shownInfo = effect
            }
        }
    }

    ViewerContent(
        state = state,
        onBack = onBack,
        loadPage = viewModel::page,
        onPageShown = viewModel::pageShown,
        loadWords = viewModel::words,
        findLine = viewModel::editableLine,
        loadEditableLines = viewModel::editableLines,
        loadRegion = viewModel::pageRegion,
        initialMode = initialMode,
        initialTool = initialTool,
        savedSignatures = savedSignatures,
        signerName = signerName,
        certificate = certificate,
        timestampsOn = timestampsOn,
        finishStep = finishStep,
        redactCheck = redactCheck,
        redactProgress = redactProgress,
        toolStyles = toolStyles,
        marks = marks,
        pendingInk = pendingInk,
        search = search,
        stamps = stamps,
        snackbarHostState = snackbarHostState,
        openDocuments = openDocuments,
        unsavedDocuments = unsavedDocuments,
        currentUri = uri.toString(),
        onSwitchTo = { onSwitchTo(Uri.parse(it.uri)) },
        onCloseDocument = { documents.close(it.uri) },
        onCloseAll = documents::closeAll,
        onOpenAnother = pickAnother,
        restorePosition = position,
        onViewPosition = viewModel::viewPositionChanged,
        pageColors = pageColors,
        onPageColors = settings::setPageColors,
        readAloud = readAloud,
        readingTextSize = readingTextSize,
        onReadingTextSize = settings::setReadingTextSize,
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
                    viewModel.save(thenClose = true, document = action.document)
                }
                ViewerAction.DiscardChanges -> viewModel.discardChanges()
                is ViewerAction.StartReadAloud -> viewModel.readAloud.start(action.page)
                is ViewerAction.ControlReadAloud -> viewModel.readAloud.let {
                    when (action.command) {
                        ReadAloudCommand.Pause -> it.pause()
                        ReadAloudCommand.Resume -> it.resume()
                        ReadAloudCommand.Next -> it.next()
                        ReadAloudCommand.Previous -> it.previous()
                        ReadAloudCommand.Stop -> it.stop()
                        ReadAloudCommand.Acknowledge -> it.acknowledgeUnavailable()
                    }
                }
                is ViewerAction.Rotate -> viewModel.rotatePages(action.pages)
                is ViewerAction.Watermark -> viewModel.watermark(action.pages, action.text, action.image, action.style)
                is ViewerAction.RemoveWatermarks -> viewModel.removeWatermarks(action.pages)
                is ViewerAction.Crop -> viewModel.cropPages(action.pages, action.margins)
                is ViewerAction.TrimMargins -> viewModel.trimMargins(action.margins)
                is ViewerAction.AddLink -> viewModel.addLink(action.page, action.box, action.target)
                is ViewerAction.ChangeLink -> viewModel.changeLink(action.page, action.index, action.target)
                is ViewerAction.RemoveLink -> viewModel.removeLink(action.page, action.index)
                is ViewerAction.Delete -> viewModel.deletePages(action.pages)
                is ViewerAction.InsertBlank -> viewModel.insertBlankPage(action.afterPage)
                is ViewerAction.Move -> viewModel.movePage(action.from, action.to)
                is ViewerAction.Shift -> viewModel.shiftPages(action.pages, action.by)
                ViewerAction.Merge -> mergePicker.launch(arrayOf("application/pdf"))
                is ViewerAction.Share -> viewModel.share(action.option, action.pages)
                is ViewerAction.Extract -> viewModel.extract(action.pages)
                is ViewerAction.Split -> viewModel.split(action.parts)
                is ViewerAction.Redact -> viewModel.redact(action.boxes)
                is ViewerAction.CheckRedaction -> viewModel.checkRedaction(action.boxes)
                ViewerAction.ShowInfo -> viewModel.documentInfo()
                ViewerAction.Print -> viewModel.print()
                is ViewerAction.Search -> viewModel.search(action.query)
                is ViewerAction.Unlock -> viewModel.unlock(action.password)
                is ViewerAction.SetPassword -> viewModel.setPassword(action.password)
                is ViewerAction.RemoveRestrictions -> viewModel.removeRestrictions(action.password, action.onResult)
                is ViewerAction.Stroke -> viewModel.ink(action.page, listOf(action.points), action.style)
                is ViewerAction.Box -> action.tool.markup.let { kind ->
                    if (kind != null) {
                        viewModel.markText(action.page, action.start, action.end, kind, action.style)
                    } else {
                        viewModel.shape(action.page, action.start, action.end, action.style, action.shape)
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
                is ViewerAction.ReplaceText -> viewModel.replaceText(action.page, action.at, action.oldText, action.newText)
                is ViewerAction.AddDate -> viewModel.addDate(action.page, action.at)
                is ViewerAction.AddText -> viewModel.addText(action.page, action.at, action.text)
                is ViewerAction.AddCheckmark -> viewModel.addCheckmark(action.page, action.at)
                is ViewerAction.FillField -> viewModel.fillField(action.field, action.value)
                is ViewerAction.SignField -> viewModel.signField(action.field)
                is ViewerAction.FinishSigning -> viewModel.finishSigning(action.name, action.consentText, action.options)
                // Some file managers label .p12 files as octet-stream, so allow any file.
                ViewerAction.ImportCertificate -> certificatePicker.launch(arrayOf("application/x-pkcs12", "application/octet-stream", "*/*"))
                ViewerAction.RemoveCertificate -> viewModel.removeCertificate()
                is ViewerAction.SetTimestamps -> viewModel.setTimestamps(action.on)
                is ViewerAction.MoveStamp -> viewModel.moveStamp(action.id, action.delta.x, action.delta.y)
                is ViewerAction.ResizeStamp -> viewModel.resizeStamp(action.id, action.factor)
                is ViewerAction.DeleteStamp -> viewModel.deleteStamp(action.id)
                ViewerAction.CommitStamps -> viewModel.commitStamps()
                is ViewerAction.AddEditText -> viewModel.addEditText(action.page, action.at, action.text)
                is ViewerAction.PickImage -> {
                    imagePage = action.page
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        },
    )

    certificateFile?.let { file ->
        CertificatePasswordDialog(
            onDismiss = { certificateFile = null },
            onImport = { password ->
                certificateFile = null
                viewModel.importCertificate(file, password)
            },
        )
    }
    shownInfo?.let { DocumentInfoDialog(it.name, it.info, onDismiss = { shownInfo = null }) }
}

/** Stateless viewer UI, so it can be previewed and screenshot-tested without a real PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerContent(
    state: ViewerState,
    onBack: () -> Unit,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    /** Told once the main page view shows a page rendered at a revision; thumbnails do not count. */
    onPageShown: (index: Int, revision: Int) -> Unit = { _, _ -> },
    loadWords: suspend (page: Int) -> List<PageWord> = { emptyList() },
    findLine: suspend (page: Int, at: Offset) -> TextEditing.EditableLine? = { _, _ -> null },
    loadEditableLines: suspend (page: Int) -> List<TextEditing.EditableLine> = { emptyList() },
    loadRegion: LoadRegion = { _, _, _ -> null },
    initialMode: ViewerMode = ViewerMode.Read,
    initialSelectedPage: Int = 0,
    initialSelectedPages: Set<Int> = setOf(initialSelectedPage),
    initialTool: Int? = null,
    initialShape: Annotator.Shape = Annotator.Shape.Rectangle,
    initialSignField: Int? = null,
    initialRedactions: List<RedactBox> = emptyList(),
    initialConfirmRedact: Boolean = false,
    initialRedactFill: RedactFill? = null,
    savedSignatures: Map<SignatureStore.Kind, Bitmap> = emptyMap(),
    signerName: String = "",
    certificate: CertificateInfo? = null,
    timestampsOn: Boolean = false,
    finishStep: SignedCopy.Step? = null,
    redactCheck: RedactCheck? = null,
    redactProgress: RedactProgress? = null,
    initialShowCertificate: Boolean = false,
    initialShowSignatures: Boolean = false,
    toolStyles: Map<AnnotateTool, ToolStyle> = emptyMap(),
    marks: List<Mark> = emptyList(),
    pendingInk: List<PendingStroke> = emptyList(),
    search: SearchResults = SearchResults(),
    initialSearchQuery: String? = null,
    stamps: List<PlacedStamp> = emptyList(),
    initialSelectedStamp: Long? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    openDocuments: List<DocumentEntry> = emptyList(),
    unsavedDocuments: Set<String> = emptySet(),
    currentUri: String? = null,
    onSwitchTo: (DocumentEntry) -> Unit = {},
    onCloseDocument: (DocumentEntry) -> Unit = {},
    onCloseAll: () -> Unit = {},
    onOpenAnother: () -> Unit = {},
    restorePosition: ViewPosition? = null,
    onViewPosition: (ViewPosition) -> Unit = {},
    pageColors: PageColors = PageColors.Normal,
    onPageColors: (PageColors) -> Unit = {},
    readAloud: ReadAloudState = ReadAloudState(),
    readingTextSize: Int = AppSettings.DEFAULT_TEXT_SIZE,
    onReadingTextSize: (Int) -> Unit = {},
    initialReflow: Boolean = false,
    @StringRes tip: Int? = null,
    onTipDismissed: () -> Unit = {},
    onModeEntered: (ViewerMode) -> Unit = {},
    onAction: (ViewerAction) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // On a tablet held wide the pages sit two to a row, so the list's items are rows, not pages.
    val window = rememberWindowSize()
    val columns = if (window.twoPages) 2 else 1
    val currentPage by remember(columns) { derivedStateOf { firstPageOf(listState.firstVisibleItemIndex, columns) } }
    var panelOpen by rememberSaveable { mutableStateOf(true) }
    // The list is kept at the same page when turning the tablet changes how many pages a row holds.
    var shownColumns by rememberSaveable { mutableIntStateOf(columns) }
    LaunchedEffect(columns) {
        if (shownColumns != columns) {
            listState.scrollToItem(rowOf(firstPageOf(listState.firstVisibleItemIndex, shownColumns), columns))
            shownColumns = columns
        }
    }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var selectedTool by rememberSaveable { mutableStateOf(initialTool) }
    // Pages mode's selection; never empty, so the tools always have something to act on.
    var selectedPages by rememberSaveable(stateSaver = PageSetSaver) { mutableStateOf(initialSelectedPages) }
    val selectedPage = selectedPages.minOrNull() ?: 0
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var extracting by rememberSaveable { mutableStateOf(false) }
    var splitting by rememberSaveable { mutableStateOf(false) }
    // Areas marked with Redact stay until they are applied or removed; the screen does not forget them on Done.
    var redactions by rememberSaveable(stateSaver = RedactBoxesSaver) { mutableStateOf(initialRedactions) }
    var confirmRedact by rememberSaveable { mutableStateOf(initialConfirmRedact) }
    // The box colour for new marks; null is Auto (see RedactBar).
    var redactFill by rememberSaveable { mutableStateOf(initialRedactFill) }
    // The address a tapped link leads to, while asking whether to open it; and the box just dragged for a new link.
    var openingLink by rememberSaveable { mutableStateOf<String?>(null) }
    var newLinkBox by rememberSaveable(stateSaver = PageBoxSaver) { mutableStateOf<Pair<Int, DisplayRect>?>(null) }
    // The link tapped in Edit's Add link, by page and place in its annotations, while asking what to do with it.
    var pickedLink by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    val linkContext = LocalContext.current
    val linkResources = LocalResources.current
    var watermarking by rememberSaveable { mutableStateOf(false) }
    var watermarkImage by rememberSaveable { mutableStateOf<Uri?>(null) }
    val watermarkPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) watermarkImage = it
    }
    var cropping by rememberSaveable { mutableStateOf(false) }
    // What to do once the reader settles unsaved changes; non-null while the dialog shows.
    // This and the other dialog gates below are saved, so turning or unfolding the device keeps
    // the dialog and whatever was typed in it (each dialog saves its own fields).
    var leavePrompt by rememberSaveable(stateSaver = LeavePromptSaver) { mutableStateOf<LeavePrompt?>(null) }
    var showSwitcher by rememberSaveable { mutableStateOf(false) }
    var pendingNote by rememberSaveable(stateSaver = PageOffsetSaver) { mutableStateOf<Pair<Int, Offset>?>(null) }
    var pendingTextBox by rememberSaveable(stateSaver = PageOffsetSaver) { mutableStateOf<Pair<Int, Offset>?>(null) }
    var stampKind by rememberSaveable { mutableStateOf(Stamps.Kind.Approved) }
    var shapeKind by rememberSaveable { mutableStateOf(initialShape) }
    var pendingText by rememberSaveable(stateSaver = PageOffsetSaver) { mutableStateOf<Pair<Int, Offset>?>(null) }
    // The line Edit text found under the last tap: its page, where it was tapped, and its words.
    var editingLine by rememberSaveable(stateSaver = EditingLineSaver) { mutableStateOf<Triple<Int, Offset, String>?>(null) }
    val noEditableText = stringResource(R.string.edit_text_none)
    // The form field being filled in, found again by name, page and box: fields are read afresh after a change.
    var editingFieldKey by rememberSaveable(stateSaver = FieldKeySaver) { mutableStateOf<FieldKey?>(null) }
    var padFor by rememberSaveable { mutableStateOf<SignatureStore.Kind?>(null) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var showCertificate by rememberSaveable { mutableStateOf(initialShowCertificate) }
    var showSignatures by rememberSaveable { mutableStateOf(initialShowSignatures) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // The place to sign Next field last went to, and the one waiting for the signature to be drawn.
    // Held as the fields themselves: they are found again after pages move, when their order and
    // number can change, so an index into the list would point at the wrong place.
    var currentField by rememberSaveable(stateSaver = SignFieldSaver) { mutableStateOf<SignField?>(null) }
    var pendingField by remember { mutableStateOf<SignField?>(null) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var choosingPassword by rememberSaveable { mutableStateOf(false) }
    var showingRestrictions by rememberSaveable { mutableStateOf(false) }
    // Chosen here so the page preview follows at once; the view model remembers them for next time.
    var styles by remember { mutableStateOf(toolStyles) }
    LaunchedEffect(toolStyles) { styles = styles + toolStyles }
    fun styleOf(tool: AnnotateTool) = styles[tool] ?: tool.defaultStyle

    // Selected text, and the lines of a selection waiting for its note to be typed.
    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var pendingTextNote by rememberSaveable(stateSaver = PageLinesSaver) { mutableStateOf<Pair<Int, List<Rect>>?>(null) }

    // The mark picked for editing, by page and index, which stay the same while it is restyled.
    var pickedMark by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val selectedMark = pickedMark?.let { (page, index) -> marks.firstOrNull { it.page == page && it.index == index } }
        ?.takeIf { mode == ViewerMode.Read || (mode == ViewerMode.Annotate && AnnotateTool.forLabel(selectedTool) == null) }
    var showComments by rememberSaveable { mutableStateOf(false) }
    // The mark whose comment is being typed, by page and index like [pickedMark].
    var commentForKey by rememberSaveable(stateSaver = PagePairSaver) { mutableStateOf<Pair<Int, Int>?>(null) }
    val commentFor = commentForKey?.let { (page, index) -> marks.firstOrNull { it.page == page && it.index == index } }
    var searching by rememberSaveable { mutableStateOf(initialSearchQuery != null) }
    var query by rememberSaveable { mutableStateOf(initialSearchQuery.orEmpty()) }
    var currentMatch by rememberSaveable { mutableIntStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    var focusSearch by remember { mutableStateOf(false) }
    var goingToPage by rememberSaveable { mutableStateOf(false) }
    // Reading mode shows the text reflowed instead of the pages; only from Read mode.
    var reflowing by rememberSaveable { mutableStateOf(initialReflow) }
    val reflowState = rememberLazyListState()
    var showingOutline by rememberSaveable { mutableStateOf(false) }
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
    // Marks are places on the pages as they were drawn: a page edit, an undo or another change
    // to the document can move what is under them, and a saved redaction has used them. Either
    // way they go, rather than redact something the user did not mark.
    var marksMadeOn by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(ready?.revision, ready?.redactionsSaved) {
        val current = ready ?: return@LaunchedEffect
        val now = "${current.revision}/${current.redactionsSaved}"
        if (marksMadeOn != null && marksMadeOn != now) redactions = emptyList()
        marksMadeOn = now
    }
    val signedFields = ready?.let { signedPlaces(it, stamps) }.orEmpty()
    val editingField = editingFieldKey?.let { key -> ready?.formFields?.firstOrNull { it.key == key } }
    val pageCount = ready?.pageSizes?.size ?: 0
    // Stamps still being placed count as changes, for Undo and for offering Finish.
    val canUndo = ready?.canUndo == true || stamps.isNotEmpty()
    val hasSignature = ready?.hasSignature == true || stamps.any { it.content is StampContent.Signature }
    LaunchedEffect(mode, selectedTool, ready?.revision) { selection = null }

    // The selection before each page edit still in flight, oldest first. The selection moves as
    // soon as an edit is asked for; once the edit settles it is put back if the edit failed, and
    // kept within the pages there are either way. A landed edit shows as a new revision, a failed
    // one as another failed page edit, so every settled edit is seen here exactly once.
    val selectionsBeforeEdits = remember { ArrayDeque<Set<Int>>() }
    var seenFailedPageEdits by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(ready?.revision, ready?.failedPageEdits) {
        val current = ready ?: return@LaunchedEffect
        val failed = seenFailedPageEdits.let { it != null && it != current.failedPageEdits }
        seenFailedPageEdits = current.failedPageEdits
        val before = selectionsBeforeEdits.removeFirstOrNull()
        selectedPages = settleSelection(selectedPages, before, failed, current.pageSizes.size)
    }

    /** Asks for a page edit, showing [then] as the selection meanwhile; see [selectionsBeforeEdits]. */
    fun editPages(action: ViewerAction, then: Set<Int>) {
        selectionsBeforeEdits.addLast(selectedPages)
        onAction(action)
        selectedPages = then
    }

    /** Scrolls so [field], a place to sign, is about a third of the way down the screen. */
    fun goToField(field: SignField, animate: Boolean = true) {
        val current = ready ?: return
        val size = current.pageSizes.getOrNull(field.page) ?: return
        currentField = field
        scope.launch {
            val viewport = snapshotFlow { listState.layoutInfo.viewportSize }.first { it.height > 0 }
            // Pages fill the list's width, less its 8 dp padding on each side and the gaps between them.
            val pageHeight = ((viewport.width - with(density) { (8.dp * (columns + 1)).toPx() }) / columns) / size.aspectRatio
            val offset = (field.box.top * pageHeight - viewport.height / 3f).roundToInt().coerceAtLeast(0)
            if (animate) listState.animateScrollToItem(rowOf(field.page, columns), offset) else listState.scrollToItem(rowOf(field.page, columns), offset)
        }
    }

    /** Next field: the first place not signed yet after the current one, going round to the start. */
    fun nextField() {
        val current = ready ?: return
        goToField(SignatureFields.nextUnsigned(current.signFields, signedFields, currentField) ?: return)
    }

    /** A tap on the place to sign at [index] in the fields as shown: sign it, or draw the signature first if there is none yet. */
    fun signPlace(index: Int) {
        val field = ready?.signFields?.getOrNull(index) ?: return
        currentField = field
        if (savedSignatures[SignatureStore.Kind.Signature] == null) {
            pendingField = field
            padFor = SignatureStore.Kind.Signature
        } else {
            onAction(ViewerAction.SignField(field))
        }
    }

    LaunchedEffect(ready == null) {
        if (ready != null) initialSignField?.let { index -> ready.signFields.getOrNull(index)?.let { goToField(it, animate = false) } }
    }

    // Tells the caller which mode is on screen, so it can pick a tip for it.
    val isReady = ready != null
    val currentOnModeEntered by rememberUpdatedState(onModeEntered)
    LaunchedEffect(mode, isReady) {
        if (isReady) currentOnModeEntered(mode)
    }

    // Stamps still on screen (placed, or being written in after Done) are changes too.
    val hasUnsavedChanges = ready?.hasUnsavedChanges == true || stamps.isNotEmpty()

    /** What [prompt] goes on to do once the changes are settled. */
    fun thenOf(prompt: LeavePrompt): () -> Unit = when (prompt.kind) {
        LeavePrompt.Kind.Back -> onBack
        LeavePrompt.Kind.CloseCurrent -> {
            { openDocuments.firstOrNull { it.uri == prompt.document }?.let(onCloseDocument); onBack() }
        }
        LeavePrompt.Kind.CloseOther -> {
            { openDocuments.firstOrNull { it.uri == prompt.document }?.let(onCloseDocument) }
        }
        LeavePrompt.Kind.CloseAll -> {
            { onCloseAll(); onBack() }
        }
    }

    /**
     * Goes on with [prompt] once the reader has settled the unsaved changes of the document on
     * screen, asking to save first when there are any.
     */
    fun leave(prompt: LeavePrompt) {
        if (hasUnsavedChanges) leavePrompt = prompt else thenOf(prompt)()
    }

    // Where the reader was when this document was last on screen, once, as soon as it is shown.
    var positionRestored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isReady, restorePosition) {
        val position = restorePosition ?: return@LaunchedEffect
        if (!isReady || positionRestored) return@LaunchedEffect
        positionRestored = true
        if (position.page in 1 until pageCount || (position.page == 0 && position.offset > 0)) {
            listState.scrollToItem(rowOf(position.page, columns), position.offset)
        }
    }
    val currentOnViewPosition by rememberUpdatedState(onViewPosition)
    LaunchedEffect(listState, columns) {
        snapshotFlow { ViewPosition(firstPageOf(listState.firstVisibleItemIndex, columns), listState.firstVisibleItemScrollOffset) }
            .collect { currentOnViewPosition(it) }
    }

    fun onPagesTool(tool: Int) {
        when (tool) {
            R.string.tool_rotate -> editPages(ViewerAction.Rotate(selectedPages), then = selectedPages)
            // Several selected pages move together, keeping the gaps between them.
            R.string.tool_move_earlier -> if (selectedPage > 0) {
                editPages(ViewerAction.Shift(selectedPages, -1), then = selectedPages.map { it - 1 }.toSet())
            }
            R.string.tool_move_later -> if (selectedPages.max() < pageCount - 1) {
                editPages(ViewerAction.Shift(selectedPages, 1), then = selectedPages.map { it + 1 }.toSet())
            }
            R.string.tool_insert -> {
                val after = selectedPages.max()
                editPages(ViewerAction.InsertBlank(after), then = setOf(after + 1))
            }
            R.string.tool_watermark -> watermarking = true
            R.string.tool_crop -> cropping = true
            R.string.tool_delete -> confirmDelete = true
            R.string.tool_extract -> extracting = true
            R.string.tool_merge -> onAction(ViewerAction.Merge)
            R.string.tool_split -> splitting = true
            R.string.tool_select_all -> selectedPages = (0 until pageCount).toSet()
            // Straight to Android's share menu with just the selected pages, as a new PDF.
            R.string.tool_share_selected -> onAction(ViewerAction.Share(ShareOption.SomePages, selectedPages.sorted()))
        }
    }

    // Leaving Pages lands on the page that was selected there.
    var returnToPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(returnToPage) {
        returnToPage?.let { listState.scrollToItem(rowOf(it, columns)) }
        returnToPage = null
    }

    fun backToReading() {
        if (mode == ViewerMode.Pages) returnToPage = selectedPage
        // Done keeps what was placed: it is written into the PDF on the way out.
        if ((mode == ViewerMode.Sign || mode == ViewerMode.Edit) && stamps.isNotEmpty()) onAction(ViewerAction.CommitStamps)
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
    // Keyed on the results too, since an edit clears them and the query must run again.
    LaunchedEffect(query, searching, search.query) {
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
        val pageHeight = viewport.width / columns / size.aspectRatio
        val top = match.boxes.minOfOrNull { it.top } ?: 0f
        listState.animateScrollToItem(rowOf(match.page, columns), (top * pageHeight - viewport.height / 3f).toInt().coerceAtLeast(0))
    }

    // The page being read aloud stays on screen, in whichever view is showing.
    LaunchedEffect(readAloud.active, readAloud.page, reflowing) {
        if (!readAloud.active) return@LaunchedEffect
        if (reflowing) reflowState.animateScrollToItem(readAloud.page) else listState.animateScrollToItem(rowOf(readAloud.page, columns))
    }
    val readAloudUnavailable = stringResource(R.string.read_aloud_unavailable)
    LaunchedEffect(readAloud.unavailable) {
        if (readAloud.unavailable) {
            // Cleared at once so a rotation does not show it again; the message outlives this effect.
            onAction(ViewerAction.ControlReadAloud(ReadAloudCommand.Acknowledge))
            scope.launch { snackbarHostState.showSnackbar(readAloudUnavailable) }
        }
    }

    // Leaving reading mode puts the page list where the reader had got to.
    fun exitReflow() {
        reflowing = false
        scope.launch { listState.scrollToItem(rowOf(reflowState.firstVisibleItemIndex, columns)) }
    }

    BackHandler(enabled = reflowing || selectedMark != null || mode != ViewerMode.Read || searching || ready?.hasUnsavedChanges == true) {
        when {
            reflowing -> exitReflow()
            selectedMark != null -> pickedMark = null
            mode != ViewerMode.Read -> backToReading()
            searching -> closeSearch()
            else -> leave(LeavePrompt(LeavePrompt.Kind.Back))
        }
    }

    // Under 600 dp wide the top bar keeps search, Save and the open documents, and the rest go in a menu.
    val compactBar = !window.navigationRail
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    when {
                        reflowing -> TopBarTitle(stringResource(R.string.reading_mode))
                        mode == ViewerMode.Read && searching -> SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            focusRequester = searchFocus,
                        )
                        mode == ViewerMode.Pages && selectedPages.size > 1 ->
                            TopBarTitle(pluralStringResource(R.plurals.pages_selected, selectedPages.size, selectedPages.size))
                        mode != ViewerMode.Read -> TopBarTitle(stringResource(mode.label))
                        // Tapping "Page 3 of 12" asks which page to go to. A phone shows it as
                        // "3 / 12" so it keeps to one line beside the actions.
                        ready != null -> {
                            val full = stringResource(R.string.page_of, currentPage + 1, pageCount)
                            TopBarTitle(
                                if (compactBar) stringResource(R.string.page_of_short, currentPage + 1, pageCount) else full,
                                modifier = Modifier
                                    .clickable(onClickLabel = stringResource(R.string.go_to_page)) { goingToPage = true }
                                    .semantics { contentDescription = full }
                                    .testTag("page-indicator"),
                            )
                        }
                        else -> TopBarTitle(stringResource(R.string.app_name))
                    }
                },
                navigationIcon = {
                    if (reflowing) {
                        IconButton(onClick = { exitReflow() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    } else if (mode == ViewerMode.Read) {
                        IconButton(onClick = { if (searching) closeSearch() else leave(LeavePrompt(LeavePrompt.Kind.Back)) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    } else if (mode == ViewerMode.Pages && selectedPages.size > 1) {
                        IconButton(onClick = { selectedPages = setOf(selectedPage) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    }
                },
                actions = {
                    if (reflowing) {
                        TextSizeButtons(
                            readingTextSize, onReadingTextSize,
                            AppSettings.MIN_TEXT_SIZE, AppSettings.MAX_TEXT_SIZE, AppSettings.TEXT_SIZE_STEP,
                        )
                        val readAloudFromHere = { onAction(ViewerAction.StartReadAloud(reflowState.firstVisibleItemIndex)) }
                        if (compactBar) {
                            TopBarMenu(
                                items = if (readAloud.active) emptyList() else listOf(TopBarMenuItem(R.string.tool_read_aloud, readAloudFromHere)),
                                pageColors = pageColors,
                                onPageColors = onPageColors,
                            )
                        } else {
                            if (!readAloud.active) {
                                TextButton(onClick = readAloudFromHere) { Text(stringResource(R.string.tool_read_aloud)) }
                            }
                            PageColorsButton(pageColors, onPageColors)
                        }
                    } else if (mode == ViewerMode.Read && searching) {
                        SearchStepper(search, currentMatch) { step ->
                            val count = search.matches.size
                            if (count > 0) currentMatch = (currentMatch + step + count) % count
                        }
                    } else if (mode == ViewerMode.Read) {
                        val openReadingMode = {
                            scope.launch { reflowState.scrollToItem(currentPage) }
                            reflowing = true
                        }
                        if (ready != null) {
                            IconButton(onClick = {
                                focusSearch = true
                                searching = true
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                            }
                            if (window.sidePanel) {
                                IconButton(onClick = { panelOpen = !panelOpen }) {
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.side_panel))
                                }
                            }
                            if (!compactBar) {
                                ReadingModeButton { openReadingMode() }
                                IconButton(onClick = { sharing = true }) {
                                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.tool_share))
                                }
                                if (ready.outline.isNotEmpty()) {
                                    IconButton(onClick = { showingOutline = true }) {
                                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.contents))
                                    }
                                }
                            }
                        }
                        if (ready?.hasUnsavedChanges == true) {
                            TextButton(onClick = { onAction(ViewerAction.Save) }) { Text(stringResource(R.string.save), maxLines = 1) }
                        }
                        if (openDocuments.isNotEmpty()) {
                            OpenDocumentsButton(openDocuments.size, onClick = { showSwitcher = true })
                        }
                        if (ready != null) {
                            if (compactBar) {
                                // On a phone the rarer actions share one menu, so "3 / 12" never wraps.
                                TopBarMenu(
                                    items = buildList {
                                        add(TopBarMenuItem(R.string.reading_mode) { openReadingMode() })
                                        add(TopBarMenuItem(R.string.tool_share) { sharing = true })
                                        if (ready.outline.isNotEmpty()) add(TopBarMenuItem(R.string.contents) { showingOutline = true })
                                    },
                                    pageColors = pageColors,
                                    onPageColors = onPageColors,
                                )
                            } else {
                                PageColorsButton(pageColors, onPageColors)
                            }
                        }
                    } else {
                        IconButton(onClick = { pickedMark = null; onAction(ViewerAction.Undo) }, enabled = canUndo) {
                            Icon(EditIcons.Undo, contentDescription = stringResource(R.string.undo))
                        }
                        IconButton(onClick = { pickedMark = null; onAction(ViewerAction.Redo) }, enabled = ready?.canRedo == true) {
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
                readAloud.active && mode == ViewerMode.Read -> ReadAloudBar(readAloud) { onAction(ViewerAction.ControlReadAloud(it)) }
                reflowing -> Unit
                selectedMark != null -> MarkEditBar(
                    mark = selectedMark,
                    onStyle = { style ->
                        val color = style.rgb.takeIf { style.color != selectedMark.displayColor }
                        val width = style.width.takeIf { selectedMark.kind.widths.isNotEmpty() && it != selectedMark.width }
                        // Tapping the swatch already in use changes nothing, so it costs no undo step.
                        if (color != null || width != null) {
                            onAction(ViewerAction.EditMark(selectedMark.page, selectedMark.index, color = color, width = width))
                        }
                    },
                    onComment = { commentForKey = selectedMark?.let { it.page to it.index } },
                    onDelete = {
                        onAction(ViewerAction.DeleteMark(selectedMark.page, selectedMark.index))
                        pickedMark = null
                    },
                    onDone = { pickedMark = null },
                )
                mode == ViewerMode.Read && searching -> Unit
                mode == ViewerMode.Read -> ModeBar(onModeSelected = {
                    mode = it
                    if (it == ViewerMode.Pages) selectedPages = setOf(currentPage)
                })
                // Page tools act once on the selected page, so none stays highlighted.
                mode == ViewerMode.Pages -> ToolStrip(mode, selectedTool = null, onToolSelected = { onPagesTool(it) })
                // Choosing the active Annotate tool again puts it down, so one finger scrolls again.
                mode == ViewerMode.Annotate -> Column {
                    if (AnnotateTool.forLabel(selectedTool) == AnnotateTool.Stamp) StampBar(stampKind, onSelect = { stampKind = it })
                    if (AnnotateTool.forLabel(selectedTool) == AnnotateTool.Shapes) ShapeBar(shapeKind, onSelect = { shapeKind = it })
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
                mode == ViewerMode.Edit -> Column {
                    if (selectedTool == R.string.tool_add_text) EditHint(R.string.edit_hint_text)
                    if (selectedTool == R.string.tool_add_link) EditHint(R.string.edit_hint_link)
                    if (selectedTool == R.string.tool_edit_text) EditHint(R.string.edit_hint_edit_text)
                    if (selectedTool == R.string.tool_redact) {
                        RedactBar(
                            count = redactions.size,
                            fill = redactFill,
                            onFill = { redactFill = it },
                            onRemoveLast = { redactions = redactions.dropLast(1) },
                            onApply = { confirmRedact = true },
                        )
                    }
                    ToolStrip(mode, selectedTool, onToolSelected = { label ->
                        when {
                            // Add image acts once: pick a picture and it lands on the page in view.
                            label == R.string.tool_add_image -> {
                                selectedTool = null
                                onAction(ViewerAction.PickImage(currentPage))
                            }
                            selectedTool == label -> selectedTool = null
                            else -> selectedTool = label
                        }
                    })
                }
                mode == ViewerMode.Sign -> Column {
                    ready?.takeIf { it.signFields.isNotEmpty() }?.let {
                        SignFieldsBanner(remaining = it.signFields.size - signedFields.size, onNext = { nextField() })
                    }
                    SignTool.forLabel(selectedTool)?.let { tool ->
                        SignHint(
                            tool = tool,
                            savedImage = tool.signatureKind?.let { savedSignatures[it] },
                            onRedraw = { padFor = tool.signatureKind },
                            hint = if (tool == SignTool.FillForm && ready.formFields.isEmpty()) R.string.sign_hint_no_fields else tool.hint,
                        )
                    }
                    ToolStrip(mode, selectedTool, onToolSelected = { label ->
                        val tool = SignTool.forLabel(label)
                        when {
                            label == R.string.tool_certificate -> showCertificate = true
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
                        R.string.tool_read_aloud -> {
                            onAction(ViewerAction.StartReadAloud(currentPage))
                            backToReading()
                        }
                        R.string.tool_share -> sharing = true
                        R.string.tool_password -> choosingPassword = true
                        R.string.tool_restrictions -> showingRestrictions = true
                        R.string.tool_info -> onAction(ViewerAction.ShowInfo)
                        R.string.tool_print -> onAction(ViewerAction.Print)
                        R.string.tool_comments -> showComments = true
                    }
                })
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
        // On a large screen the pages, contents and comments sit beside the document.
        if (window.sidePanel && panelOpen && ready != null && mode != ViewerMode.Pages && !reflowing) {
            ViewerSidePanel(
                pageSizes = ready.pageSizes,
                revision = ready.revision,
                currentPage = currentPage,
                loadPage = loadPage,
                pageColors = pageColors,
                outline = ready.outline,
                marks = marks,
                onGoToPage = { page -> scope.launch { listState.animateScrollToItem(rowOf(page, columns)) } },
                onOpenMark = { mark ->
                    // Mark editing happens while reading, or in Annotate with no tool picked. Done
                    // is taken on the way out of Sign or Edit, so stamps still being placed are kept.
                    if (mode != ViewerMode.Annotate) backToReading()
                    selectedTool = null
                    returnToPage = mark.page
                    pickedMark = mark.page to mark.index
                },
            )
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant),
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
                reflowing -> ReflowView(
                    pageCount = pageCount,
                    revision = ready.revision,
                    loadWords = loadWords,
                    textSize = readingTextSize,
                    pageColors = pageColors,
                    listState = reflowState,
                    spoken = readAloud.takeIf { it.active },
                )
                mode == ViewerMode.Pages -> PageGrid(
                    pageSizes = ready.pageSizes,
                    revision = ready.revision,
                    failedEdits = ready.failedPageEdits,
                    selectedPages = selectedPages,
                    // With several pages picked, a tap adds or removes one; otherwise it picks just that page.
                    onPageTapped = { page ->
                        selectedPages = when {
                            selectedPages.size < 2 -> setOf(page)
                            else -> selectedPages.toggle(page)
                        }
                    },
                    onSelectionToggled = { page -> selectedPages = selectedPages.toggle(page) },
                    onPageMoved = { from, to -> editPages(ViewerAction.Move(from, to), then = setOf(to)) },
                    loadPage = loadPage,
                    pageColors = pageColors,
                )
                else -> {
                    val tool = AnnotateTool.forLabel(selectedTool).takeIf { mode == ViewerMode.Annotate }
                    val signTool = SignTool.forLabel(selectedTool).takeIf { mode == ViewerMode.Sign }
                    PageList(ready.pageSizes, ready.revision, loadPage, loadRegion, listState, pageColors, columns, onPageShown) { page ->
                        // Pen strokes still being saved stay where they were drawn, in every mode.
                        val inkOnPage = pendingInk.filter { it.page == page }
                        if (inkOnPage.isNotEmpty()) PendingInkLayer(page, inkOnPage, ready.pageSizes[page].widthPt)
                        if (mode == ViewerMode.Read && searching) {
                            val onPage = search.matches.withIndex().filter { it.value.page == page }
                            if (onPage.isNotEmpty()) SearchHighlights(onPage, currentMatch)
                        }
                        val showsStamps = mode == ViewerMode.Sign || mode == ViewerMode.Edit
                        val pageStamps = if (showsStamps) stamps.filter { it.page == page } else emptyList()
                        val addsText = mode == ViewerMode.Edit && selectedTool == R.string.tool_add_text
                        val editsText = mode == ViewerMode.Edit && selectedTool == R.string.tool_edit_text
                        val words by produceState(emptyList<PageWord>(), page, ready.revision) { value = loadWords(page) }
                        // Text can be selected while reading, or in Annotate before a tool is picked.
                        if (mode == ViewerMode.Read || (mode == ViewerMode.Annotate && tool == null)) {
                            // Links open only while reading. Their layer wraps the other two, so a
                            // long press under a link still selects the words and a tap on a mark still picks it.
                            val pageLinks = if (mode == ViewerMode.Read) ready.links.filter { it.page == page } else emptyList()
                            LinkLayer(page, pageLinks, onOpen = { link ->
                                when (val target = link.target) {
                                    is LinkTarget.Web -> openingLink = target.uri
                                    is LinkTarget.Page -> scope.launch { listState.animateScrollToItem(rowOf(target.index.coerceIn(0, pageCount - 1), columns)) }
                                }
                            }) {
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
                        }
                        if (mode == ViewerMode.Edit && selectedTool == R.string.tool_add_link) {
                            LinkBoxLayer(
                                page, ready.links.filter { it.page == page },
                                onBox = { box -> newLinkBox = page to box },
                                onPick = { pickedLink = it.page to it.index },
                            )
                        }
                        if (editsText) {
                            // Outlined are the lines Edit text can change, which are not all of the page's words.
                            val editableLines by produceState(emptyList<TextEditing.EditableLine>(), page, ready.revision) { value = loadEditableLines(page) }
                            EditTextLayer(page, editableLines) { at ->
                                scope.launch {
                                    val line = findLine(page, at)
                                    if (line == null) snackbarHostState.showSnackbar(noEditableText)
                                    else editingLine = Triple(page, at, line.text)
                                }
                            }
                        }
                        if (signTool == SignTool.FillForm) {
                            FormFieldLayer(ready.formFields.filter { it.page == page }) { field ->
                                when (val tap = formTap(field)) {
                                    is FormTap.Set -> onAction(ViewerAction.FillField(field, tap.value))
                                    FormTap.Ask -> editingFieldKey = field.key
                                    FormTap.Nothing -> Unit
                                }
                            }
                        } else if (signTool != null || addsText || (selectedStamp != null && pageStamps.isNotEmpty())) {
                            TapLayer(page) { at ->
                                val kind = signTool?.signatureKind
                                when {
                                    // The first tap away from a selected stamp only lets go of it.
                                    selectedStamp != null -> selectedStamp = null
                                    addsText -> pendingText = page to at
                                    signTool == null -> Unit
                                    kind != null && savedSignatures[kind] == null -> padFor = kind
                                    kind != null -> onAction(ViewerAction.PlaceSignature(page, at, kind))
                                    signTool == SignTool.Date -> onAction(ViewerAction.AddDate(page, at))
                                    signTool == SignTool.Text -> pendingText = page to at
                                    else -> onAction(ViewerAction.AddCheckmark(page, at))
                                }
                            }
                        }
                        if (mode == ViewerMode.Sign) {
                            val places = ready.signFields.withIndex()
                                .filter { (i, field) -> field.page == page && i !in signedFields }
                                .map { it.index to it.value }
                            if (places.isNotEmpty()) {
                                SignFieldLayer(places, current = SignatureFields.indexOf(ready.signFields, currentField), onTap = { signPlace(it) })
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
                        val redacting = mode == ViewerMode.Edit && selectedTool == R.string.tool_redact
                        if (mode == ViewerMode.Edit && (redacting || redactions.any { it.page == page })) {
                            RedactionLayer(
                                page = page,
                                boxes = redactions.filter { it.page == page },
                                active = redacting,
                                pageWidthPt = ready.pageSizes[page].widthPt,
                                words = words,
                                onBox = { rect ->
                                    val chosen = redactFill
                                    val box = RedactBox(page, rect, chosen ?: RedactFill.Black)
                                    redactions = redactions + box
                                    if (chosen == null) {
                                        // Auto: look at the page under the mark. A mark removed or cleared meanwhile stays gone.
                                        scope.launch {
                                            val fill = contrastingFill(runCatching { loadPage(page, AUTO_FILL_WIDTH_PX) }.getOrNull(), rect)
                                            if (fill != box.fill) redactions = redactions.map { if (it === box) box.copy(fill = fill) else it }
                                        }
                                    }
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
                                onBox = { start, end -> onAction(ViewerAction.Box(page, tool, style, start, end, shapeKind)) },
                                shape = shapeKind,
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
            // Over a signed PDF: who signed it and whether it changed since.
            if (mode == ViewerMode.Read && ready != null && ready.signatures.isNotEmpty()) {
                SignatureBanner(ready.signatures, onClick = { showSignatures = true }, modifier = Modifier.align(Alignment.TopCenter))
            }
            if (tip != null && ready != null) {
                TipCard(tip, onTipDismissed, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
        }
    }

    if (showSignatures && ready != null) {
        SignatureDetailsDialog(ready.signatures, onDismiss = { showSignatures = false })
    }

    if (showCertificate) {
        CertificateDialog(
            certificate = certificate,
            timestampsOn = timestampsOn,
            onImport = { onAction(ViewerAction.ImportCertificate) },
            onRemove = { onAction(ViewerAction.RemoveCertificate) },
            onTimestampsChange = { onAction(ViewerAction.SetTimestamps(it)) },
            onDismiss = { showCertificate = false },
        )
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
                    editPages(ViewerAction.Delete(selectedPages), then = setOf(selectedPage))
                }) { Text(stringResource(R.string.tool_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // The confirmation needs a look at the marked pages first, made once per set of marks (and
    // again after the process was killed, when the view model has forgotten it).
    LaunchedEffect(confirmRedact, redactions, redactCheck) {
        if (confirmRedact && redactions.isNotEmpty() && redactCheck?.boxes != redactions) onAction(ViewerAction.CheckRedaction(redactions))
    }
    if (confirmRedact) {
        RedactConfirmDialog(
            count = redactions.size,
            check = redactCheck?.takeIf { it.boxes == redactions },
            onDismiss = { confirmRedact = false },
            onConfirm = {
                confirmRedact = false
                // Text and pictures placed with Add text or Add image are part of the page being redacted.
                // Writing them in does not move anything under the marks, so the marks stay for a
                // second try if the save is cancelled.
                if (stamps.isNotEmpty()) {
                    marksMadeOn = null
                    onAction(ViewerAction.CommitStamps)
                }
                onAction(ViewerAction.Redact(redactions))
            },
        )
    }

    if (extracting) {
        ExtractPagesDialog(
            pageCount = pageCount,
            selectedPages = selectedPages.sorted(),
            onDismiss = { extracting = false },
            onExtract = {
                extracting = false
                onAction(ViewerAction.Extract(it))
            },
        )
    }

    openingLink?.let { address ->
        OpenLinkDialog(
            address = address,
            onDismiss = { openingLink = null },
            onOpen = {
                openingLink = null
                val opened = runCatching {
                    linkContext.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(address)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                if (!opened) scope.launch { snackbarHostState.showSnackbar(linkResources.getString(R.string.link_open_failed)) }
            },
        )
    }

    newLinkBox?.let { (page, box) ->
        AddLinkDialog(
            pageCount = pageCount,
            onDismiss = { newLinkBox = null },
            onAdd = { target ->
                newLinkBox = null
                onAction(ViewerAction.AddLink(page, box, target))
            },
        )
    }

    // The link picked in Edit's Add link, if it is still there (it goes when the link is removed or undone).
    pickedLink?.let { (page, index) -> ready?.links?.firstOrNull { it.page == page && it.index == index } }?.let { link ->
        AddLinkDialog(
            pageCount = pageCount,
            existing = link.target,
            onDismiss = { pickedLink = null },
            onAdd = { target ->
                pickedLink = null
                onAction(ViewerAction.ChangeLink(link.page, link.index, target))
            },
            onRemove = {
                pickedLink = null
                onAction(ViewerAction.RemoveLink(link.page, link.index))
            },
        )
    }

    if (watermarking) {
        WatermarkDialog(
            pageCount = pageCount,
            selectedPages = selectedPages.sorted(),
            pageAspect = ready?.pageSizes?.getOrNull(selectedPage)?.aspectRatio ?: 0.77f,
            image = watermarkImage,
            onChooseImage = { watermarkPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismiss = { watermarking = false },
            onWatermark = { pages, text, image, style ->
                watermarking = false
                onAction(ViewerAction.Watermark(pages, text, image, style))
            },
            onRemove = { pages ->
                watermarking = false
                onAction(ViewerAction.RemoveWatermarks(pages))
            },
        )
    }

    if (cropping) {
        CropPagesDialog(
            pageCount = pageCount,
            selectedPages = selectedPages.sorted(),
            pageAspect = ready?.pageSizes?.getOrNull(selectedPage)?.aspectRatio ?: 0.77f,
            previewPage = selectedPage,
            loadPage = loadPage,
            onDismiss = { cropping = false },
            onTrim = { margins ->
                cropping = false
                editPages(ViewerAction.TrimMargins(margins), then = selectedPages)
            },
            onCrop = { pages, margins ->
                cropping = false
                editPages(ViewerAction.Crop(pages, margins), then = selectedPages)
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
            onDismiss = {
                padFor = null
                pendingField = null
            },
            onSave = { image, method ->
                padFor = null
                onAction(ViewerAction.SaveSignature(kind, image, method))
                // Drawn after tapping a place to sign: sign it straight away.
                if (kind == SignatureStore.Kind.Signature) pendingField?.let { field -> onAction(ViewerAction.SignField(field)) }
                pendingField = null
            },
        )
    }

    if (finishing) {
        FinishSigningDialog(
            initialName = signerName,
            certificate = certificate,
            onDismiss = { finishing = false },
            onFinish = { name, consentText, options ->
                finishing = false
                backToReading()
                onAction(ViewerAction.FinishSigning(name, consentText, options))
            },
        )
    }

    finishStep?.let { FinishProgressDialog(it) }
    redactProgress?.let {
        ProgressDialog(
            title = stringResource(R.string.redact_progress_title),
            text = stringResource(R.string.redact_progress_page, it.page, it.of),
            tag = "redact-progress",
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

    if (showingRestrictions) {
        RestrictionsDialog(
            restrictions = ready?.restrictions.orEmpty(),
            onDismiss = { showingRestrictions = false },
            onRemove = { password, wrong ->
                onAction(ViewerAction.RemoveRestrictions(password) { ok -> if (ok) showingRestrictions = false else wrong() })
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
                onAction(if (mode == ViewerMode.Edit) ViewerAction.AddEditText(page, at, text) else ViewerAction.AddText(page, at, text))
            },
        )
    }

    editingLine?.let { (page, at, text) ->
        TextEntryDialog(
            title = R.string.edit_text_title,
            hint = R.string.edit_text_hint,
            initial = text,
            confirm = R.string.save,
            allowBlank = true,
            onDismiss = { editingLine = null },
            onAdd = { changed ->
                editingLine = null
                if (changed != text) onAction(ViewerAction.ReplaceText(page, at, text, changed))
            },
        )
    }

    editingField?.let { field ->
        FormFieldDialog(
            field = field,
            onDismiss = { editingFieldKey = null },
            onSet = { value ->
                editingFieldKey = null
                if (value != field.value) onAction(ViewerAction.FillField(field, value))
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
                // A note on text is a highlight carrying the note, as most PDF readers make it.
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
        // Every open document keeps its own session, so switching and opening another never
        // lose anything and ask nothing; closing a document with unsaved changes does ask.
        val unsavedOthers = unsavedDocuments.filter { it != currentUri }
        OpenDocumentsSheet(
            documents = openDocuments,
            unsaved = if (hasUnsavedChanges) unsavedOthers.toSet() + setOfNotNull(currentUri) else unsavedOthers.toSet(),
            currentUri = currentUri,
            onDismiss = { showSwitcher = false },
            onSwitchTo = { entry ->
                showSwitcher = false
                if (entry.uri != currentUri) onSwitchTo(entry)
            },
            onClose = { entry ->
                when {
                    entry.uri == currentUri -> {
                        showSwitcher = false
                        leave(LeavePrompt(LeavePrompt.Kind.CloseCurrent, entry.uri))
                    }
                    entry.uri in unsavedOthers -> {
                        showSwitcher = false
                        leavePrompt = LeavePrompt(LeavePrompt.Kind.CloseOther, entry.uri)
                    }
                    else -> onCloseDocument(entry)
                }
            },
            onCloseAll = {
                showSwitcher = false
                // Saving from here could only save the document on screen, so when others have
                // changes too the choice is to discard them all or go back and save each.
                val closeAll = LeavePrompt(LeavePrompt.Kind.CloseAll, canSave = unsavedOthers.isEmpty())
                if (unsavedOthers.isNotEmpty()) leavePrompt = closeAll else leave(closeAll)
            },
            onOpenAnother = {
                showSwitcher = false
                onOpenAnother()
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
            onDismiss = { commentForKey = null },
            onAdd = { text ->
                commentForKey = null
                onAction(ViewerAction.EditMark(mark.page, mark.index, comment = text))
            },
        )
    }

    leavePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { leavePrompt = null },
            title = { Text(stringResource(if (prompt.canSave) R.string.unsaved_title else R.string.unsaved_others_title)) },
            text = { Text(stringResource(if (prompt.canSave) R.string.unsaved_body else R.string.unsaved_others_body)) },
            confirmButton = {
                if (prompt.canSave) {
                    TextButton(onClick = {
                        leavePrompt = null
                        onAction(ViewerAction.SaveAndLeave(thenOf(prompt), prompt.document))
                    }) { Text(stringResource(R.string.save)) }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { leavePrompt = null }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        leavePrompt = null
                        // Going back leaves the session in place, so its changes are dropped first;
                        // the other ways out close the session, changes and all.
                        if (prompt.kind == LeavePrompt.Kind.Back) onAction(ViewerAction.DiscardChanges)
                        thenOf(prompt)()
                    }) { Text(stringResource(R.string.discard)) }
                }
            },
        )
    }
}

/**
 * The unsaved-changes question, and what it is on the way to ([kind]): going back, closing the
 * document on screen, closing another open document, or closing them all. [document] is the
 * document being closed for the two closing kinds. With [canSave] false only discarding is
 * offered (several documents have changes). Plain values, so the open dialog survives a turn.
 */
private data class LeavePrompt(val kind: Kind, val document: String? = null, val canSave: Boolean = true) {
    enum class Kind { Back, CloseCurrent, CloseOther, CloseAll }
}

private val LeavePromptSaver = listSaver<LeavePrompt?, Any>(
    save = { prompt -> if (prompt == null) emptyList() else listOf(prompt.kind.name, prompt.document ?: "", prompt.canSave) },
    restore = { saved -> LeavePrompt(LeavePrompt.Kind.valueOf(saved[0] as String), (saved[1] as String).ifEmpty { null }, saved[2] as Boolean) },
)

/** Picks a form field out of the document's list again after a turn: a radio group shares a name, so the box tells its choices apart. */
private data class FieldKey(val name: String, val page: Int, val box: DisplayRect)

private val FormField.key: FieldKey get() = FieldKey(name, page, box)

private val FieldKeySaver = listSaver<FieldKey?, Any>(
    save = { key -> if (key == null) emptyList() else listOf(key.name, key.page, key.box.left, key.box.top, key.box.right, key.box.bottom) },
    restore = { saved ->
        FieldKey(saved[0] as String, saved[1] as Int, DisplayRect(saved[2] as Float, saved[3] as Float, saved[4] as Float, saved[5] as Float))
    },
)

// Savers for the dialog gates: a page with a tapped point, a dragged box, a line's words or the
// selected lines. Each saves nothing for null, so the dialog stays closed when it was closed.

private val PageOffsetSaver = listSaver<Pair<Int, Offset>?, Any>(
    save = { pending -> if (pending == null) emptyList() else listOf(pending.first, pending.second.x, pending.second.y) },
    restore = { saved -> (saved[0] as Int) to Offset(saved[1] as Float, saved[2] as Float) },
)

private val PagePairSaver = listSaver<Pair<Int, Int>?, Int>(
    save = { pair -> if (pair == null) emptyList() else listOf(pair.first, pair.second) },
    restore = { saved -> saved[0] to saved[1] },
)

private val PageBoxSaver = listSaver<Pair<Int, DisplayRect>?, Any>(
    save = { pending ->
        if (pending == null) emptyList()
        else listOf(pending.first, pending.second.left, pending.second.top, pending.second.right, pending.second.bottom)
    },
    restore = { saved -> (saved[0] as Int) to DisplayRect(saved[1] as Float, saved[2] as Float, saved[3] as Float, saved[4] as Float) },
)

private val EditingLineSaver = listSaver<Triple<Int, Offset, String>?, Any>(
    save = { line -> if (line == null) emptyList() else listOf(line.first, line.second.x, line.second.y, line.third) },
    restore = { saved -> Triple(saved[0] as Int, Offset(saved[1] as Float, saved[2] as Float), saved[3] as String) },
)

private val PageLinesSaver = listSaver<Pair<Int, List<Rect>>?, Any>(
    save = { pending ->
        if (pending == null) emptyList()
        else listOf(pending.first) + pending.second.flatMap { listOf(it.left, it.top, it.right, it.bottom) }
    },
    restore = { saved ->
        (saved[0] as Int) to saved.drop(1).chunked(4).map { Rect(it[0] as Float, it[1] as Float, it[2] as Float, it[3] as Float) }
    },
)

@Composable
private fun PageList(
    pageSizes: List<PageSize>,
    revision: Int,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    loadRegion: LoadRegion,
    listState: LazyListState,
    pageColors: PageColors,
    columns: Int = 1,
    onPageShown: (index: Int, revision: Int) -> Unit = { _, _ -> },
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
        // Each page is as wide as its share of the row, so one is rendered at that width.
        val widthPx = with(LocalDensity.current) { ((maxWidth - 8.dp * (columns + 1)) / columns).roundToPx() }
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
            items(rowCount(pageSizes.size, columns)) { row ->
                val first = firstPageOf(row, columns)
                val next = firstPageOf(row + 1, columns)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    // The first page of a two-page view sits alone on the right, as a book's cover does.
                    if (next - first < columns) Spacer(Modifier.weight((columns - (next - first)).toFloat()))
                    for (index in first until minOf(pageSizes.size, next)) {
                        PageImage(index, pageSizes[index], revision, widthPx, loadPage, Modifier.weight(1f), pageColors, onRendered = onPageShown) {
                            ZoomDetailLayer(index, revision, detail, detailColors)
                            overlay(index)
                        }
                    }
                    // A last page left alone in its row keeps its width instead of stretching.
                    if (next > pageSizes.size) Spacer(Modifier.weight((next - pageSizes.size).toFloat()))
                }
            }
        }
    }
}

/**
 * One page, rendered at [widthPx]. Re-renders when the document's [revision] changes.
 * [pageColors] tints only what is drawn on screen (see PageColors.kt). [onRendered] hears which
 * revision of the page is on screen each time a new picture of it is shown.
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
    onRendered: (index: Int, revision: Int) -> Unit = { _, _ -> },
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val currentOnRendered by rememberUpdatedState(onRendered)
    val bitmap by produceState<Bitmap?>(null, index, widthPx, revision) {
        val rendered = loadPage(index, widthPx)
        value = rendered
        // In the same frame as the new picture, so anything drawn over the old one in its place
        // does not go a frame early.
        if (rendered != null) currentOnRendered(index, revision)
    }
    val colorFilter = remember(pageColors) { pageColors.matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
    Box(
        // Overlays (links, selections, marks) stay within the page even where their boxes reach past it.
        modifier.aspectRatio(size.aspectRatio).clipToBounds().background(pageColors.paper),
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

/** The width a page is drawn at to see what colour lies under a new redaction mark. */
private const val AUTO_FILL_WIDTH_PX = 400

/** How long the view must be still before zoomed pages are sharpened. */
private const val SETTLE_MILLIS = 150L

/** Adds [page] to the selection, or takes it out unless it is the only one left. */
private fun Set<Int>.toggle(page: Int): Set<Int> = when {
    page !in this -> this + page
    size > 1 -> this - page
    else -> this
}

private val PageSetSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })

// The page list's items are rows of [columns] pages. With two columns the first page sits alone in
// row 0, as the cover of a book, so facing pages (2-3, 4-5) share a row; with one column a row is a page.

/** The row holding [page]. */
internal fun rowOf(page: Int, columns: Int): Int = if (columns == 1) page else (page + 1) / columns

/** The first page of [row]; one past the last page for the row after the last. */
internal fun firstPageOf(row: Int, columns: Int): Int = if (columns == 1) row else (row * columns - 1).coerceAtLeast(0)

/** How many rows [pageCount] pages take. */
internal fun rowCount(pageCount: Int, columns: Int): Int = if (pageCount == 0) 0 else rowOf(pageCount - 1, columns) + 1

/** Keeps the current place to sign across configuration changes; nothing is saved for null. */
private val SignFieldSaver = listSaver<SignField?, Any>(
    save = { field ->
        if (field == null) emptyList()
        else listOf(field.page, field.box.left, field.box.top, field.box.right, field.box.bottom, field.source.name)
    },
    restore = { saved ->
        SignField(
            saved[0] as Int,
            DisplayRect(saved[1] as Float, saved[2] as Float, saved[3] as Float, saved[4] as Float),
            SignField.Source.valueOf(saved[5] as String),
        )
    },
)

/** The tip shown the first time [this] mode is entered, if any. */
private val ViewerMode.tip: Tip?
    get() = when (this) {
        ViewerMode.Read -> Tip.ReadZoom
        ViewerMode.Annotate -> Tip.Annotate
        ViewerMode.Sign -> Tip.Sign
        ViewerMode.Pages -> Tip.Pages
        ViewerMode.Edit -> Tip.Edit
        else -> null
    }
