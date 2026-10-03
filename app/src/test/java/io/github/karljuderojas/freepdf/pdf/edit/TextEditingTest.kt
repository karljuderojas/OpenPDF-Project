package io.github.karljuderojas.freepdf.pdf.edit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Runs under Robolectric so FreePdfApp initialises PdfBox-Android's resources. */
@RunWith(AndroidJUnit4::class)
class TextEditingTest {

    /** A Letter page with [draw] on it, saved and loaded again so it is a PDF as a file has it. */
    private fun page(rotation: Int = 0, draw: PDPageContentStream.(PDDocument) -> Unit): PDDocument {
        val document = PDDocument()
        val page = PDPage().also { it.rotation = rotation }
        document.addPage(page)
        PDPageContentStream(document, page).use { it.draw(document) }
        val bytes = ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        document.close()
        return PDDocument.load(bytes)
    }

    private fun PDPageContentStream.line(font: PDFont, size: Float, x: Float, y: Float, text: String) {
        beginText()
        setFont(font, size)
        newLineAtOffset(x, y)
        showText(text)
        endText()
    }

    private fun PDDocument.text() = PDFTextStripper().getText(this).trim().lines().joinToString("\n") { it.trim() }

    // Fractions of a Letter page across and down, for a point x, y points from its bottom left.
    private fun fx(x: Float) = x / 612f
    private fun fy(y: Float) = (792f - y) / 792f

    private fun liberation(document: PDDocument) = PDType0Font.load(document, PDFBoxResourceLoader.getStream(PdfText.LIBERATION_SANS))

    @Test
    fun findsTheLineUnderATapAndNothingElsewhere() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Hello world") }.use { document ->
            assertEquals("Hello world", TextEditing.lineAt(document, 0, fx(90f), fy(703f))?.text)
            assertNull(TextEditing.lineAt(document, 0, fx(300f), fy(300f)))
        }
    }

    @Test
    fun replacesTheWordsInTheSameFontAndLeavesOtherLinesAlone() {
        page {
            line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Hello world")
            line(PDType1Font.HELVETICA_BOLD, 14f, 72f, 650f, "Second line")
        }.use { document ->
            val outcome = TextEditing.replace(document, 0, fx(90f), fy(703f), "Hello world", "Goodbye moon")
            assertEquals(TextEditing.Outcome.SameFont, outcome)
            assertEquals("Goodbye moon\nSecond line", document.text())
        }
    }

    @Test
    fun theOldWordsAreRemovedNotPaintedOver() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Secret figure") }.use { document ->
            TextEditing.replace(document, 0, fx(90f), fy(703f), "Secret figure", "Public figure")
            // One line of text on the page, so nothing of the old words is left under the new.
            assertEquals("Public figure", document.text())
        }
    }

    @Test
    fun aLineMadeOfSeveralStringsIsOneLineButAFarColumnIsNot() {
        page {
            beginText()
            setFont(PDType1Font.HELVETICA, 12f)
            newLineAtOffset(72f, 700f)
            showText("Total: ")
            showText("100 USD")
            endText()
            line(PDType1Font.HELVETICA, 12f, 400f, 700f, "Right column")
        }.use { document ->
            assertEquals("Total: 100 USD", TextEditing.lineAt(document, 0, fx(80f), fy(703f))?.text)
            TextEditing.replace(document, 0, fx(80f), fy(703f), "Total: 100 USD", "Total: 250 USD")
            assertEquals("Total: 250 USD Right column", document.text().replace(Regex("\\s+"), " "))
        }
    }

    @Test
    fun aSubsetFontKeepsToTheLettersItHasAndOtherLettersUseTheBundledFont() {
        page { document -> line(liberation(document), 12f, 72f, 700f, "Pay the invoice") }.use { document ->
            assertEquals("Pay the invoice", TextEditing.lineAt(document, 0, fx(90f), fy(703f))?.text)
            val same = TextEditing.replace(document, 0, fx(90f), fy(703f), "Pay the invoice", "Pay the in")
            assertEquals(TextEditing.Outcome.SameFont, same)
            assertEquals("Pay the in", document.text())
            // "z" was never drawn, so the subset has no glyph for it.
            val other = TextEditing.replace(document, 0, fx(90f), fy(703f), "Pay the in", "Pay the zebra")
            assertEquals(TextEditing.Outcome.OtherFont, other)
            assertEquals("Pay the zebra", document.text())
        }
    }

    @Test
    fun lettersHelveticaLacksMoveTheLineToTheBundledFont() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Date: Oct 3") }.use { document ->
            val outcome = TextEditing.replace(document, 0, fx(90f), fy(703f), "Date: Oct 3", "Дата: 3 окт")
            assertEquals(TextEditing.Outcome.OtherFont, outcome)
            assertEquals("Дата: 3 окт", document.text())
        }
    }

    @Test
    fun anEmptyReplacementDeletesTheLine() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Remove me") }.use { document ->
            TextEditing.replace(document, 0, fx(90f), fy(703f), "Remove me", "")
            assertEquals("", document.text())
        }
    }

    @Test
    fun aLineThatChangedSinceItWasReadIsRefused() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Hello world") }.use { document ->
            val failure = runCatching { TextEditing.replace(document, 0, fx(90f), fy(703f), "Something else", "X") }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals("Hello world", document.text())
        }
    }

    @Test
    fun textThatReadsUprightOnARotatedPageCanBeEdited() {
        page(rotation = 90) {
            beginText()
            setFont(PDType1Font.HELVETICA, 12f)
            setTextMatrix(Matrix.getRotateInstance(Math.toRadians(90.0), 100f, 100f))
            showText("Sideways ok")
            endText()
        }.use { document ->
            // Shown turned a quarter clockwise, the text runs left to right from near the top left.
            var found: TextEditing.EditableLine? = null
            var at = 0f to 0f
            for (i in 0 until 60) for (j in 0 until 60) {
                TextEditing.lineAt(document, 0, i / 60f, j / 60f)?.let { found = it; at = i / 60f to j / 60f }
            }
            assertEquals("Sideways ok", found?.text)
            TextEditing.replace(document, 0, at.first, at.second, "Sideways ok", "Turned")
            assertEquals("Turned", document.text())
        }
    }
}
