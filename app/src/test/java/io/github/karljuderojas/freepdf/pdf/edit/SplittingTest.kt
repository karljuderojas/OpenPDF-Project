package io.github.karljuderojas.freepdf.pdf.edit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Extract and Split. Runs under Robolectric so FreePdfApp initialises PdfBox-Android. */
@RunWith(AndroidJUnit4::class)
class SplittingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun sample(): PDDocument =
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    /** The 2-page sample with three blank A4 pages inserted: agreement 1, A4, A4, agreement 2, A4. */
    private fun fivePages(): File = temp.newFile("five.pdf").also { file ->
        sample().use { document ->
            PageEditor.insertBlank(document, 1, PDRectangle.A4)
            PageEditor.insertBlank(document, 1, PDRectangle.A4)
            PageEditor.insertBlank(document, 4, PDRectangle.A4)
            document.save(file)
        }
    }

    @Test
    fun keepOnlyKeepsTheNamedPagesInDocumentOrder() {
        sample().use { document ->
            val secondPageText = text(document, 2)
            PageEditor.keepOnly(document, listOf(1))
            assertEquals(1, document.numberOfPages)
            assertEquals(secondPageText, text(document, 1))
        }
        PDDocument.load(fivePages()).use { document ->
            PageEditor.keepOnly(document, listOf(3, 0, 4))
            assertEquals(3, document.numberOfPages)
            assertTrue(isAgreement(document, 0))
            assertTrue(isAgreement(document, 1))
            assertEquals(PDRectangle.A4.height, document.getPage(2).mediaBox.height, 0.01f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun keepOnlyRefusesPagesThatDoNotExist() {
        sample().use { PageEditor.keepOnly(it, listOf(2)) }
    }

    @Test
    fun plansSplits() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), Splitting.everyN(5, 2))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3, 4)), Splitting.intoTwo(5, 1))
        assertEquals(listOf(listOf(0), listOf(1)), Splitting.everyN(2, 1))
        assertEquals(listOf(listOf(0, 1)), Splitting.everyN(2, 5))
    }

    @Test
    fun writtenPartsHoldTheirPagesAndLeaveTheSourceAlone() {
        val source = fivePages()
        val sizes = Splitting.everyN(5, 2).map { part ->
            val bytes = ByteArrayOutputStream().also { Splitting.writePart(source, part, it) }.toByteArray()
            PDDocument.load(bytes).use { it.numberOfPages }
        }
        assertEquals(listOf(2, 2, 1), sizes)

        val extracted = ByteArrayOutputStream().also { Splitting.writePart(source, listOf(0, 3), it) }.toByteArray()
        PDDocument.load(extracted).use { document ->
            assertEquals(2, document.numberOfPages)
            assertTrue(isAgreement(document, 0) && isAgreement(document, 1))
        }
        PDDocument.load(source).use { assertEquals(5, it.numberOfPages) }
    }

    @Test
    fun partsOfALockedPdfStayLockedWithItsPassword() {
        val five = fivePages()
        val source = File(five.parentFile, "locked.pdf")
        PDDocument.load(five).use { document ->
            document.protect(StandardProtectionPolicy("owner-secret", "open sesame", AccessPermission()).apply { encryptionKeyLength = 128 })
            document.save(source)
        }
        val bytes = ByteArrayOutputStream().also { Splitting.writePart(source, listOf(1, 2), it, "open sesame") }.toByteArray()
        assertTrue(runCatching { PDDocument.load(bytes).close() }.isFailure)
        PDDocument.load(bytes, "open sesame").use {
            assertTrue(it.isEncrypted)
            assertEquals(2, it.numberOfPages)
        }
    }

    @Test
    fun namesTheNewFiles() {
        assertEquals("Lease (part 2).pdf", Splitting.partName("Lease.pdf", 2))
        assertEquals("Lease (part 1).pdf", Splitting.partName("Lease.PDF", 1))
        assertEquals("Lease (pages 2-4).pdf", Splitting.extractName("Lease.pdf", listOf(1, 2, 3)))
        assertEquals("Lease (pages 1-3, 5).pdf", Splitting.extractName("Lease", listOf(0, 1, 2, 4)))
        assertEquals("Lease (page 2).pdf", Splitting.extractName("Lease.pdf", listOf(1)))
    }

    /** The sample's pages are letter size with text; the inserted blanks are A4 and empty. */
    private fun isAgreement(document: PDDocument, index: Int): Boolean =
        document.getPage(index).mediaBox.height == PDRectangle.LETTER.height && text(document, index + 1).isNotBlank()

    private fun text(document: PDDocument, page: Int): String =
        PDFTextStripper().apply { startPage = page; endPage = page }.getText(document)
}
