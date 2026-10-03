package io.github.karljuderojas.freepdf.pdf.annotate

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.edit.CropMargins
import io.github.karljuderojas.freepdf.pdf.edit.PageCrop
import io.github.karljuderojas.freepdf.pdf.text.PageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Runs under Robolectric so FreePdfApp initialises PdfBox-Android's resources. */
@RunWith(AndroidJUnit4::class)
class MarksTest {

    private val letter = PdfRect(0f, 0f, 612f, 792f)

    private fun sample() = PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    /** The sample with a highlight over "Northwind Studio", a pen stroke and a note, saved and reopened. */
    private fun marked(): PDDocument = sample().use { document ->
        val words = PageText.words(document, 0)
        val from = words.indexOfFirst { it.text == "Northwind" }
        val line = words.subList(from, from + 2).let { w ->
            val a = displayToPdf(w.first().left, w.first().top, 0, letter)
            val b = displayToPdf(w.last().right, w.last().bottom, 0, letter)
            PdfRect(a.x, b.y, b.x, a.y)
        }
        Annotator.markText(document, 0, listOf(line), comment = "Check the name", author = "Dana")
        Annotator.ink(document, 0, listOf(listOf(PdfPoint(100f, 200f), PdfPoint(200f, 220f))))
        Annotator.note(document, 1, PdfPoint(300f, 500f), "Sign here")
        val bytes = ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        PDDocument.load(bytes)
    }

    @Test
    fun listsEveryMarkWithWhatItSaysAndCovers() {
        val marks = marked().use { Marks.list(it) }
        assertEquals(listOf(Mark.Kind.Highlight, Mark.Kind.Ink, Mark.Kind.Note), marks.map { it.kind })
        val highlight = marks[0]
        assertEquals("Northwind Studio", highlight.markedText)
        assertEquals("Check the name", highlight.comment)
        assertEquals("Dana", highlight.author)
        assertEquals(1, marks[2].page)
        assertEquals("Sign here", marks[2].comment)
        marks.forEach {
            assertTrue(it.left < it.right && it.top < it.bottom)
            assertTrue(it.modified != null)
        }
    }

    @Test
    fun aMarkInACroppedAwayMarginIsNotListed() {
        marked().use { document ->
            // The note on page 2 sits at y = 500pt, just over a third of the way down an 11in page.
            assertEquals(1, Marks.list(document).count { it.page == 1 })
            PageCrop.crop(document, listOf(1), CropMargins(top = 0.45f))
            assertTrue(Marks.list(document).none { it.page == 1 })
            assertEquals(2, Marks.list(document).count { it.page == 0 })
        }
    }

    @Test
    fun editChangesColourWidthAndComment() {
        val marks = marked().use { document ->
            Marks.edit(document, 0, 0, color = Annotator.Rgb.Blue, comment = "Fixed")
            Marks.edit(document, 0, 1, width = 8f)
            Marks.list(document)
        }
        assertEquals(Annotator.Rgb.Blue, marks[0].color)
        assertEquals("Fixed", marks[0].comment)
        assertEquals(8f, marks[1].width)
    }

    @Test
    fun aBlankCommentRemovesIt() {
        val highlight = marked().use { document ->
            Marks.edit(document, 0, 0, comment = "  ")
            Marks.list(document).first()
        }
        assertEquals("", highlight.comment)
    }

    @Test
    fun textBoxesListTheirTextAndEditWithTheirOwnStyle() {
        val box = marked().use { document ->
            TextBoxes.add(document, 0, PdfPoint(72f, 300f), "Call Dana", Annotator.Rgb.Red, fontSize = 16f)
            // The sample's note on page 2 comes after it, so pick the text box by kind.
            val index = Marks.list(document).single { it.kind == Mark.Kind.TextBox }.index
            Marks.edit(document, 0, index, color = Annotator.Rgb.Blue, width = 24f, comment = "Call Dana first")
            Marks.list(document).single { it.kind == Mark.Kind.TextBox }
        }
        assertEquals(Mark.Kind.TextBox, box.kind)
        assertEquals("Call Dana first", box.comment)
        assertEquals(24f, box.width, 0.01f)
        assertEquals(Annotator.Rgb.Blue.b, box.color!!.b, 0.001f)
    }

    @Test
    fun recolouringAStampRedrawsIt() {
        val (before, after, mark) = marked().use { document ->
            val stamp = Stamps.add(document, 1, PdfPoint(300f, 300f), Stamps.Kind.Draft)
            val before = String(stamp.normalAppearanceStream.contentStream.toByteArray(), Charsets.ISO_8859_1)
            val index = Marks.list(document).single { it.kind == Mark.Kind.Stamp }.index
            Marks.edit(document, 1, index, color = Annotator.Rgb.Red)
            val after = String(stamp.normalAppearanceStream.contentStream.toByteArray(), Charsets.ISO_8859_1)
            Triple(before, after, Marks.list(document).single { it.kind == Mark.Kind.Stamp })
        }
        assertTrue(before != after)
        assertTrue(after, after.contains("(DRAFT) Tj"))
        assertEquals(Annotator.Rgb.Red.r, mark.color!!.r, 0.001f)
    }

    @Test
    fun wordsAreOnlyAskedForPagesWithMarkedText() {
        val asked = ArrayList<Int>()
        marked().use { document ->
            Marks.list(document) { page -> asked += page; PageText.words(document, page) }
        }
        // The highlight is on the first page; the note on the second needs no words.
        assertEquals(listOf(0), asked)
    }

    @Test
    fun aDotStrokeIsDrawnAsASmallRing() {
        sample().use { document ->
            Annotator.ink(document, 0, listOf(listOf(PdfPoint(100f, 100f))), lineWidth = 4f)
            Annotator.ink(document, 0, listOf(listOf(PdfPoint(200f, 200f), PdfPoint(200.1f, 200f))), lineWidth = 4f)
            val inks = document.getPage(0).annotations.filter { it.subtype == PDAnnotationMarkup.SUB_TYPE_INK }.map { it as PDAnnotationMarkup }
            assertEquals(2, inks.size)
            inks.forEach { ink ->
                val path = ink.inkList.single()
                // A closed ring of several points around the tap, spanning less than the pen width.
                assertTrue(path.size >= 2 * 5)
                assertEquals(path[0], path[path.size - 2], 0.001f)
                assertEquals(path[1], path[path.size - 1], 0.001f)
                val xs = (path.indices step 2).map { path[it] }
                assertTrue(xs.max() - xs.min() in 0.5f..4f)
                val content = String(ink.normalAppearanceStream.contentStream.toByteArray(), Charsets.ISO_8859_1)
                assertTrue(content, content.contains(" l\n") || content.contains(" l "))
            }
            // A real stroke is kept as drawn.
            Annotator.ink(document, 0, listOf(listOf(PdfPoint(10f, 10f), PdfPoint(50f, 60f))), lineWidth = 4f)
            val line = document.getPage(0).annotations.last() as PDAnnotationMarkup
            assertEquals(listOf(10f, 10f, 50f, 60f), line.inkList.single().toList())
        }
    }

    @Test
    fun deleteRemovesOnlyThatMark() {
        val marks = marked().use { document ->
            Marks.delete(document, 0, 0)
            Marks.list(document)
        }
        assertEquals(listOf(Mark.Kind.Ink, Mark.Kind.Note), marks.map { it.kind })
    }
}
