package io.github.karljuderojas.freepdf.settings

import android.content.SharedPreferences
import androidx.annotation.StringRes
import io.github.karljuderojas.freepdf.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-line hints, each shown the first time its situation comes up. */
enum class Tip(@StringRes val text: Int) {
    ReadZoom(R.string.tip_read_zoom),
    Annotate(R.string.tip_annotate),
    Sign(R.string.tip_sign),
    Pages(R.string.tip_pages),
    Edit(R.string.tip_edit),
}

/**
 * Which tips were already shown, following the UX blueprint: each tip shows once, never more than
 * one per app session, and the Settings tab can turn them off. No tour at first launch.
 */
class Tips(private val prefs: SharedPreferences) {

    private val seen = prefs.getStringSet(KEY_SEEN, null).orEmpty().toMutableSet()

    // In memory only, so the next session (the app process starting again) may show the next tip.
    private var shownThisSession = false

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun shouldShow(tip: Tip): Boolean = _enabled.value && !shownThisSession && tip.name !in seen

    /** Call when [tip] is put on screen: it will not show again, and no other tip shows this session. */
    fun markSeen(tip: Tip) {
        shownThisSession = true
        seen += tip.name
        prefs.edit().putStringSet(KEY_SEEN, seen.toSet()).apply()
    }

    /** Lets every tip show once more, starting right away. */
    fun resetAll() {
        shownThisSession = false
        seen.clear()
        prefs.edit().remove(KEY_SEEN).apply()
    }

    private companion object {
        const val KEY_SEEN = "seen"
        const val KEY_ENABLED = "enabled"
    }
}
