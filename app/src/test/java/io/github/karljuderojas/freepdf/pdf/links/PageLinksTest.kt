package io.github.karljuderojas.freepdf.pdf.links

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.edit.CropMargins
import io.github.karljuderojas.freepdf.pdf.edit.PageCrop
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
    fun aLinkCanBeRemoved() {
        sample().use { document ->
            PageLinks.add(document, 0, box, LinkTarget.Web("https://example.com/a"))
            PageLinks.add(document, 0, DisplayRect(0.1f, 0.5f, 0.5f, 0.6f), LinkTarget.Web("https://example.com/b"))
            val first = PageLinks.read(document).first { it.target == LinkTarget.Web("https://example.com/a") }
            PageLinks.remove(document, first.page, first.index)
            roundTrip(document).use { reopened ->
                assertEquals(listOf(LinkTarget.Web("https://example.com/b")), PageLinks.read(reopened).map { it.target })
            }
            assertThrows(IllegalArgumentException::class.java) { PageLinks.remove(document, 0, 7) }
        }
    }

    @Test
    fun aLinkCanBePointedSomewhereElse() {
        sample().use { document ->
            PageEditor.insertBlank(document, 1)
            PageLinks.add(document, 0, box, LinkTarget.Web("https://exmple.com"))
            val link = PageLinks.read(document).single()
            PageLinks.update(document, link.page, link.index, LinkTarget.Page(1))
            roundTrip(document).use { reopened ->
                val changed = PageLinks.read(reopened).single()
                assertEquals(LinkTarget.Page(1), changed.target)
                assertEquals(link.index, changed.index)
                assertEquals(box.left, changed.box.left, 0.001f)
                assertEquals(box.bottom, changed.box.bottom, 0.001f)
            }
            assertThrows(IllegalArgumentException::class.java) {
                PageLinks.update(document, 0, link.index, LinkTarget.Web("javascript:alert(1)"))
            }
        }
    }

    @Test
    fun aLinkInACroppedAwayMarginIsLeftOutAndOneOnTheEdgeIsCutToThePage() {
        sample().use { document ->
            // The first sits a fifth of the way down the page; the second crosses the new top edge.
            PageLinks.add(document, 0, box, LinkTarget.Web("https://example.com/header"))
            PageLinks.add(document, 0, DisplayRect(0.1f, 0.4f, 0.5f, 0.6f), LinkTarget.Web("https://example.com/edge"))
            PageCrop.crop(document, listOf(0), CropMargins(top = 0.45f))
            val link = PageLinks.read(document).single()
            assertEquals(LinkTarget.Web("https://example.com/edge"), link.target)
            assertEquals(0f, link.box.top, 0.001f)
            assertEquals((0.6f - 0.45f) / 0.55f, link.box.bottom, 0.001f)
        }
    }

    @Test
    fun oneLinkPdfBoxCannotReadDoesNotHideTheOthers() {
        sample().use { document ->
            val broken = PDAnnotationLink().apply {
                rectangle = PDRectangle(10f, 10f, 50f, 20f)
                // A GoTo whose destination is a number, which PdfBox refuses to read.
                cosObject.setItem(
                    COSName.A,
                    COSDictionary().apply {
                        setItem(COSName.S, COSName.getPDFName("GoTo"))
                        setInt(COSName.D, 5)
                    },
                )
            }
            val good = PDAnnotationLink().apply {
                rectangle = PDRectangle(10f, 40f, 50f, 20f)
                action = PDActionURI().apply { uri = "https://ok.example" }
            }
            document.getPage(0).annotations = listOf(broken, good)
            val links = PageLinks.read(document)
            assertEquals(listOf(LinkTarget.Web("https://ok.example")), links.map { it.target })
            assertEquals(1, links.single().index)
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
