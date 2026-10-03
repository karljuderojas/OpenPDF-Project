package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
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
            assertTrue(PdfText.isLatinGreekOrCyrillic("3 paź 2026"))
            assertTrue(PdfText.isLatinGreekOrCyrillic("3 окт. 2026 г."))
            assertTrue(!PdfText.isLatinGreekOrCyrillic("2026年10月3日"))
            assertTrue(!PdfText.isLatinGreekOrCyrillic("٣ أكتوبر ٢٠٢٦"))
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

    @Test
    fun editModeTextAndPicturesLandInThePage() {
        sample().use { document ->
            val page = document.getPage(0)
            val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
            fun at(nx: Float, ny: Float) = displayToPdf(nx, ny, page.rotation, crop)

            PageEditor.addText(document, 0, "Added in Edit mode", at(0.1f, 0.06f), fontSize = 14f)
            // A photo (no transparency) and a logo with a see-through background.
            PageEditor.addImage(document, 0, photoBitmap(), at(0.55f, 0.30f), width = 200f, height = 120f)
            PageEditor.addImage(document, 0, logoBitmap(), at(0.08f, 0.22f), width = 72f, height = 72f)

            val images = page.resources.xObjectNames.map { page.resources.getXObject(it) }.filterIsInstance<PDImageXObject>()
            assertEquals(2, images.size)
            // The photo is stored as JPEG to keep the file small; the logo keeps its transparency.
            assertTrue(images.any { it.suffix == "jpg" })
            assertTrue(images.any { it.suffix == "png" && it.cosObject.containsKey(COSName.SMASK) })
            assertTrue(text(document, page = 1).contains("Added in Edit mode"))

            File("build/outputs/qa").apply { mkdirs() }.resolve("edit-text-and-image.pdf").let { document.save(it) }
        }
    }

    private fun photoBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(500, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(135, 190, 235))
            drawCircle(380f, 90f, 50f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 200, 60) })
            drawRect(0f, 210f, 500f, 300f, Paint().apply { color = Color.rgb(70, 140, 70) })
        }
        bitmap.setHasAlpha(false)
        return bitmap
    }

    private fun logoBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(100f, 100f, 90f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 40, 60) })
        return bitmap
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
