package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Read mode's bottom bar: five labeled modes. */
@Composable
fun ModeBar(onModeSelected: (ViewerMode) -> Unit) {
    NavigationBar {
        ViewerMode.barModes.forEach { mode ->
            NavigationBarItem(
                selected = false,
                onClick = { onModeSelected(mode) },
                icon = { mode.icon?.let { Icon(it, contentDescription = null) } },
                label = { Text(stringResource(mode.label)) },
            )
        }
    }
}

/**
 * A mode's tool strip. Drawing tools are labeled and stay selected after use ("sticky tools");
 * Pages mode passes no selection because its tools act once. Progress is in docs/ROADMAP.md.
 */
@Composable
fun ToolStrip(mode: ViewerMode, selectedTool: Int?, onToolSelected: (Int) -> Unit) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            mode.tools.forEach { tool ->
                FilterChip(
                    selected = tool == selectedTool,
                    onClick = { onToolSelected(tool) },
                    label = { Text(stringResource(tool)) },
                )
            }
        }
    }
}
