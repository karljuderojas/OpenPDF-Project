package io.github.karljuderojas.freepdf.ui.viewer

import android.net.Uri
import io.github.karljuderojas.freepdf.files.Documents
import io.github.karljuderojas.freepdf.pdf.edit.EditSession
import io.github.karljuderojas.freepdf.pdf.sign.AuditEvent
import io.github.karljuderojas.freepdf.pdf.sign.SignField
import io.github.karljuderojas.freepdf.pdf.sign.SignatureReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/** Where the reader was in a document: the first page on screen and how far it is scrolled, in pixels. */
data class ViewPosition(val page: Int = 0, val offset: Int = 0)

/**
 * Everything about one open document that outlives the screen showing it: its working copy
 * with undo and redo, what the audit page will say about it, the signatures it came with, any
 * stamps still being placed, and where the reader was. [key] is the URI the document was opened
 * from, as the open-documents list names it; [uri] is where saving goes, which Save As moves.
 *
 * The viewer's view model reads and writes this while it is attached; only one is at a time.
 */
class DocumentSession(val key: String, val session: EditSession) {

    var uri: Uri = Uri.parse(key)

    /** The original's fingerprint and when it was opened, for the audit page. Set on first open. */
    var originalSha256 = ""
    var openedAt: Instant = Instant.now()

    // One entry per edit (null for edits that are not part of signing), so undo can drop the
    // matching entry; see ViewerViewModel.
    val editLog = ArrayList<AuditEvent?>()
    val redoLog = ArrayList<AuditEvent?>()

    /** What SignatureVerifier found in the file as opened. */
    var signatures: List<SignatureReport> = emptyList()

    /** The places to sign, found once per arrangement of the pages; null when they must be found again. */
    var signFields: List<SignField>? = null

    /** Stamps placed but not yet written into the PDF, kept so switching away does not lose them. */
    var stamps: List<PlacedStamp> = emptyList()
    var nextStampId = 1L

    var position = ViewPosition()

    /** True if closing this document now would lose something: edits, or stamps not written in yet. */
    val hasUnsavedChanges: Boolean get() = session.hasUnsavedChanges || stamps.isNotEmpty()
}

/**
 * The live sessions of the documents in the open list, one per document, so switching between
 * them keeps each one's edits, undo history and place. Owned by the application: the viewer's
 * view model comes and goes with its screen, and [attach]es to the session of the document it
 * shows. A session ends, and its working copy is deleted, when its document is [close]d or when
 * it is the oldest without unsaved changes of more than [maxOpen] (matching the open list's own
 * cap and rule, see Documents.MAX_OPEN). Working copies left in [root] by a process that was
 * killed are deleted on start.
 */
class DocumentSessions(private val root: File, private val maxOpen: Int = Documents.MAX_OPEN) {

    // In attach order, oldest first, like the open list in reverse.
    private val entries = LinkedHashMap<String, DocumentSession>()

    private val _unsaved = MutableStateFlow<Set<String>>(emptySet())

    /** The keys of the documents with unsaved changes, as of the last [refresh]. */
    val unsaved: StateFlow<Set<String>> = _unsaved.asStateFlow()

    init {
        root.deleteRecursively()
    }

    @Synchronized
    fun get(key: String): DocumentSession? = entries[key]

    val size: Int @Synchronized get() = entries.size

    /**
     * The session for [key], made from [source] if it has none yet. Attaching makes it the most
     * recently used; the least recently used beyond [maxOpen] are closed, skipping any with
     * unsaved changes (so there can be more than [maxOpen] while all of them have changes).
     */
    @Synchronized
    fun attach(key: String, source: () -> InputStream): DocumentSession {
        val existing = entries.remove(key)
        val entry = existing ?: DocumentSession(key, source().use { EditSession(File(root, UUID.randomUUID().toString()), it) })
        entries[key] = entry
        val closing = entries.values.filter { it !== entry && !it.hasUnsavedChanges }.take((entries.size - maxOpen).coerceAtLeast(0))
        closing.forEach { entries.remove(it.key)?.session?.close() }
        refresh()
        return entry
    }

    /** Ends [key]'s session, deleting its working copy and undo history. Nothing happens if it has none. */
    @Synchronized
    fun close(key: String) {
        entries.remove(key)?.session?.close()
        refresh()
    }

    @Synchronized
    fun closeAll() {
        entries.values.forEach { it.session.close() }
        entries.clear()
        refresh()
    }

    /** Brings [unsaved] up to date; called after anything that changes a session. */
    @Synchronized
    fun refresh() {
        _unsaved.value = entries.values.filter { it.hasUnsavedChanges }.map { it.key }.toSet()
    }
}
