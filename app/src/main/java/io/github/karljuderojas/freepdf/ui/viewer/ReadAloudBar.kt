package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.speech.ReadAloudState

/** What the read-aloud bar asks for besides starting. */
/** [Acknowledge] says the "not available" message has been shown; the bar never sends it. */
enum class ReadAloudCommand { Pause, Resume, Next, Previous, Stop, Acknowledge }

/** The controls shown while reading aloud: the sentence being spoken, skip back and on, pause or play, and Stop. */
@Composable
internal fun ReadAloudBar(state: ReadAloudState, onCommand: (ReadAloudCommand) -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding().testTag("read-aloud-bar")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.read_aloud_page, state.page + 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(state.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onCommand(ReadAloudCommand.Previous) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.read_aloud_previous))
                }
                FilledTonalButton(onClick = { onCommand(if (state.speaking) ReadAloudCommand.Pause else ReadAloudCommand.Resume) }) {
                    Text(stringResource(if (state.speaking) R.string.read_aloud_pause else R.string.read_aloud_play))
                }
                IconButton(onClick = { onCommand(ReadAloudCommand.Next) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.read_aloud_next))
                }
                TextButton(onClick = { onCommand(ReadAloudCommand.Stop) }) { Text(stringResource(R.string.read_aloud_stop)) }
            }
        }
    }
}
