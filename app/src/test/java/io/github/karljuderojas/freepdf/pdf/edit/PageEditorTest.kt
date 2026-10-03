package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStamper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Text, checkmarks and signatures placed in Sign mode. Runs under Robolectric so FreePdfApp
 * initialises PdfBox-Android's resources (the bundled Liberation Sans font needs them).
 *
 * Also writes build/outputs/qa/stamps-on-rotated-page.pdf, which CI renders with poppler and
 * posts with the screenshots, since PDFium cannot render under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageEditorTest {

    private fun sample(): PDDocument =
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    @Test
    fun textOutsideHelveticaDoesNotThrowAndIsKept() {
        sample().use { document ->
            // A Polish and a Russian date, as DateFormat gives them, plus a Greek name.
            val texts = listOf("3 paź 2026", "3 окт. 2026 г.", "Νίκος Παπαδόπουλος", "Oct 3, 2026")
            texts.forEachIndexed { i, text -> PageEditor.addText(document, 0, text, PdfPoint(72f, 700f - i * 20f)) }
            val extracted = text(document, page = 1)
            texts.forEach { assertTrue("missing $it in:\n$extracted", extracted.contains(it)) }
        }
    }

    @Test
    fun plainTextKeepsTheStandardFontAndUnknownGlyphsBecomeQuestionMarks() {
        sample().use { document ->
            assertSame(PDType1Font.HELVETICA, PdfText.fontFor(document, "Oct 3, 2026"))
            val font = PdfText.fontFor(document, "2026年10月3日")
            assertEquals("2026?10?3?", PdfText.printable("2026年10月3日", font))
            assertEquals("a b", PdfText.printable("a\tb", font))
            assertEquals(listOf("one", "two"), PdfText.lines("one\ntwo\n"))
        }
    }

    @Test
    fun multiLineTextLandsOnSeparateLines() {
        sample().use { document ->
            PageEditor.addText(document, 0, "First line\nSecond line", PdfPoint(72f, 700f), fontSize = 11f)
            val lines = text(document, page = 1).lines()
            assertTrue(lines.any { it.trim() == "First line" })
            assertTrue(lines.any { it.trim() == "Second line" })
        }
    }

    @Test
    fun stampsOnARotatedPageReadUprightAsShown() {
        sample().use { document ->
            // A scan-like page: stored portrait, shown turned 90 degrees clockwise.
            PageEditor.rotate(document, 0, 90)
            val page = document.getPage(0)
            val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
            // Taps as the user sees them: a line near the bottom of the displayed page.
            fun tap(nx: Float, ny: Float) = displayToPdf(nx, ny, page.rotation, crop)

            PageEditor.addText(document, 0, "Signed on a rotated page: 3 paź 2026", tap(0.08f, 0.80f), fontSize = 14f)
            PageEditor.addCheckmark(document, 0, tap(0.08f, 0.88f), size = 14f)
            PageEditor.addText(document, 0, "I agree to the terms above", tap(0.12f, 0.875f), fontSize = 12f)
            SignatureStamper.stamp(document, 0, signatureBitmap(), tap(0.65f, 0.86f), maxWidth = 160f, maxHeight = 56f, caption = "Dana Whitfield")

            // Reading with the page's rotation applied, the text comes out in display order.
            val extracted = text(document, page = 1)
            assertTrue(extracted, extracted.contains("Signed on a rotated page: 3 paź 2026"))
            assertTrue(extracted, extracted.contains("I agree to the terms above"))

            File("build/outputs/qa").apply { mkdirs() }.resolve("stamps-on-rotated-page.pdf").let { document.save(it) }
        }
    }

    private fun text(document: PDDocument, page: Int): String =
        PDFTextStripper().apply { startPage = page; endPage = page }.getText(document)

    private fun signatureBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 63, 168); strokeWidth = 6f; style = Paint.Style.STROKE }
        Canvas(bitmap).apply {
            drawLine(20f, 70f, 280f, 60f, paint)
            drawArc(40f, 10f, 160f, 80f, 200f, 300f, false, paint)
        }
        return bitmap
    }
}
