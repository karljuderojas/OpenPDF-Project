package io.github.karljuderojas.freepdf.pdf.edit

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A private working copy of the open PDF. Edits are applied to the copy with PdfBox and the viewer
 * re-renders it, so nothing touches the user's file until [writeTo]. Every edit keeps a snapshot
 * of the copy before it, and the password then in effect, which is what [undo] restores; an
 * undone edit can be put back with [redo] until the next new edit.
 *
 * Not thread-safe: the caller serialises edits (the viewer does them under its render lock).
 */
class EditSession(private val dir: File, source: InputStream) {

    val workingFile = File(dir, "working.pdf")

    // The PDF as it was opened. Edits re-encrypt the working copy under a password of their own
    // (see PdfDocuments.keepProtection), so the owner password is checked against this copy.
    private val originalFile = File(dir, "original.pdf")

    /** A saved copy of the document, which version of it that was, and the password that opens it. */
    private class Snapshot(val file: File, val version: Int, val password: String)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var snapshotCount = 0

    // Every state of the working copy gets its own number, so undoing back to the saved state,
    // or redoing forward to it, counts as saved, while a new edit made after an undo does not.
    private var versionCount = 0
    private var version = 0
    private var savedVersion = 0

    init {
        dir.mkdirs()
        workingFile.outputStream().use { source.copyTo(it) }
        Files.copy(workingFile.toPath(), originalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /** The password the PDF was unlocked with; empty for one that opens without. See [unlock]. */
    var password: String = ""
        private set

    /** True if the PDF is locked and [unlock] has not been given its password yet. */
    fun needsPassword(): Boolean = !PdfDocuments.opens(workingFile, password)

    /** Uses [candidate] from now on if it opens the PDF. Edits keep the file locked with it. */
    fun unlock(candidate: String): Boolean = PdfDocuments.opens(workingFile, candidate).also { if (it) password = candidate }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val hasUnsavedChanges: Boolean get() = version != savedVersion

    /** Applies [change] to the working copy. If it throws, the copy is left as it was. */
    fun edit(change: (PDDocument) -> Unit) = commit(password) { document ->
        change(document)
        PdfDocuments.keepProtection(document, password)
    }

    /**
     * Locks the working copy with [newPassword] from now on, or removes its password when
     * [newPassword] is empty, as one undoable edit. See [PdfDocuments.setProtection].
     */
    fun setPassword(newPassword: String) = commit(newPassword) { PdfDocuments.setProtection(it, newPassword) }

    /** Thrown by [removeRestrictions] when the password given is not the PDF's owner password. */
    class WrongOwnerPassword : Exception()

    /**
     * True if [candidate] is the owner password of the PDF as it was opened. Edits made since do
     * not change the answer: they are checked against the copy taken at the start, not the
     * working copy, whose owner password is the session's own.
     */
    fun isOwnerPassword(candidate: String): Boolean = PdfDocuments.isOwnerPassword(originalFile, candidate)

    /**
     * Takes every restriction, and any password, off the working copy as one undoable edit. Only
     * the owner password allows it (see [isOwnerPassword]): with anything else this throws
     * [WrongOwnerPassword] and changes nothing. Once it matches, the working copy is unlocked
     * with the password this session holds; PdfBox strips the security either way.
     */
    fun removeRestrictions(ownerPassword: String) {
        if (!isOwnerPassword(ownerPassword)) throw WrongOwnerPassword()
        commit("") { it.isAllSecurityToBeRemoved = true }
    }

    /** Saves [change] as the new working copy, which [nextPassword] opens, and records an undo step. */
    private fun commit(nextPassword: String, change: (PDDocument) -> Unit) {
        val next = File(dir, "next.pdf")
        try {
            PDDocument.load(workingFile, password).use { document ->
                change(document)
                document.save(next)
            }
        } catch (e: Throwable) {
            next.delete()
            throw e
        }
        undoStack.addLast(stash())
        moveOver(next, workingFile)
        password = nextPassword
        version = ++versionCount
        redoStack.forEach { it.file.delete() }
        redoStack.clear()
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst().file.delete()
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(stash())
        restore(snapshot)
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(stash())
        restore(snapshot)
    }

    /** Copies the working copy to [output] and marks the current state as saved. */
    fun writeTo(output: OutputStream) {
        workingFile.inputStream().use { it.copyTo(output) }
        markSaved()
    }

    /** Records that the working copy, as it is now, has been written out successfully. */
    fun markSaved() {
        savedVersion = version
    }

    fun close() {
        dir.deleteRecursively()
    }

    /** Moves the working copy aside as a snapshot of the current version. */
    private fun stash(): Snapshot {
        val file = File(dir, "snapshot-${snapshotCount++}.pdf")
        moveOver(workingFile, file)
        return Snapshot(file, version, password)
    }

    private fun restore(snapshot: Snapshot) {
        moveOver(snapshot.file, workingFile)
        version = snapshot.version
        password = snapshot.password
    }

    private fun moveOver(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val MAX_UNDO = 30
    }
}
