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
import androidx.compose.material3.OutlinedButton
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

/** Shorter passwords are more likely a slip than a choice; see R.string.password_too_short. */
private const val MIN_PASSWORD_LENGTH = 4

private enum class PasswordStep { Locked, Add, Change, Remove }

/**
 * The More mode's Password tool. A PDF that opens without a password is offered one; a locked
 * ([isProtected]) one can have its password changed or removed. [onSetPassword] gets the new
 * password, or "" to remove it.
 */
@Composable
fun PasswordDialog(isProtected: Boolean, onDismiss: () -> Unit, onSetPassword: (String) -> Unit) {
    var step by rememberSaveable { mutableStateOf(if (isProtected) PasswordStep.Locked else PasswordStep.Add) }
    when (step) {
        PasswordStep.Locked -> LockedDialog(
            onChange = { step = PasswordStep.Change },
            onRemove = { step = PasswordStep.Remove },
            onDismiss = onDismiss,
        )
        PasswordStep.Add, PasswordStep.Change -> NewPasswordDialog(
            changing = step == PasswordStep.Change,
            onDismiss = onDismiss,
            onConfirm = onSetPassword,
        )
        PasswordStep.Remove -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.password_remove_title)) },
            text = { Text(stringResource(R.string.password_remove_body)) },
            confirmButton = {
                TextButton(onClick = { onSetPassword("") }) { Text(stringResource(R.string.password_remove)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** For a PDF that already has a password: change it or remove it. */
@Composable
private fun LockedDialog(onChange: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.password_locked_body))
                Button(onClick = onChange, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.password_change_title))
                }
                OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.password_remove))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Asks for a new password twice, so a typo does not lock the user out of their own file. */
@Composable
private fun NewPasswordDialog(changing: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    // Only once the second entry can no longer turn into the first, so typing it is not an error.
    val mismatch = confirmation.isNotEmpty() && !password.startsWith(confirmation)
    val valid = password.length >= MIN_PASSWORD_LENGTH && confirmation == password
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text(stringResource(if (changing) R.string.password_change_title else R.string.password_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.password_add_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.password_too_short)) },
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            Text(stringResource(if (visible) R.string.password_hide else R.string.password_show))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("new-password-field"),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text(stringResource(R.string.password_confirm_label)) },
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.password_mismatch)) }
                    } else {
                        null
                    },
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(password) }),
                    modifier = Modifier.fillMaxWidth().testTag("confirm-password-field"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = valid) {
                Text(stringResource(if (changing) R.string.password_change_title else R.string.password_add_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
