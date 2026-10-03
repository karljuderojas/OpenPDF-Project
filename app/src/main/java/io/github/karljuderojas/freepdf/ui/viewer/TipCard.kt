package io.github.karljuderojas.freepdf.ui.viewer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R

/** A one-time hint over the page, dark like a snackbar so it stands apart from the document. */
@Composable
fun TipCard(@StringRes text: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("tip"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
            ) {
                Text(stringResource(R.string.tip_got_it))
            }
        }
    }
}
