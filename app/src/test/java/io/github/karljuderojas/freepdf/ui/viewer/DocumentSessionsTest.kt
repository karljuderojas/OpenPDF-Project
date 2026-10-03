package io.github.karljuderojas.freepdf.ui.viewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/** Runs under Robolectric so FreePdfApp initialises PdfBox-Android's resources for the edit. */
@RunWith(AndroidJUnit4::class)
class DocumentSessionsTest {

    private val dir = Files.createTempDirectory("document-sessions").toFile()
    private val root = File(dir, "edit")

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun sample(): InputStream = javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf")

    @Test
    fun attachingAgainReturnsTheSameSessionWithItsStateKept() {
        val sessions = DocumentSessions(root, maxOpen = 3)
        var copies = 0
        val first = sessions.attach("content://a") { copies++; sample() }
        first.position = ViewPosition(page = 1, offset = 40)
        first.session.edit { PageEditor.rotate(it, 0, 90) }

        val again = sessions.attach("content://a") { copies++; sample() }
        assertSame(first, again)
        assertEquals(1, copies)
        assertEquals(ViewPosition(1, 40), again.position)
        assertTrue(again.session.canUndo)
        assertEquals(1, sessions.size)
    }

    @Test
    fun closingRemovesTheSessionAndDeletesItsWorkingCopy() {
        val sessions = DocumentSessions(root)
        val entry = sessions.attach("content://a") { sample() }
        val workingFile = entry.session.workingFile
        assertTrue(workingFile.exists())

        sessions.close("content://a")
        assertNull(sessions.get("content://a"))
        assertFalse(workingFile.exists())
        assertFalse(workingFile.parentFile!!.exists())
        assertEquals(0, sessions.size)

        // Closing what is not open is harmless.
        sessions.close("content://a")
    }

    @Test
    fun theOldestSessionIsClosedBeyondTheCap() {
        val sessions = DocumentSessions(root, maxOpen = 2)
        val a = sessions.attach("content://a") { sample() }
        val b = sessions.attach("content://b") { sample() }
        // Coming back to A makes B the oldest.
        sessions.attach("content://a") { sample() }
        val c = sessions.attach("content://c") { sample() }

        assertEquals(2, sessions.size)
        assertNull(sessions.get("content://b"))
        assertFalse(b.session.workingFile.exists())
        assertSame(a, sessions.get("content://a"))
        assertSame(c, sessions.get("content://c"))
        assertTrue(a.session.workingFile.exists())
    }

    @Test
    fun unsavedListsTheDocumentsWithChanges() {
        val sessions = DocumentSessions(root)
        val a = sessions.attach("content://a") { sample() }
        val b = sessions.attach("content://b") { sample() }
        assertEquals(emptySet<String>(), sessions.unsaved.value)

        a.session.edit { PageEditor.rotate(it, 0, 90) }
        b.stamps = listOf(PlacedStamp(1L, 0, StampContent.Checkmark, StampBox(0.1f, 0.1f, 0.2f, 0.2f)))
        sessions.refresh()
        assertEquals(setOf("content://a", "content://b"), sessions.unsaved.value)

        a.session.undo()
        b.stamps = emptyList()
        sessions.closeAll()
        assertEquals(emptySet<String>(), sessions.unsaved.value)
        assertEquals(0, sessions.size)
    }

    @Test
    fun workingCopiesLeftByAKilledProcessAreDeletedOnStart() {
        val leftover = File(root, "stale/working.pdf").apply { parentFile!!.mkdirs(); writeText("old") }
        DocumentSessions(root)
        assertFalse(leftover.exists())
    }
}
