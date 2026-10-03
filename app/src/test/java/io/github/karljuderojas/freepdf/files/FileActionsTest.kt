package io.github.karljuderojas.freepdf.files

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileActionsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun cleanNameKeepsOrRestoresThePdfExtension() {
        assertEquals("Lease.pdf", FileActions.cleanName("Lease"))
        assertEquals("Lease.pdf", FileActions.cleanName("  Lease.pdf "))
        assertEquals("Lease.PDF", FileActions.cleanName("Lease.PDF"))
    }

    @Test
    fun cleanNameRejectsEmptyAndPathNames() {
        assertNull(FileActions.cleanName("   "))
        assertNull(FileActions.cleanName("a/b"))
        assertNull(FileActions.cleanName("pdf"))
    }

    @Test
    fun editableNameDropsTheExtension() {
        assertEquals("W-9 form", FileActions.editableName("W-9 form.pdf"))
    }

    @Test
    fun ownFilesRenameAndDelete() {
        val file = temp.newFile("a.pdf")
        val renamed = FileActions.rename(context, Uri.fromFile(file), "b.pdf") as RenameResult.Renamed
        assertEquals("b.pdf", renamed.name)
        assertFalse(file.exists())
        assertEquals(DeleteResult.Deleted, FileActions.delete(context, renamed.uri))
        assertFalse(temp.root.resolve("b.pdf").exists())
    }

    @Test
    fun renameNeverOverwritesAnotherFile() {
        val a = temp.newFile("a.pdf")
        temp.newFile("b.pdf")
        assertEquals(RenameResult.Failed, FileActions.rename(context, Uri.fromFile(a), "b.pdf"))
        assertTrue(a.exists())
    }

    @Test
    fun unknownSchemesAreUnsupported() {
        val uri = Uri.parse("http://example.com/a.pdf")
        assertEquals(RenameResult.Unsupported, FileActions.rename(context, uri, "b.pdf"))
        assertEquals(DeleteResult.Unsupported, FileActions.delete(context, uri))
    }
}
