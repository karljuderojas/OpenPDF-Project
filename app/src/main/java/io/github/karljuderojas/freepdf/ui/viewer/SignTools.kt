package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore

/** The Sign tools that are wired up. Each one places something where the user taps. */
enum class SignTool(val label: Int, val hint: Int, val signatureKind: SignatureStore.Kind? = null) {
    Signature(R.string.tool_signature, R.string.sign_hint_signature, SignatureStore.Kind.Signature),
    Initials(R.string.tool_initials, R.string.sign_hint_initials, SignatureStore.Kind.Initials),
    Date(R.string.tool_date, R.string.sign_hint_date),
    Text(R.string.tool_text, R.string.sign_hint_text),
    Checkmark(R.string.tool_checkmark, R.string.sign_hint_checkmark);

    companion object {
        fun forLabel(label: Int?): SignTool? = entries.firstOrNull { it.label == label }
    }
}

/** Reports taps on one page as fractions of the page (0..1, origin top-left). */
@Composable
fun TapLayer(page: Int, onTap: (Offset) -> Unit) {
    // The gesture loop outlives recompositions, so always call the latest callback.
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        Modifier
            .fillMaxSize()
            .testTag("tap-layer-$page")
            .pointerInput(Unit) {
                detectTapGestures { currentOnTap(Offset(it.x / size.width, it.y / size.height)) }
            },
    )
}

/** Above the Sign tool strip: what a tap will do, with the saved signature and Redraw. */
@Composable
fun SignHint(tool: SignTool, savedImage: Bitmap?, onRedraw: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (savedImage != null) {
                Image(
                    savedImage.asImageBitmap(),
                    contentDescription = stringResource(tool.label),
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(max = 120.dp)
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .padding(4.dp),
                )
            }
            Text(
                stringResource(tool.hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (savedImage != null) {
                TextButton(onClick = onRedraw) { Text(stringResource(R.string.redraw)) }
            }
        }
    }
}
