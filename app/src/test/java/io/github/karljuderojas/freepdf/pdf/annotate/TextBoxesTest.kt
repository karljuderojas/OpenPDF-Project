package io.github.karljuderojas.freepdf.pdf.annotate

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Text boxes as FreeText annotations. Runs under Robolectric so FreePdfApp initialises
 * PdfBox-Android's resources (the bundled Liberation Sans font needs them).
 */
@RunWith(AndroidJUnit4::class)
class TextBoxesTest {

    private fun sample(): PDDocument =
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    private fun PDDocument.freeTexts(page: Int = 0) =
        getPage(page).annotations.filter { it.subtype == PDAnnotationMarkup.SUB_TYPE_FREETEXT }

    @Test
    fun addsAFreeTextBoxSizedToItsText() {
        sample().use { document ->
            val box = TextBoxes.add(document, 0, PdfPoint(72f, 700f), "Please review\nsection 4", Annotator.Rgb.Red, fontSize = 14f, author = "Dana")

            assertEquals(PDAnnotationMarkup.SUB_TYPE_FREETEXT, box.subtype)
            assertEquals("Please review\nsection 4", box.contents)
            assertEquals("Dana", box.titlePopup)
            assertTrue(box.defaultAppearance, box.defaultAppearance.contains("14.00 Tf"))

            val rect = box.rectangle
            assertEquals(72f, rect.lowerLeftX, 0.01f)
            assertEquals(700f, rect.upperRightY, 0.01f)
            // Two lines of 14pt text: wider than a few characters, about two line heights tall.
            assertTrue("width ${rect.width}", rect.width in 60f..200f)
            assertTrue("height ${rect.height}", rect.height in 30f..50f)
            val mediaBox = document.getPage(0).mediaBox
            assertTrue(rect.upperRightX <= mediaBox.upperRightX && rect.lowerLeftY >= mediaBox.lowerLeftY)

            val appearance = box.normalAppearanceStream
            assertNotNull(appearance)
            assertTrue(appearance.contentStream.toByteArray().isNotEmpty())
            assertEquals(rect.width, appearance.getBBox().width, 0.01f)
            assertTrue(appearance.resources.fontNames.iterator().hasNext())

            val style = TextBoxes.styleOf(box)!!
            assertEquals(14f, style.fontSize, 0.01f)
            assertEquals(Annotator.Rgb.Red.r, style.color.r, 0.01f)
        }
    }

    @Test
    fun longTextWrapsBeforeThePageEdge() {
        sample().use { document ->
            val text = "This note is far too long to fit on one line of the page, so it has to wrap " +
                "onto several lines instead of running off the right-hand edge where nobody can read it. " +
                "Please check the dates in section two, the payment terms in section four and the " +
                "notice period in section nine before anyone signs, then send the marked copy back to Dana."
            val box = TextBoxes.add(document, 0, PdfPoint(72f, 700f), text, Annotator.Rgb.Blue, fontSize = 12f)

            val rect = box.rectangle
            val page = document.getPage(0).cropBox
            assertTrue("right ${rect.upperRightX}", rect.upperRightX <= page.upperRightX)
            // Several lines of 12pt text, 1.2 line spacing: at least three lines tall.
            assertTrue("height ${rect.height}", rect.height >= 3 * 12f * 1.2f)

            val content = String(box.normalAppearanceStream.contentStream.toByteArray(), Charsets.ISO_8859_1)
            val shown = Regex("\\) Tj").findAll(content).count()
            assertTrue("lines shown: $shown", shown >= 3)
            assertEquals(shown - 1, Regex("\\bT\\*").findAll(content).count())
            // Every word is still there, in order.
            val words = Regex("\\((.*?)\\) Tj").findAll(content).joinToString(" ") { it.groupValues[1] }
            assertEquals(text, words)
            assertEquals(text, box.contents)
        }
    }

