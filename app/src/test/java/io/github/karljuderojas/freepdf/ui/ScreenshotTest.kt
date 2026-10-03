package io.github.karljuderojas.freepdf.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.IntRect
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.pdf.render.OutlineItem
import io.github.karljuderojas.freepdf.pdf.render.PageBox
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.ui.sign.SignatureInk
import io.github.karljuderojas.freepdf.settings.ThemeChoice
import io.github.karljuderojas.freepdf.ui.sign.TypedSignature
import io.github.karljuderojas.freepdf.ui.files.FilesContent
import io.github.karljuderojas.freepdf.ui.home.HomeContent
import io.github.karljuderojas.freepdf.ui.settings.SettingsContent
import io.github.karljuderojas.freepdf.ui.tools.ToolsContent
import io.github.karljuderojas.freepdf.ui.theme.FreePdfTheme
import io.github.karljuderojas.freepdf.ui.viewer.GoToPageDialog
import io.github.karljuderojas.freepdf.ui.viewer.SearchResults
import io.github.karljuderojas.freepdf.ui.viewer.TextMatch
import io.github.karljuderojas.freepdf.ui.viewer.PlacedStamp
import io.github.karljuderojas.freepdf.ui.viewer.StampContent
import io.github.karljuderojas.freepdf.ui.viewer.StampGeometry
import io.github.karljuderojas.freepdf.ui.viewer.ViewerAction
import io.github.karljuderojas.freepdf.ui.viewer.ViewerContent
import io.github.karljuderojas.freepdf.ui.viewer.ViewerMode
import io.github.karljuderojas.freepdf.ui.viewer.ViewerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

private const val HOUR = 60 * 60 * 1000L

/**
 * Renders each screen to a PNG. CI runs `recordRoborazziDebug` on every pull request and posts
 * the images on the PR for review. Add a test here for every new or changed screen.
 *
 * Pages come from a real sample PDF (resources/sample/agreement.pdf), pre-rendered to PNG by
 * scripts/make_sample_pdf.py, because PDFium's native library does not load under Robolectric.
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

    @Test
    fun home() = capture("home") { shell(MainTab.Home) { HomeContent(sampleRecent, {}, {}, {}, {}, {}, {}, modifier = it) } }

    @Test
    fun homeEmpty() = capture("home_empty") { shell(MainTab.Home) { HomeContent(emptyList(), {}, {}, {}, {}, {}, {}, modifier = it) } }

    @Test
    fun tools() = capture("tools") { shell(MainTab.Tools) { ToolsContent(onToolPicked = {}, modifier = it) } }

    @Test
    fun toolsSearch() = capture("tools_search_tick") {
        shell(MainTab.Tools) { ToolsContent(onToolPicked = {}, modifier = it, initialQuery = "tick") }
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
                modifier = it,
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
        // Mid-drag across the "1. Services" paragraph, so the preview shows.
        composeRule.onNodeWithTag("annotation-layer-0").performTouchInput {
            down(Offset(118f, 258f))
            listOf(200f, 420f, 640f, 836f).forEachIndexed { i, x -> moveTo(Offset(x, 262f + i * 20f)) }
        }
        captureRoot("viewer_annotate_highlight")
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
    fun viewerSign() = capture("viewer_sign") { viewer(ViewerMode.Sign) }

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

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignPadTyped() {
        show { viewer(ViewerMode.Sign, signerName = "Dana Whitfield") }
        composeRule.onNodeWithText("Signature").performClick()
        composeRule.onNodeWithTag("pad-tab-type").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_pad_typed.png")
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

    @Test
    fun viewerPages() = capture("viewer_pages") {
        viewer(ViewerMode.Pages, sample.copy(canUndo = true, hasUnsavedChanges = true), selectedPage = 1)
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

    @Test
    fun viewerMore() = capture("viewer_more") { viewer(ViewerMode.More) }

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
    private fun files(open: List<DocumentEntry>, recent: List<DocumentEntry>) = shell(MainTab.Files) {
        FilesContent(open, recent, onOpenFile = {}, onOpen = {}, onClose = {}, onShare = {}, onForget = {}, modifier = it, now = now)
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
        tool: Int? = null,
        savedSignatures: Map<SignatureStore.Kind, Bitmap> = emptyMap(),
        signerName: String = "",
        search: SearchResults = SearchResults(),
        searchQuery: String? = null,
        stamps: List<PlacedStamp> = emptyList(),
        selectedStamp: Long? = null,
        onAction: (ViewerAction) -> Unit = {},
    ) {
        ViewerContent(
            state = state,
            onBack = {},
            loadPage = { index, width -> scaled(samplePages[index], width) },
            loadRegion = { index, fullWidth, region -> largePages[index]?.let { cropped(it, fullWidth, region) } },
            initialMode = mode,
            initialSelectedPage = selectedPage,
            initialTool = tool,
            savedSignatures = savedSignatures,
            signerName = signerName,
            search = search,
            initialSearchQuery = searchQuery,
            stamps = stamps,
            initialSelectedStamp = selectedStamp,
            onAction = onAction,
        )
    }

    private val letter = PageSize(612f, 792f)

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

    private fun capture(name: String, content: @Composable () -> Unit) {
        show(content)
        captureRoot(name)
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { FreePdfTheme(dynamicColor = false, content = content) }
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
