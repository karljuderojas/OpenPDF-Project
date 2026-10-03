package io.github.karljuderojas.freepdf.files

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** A PDF the user has opened. [uri] is kept as a string so it can be stored and compared. */
data class DocumentEntry(val uri: String, val name: String, val openedAt: Long)

/**
 * What the Files tab lists: the PDFs open in this session, and a recent history that survives
 * restarts. Both live only on this device (history in app preferences, never synced anywhere).
 * [onClosed] hears of every PDF leaving the open list, closed or dropped off the end, so
 * whatever is kept for it while it is open can be let go.
 */
class Documents(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onClosed: (uri: String) -> Unit = {},
) {

    private val _recent = MutableStateFlow(readRecent())
    val recent: StateFlow<List<DocumentEntry>> = _recent.asStateFlow()

    private val _open = MutableStateFlow<List<DocumentEntry>>(emptyList())

    /** Opened since the app started and not closed since, newest first, at most [MAX_OPEN]. */
    val open: StateFlow<List<DocumentEntry>> = _open.asStateFlow()

    /**
     * Called when a PDF opens. It joins the open list, and the recent history too when [remember]
     * is true (only when the app can reopen it later, i.e. it holds lasting access to the file).
     */
    fun opened(uri: String, name: String, remember: Boolean) {
        val entry = DocumentEntry(uri, name, clock())
        val before = _open.value
        val after = (listOf(entry) + before.filter { it.uri != uri }).take(MAX_OPEN)
        _open.value = after
        before.filter { old -> after.none { it.uri == old.uri } }.forEach { onClosed(it.uri) }
        if (remember) {
            _recent.update { list -> (listOf(entry) + list.filter { it.uri != uri }).take(MAX_RECENT) }
            writeRecent()
        }
    }

    fun close(uri: String) {
        val before = _open.value
        _open.value = before.filter { it.uri != uri }
        if (before.any { it.uri == uri }) onClosed(uri)
    }

    fun closeAll() {
        val before = _open.value
        _open.value = emptyList()
        before.forEach { onClosed(it.uri) }
    }

    fun forget(uri: String) {
        _recent.update { list -> list.filter { it.uri != uri } }
        writeRecent()
    }

    /** Empties the history. Files open right now stay open. */
    fun clearHistory() {
        _recent.value = emptyList()
        writeRecent()
    }

    private fun readRecent(): List<DocumentEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            array.getJSONObject(i).run { DocumentEntry(getString("uri"), getString("name"), getLong("openedAt")) }
        }
    }.getOrDefault(emptyList())

    private fun writeRecent() {
        val array = JSONArray()
        _recent.value.forEach {
            array.put(JSONObject().put("uri", it.uri).put("name", it.name).put("openedAt", it.openedAt))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "recent"
        private const val MAX_RECENT = 50

        /** The viewer's switcher lists every open file, so the oldest drop off past this. */
        const val MAX_OPEN = 8
    }
}
