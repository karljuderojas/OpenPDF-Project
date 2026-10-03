package io.github.karljuderojas.freepdf.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.settings.PageColors
import io.github.karljuderojas.freepdf.settings.SpeechRate
import io.github.karljuderojas.freepdf.settings.ThemeChoice
import io.github.karljuderojas.freepdf.ui.viewer.label

/** The Settings tab: theme, reading, history, tips and about. Stateless so it can be screenshot-tested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    theme: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit,
    rememberHistory: Boolean,
    onRememberHistory: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    version: String,
    onSourceCode: () -> Unit,
    showTips: Boolean = true,
    onShowTips: (Boolean) -> Unit = {},
    onResetTips: () -> Unit = {},
    modifier: Modifier = Modifier,
    pageColors: PageColors = PageColors.Normal,
    onPageColors: (PageColors) -> Unit = {},
    speechRate: SpeechRate = SpeechRate.Normal,
    onSpeechRate: (SpeechRate) -> Unit = {},
) {
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(R.string.settings_appearance)
            ListItem(headlineContent = { Text(stringResource(R.string.settings_theme)) })
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ThemeChoice.entries.forEachIndexed { i, choice ->
                    SegmentedButton(
                        selected = choice == theme,
                        onClick = { onTheme(choice) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeChoice.entries.size),
                    ) {
                        Text(stringResource(choice.label))
                    }
                }
            }

            SectionHeader(R.string.settings_reading)
            ListItem(
                headlineContent = { Text(stringResource(R.string.page_colors)) },
                supportingContent = { Text(stringResource(R.string.settings_page_colors_detail)) },
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                PageColors.entries.forEachIndexed { i, choice ->
                    SegmentedButton(
                        selected = choice == pageColors,
                        onClick = { onPageColors(choice) },
                        shape = SegmentedButtonDefaults.itemShape(i, PageColors.entries.size),
                    ) {
                        Text(stringResource(choice.label))
                    }
                }
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_speech_rate)) },
                supportingContent = { Text(stringResource(R.string.settings_speech_rate_detail)) },
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SpeechRate.entries.forEachIndexed { i, choice ->
                    SegmentedButton(
                        selected = choice == speechRate,
                        onClick = { onSpeechRate(choice) },
                        shape = SegmentedButtonDefaults.itemShape(i, SpeechRate.entries.size),
                    ) {
                        Text(stringResource(choice.label))
                    }
                }
            }

            SectionHeader(R.string.settings_history)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_remember_history)) },
                supportingContent = { Text(stringResource(R.string.settings_remember_history_detail)) },
                trailingContent = { Switch(checked = rememberHistory, onCheckedChange = onRememberHistory) },
                modifier = Modifier.clickable { onRememberHistory(!rememberHistory) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_history)) },
                supportingContent = { Text(stringResource(R.string.settings_clear_history_detail)) },
                modifier = Modifier.clickable { confirmClear = true },
            )

            SectionHeader(R.string.settings_tips)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_tips)) },
                supportingContent = { Text(stringResource(R.string.settings_show_tips_detail)) },
                trailingContent = { Switch(checked = showTips, onCheckedChange = onShowTips) },
                modifier = Modifier.clickable { onShowTips(!showTips) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reset_tips)) },
                supportingContent = { Text(stringResource(R.string.settings_reset_tips_detail)) },
                modifier = Modifier.clickable(onClick = onResetTips),
            )

            SectionHeader(R.string.settings_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version, stringResource(R.string.app_name), version)) },
                supportingContent = { Text(stringResource(R.string.settings_about_detail)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source_code)) },
                supportingContent = { Text(SOURCE_URL.removePrefix("https://")) },
                modifier = Modifier.clickable(onClick = onSourceCode),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_history_title)) },
            text = { Text(stringResource(R.string.settings_clear_history_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Where the app's source lives, for the About section. */
const val SOURCE_URL = "https://github.com/karljuderojas/OpenPDF-Project"

private val ThemeChoice.label: Int
    get() = when (this) {
        ThemeChoice.System -> R.string.theme_system
        ThemeChoice.Light -> R.string.theme_light
        ThemeChoice.Dark -> R.string.theme_dark
    }

private val SpeechRate.label: Int
    get() = when (this) {
        SpeechRate.Slow -> R.string.speech_rate_slow
        SpeechRate.Normal -> R.string.speech_rate_normal
        SpeechRate.Fast -> R.string.speech_rate_fast
    }

@Composable
private fun SectionHeader(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
