package io.github.karljuderojas.freepdf.pdf.sign

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Finding the places to sign. Runs under Robolectric so FreePdfApp initialises PdfBox-Android's resources. */
@RunWith(AndroidJUnit4::class)
class SignatureFieldsTest {

    @Test
    fun findsTheTwoSignatureLinesOfTheSampleAgreement() {
        val fields = PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf")).use { SignatureFields.find(it) }

        // Page 2 has "Provider signature (Northwind Studio)" and "Client signature (Lakeside Bakery)"
        // under drawn lines; its "Signatures" heading and the "Date" labels are not places to sign.
        assertEquals(fields.toString(), 2, fields.size)
        fields.forEach { assertEquals(1, it.page) }
        fields.forEach { assertEquals(SignField.Source.TextCue, it.source) }
        val (provider, client) = fields
        assertTrue("top to bottom: $fields", provider.box.top < client.box.top)
        // Labels start 72 pt in; their tops are at about 263 pt and 343 pt down the 792 pt page.
        listOf(provider to 263f, client to 343f).forEach { (field, labelTop) ->
            assertEquals(72f / 612f, field.box.left, 0.005f)
            assertTrue("above its label: $field", field.box.bottom < labelTop / 792f && field.box.bottom > (labelTop - 12f) / 792f)
            assertEquals(36f / 792f, field.box.bottom - field.box.top, 0.002f)
            assertEquals(200f / 612f, field.box.right - field.box.left, 0.002f)
        }
    }

    @Test
    fun anEmptySignatureFieldIsFoundAndHidesTheLabelUnderIt() {
        withSignatureField(signed = false) { document ->
            val fields = SignatureFields.find(document)
            assertEquals(fields.toString(), 1, fields.size)
            val field = fields.single()
            assertEquals(SignField.Source.FormField, field.source)
            assertEquals(0, field.page)
            // The widget is 72..272 pt across and 612..648 pt up a 612 x 792 pt page.
            assertEquals(72f / 612f, field.box.left, 0.001f)
            assertEquals(272f / 612f, field.box.right, 0.001f)
            assertEquals((792f - 648f) / 792f, field.box.top, 0.001f)
            assertEquals((792f - 612f) / 792f, field.box.bottom, 0.001f)
        }
    }

    @Test
    fun aSignedFieldIsNotAPlaceToSignButItsLabelStillIs() {
        withSignatureField(signed = true) { document ->
            val fields = SignatureFields.find(document)
            assertEquals(fields.toString(), listOf(SignField.Source.TextCue), fields.map { it.source })
        }
    }

    @Test
    fun aRunOfUnderscoresIsWhereTheSignatureGoes() {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            text(document, page, 72f, 400f, "Signature: ____________________")
            text(document, page, 72f, 300f, "Signatures")
            text(document, page, 72f, 200f, "Your signature on this form confirms that everything you have told us above is true.")
            val fields = SignatureFields.find(document)
            assertEquals(fields.toString(), 1, fields.size)
            val box = fields.single().box
            // The underscores start after "Signature: " and end on the line at 400 pt up.
            assertTrue(box.toString(), box.left > 72f / 612f + 0.05f)
            assertEquals((792f - 400f) / 792f, box.bottom, 0.005f)
        }
    }

    @Test
    fun aLabelSetApartFromItsTypedLineIsSignedOnTheLineNotOverTheTextAbove() {
        // witness.pdf: "IN WITNESS WHEREOF, ... sealed and signed:" on a baseline 507 pt up, then
        // "Signature:" at 72 pt across and 483 pt up, with its underscores from 170 to 420 pt across.
        val fields = PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/witness.pdf")).use { SignatureFields.find(it) }
        assertEquals(fields.toString(), 1, fields.size)
        val box = fields.single().box
        assertEquals(170f / 612f, box.left, 0.005f)
        assertEquals(420f / 612f, box.right, 0.005f)
        assertEquals((792f - 483f + 2f) / 792f, box.bottom, 0.004f)
        assertTrue("not over the IN WITNESS line: $box", box.top >= (792f - 507f) / 792f)
        assertTrue("tall enough to sign in: $box", (box.bottom - box.top) * 792f >= 16f)
    }

    @Test
    fun aDrawnLineAfterTheLabelIsWhereTheSignatureGoes() {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            text(document, page, 72f, 424f, "The parties have signed this agreement on the date below:")
            text(document, page, 72f, 400f, "Signature:")
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { stream ->
                stream.setLineWidth(0.75f)
                stream.moveTo(170f, 398f)
                stream.lineTo(420f, 398f)
                stream.stroke()
            }
            val fields = SignatureFields.find(document)
            assertEquals(fields.toString(), 1, fields.size)
            val box = fields.single().box
            assertEquals(170f / 612f, box.left, 0.005f)
            assertEquals(420f / 612f, box.right, 0.005f)
            assertEquals((792f - 398f + 2f) / 792f, box.bottom, 0.004f)
            assertTrue("not over the sentence above: $box", box.top >= (792f - 424f) / 792f)
        }
    }

    @Test
    fun withNoLineAndTextJustAboveTheBoxGoesAfterTheLabel() {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            text(document, page, 72f, 412f, "Please read everything above before you put your name to it.")
            text(document, page, 72f, 400f, "Signature:")
            val fields = SignatureFields.find(document)
            assertEquals(fields.toString(), 1, fields.size)
            val box = fields.single().box
            // "Signature:" in 10 pt Helvetica ends about 117 pt across; the box starts after it, on its row.
            assertTrue("after the label: $box", box.left > 117f / 612f)
            assertEquals((792f - 400f + 2f) / 792f, box.bottom, 0.004f)
        }
    }

    /** A page with "Sign here" at 600 pt up and a signature field over the space just above it. */
    private fun withSignatureField(signed: Boolean, check: (PDDocument) -> Unit) {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            text(document, page, 72f, 600f, "Sign here")
            val form = PDAcroForm(document)
            document.documentCatalog.acroForm = form
            val field = PDSignatureField(form)
            field.partialName = "Signature1"
            val widget = field.widgets[0]
            widget.rectangle = PDRectangle(72f, 612f, 200f, 36f)
            widget.page = page
            page.annotations = listOf<PDAnnotation>(widget)
            form.fields = listOf<PDField>(field)
            if (signed) field.cosObject.setItem(COSName.V, PDSignature().cosObject)
            check(document)
        }
    }

    private fun text(document: PDDocument, page: PDPage, x: Float, y: Float, text: String) {
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 10f)
            stream.newLineAtOffset(x, y)
            stream.showText(text)
            stream.endText()
        }
    }
}
