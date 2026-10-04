package io.github.karljuderojas.freepdf.files

import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A PDF the user has opened. [uri] is kept as a string so it can be stored and compared. */
data class DocumentEntry(val uri: String, val name: String, val openedAt: Long)

/**
 * What the Files tab lists: the PDFs open in this session, and a recent history that survives
 * restarts. Both live only on this device (history in app preferences, never synced anywhere).
 * [onClosed] hears of every PDF leaving the open list, closed or dropped off the end, so
 * whatever is kept for it while it is open can be let go. [hasUnsavedChanges] says whether a
 * PDF has changes that closing would lose; such a PDF is never dropped off the end.
 */
class Documents(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onClosed: (uri: String) -> Unit = {},
    private val hasUnsavedChanges: (uri: String) -> Boolean = { false },
) {

    private val _recent = MutableStateFlow(readRecent())
    val recent: StateFlow<List<DocumentEntry>> = _recent.asStateFlow()

    private val _open = MutableStateFlow<List<DocumentEntry>>(emptyList())

    /** Opened since the app started and not closed since, newest first, at most [MAX_OPEN] unless more have unsaved changes. */
    val open: StateFlow<List<DocumentEntry>> = _open.asStateFlow()

    /**
     * Called when a PDF opens. It joins the open list, and the recent history too when [remember]
     * is true (only when the app can reopen it later, i.e. it holds lasting access to the file).
     * Past [MAX_OPEN] the oldest PDFs without unsaved changes leave the list; see [MAX_OPEN].
     */
    fun opened(uri: String, name: String, remember: Boolean) {
        val entry = DocumentEntry(uri, name, clock())
        val before = _open.value
        val kept = listOf(entry) + before.filter { it.uri != uri }
        val leaving = kept.drop(1).asReversed().filter { !hasUnsavedChanges(it.uri) }.take((kept.size - MAX_OPEN).coerceAtLeast(0)).map { it.uri }.toSet()
        val after = kept.filter { it.uri !in leaving }
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

    /**
     * After the file behind [oldUri] was renamed: the history entry and the open-list entry (same
     * place, same time) take the new URI and name. Only called for a document without unsaved
     * changes, whose session the caller has closed, since sessions are keyed by URI.
     */
    fun renamed(oldUri: String, newUri: String, newName: String) {
        fun swap(list: List<DocumentEntry>) = list.map { if (it.uri == oldUri) it.copy(uri = newUri, name = newName) else it }
            .distinctBy { it.uri }
        _open.update(::swap)
        if (_recent.value.any { it.uri == oldUri }) {
            _recent.update(::swap)
            writeRecent()
        }
    }

    /**
     * After the lasting grant on the folder [tree] was given back: its PDFs cannot be opened
     * again later, so they leave the history. Files open right now stay open.
     */
    fun forgetUnder(tree: String) {
        val root = Uri.parse(tree)
        if (_recent.value.none { covers(root, Uri.parse(it.uri)) }) return
        _recent.update { list -> list.filterNot { covers(root, Uri.parse(it.uri)) } }
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

        /**
         * The viewer's switcher lists every open file, so the oldest drop off past this. One
         * with unsaved changes never does: dropping it would throw the changes away without a
         * word, so the oldest saved one goes instead, and when every open file has changes the
         * list grows past the cap until one of them is saved or closed. The same rule keeps its
         * session alive (DocumentSessions), so the two lists stay in step.
         */
        const val MAX_OPEN = 8

        /**
         * True if the app can open [uri] again after a restart, so it is worth keeping in the
         * history: a content:// document it holds a lasting read grant for ([hasPersistedRead]),
         * or a file of its own under [filesDir]. Other file:// URIs, such as a PDF handed over by
         * another app or one in the cache, usually no longer open later, so they stay out.
         */
        fun lasting(uri: Uri, filesDir: File, hasPersistedRead: () -> Boolean): Boolean = when (uri.scheme) {
            "content" -> hasPersistedRead()
            "file" -> uri.path?.let { isUnder(File(it), filesDir) } ?: false
            else -> false
        }

        /**
         * True if a grant on [grant] gives access to [uri]: the same URI, or a document inside
         * the folder tree [grant] is for (the system grants a whole tree at once, and its
         * documents are reached through tree URIs that start with it).
         */
        fun covers(grant: Uri, uri: Uri): Boolean = uri == grant || uri.toString().startsWith("$grant/document/")

        private fun isUnder(file: File, dir: File): Boolean {
            val root = runCatching { dir.canonicalFile }.getOrDefault(dir.absoluteFile)
            val path = runCatching { file.canonicalFile }.getOrDefault(file.absoluteFile)
            return path.startsWith(root) && path != root
        }
    }
}
