package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.edit.PdfDocuments.Restriction

/**
 * The More mode's Restrictions tool: lists what the PDF's permissions hold back, and, if any
 * apply, asks for the owner password to lift them. [onRemove] gets the password and a callback
 * to run if it turns out not to be the owner password; the caller closes the dialog on success.
 */
@Composable
fun RestrictionsDialog(restrictions: List<Restriction>, onDismiss: () -> Unit, onRemove: (String, wrong: () -> Unit) -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    var wrong by rememberSaveable { mutableStateOf(false) }
    if (!asking) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.restrictions_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (restrictions.isEmpty()) {
                        Text(stringResource(R.string.restrictions_none))
                    } else {
                        Text(stringResource(R.string.restrictions_body))
                        restrictions.forEach { Text("• " + stringResource(it.label())) }
                    }
                }
            },
            confirmButton = {
                if (restrictions.isEmpty()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                } else {
                    Button(onClick = { asking = true }) { Text(stringResource(R.string.restrictions_remove)) }
                }
            },
            dismissButton = if (restrictions.isEmpty()) null else {
                { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
            },
        )
    } else {
        OwnerPasswordDialog(
            wrong = wrong,
            onDismiss = onDismiss,
            onConfirm = { password ->
                wrong = false
                onRemove(password) { wrong = true }
            },
        )
    }
}

@Composable
private fun OwnerPasswordDialog(wrong: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.restrictions_owner_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.restrictions_owner_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.restrictions_owner_label)) },
                    singleLine = true,
                    isError = wrong,
                    supportingText = if (wrong) {
                        { Text(stringResource(R.string.restrictions_owner_wrong)) }
                    } else {
                        null
                    },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onConfirm(password) }),
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            Text(stringResource(if (visible) R.string.password_hide else R.string.password_show))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("owner-password-field"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.restrictions_remove))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun Restriction.label() = when (this) {
    Restriction.Print -> R.string.restriction_print
    Restriction.Copy -> R.string.restriction_copy
    Restriction.Edit -> R.string.restriction_edit
    Restriction.Annotate -> R.string.restriction_annotate
    Restriction.FillForms -> R.string.restriction_fill_forms
    Restriction.Assemble -> R.string.restriction_assemble
}
