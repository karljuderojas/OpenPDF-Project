package io.github.karljuderojas.freepdf.pdf.links

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PageLinksTest {

    private fun sample(): PDDocument =
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    /** Saves and reopens, so the test reads what another app would. */
    private fun roundTrip(document: PDDocument): PDDocument {
        val bytes = ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        return PDDocument.load(bytes)
    }

    private val box = DisplayRect(0.1f, 0.2f, 0.5f, 0.3f)

    @Test
    fun aWebLinkComesBackWhereItWasPut() {
        sample().use { document ->
            PageLinks.add(document, 0, box, LinkTarget.Web("https://example.com/terms"))
            roundTrip(document).use { reopened ->
                val link = PageLinks.read(reopened).single()
                assertEquals(0, link.page)
                assertEquals(LinkTarget.Web("https://example.com/terms"), link.target)
                assertEquals(box.left, link.box.left, 0.001f)
                assertEquals(box.top, link.box.top, 0.001f)
                assertEquals(box.right, link.box.right, 0.001f)
                assertEquals(box.bottom, link.box.bottom, 0.001f)
            }
        }
    }

    @Test
    fun aPageLinkComesBackAsThePage() {
        sample().use { document ->
            PageEditor.insertBlank(document, 1)
            PageLinks.add(document, 0, box, LinkTarget.Page(1))
            roundTrip(document).use { reopened ->
                assertEquals(LinkTarget.Page(1), PageLinks.read(reopened).single().target)
            }
        }
    }

    @Test
    fun theBoxFollowsARotatedPage() {
        sample().use { document ->
            document.getPage(0).rotation = 90
            PageLinks.add(document, 0, box, LinkTarget.Web("https://example.com"))
            val link = PageLinks.read(document).single()
            assertEquals(box.left, link.box.left, 0.001f)
            assertEquals(box.bottom, link.box.bottom, 0.001f)
        }
    }

    @Test
    fun linksThatCouldOpenFilesOrScriptsAreLeftOut() {
        sample().use { document ->
            val page = document.getPage(0)
            page.annotations = listOf("javascript:alert(1)", "file:///data/data/secret", "intent://x#Intent;end", "https://ok.example").map { address ->
                PDAnnotationLink().apply {
                    rectangle = PDRectangle(10f, 10f, 50f, 20f)
                    action = PDActionURI().apply { uri = address }
                }
            }
            assertEquals(listOf(LinkTarget.Web("https://ok.example")), PageLinks.read(document).map { it.target })
        }
    }

    @Test
    fun refusesAnAddressItCouldNotOpenOrAMissingPage() {
        sample().use { document ->
            assertThrows(IllegalArgumentException::class.java) { PageLinks.add(document, 0, box, LinkTarget.Web("javascript:alert(1)")) }
            assertThrows(IllegalArgumentException::class.java) { PageLinks.add(document, 0, box, LinkTarget.Page(7)) }
        }
    }

    @Test
    fun typedAddressesAreTidied() {
        assertEquals("https://example.com", PageLinks.normaliseAddress(" example.com "))
        assertEquals("http://example.com/a?b=1", PageLinks.normaliseAddress("http://example.com/a?b=1"))
        assertEquals("mailto:me@example.com", PageLinks.normaliseAddress("me@example.com"))
        assertEquals("tel:+15551234", PageLinks.normaliseAddress("tel:+15551234"))
        assertNull(PageLinks.normaliseAddress(""))
        assertNull(PageLinks.normaliseAddress("two words.com"))
        assertNull(PageLinks.normaliseAddress("javascript:alert(1)"))
        assertNull(PageLinks.normaliseAddress("file:///etc/passwd"))
        assertTrue(PageLinks.isOpenable("MAILTO:me@example.com"))
    }
}
