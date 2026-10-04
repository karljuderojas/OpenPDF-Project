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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("folder-test", Context.MODE_PRIVATE)
    private val released = ArrayList<String>()
    private fun store() = FolderStore(prefs, onReleased = released::add)
    private val pdfs = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APDFs")
    private val scans = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AScans")

    @Test
    fun choosingKeepsTheFolderWithAWritableGrantAcrossRestarts() {
        val store = store()
        store.choose(context, pdfs)
        assertEquals(pdfs.toString(), store.folder.value)
        assertTrue(store.canWrite(context))
        assertEquals(pdfs.toString(), store().folder.value)
        assertEquals(emptyList<String>(), released)
    }

    @Test
    fun changingTheFolderGivesTheOldGrantBackAndSaysSo() {
        val store = store()
        store.choose(context, pdfs)
        store.choose(context, scans)
        assertEquals(scans.toString(), store.folder.value)
        assertEquals(listOf(scans), context.contentResolver.persistedUriPermissions.map { it.uri })
        assertEquals(listOf(pdfs.toString()), released)

        // Choosing the same folder again is not a change.
        store.choose(context, scans)
        assertEquals(listOf(pdfs.toString()), released)
    }

    @Test
    fun forgettingGivesTheGrantBackAndSaysSo() {
        val store = store()
        store.choose(context, pdfs)
        store.forget(context)
        assertNull(store.folder.value)
        assertNull(store().folder.value)
        assertFalse(store.canWrite(context))
        assertTrue(context.contentResolver.persistedUriPermissions.none { it.uri == pdfs })
        assertEquals(listOf(pdfs.toString()), released)
    }

    @Test
    fun aReadOnlyGrantCannotWrite() {
        val store = store()
        prefs.edit().putString("folder", pdfs.toString()).commit()
        context.contentResolver.takePersistableUriPermission(pdfs, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertFalse(store().canWrite(context))
        assertFalse(store.canWrite(context))
    }

    @Test
    fun aTreeDocumentHasThePickerUriAsItsAlias() {
        val child = Uri.parse("$pdfs/document/primary%3APDFs%2Fa.pdf")
        assertEquals(
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3APDFs%2Fa.pdf"),
            documentAlias(child),
        )
        assertNull(documentAlias(pdfs))
        assertNull(documentAlias(Uri.parse("content://com.android.externalstorage.documents/document/primary%3Aa.pdf")))
        assertNull(documentAlias(Uri.parse("file:///data/user/0/app/files/a.pdf")))
    }
}
