package io.github.karljuderojas.freepdf.pdf.info

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Document info, read with PdfBox under Robolectric so FreePdfApp sets up PdfBox-Android. */
@RunWith(AndroidJUnit4::class)
class DocumentInfoTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun readsTheSampleAgreement() {
        val file = sampleCopy()
        val info = DocumentInfo.read(file)
        assertEquals(2, info.pageCount)
        assertEquals("Letter, 8.5 × 11 in", DocumentInfo.sizeLabel(info.pageSize!!, metric = true, locale = Locale.US))
        assertFalse(info.encrypted)
        assertFalse(info.hasFormFields)
        assertEquals(0, info.signatureCount)
        assertEquals("1.7", info.pdfVersion)
        assertEquals(file.length(), info.fileSizeBytes)
    }

    @Test
    fun readsBackMetadataSetOnACopy() {
        val file = temp.newFile("with-metadata.pdf")
        val created = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 28, 9, 30, 0)
        }
        PDDocument.load(sampleCopy()).use { document ->
            document.documentInformation.apply {
                title = "Service Agreement"
                author = "Dana Whitfield"
                subject = "  "
                keywords = ""
                creator = "Microsoft Word"
                producer = "macOS Quartz PDFContext"
                creationDate = created
            }
            document.save(file)
        }

        val info = DocumentInfo.read(file)
        assertEquals("Service Agreement", info.title)
        assertEquals("Dana Whitfield", info.author)
        assertNull(info.subject)
        assertNull(info.keywords)
        assertEquals("Microsoft Word", info.creator)
        assertEquals("macOS Quartz PDFContext", info.producer)
        assertEquals(Instant.parse("2026-09-28T09:30:00Z"), info.created)
        assertNull(info.modified)
    }

    @Test
    fun namesStandardPaperEitherWayUp() {
        assertEquals("Letter, 8.5 × 11 in", label(612f, 792f))
        assertEquals("Letter, 11 × 8.5 in", label(792f, 612f))
        // As most writers round it.
        assertEquals("A4, 210 × 297 mm", label(595f, 842f))
        assertEquals("A4, 210 × 297 mm", label(PDRectangle.A4.width, PDRectangle.A4.height))
        assertEquals("Legal, 8.5 × 14 in", label(612f, 1008f))
    }

    @Test
    fun otherSizesUseTheLocalUnit() {
        assertEquals("5 × 7 in", label(360f, 504f, metric = false))
        assertEquals("127 × 178 mm", label(360f, 504f, metric = true))
    }

    @Test
    fun differentPagesAreMixedAndRotatedPagesCountAsShown() {
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.LETTER))
            // Stored landscape, shown portrait: the same as the first page on screen.
            document.addPage(PDPage(PDRectangle(792f, 612f)).apply { rotation = 90 })
            assertEquals(PageSize(612f, 792f), DocumentInfo.read(document, 0).pageSize)

            document.addPage(PDPage(PDRectangle.A4))
            assertNull(DocumentInfo.read(document, 0).pageSize)
        }
    }

    @Test
    fun aRotatedLetterPageIsLandscape() {
        val page = PDPage(PDRectangle.LETTER).apply { rotation = 270 }
        assertEquals("Letter, 11 × 8.5 in", DocumentInfo.sizeLabel(DocumentInfo.shownSize(page), metric = false, locale = Locale.US))
    }

    private fun label(width: Float, height: Float, metric: Boolean = false) =
        DocumentInfo.sizeLabel(PageSize(width, height), metric, Locale.US)

    private fun sampleCopy(): File {
        val file = temp.newFile("agreement.pdf")
        val input = javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf") ?: error("Missing sample/agreement.pdf")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return file
    }
}
