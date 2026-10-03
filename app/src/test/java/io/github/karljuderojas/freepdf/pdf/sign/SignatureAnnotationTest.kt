package io.github.karljuderojas.freepdf.pdf.sign

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.karljuderojas.freepdf.pdf.PdfPoint
import io.github.karljuderojas.freepdf.pdf.edit.PageEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

/** Signatures kept editable as stamp annotations, and locking them into the page at Finish. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignatureAnnotationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun sample(): PDDocument =
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    @Test
    fun placesAnEditableStampFittedOverTheTap() {
        val bytes = sample().use { document ->
            // A 300 x 100 image fitted into 160 x 56 points is 160 x 53.3, centred on x = 200.
            SignatureAnnotation.add(document, 1, signatureBitmap(), PdfPoint(200f, 150f), maxWidth = 160f, maxHeight = 56f)
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        PDDocument.load(bytes).use { document ->
            val annotation = document.getPage(1).annotations.single()
            assertTrue(SignatureAnnotation.isSignature(annotation))
            assertNotNull(annotation.normalAppearanceStream)
            val rect = annotation.rectangle
            assertEquals(120f, rect.lowerLeftX, 0.1f)
            assertEquals(150f, rect.lowerLeftY, 0.1f)
            assertEquals(160f, rect.width, 0.1f)
            assertEquals(53.3f, rect.height, 0.1f)
        }
    }

    @Test
    fun onARotatedPageTheStampStandsUpright() {
        sample().use { document ->
            PageEditor.rotate(document, 1, 90)
            SignatureAnnotation.add(document, 1, signatureBitmap(), PdfPoint(300f, 400f), maxWidth = 160f, maxHeight = 56f)
            // Turned with the page, the stored box is tall and narrow so that it shows wide.
            val rect = document.getPage(1).annotations.single().rectangle
            assertEquals(53.3f, rect.width, 0.1f)
            assertEquals(160f, rect.height, 0.1f)
        }
    }

    @Test
    fun lockingDrawsSignaturesIntoThePageAndLeavesOtherMarks() {
        sample().use { document ->
            val page = document.getPage(1)
            val before = page.resources.xObjectNames.count()
            SignatureAnnotation.add(document, 1, signatureBitmap(), PdfPoint(200f, 150f), maxWidth = 160f, maxHeight = 56f)
            page.annotations = page.annotations + PDAnnotationRubberStamp()

            assertEquals(1, SignatureAnnotation.lock(document))
            assertEquals(1, page.annotations.size)
            assertFalse(SignatureAnnotation.isSignature(page.annotations.single()))
            assertEquals(before + 1, page.resources.xObjectNames.count())
        }
    }

    @Test
    fun signedCopyLocksUnlessAskedToKeepSignaturesEditable() {
        val source = temp.newFile("agreement.pdf")
        sample().use { document ->
            SignatureAnnotation.add(document, 1, signatureBitmap(), PdfPoint(200f, 150f), maxWidth = 160f, maxHeight = 56f)
            document.save(source)
        }
        val locked = signedCopy(source, lock = true)
        PDDocument.load(locked).use { document ->
            assertTrue(document.getPage(1).annotations.none(SignatureAnnotation::isSignature))
            assertFalse(auditText(document).contains("keep the signatures editable"))
        }
        val editable = signedCopy(source, lock = false)
        PDDocument.load(editable).use { document ->
            assertTrue(document.getPage(1).annotations.any(SignatureAnnotation::isSignature))
            assertTrue(auditText(document).contains("keep the signatures editable"))
        }
    }

    private fun signedCopy(source: File, lock: Boolean): ByteArray {
        val signedAt = Instant.parse("2026-10-03T15:04:00Z")
        val trail = AuditTrail(
            documentName = "agreement.pdf",
            originalSha256 = "0".repeat(64),
            events = listOf(AuditEvent(AuditEvent.Type.Completed, "Dana Whitfield", signedAt)),
        ).withSigner(
            SignerRecord(
                name = "Dana Whitfield",
                email = null,
                signedAt = signedAt,
                method = SignatureMethod.Drawn,
                reason = null,
                device = "Google Pixel 7, Android 16",
                consentText = "I agree to sign this document electronically.",
            ),
        )
        val output = ByteArrayOutputStream()
        SignedCopy.write(source, trail, "Dana Whitfield", identity = null, output, File(temp.root, "scratch.pdf"), lock = lock)
        return output.toByteArray()
    }

    private fun auditText(document: PDDocument): String =
        PDFTextStripper().apply { startPage = document.numberOfPages; endPage = document.numberOfPages }
            .getText(document).replace(Regex("\\s+"), " ")

    private fun signatureBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 63, 168); strokeWidth = 6f; style = Paint.Style.STROKE }
        Canvas(bitmap).drawLine(20f, 70f, 280f, 60f, paint)
        return bitmap
    }
}
