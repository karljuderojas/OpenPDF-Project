package io.github.karljuderojas.freepdf.pdf.edit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.annotate.Annotator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The "Locked copy" share option. Also writes build/outputs/qa/locked-copy.pdf, which CI renders
 * with poppler and posts with the screenshots, since PDFium cannot render under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlattenerTest {

    @Test
    fun marksBecomePartOfThePage() {
        sample().use { document ->
            Annotator.markText(document, 0, listOf(PdfRect(72f, 640f, 400f, 656f)))
            Annotator.ink(document, 0, listOf((0..40).map { PdfPoint(100f + it * 8f, 520f + 20f * kotlin.math.sin(it / 3f)) }))
            Annotator.note(document, 0, PdfPoint(500f, 700f), "Check the dates")
            Annotator.shape(document, 0, PdfRect(80f, 420f, 320f, 480f))
            val formsBefore = xObjects(document)

            Flattener.flatten(document)

            assertEquals(0, document.getPage(0).annotations.size)
            // One form XObject per mark is now painted by the page's own content.
            assertEquals(formsBefore + 4, xObjects(document))

            val out = File("build/outputs/qa/locked-copy.pdf").apply { parentFile?.mkdirs() }
            document.save(out)
        }
        PDDocument.load(File("build/outputs/qa/locked-copy.pdf")).use { saved ->
            assertTrue(saved.getPage(0).annotations.isEmpty())
            assertEquals(2, saved.numberOfPages)
        }
    }

    @Test
    fun linksKeepWorking() {
        sample().use { document ->
            val page = document.getPage(1)
            page.annotations = page.annotations + PDAnnotationLink().apply {
                rectangle = PDRectangle(72f, 72f, 100f, 20f)
            }
            Annotator.note(document, 1, PdfPoint(300f, 300f), "Note")
            Flattener.flatten(document)
            val left = document.getPage(1).annotations
            assertEquals(1, left.size)
            assertTrue(left.single() is PDAnnotationLink)
        }
    }

    private fun xObjects(document: PDDocument) = document.getPage(0).resources.xObjectNames.count()

    private fun sample(): PDDocument =
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))
}
