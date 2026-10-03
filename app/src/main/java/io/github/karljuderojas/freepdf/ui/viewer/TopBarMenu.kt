package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.settings.PageColors

/** One entry in the top bar's overflow menu. */
internal class TopBarMenuItem(val label: Int, val onClick: () -> Unit)

/**
 * The top bar's "More options" menu on a phone, where the less-used actions go so the title keeps
 * its one line. [items] come first, then the page colors to pick from.
 */
@Composable
internal fun TopBarMenu(items: List<TopBarMenuItem>, pageColors: PageColors, onPageColors: (PageColors) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("top-bar-menu")) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(stringResource(item.label)) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
            if (items.isNotEmpty()) HorizontalDivider()
            Text(
                stringResource(R.string.page_colors),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            PageColors.entries.forEach { colors ->
                DropdownMenuItem(
                    text = { Text(stringResource(colors.label)) },
                    leadingIcon = { RadioButton(selected = colors == pageColors, onClick = null) },
                    onClick = {
                        open = false
                        onPageColors(colors)
                    },
                )
            }
        }
    }
}

/** A top-bar title that never wraps: it stays on one line and trails off if it must. */
@Composable
internal fun TopBarTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier)
}
