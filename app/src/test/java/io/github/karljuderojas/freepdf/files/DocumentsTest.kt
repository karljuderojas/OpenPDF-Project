package io.github.karljuderojas.freepdf.files

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test", Context.MODE_PRIVATE)
    private var time = 1_000L
    private fun documents() = Documents(prefs, clock = { time++ })

    @Test
    fun reopeningMovesADocumentToTheTopWithoutDuplicates() {
        val documents = documents()
        documents.opened("content://a", "A.pdf", remember = true)
        documents.opened("content://b", "B.pdf", remember = true)
        documents.opened("content://a", "A.pdf", remember = true)
        assertEquals(listOf("A.pdf", "B.pdf"), documents.recent.value.map { it.name })
        assertEquals(listOf("A.pdf", "B.pdf"), documents.open.value.map { it.name })
    }

    @Test
    fun historySurvivesARestartButOpenDocumentsDoNot() {
        documents().opened("content://a", "A.pdf", remember = true)
        val restarted = documents()
        assertEquals(listOf("A.pdf"), restarted.recent.value.map { it.name })
        assertEquals(emptyList<DocumentEntry>(), restarted.open.value)
    }

    @Test
    fun filesWithoutLastingAccessStayOutOfHistory() {
        val documents = documents()
        documents.opened("content://mail/1", "Attachment.pdf", remember = false)
        assertEquals(emptyList<DocumentEntry>(), documents.recent.value)
        assertEquals(listOf("Attachment.pdf"), documents.open.value.map { it.name })
    }

    @Test
    fun closeAndForgetRemoveFromTheirLists() {
        val documents = documents()
        documents.opened("content://a", "A.pdf", remember = true)
        documents.close("content://a")
        documents.forget("content://a")
        assertEquals(emptyList<DocumentEntry>(), documents.open.value)
        assertEquals(emptyList<DocumentEntry>(), documents.recent.value)
    }

    @Test
    fun clearingHistoryKeepsOpenDocuments() {
        val documents = documents()
        documents.opened("content://a", "A.pdf", remember = true)
        documents.clearHistory()
        assertEquals(emptyList<DocumentEntry>(), documents.recent.value)
        assertEquals(emptyList<DocumentEntry>(), documents().recent.value)
        assertEquals(listOf("A.pdf"), documents.open.value.map { it.name })
    }

    @Test
    fun atMostEightDocumentsStayOpenDroppingTheOldest() {
        val documents = documents()
        (1..10).forEach { documents.opened("content://$it", "$it.pdf", remember = false) }
        assertEquals((10 downTo 3).map { "$it.pdf" }, documents.open.value.map { it.name })
    }

    @Test
    fun aDocumentWithUnsavedChangesIsNeverDroppedOffTheEnd() {
        val closed = ArrayList<String>()
        val documents = Documents(prefs, { time++ }, onClosed = closed::add, hasUnsavedChanges = { it == "content://1" || it == "content://3" })
        (1..9).forEach { documents.opened("content://$it", "$it.pdf", remember = false) }
        // The oldest saved one (2) goes instead of the oldest (1), which has changes.
        assertEquals(listOf("content://2"), closed)
        assertEquals(listOf(9, 8, 7, 6, 5, 4, 3, 1).map { "$it.pdf" }, documents.open.value.map { it.name })

        documents.opened("content://10", "10.pdf", remember = false)
        assertEquals(listOf("content://2", "content://4"), closed)
        assertEquals(listOf(10, 9, 8, 7, 6, 5, 3, 1).map { "$it.pdf" }, documents.open.value.map { it.name })
    }

    @Test
    fun theOpenListGrowsPastTheCapWhileEveryDocumentHasChanges() {
        val closed = ArrayList<String>()
        val documents = Documents(prefs, { time++ }, onClosed = closed::add, hasUnsavedChanges = { true })
        (1..10).forEach { documents.opened("content://$it", "$it.pdf", remember = false) }
        assertEquals(emptyList<String>(), closed)
        assertEquals(10, documents.open.value.size)

        // Once something is saved, the next open lets the oldest saved one go.
        val saved = Documents(prefs, { time++ }, onClosed = closed::add, hasUnsavedChanges = { it != "content://2" })
        (1..10).forEach { saved.opened("content://$it", "$it.pdf", remember = false) }
        assertEquals(listOf("content://2"), closed)
        assertEquals(9, saved.open.value.size)
    }

    @Test
    fun leavingTheOpenListIsReportedHoweverItHappens() {
        val closed = ArrayList<String>()
        val documents = Documents(prefs, { time++ }, onClosed = closed::add)
        (1..9).forEach { documents.opened("content://$it", "$it.pdf", remember = false) }
        assertEquals(listOf("content://1"), closed)

        documents.close("content://5")
        documents.close("content://5")
        assertEquals(listOf("content://1", "content://5"), closed)

        // Reopening a document that is already open is not a close.
        documents.opened("content://9", "9.pdf", remember = false)
        assertEquals(2, closed.size)

        documents.closeAll()
        assertEquals(9, closed.size)
        assertEquals(emptyList<DocumentEntry>(), documents.open.value)
    }

    @Test
    fun onlyReopenableUrisAreLasting() {
        val filesDir = temp.newFolder("files")
        val own = File(filesDir, "kept/copy.pdf")
        assertTrue(Documents.lasting(Uri.parse("content://docs/1"), filesDir) { true })
        assertFalse(Documents.lasting(Uri.parse("content://mail/1"), filesDir) { false })
        assertTrue(Documents.lasting(Uri.fromFile(own), filesDir) { false })
        assertFalse(Documents.lasting(Uri.fromFile(filesDir), filesDir) { true })
        assertFalse(Documents.lasting(Uri.fromFile(File(temp.root, "cache/shared/x.pdf")), filesDir) { true })
        assertFalse(Documents.lasting(Uri.fromFile(File(temp.root, "files2/x.pdf")), filesDir) { true })
        assertFalse(Documents.lasting(Uri.parse("https://example.com/x.pdf"), filesDir) { true })
    }

    @Test
    fun closeAllEmptiesOpenDocumentsButKeepsHistory() {
        val documents = documents()
        documents.opened("content://a", "A.pdf", remember = true)
        documents.opened("content://b", "B.pdf", remember = true)
        documents.closeAll()
        assertEquals(emptyList<DocumentEntry>(), documents.open.value)
        assertEquals(listOf("B.pdf", "A.pdf"), documents.recent.value.map { it.name })
    }

    @Test
    fun renamingKeepsTheEntryInPlaceInBothLists() {
        val documents = documents()
        documents.opened("content://a", "A.pdf", remember = true)
        documents.opened("content://b", "B.pdf", remember = true)
        documents.renamed("content://a", "content://a2", "Renamed.pdf")
        assertEquals(listOf("B.pdf", "Renamed.pdf"), documents.recent.value.map { it.name })
        assertEquals(listOf("content://b", "content://a2"), documents.open.value.map { it.uri })
        assertEquals(listOf("B.pdf", "Renamed.pdf"), documentsAfterRestart().recent.value.map { it.name })
    }

    @Test
    fun forgettingAFolderDropsItsPdfsFromHistoryButNotFromOpen() {
        val documents = documents()
        val tree = "content://com.android.externalstorage.documents/tree/primary%3APDFs"
        documents.opened("$tree/document/primary%3APDFs%2Fa.pdf", "a.pdf", remember = true)
        documents.opened("content://com.android.externalstorage.documents/document/primary%3Ab.pdf", "b.pdf", remember = true)
        documents.opened("$tree/document/primary%3APDFs%2Fc.pdf", "c.pdf", remember = true)
        // A different folder whose tree URI merely starts the same way is not under it.
        documents.opened("${tree}2/document/primary%3APDFs2%2Fd.pdf", "d.pdf", remember = true)
        documents.forgetUnder(tree)
        assertEquals(listOf("d.pdf", "b.pdf"), documents.recent.value.map { it.name })
        assertEquals(listOf("d.pdf", "b.pdf"), documentsAfterRestart().recent.value.map { it.name })
        assertEquals(listOf("d.pdf", "c.pdf", "b.pdf", "a.pdf"), documents.open.value.map { it.name })
    }

    @Test
    fun aGrantCoversItselfAndTheDocumentsOfItsTree() {
        val tree = Uri.parse("content://docs/tree/primary%3APDFs")
        assertTrue(Documents.covers(tree, tree))
        assertTrue(Documents.covers(tree, Uri.parse("$tree/document/primary%3APDFs%2Fa.pdf")))
        assertFalse(Documents.covers(tree, Uri.parse("content://docs/document/primary%3APDFs%2Fa.pdf")))
        assertFalse(Documents.covers(tree, Uri.parse("${tree}2/document/primary%3APDFs2%2Fa.pdf")))
        val document = Uri.parse("content://docs/document/primary%3Aa.pdf")
        assertTrue(Documents.covers(document, document))
        assertFalse(Documents.covers(document, Uri.parse("content://docs/document/primary%3Ab.pdf")))
    }

    private fun documentsAfterRestart() = documents()
}
