package io.github.karljuderojas.freepdf.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.karljuderojas.freepdf.R

/** The four tabs at the bottom of the app, following the UX blueprint. */
enum class MainTab(@StringRes val label: Int) {
    Home(R.string.tab_home),
    Files(R.string.files_title),
    Tools(R.string.tab_tools),
    Settings(R.string.tab_settings);

    val icon: Painter
        @Composable get() = when (this) {
            Home -> rememberVectorPainter(Icons.Filled.Home)
            Files -> painterResource(R.drawable.ic_folder)
            Tools -> rememberVectorPainter(Icons.Filled.Build)
            Settings -> rememberVectorPainter(Icons.Filled.Settings)
        }
}

/**
 * The home level of the app: one tab's screen above the bottom tab bar. Each tab draws its own
 * top bar; [content] gets a modifier that keeps it clear of the tab bar. Each tab's saveable
 * state (scroll positions, search text, open menus) is kept while another tab is shown, so
 * coming back finds it as it was left.
 */
@Composable
fun AppShell(selected: MainTab, onSelect: (MainTab) -> Unit, content: @Composable (MainTab, Modifier) -> Unit) {
    val tabStates = rememberSaveableStateHolder()
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        tabStates.SaveableStateProvider(key = selected.name) {
            content(selected, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}
