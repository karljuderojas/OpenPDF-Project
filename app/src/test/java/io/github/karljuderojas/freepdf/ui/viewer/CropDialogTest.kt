package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.karljuderojas.freepdf.pdf.edit.CropMargins
import io.github.karljuderojas.freepdf.pdf.edit.MarginFinder
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/** The Trim margins path of [CropPagesDialog]: what it finds, hands on, drops and keeps. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var trimmed: Map<Int, CropMargins>? = null
    private var cropped: Pair<Set<Int>, CropMargins?>? = null

    /** A white page with a black block that leaves [margin] of the page clear on every side, or each side as given. */
    private fun page(margin: Float, left: Float = margin, top: Float = margin, right: Float = margin, bottom: Float = margin): Bitmap {
        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(300 * left, 400 * top, 300 * (1 - right), 400 * (1 - bottom), Paint().apply { color = Color.BLACK })
        }
        return bitmap
    }

    @Composable
    private fun TrimDialog(selectedPages: List<Int>, loadPage: suspend (Int, Int) -> Bitmap?) {
        CropPagesDialog(
            pageCount = 3,
            selectedPages = selectedPages,
            pageAspect = 0.75f,
            previewPage = selectedPages.first(),
            onDismiss = {},
            onCrop = { pages, margins -> cropped = pages to margins },
            onTrim = { trimmed = it },
            loadPage = loadPage,
        )
    }

    private fun waitForNote(text: String) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertMargin(expected: Float, actual: Float) = assertEquals(expected, actual, 0.01f)

    @Test
    fun trimHandsTheFoundMarginsToOnTrim() {
        composeRule.setContent { TrimDialog(listOf(1)) { _, _ -> page(0.1f) } }
        composeRule.onNodeWithText("Crop").assertIsNotEnabled()
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("Found white margins")
        composeRule.onNodeWithTag("crop-trim").assertIsEnabled()
        composeRule.onNodeWithText("Crop").performClick()

        val found = trimmed!!
        assertEquals(setOf(1), found.keys)
        assertMargin(0.1f - MarginFinder.PADDING, found.getValue(1).left)
        assertMargin(0.1f - MarginFinder.PADDING, found.getValue(1).bottom)
        assertNull(cropped)
    }

    @Test
    fun aSliderTouchDropsTheFoundMarginsButKeepsTheOtherEdges() {
        composeRule.setContent { TrimDialog(listOf(1)) { _, _ -> page(0.1f) } }
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("Found white margins")
        composeRule.onNodeWithTag("crop-left").performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crop-trim-note").assertDoesNotExist()
        composeRule.onNodeWithText("Crop").performClick()

        assertNull(trimmed)
        val (pages, margins) = cropped!!
        assertEquals(setOf(1), pages)
        assertMargin(0.3f, margins!!.left)
        assertMargin(0.1f - MarginFinder.PADDING, margins.top)
        assertMargin(0.1f - MarginFinder.PADDING, margins.right)
    }

    @Test
    fun changingThePagesWhileLookingDropsThatScan() {
        val gate = CompletableDeferred<Unit>()
        composeRule.setContent { TrimDialog(listOf(1)) { _, _ -> gate.await(); page(0.1f) } }
        composeRule.onNodeWithTag("crop-trim").performClick()
        composeRule.onNodeWithTag("crop-trim").assertIsNotEnabled()
        composeRule.onNodeWithTag("crop-trim-note").assertTextContains("Looking", substring = true)

        // All pages chosen while the one page is still being looked at: that answer is not wanted.
        composeRule.onNodeWithTag("crop-all").performClick()
        composeRule.onNodeWithTag("crop-trim").assertIsEnabled()
        composeRule.onNodeWithTag("crop-trim-note").assertDoesNotExist()
        gate.complete(Unit)
        Thread.sleep(300)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crop-trim-note").assertDoesNotExist()
        composeRule.onNodeWithText("Crop").assertIsNotEnabled()

        // Asked again, it looks at the pages chosen now.
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("Found white margins on 3 of 3 pages")
        composeRule.onNodeWithText("Crop").performClick()
        assertEquals(setOf(0, 1, 2), trimmed!!.keys)
    }

    @Test
    fun theFoundMarginsSurviveARecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { TrimDialog(listOf(1)) { _, _ -> page(0.1f) } }
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("Found white margins")

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crop-trim-note").assertTextContains("Found white margins", substring = true)
        composeRule.onNodeWithText("Crop").performClick()
        assertEquals(setOf(1), trimmed!!.keys)
        assertMargin(0.1f - MarginFinder.PADDING, trimmed!!.getValue(1).top)
    }

    @Test
    fun aScanUnderWayStartsAgainAfterARecreation() {
        val gate = CompletableDeferred<Unit>()
        val loads = AtomicInteger()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { TrimDialog(listOf(1)) { _, _ -> loads.incrementAndGet(); gate.await(); page(0.1f) } }
        composeRule.onNodeWithTag("crop-trim").performClick()
        composeRule.waitUntil(5_000) { loads.get() == 1 }

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crop-trim").assertIsNotEnabled()
        composeRule.onNodeWithTag("crop-trim-note").assertTextContains("Looking", substring = true)
        composeRule.waitUntil(5_000) { loads.get() == 2 }
        gate.complete(Unit)
        waitForNote("Found white margins")
        composeRule.onNodeWithTag("crop-trim").assertIsEnabled()
    }

    @Test
    fun aPageThatCannotBeDrawnIsLeftOutRatherThanFailing() {
        composeRule.setContent {
            TrimDialog(listOf(0, 1)) { index, _ -> if (index == 0) throw IllegalStateException("Page 1 is damaged") else page(0.1f) }
        }
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("Found white margins on 1 of 2 pages")
        composeRule.onNodeWithTag("crop-trim").assertIsEnabled()
        composeRule.onNodeWithText("Crop").performClick()
        // Only the page that could be drawn is cut: nobody knows what is on the other.
        assertEquals(setOf(1), trimmed!!.keys)
        assertMargin(0.1f - MarginFinder.PADDING, trimmed!!.getValue(1).left)
    }

    @Test
    fun aBlankPageIsCutLikeTheOthersSoThePagesKeepOneSize() {
        val blank = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.WHITE) }
        composeRule.setContent { TrimDialog(listOf(0, 1)) { index, _ -> if (index == 0) blank else page(0.1f) } }
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("Found white margins on 1 of 2 pages")
        composeRule.onNodeWithText("Crop").performClick()
        assertEquals(setOf(0, 1), trimmed!!.keys)
        assertMargin(0.1f - MarginFinder.PADDING, trimmed!!.getValue(0).left)
    }

    @Test
    fun aPagePrintedToItsEdgesStopsTheOthersBeingCut() {
        // Page 2 is black to every edge. Before, it dropped out of the scan and was cut by page 1's margins.
        composeRule.setContent { TrimDialog(listOf(0, 1)) { index, _ -> if (index == 0) page(0.1f) else page(0f) } }
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("printed to its edge")
        composeRule.onNodeWithText("Crop").assertIsNotEnabled()
        assertNull(trimmed)
    }

    @Test
    fun aSinglePagePrintedToItsEdgesHasNothingToTrim() {
        composeRule.setContent { TrimDialog(listOf(1)) { _, _ -> page(0f) } }
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("No white margins")
        composeRule.onNodeWithText("Crop").assertIsNotEnabled()
    }

    @Test
    fun severalPagesShareTheSmallestMarginFound() {
        composeRule.setContent { TrimDialog(listOf(0, 1, 2)) { index, _ -> page(if (index == 1) 0.2f else 0.1f) } }
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("Found white margins on 3 of 3 pages")
        composeRule.onNodeWithText("Crop").performClick()

        val found = trimmed!!
        assertEquals(setOf(0, 1, 2), found.keys)
        assertEquals(1, found.values.toSet().size)
        assertMargin(0.1f - MarginFinder.PADDING, found.getValue(1).left)
    }

    @Test
    fun pagesPrintedToOppositeEdgesLeaveNothingToTrimThemBy() {
        // One page printed up to its right edge, the other up to its left: no edge is clear on both.
        composeRule.setContent {
            TrimDialog(listOf(0, 1)) { index, _ -> if (index == 0) page(0f, left = 0.1f) else page(0f, right = 0.1f) }
        }
        composeRule.onNodeWithTag("crop-trim").performClick()
        waitForNote("printed to its edge")
        composeRule.onNodeWithText("Crop").assertIsNotEnabled()
    }
}
