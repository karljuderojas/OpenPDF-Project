package io.github.karljuderojas.freepdf.pdf.annotate

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Rubber stamps. Also writes build/outputs/qa/textbox-and-stamps.pdf, which CI renders with
 * poppler and posts with the screenshots, since PDFium cannot render under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class StampsTest {

    private fun sample(): PDDocument =
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    @Test
    fun addsAStampWithNameLabelAndAppearance() {
        sample().use { document ->
            val stamp = Stamps.add(document, 0, PdfPoint(300f, 400f), Stamps.Kind.Approved, author = "Dana")

            assertEquals(PDAnnotationRubberStamp.SUB_TYPE, stamp.subtype)
            assertEquals(PDAnnotationRubberStamp.NAME_APPROVED, stamp.name)
            assertEquals("Approved", stamp.contents)
            assertEquals("Dana", stamp.titlePopup)

            val rect = stamp.rectangle
            // Centred on the point, wider than tall, and a sensible size for a 20pt label.
            assertEquals(300f, (rect.lowerLeftX + rect.upperRightX) / 2, 0.01f)
            assertEquals(400f, (rect.lowerLeftY + rect.upperRightY) / 2, 0.01f)
            assertTrue("width ${rect.width}", rect.width in 80f..200f)
            assertTrue("height ${rect.height}", rect.height in 25f..45f)

            val appearance = stamp.normalAppearanceStream
            assertNotNull(appearance)
            val content = String(appearance.contentStream.toByteArray(), Charsets.ISO_8859_1)
            assertTrue(content, content.contains("(APPROVED) Tj"))
            assertTrue(appearance.resources.fontNames.iterator().hasNext())
            assertTrue(appearance.resources.extGStateNames.iterator().hasNext())
        }
    }

    @Test
    fun restyleRedrawsTheStampInTheNewColour() {
        sample().use { document ->
            val stamp = Stamps.add(document, 0, PdfPoint(300f, 400f), Stamps.Kind.Approved)
            val before = String(stamp.normalAppearanceStream.contentStream.toByteArray(), Charsets.ISO_8859_1)
            val rect = stamp.rectangle

            assertTrue(Stamps.restyle(document, stamp, Annotator.Rgb(0f, 0f, 1f)))

            val after = String(stamp.normalAppearanceStream.contentStream.toByteArray(), Charsets.ISO_8859_1)
            assertNotEquals(before, after)
            assertTrue(after, after.contains("0 0 1 rg") && after.contains("0 0 1 RG"))
            assertTrue(after, after.contains("(APPROVED) Tj"))
            assertEquals(listOf(0f, 0f, 1f), stamp.color.components.toList())
            // Same place and size as before.
            assertEquals(rect.lowerLeftX, stamp.rectangle.lowerLeftX, 0.01f)
            assertEquals(rect.upperRightY, stamp.rectangle.upperRightY, 0.01f)
        }
    }

    @Test
    fun aStampFromAnotherAppIsLeftAlone() {
        sample().use { document ->
            val foreign = PDAnnotationRubberStamp().apply {
                name = "Reviewed"
                contents = "Reviewed by legal"
                rectangle = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(100f, 100f, 120f, 40f)
            }
            document.getPage(0).annotations.add(foreign)
            assertEquals(null, Stamps.kindOf(foreign))
            assertFalse(Stamps.restyle(document, foreign, Annotator.Rgb.Blue))
            assertEquals(null, foreign.color)
        }
    }

    @Test
    fun everyKindSurvivesSaveAndReload() {
        val bytes = sample().use { document ->
            Stamps.Kind.values().forEachIndexed { i, kind ->
                Stamps.add(document, 0, PdfPoint(200f, 700f - i * 50f), kind)
            }
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        PDDocument.load(bytes).use { document ->
            val stamps = document.getPage(0).annotations.filterIsInstance<PDAnnotationRubberStamp>()
            assertEquals(Stamps.Kind.values().map { it.pdfName }, stamps.map { it.name })
            assertEquals(Stamps.Kind.values().map { it.label }, stamps.map { it.contents })
            stamps.forEach { assertTrue(it.normalAppearanceStream.contentStream.toByteArray().isNotEmpty()) }
        }
    }

    @Test
    fun writesAQaPageWithATextBoxAndStamps() {
        sample().use { document ->
            TextBoxes.add(document, 0, PdfPoint(72f, 720f), "Please check the dates in section 2.\nZażółć gęślą jaźń · Привет", Annotator.Rgb.Blue, fontSize = 13f, author = "Dana")
            Stamps.add(document, 0, PdfPoint(150f, 600f), Stamps.Kind.Approved)
            Stamps.add(document, 0, PdfPoint(400f, 600f), Stamps.Kind.Confidential)
            Stamps.add(document, 0, PdfPoint(150f, 540f), Stamps.Kind.Draft)
            Stamps.add(document, 0, PdfPoint(400f, 540f), Stamps.Kind.Void)
            File("build/outputs/qa").apply { mkdirs() }.resolve("textbox-and-stamps.pdf").let { document.save(it) }
        }
    }
}
