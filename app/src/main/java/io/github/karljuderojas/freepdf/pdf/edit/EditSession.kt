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
 * of the copy before it, and the password then in effect, which is what [undo] restores.
 *
 * Not thread-safe: the caller serialises edits (the viewer does them under its render lock).
 */
class EditSession(private val dir: File, source: InputStream) {

    val workingFile = File(dir, "working.pdf")

    private val undoStack = ArrayDeque<File>()

    // The password in effect before each snapshot's edit, kept in step with [undoStack].
    private val undoPasswords = ArrayDeque<String>()
    private var snapshotCount = 0

    /** Undo depth at the last save; -1 once that state has been trimmed off the undo stack. */
    private var savedDepth = 0

    init {
        dir.mkdirs()
        workingFile.outputStream().use { source.copyTo(it) }
    }

    /** The password the PDF was unlocked with; empty for one that opens without. See [unlock]. */
    var password: String = ""
        private set

    /** True if the PDF is locked and [unlock] has not been given its password yet. */
    fun needsPassword(): Boolean = !PdfDocuments.opens(workingFile, password)

    /** Uses [candidate] from now on if it opens the PDF. Edits keep the file locked with it. */
    fun unlock(candidate: String): Boolean = PdfDocuments.opens(workingFile, candidate).also { if (it) password = candidate }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val hasUnsavedChanges: Boolean get() = undoStack.size != savedDepth

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
        val snapshot = File(dir, "undo-${snapshotCount++}.pdf")
        moveOver(workingFile, snapshot)
        moveOver(next, workingFile)
        undoStack.addLast(snapshot)
        undoPasswords.addLast(password)
        password = nextPassword
        if (undoStack.size > MAX_UNDO) {
            undoStack.removeFirst().delete()
            undoPasswords.removeFirst()
            savedDepth--
        }
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        moveOver(snapshot, workingFile)
        password = undoPasswords.removeLast()
    }

    /** Copies the working copy to [output] and marks the current state as saved. */
    fun writeTo(output: OutputStream) {
        workingFile.inputStream().use { it.copyTo(output) }
        savedDepth = undoStack.size
    }

    fun close() {
        dir.deleteRecursively()
    }

    private fun moveOver(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val MAX_UNDO = 30
    }
}
