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
 */
class Documents(private val prefs: SharedPreferences, private val clock: () -> Long = System::currentTimeMillis) {

    private val _recent = MutableStateFlow(readRecent())
    val recent: StateFlow<List<DocumentEntry>> = _recent.asStateFlow()

    private val _open = MutableStateFlow<List<DocumentEntry>>(emptyList())

    /** Opened since the app started and not closed from the Files tab, newest first. */
    val open: StateFlow<List<DocumentEntry>> = _open.asStateFlow()

    /**
     * Called when a PDF opens. It joins the open list, and the recent history too when [remember]
     * is true (only when the app can reopen it later, i.e. it holds lasting access to the file).
     */
    fun opened(uri: String, name: String, remember: Boolean) {
        val entry = DocumentEntry(uri, name, clock())
        _open.update { list -> listOf(entry) + list.filter { it.uri != uri } }
        if (remember) {
            _recent.update { list -> (listOf(entry) + list.filter { it.uri != uri }).take(MAX_RECENT) }
            writeRecent()
        }
    }

    fun close(uri: String) {
        _open.update { list -> list.filter { it.uri != uri } }
    }

    fun forget(uri: String) {
        _recent.update { list -> list.filter { it.uri != uri } }
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

    private companion object {
        const val KEY = "recent"
        const val MAX_RECENT = 50
    }
}
