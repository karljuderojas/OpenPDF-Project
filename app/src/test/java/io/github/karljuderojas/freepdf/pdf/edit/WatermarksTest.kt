package io.github.karljuderojas.freepdf.pdf.edit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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

    @Test
    fun textLandsOnlyOnTheNamedPages() {
        sample().use { document ->
            PageEditor.insertBlank(document, 1)
            Watermarks.addText(document, listOf(1), "CONFIDENTIAL", WatermarkStyle())
            assertTrue(text(document, page = 2).contains("CONFIDENTIAL"))
            assertFalse(text(document, page = 1).contains("CONFIDENTIAL"))
        }
    }

    @Test
    fun textIsDrawnFaintAndTurned() {
        sample().use { document ->
            Watermarks.addText(document, listOf(0), "DRAFT", WatermarkStyle(opacity = 0.25f, angle = 45f))
            val page = document.getPage(0)
            val content = page.contents.use { it.readBytes() }.toString(Charsets.ISO_8859_1)
            // 45 degrees turned: cos and sin are both about 0.7071 in the rotation matrix.
            assertTrue(content, content.contains("0.70710"))
            val states = page.resources.extGStateNames.map { page.resources.getExtGState(it) }
            assertTrue(states.any { it.nonStrokingAlphaConstant == 0.25f })
        }
    }

    @Test
    fun textKeepsItsWidthOnARotatedPage() {
        sample().use { document ->
            document.getPage(0).rotation = 90
            Watermarks.addText(document, listOf(0), "SAMPLE", WatermarkStyle(angle = 0f))
            assertTrue(text(document, page = 1).contains("SAMPLE"))
        }
    }

    @Test
    fun lettersOutsideLatinDoNotThrow() {
        sample().use { document ->
            Watermarks.addText(document, listOf(0), "СЕКРЕТНО", WatermarkStyle())
            assertTrue(text(document, page = 1).contains("СЕКРЕТНО"))
        }
    }

    @Test
    fun aPictureIsAddedToThePage() {
        sample().use { document ->
            val logo = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.argb(255, 26, 63, 168)) }
            Watermarks.addImage(document, listOf(0), logo, WatermarkStyle(size = 0.5f, angle = 0f))
            val images = document.getPage(0).resources.xObjectNames.map { document.getPage(0).resources.getXObject(it) }
            assertTrue(images.any { it is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject && it.width == 200 })
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
            File("build/outputs/qa").apply { mkdirs() }.resolve("watermarks.pdf").let { document.save(it) }
        }
    }
}
