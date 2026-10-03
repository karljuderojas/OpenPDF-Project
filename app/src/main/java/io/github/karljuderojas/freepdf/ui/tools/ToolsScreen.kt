package io.github.karljuderojas.freepdf.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R

/**
 * The Tools tab: every working tool in one searchable list, grouped like the viewer's mode bar.
 * Search also matches other words for each tool ("tick" finds Checkmark). Picking a tool asks for
 * a PDF and opens it in that tool's mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsContent(
    onToolPicked: (ToolEntry) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val resources = LocalResources.current
    val searchable = remember(resources) {
        toolCatalog.map { tool ->
            tool to buildList {
                add(resources.getString(tool.label))
                add(resources.getString(tool.mode.label))
                tool.synonyms?.let { addAll(resources.getString(it).split(',')) }
            }
        }
    }
    val shown = searchable.filter { (_, names) -> matchesToolQuery(query, names) }.map { it.first }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_tools)) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.tools_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("tools-search"),
                )
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.tools_none, query.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            shown.groupBy { it.mode }.forEach { (mode, tools) ->
                item(key = "header-${mode.name}") {
                    Text(
                        stringResource(mode.label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(tools, key = { "${it.mode.name}-${it.label}" }) { tool ->
                    ListItem(
                        headlineContent = { Text(stringResource(tool.label)) },
                        supportingContent = tool.description?.let { about -> @Composable { Text(stringResource(about)) } },
                        modifier = Modifier.clickable { onToolPicked(tool) },
                    )
                }
            }
        }
    }
}