    @Test
    fun aWordWiderThanTheBoxIsBrokenBetweenLetters() {
        sample().use { document ->
            // Only 20pt of room before the edge, so the box falls back to its narrowest width and
            // the word is split rather than left to overflow.
            val box = TextBoxes.add(document, 0, PdfPoint(592f, 700f), "Unbreakablewordhere", Annotator.Rgb.Blue, fontSize = 12f)
            val content = String(box.normalAppearanceStream.contentStream.toByteArray(), Charsets.ISO_8859_1)
            assertTrue(content, Regex("\\) Tj").findAll(content).count() >= 2)
            assertTrue("width ${box.rectangle.width}", box.rectangle.width <= 4 * 12f + 2 * 4f + 0.01f)
        }
    }

    @Test
    fun editWrapsToo() {
        sample().use { document ->
            val box = TextBoxes.add(document, 0, PdfPoint(72f, 700f), "Short", Annotator.Rgb.Red)
            TextBoxes.edit(document, box, "A line of text ".repeat(12).trim(), Annotator.Rgb.Red, 12f)
            assertTrue(box.rectangle.upperRightX <= document.getPage(0).cropBox.upperRightX)
            val content = String(box.normalAppearanceStream.contentStream.toByteArray(), Charsets.ISO_8859_1)
            assertTrue(content, Regex("\\) Tj").findAll(content).count() >= 2)
        }
    }

    @Test
    fun survivesSaveAndReload() {
        val bytes = sample().use { document ->
            TextBoxes.add(document, 0, PdfPoint(100f, 500f), "Kept after saving", Annotator.Rgb.Blue)
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        PDDocument.load(bytes).use { document ->
            val box = document.freeTexts().single() as PDAnnotationMarkup
            assertEquals("Kept after saving", box.contents)
            assertTrue(box.normalAppearanceStream.contentStream.toByteArray().isNotEmpty())
        }
    }

    @Test
    fun nonLatinTextDoesNotThrowAndSurvivesSaving() {
        val bytes = sample().use { document ->
            TextBoxes.add(document, 0, PdfPoint(72f, 600f), "Zażółć gęślą jaźń\nПривет, мир", Annotator.Rgb.Blue)
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        PDDocument.load(bytes).use { document ->
            val box = document.freeTexts().single()
            assertEquals("Zażółć gęślą jaźń\nПривет, мир", box.contents)
            assertTrue(box.normalAppearanceStream.contentStream.toByteArray().isNotEmpty())
        }
    }

    @Test
    fun editKeepsTheTopLeftAndResizes() {
        sample().use { document ->
            val box = TextBoxes.add(document, 0, PdfPoint(72f, 700f), "Short", Annotator.Rgb.Red)
            val before = box.rectangle
            TextBoxes.edit(document, box, "A much longer line of text than before", Annotator.Rgb.Blue, 16f)

            val after = box.rectangle
            assertEquals("A much longer line of text than before", box.contents)
            assertEquals(before.lowerLeftX, after.lowerLeftX, 0.01f)
            assertEquals(before.upperRightY, after.upperRightY, 0.01f)
            assertTrue(after.width > before.width)
            assertEquals(16f, TextBoxes.styleOf(box)!!.fontSize, 0.01f)
            assertEquals(1, document.freeTexts().size)
        }
    }

    @Test
    fun onARotatedPageTheBoxTurnsWithThePage() {
        sample().use { document ->
            PageEditor.rotate(document, 0, 90)
            val box = TextBoxes.add(document, 0, PdfPoint(300f, 300f), "Sideways page", Annotator.Rgb.Red)
            val rect = box.rectangle
            val bBox = box.normalAppearanceStream.getBBox()
            // Stored rectangle is the upright box turned a quarter: width and height swap.
            assertEquals(bBox.width, rect.height, 0.01f)
            assertEquals(bBox.height, rect.width, 0.01f)
            // Displayed top-left of a page turned 90 degrees clockwise is the stored bottom-left.
            assertEquals(300f, rect.lowerLeftX, 0.01f)
            assertEquals(300f, rect.lowerLeftY, 0.01f)

            TextBoxes.edit(document, box, "Sideways page, edited", Annotator.Rgb.Red, 12f)
            assertEquals(300f, box.rectangle.lowerLeftX, 0.01f)
            assertEquals(300f, box.rectangle.lowerLeftY, 0.01f)
        }
    }
}
