package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.sign.SignField
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore

/** The places to sign in [ready] with a signature in them, counting [stamps] still being placed or moved. */
fun signedPlaces(ready: ViewerState.Ready, stamps: List<PlacedStamp>): Set<Int> {
    val placed = stamps.filter { (it.content as? StampContent.Signature)?.kind == SignatureStore.Kind.Signature }
    return ready.signedFields + ready.signFields.indices.filter { i ->
        placed.any { ready.signFields[i].covers(it.page, it.box.left + it.box.width / 2, it.box.top + it.box.height / 2) }
    }
}

/**
 * Marks the places to sign on one page. [fields] pairs each place with its index in
 * [ViewerState.Ready.signFields]; [current] is the one Next field went to, drawn stronger.
 * Only the boxes take taps, so a tap anywhere else still reaches the Sign tool underneath.
 */
@Composable
fun SignFieldLayer(fields: List<Pair<Int, SignField>>, current: Int?, onTap: (Int) -> Unit) {
    val colour = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(4.dp)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        fields.forEach { (index, field) ->
            val box = field.box
            val emphasised = index == current
            Box(
                Modifier
                    .offset(maxWidth * box.left, maxHeight * box.top)
                    .size(maxWidth * (box.right - box.left), maxHeight * (box.bottom - box.top))
                    .background(colour.copy(alpha = if (emphasised) 0.16f else 0.08f), shape)
                    .border(if (emphasised) 2.dp else 1.dp, colour.copy(alpha = if (emphasised) 1f else 0.7f), shape)
                    .testTag("sign-field-$index")
                    .clickable { onTap(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.sign_field_tag),
                    style = MaterialTheme.typography.labelSmall,
                    color = colour,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * Above the Sign tools when the document asks for signatures: how many places are left, and
 * Next field to go to the next one. Once all are signed it says so; Finish is in the top bar.
 */
@Composable
fun SignFieldsBanner(remaining: Int, onNext: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("sign-fields-banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (remaining == 0) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(
                if (remaining == 0) {
                    stringResource(R.string.sign_fields_done)
                } else {
                    LocalResources.current.getQuantityString(R.plurals.sign_fields_remaining, remaining, remaining)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).padding(vertical = if (remaining == 0) 10.dp else 0.dp),
            )
            if (remaining > 0) {
                Button(onClick = onNext) { Text(stringResource(R.string.sign_field_next)) }
            }
        }
    }
}
