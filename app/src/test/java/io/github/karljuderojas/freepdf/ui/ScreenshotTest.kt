package io.github.karljuderojas.freepdf.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.form.FormFiller
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.ui.sign.SignatureInk
import io.github.karljuderojas.freepdf.ui.files.FilesContent
import io.github.karljuderojas.freepdf.ui.theme.FreePdfTheme
import io.github.karljuderojas.freepdf.ui.viewer.ViewerContent
import io.github.karljuderojas.freepdf.ui.viewer.ViewerMode
import io.github.karljuderojas.freepdf.ui.viewer.ViewerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

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
    private val sample = ViewerState.Ready(List(samplePages.size) { PageSize(612f, 792f) })

    // The sign-up form (sample/form.pdf), with its fields read by the app's own code.
    private val formPages = listOf(loadSample("form-page.png"))
    private val form by lazy {
        ViewerState.Ready(
            listOf(PageSize(612f, 792f)),
            formFields = javaClass.classLoader!!.getResourceAsStream("sample/form.pdf").use { PDDocument.load(it).use(FormFiller::fields) },
        )
    }

    @Test
    fun filesEmpty() = capture("files_empty") { files(open = emptyList(), recent = emptyList()) }

    @Test
    fun files() {
        val hour = 60 * 60 * 1000L
        val agreement = DocumentEntry("content://a", "Service Agreement.pdf", now - 1 * hour)
        capture("files") {
            files(
                open = listOf(agreement, DocumentEntry("content://b", "Lease renewal 2027.pdf", now - 2 * hour)),
                recent = listOf(
                    agreement,
                    DocumentEntry("content://c", "W-9 form.pdf", now - 5 * hour),
                    DocumentEntry("content://d", "Bakery menu draft.pdf", now - 20 * hour),
                    DocumentEntry("content://e", "Invoice 1042.pdf", now - 30 * hour),
                    DocumentEntry("content://f", "Lease renewal 2027.pdf", now - 4 * 24 * hour),
                    DocumentEntry("content://g", "Insurance claim.pdf", now - 9 * 24 * hour),
                ),
            )
        }
    }

    @Test
    fun viewerRead() = capture("viewer_read") { viewer(ViewerMode.Read) }

    @Test
    fun viewerReadZoomed() {
        show { viewer(ViewerMode.Read) }
        // Pinch out to about 2.5x while dragging both fingers down, which pans to the page's title.
        composeRule.onNodeWithTag("page-list").performTouchInput {
            pinch(Offset(440f, 250f), Offset(340f, 1300f), Offset(640f, 450f), Offset(740f, 1900f))
        }
        captureRoot("viewer_read_zoomed")
    }

    @Test
    fun viewerUnsaved() = capture("viewer_read_unsaved") {
        viewer(ViewerMode.Read, sample.copy(canUndo = true, hasUnsavedChanges = true))
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
        composeRule.onNodeWithTag("form-field-name-0").performClick()
        // The dialog focuses its text field, whose blinking cursor never lets Compose go idle,
        // so drive the clock by hand from here.
        composeRule.mainClock.autoAdvance = false
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

    @Composable
    private fun files(open: List<DocumentEntry>, recent: List<DocumentEntry>) {
        FilesContent(open, recent, onOpenFile = {}, onOpen = {}, onClose = {}, onShare = {}, onForget = {}, now = now)
    }

    @Composable
    private fun viewer(
        mode: ViewerMode,
        state: ViewerState = sample,
        selectedPage: Int = 0,
        tool: Int? = null,
        savedSignatures: Map<SignatureStore.Kind, Bitmap> = emptyMap(),
        signerName: String = "",
        pages: List<Bitmap> = samplePages,
    ) {
        ViewerContent(
            state = state,
            onBack = {},
            loadPage = { index, width -> scaled(pages[index], width) },
            initialMode = mode,
            initialSelectedPage = selectedPage,
            initialTool = tool,
            savedSignatures = savedSignatures,
            signerName = signerName,
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

    private fun loadSample(name: String): Bitmap {
        val stream = javaClass.classLoader!!.getResourceAsStream("sample/$name") ?: error("Missing sample/$name")
        return stream.use { BitmapFactory.decodeStream(it) } ?: error("Could not decode sample/$name")
    }
}
