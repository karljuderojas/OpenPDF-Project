package io.github.karljuderojas.freepdf.pdf.redact

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.displayToPdf
import io.github.karljuderojas.freepdf.pdf.text.PageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * True redaction: what is under a marked area must be gone from the saved file, not just
 * covered. Every check here is made on a document saved to bytes and loaded again, and on the
 * raw (inflated) content streams as well as through PdfBox's text extraction.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RedactorTest {

    private val helvetica = PDType1Font.HELVETICA

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
        const val BLACK = 0xFF000000.toInt()
    }

    // ---- helpers ----

    /** Adds a Letter page whose content is [content], drawn with Helvetica as /F1. */
    private fun PDDocument.addPage(content: String, configure: PDPage.() -> Unit = {}): PDPage {
        val page = PDPage(PDRectangle.LETTER)
        addPage(page)
        page.resources = com.tom_roush.pdfbox.pdmodel.PDResources().also { it.put(COSName.getPDFName("F1"), helvetica) }
        page.setContents(PDStream(this, ByteArrayInputStream(content.toByteArray(Charsets.ISO_8859_1))))
        page.configure()
        return page
    }

    private fun roundTrip(document: PDDocument): PDDocument {
        val out = ByteArrayOutputStream()
        document.save(out)
        return PDDocument.load(out.toByteArray())
    }

    private fun textOf(document: PDDocument, page: Int = 1): String =
        PDFTextStripper().apply { startPage = page; endPage = page }.getText(document)

    /** The page's content as the plain text of its operators, with every stream inflated. */
    private fun rawContent(document: PDDocument, page: Int = 0): String {
        val pdPage = document.getPage(page)
        return String(pdPage.contents.readBytes(), Charsets.ISO_8859_1)
    }

    private fun operators(document: PDDocument, page: Int = 0): List<String> =
        PDFStreamParser(document.getPage(page)).apply { parse() }.tokens.filterIsInstance<Operator>().map { it.name }

    /** Where the text "[word]" starts on the first page, from PdfBox's own layout analysis. */
    private fun xOf(document: PDDocument, word: String): Float {
        var found = Float.NaN
        object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: List<TextPosition>) {
                val at = text.indexOf(word)
                if (at >= 0 && found.isNaN()) found = textPositions[at].x
            }
        }.getText(document)
        return found
    }

    /** The box of [text] set in Helvetica at [size] starting at ([x], [y]), restricted to the characters [from] until [to]. */
    private fun boxOf(text: String, size: Float, x: Float, y: Float, from: Int, to: Int): PdfRect {
        val left = x + helvetica.getStringWidth(text.substring(0, from)) / 1000f * size
        val right = x + helvetica.getStringWidth(text.substring(0, to)) / 1000f * size
        return PdfRect(left, y - 2f, right, y + size)
    }

    // ---- text ----

    @Test
    fun removesTheTextFromTheFileAndKeepsTheRest() {
        val ssn = "SSN: 123-45-6789"
        PDDocument().use { source ->
            source.addPage("BT /F1 12 Tf 72 700 Td (Name: Alice Johnson) Tj 0 -20 Td ($ssn) Tj 0 -20 Td (Public line) Tj ET")
            val area = boxOf(ssn, 12f, 72f, 680f, from = 5, to = ssn.length)

            val result = Redactor.redact(source, mapOf(0 to listOf(area)))
            assertEquals(11, result.textCharacters)
            assertFalse(result.foundNothing)

            roundTrip(source).use { saved ->
                val text = textOf(saved)
                assertFalse(text, text.contains("6789"))
                assertFalse(text, text.contains("123"))
                assertTrue(text, text.contains("SSN:"))
                assertTrue(text, text.contains("Alice Johnson"))
                assertTrue(text, text.contains("Public line"))
                // Not in the content stream either: it is gone, not hidden.
                val raw = rawContent(saved)
                assertFalse(raw, raw.contains("6789"))
                assertFalse(raw, raw.contains("123-45"))
                // And a black box is painted over the spot.
                assertTrue(raw, raw.contains("0 0 0 rg"))
            }
        }
    }

    @Test
    fun theTextAfterTheRemovedWordsStaysWhereItWas() {
        val line = "Name: Alice Johnson Smith"
        PDDocument().use { source ->
            source.addPage("BT /F1 12 Tf 72 700 Td ($line) Tj ET")
            val before = xOf(source, "Smith")
            Redactor.redact(source, mapOf(0 to listOf(boxOf(line, 12f, 72f, 700f, from = 12, to = 20))))

            roundTrip(source).use { saved ->
                assertFalse(textOf(saved).contains("Johnson"))
                assertEquals(before, xOf(saved, "Smith"), 0.05f)
                assertEquals(72f, xOf(saved, "Name:"), 0.05f)
            }
        }
    }

    @Test
    fun cutsGlyphsOutOfTJArraysAndKeepsTheirSpacing() {
        PDDocument().use { source ->
            source.addPage("BT /F1 12 Tf 72 700 Td [(Hel) -80 (lo ) 30 (Secret) -40 (Word)] TJ ET")
            val before = xOf(source, "Word")
            // "Hello " is 6 characters wide in Helvetica; "Secret" follows it.
            val width = helvetica.getStringWidth("Hello ") / 1000f * 12f
            val secret = helvetica.getStringWidth("Secret") / 1000f * 12f
            // Adjustments move the pen, so measure the box from the text as PdfBox lays it out.
            val start = xOf(source, "Secret")
            Redactor.redact(source, mapOf(0 to listOf(PdfRect(start + 0.5f, 698f, start + secret - 0.5f, 712f))))

            roundTrip(source).use { saved ->
                val text = textOf(saved)
                assertFalse(text, text.contains("Secret"))
                assertTrue(text, text.contains("Hello"))
                assertEquals(before, xOf(saved, "Word"), 0.05f)
                assertTrue(width > 0)
            }
        }
    }

    @Test
    fun handlesQuoteOperatorsAndRendersTheRestInPlace() {
        PDDocument().use { source ->
            source.addPage("BT /F1 12 Tf 14 TL 72 700 Td (first) Tj (second secret) ' 2 1 (third secret) \" (fourth) ' ET")
            val before = xOf(source, "fourth")
            val start = xOf(source, "secret")
            Redactor.redact(source, mapOf(0 to listOf(PdfRect(start - 1f, 650f, start + 100f, 700f))))

            roundTrip(source).use { saved ->
                val text = textOf(saved)
                assertFalse(text, text.contains("secret"))
                assertTrue(text, text.contains("first"))
                assertTrue(text, text.contains("second"))
                assertTrue(text, text.contains("fourth"))
                assertEquals(before, xOf(saved, "fourth"), 0.05f)
            }
        }
    }

    @Test
    fun removesInvisibleTextToo() {
        // Render mode 3 is what an OCR tool puts under a scanned page.
        PDDocument().use { source ->
            source.addPage("BT /F1 12 Tf 3 Tr 72 700 Td (hidden ocr words) Tj ET")
            Redactor.redact(source, mapOf(0 to listOf(PdfRect(60f, 690f, 300f, 720f))))
            roundTrip(source).use { saved ->
                assertFalse(textOf(saved), textOf(saved).contains("ocr"))
                assertFalse(rawContent(saved).contains("ocr"))
            }
        }
    }

    @Test
    fun worksOnARotatedPage() {
        val line = "Account 99887766"
        PDDocument().use { source ->
            source.addPage("BT /F1 12 Tf 72 700 Td ($line) Tj ET") { rotation = 90 }
            Redactor.redact(source, mapOf(0 to listOf(boxOf(line, 12f, 72f, 700f, from = 8, to = line.length))))
            roundTrip(source).use { saved ->
                val text = textOf(saved).replace(Regex("\\s"), "")
                assertFalse(text, text.contains("9988"))
                assertTrue(text, text.contains("Account"))
                assertEquals(90, saved.getPage(0).rotation)
            }
        }
    }

    @Test
    fun otherPagesAreUntouched() {
        PDDocument().use { source ->
            source.addPage("BT /F1 12 Tf 72 700 Td (secret on one) Tj ET")
            source.addPage("BT /F1 12 Tf 72 700 Td (secret on two) Tj ET")
            Redactor.redact(source, mapOf(0 to listOf(PdfRect(60f, 690f, 400f, 720f))))
            roundTrip(source).use { saved ->
                assertFalse(textOf(saved, 1).contains("secret"))
                assertTrue(textOf(saved, 2).contains("secret on two"))
            }
        }
    }

    @Test
    fun redactsAWordPickedOnTheSamplePageTheWayTheViewerDoes() {
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf")).use { source ->
            val crop = source.getPage(0).cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
            val words = PageText.words(source, 0)
            val word = words.first { it.text == "Northwind" }
            val a = displayToPdf(word.left, word.top, 0, crop)
            val b = displayToPdf(word.right, word.bottom, 0, crop)
            val area = PdfRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))

            val before = textOf(source, 1)
            assertTrue(before.contains("Northwind"))
            Redactor.redact(source, mapOf(0 to listOf(area)))

            roundTrip(source).use { saved ->
                val text = textOf(saved, 1)
                assertFalse(text, text.contains("Northwind"))
                assertTrue(text, text.contains("Services"))
                assertEquals(2, saved.numberOfPages)
                // Only that one word went: the rest of the page still reads.
                assertEquals(before.replace("Northwind", "").replace(Regex("\\s+"), " ").trim().length.toFloat(),
                    text.replace(Regex("\\s+"), " ").trim().length.toFloat(), 3f)
            }
        }
    }

    // ---- line art ----

    @Test
    fun removesLineArtInTheAreaAndKeepsWhatOnlyLiesUnderIt() {
        PDDocument().use { source ->
            // A grey page background, a line through the area, a box drawn wholly inside it, and a line elsewhere.
            source.addPage(
                """
                0.9 g 0 0 612 792 re f
                0 G 2 w 100 100 m 300 100 l S
                0 g 150 90 20 20 re f
                0 G 100 500 m 300 500 l S
                """.trimIndent(),
            )
            val result = Redactor.redact(source, mapOf(0 to listOf(PdfRect(120f, 80f, 200f, 120f))))
            assertEquals(2, result.shapes)

            roundTrip(source).use { saved ->
                // Background kept; the crossing line and the inner box become "n"; the far line stays.
                val ops = operators(saved).filter { it in setOf("f", "S", "n") }
                assertEquals(listOf("f", "n", "n", "S", "f"), ops)
            }
        }
    }

    @Test
    fun keepsTheBorderOfABoxThatSurroundsTheArea() {
        PDDocument().use { source ->
            source.addPage("0 G 1 w 50 50 400 300 re S")
            val result = Redactor.redact(source, mapOf(0 to listOf(PdfRect(200f, 150f, 300f, 250f))))
            assertEquals(0, result.shapes)
            roundTrip(source).use { assertTrue(operators(it).contains("S")) }
        }
    }

    // ---- pictures ----

    /** A [size] x [size] picture whose left half is red and right half green. */
    private fun twoColourPicture(document: PDDocument, size: Int = 100): PDImageXObject {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) for (x in 0 until size) bitmap.setPixel(x, y, if (x < size / 2) RED else GREEN)
        return LosslessFactory.createFromImage(document, bitmap)
    }

    @Test
    fun blanksOnlyThePixelsInTheArea() {
        PDDocument().use { source ->
            val page = source.addPage("q 200 0 0 200 100 400 cm /Im1 Do Q")
            val name = page.resources.add(twoColourPicture(source))
            assertEquals("Im1", name.name)

            // The picture covers x 100..300, y 400..600. Mark its top-left quarter (red).
            val result = Redactor.redact(source, mapOf(0 to listOf(PdfRect(100f, 500f, 200f, 600f))))
            assertEquals(1, result.pictures)

            roundTrip(source).use { saved ->
                val resources = saved.getPage(0).resources
                // The original picture is gone from the file; only the new one is left.
                assertEquals(1, resources.xObjectNames.count())
                val picture = resources.getXObject(resources.xObjectNames.first()) as PDImageXObject
                val pixels = picture.image
                assertEquals(100, pixels.width)
                assertEquals(BLACK, pixels.getPixel(10, 10))
                assertEquals(BLACK, pixels.getPixel(48, 48))
                // Below the area, and to the right of it, the picture is as it was.
                assertEquals(RED, pixels.getPixel(10, 80))
                assertEquals(GREEN, pixels.getPixel(80, 10))
            }
        }
    }

    @Test
    fun aPictureTheAreaDoesNotReachIsLeftAlone() {
        PDDocument().use { source ->
            val page = source.addPage("q 200 0 0 200 100 400 cm /Im1 Do Q")
            page.resources.add(twoColourPicture(source))
            val result = Redactor.redact(source, mapOf(0 to listOf(PdfRect(400f, 400f, 500f, 500f))))
            assertEquals(0, result.pictures)
            roundTrip(source).use { saved ->
                val resources = saved.getPage(0).resources
                val picture = resources.getXObject(resources.xObjectNames.first()) as PDImageXObject
                assertEquals(RED, picture.image.getPixel(10, 10))
            }
        }
    }

    // ---- forms ----

    @Test
    fun rewritesAFormInACopyAndLeavesOtherPagesUsingTheOriginal() {
        PDDocument().use { source ->
            val form = PDFormXObject(source)
            form.bBox = PDRectangle(0f, 0f, 300f, 100f)
            form.resources = com.tom_roush.pdfbox.pdmodel.PDResources().also { it.put(COSName.getPDFName("F1"), helvetica) }
            form.stream.createOutputStream().use { it.write("BT /F1 12 Tf 10 50 Td (form secret) Tj ET".toByteArray()) }

            fun page(): PDPage {
                val page = source.addPage("q 1 0 0 1 100 400 cm /Fm1 Do Q")
                page.resources.put(COSName.getPDFName("Fm1"), form)
                return page
            }
            page()
            page()

            // The form paints at (110, 450); mark it on the first page only.
            val result = Redactor.redact(source, mapOf(0 to listOf(PdfRect(105f, 445f, 400f, 470f))))
            assertEquals(1, result.forms)

            roundTrip(source).use { saved ->
                assertFalse(textOf(saved, 1).contains("secret"))
                assertTrue(textOf(saved, 2).contains("form secret"))
            }
        }
    }

    // ---- annotations and metadata ----

    @Test
    fun removesAnnotationsInTheAreaAndTheWordsFromMetadata() {
        val line = "Client: Zebediah Quill"
        PDDocument().use { source ->
            val page = source.addPage("BT /F1 12 Tf 72 700 Td ($line) Tj ET")
            val note = PDAnnotationText().apply {
                rectangle = PDRectangle(150f, 695f, 20f, 20f)
                contents = "Zebediah owes us money"
            }
            val far = PDAnnotationText().apply {
                rectangle = PDRectangle(400f, 300f, 20f, 20f)
                contents = "Keep me"
            }
            page.annotations = listOf(note, far)
            source.documentInformation.title = "Letter to Zebediah Quill"
            source.documentInformation.author = "Records office"

            val result = Redactor.redact(source, mapOf(0 to listOf(boxOf(line, 12f, 72f, 700f, from = 8, to = line.length))))
            assertEquals(1, result.annotations)

            roundTrip(source).use { saved ->
                val annotations = saved.getPage(0).annotations
                assertEquals(1, annotations.size)
                assertEquals("Keep me", annotations.single().contents)
                assertNull(saved.documentInformation.title)
                assertEquals("Records office", saved.documentInformation.author)
                assertFalse(String(ByteArrayOutputStream().also { saved.save(it) }.toByteArray(), Charsets.ISO_8859_1).contains("Zebediah"))
            }
        }
    }

    @Test
    fun blankAreasStillGetTheirBlackBox() {
        PDDocument().use { source ->
            source.addPage("BT /F1 12 Tf 72 700 Td (hello) Tj ET")
            val result = Redactor.redact(source, mapOf(0 to listOf(PdfRect(300f, 300f, 400f, 340f))))
            assertTrue(result.foundNothing)
            roundTrip(source).use { assertTrue(rawContent(it).contains("0 0 0 rg")) }
        }
    }

    @Test
    fun suggestsACopyName() {
        assertEquals("Lease (redacted).pdf", Redactor.suggestedName("Lease.pdf"))
        assertEquals("notes (redacted).pdf", Redactor.suggestedName("notes"))
    }

    @Test
    fun writesAnExampleForReview() {
        PDDocument().use { source ->
            source.addPage(
                "BT /F1 16 Tf 72 720 Td (Patient record) Tj 0 -30 Td /F1 12 Tf (Name: Alice Johnson) Tj 0 -20 Td " +
                    "(Date of birth: 04/12/1986) Tj 0 -20 Td (SSN: 123-45-6789) Tj 0 -20 Td (Diagnosis: seasonal allergies) Tj ET",
            )
            val ssn = "SSN: 123-45-6789"
            Redactor.redact(
                source,
                mapOf(0 to listOf(boxOf(ssn, 12f, 72f, 650f, from = 5, to = ssn.length), boxOf("Date of birth: 04/12/1986", 12f, 72f, 670f, 15, 25))),
            )
            val out = File("build/outputs/qa/redacted-example.pdf").apply { parentFile?.mkdirs() }
            source.save(out)
        }
        PDDocument.load(File("build/outputs/qa/redacted-example.pdf")).use { saved ->
            assertNotNull(saved)
            assertFalse(textOf(saved).contains("6789"))
        }
    }
}
