package io.github.karljuderojas.freepdf.ui.sign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.sign.SignatureReport
import io.github.karljuderojas.freepdf.pdf.sign.SignaturesVerdict
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/**
 * The slim strip shown over a signed PDF: who signed and whether the file changed since. Tapping
 * it opens [SignatureDetailsDialog].
 */
@Composable
fun SignatureBanner(reports: List<SignatureReport>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val verdict = SignaturesVerdict.of(reports) ?: return
    val signers = signerSummary(reports)
    val (text, icon, good) = when (verdict) {
        SignaturesVerdict.Unchanged -> Triple(stringResource(R.string.verify_unchanged, signers), Icons.Filled.CheckCircle, true)
        SignaturesVerdict.ChangedAfterSigning -> Triple(stringResource(R.string.verify_changed, signers), Icons.Filled.Warning, false)
        SignaturesVerdict.Invalid -> Triple(stringResource(R.string.verify_invalid), Icons.Filled.Warning, false)
        SignaturesVerdict.CannotCheck -> Triple(stringResource(R.string.verify_cannot_check, signers), Icons.Filled.Info, false)
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (good) colors.secondaryContainer else colors.errorContainer,
        contentColor = if (good) colors.onSecondaryContainer else colors.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("signature-banner"),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.verify_details), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Each signature in the file, with what was checked and what it shows. */
@Composable
fun SignatureDetailsDialog(reports: List<SignatureReport>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.verify_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                reports.forEachIndexed { index, report ->
                    if (index > 0) HorizontalDivider()
                    SignatureDetails(report, signedAgainLater = index < reports.lastIndex && reports.last().coversWholeFile)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun SignatureDetails(report: SignatureReport, signedAgainLater: Boolean) {
    val documentTimestamp = report.kind == SignatureReport.Kind.DocumentTimestamp
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (documentTimestamp) stringResource(R.string.verify_document_timestamp)
            else report.signer ?: stringResource(R.string.verify_unnamed),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        when (report.integrity) {
            SignatureReport.Integrity.Intact -> Check(true, stringResource(R.string.verify_intact))
            SignatureReport.Integrity.Broken -> Check(false, stringResource(R.string.verify_broken))
            else -> Check(null, stringResource(R.string.verify_unsupported))
        }
        when {
            report.coversWholeFile -> Check(true, stringResource(R.string.verify_whole_file))
            signedAgainLater -> Check(null, stringResource(R.string.verify_signed_again))
            else -> Check(false, stringResource(R.string.verify_added_after))
        }
        if (!documentTimestamp && report.intact) {
            val issuer = report.issuer ?: stringResource(R.string.verify_unnamed)
            when (report.trust) {
                SignatureReport.Trust.Trusted -> Check(true, stringResource(R.string.verify_trusted, issuer))
                SignatureReport.Trust.SelfSigned -> Check(null, stringResource(R.string.verify_self_signed))
                SignatureReport.Trust.Unknown -> Check(null, stringResource(R.string.verify_unknown_issuer, issuer))
            }
            if (!report.certificateValidAtSigning) Check(false, stringResource(R.string.verify_expired))
        }
        val stamp = report.timestamp
        when {
            stamp != null && stamp.valid -> Check(
                true,
                stringResource(R.string.verify_timestamp, stamp.authority ?: stringResource(R.string.verify_unnamed), format(stamp.time)),
            )
            stamp != null -> Check(false, stringResource(R.string.verify_timestamp_invalid))
            report.signingTime != null -> Check(null, stringResource(R.string.verify_signed_at, format(report.signingTime)))
        }
        report.reason?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.verify_reason, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One finding: a tick when [ok], a warning when not, plain information when null. */
@Composable
private fun Check(ok: Boolean?, text: String) {
    val (icon: ImageVector, tint: Color) = when (ok) {
        true -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        false -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
        null -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun signerSummary(reports: List<SignatureReport>): String {
    val names = reports.filter { it.kind != SignatureReport.Kind.DocumentTimestamp }
        .map { it.signer ?: stringResource(R.string.verify_unnamed) }
        .distinct()
        .ifEmpty { listOf(stringResource(R.string.verify_unnamed)) }
    return if (names.size == 1) names.first() else stringResource(R.string.verify_signers_more, names.first(), names.size - 1)
}

private fun format(instant: Instant): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date.from(instant))
