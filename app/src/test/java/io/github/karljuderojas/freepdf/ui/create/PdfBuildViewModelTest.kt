package io.github.karljuderojas.freepdf.ui.create

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.karljuderojas.freepdf.pdf.create.PageFit
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfBuildViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun viewModel() = PdfBuildViewModel(ApplicationProvider.getApplicationContext<Application>()).apply {
        // Run the build inline, so the test sees each state without waiting on a thread.
        io = Dispatchers.Unconfined
    }

    private fun picture() = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    @Test
    fun theResultWaitsInTheViewModelForTheScreenThatComesBackAfterARotation() {
        val target = File(temp.root, "out.pdf")
        val viewModel = viewModel()
        val seen = mutableListOf<BuildState>()
        viewModel.build(2, PageFit.Picture, Uri.fromFile(target)) { seen += viewModel.state.value; picture() }
        shadowOf(Looper.getMainLooper()).idle()

        // Progress was reported while the pages were made...
        assertEquals(listOf(BuildState.Making(0, 2), BuildState.Making(1, 2)), seen)
        // ...and the finished PDF is there for whichever screen asks, as if after a rotation.
        val recreated = viewModel.state.value
        assertEquals(BuildState.Done(Uri.fromFile(target)), recreated)
        PDDocument.load(target).use { assertEquals(2, it.numberOfPages) }

        viewModel.finished()
        assertEquals(BuildState.Idle, viewModel.state.value)
    }

    @Test
    fun aPictureThatCannotBeLoadedLeavesAFailureUntilTheUserMovesOn() {
        val target = File(temp.root, "out.pdf")
        val viewModel = viewModel()
        viewModel.build(2, PageFit.A4, Uri.fromFile(target)) { i -> if (i == 1) error("gone") else picture() }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(BuildState.Failed, viewModel.state.value)
        assertFalse("no half-written PDF", target.exists())
        // Opening nothing changes nothing; clearing the failure does.
        viewModel.finished()
        assertEquals(BuildState.Failed, viewModel.state.value)
        viewModel.clearFailure()
        assertEquals(BuildState.Idle, viewModel.state.value)
    }

    @Test
    fun aSecondSaveIsIgnoredWhileOneIsRunning() {
        val target = File(temp.root, "out.pdf")
        val viewModel = viewModel()
        var second = false
        viewModel.build(1, PageFit.A4, Uri.fromFile(target)) {
            viewModel.build(1, PageFit.A4, Uri.fromFile(File(temp.root, "other.pdf"))) { second = true; picture() }
            picture()
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(second)
        assertTrue(viewModel.state.value is BuildState.Done)
    }
}
