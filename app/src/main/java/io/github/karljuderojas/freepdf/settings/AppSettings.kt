package io.github.karljuderojas.freepdf.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's light or dark look. [System] follows the phone's setting. */
enum class ThemeChoice { System, Light, Dark }

/** How pages are tinted while reading. Only the screen changes; the PDF itself is never touched. */
enum class PageColors { Normal, Night, Sepia }

/** How fast Read aloud speaks; [factor] is the engine's rate, where 1 is its normal pace. */
enum class SpeechRate(val factor: Float) { Slow(0.75f), Normal(1f), Fast(1.4f) }

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

    private val _readingTextSize = MutableStateFlow(prefs.getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))

    /** The size, in sp, of the text in reading mode. */
    val readingTextSize: StateFlow<Int> = _readingTextSize.asStateFlow()

    fun setReadingTextSize(size: Int) {
        _readingTextSize.value = size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        prefs.edit().putInt(KEY_TEXT_SIZE, _readingTextSize.value).apply()
    }

    private val _speechRate = MutableStateFlow(
        prefs.getString(KEY_SPEECH_RATE, null)?.let { name -> SpeechRate.entries.firstOrNull { it.name == name } } ?: SpeechRate.Normal,
    )

    /** The pace of Read aloud; the next sentence spoken uses it. */
    val speechRate: StateFlow<SpeechRate> = _speechRate.asStateFlow()

    fun setSpeechRate(rate: SpeechRate) {
        _speechRate.value = rate
        prefs.edit().putString(KEY_SPEECH_RATE, rate.name).apply()
    }

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

    companion object {
        /** The watermark words typed last that were not one of the ready-made ones, or null. */
        fun lastWatermarkText(prefs: SharedPreferences): String? = prefs.getString(KEY_WATERMARK_TEXT, null)?.takeIf { it.isNotBlank() }

        fun setLastWatermarkText(prefs: SharedPreferences, text: String) {
            prefs.edit().putString(KEY_WATERMARK_TEXT, text).apply()
        }

        /** Forgets the words kept by [setLastWatermarkText], so they are no longer offered. */
        fun clearLastWatermarkText(prefs: SharedPreferences) {
            prefs.edit().remove(KEY_WATERMARK_TEXT).apply()
        }

        private const val KEY_WATERMARK_TEXT = "watermark_custom_text"
        const val MIN_TEXT_SIZE = 12
        const val MAX_TEXT_SIZE = 36
        const val DEFAULT_TEXT_SIZE = 18
        const val TEXT_SIZE_STEP = 2
        private const val KEY_TEXT_SIZE = "reading_text_size"
        private const val KEY_THEME = "theme"
        private const val KEY_HISTORY = "remember_history"
        private const val KEY_PAGE_COLORS = "page_colors"
        private const val KEY_SPEECH_RATE = "speech_rate"
    }
}
