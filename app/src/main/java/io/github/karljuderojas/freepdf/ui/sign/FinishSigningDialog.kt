package io.github.karljuderojas.freepdf.ui.sign

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R

/** What Finish does besides recording the signer: see [FinishSigningDialog]. */
data class FinishOptions(val lock: Boolean, val seal: Boolean, val share: Boolean)

/**
 * The last step of signing: the signer's name, whether signatures are locked into the page or
 * kept editable, the digital seal, and consent to sign electronically. Saving stays off until
 * there is a name and the consent box is ticked, so the signed copy always records an explicit
 * agreement. A seal needs locked signatures, because moving one later would break it.
 */
@Composable
fun FinishSigningDialog(
    initialName: String,
    initialLock: Boolean = true,
    onDismiss: () -> Unit,
    onFinish: (name: String, consentText: String, options: FinishOptions) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var lock by rememberSaveable { mutableStateOf(initialLock) }
    var seal by rememberSaveable { mutableStateOf(true) }
    var consent by rememberSaveable { mutableStateOf(false) }
    val consentText = stringResource(R.string.finish_consent)
    val canFinish = consent && name.isNotBlank()

    fun finish(share: Boolean) = onFinish(name.trim(), consentText, FinishOptions(lock, seal && lock, share))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finish_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.finish_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().testTag("signer-name"),
                )
                Column(Modifier.selectableGroup()) {
                    ChoiceRow(
                        selected = lock,
                        onSelect = { lock = true },
                        tag = "lock",
                        title = R.string.finish_lock,
                        detail = R.string.finish_lock_detail,
                    )
                    ChoiceRow(
                        selected = !lock,
                        onSelect = { lock = false },
                        tag = "keep-editable",
                        title = R.string.finish_editable,
                        detail = R.string.finish_editable_detail,
                    )
                }
                CheckRow(checked = seal && lock, onChange = { seal = it }, tag = "seal", enabled = lock) {
                    Column {
                        Text(stringResource(R.string.finish_seal), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (lock) R.string.finish_seal_detail else R.string.finish_seal_needs_lock),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CheckRow(checked = consent, onChange = { consent = it }, tag = "consent") {
                    Text(consentText, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.finish_record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { finish(share = false) }, enabled = canFinish) {
                    Text(stringResource(R.string.finish_save))
                }
                Button(onClick = { finish(share = true) }, enabled = canFinish) {
                    Text(stringResource(R.string.finish_save_share))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** One option of a choice, with a line explaining it; the whole row selects it. */
@Composable
private fun ChoiceRow(selected: Boolean, onSelect: () -> Unit, tag: String, @StringRes title: Int, @StringRes detail: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp, top = 2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A checkbox whose whole row, label included, toggles it. */
@Composable
private fun CheckRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp, top = 2.dp)) { label() }
    }
}
