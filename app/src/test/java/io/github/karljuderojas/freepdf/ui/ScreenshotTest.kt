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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.ui.home.HomeScreen
import io.github.karljuderojas.freepdf.ui.theme.FreePdfTheme
import io.github.karljuderojas.freepdf.ui.viewer.ViewerContent
import io.github.karljuderojas.freepdf.ui.viewer.ViewerMode
import io.github.karljuderojas.freepdf.ui.viewer.ViewerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
    fun home() = capture("home") { HomeScreen(onOpenPdf = {}) }

    @Test
    fun viewerRead() = capture("viewer_read") { viewer(ViewerMode.Read) }

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
        composeRule.onNodeWithTag("annotation-layer-0").performTouchInput { click(Offset(860f, 240f)) }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_annotate_note.png")
    }

    @Test
    fun viewerSign() = capture("viewer_sign") { viewer(ViewerMode.Sign) }

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

    @Composable
    private fun viewer(mode: ViewerMode, state: ViewerState = sample, selectedPage: Int = 0, tool: Int? = null) {
        ViewerContent(
            state = state,
            onBack = {},
            loadPage = { index, width -> scaled(samplePages[index], width) },
            initialMode = mode,
            initialSelectedPage = selectedPage,
            initialTool = tool,
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

    private fun scaled(page: Bitmap, width: Int): Bitmap =
        Bitmap.createScaledBitmap(page, width, width * page.height / page.width, true)

    private fun loadSample(name: String): Bitmap {
        val stream = javaClass.classLoader!!.getResourceAsStream("sample/$name") ?: error("Missing sample/$name")
        return stream.use { BitmapFactory.decodeStream(it) } ?: error("Could not decode sample/$name")
    }
}
