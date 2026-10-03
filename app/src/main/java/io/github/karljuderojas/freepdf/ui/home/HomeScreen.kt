package io.github.karljuderojas.freepdf.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.ui.files.RecentRow
import io.github.karljuderojas.freepdf.ui.viewer.ViewerMode

/** Home's shortcuts. Each but All tools asks for a PDF and opens it in [mode], with [tool] picked. */
enum class QuickAction(@StringRes val label: Int, val icon: ImageVector, val mode: ViewerMode?, @StringRes val tool: Int? = null) {
    Sign(R.string.home_sign_pdf, Icons.Filled.Edit, ViewerMode.Sign, R.string.tool_signature),
    Annotate(R.string.mode_annotate, Icons.Filled.Create, ViewerMode.Annotate),
    Pages(R.string.home_organize_pages, Icons.AutoMirrored.Filled.List, ViewerMode.Pages),
    AllTools(R.string.home_all_tools, Icons.Filled.Build, null),
}

/** How many recent files Home lists; Files has the rest. */
const val HOME_RECENT_COUNT = 5

/** The Home tab: quick actions with Sign a PDF first, then the five most recent files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    recent: List<DocumentEntry>,
    onOpenFile: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onOpen: (DocumentEntry) -> Unit,
    onShare: (DocumentEntry) -> Unit,
    onForget: (DocumentEntry) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenFile,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.open_file)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickAction.entries.forEach { action ->
                        QuickActionCard(action, highlighted = action == QuickAction.Sign, Modifier.weight(1f)) {
                            onQuickAction(action)
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.home_recent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (recent.size > HOME_RECENT_COUNT) {
                        TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all)) }
                    }
                }
            }
            if (recent.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_no_recent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                    )
                }
            }
            items(recent.take(HOME_RECENT_COUNT), key = { it.uri }) { RecentRow(it, onOpen, onShare, onForget) }
        }
    }
}

@Composable
private fun QuickActionCard(action: QuickAction, highlighted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = if (highlighted) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(onClick = onClick, modifier = modifier, colors = colors) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(action.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(action.label),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                minLines = 2,
            )
        }
    }
}
