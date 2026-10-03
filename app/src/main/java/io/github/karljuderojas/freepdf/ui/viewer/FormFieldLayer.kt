package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.form.FormField

/**
 * Fill form: outlines each of the page's own form fields so they are easy to find, and reports
 * taps on them. Tiny boxes such as checkboxes get a finger-sized touch area around them.
 */
@Composable
fun FormFieldLayer(fields: List<FormField>, onTap: (FormField) -> Unit) {
    val outline = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize()) {
        fields.forEachIndexed { i, field ->
            val width = maxWidth * (field.box.right - field.box.left)
            val height = maxHeight * (field.box.bottom - field.box.top)
            val touchWidth = maxOf(width, MIN_TOUCH)
            val touchHeight = maxOf(height, MIN_TOUCH)
            Box(
                Modifier
                    .offset(maxWidth * field.box.left - (touchWidth - width) / 2, maxHeight * field.box.top - (touchHeight - height) / 2)
                    .size(touchWidth, touchHeight)
                    .testTag("form-field-${field.name}-$i")
                    .clickable { onTap(field) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width, height)
                        .background(outline.copy(alpha = 0.10f), RoundedCornerShape(2.dp))
                        .border(1.dp, outline.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/** What a tap on [field] does: checkboxes and radio buttons change at once, the rest ask. */
sealed interface FormTap {
    data class Set(val value: String?) : FormTap
    data object Ask : FormTap
    data object Nothing : FormTap
}

fun formTap(field: FormField): FormTap = when (field.kind) {
    FormField.Kind.Checkbox -> FormTap.Set(if (field.checked) null else field.onState)
    // A chosen radio button stays chosen; picking another one moves the choice.
    FormField.Kind.Radio -> if (field.checked || field.onState == null) FormTap.Nothing else FormTap.Set(field.onState)
    FormField.Kind.Text, FormField.Kind.Choice -> FormTap.Ask
}

/** Asks for a text field's value, or a dropdown's choice, starting from what is there now. */
@Composable
fun FormFieldDialog(field: FormField, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    if (field.kind == FormField.Kind.Choice) {
        ChoiceDialog(field, onDismiss, onSet)
        return
    }
    // Opens ready to type, with the cursor after what is there.
    var text by rememberSaveable(field.name, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(field.value, TextRange(field.value.length)))
    }
    val focus = remember { FocusRequester() }
    val submit = { onSet(if (field.multiline) text.text.trimEnd() else text.text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.label.ifBlank { stringResource(R.string.form_field_untitled) }) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = if (field.maxLength > 0 && it.text.length > field.maxLength) text else it },
                singleLine = !field.multiline,
                minLines = if (field.multiline) 3 else 1,
                keyboardOptions = KeyboardOptions(imeAction = if (field.multiline) ImeAction.Default else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("form-field-input"),
            )
            // A frame after the dialog opens, once its window can take focus.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                focus.requestFocus()
            }
        },
        confirmButton = {
            TextButton(onClick = submit) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChoiceDialog(field: FormField, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.label.ifBlank { stringResource(R.string.form_field_untitled) }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                field.options.forEach { option ->
                    val selected = option.value == field.value
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSet(option.value) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(option.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val MIN_TOUCH: Dp = 40.dp
