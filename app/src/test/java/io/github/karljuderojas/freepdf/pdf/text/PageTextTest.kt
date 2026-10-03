package io.github.karljuderojas.freepdf.pdf.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs under Robolectric so FreePdfApp initialises PdfBox-Android's resources. */
@RunWith(AndroidJUnit4::class)
class PageTextTest {

    private fun sample() = PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    @Test
    fun findsTheWordsOfTheSampleInReadingOrder() {
        val words = sample().use { PageText.words(it, 0) }
        val text = words.map { it.text }
        assertEquals(listOf("Service", "Agreement"), text.take(2))
        val services = text.indexOf("Services")
        assertTrue("Services is on the page", services > 0)
        assertEquals("1.", text[services - 1])
        assertEquals("Northwind", text[services + 1])
    }

    @Test
    fun boxesSitWhereTheTextIsDrawn() {
        val words = sample().use { PageText.words(it, 0) }
        // The title is drawn at x = 72pt, baseline 712pt, 22pt Helvetica Bold, on a 612 x 792 page.
        val title = words.first()
        assertEquals(72f / 612f, title.left, 0.002f)
        assertTrue("title box is above its baseline", title.top < (792f - 712f) / 792f)
        assertTrue("title box covers the baseline", title.bottom > (792f - 712f) / 792f)
        words.forEach {
            assertTrue("${it.text} lies on the page", it.left >= 0f && it.right <= 1f && it.top >= 0f && it.bottom <= 1f)
            assertTrue("${it.text} has a size", it.right > it.left && it.bottom > it.top)
        }
    }

    @Test
    fun wordsOnOneLineShareItsNumberAndLaterLinesCountUp() {
        val words = sample().use { PageText.words(it, 0) }
        assertEquals(words[0].line, words[1].line)
        val services = words.first { it.text == "Services" }
        assertTrue(services.line > words[0].line)
        assertEquals(words.map { it.line }.sorted(), words.map { it.line })
    }

    @Test
    fun textDrawnUprightOnARotatedPageIsBoxedWhereItIsShown() {
        val words = sample().use { document ->
            // A scan-like page: shown turned a quarter clockwise, with text drawn so it reads upright.
            val page = document.getPage(0)
            page.rotation = 90
            PageEditor.addText(document, 0, "Upright", displayToPdf(0.10f, 0.50f, 90, page.cropBox.let {
                PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY)
            }), fontSize = 14f)
            PageText.words(document, 0)
        }
        val word = words.first { it.text == "Upright" }
        // Its left edge is where the tap was, a tenth of the way across the displayed page.
        assertEquals(0.10f, word.left, 0.005f)
        assertTrue("box sits on its baseline", word.top < 0.50f && word.bottom > 0.50f)
        assertTrue("reads left to right", word.right - word.left > word.bottom - word.top)
    }

    @Test
    fun rotatedPagesTurnTheBoxesWithThePage() {
        val upright = sample().use { PageText.words(it, 0) }.first()
        val turned = sample().use { document ->
            document.getPage(0).rotation = 90
            PageText.words(document, 0)
        }.first()
        // Turned a quarter clockwise, the title's left edge (x = 72pt) becomes its top edge.
        assertEquals(upright.left, turned.top, 0.002f)
        assertEquals(1f - upright.bottom, turned.left, 0.002f)
    }
}
