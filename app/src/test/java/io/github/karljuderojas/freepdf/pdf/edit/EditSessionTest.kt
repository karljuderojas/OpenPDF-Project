package io.github.karljuderojas.freepdf.pdf.edit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
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

    @Test
    fun redoPutsBackWhatUndoTookAway() {
        session.edit { PageEditor.insertBlank(it, 1) }
        session.edit { PageEditor.rotate(it, 0, 90) }
        session.undo()
        session.undo()
        assertTrue(session.canRedo)
        assertEquals(2, pageCount())

        session.redo()
        assertEquals(3, pageCount())
        assertEquals(0, PDDocument.load(session.workingFile).use { it.getPage(0).rotation })
        session.redo()
        assertEquals(90, PDDocument.load(session.workingFile).use { it.getPage(0).rotation })
        assertFalse(session.canRedo)
        assertTrue(session.canUndo)
    }

    @Test
    fun aNewEditDropsWhatCouldBeRedone() {
        session.edit { PageEditor.insertBlank(it, 1) }
        session.undo()
        session.edit { PageEditor.rotate(it, 0, 90) }
        assertFalse(session.canRedo)
        session.redo()
        assertEquals(2, pageCount())
    }

    @Test
    fun undoingAndRedoingBackToTheSavedCopyCountsAsSaved() {
        session.edit { PageEditor.insertBlank(it, 1) }
        session.writeTo(ByteArrayOutputStream())
        session.undo()
        assertTrue(session.hasUnsavedChanges)
        session.redo()
        assertFalse(session.hasUnsavedChanges)

        // A different edit after an undo is not the saved copy, even at the same undo depth.
        session.undo()
        session.edit { PageEditor.rotate(it, 0, 90) }
        assertTrue(session.hasUnsavedChanges)
    }

    @Test
    fun aLockedPdfOpensWithItsPasswordAndStaysLockedAfterEdits() {
        val locked = File(dir, "locked.pdf")
        PDDocument.load(session.workingFile).use { document ->
            val permissions = AccessPermission().apply { setCanPrint(false) }
            document.protect(StandardProtectionPolicy("owner-secret", "open sesame", permissions).apply { encryptionKeyLength = 128 })
            document.save(locked)
        }
        val lockedSession = EditSession(File(dir, "locked-session"), locked.inputStream())
        try {
            assertTrue(lockedSession.needsPassword())
            assertFalse(lockedSession.unlock("wrong"))
            assertTrue(lockedSession.needsPassword())
            assertTrue(lockedSession.unlock("open sesame"))
            assertFalse(lockedSession.needsPassword())

            lockedSession.edit { PageEditor.rotate(it, 0, 90) }

            // Still locked with the same password, the same permissions, and the edit applied.
            assertFalse(PdfDocuments.opens(lockedSession.workingFile, ""))
            PDDocument.load(lockedSession.workingFile, "open sesame").use {
                assertTrue(it.isEncrypted)
                assertEquals(90, it.getPage(0).rotation)
                assertFalse(it.currentAccessPermission.canPrint())
            }
        } finally {
            lockedSession.close()
        }
    }

    @Test
    fun anOwnerPasswordStaysTheOwnerPassword() {
        val locked = File(dir, "owner.pdf")
        PDDocument.load(session.workingFile).use { document ->
            document.protect(StandardProtectionPolicy("owner-secret", "user", AccessPermission()).apply { encryptionKeyLength = 128 })
            document.save(locked)
        }
        val lockedSession = EditSession(File(dir, "owner-session"), locked.inputStream())
        try {
            assertTrue(lockedSession.unlock("owner-secret"))
            lockedSession.edit { PageEditor.insertBlank(it, 1) }
            PDDocument.load(lockedSession.workingFile, "owner-secret").use {
                assertTrue(it.currentAccessPermission.isOwnerPermission)
                assertEquals(3, it.numberOfPages)
            }
        } finally {
            lockedSession.close()
        }
    }

    private fun pageCount() = PDDocument.load(session.workingFile).use { it.numberOfPages }
}
