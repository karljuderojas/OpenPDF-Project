package io.github.karljuderojas.freepdf.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.form.FormFiller
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.annotate.Mark
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.info.DocumentInfo
import io.github.karljuderojas.freepdf.pdf.links.LinkTarget
import io.github.karljuderojas.freepdf.pdf.links.PageLink
import io.github.karljuderojas.freepdf.pdf.render.OutlineItem
import io.github.karljuderojas.freepdf.pdf.render.PageBox
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.sign.CertificateInfo
import io.github.karljuderojas.freepdf.pdf.sign.SignatureReport
import io.github.karljuderojas.freepdf.pdf.sign.SignatureFields
import io.github.karljuderojas.freepdf.pdf.text.PageText
import io.github.karljuderojas.freepdf.pdf.text.PageWord
import io.github.karljuderojas.freepdf.pdf.edit.TextEditing.EditableLine
import io.github.karljuderojas.freepdf.ui.viewer.nearest
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.pdf.sign.SignedCopy
import io.github.karljuderojas.freepdf.pdf.sign.TimestampReport
import io.github.karljuderojas.freepdf.ui.sign.SignatureInk
import io.github.karljuderojas.freepdf.settings.PageColors
import io.github.karljuderojas.freepdf.settings.SpeechRate
import io.github.karljuderojas.freepdf.settings.ThemeChoice
import io.github.karljuderojas.freepdf.ui.sign.TypedSignature
import io.github.karljuderojas.freepdf.speech.ReadAloudState
import io.github.karljuderojas.freepdf.ui.files.FilesContent
import io.github.karljuderojas.freepdf.ui.files.UnsavedCloseDialog
import io.github.karljuderojas.freepdf.ui.sign.CertificatePasswordDialog
import io.github.karljuderojas.freepdf.ui.sign.FinishSigningDialog
import io.github.karljuderojas.freepdf.ui.home.HomeContent
import io.github.karljuderojas.freepdf.ui.settings.SettingsContent
import io.github.karljuderojas.freepdf.ui.tools.ToolsContent
import io.github.karljuderojas.freepdf.ui.create.ImagesToPdfContent
import io.github.karljuderojas.freepdf.ui.create.ScannerContent
import io.github.karljuderojas.freepdf.ui.create.thumbKey
import io.github.karljuderojas.freepdf.pdf.scan.PageDetector
import io.github.karljuderojas.freepdf.pdf.scan.PerspectiveWarp
import io.github.karljuderojas.freepdf.pdf.scan.Quad
import io.github.karljuderojas.freepdf.pdf.scan.ScanFilter
import io.github.karljuderojas.freepdf.pdf.scan.ScanFilters
import io.github.karljuderojas.freepdf.pdf.scan.ScanPage
import io.github.karljuderojas.freepdf.pdf.scan.SyntheticPhoto
import io.github.karljuderojas.freepdf.pdf.create.PageFit
import androidx.compose.ui.graphics.asImageBitmap
import io.github.karljuderojas.freepdf.ui.theme.FreePdfTheme
import io.github.karljuderojas.freepdf.ui.viewer.DocumentInfoDialog
import io.github.karljuderojas.freepdf.ui.viewer.AnnotateTool
import io.github.karljuderojas.freepdf.ui.viewer.ToolStyle
import io.github.karljuderojas.freepdf.ui.viewer.GoToPageDialog
import io.github.karljuderojas.freepdf.ui.viewer.SearchResults
import io.github.karljuderojas.freepdf.ui.viewer.TextMatch
import io.github.karljuderojas.freepdf.ui.viewer.PlacedStamp
import io.github.karljuderojas.freepdf.ui.viewer.RedactBox
import io.github.karljuderojas.freepdf.ui.viewer.StampContent
import io.github.karljuderojas.freepdf.ui.viewer.StampGeometry
import io.github.karljuderojas.freepdf.ui.viewer.ViewerAction
import io.github.karljuderojas.freepdf.ui.viewer.ViewerContent
import io.github.karljuderojas.freepdf.ui.viewer.ViewerMode
import io.github.karljuderojas.freepdf.ui.viewer.ViewerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.Instant
import java.util.Calendar

private const val HOUR = 60 * 60 * 1000L

/**
 * Takes the content down with its saveable state kept, and brings it back restored from that
 * state, as the activity being recreated does. StateRestorationTester does the same but waits
 * for Compose to be idle between the steps, which a dialog with a text field never is, so the
 * test drives the clock itself around [save] and [restore].
 */
private class Restoration {
    private var saved: Map<String, List<Any?>> = emptyMap()
    private var parent: SaveableStateRegistry? = null
    private var current: SaveableStateRegistry? = null
    private var generation by mutableIntStateOf(0)
    private var showing by mutableStateOf(true)

    @Composable
    fun Content(content: @Composable () -> Unit) {
        parent = LocalSaveableStateRegistry.current
        if (!showing) return
        // Values must be ones a Bundle takes, as the activity's own registry requires.
        val registry = remember(generation) { SaveableStateRegistry(saved) { parent?.canBeSaved(it) ?: true }.also { current = it } }
        CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
    }

    fun save() {
        saved = checkNotNull(current).performSave()
        showing = false
    }

    fun restore() {
        generation++
        showing = true
    }
}

