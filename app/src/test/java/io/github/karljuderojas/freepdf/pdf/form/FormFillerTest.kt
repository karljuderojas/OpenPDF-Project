package io.github.karljuderojas.freepdf.pdf.form

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.karljuderojas.freepdf.pdf.form.FormField.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reading and filling the sample sign-up form (resources/sample/form.pdf, written by
 * scripts/make_sample_pdf.py). Runs under Robolectric so FreePdfApp initialises PdfBox-Android.
 *
 * Also writes build/outputs/qa/filled-form.pdf, which CI renders with poppler and posts with the
 * screenshots, to show the saved values as another PDF reader draws them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormFillerTest {

    private fun form(): PDDocument = PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/form.pdf"))

    @Test
    fun readsEveryFieldWithItsKindLabelAndPlace() {
        val fields = form().use { FormFiller.fields(it) }
        assertEquals(
            listOf("name", "email", "phone", "comments", "saturday", "sunday", "size", "size", "size", "size", "team", "date"),
            fields.map { it.name },
        )
        val byName = fields.associateBy { it.name }
        assertEquals(Kind.Text, byName.getValue("name").kind)
        assertEquals("Full name", byName.getValue("name").label)
        assertTrue(byName.getValue("comments").multiline)
        assertEquals(Kind.Checkbox, byName.getValue("saturday").kind)
        assertEquals("Yes", byName.getValue("saturday").onState)
        assertEquals(listOf("S", "M", "L", "XL"), fields.filter { it.kind == Kind.Radio }.map { it.onState })
        assertEquals(listOf("Kitchen", "Front of house", "Delivery"), byName.getValue("team").options.map { it.label })
        assertTrue(fields.none { it.checked })
        // The name box spans the page's text column, a sixth of the way down.
        val name = byName.getValue("name").box
        assertEquals(72f / 612f, name.left, 0.001f)
        assertEquals(540f / 612f, name.right, 0.001f)
        assertEquals((792f - 662f) / 792f, name.top, 0.001f)
    }

    @Test
    fun filledValuesSurviveSavingAndShowAsChecked() {
        val bytes = form().use { document ->
            fillAll(document)
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        PDDocument.load(bytes).use { document ->
            val fields = FormFiller.fields(document)
            val byName = fields.groupBy { it.name }
            assertEquals("Dana Whitfield", byName.getValue("name").single().value)
            assertEquals("dana@example.com", byName.getValue("email").single().value)
            assertTrue(byName.getValue("saturday").single().checked)
            assertFalse(byName.getValue("sunday").single().checked)
            // Choosing L after M leaves only L chosen.
            assertEquals(listOf("L"), byName.getValue("size").filter { it.checked }.map { it.onState })
            assertEquals("L", byName.getValue("size").first().value)
            assertEquals("Front of house", byName.getValue("team").single().value)
            assertEquals("Happy to help with setup.\nI can bring a van.", byName.getValue("comments").single().value)
            // Each value has its own drawing, so readers that do not redraw fields still show it.
            val name = document.documentCatalog.acroForm.getField("name")
            assertTrue(name.widgets.single().appearance?.normalAppearance != null)
        }
    }

    @Test
    fun untickingAndClearing() {
        form().use { document ->
            FormFiller.fill(document, "sunday", "Yes")
            FormFiller.fill(document, "sunday", null)
            FormFiller.fill(document, "size", "S")
            FormFiller.fill(document, "size", null)
            FormFiller.fill(document, "email", "a@b.c")
            FormFiller.fill(document, "email", "")
            val fields = FormFiller.fields(document)
            assertTrue(fields.none { it.checked })
            assertEquals("", fields.first { it.name == "email" }.value)
        }
    }

    @Test
    fun lettersOutsideHelveticaAreKeptAndDrawn() {
        form().use { document ->
            FormFiller.fill(document, "name", "Łukasz Żółć")
            FormFiller.fill(document, "phone", "Νίκος")
            // Liberation Sans has no Chinese; the drawing shows "?" but the field keeps the text.
            FormFiller.fill(document, "comments", "王小明")
            val values = FormFiller.fields(document).associate { it.name to it.value }
            assertEquals("Łukasz Żółć", values["name"])
            assertEquals("Νίκος", values["phone"])
            assertEquals("王小明", values["comments"])
        }
    }

    @Test
    fun aFormWithoutItsFontStillFills() {
        form().use { document ->
            document.documentCatalog.acroForm.defaultResources = null
            FormFiller.fill(document, "name", "Dana Whitfield")
            assertEquals("Dana Whitfield", document.documentCatalog.acroForm.getField("name").valueAsString)
        }
    }

    @Test
    fun readOnlyFieldsAreLeftOutAndPlainPdfsHaveNone() {
        form().use { document ->
            document.documentCatalog.acroForm.getField("phone").isReadOnly = true
            assertTrue(FormFiller.fields(document).none { it.name == "phone" })
        }
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf")).use {
            assertTrue(FormFiller.fields(it).isEmpty())
        }
    }

    @Test
    fun writesTheFilledFormForReview() {
        form().use { document ->
            fillAll(document)
            File("build/outputs/qa").apply { mkdirs() }.resolve("filled-form.pdf").let { document.save(it) }
            assertTrue(document.documentCatalog.acroForm.getField("size").cosObject.getNameAsString(COSName.V) == "L")
        }
    }

    private fun fillAll(document: PDDocument) {
        FormFiller.fill(document, "name", "Dana Whitfield")
        FormFiller.fill(document, "email", "dana@example.com")
        FormFiller.fill(document, "phone", "555 0142")
        FormFiller.fill(document, "saturday", "Yes")
        FormFiller.fill(document, "size", "M")
        FormFiller.fill(document, "size", "L")
        FormFiller.fill(document, "team", "Front of house")
        FormFiller.fill(document, "comments", "Happy to help with setup.\nI can bring a van.")
        FormFiller.fill(document, "date", "Oct 3, 2026")
    }
}
