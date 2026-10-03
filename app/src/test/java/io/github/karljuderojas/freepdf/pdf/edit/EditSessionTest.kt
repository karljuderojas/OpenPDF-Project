package io.github.karljuderojas.freepdf.pdf.edit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** Runs under Robolectric so FreePdfApp initialises PdfBox-Android's resources. */
@RunWith(AndroidJUnit4::class)
class EditSessionTest {

    private val dir = Files.createTempDirectory("edit-session").toFile()
    private val session = EditSession(File(dir, "session"), javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    @After
    fun cleanUp() {
        session.close()
        dir.deleteRecursively()
    }

    @Test
    fun editsApplyToTheWorkingCopyAndUndo() {
        assertEquals(2, pageCount())
        assertFalse(session.hasUnsavedChanges)

        session.edit { PageEditor.insertBlank(it, 1) }
        session.edit { PageEditor.rotate(it, 0, 90) }
        assertEquals(3, pageCount())
        assertEquals(90, PDDocument.load(session.workingFile).use { it.getPage(0).rotation })
        assertTrue(session.hasUnsavedChanges)

        session.undo()
        assertEquals(0, PDDocument.load(session.workingFile).use { it.getPage(0).rotation })
        session.undo()
        assertEquals(2, pageCount())
        assertFalse(session.canUndo)
        assertFalse(session.hasUnsavedChanges)
    }

    @Test
    fun savingClearsUnsavedChangesUntilTheNextEdit() {
        session.edit { PageEditor.delete(it, 1) }
        val saved = ByteArrayOutputStream().also { session.writeTo(it) }.toByteArray()
        assertEquals(1, PDDocument.load(saved).use { it.numberOfPages })
        assertFalse(session.hasUnsavedChanges)

        session.undo()
        assertTrue(session.hasUnsavedChanges)
    }

    @Test
    fun aFailedEditLeavesTheCopyAsItWas() {
        runCatching { session.edit { PageEditor.delete(it, 7) } }
        assertEquals(2, pageCount())
        assertFalse(session.canUndo)
    }

    private fun pageCount() = PDDocument.load(session.workingFile).use { it.numberOfPages }
}
