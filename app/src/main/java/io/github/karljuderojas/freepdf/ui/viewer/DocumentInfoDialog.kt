package io.github.karljuderojas.freepdf.ui.viewer

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.info.DocumentInfo
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/** More > Document info: the open PDF's details, read-only, titled with its file [name]. */
@Composable
fun DocumentInfoDialog(name: String, info: DocumentInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val yes = stringResource(R.string.info_yes)
    val no = stringResource(R.string.info_no)
    val rows = listOf(
        R.string.info_title to info.title,
        R.string.info_author to info.author,
        R.string.info_subject to info.subject,
        R.string.info_keywords to info.keywords,
        R.string.info_created to info.created?.let(::formatDate),
        R.string.info_modified to info.modified?.let(::formatDate),
        R.string.info_pages to info.pageCount.toString(),
        R.string.info_page_size to when {
            info.pageCount == 0 -> null
            info.pageSize == null -> stringResource(R.string.info_mixed_sizes)
            else -> DocumentInfo.sizeLabel(info.pageSize, metric = usesMetric())
        },
        R.string.info_file_size to Formatter.formatShortFileSize(context, info.fileSizeBytes),
        R.string.info_creator to info.creator,
        R.string.info_producer to info.producer,
        R.string.info_pdf_version to info.pdfVersion,
        R.string.info_protected to if (info.encrypted) yes else no,
        R.string.info_form to if (info.hasFormFields) yes else no,
        R.string.info_signatures to
            if (info.signatureCount > 0) info.signatureCount.toString() else stringResource(R.string.info_none),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).testTag("document-info"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rows.forEach { (label, value) ->
                    if (value != null) {
                        Column {
                            Text(
                                stringResource(label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(value, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.info_close)) }
        },
    )
}

/** The phone's medium date and short time, such as "Oct 3, 2026, 3:00 PM". */
private fun formatDate(instant: Instant): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date.from(instant))

/** Inches for the few countries that use US paper; millimetres everywhere else. */
private fun usesMetric() = Locale.getDefault().country !in setOf("US", "LR", "MM")