/**
 * Renders each screen to a PNG. CI runs `recordRoborazziDebug` on every pull request and posts
 * the images on the PR for review. Add a test here for every new or changed screen.
 *
 * Pages come from a real sample PDF (resources/sample/agreement.pdf), pre-rendered to PNG by
 * scripts/make_sample_pdf.py, because PDFium's native library does not load under Robolectric.
 * The Fill form screens use the sample sign-up form (resources/sample/form.pdf) the same way.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val samplePages = listOf(loadSample("page-1.png"), loadSample("page-2.png"))

    // Page 1 at 2.5x, standing in for PDFium's sharp rendering of a zoomed page.
    private val largePages by lazy { mapOf(0 to loadSample("page-1-large.png")) }
    private val sample = ViewerState.Ready(List(samplePages.size) { PageSize(612f, 792f) })
    // The two sample pages repeated, for the selection and drag screens.
    private val sixPages = ViewerState.Ready(List(6) { PageSize(612f, 792f) })

    // Six pages with a table of contents, for the tablet's side panel.
    private val tabletState = sixPages.copy(
        outline = listOf(
            OutlineItem("Service Agreement", 0, 0),
            OutlineItem("1. Services", 0, 1),
            OutlineItem("2. Payment", 1, 1),
            OutlineItem("Signatures", 1, 0),
        ),
    )

    // The sign-up form (sample/form.pdf), with its fields read by the app's own code.
    private val formPages = listOf(loadSample("form-page.png"))
    private val form by lazy {
        ViewerState.Ready(
            listOf(PageSize(612f, 792f)),
            formFields = javaClass.classLoader!!.getResourceAsStream("sample/form.pdf").use { PDDocument.load(it).use(FormFiller::fields) },
        )
    }

    // The agreement's two places to sign (both on page 2), found by the app's own code.
    private val signing by lazy {
        sample.copy(signFields = javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf").use { PDDocument.load(it).use(SignatureFields::find) })
    }

    @Test
    fun home() = capture("home") { shell(MainTab.Home) { HomeContent(sampleRecent, {}, {}, {}, {}, {}, {}, modifier = it) } }

    @Test
    fun homeEmpty() = capture("home_empty") { shell(MainTab.Home) { HomeContent(emptyList(), {}, {}, {}, {}, {}, {}, modifier = it) } }

    @Test
    fun tools() = capture("tools") { shell(MainTab.Tools) { ToolsContent(onToolPicked = {}, modifier = it) } }

    @Test
    fun imagesToPdfEmpty() = capture("images_to_pdf_empty") {
        ImagesToPdfContent(emptyList(), emptyMap(), PageFit.A4, null, false, {}, { _, _ -> }, {}, {}, {}, {})
    }

    @Test
    fun imagesToPdfPicked() = capture("images_to_pdf_picked") {
        val photos = listOf("content://a", "content://b", "content://c")
        val thumbs = photos.zip(listOf(samplePages[0], samplePages[1], samplePages[0])).toMap().mapValues { it.value.asImageBitmap() }
        ImagesToPdfContent(photos, thumbs, PageFit.A4, null, false, {}, { _, _ -> }, {}, {}, {}, {})
    }

    @Test
    fun imagesToPdfMaking() = capture("images_to_pdf_making") {
        val photos = listOf("content://a", "content://b", "content://c")
        val thumbs = photos.zip(listOf(samplePages[0], samplePages[1], samplePages[0])).toMap().mapValues { it.value.asImageBitmap() }
        ImagesToPdfContent(photos, thumbs, PageFit.Picture, 2, false, {}, { _, _ -> }, {}, {}, {}, {})
    }

    // A skewed photo of the sample page on a desk, with the corners the app's own detector finds in it.
    private val deskPhoto by lazy { SyntheticPhoto.make(samplePages[0]) }
    private val detectedQuad by lazy { PageDetector.detect(deskPhoto) ?: Quad.inset(0.04f) }

    private fun scanThumbs(pages: List<ScanPage>, filter: ScanFilter) = pages.associate { page ->
        thumbKey(page, filter) to ScanFilters.apply(PerspectiveWarp.warp(deskPhoto, page.quad, 480), filter).asImageBitmap()
    }

    @Test
    fun scannerEmpty() = capture("scanner_empty") {
        ScannerContent(emptyList(), emptyMap(), ScanFilter.Color, null, false, null, false, null, false, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
    }

    @Test
    fun scannerPages() = capture("scanner_pages") {
        val pages = listOf(ScanPage(1, "a", detectedQuad), ScanPage(2, "b", detectedQuad))
        ScannerContent(pages, scanThumbs(pages, ScanFilter.Color), ScanFilter.Color, null, false, null, false, null, false, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
    }

    @Test
    fun scannerPagesBlackAndWhite() = capture("scanner_pages_black_white") {
        val pages = listOf(ScanPage(1, "a", detectedQuad), ScanPage(2, "b", detectedQuad))
        ScannerContent(pages, scanThumbs(pages, ScanFilter.BlackWhite), ScanFilter.BlackWhite, null, false, null, false, null, false, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
    }

    @Test
    fun scannerAdjustEdges() = capture("scanner_adjust_edges") {
        val pages = listOf(ScanPage(1, "a", detectedQuad))
        ScannerContent(pages, emptyMap(), ScanFilter.Color, 0, true, deskPhoto.asImageBitmap(), false, null, false, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
    }

    @Test
    fun scannerSaving() = capture("scanner_saving") {
        val pages = listOf(ScanPage(1, "a", detectedQuad), ScanPage(2, "b", detectedQuad))
        ScannerContent(pages, scanThumbs(pages, ScanFilter.Grayscale), ScanFilter.Grayscale, null, false, null, false, 1, false, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
    }

    /** The photo, then the straightened page in each look, so the pipeline can be judged by eye. */
    @Test
    fun scannerLooks() = capture("scanner_looks") {
        val flat = PerspectiveWarp.warp(deskPhoto, detectedQuad, 700)
        val shots = listOf(
            "Photo" to deskPhoto,
            "Color" to ScanFilters.apply(flat, ScanFilter.Color),
            "Grayscale" to ScanFilters.apply(flat, ScanFilter.Grayscale),
            "Black and white" to ScanFilters.apply(flat, ScanFilter.BlackWhite),
        )
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxSize().background(Color.White).padding(8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            shots.forEach { (label, bitmap) ->
                androidx.compose.foundation.layout.Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text(label, color = Color.Black)
                    androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = label, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    @Test
    fun toolsSearch() = capture("tools_search_tick") {
        shell(MainTab.Tools) { ToolsContent(onToolPicked = {}, modifier = it, initialQuery = "tick") }
    }

    @Test
    fun toolsSearchKeptAcrossTabs() {
        // Tabs keep their state while another is shown: the search typed in Tools is still there
        // after a trip to Home, so this matches tools_search_tick.
        show {
            var tab by remember { mutableStateOf(MainTab.Tools) }
            AppShell(selected = tab, onSelect = { tab = it }) { selected, modifier ->
                when (selected) {
                    MainTab.Tools -> ToolsContent(onToolPicked = {}, modifier = modifier)
                    else -> HomeContent(emptyList(), {}, {}, {}, {}, {}, {}, modifier = modifier)
                }
            }
        }
        composeRule.onNodeWithTag("tools-search").performTextInput("tick")
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tools-search").assertTextContains("tick")
        captureRoot("tools_search_kept")
    }

    @Test
    fun toolsSearchNothing() = capture("tools_search_none") {
        shell(MainTab.Tools) { ToolsContent(onToolPicked = {}, modifier = it, initialQuery = "spreadsheet") }
    }

    @Test
    fun settings() = capture("settings") {
        shell(MainTab.Settings) {
            SettingsContent(
                theme = ThemeChoice.System,
                onTheme = {},
                rememberHistory = true,
                onRememberHistory = {},
                onClearHistory = {},
                version = "0.1.0",
                onSourceCode = {},
                showTips = true,
                onShowTips = {},
                onResetTips = {},
                modifier = it,
                pageColors = PageColors.Normal,
                onPageColors = {},
            )
        }
    }

    @Test
    fun settingsSpeechRate() = capture("settings_speech_rate") {
        shell(MainTab.Settings) {
            SettingsContent(
                theme = ThemeChoice.System,
                onTheme = {},
                rememberHistory = true,
                onRememberHistory = {},
                onClearHistory = {},
                version = "0.1.0",
                onSourceCode = {},
                modifier = it,
                pageColors = PageColors.Sepia,
                onPageColors = {},
                speechRate = SpeechRate.Fast,
                onSpeechRate = {},
            )
        }
    }

    @Test
    fun settingsDark() = capture("settings_dark", darkTheme = true) {
        shell(MainTab.Settings) {
            SettingsContent(
                theme = ThemeChoice.Dark,
                onTheme = {},
                rememberHistory = true,
                onRememberHistory = {},
                onClearHistory = {},
                version = "0.1.0",
                onSourceCode = {},
                showTips = true,
                onShowTips = {},
                onResetTips = {},
                modifier = it,
                pageColors = PageColors.Normal,
                onPageColors = {},
            )
        }
    }

    @Test
    fun filesEmpty() = capture("files_empty") { files(open = emptyList(), recent = emptyList()) }

    @Test
    fun files() = capture("files") {
        files(
            open = listOf(sampleRecent[0], DocumentEntry("content://b", "Lease renewal 2027.pdf", now - 2 * HOUR)),
            recent = sampleRecent,
        )
    }

    // Tablets: tabs in a rail, and a landscape viewer with a side panel and two pages to a row.

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletFiles() = capture("tablet_files") {
        files(
            open = listOf(sampleRecent[0], DocumentEntry("content://b", "Lease renewal 2027.pdf", now - 2 * HOUR)),
            recent = sampleRecent,
        )
    }

    // A phone on its side is wide enough for the rail too, though not for the tablet layouts.
    @Test
    @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun phoneLandscapeFiles() = capture("phone_landscape_files") {
        files(
            open = listOf(sampleRecent[0], DocumentEntry("content://b", "Lease renewal 2027.pdf", now - 2 * HOUR)),
            recent = sampleRecent,
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletViewerLandscape() = capture("tablet_viewer_landscape") { viewer(ViewerMode.Read, state = tabletState) }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletViewerContents() {
        show { viewer(ViewerMode.Read, state = tabletState) }
        composeRule.onNodeWithText("Contents").performClick()
        captureRoot("tablet_viewer_contents")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletViewerComments() {
        show { viewer(ViewerMode.Read, state = tabletState, marks = sampleMarks) }
        composeRule.onNodeWithText("Comments", substring = true).performClick()
        captureRoot("tablet_viewer_comments")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletViewerPortrait() = capture("tablet_viewer_portrait") { viewer(ViewerMode.Read, state = tabletState) }

    @Test
    fun viewerReflow() = capture("viewer_reflow") { viewer(ViewerMode.Read, reflow = true) }

    @Test
    fun viewerReflowNightLarge() = capture("viewer_reflow_night_large") {
        viewer(ViewerMode.Read, reflow = true, pageColors = PageColors.Night, readingTextSize = 26)
    }

    @Test
    fun viewerReadAloud() = capture("viewer_read_aloud") {
        viewer(
            ViewerMode.Read,
            readAloud = ReadAloudState(active = true, speaking = true, page = 0, sentence = 2, text = "The Provider agrees to perform the services described in Schedule A."),
        )
    }

    @Test
    fun viewerReflowReadAloud() = capture("viewer_reflow_read_aloud") {
        viewer(
            ViewerMode.Read,
            reflow = true,
            readAloud = ReadAloudState(active = true, speaking = false, page = 0, sentence = 1, text = "Paused on this sentence."),
        )
    }

    @Test
    fun viewerReflowSepia() = capture("viewer_reflow_sepia") { viewer(ViewerMode.Read, reflow = true, pageColors = PageColors.Sepia) }

    @Test
    fun filesCloseUnsaved() = capture("files_close_unsaved") {
        files(
            open = listOf(sampleRecent[0], DocumentEntry("content://b", "Lease renewal 2027.pdf", now - 2 * HOUR)),
            recent = sampleRecent,
            unsaved = setOf("content://b"),
            closing = true,
        )
    }

    // The same question for a document with stamps still being placed: only the viewer can write them in.
    @Test
    fun filesCloseUnsavedStamps() = capture("files_close_unsaved_stamps") {
        files(
            open = listOf(sampleRecent[0], DocumentEntry("content://b", "Lease renewal 2027.pdf", now - 2 * HOUR)),
            recent = sampleRecent,
            unsaved = setOf("content://b"),
            closing = true,
            stamps = true,
        )
    }

    @Test
    fun viewerRead() = capture("viewer_read") { viewer(ViewerMode.Read) }

    @Test
    fun viewerReadZoomed() {
        show { viewer(ViewerMode.Read) }
        composeRule.waitForIdle()
        // Pinch out to about 2.5x while dragging both fingers down, which pans to the page's title.
        // The clock is held, so the first capture is the scaled page before it is sharpened.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("page-list").performTouchInput {
            pinch(Offset(440f, 250f), Offset(340f, 1300f), Offset(640f, 450f), Offset(740f, 1900f))
        }
        composeRule.mainClock.advanceTimeByFrame()
        captureRoot("viewer_read_zoomed_mid_pinch")
        // Once the view has been still for a moment, the visible part is rendered at 2.5x.
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        captureRoot("viewer_read_zoomed")
    }

    @Test
    fun viewerReadOpenDocs() = capture("viewer_read_open_docs") {
        viewer(ViewerMode.Read, openDocuments = sampleRecent.take(3))
    }

    @Test
    fun viewerSwitcher() {
        show { viewer(ViewerMode.Read, openDocuments = sampleRecent.take(3)) }
        composeRule.onNodeWithTag("open-documents").performClick()
        composeRule.waitForIdle()
        // The sheet is its own window, so capture the whole screen rather than the root node.
        captureScreenRoboImage("build/outputs/roborazzi/viewer_switcher.png")
    }

    @Test
    fun viewerSwitcherUnsaved() {
        // The document on screen and the W-9 both have changes not saved yet; switching keeps
        // them, and the cards say so.
        show {
            viewer(
                ViewerMode.Read,
                sample.copy(canUndo = true, hasUnsavedChanges = true),
                openDocuments = sampleRecent.take(3),
                unsavedDocuments = setOf(sampleRecent[1].uri),
            )
        }
        composeRule.onNodeWithTag("open-documents").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_switcher_unsaved.png")
    }

    @Test
    fun viewerCloseAllWithOthersUnsaved() {
        // Close all with changes in another document: saving from here could only save this
        // one, so the only choices are to discard them all or go back.
        show { viewer(ViewerMode.Read, openDocuments = sampleRecent.take(3), unsavedDocuments = setOf(sampleRecent[1].uri)) }
        composeRule.onNodeWithTag("open-documents").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Close all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard changes?").assertExists()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_close_all_unsaved.png")
    }

    @Test
    fun viewerReadNight() = capture("viewer_read_night") { viewer(ViewerMode.Read, pageColors = PageColors.Night) }

    @Test
    fun viewerReadSepia() = capture("viewer_read_sepia") { viewer(ViewerMode.Read, pageColors = PageColors.Sepia) }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPageColorsMenu() {
        show { viewer(ViewerMode.Read, pageColors = PageColors.Night) }
        composeRule.onNodeWithContentDescription("Page colors").performClick()
        composeRule.waitForIdle()
        // The menu is a popup, so capture the whole screen rather than the root node.
        captureScreenRoboImage("build/outputs/roborazzi/viewer_page_colors_menu.png")
    }

    @Test
    fun viewerUnsaved() = capture("viewer_read_unsaved") {
        viewer(ViewerMode.Read, sample.copy(canUndo = true, hasUnsavedChanges = true))
    }

    @Test
    fun viewerSearch() = capture("viewer_search") {
        // Where "Client" sits in the sample, from pdftotext -bbox, in points from the top left.
        fun match(page: Int, left: Float, top: Float, right: Float, bottom: Float) =
            TextMatch(page, listOf(PageBox(left / 612f, top / 792f, right / 612f, bottom / 792f)))
        val results = SearchResults(
            query = "Client",
            matches = listOf(
                match(0, 93.0f, 165.5f, 118.0f, 175.2f),
                match(0, 202.7f, 293.5f, 229.6f, 303.2f),
                match(0, 424.5f, 350.5f, 451.4f, 360.2f),
                match(0, 170.6f, 407.5f, 197.5f, 417.2f),
                match(1, 396.5f, 138.5f, 423.3f, 148.2f),
                match(1, 72.0f, 343.2f, 96.3f, 352.0f),
            ),
        )
        viewer(ViewerMode.Read, search = results, searchQuery = "Client")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerGoToPage() {
        // Shown over the viewer directly; tapping "Page 1 of 2" opens the same dialog. Its page
        // field takes focus, and the blinking cursor never lets Compose go idle, so the dialog
        // opens only after the clock is being driven by hand.
        val open = mutableStateOf(false)
        show {
            viewer(ViewerMode.Read)
            if (open.value) GoToPageDialog(pageCount = 2, onDismiss = {}, onGo = {})
        }
        composeRule.mainClock.autoAdvance = false
        open.value = true
        composeRule.mainClock.advanceTimeBy(500)
        // The dialog is its own window, which is attached and laid out by the main looper.
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.mainClock.advanceTimeBy(500)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_go_to_page.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerOutline() {
        val outline = listOf(OutlineItem("Service Agreement", 0, 0)) +
            listOf("1. Services", "2. Timeline", "3. Fees and payment", "4. Changes", "5. Ownership")
                .map { OutlineItem(it, 0, 1) } +
            listOf("6. Confidentiality", "7. Termination", "Signatures").map { OutlineItem(it, 1, 1) }
        show { viewer(ViewerMode.Read, sample.copy(outline = outline)) }
        composeRule.onNodeWithContentDescription("Contents").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_outline.png")
    }

    @Test
    fun viewerPassword() = capture("viewer_password") { viewer(ViewerMode.Read, ViewerState.Locked()) }

    @Test
    fun viewerFailed() = capture("viewer_failed") { viewer(ViewerMode.Read, ViewerState.Failed("Not a PDF file")) }

    @Test
    fun viewerLoading() {
        show { viewer(ViewerMode.Read, ViewerState.Loading) }
        // The spinner never stops, so hold the clock at one moment before capturing.
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(500)
        captureRoot("viewer_loading")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSaveChanges() {
        show { viewer(ViewerMode.Read, sample.copy(canUndo = true, hasUnsavedChanges = true)) }
        // Going back with unsaved changes asks whether to save them first.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        // The dialog is its own window, so capture the whole screen rather than the root node.
        captureScreenRoboImage("build/outputs/roborazzi/viewer_save_changes.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPasswordWrong() {
        show { viewer(ViewerMode.Read, ViewerState.Locked(wrongPassword = true)) }
        // Typing focuses the field, whose blinking cursor never lets Compose go idle.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("password-field").performTextInput("lease2026")
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_password_wrong.png")
    }

    @Test
    fun viewerAnnotate() = capture("viewer_annotate") { viewer(ViewerMode.Annotate) }

    @Test
    fun viewerAnnotateHighlight() {
        show { viewer(ViewerMode.Annotate, tool = R.string.tool_highlight) }
        // Mid-drag from "Northwind" into the paragraph's second line: the preview snaps to whole words.
        val from = wordIndex(0, "Northwind")
        val layer = "annotation-layer-0"
        composeRule.onNodeWithTag(layer).performTouchInput {
            down(wordCentre(layer, 0, from))
            moveTo(wordCentre(layer, 0, from + 3))
            moveTo(wordCentre(layer, 0, from + 17))
        }
        captureRoot("viewer_annotate_highlight")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSelectText() {
        show { viewer(ViewerMode.Read) }
        // Press and hold on "Northwind", then drag to the end of the next line.
        val from = wordIndex(0, "Northwind")
        val layer = "text-layer-0"
        composeRule.onNodeWithTag(layer).performTouchInput { down(wordCentre(layer, 0, from)) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag(layer).performTouchInput {
            moveTo(wordCentre(layer, 0, from + 6))
            moveTo(wordCentre(layer, 0, from + 20))
            up()
        }
        composeRule.waitForIdle()
        // The popup is its own window, so capture the whole screen.
        captureScreenRoboImage("build/outputs/roborazzi/viewer_select_text.png")
    }

    @Test
    fun viewerAnnotatePen() {
        show { viewer(ViewerMode.Annotate, tool = R.string.tool_pen) }
        composeRule.onNodeWithTag("annotation-layer-0").performTouchInput {
            down(Offset(160f, 960f))
            for (i in 1..60) {
                val t = i / 60f
                moveTo(Offset(160f + t * 520f, 960f - 70f * kotlin.math.sin(t * 12f) * (1f - t / 2)))
            }
        }
        captureRoot("viewer_annotate_pen")
    }

    @Test
    fun viewerAnnotatePenStyled() {
        // A thick red pen picked from the style bar, with an undone stroke that Redo can bring back.
        show {
            viewer(
                ViewerMode.Annotate,
                sample.copy(canUndo = true, canRedo = true, hasUnsavedChanges = true),
                tool = R.string.tool_pen,
                toolStyles = mapOf(AnnotateTool.Pen to ToolStyle(Color(0xFFE52929), 8f)),
            )
        }
        composeRule.onNodeWithTag("annotation-layer-0").performTouchInput {
            down(Offset(200f, 900f))
            for (i in 1..40) moveTo(Offset(200f + i * 12f, 900f + 40f * kotlin.math.sin(i / 6f)))
        }
        captureRoot("viewer_annotate_pen_styled")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerAnnotateNote() {
        show { viewer(ViewerMode.Annotate, tool = R.string.tool_note) }
        // The dialog focuses its text field, whose blinking cursor never lets Compose go idle,
        // so drive the clock by hand from here.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("annotation-layer-0").performTouchInput { click(Offset(860f, 240f)) }
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_annotate_note.png")
    }

    @Test
    fun viewerAnnotateTip() = capture("viewer_annotate_tip") { viewer(ViewerMode.Annotate, tip = R.string.tip_annotate) }

    @Test
    fun viewerMarkSelected() {
        show { viewer(ViewerMode.Read, marks = sampleMarks) }
        // Tap the highlight over "Northwind Studio": it gets an outline and the edit bar replaces the mode bar.
        val highlight = sampleMarks.first()
        composeRule.onNodeWithTag("mark-layer-0").performTouchInput {
            click(Offset((highlight.left + highlight.right) / 2 * width, (highlight.top + highlight.bottom) / 2 * height))
        }
        composeRule.waitForIdle()
        captureRoot("viewer_mark_selected")
    }

    @Test
    fun viewerStampSelected() {
        // A stamp this app placed can be recoloured, so its edit bar offers the colours too.
        val stamp = Mark(
            page = 0, index = 2, kind = Mark.Kind.Stamp, left = 0.3f, top = 0.62f, right = 0.55f, bottom = 0.67f,
            color = Annotator.Rgb(0.13f, 0.55f, 0.25f), width = 1f, comment = "Approved", markedText = "", author = "Dana",
            modified = sampleMarks.first().modified,
        )
        show { viewer(ViewerMode.Read, marks = sampleMarks + stamp) }
        composeRule.onNodeWithTag("mark-layer-0").performTouchInput {
            click(Offset((stamp.left + stamp.right) / 2 * width, (stamp.top + stamp.bottom) / 2 * height))
        }
        composeRule.waitForIdle()
        captureRoot("viewer_stamp_selected")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerComments() {
        show { viewer(ViewerMode.Annotate, marks = sampleMarks) }
        composeRule.onNodeWithText("Comments").performScrollTo().performClick()
        composeRule.waitForIdle()
        // The sheet is its own window, so capture the whole screen.
        captureScreenRoboImage("build/outputs/roborazzi/viewer_comments.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerAnnotateTextBox() {
        show { viewer(ViewerMode.Annotate, tool = R.string.tool_text_box) }
        // The dialog's text field keeps Compose from going idle, as in viewerAnnotateNote.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("annotation-layer-0").performTouchInput { click(Offset(300f, 1000f)) }
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_annotate_text_box.png")
    }

    @Test
    fun viewerAnnotateStamp() = capture("viewer_annotate_stamp") { viewer(ViewerMode.Annotate, tool = R.string.tool_stamp) }

    @Test
    fun viewerSign() = capture("viewer_sign") { viewer(ViewerMode.Sign) }

    @Test
    fun viewerSignTip() = capture("viewer_sign_tip") { viewer(ViewerMode.Sign, tip = R.string.tip_sign) }

    @Test
    fun viewerSignPlacing() = capture("viewer_sign_placing") {
        viewer(
            ViewerMode.Sign,
            tool = R.string.tool_signature,
            savedSignatures = mapOf(SignatureStore.Kind.Signature to SignatureInk.render(sampleSignature(), 0xFF1A3FA8.toInt(), 6f)),
        )
    }

    @Test
    fun viewerSignAdjust() {
        // A signature just placed on the Provider line, selected, and a date beside it.
        show { viewer(ViewerMode.Sign, stamps = placedStamps(), selectedStamp = 1L) }
        composeRule.onNodeWithTag("page-list").performScrollToIndex(1)
        captureRoot("viewer_sign_adjust")
    }

    @Test
    fun viewerSignMoveResize() {
        show {
            var stamps by remember { mutableStateOf(placedStamps()) }
            viewer(ViewerMode.Sign, stamps = stamps, selectedStamp = 1L, onAction = { action ->
                stamps = when (action) {
                    is ViewerAction.MoveStamp -> stamps.map { if (it.id == action.id) it.copy(box = it.box.moved(action.delta.x, action.delta.y)) else it }
                    is ViewerAction.ResizeStamp -> stamps.map { if (it.id == action.id) it.copy(box = it.box.scaled(action.factor, letter)) else it }
                    else -> stamps
                }
            })
        }
        composeRule.onNodeWithTag("page-list").performScrollToIndex(1)
        // Drag the signature down to the Client line, then pull its corner to make it bigger.
        composeRule.onNodeWithTag("stamp-1").performTouchInput {
            down(center)
            for (i in 1..10) moveBy(Offset(0f, 13.5f))
            up()
        }
        composeRule.onNodeWithTag("stamp-resize-1").performTouchInput {
            down(center)
            for (i in 1..10) moveBy(Offset(10f, 3f))
            up()
        }
        composeRule.waitForIdle()
        captureRoot("viewer_sign_move_resize")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignPad() {
        show { viewer(ViewerMode.Sign) }
        // First use of Signature opens the pad.
        composeRule.onNodeWithText("Signature").performClick()
        composeRule.onNodeWithTag("signature-pad").performTouchInput {
            sampleSignature().forEach { stroke ->
                down(stroke.first())
                stroke.drop(1).forEach { moveTo(it) }
                up()
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_pad.png")
    }

    // The pad after the phone is rotated: the strokes drawn so far are still there.
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignPadRestored() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { FreePdfTheme(dynamicColor = false) { viewer(ViewerMode.Sign) } }
        composeRule.onNodeWithText("Signature").performClick()
        composeRule.onNodeWithTag("signature-pad").performTouchInput {
            sampleSignature().forEach { stroke ->
                down(stroke.first())
                stroke.drop(1).forEach { moveTo(it) }
                up()
            }
        }
        composeRule.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("signature-pad").assertExists()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_pad_restored.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignPadTyped() {
        show { viewer(ViewerMode.Sign, signerName = "Dana Whitfield") }
        composeRule.onNodeWithText("Signature").performClick()
        composeRule.onNodeWithTag("pad-tab-type").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_pad_typed.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignPadPhoto() {
        show { viewer(ViewerMode.Sign) }
        composeRule.onNodeWithText("Signature").performClick()
        composeRule.onNodeWithTag("pad-tab-photo").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_pad_photo.png")
    }

    // The Photo tab after a picture is chosen: the crop box, and the signature cut out of the paper.
    @Test
    fun signaturePhotoCropped() = capture("signature_photo_cropped") {
        val photo = io.github.karljuderojas.freepdf.pdf.sign.SignaturePhotos.make()
        val box = io.github.karljuderojas.freepdf.ui.create.CropBox(0.15f, 0.15f, 0.85f, 0.9f)
        val cut = io.github.karljuderojas.freepdf.pdf.sign.SignatureCutout.extract(photo, 0xFF111111.toInt())
        androidx.compose.material3.Surface(tonalElevation = 6.dp) {
            io.github.karljuderojas.freepdf.ui.sign.SignaturePhotoContent(
                hasPhoto = true, photo = photo, crop = box, onCrop = {}, result = cut, looked = true,
                onTakePhoto = {}, onChoosePhoto = {}, onUseAnother = {}, modifier = Modifier.padding(20.dp),
            )
        }
    }

    @Test
    fun viewerSignPlacingTyped() {
        val typeface = ResourcesCompat.getFont(RuntimeEnvironment.getApplication(), R.font.dancing_script)!!
        capture("viewer_sign_placing_typed") {
            viewer(
                ViewerMode.Sign,
                tool = R.string.tool_signature,
                savedSignatures = mapOf(
                    SignatureStore.Kind.Signature to TypedSignature.render("Dana Whitfield", typeface, 0xFF1A3FA8.toInt()),
                ),
            )
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignFinish() {
        show { viewer(ViewerMode.Sign, sample.copy(canUndo = true, hasUnsavedChanges = true, hasSignature = true), signerName = "Dana Whitfield") }
        // The dialog's name field takes focus, and its blinking cursor never lets Compose go idle,
        // so drive the clock by hand and capture without further clicks.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_finish.png")
    }

    // Finish is waiting on the timestamp server; the spinner never lets Compose go idle, so drive the clock.
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignFinishing() {
        var step by mutableStateOf<SignedCopy.Step?>(null)
        show { viewer(ViewerMode.Sign, sample.copy(hasSignature = true), finishStep = step) }
        composeRule.mainClock.autoAdvance = false
        step = SignedCopy.Step.Timestamping
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_finishing.png")
    }

    // Next field has gone to the first place to sign, on page 2.
    @Test
    fun viewerSignFields() = capture("viewer_sign_fields") { viewer(ViewerMode.Sign, signing, signField = 0) }

    // Next field tapped again: the second place is the current one now, drawn stronger than the first.
    @Test
    fun viewerSignFieldsNext() {
        show { viewer(ViewerMode.Sign, signing, signField = 0) }
        composeRule.onNodeWithText("Next field").performClick()
        composeRule.waitForIdle()
        captureRoot("viewer_sign_fields_next")
    }

    // Both places tapped: each holds the signature, fitted to it and still movable; the last one is selected.
    @Test
    fun viewerSignFieldsDone() = capture("viewer_sign_fields_done") {
        val signature = SignatureInk.render(sampleSignature(), 0xFF1A3FA8.toInt(), 6f)
        val stamps = signing.signFields.mapIndexed { i, field ->
            val box = StampGeometry.fieldBox(field.box, signature.width, signature.height, signing.pageSizes[field.page])
            PlacedStamp(i + 1L, field.page, StampContent.Signature(SignatureStore.Kind.Signature, signature), box)
        }
        viewer(ViewerMode.Sign, signing, signField = 1, stamps = stamps, selectedStamp = stamps.size.toLong())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignFinishEditable() {
        var finishing by mutableStateOf(false)
        show {
            viewer(ViewerMode.Sign, sample.copy(hasSignature = true))
            if (finishing) {
                FinishSigningDialog(initialName = "Dana Whitfield", initialLock = false, onDismiss = {}, onFinish = { _, _, _ -> })
            }
        }
        // As in viewerSignFinish, the dialog's name field never lets Compose go idle, so it opens
        // only once the clock is driven by hand.
        composeRule.mainClock.autoAdvance = false
        finishing = true
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_finish_editable.png")
    }

    @Test
    fun viewerFillForm() {
        show { viewer(ViewerMode.Sign, form, tool = R.string.tool_fill_form, pages = formPages) }
        // Bring the selected Fill form chip into view at the end of the tool strip.
        composeRule.onNodeWithText("Fill form").performScrollTo()
        captureRoot("viewer_fill_form")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerFillFormText() {
        // Editing a name typed earlier.
        val filled = form.copy(formFields = form.formFields.map { if (it.name == "name") it.copy(value = "Dana Whitfield") else it })
        show { viewer(ViewerMode.Sign, filled, tool = R.string.tool_fill_form, pages = formPages) }
        // A dialog with a text field never lets Compose go idle here (see viewerAnnotateNote),
        // so drive the clock by hand from here.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("form-field-name-0").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_fill_form_text.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerFillFormChoice() {
        show { viewer(ViewerMode.Sign, form, tool = R.string.tool_fill_form, pages = formPages) }
        composeRule.onNodeWithTag("form-field-team-10").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_fill_form_choice.png")
    }

    @Test
    fun viewerFillFormNoFields() = capture("viewer_fill_form_no_fields") {
        viewer(ViewerMode.Sign, tool = R.string.tool_fill_form)
    }

    @Test
    fun viewerEdit() = capture("viewer_edit") { viewer(ViewerMode.Edit) }

    @Test
    fun viewerEditAddText() = capture("viewer_edit_add_text") { viewer(ViewerMode.Edit, tool = R.string.tool_add_text) }

    @Test
    fun viewerEditAddLink() = capture("viewer_edit_add_link") { viewer(ViewerMode.Edit, tool = R.string.tool_add_link) }

    // Dragging a box over the page asks where the link should go.
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerEditAddLinkAsk() {
        show { viewer(ViewerMode.Edit, tool = R.string.tool_add_link) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("link-box-layer-0").performTouchInput {
            swipe(Offset(width * 0.1f, height * 0.2f), Offset(width * 0.6f, height * 0.25f))
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_edit_add_link_ask.png")
    }

    // The same dialog after the phone is turned: the dragged box and the question are still there.
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerEditAddLinkAskRestored() {
        val restoration = Restoration()
        show { restoration.Content { viewer(ViewerMode.Edit, tool = R.string.tool_add_link) } }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("link-box-layer-0").performTouchInput {
            swipe(Offset(width * 0.1f, height * 0.2f), Offset(width * 0.6f, height * 0.25f))
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        turn(restoration)
        assertTrue("Add a link" in dialogTexts())
        captureScreenRoboImage("build/outputs/roborazzi/viewer_edit_add_link_ask_restored.png")
    }

    // Tapping a web link in a PDF asks before leaving the app.
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerLinkOpen() {
        val linked = sample.copy(links = listOf(PageLink(0, DisplayRect(0.1f, 0.1f, 0.6f, 0.15f), LinkTarget.Web("https://example.com/terms"))))
        show { viewer(ViewerMode.Read, linked) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("link-0-0").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_link_open.png")
    }

    @Test
    fun viewerEditText() = capture("viewer_edit_text") { viewer(ViewerMode.Edit, tool = R.string.tool_edit_text) }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerEditTextDialog() {
        show { viewer(ViewerMode.Edit, tool = R.string.tool_edit_text) }
        // Tap the line holding "Northwind": its words open in a box to change. The dialog's text
        // field keeps Compose from going idle, as in viewerAnnotateNote.
        composeRule.mainClock.autoAdvance = false
        val layer = "edit-text-layer-0"
        composeRule.onNodeWithTag(layer).performTouchInput { click(wordCentre(layer, 0, wordIndex(0, "Northwind"))) }
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_edit_text_dialog.png")
    }

    // The same dialog after the phone is turned: the line's words are still there to change.
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerEditTextDialogRestored() {
        val restoration = Restoration()
        show { restoration.Content { viewer(ViewerMode.Edit, tool = R.string.tool_edit_text) } }
        composeRule.mainClock.autoAdvance = false
        val layer = "edit-text-layer-0"
        composeRule.onNodeWithTag(layer).performTouchInput { click(wordCentre(layer, 0, wordIndex(0, "Northwind"))) }
        composeRule.mainClock.advanceTimeBy(1_000)
        turn(restoration)
        assertTrue(dialogTexts().any { "Northwind" in it })
        captureScreenRoboImage("build/outputs/roborazzi/viewer_edit_text_dialog_restored.png")
    }

    /**
     * Saves the screen's state and brings the screen back from it, as turning the phone does,
     * with the clock driven by hand (a dialog's text field never lets Compose go idle).
     */
    private fun turn(restoration: Restoration) {
        restoration.save()
        composeRule.mainClock.advanceTimeBy(500)
        shadowOf(Looper.getMainLooper()).idle()
        restoration.restore()
        composeRule.mainClock.advanceTimeBy(500)
        // The dialog is its own window, which is attached and laid out by the main looper.
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.mainClock.advanceTimeBy(500)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The texts in the newest dialog window, read straight from its semantics (no idle wait). */
    private fun dialogTexts(): List<String> {
        val dialog = ShadowDialog.getLatestDialog() ?: return emptyList()
        val texts = ArrayList<String>()
        fun collect(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
            node.config.getOrNull(SemanticsProperties.EditableText)?.let { texts += it.text }
            node.children.forEach { collect(it) }
        }
        fun walk(view: View) {
            if (view is RootForTest) collect(view.semanticsOwner.rootSemanticsNode)
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(dialog.window!!.decorView)
        return texts
    }

    @Test
    fun viewerEditPlaced() {
        // A heading typed with Add text, and a picture from Add image, selected so its handles show.
        val heading = "Draft - for review"
        val picture = sampleLogo()
        val stamps = listOf(
            PlacedStamp(
                1L, 0, StampContent.Text(heading, null),
                StampGeometry.textBox(Offset(0.1f, 0.06f), listOf(heading), letter, StampGeometry.EDIT_TEXT_SIZE) { it.length * 0.5f },
            ),
            PlacedStamp(
                2L, 0, StampContent.Image(picture),
                StampGeometry.imageBox(Offset(0.72f, 0.2f), picture.width, picture.height, letter).scaled(0.6f, letter),
            ),
        )
        show { viewer(ViewerMode.Edit, sample.copy(canUndo = true), stamps = stamps, selectedStamp = 2L) }
        captureRoot("viewer_edit_placed")
    }

    @Test
    fun viewerRedact() = capture("viewer_redact") { viewer(ViewerMode.Edit, tool = R.string.tool_redact) }

    @Test
    fun viewerRedactMarkedByDragging() {
        // Dragging over text with Redact chosen marks whole words, one box per line.
        show { viewer(ViewerMode.Edit, tool = R.string.tool_redact) }
        val from = wordIndex(0, "Northwind")
        val layer = "annotation-layer-0"
        composeRule.onNodeWithTag(layer).performTouchInput {
            down(wordCentre(layer, 0, from))
            moveTo(wordCentre(layer, 0, from + 3))
            moveTo(wordCentre(layer, 0, from + 17))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Apply").assertExists()
        captureRoot("viewer_redact_dragged")
    }

    @Test
    fun viewerRedactMarked() = capture("viewer_redact_marked") {
        viewer(ViewerMode.Edit, tool = R.string.tool_redact, redactions = sampleRedactions())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerRedactConfirm() {
        show { viewer(ViewerMode.Edit, tool = R.string.tool_redact, redactions = sampleRedactions(), confirmRedact = true) }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_redact_confirm.png")
    }

    @Test
    fun viewerRedactApplyAsksThenSendsTheMarks() {
        val actions = mutableListOf<ViewerAction>()
        show { viewer(ViewerMode.Edit, tool = R.string.tool_redact, redactions = sampleRedactions(), onAction = { actions += it }) }
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.waitForIdle()
        // Nothing is sent until the dialog is confirmed.
        assertTrue(actions.none { it is ViewerAction.Redact })
        composeRule.onNodeWithText("Save redacted copy").performClick()
        composeRule.waitForIdle()
        val redact = actions.filterIsInstance<ViewerAction.Redact>().single()
        assertEquals(sampleRedactions(), redact.boxes)
    }

    @Test
    fun viewerPages() = capture("viewer_pages") {
        viewer(ViewerMode.Pages, sample.copy(canUndo = true, hasUnsavedChanges = true), selectedPage = 1)
    }

    @Test
    fun viewerPagesNight() = capture("viewer_pages_night") {
        viewer(ViewerMode.Pages, selectedPage = 1, pageColors = PageColors.Night)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPagesDelete() {
        show { viewer(ViewerMode.Pages, selectedPage = 1) }
        composeRule.onNodeWithText("Delete").performScrollTo().performClick()
        composeRule.waitForIdle()
        // The dialog is its own window, so capture the whole screen rather than the root node.
        captureScreenRoboImage("build/outputs/roborazzi/viewer_pages_delete.png")
    }

    // The dialogs' page fields keep Compose from going idle, so drive the clock by hand.
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPagesExtract() {
        show { viewer(ViewerMode.Pages, selectedPage = 1) }
        composeRule.onNodeWithText("Extract").performScrollTo()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Extract").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_pages_extract.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPagesSplit() {
        show { viewer(ViewerMode.Pages, selectedPage = 0) }
        composeRule.onNodeWithText("Split").performScrollTo()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Split").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_pages_split.png")
    }

    // The text field keeps Compose from going idle, so drive the clock by hand (see viewerPagesExtract).
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPagesWatermark() {
        show { viewer(ViewerMode.Pages, sixPages, selectedPage = 1) }
        composeRule.onNodeWithText("Watermark").performScrollTo()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Watermark").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_pages_watermark.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPagesCrop() {
        show { viewer(ViewerMode.Pages, sixPages, selectedPage = 1) }
        composeRule.onNodeWithText("Crop").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Crop").performClick()
        composeRule.waitForIdle()
        // Trim a bit off three edges so the sketch shows what stays.
        for ((tag, amount) in listOf("crop-left" to 0.1f, "crop-top" to 0.2f, "crop-right" to 0.05f)) {
            composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(amount) }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_pages_crop.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPagesSplitEvery() {
        // With the last page selected there is nothing to split after it, so the dialog opens in
        // "every few pages" mode. Its text field never lets Compose go idle (see viewerPasswordAdd),
        // so no further taps are possible once it is open; the clock is driven by hand instead.
        show { viewer(ViewerMode.Pages, sixPages, selectedPage = 5) }
        composeRule.onNodeWithText("Split").performScrollTo()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Split").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_pages_split_every.png")
    }

    @Test
    fun viewerPagesMultiSelect() = capture("viewer_pages_multi_select") {
        viewer(ViewerMode.Pages, sixPages, selectedPages = setOf(0, 2, 3))
    }

    @Test
    fun viewerPagesDeleteSeveral() {
        show { viewer(ViewerMode.Pages, sixPages, selectedPages = setOf(0, 2, 3)) }
        composeRule.onNodeWithText("Delete").performScrollTo().performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_pages_delete_several.png")
    }

    @Test
    fun viewerPagesTip() = capture("viewer_pages_tip") { viewer(ViewerMode.Pages, sixPages, tip = R.string.tip_pages) }

    /** Page 3 held and dragged up over page 2, before it is let go. */
    @Test
    fun viewerPagesDragging() {
        show { viewer(ViewerMode.Pages, sixPages) }
        val page3 = composeRule.onNodeWithText("Page 3")
        page3.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_000)
        page3.performTouchInput {
            repeat(10) {
                moveBy(Offset(19.dp.toPx(), -20.dp.toPx()))
                advanceEventTime(16)
            }
        }
        composeRule.waitForIdle()
        captureRoot("viewer_pages_dragging")
    }

    @Test
    fun viewerMore() = capture("viewer_more") { viewer(ViewerMode.More) }

    @Test
    fun viewerReadSigned() = capture("viewer_read_signed") {
        viewer(ViewerMode.Read, sample.copy(signatures = listOf(signedByDana)))
    }

    @Test
    fun viewerReadSignedThenChanged() = capture("viewer_read_signed_changed") {
        viewer(ViewerMode.Read, sample.copy(signatures = listOf(signedByDana.copy(coversWholeFile = false))))
    }

    @Test
    fun viewerSignatureDetails() {
        val issued = signedByDana.copy(
            signer = "Sam Ortiz",
            issuer = "Example Trust CA",
            trust = SignatureReport.Trust.Trusted,
            timestamp = TimestampReport(Instant.parse("2026-10-03T16:20:05Z"), "Free TSA", valid = true),
        )
        show {
            viewer(
                ViewerMode.Read,
                sample.copy(signatures = listOf(signedByDana.copy(coversWholeFile = false), issued)),
                showSignatures = true,
            )
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_signature_details.png")
    }

    @Test
    fun viewerSignCertificate() {
        show { viewer(ViewerMode.Sign, showCertificate = true) }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_certificate.png")
    }

    @Test
    fun viewerSignCertificateImported() {
        val expires = Calendar.getInstance().apply { set(2028, Calendar.MARCH, 31) }.time
        show {
            viewer(
                ViewerMode.Sign,
                certificate = CertificateInfo("Dana Whitfield", "Example Trust CA", expires),
                timestampsOn = true,
                showCertificate = true,
            )
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_certificate_imported.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignCertificatePassword() {
        // Asked for the .p12 file's password once a certificate file is picked (see ViewerScreen).
        var asking by mutableStateOf(false)
        show {
            viewer(ViewerMode.Sign)
            if (asking) CertificatePasswordDialog(onDismiss = {}, onImport = {})
        }
        // The dialog's password field never lets Compose go idle, so it opens only once the clock
        // is driven by hand, as in viewerSignFinishEditable.
        composeRule.mainClock.autoAdvance = false
        asking = true
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_certificate_password.png")
    }

    /** A signature made with FreePDF's device certificate, checked and unchanged. */
    private val signedByDana = SignatureReport(
        fieldName = "Signature1",
        claimedSigner = "Dana Whitfield",
        claimedTime = Instant.parse("2026-10-03T15:04:00Z"),
        reason = "Signed with FreePDF",
        location = null,
        kind = SignatureReport.Kind.Pkcs7Detached,
        integrity = SignatureReport.Integrity.Intact,
        coversWholeFile = true,
        signer = "Dana Whitfield",
        issuer = "Dana Whitfield",
        signingTime = Instant.parse("2026-10-03T15:04:00Z"),
        trust = SignatureReport.Trust.SelfSigned,
    )
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerShare() {
        show { viewer(ViewerMode.Read) }
        composeRule.onNodeWithContentDescription("Share").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_share.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPasswordAdd() {
        show { viewer(ViewerMode.More) }
        // The dialog's fields never let Compose go idle, so drive the clock by hand and capture
        // without further input, like the Extract and Split dialogs.
        composeRule.onNodeWithText("Password").performScrollTo()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Password").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_password_add.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerShareSomePages() {
        show { viewer(ViewerMode.Read) }
        composeRule.onNodeWithContentDescription("Share").performClick()
        composeRule.onNodeWithText("Some pages only").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_share_some_pages.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerShareImages() {
        show { viewer(ViewerMode.Read) }
        composeRule.onNodeWithContentDescription("Share").performClick()
        // A short PDF starts with every page picked.
        composeRule.onNodeWithText("As images").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_share_images.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPasswordLocked() {
        show { viewer(ViewerMode.More, sample.copy(isProtected = true)) }
        composeRule.onNodeWithText("Password").performScrollTo()
        composeRule.onNodeWithText("Password").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_password_locked.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPasswordChange() {
        show { viewer(ViewerMode.More, sample.copy(isProtected = true)) }
        composeRule.onNodeWithText("Password").performScrollTo().performClick()
        composeRule.waitForIdle()
        // The new password's fields never let Compose go idle, as in viewerPasswordAdd.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Change password").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_password_change.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPasswordRemove() {
        show { viewer(ViewerMode.More, sample.copy(isProtected = true)) }
        composeRule.onNodeWithText("Password").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove password").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_password_remove.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerDocumentInfo() {
        fun at(day: Int, hour: Int, minute: Int) =
            Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, day, hour, minute, 0) }.toInstant()
        val info = DocumentInfo(
            title = "Service Agreement",
            author = "Dana Whitfield",
            creator = "Microsoft Word",
            producer = "Microsoft Word for Microsoft 365",
            created = at(28, 9, 30),
            modified = at(30, 16, 5),
            pageCount = 2,
            pageSize = PageSize(612f, 792f),
            pdfVersion = "1.7",
            fileSizeBytes = 84_000,
        )
        show {
            viewer(ViewerMode.More)
            DocumentInfoDialog("Service Agreement.pdf", info, onDismiss = {})
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_document_info.png")
    }

    /** Words of the sample's pages, found by the app's own PageText from the sample PDF. */
    private val sampleWords: List<List<PageWord>> by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf") ?: error("Missing sample/agreement.pdf")
        PDDocument.load(stream).use { document -> List(document.numberOfPages) { PageText.words(document, it) } }
    }

    /** A highlight with a comment, a pen drawing and a note, as Marks.list would report them. */
    private val sampleMarks: List<Mark> by lazy {
        val words = sampleWords[0]
        val from = wordIndex(0, "Northwind")
        val highlighted = words.subList(from, from + 2)
        val modified = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 3, 14, 20, 0) }.timeInMillis
        listOf(
            Mark(
                page = 0, index = 0, kind = Mark.Kind.Highlight,
                left = highlighted.first().left, top = highlighted.first().top,
                right = highlighted.last().right, bottom = highlighted.last().bottom,
                color = Annotator.Rgb.Yellow, width = 1f, comment = "Should this be Northwind Studio Ltd?",
                markedText = "Northwind Studio", author = "Dana", modified = modified,
            ),
            Mark(
                page = 0, index = 1, kind = Mark.Kind.Ink, left = 0.55f, top = 0.47f, right = 0.8f, bottom = 0.52f,
                color = Annotator.Rgb.Blue, width = 2f, comment = "", markedText = "", author = null, modified = modified,
            ),
            Mark(
                page = 1, index = 0, kind = Mark.Kind.Note, left = 0.7f, top = 0.2f, right = 0.733f, bottom = 0.225f,
                color = Annotator.Rgb.Yellow, width = 1f, comment = "Both parties sign here.", markedText = "",
                author = "Dana", modified = modified,
            ),
        )
    }

    /** [page] with the sample marks painted on, as PDFium would show them. */
    private fun withMarks(page: Bitmap, index: Int, marks: List<Mark>): Bitmap {
        val copy = page.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(copy)
        marks.filter { it.page == index }.forEach { mark ->
            val c = mark.color ?: return@forEach
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(if (mark.kind == Mark.Kind.Highlight) 110 else 255, (c.r * 255).toInt(), (c.g * 255).toInt(), (c.b * 255).toInt())
                style = if (mark.kind == Mark.Kind.Ink) android.graphics.Paint.Style.STROKE else android.graphics.Paint.Style.FILL
                strokeWidth = 4f
            }
            canvas.drawRect(mark.left * copy.width, mark.top * copy.height, mark.right * copy.width, mark.bottom * copy.height, paint)
        }
        return copy
    }

    private fun wordIndex(page: Int, text: String) = sampleWords[page].indexOfFirst { it.text == text }.also { check(it >= 0) { "No $text" } }

    /** The middle of a word, in pixels of the node tagged [tag], which covers the page. */
    private fun wordCentre(tag: String, page: Int, index: Int): Offset {
        val size = composeRule.onNodeWithTag(tag).fetchSemanticsNode().size
        val word = sampleWords[page][index]
        return Offset((word.left + word.right) / 2 * size.width, (word.top + word.bottom) / 2 * size.height)
    }

    // 3 Oct 2026, 15:00 on the test machine's clock, so Today and Yesterday group the same way everywhere.
    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 3, 15, 0, 0) }.timeInMillis

    private val sampleRecent = listOf(
        DocumentEntry("content://a", "Service Agreement.pdf", now - 1 * HOUR),
        DocumentEntry("content://c", "W-9 form.pdf", now - 5 * HOUR),
        DocumentEntry("content://d", "Bakery menu draft.pdf", now - 20 * HOUR),
        DocumentEntry("content://e", "Invoice 1042.pdf", now - 30 * HOUR),
        DocumentEntry("content://f", "Lease renewal 2027.pdf", now - 4 * 24 * HOUR),
        DocumentEntry("content://g", "Insurance claim.pdf", now - 9 * 24 * HOUR),
    )

    @Composable
    private fun files(
        open: List<DocumentEntry>,
        recent: List<DocumentEntry>,
        unsaved: Set<String> = emptySet(),
        closing: Boolean = false,
        stamps: Boolean = false,
    ) = shell(MainTab.Files) {
        FilesContent(open, recent, onOpenFile = {}, onOpen = {}, onClose = {}, onShare = {}, onForget = {}, modifier = it, unsaved = unsaved, now = now)
        if (closing) UnsavedCloseDialog(onSave = {}, onDiscard = {}, onCancel = {}, stamps = stamps)
    }

    /** A tab's screen inside the bottom tab bar, as the app shows it. */
    @Composable
    private fun shell(tab: MainTab, content: @Composable (Modifier) -> Unit) {
        AppShell(selected = tab, onSelect = {}) { _, modifier -> content(modifier) }
    }

    @Composable
    private fun viewer(
        mode: ViewerMode,
        state: ViewerState = sample,
        selectedPage: Int = 0,
        selectedPages: Set<Int> = setOf(selectedPage),
        tool: Int? = null,
        savedSignatures: Map<SignatureStore.Kind, Bitmap> = emptyMap(),
        signerName: String = "",
        certificate: CertificateInfo? = null,
        timestampsOn: Boolean = false,
        finishStep: SignedCopy.Step? = null,
        showCertificate: Boolean = false,
        showSignatures: Boolean = false,
        toolStyles: Map<AnnotateTool, ToolStyle> = emptyMap(),
        marks: List<Mark> = emptyList(),
        search: SearchResults = SearchResults(),
        searchQuery: String? = null,
        openDocuments: List<DocumentEntry> = emptyList(),
        unsavedDocuments: Set<String> = emptySet(),
        pageColors: PageColors = PageColors.Normal,
        reflow: Boolean = false,
        readingTextSize: Int = 18,
        readAloud: ReadAloudState = ReadAloudState(),
        tip: Int? = null,
        stamps: List<PlacedStamp> = emptyList(),
        selectedStamp: Long? = null,
        redactions: List<RedactBox> = emptyList(),
        confirmRedact: Boolean = false,
        onAction: (ViewerAction) -> Unit = {},
        pages: List<Bitmap> = samplePages,
        signField: Int? = null,
    ) {
        ViewerContent(
            state = state,
            onBack = {},
            loadPage = { index, width -> scaled(withMarks(pages[index % pages.size], index, marks), width) },
            // The agreement's words and sharp, zoomed-in renders; the form has neither.
            loadWords = { if (pages === samplePages) sampleWords[it % sampleWords.size] else emptyList() },
            // Every line of the sample's words can be edited, as in a PDF whose text is not scanned.
            findLine = { page, at ->
                val words = sampleWords[page % sampleWords.size]
                words.nearest(at, aspect = 792f / 612f, reach = 0.05f)?.let { i ->
                    val line = words.filter { it.line == words[i].line }
                    EditableLine(
                        line.joinToString(" ") { it.text },
                        DisplayRect(line.minOf { it.left }, line.minOf { it.top }, line.maxOf { it.right }, line.maxOf { it.bottom }),
                    )
                }
            },
            loadRegion = { index, fullWidth, region ->
                largePages[index]?.takeIf { pages === samplePages }?.let { cropped(it, fullWidth, region) }
            },
            initialMode = mode,
            initialSelectedPage = selectedPage,
            initialSelectedPages = selectedPages,
            initialTool = tool,
            initialSignField = signField,
            savedSignatures = savedSignatures,
            signerName = signerName,
            certificate = certificate,
            timestampsOn = timestampsOn,
            finishStep = finishStep,
            initialShowCertificate = showCertificate,
            initialShowSignatures = showSignatures,
            toolStyles = toolStyles,
            marks = marks,
            search = search,
            initialSearchQuery = searchQuery,
            openDocuments = openDocuments,
            unsavedDocuments = unsavedDocuments,
            currentUri = openDocuments.firstOrNull()?.uri,
            pageColors = pageColors,
            initialReflow = reflow,
            readAloud = readAloud,
            readingTextSize = readingTextSize,
            tip = tip,
            stamps = stamps,
            initialSelectedStamp = selectedStamp,
            initialRedactions = redactions,
            initialConfirmRedact = confirmRedact,
            onAction = onAction,
        )
    }

    private val letter = PageSize(612f, 792f)

    /** The agreement's company name and the first line of its next paragraph, marked for redaction. */
    private fun sampleRedactions(): List<RedactBox> {
        val from = wordIndex(0, "Northwind")
        return sampleWords[0].subList(from, from + 2).map { RedactBox(0, Rect(it.left, it.top, it.right, it.bottom)) }
    }

    /** On page 2 of the sample: a signature on the Provider line and a date on its Date line. */
    private fun placedStamps(): List<PlacedStamp> {
        val signature = SignatureInk.render(sampleSignature(), 0xFF1A3FA8.toInt(), 6f)
        val date = "Oct 3, 2026"
        return listOf(
            PlacedStamp(
                1L, 1, StampContent.Signature(SignatureStore.Kind.Signature, signature),
                StampGeometry.signatureBox(Offset(0.3f, 0.322f), signature.width, signature.height, SignatureStore.Kind.Signature, letter),
            ),
            PlacedStamp(
                2L, 1, StampContent.Text(date, "date"),
                StampGeometry.textBox(Offset(0.565f, 0.316f), listOf(date), letter) { it.length * 0.5f },
            ),
        )
    }

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        show(darkTheme, content)
        captureRoot(name)
    }

    private fun show(darkTheme: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent { FreePdfTheme(darkTheme = darkTheme, dynamicColor = false, content = content) }
    }

    private fun captureRoot(name: String) {
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** A made-up cursive signature, in pad pixels: a looping first stroke and an underline. */
    private fun sampleSignature(): List<List<Offset>> {
        val loops = (0..80).map { i ->
            val t = i / 80f
            Offset(120f + t * 560f + 30f * kotlin.math.cos(t * 28f), 300f - 90f * kotlin.math.sin(t * 14f) * (1.1f - t))
        }
        val underline = (0..20).map { i -> Offset(150f + i * 30f, 410f - i * 2f) }
        return listOf(loops, underline)
    }

    /** A made-up company logo: a blue rounded badge with a white check, on a see-through background. */
    private fun sampleLogo(): Bitmap {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF1A3FA8.toInt()
        canvas.drawRoundRect(20f, 20f, 380f, 380f, 80f, 80f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 40f
        paint.strokeCap = android.graphics.Paint.Cap.ROUND
        canvas.drawLines(floatArrayOf(110f, 210f, 175f, 275f, 175f, 275f, 295f, 130f), paint)
        return bitmap
    }

    private fun scaled(page: Bitmap, width: Int): Bitmap =
        Bitmap.createScaledBitmap(page, width, width * page.height / page.width, true)

    /** [region] of [page] as it would look with the whole page scaled to [fullWidth]. */
    private fun cropped(page: Bitmap, fullWidth: Int, region: IntRect): Bitmap {
        val ratio = page.width.toFloat() / fullWidth
        val source = android.graphics.Rect(
            (region.left * ratio).toInt(), (region.top * ratio).toInt(),
            (region.right * ratio).toInt(), (region.bottom * ratio).toInt(),
        )
        val out = Bitmap.createBitmap(region.width, region.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(page, source, android.graphics.Rect(0, 0, region.width, region.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    private fun loadSample(name: String): Bitmap {
        val stream = javaClass.classLoader!!.getResourceAsStream("sample/$name") ?: error("Missing sample/$name")
        return stream.use { BitmapFactory.decodeStream(it) } ?: error("Could not decode sample/$name")
    }
}
