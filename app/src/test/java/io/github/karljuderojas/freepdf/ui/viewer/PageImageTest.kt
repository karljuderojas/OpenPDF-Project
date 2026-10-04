package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The page on screen is what tells the pending pen strokes they have been drawn into the
 * picture (see [PendingInk]): it says so only once a new picture is really shown, with the
 * revision it was asked for, and never for a render that gave nothing.
 */
@RunWith(AndroidJUnit4::class)
class PageImageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aPageReportsItselfShownOnceItsNewPictureIsOnScreen() {
        // One gate per render, opened by the test when that render "finishes".
        val renders = List(3) { CompletableDeferred<Bitmap?>() }
        var asked = 0
        val shown = ArrayList<Pair<Int, Int>>()
        var revision by mutableIntStateOf(0)
        composeRule.setContent {
            PageImage(
                index = 2, size = PageSize(612f, 792f), revision = revision, widthPx = 60,
                loadPage = { _, _ -> renders[asked++].await() },
                onRendered = { page, at -> shown += page to at },
            )
        }
        composeRule.waitForIdle()
        // Nothing to report while the first render is still running.
        assertEquals(emptyList<Pair<Int, Int>>(), shown)

        renders[0].complete(Bitmap.createBitmap(60, 78, Bitmap.Config.ARGB_8888))
        composeRule.waitUntil(5_000) { shown.size == 1 }
        assertEquals(listOf(2 to 0), shown)

        // An edit lands: the old picture stays, and nothing is said, until the new one arrives.
        revision = 1
        composeRule.waitForIdle()
        assertEquals(listOf(2 to 0), shown)
        renders[1].complete(Bitmap.createBitmap(60, 78, Bitmap.Config.ARGB_8888))
        composeRule.waitUntil(5_000) { shown.size == 2 }
        assertEquals(listOf(2 to 0, 2 to 1), shown)

        // A render that gives no picture (a page too big for the heap) reports nothing.
        revision = 2
        composeRule.waitForIdle()
        renders[2].complete(null)
        composeRule.waitForIdle()
        assertEquals(listOf(2 to 0, 2 to 1), shown)
    }
}
