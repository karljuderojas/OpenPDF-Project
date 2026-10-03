package io.github.karljuderojas.freepdf.pdf.form

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.edit.PdfText
import io.github.karljuderojas.freepdf.pdf.pdfToDisplay
import java.util.IdentityHashMap

/**
 * One box of a PDF's own form (an AcroForm widget) that the user can fill in. A radio group
 * has one per choice, all with the same [name].
 *
 * [value] is the text or the chosen option's export value. For checkboxes and radio buttons,
 * [onState] is what this box sets the field to when ticked, and [checked] whether it is.
 */
data class FormField(
    val name: String,
    val label: String,
    val kind: Kind,
    val page: Int,
    val box: DisplayRect,
    val value: String = "",
    val onState: String? = null,
    val checked: Boolean = false,
    val options: List<Option> = emptyList(),
    val multiline: Boolean = false,
    val maxLength: Int = 0,
) {
    enum class Kind { Text, Checkbox, Radio, Choice }

    /** A dropdown or list entry: [label] is shown, [value] is stored. */
    data class Option(val label: String, val value: String)
}

/** Reads and fills a PDF's form fields. Signature fields and buttons are left to other tools. */
object FormFiller {

    /** Every fillable box, in page order. Hidden and read-only fields are left out. */
    fun fields(document: PDDocument): List<FormField> {
        val form = document.documentCatalog.acroForm ?: return emptyList()
        val pageOf = IdentityHashMap<COSDictionary, Int>()
        document.pages.forEachIndexed { index, page ->
            page.annotations.filterIsInstance<PDAnnotationWidget>().forEach { pageOf[it.cosObject] = index }
        }
        val fields = ArrayList<FormField>()
        for (field in form.fieldTree) {
            if (field !is PDTerminalField || field.isReadOnly) continue
            val kind = when (field) {
                is PDTextField -> FormField.Kind.Text
                is PDCheckBox -> FormField.Kind.Checkbox
                is PDRadioButton -> FormField.Kind.Radio
                is PDChoice -> FormField.Kind.Choice
                else -> continue
            }
            val label = field.alternateFieldName?.takeIf { it.isNotBlank() } ?: field.partialName.orEmpty()
            val options = (field as? PDChoice)?.let { choice ->
                val shown = choice.optionsDisplayValues
                choice.optionsExportValues.mapIndexed { i, value -> FormField.Option(shown.getOrElse(i) { value }, value) }
            }.orEmpty()
            val value = when (field) {
                is PDChoice -> field.value.firstOrNull().orEmpty()
                is PDButton -> field.cosObject.getNameAsString(COSName.V).orEmpty()
                else -> field.valueAsString.orEmpty()
            }
            for (widget in field.widgets) {
                if (widget.isHidden || widget.isNoView) continue
                val pageIndex = pageOf[widget.cosObject] ?: widget.page?.let { document.pages.indexOf(it) }?.takeIf { it >= 0 } ?: continue
                val rect = widget.rectangle ?: continue
                val page = document.getPage(pageIndex)
                val crop = page.cropBox.let { PdfRect(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) }
                val onState = (field as? PDButton)?.let { onStateOf(widget) }
                fields += FormField(
                    name = field.fullyQualifiedName,
                    label = label,
                    kind = kind,
                    page = pageIndex,
                    box = pdfToDisplay(PdfRect(rect.lowerLeftX, rect.lowerLeftY, rect.upperRightX, rect.upperRightY), page.rotation, crop),
                    value = value,
                    onState = onState,
                    checked = onState != null && widget.appearanceState?.name == onState,
                    options = options,
                    multiline = (field as? PDTextField)?.isMultiline == true,
                    maxLength = (field as? PDTextField)?.maxLen?.coerceAtLeast(0) ?: 0,
                )
            }
        }
        return fields.sortedBy { it.page }
    }

    /**
     * Sets the field called [name]: the text for a text field, the option's value for a
     * dropdown or list, and for a checkbox or radio button the box's on state, or null to clear.
     */
    fun fill(document: PDDocument, name: String, value: String?) {
        val form = document.documentCatalog.acroForm ?: error("No form")
        val field = form.getField(name) ?: error("No field $name")
        // A PDF that also carries an XFA form shows that instead in some readers, with the old values.
        if (form.hasXFA()) form.xfa = null
        when (field) {
            is PDButton -> setButton(field, value)
            is PDTextField -> setText(document, form, field, value.orEmpty())
            is PDChoice -> setText(document, form, field, value.orEmpty())
            else -> error("Field $name cannot be filled")
        }
    }

    /** Ticks the box whose on state is [onState] and unticks the rest; null unticks all of them. */
    private fun setButton(field: PDButton, onState: String?) {
        val state = onState ?: OFF
        field.cosObject.setName(COSName.V, state)
        field.widgets.forEach { widget -> widget.setAppearanceState(if (onStateOf(widget) == state) state else OFF) }
    }

    /**
     * PdfBox draws the new value with the field's own font. That fails when the font is missing
     * from the form, or cannot show the letters typed (the standard Helvetica only covers
     * Western European ones), so then the bundled Liberation Sans is used instead.
     */
    private fun setText(document: PDDocument, form: PDAcroForm, field: PDTerminalField, value: String) {
        val set: (String) -> Unit = { if (field is PDChoice) field.setValue(it) else (field as PDTextField).value = it }
        if (runCatching { set(value) }.isSuccess) return
        val font = PDType0Font.load(document, PDFBoxResourceLoader.getStream(PdfText.LIBERATION_SANS), false)
        val resources = form.defaultResources ?: PDResources().also { form.defaultResources = it }
        resources.put(FALLBACK_FONT, font)
        val size = field.cosObject.getString(COSName.DA)?.let { FONT_SIZE.find(it)?.groupValues?.get(1) } ?: "0"
        field.cosObject.setString(COSName.DA, "/${FALLBACK_FONT.name} $size Tf 0 g")
        if (field is PDChoice) {
            set(value)
        } else {
            // Letters even Liberation Sans lacks (Chinese, Arabic) are drawn as "?", as with the Text tool,
            // but the field keeps what was typed.
            set(PdfText.printable(value, font))
            field.cosObject.setString(COSName.V, value)
        }
    }

    private fun onStateOf(widget: PDAnnotationWidget): String? =
        widget.appearance?.normalAppearance?.takeIf { it.isSubDictionary }?.subDictionary?.keys
            ?.map { it.name }?.firstOrNull { it != OFF }

    private const val OFF = "Off"
    private val FALLBACK_FONT = COSName.getPDFName("FreePdfSans")
    private val FONT_SIZE = Regex("""([\d.]+)\s+Tf""")
}
