package io.github.karljuderojas.freepdf.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import io.github.karljuderojas.freepdf.pdf.annotate.Mark
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.text.PageText
import io.github.karljuderojas.freepdf.pdf.text.PageWord
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.ui.sign.SignatureInk
import io.github.karljuderojas.freepdf.ui.files.FilesContent
import io.github.karljuderojas.freepdf.ui.theme.FreePdfTheme
import io.github.karljuderojas.freepdf.ui.viewer.AnnotateTool
import io.github.karljuderojas.freepdf.ui.viewer.ToolStyle
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
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val samplePages = listOf(loadSample("page-1.png"), loadSample("page-2.png"))
    private val sample = ViewerState.Ready(List(samplePages.size) { PageSize(612f, 792f) })

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
        toolStyles: Map<AnnotateTool, ToolStyle> = emptyMap(),
        marks: List<Mark> = emptyList(),
    ) {
        ViewerContent(
            state = state,
            onBack = {},
            loadPage = { index, width -> scaled(withMarks(samplePages[index], index, marks), width) },
            loadWords = { sampleWords[it] },
            initialMode = mode,
            initialSelectedPage = selectedPage,
            initialTool = tool,
            savedSignatures = savedSignatures,
            signerName = signerName,
            toolStyles = toolStyles,
            marks = marks,
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
