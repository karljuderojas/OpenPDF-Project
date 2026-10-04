package io.github.karljuderojas.freepdf.files

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** A stand-in for a documents provider: one folder, "PDFs", with a few children. */
class FakeDocumentsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "io.github.karljuderojas.freepdf.test.documents"
        var folderName: String? = "PDFs"
        var throwOnChildren = false
        /** Each child as (document id, display name, mime type, last modified, size); nulls are left out of the row. */
        var children: List<Array<Any?>> = emptyList()
    }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = MatrixCursor(columns)
        if (uri.pathSegments.lastOrNull() == "children") {
            if (throwOnChildren) throw IllegalStateException("gone")
            children.forEach { child ->
                val byColumn = mapOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID to child[0],
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME to child[1],
                    DocumentsContract.Document.COLUMN_MIME_TYPE to child[2],
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED to child[3],
                    DocumentsContract.Document.COLUMN_SIZE to child[4],
                )
                cursor.addRow(columns.map { byColumn[it] })
            }
        } else {
            val name = folderName
            if (name != null) cursor.addRow(columns.map { if (it == DocumentsContract.Document.COLUMN_DISPLAY_NAME) name else null })
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}

@RunWith(AndroidJUnit4::class)
class FolderReaderTest {

    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private val tree = DocumentsContract.buildTreeDocumentUri(FakeDocumentsProvider.AUTHORITY, "primary:PDFs")

    private fun provider(children: List<Array<Any?>>, folderName: String? = "PDFs", throwOnChildren: Boolean = false) {
        FakeDocumentsProvider.children = children
        FakeDocumentsProvider.folderName = folderName
        FakeDocumentsProvider.throwOnChildren = throwOnChildren
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, FakeDocumentsProvider.AUTHORITY)
    }

    @Test
    fun listsThePdfsOfTheFolderAsTreeDocumentsSkippingTheRest() {
        provider(
            listOf(
                arrayOf<Any?>("primary:PDFs/a.pdf", "a.pdf", "application/pdf", 1_000L, 10L),
                arrayOf<Any?>("primary:PDFs/notes.txt", "notes.txt", "text/plain", 1_000L, 10L),
                arrayOf<Any?>("primary:PDFs/Sub", "Sub", DocumentsContract.Document.MIME_TYPE_DIR, 1_000L, 0L),
                arrayOf<Any?>("primary:PDFs/Scan.PDF", "Scan.PDF", "application/octet-stream", null, null),
            ),
        )
        val listing = FolderReader.read(resolver, tree)
        assertNotNull(listing)
        assertEquals("PDFs", listing!!.name)
        assertEquals(listOf("a.pdf", "Scan.PDF"), listing.files.map { it.name })
        assertEquals(DocumentsContract.buildDocumentUriUsingTree(tree, "primary:PDFs/a.pdf").toString(), listing.files[0].uri)
        assertEquals(1_000L, listing.files[0].modified)
        assertEquals(10L, listing.files[0].size)
        // No date or size from the provider: zero, not a crash.
        assertEquals(0L, listing.files[1].modified)
        assertEquals(0L, listing.files[1].size)
        // The listed document is one the folder grant covers.
        assertTrue(Documents.covers(tree, Uri.parse(listing.files[0].uri)))
    }

    @Test
    fun anEmptyFolderIsAnEmptyListingNotUnreadable() {
        provider(emptyList())
        assertEquals(emptyList<FolderFile>(), FolderReader.read(resolver, tree)!!.files)
    }

    @Test
    fun theFolderIsNamedAfterItsIdWhenTheProviderDoesNotSay() {
        provider(emptyList(), folderName = null)
        assertEquals("PDFs", FolderReader.read(resolver, tree)!!.name)
    }

    @Test
    fun aProviderThatFailsOrIsGoneMeansUnreadable() {
        provider(emptyList(), throwOnChildren = true)
        assertNull(FolderReader.read(resolver, tree))
        assertNull(FolderReader.read(resolver, DocumentsContract.buildTreeDocumentUri("no.such.provider", "primary:PDFs")))
    }
}
