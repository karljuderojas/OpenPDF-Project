package io.github.karljuderojas.freepdf.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's light or dark look. [System] follows the phone's setting. */
enum class ThemeChoice { System, Light, Dark }

/** How pages are tinted while reading. Only the screen changes; the PDF itself is never touched. */
enum class PageColors { Normal, Night, Sepia }

/** Choices from the Settings tab. They live in app preferences on this phone only. */
class AppSettings(private val prefs: SharedPreferences) {

    private val _theme = MutableStateFlow(
        prefs.getString(KEY_THEME, null)?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } } ?: ThemeChoice.System,
    )
    val theme: StateFlow<ThemeChoice> = _theme.asStateFlow()

    private val _rememberHistory = MutableStateFlow(prefs.getBoolean(KEY_HISTORY, true))

    /** When false, opened files still show under Open now but are not added to the history. */
    val rememberHistory: StateFlow<Boolean> = _rememberHistory.asStateFlow()

    private val _pageColors = MutableStateFlow(
        prefs.getString(KEY_PAGE_COLORS, null)?.let { name -> PageColors.entries.firstOrNull { it.name == name } } ?: PageColors.Normal,
    )

    /** Applies to every document, so a reader who likes Night keeps it for the next file too. */
    val pageColors: StateFlow<PageColors> = _pageColors.asStateFlow()

    fun setTheme(theme: ThemeChoice) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun setRememberHistory(remember: Boolean) {
        _rememberHistory.value = remember
        prefs.edit().putBoolean(KEY_HISTORY, remember).apply()
    }

    fun setPageColors(colors: PageColors) {
        _pageColors.value = colors
        prefs.edit().putString(KEY_PAGE_COLORS, colors.name).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_HISTORY = "remember_history"
        const val KEY_PAGE_COLORS = "page_colors"
    }
}
