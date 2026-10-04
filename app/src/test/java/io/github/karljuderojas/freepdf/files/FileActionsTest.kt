package io.github.karljuderojas.freepdf.files

import android.content.Context
import android.content.Intent
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
    fun missingOwnFilesAreGoneNotUnsupported() {
        val missing = Uri.fromFile(temp.root.resolve("missing.pdf"))
        assertEquals(Support.Gone, FileActions.renameSupport(context, missing))
        assertEquals(Support.Gone, FileActions.deleteSupport(context, missing))
        assertEquals(RenameResult.Gone, FileActions.rename(context, missing, "b.pdf"))
        assertEquals(DeleteResult.Gone, FileActions.delete(context, missing))
        assertEquals(Support.Supported, FileActions.deleteSupport(context, Uri.fromFile(temp.newFile("here.pdf"))))
    }

    // A document reached through a folder grant is covered by it; the system would refuse a grant of its own.
    @Test
    fun persistGrantNeedsNothingForADocumentUnderAFolderGrant() {
        val resolver = context.contentResolver
        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APDFs")
        resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val old = Uri.parse("$tree/document/primary%3APDFs%2Fa.pdf")
        val new = Uri.parse("$tree/document/primary%3APDFs%2Fb.pdf")
        assertTrue(FileActions.persistGrant(resolver, old, new))
        // The folder's grant is all the app holds, untouched; no grant was taken for the new URI.
        assertEquals(listOf(tree), resolver.persistedUriPermissions.map { it.uri })
    }

    @Test
    fun persistGrantMovesAPlainDocumentGrantToTheNewUri() {
        val resolver = context.contentResolver
        val old = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AA.pdf")
        val new = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AB.pdf")
        resolver.takePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertTrue(FileActions.persistGrant(resolver, old, new))
        assertEquals(listOf(new), resolver.persistedUriPermissions.map { it.uri })
        assertTrue(FileActions.persistGrant(resolver, new, new))
    }

    @Test
    fun unknownSchemesAreUnsupported() {
        val uri = Uri.parse("http://example.com/a.pdf")
        assertEquals(RenameResult.Unsupported, FileActions.rename(context, uri, "b.pdf"))
        assertEquals(DeleteResult.Unsupported, FileActions.delete(context, uri))
    }
}
