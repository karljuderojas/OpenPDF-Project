package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Also writes build/outputs/qa/watermarks.pdf, which CI renders with poppler and posts with the
 * screenshots, since PDFium cannot render under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatermarksTest {

    private fun sample(): PDDocument =
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    private fun text(document: PDDocument, page: Int): String =
        PDFTextStripper().apply { startPage = page; endPage = page }.getText(document)

    /** The operands of every [operator] in page [index]'s content, in order. */
    private fun operands(document: PDDocument, index: Int, operator: String): List<List<COSBase>> {
        val tokens = PDFStreamParser(document.getPage(index)).also { it.parse() }.tokens
        val out = ArrayList<List<COSBase>>()
        var pending = ArrayList<COSBase>()
        tokens.forEach { token ->
            if (token is Operator) {
                if (token.name == operator) out += pending
                pending = ArrayList()
            } else if (token is COSBase) {
                pending += token
            }
        }
        return out
    }

    private fun number(operand: COSBase): Float = (operand as COSNumber).floatValue()

    /** The size the watermark's text was set in on page [index]. */
    private fun fontSize(document: PDDocument, index: Int): Float = number(operands(document, index, "Tf").last()[1])

    @Test
    fun textLandsOnlyOnTheNamedPages() {
        sample().use { document ->
            PageEditor.insertBlank(document, 1)
            Watermarks.addText(document, listOf(1), "CONFIDENTIAL", WatermarkStyle(angle = 0f))
            assertTrue(text(document, page = 2).contains("CONFIDENTIAL"))
            assertFalse(text(document, page = 1).contains("CONFIDENTIAL"))
        }
    }

    @Test
    fun textIsDrawnFaintAndTurned() {
        sample().use { document ->
            Watermarks.addText(document, listOf(0), "DRAFT", WatermarkStyle(opacity = 0.25f, angle = 45f))
            val page = document.getPage(0)
            // 45 degrees turned: cos and sin are both about 0.7071 in the rotation matrix. (Turned text
            // is also what text extraction splits up, so the tests that read text use an angle of 0.)
            val turned = operands(document, 0, "cm").any { m ->
                m.size == 6 && abs(number(m[0]) - 0.7071f) < 0.001f && abs(number(m[1]) - 0.7071f) < 0.001f
            }
            assertTrue(turned)
            val states = page.resources.extGStateNames.map { page.resources.getExtGState(it) }
            assertTrue(states.any { it.nonStrokingAlphaConstant == 0.25f && it.strokingAlphaConstant == 0.25f })
        }
    }

    @Test
    fun textKeepsItsWidthOnARotatedPage() {
        // The sample is upright; turned a quarter it is shown wider than tall, so the same size
        // spans more points and the font is set larger by the page's height over its width.
        val sizes = listOf(0, 90, 180, 270).associateWith { rotation ->
            sample().use { document ->
                document.getPage(0).rotation = rotation
                Watermarks.addText(document, listOf(0), "SAMPLE", WatermarkStyle(angle = 0f, size = 0.5f))
                assertTrue(text(document, page = 1).contains("SAMPLE"))
                fontSize(document, 0)
            }
        }
        val box = sample().use { it.getPage(0).cropBox }
        assertEquals(sizes.getValue(0), sizes.getValue(180), 0.01f)
        assertEquals(sizes.getValue(90), sizes.getValue(270), 0.01f)
        assertEquals(sizes.getValue(0) * box.height / box.width, sizes.getValue(90), 0.05f)
    }

    @Test
    fun steepTextStaysInsideAWidePage() {
        sample().use { document ->
            val page = document.getPage(0)
            page.rotation = 90
            val shownHeight = page.cropBox.width
            // Across the shown width the text would be wider than the page is tall; upright it must not be.
            Watermarks.addText(document, listOf(0), "CONFIDENTIAL", WatermarkStyle(angle = 90f, size = 1f))
            val font = PdfText.boldFontFor(document, "CONFIDENTIAL")
            val width = PdfText.widthOf("CONFIDENTIAL", font, fontSize(document, 0))
            assertTrue("$width wide on a page $shownHeight tall", width <= shownHeight + 0.01f)
        }
    }

    @Test
    fun refusesTextTheFontsCannotShow() {
        sample().use { document ->
            assertThrows(IllegalArgumentException::class.java) {
                Watermarks.addText(document, listOf(0), "\u6a5f\u5bc6", WatermarkStyle(angle = 0f))
            }
            assertThrows(IllegalArgumentException::class.java) {
                Watermarks.addText(document, listOf(0), "\u05e1\u05d5\u05d3\u05d9", WatermarkStyle(angle = 0f))
            }
            assertFalse(text(document, page = 1).contains("?"))
        }
    }

    @Test
    fun lettersOutsideLatinDoNotThrow() {
        sample().use { document ->
            Watermarks.addText(document, listOf(0), "СЕКРЕТНО", WatermarkStyle(angle = 0f))
            assertTrue(text(document, page = 1).contains("СЕКРЕТНО"))
        }
    }

    @Test
    fun aPictureIsAddedToThePage() {
        sample().use { document ->
            val logo = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.argb(255, 26, 63, 168)) }
            Watermarks.addImage(document, listOf(0), logo, WatermarkStyle(size = 0.5f, angle = 0f, opacity = 0.4f))
            val page = document.getPage(0)
            val resources = page.resources
            // The picture is stored, drawn (Do) half the page wide, and the faintness applies to it too.
            val drawn = operands(document, 0, "Do").mapNotNull { resources.getXObject(it[0] as COSName) as? PDImageXObject }
            assertTrue(drawn.any { it.width == 200 && it.height == 80 })
            val scale = operands(document, 0, "cm").last()
            assertEquals(page.cropBox.width * 0.5f, number(scale[0]), 0.01f)
            assertEquals(page.cropBox.width * 0.5f * 80 / 200, number(scale[3]), 0.01f)
            val states = resources.extGStateNames.map { resources.getExtGState(it) }
            assertTrue(states.any { it.nonStrokingAlphaConstant == 0.4f && it.strokingAlphaConstant == 0.4f })
        }
    }

    @Test
    fun watermarksCanBeTakenOffAgain() {
        sample().use { document ->
            PageEditor.insertBlank(document, 1)
            val original = text(document, page = 1)
            assertFalse(Watermarks.remove(document, listOf(0, 1)))
            Watermarks.addText(document, listOf(0, 1), "DRAFT", WatermarkStyle(angle = 0f))
            Watermarks.addImage(document, listOf(0), Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888), WatermarkStyle(angle = 0f))
            assertTrue(Watermarks.remove(document, listOf(0)))
            assertEquals(original, text(document, page = 1))
            assertTrue(operands(document, 0, "Do").isEmpty())
            // Page 2 keeps its watermark until it is asked for; a blank page's only stream was the watermark.
            assertTrue(text(document, page = 2).contains("DRAFT"))
            assertTrue(Watermarks.remove(document, listOf(1)))
            assertFalse(document.getPage(1).hasContents())
            assertFalse(Watermarks.remove(document, listOf(0, 1)))
        }
    }

    /** A one-page document whose single content stream is [content] as written, with Helvetica as /F1. */
    private fun rawPage(content: String): PDDocument {
        val document = PDDocument()
        val page = PDPage().also { it.resources = PDResources().apply { put(COSName.getPDFName("F1"), PDType1Font.HELVETICA) } }
        document.addPage(page)
        val stream = PDStream(document)
        stream.createOutputStream().use { it.write(content.toByteArray(Charsets.ISO_8859_1)) }
        page.setContents(stream)
        return document
    }

    @Test
    fun aStreamThatAlsoHoldsThePagesOwnWordsIsNeverRemoved() {
        val watermark = "/Artifact <</Subtype /Watermark /FreePDF true>> BDC BT /F1 40 Tf 100 400 Td (DRAFT) Tj ET EMC"
        val body = "/P <</MCID 0>> BDC BT /F1 12 Tf 72 700 Td (Body text) Tj ET EMC"
        rawPage("$watermark $body").use { document ->
            assertFalse(Watermarks.remove(document, listOf(0)))
            assertTrue(text(document, page = 1).contains("Body text"))
            assertTrue(text(document, page = 1).contains("DRAFT"))
        }
        // The same section on its own, after the Q that resets the page, is a watermark.
        rawPage("Q Q $watermark").use { document ->
            assertTrue(Watermarks.remove(document, listOf(0)))
            assertFalse(text(document, page = 1).contains("DRAFT"))
            assertEquals(2, operands(document, 0, "Q").size)
            assertTrue(operands(document, 0, "BDC").isEmpty())
        }
    }

    @Test
    fun anotherToolsWatermarkIsLeftAlone() {
        rawPage("/Artifact <</Subtype /Watermark>> BDC BT /F1 40 Tf 100 400 Td (CONFIDENTIAL) Tj ET EMC").use { document ->
            assertFalse(Watermarks.remove(document, listOf(0)))
            assertTrue(text(document, page = 1).contains("CONFIDENTIAL"))
        }
    }

    @Test
    fun pagesSharingOneContentsArrayAreStampedApart() {
        sample().use { document ->
            PageEditor.insertBlank(document, 1)
            val first = document.getPage(0)
            val second = document.getPage(1)
            // Some generators point identical pages at one /Contents array.
            val shared = COSArray().apply { add(first.cosObject.getDictionaryObject(COSName.CONTENTS)) }
            first.cosObject.setItem(COSName.CONTENTS, shared)
            second.cosObject.setItem(COSName.CONTENTS, shared)
            Watermarks.addText(document, listOf(0), "COPY", WatermarkStyle(angle = 0f))
            assertTrue(text(document, page = 1).contains("COPY"))
            assertFalse(text(document, page = 2).contains("COPY"))
            assertEquals(1, shared.size())
        }
    }

    @Test
    fun refusesStylesAndTextThatDrawNothing() {
        sample().use { document ->
            assertThrows(IllegalArgumentException::class.java) { Watermarks.addText(document, listOf(0), "  ", WatermarkStyle()) }
            assertThrows(IllegalArgumentException::class.java) { Watermarks.addText(document, listOf(0), "X", WatermarkStyle(opacity = 0f)) }
            assertEquals(false, WatermarkStyle(angle = 120f).isValid)
        }
    }

    @Test
    fun writesAWatermarkedCopyForTheReview() {
        sample().use { document ->
            Watermarks.addText(document, listOf(0), "CONFIDENTIAL", WatermarkStyle(opacity = 0.3f, angle = 45f, size = 0.8f))
            // Nothing to assert here: the file is for looking at, rendered by CI with poppler.
            val file = File("build/outputs/qa").apply { mkdirs() }.resolve("watermarks.pdf")
            document.save(file)
            assertTrue(file.length() > 0)
        }
    }
}
