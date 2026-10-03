package io.github.karljuderojas.freepdf.ui.sign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.karljuderojas.freepdf.pdf.sign.CertificateInfo

/**
 * The last step of signing: the signer's name, the digital seal, and consent to sign
 * electronically. Finish stays off until there is a name and the consent box is ticked, so the
 * signed copy always records an explicit agreement.
 */
@Composable
fun FinishSigningDialog(
    initialName: String,
    certificate: CertificateInfo? = null,
    onDismiss: () -> Unit,
    onFinish: (name: String, consentText: String, seal: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var seal by rememberSaveable { mutableStateOf(true) }
    var consent by rememberSaveable { mutableStateOf(false) }
    val consentText = stringResource(R.string.finish_consent)

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
                CheckRow(checked = seal, onChange = { seal = it }, tag = "seal") {
                    Column {
                        Text(stringResource(R.string.finish_seal), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (certificate == null) stringResource(R.string.finish_seal_detail)
                            else stringResource(R.string.finish_seal_detail_imported, certificate.name, certificate.issuer),
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
            Button(
                onClick = { onFinish(name.trim(), consentText, seal) },
                enabled = consent && name.isNotBlank(),
            ) { Text(stringResource(R.string.finish_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A checkbox whose whole row, label included, toggles it. */
@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, tag: String, label: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = 12.dp, top = 2.dp)) { label() }
    }
}
