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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.sign.CertificateInfo
import java.text.DateFormat

/**
 * Which certificate seals signed copies (this phone's own, or one the user imports from a .p12
 * file) and whether signatures get a trusted timestamp.
 */
@Composable
fun CertificateDialog(
    certificate: CertificateInfo?,
    timestampsOn: Boolean,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    onTimestampsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.certificate_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (certificate == null) {
                    Detail(stringResource(R.string.certificate_device), stringResource(R.string.certificate_device_detail))
                    Detail(stringResource(R.string.certificate_import), stringResource(R.string.certificate_import_detail))
                    OutlinedButton(onClick = onImport, modifier = Modifier.testTag("import-certificate")) {
                        Text(stringResource(R.string.certificate_import))
                    }
                } else {
                    val expires = DateFormat.getDateInstance(DateFormat.MEDIUM).format(certificate.expires)
                    Detail(certificate.name, stringResource(R.string.certificate_imported_detail, certificate.issuer, expires))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImport) { Text(stringResource(R.string.certificate_replace)) }
                        TextButton(onClick = onRemove) { Text(stringResource(R.string.certificate_remove)) }
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag("timestamps")
                        .toggleable(value = timestampsOn, role = Role.Switch, onValueChange = onTimestampsChange),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Detail(stringResource(R.string.timestamp_title), stringResource(R.string.timestamp_detail))
                    }
                    Switch(checked = timestampsOn, onCheckedChange = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/** Asks for the .p12 file's password. The password is used once and not stored. */
@Composable
fun CertificatePasswordDialog(onDismiss: () -> Unit, onImport: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.certificate_password_title)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.certificate_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(password.toCharArray()) }) { Text(stringResource(R.string.certificate_import_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Detail(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
