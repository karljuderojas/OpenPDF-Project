package io.github.karljuderojas.freepdf.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** What happened when a file was renamed from the Files tab. */
sealed interface RenameResult {
    /** [persisted]: the app holds a lasting grant on the new [uri], so it still opens after a restart. */
    data class Renamed(val uri: Uri, val name: String, val persisted: Boolean = true) : RenameResult
    /** The provider (or file) cannot be renamed; the name in the list is left alone. */
    data object Unsupported : RenameResult
    /** Something else is already called that, or the provider refused. */
    data object Failed : RenameResult
}

/** What happened when a file was deleted from the Files tab. */
enum class DeleteResult {
    Deleted,
    /** The provider does not allow deleting; the caller can offer to only remove it from the list. */
    Unsupported,
    Failed,
}

/**
 * Renaming and deleting the PDF behind a Files entry, through its Storage Access Framework URI
 * (or the path, for the app's own files). A provider may not support either, so both say so
 * instead of failing, and the caller falls back to the history alone.
 */
object FileActions {

    /** [typed] with ".pdf" restored when the extension was dropped; null if the name is empty or has a slash. */
    fun cleanName(typed: String): String? {
        val trimmed = typed.trim().trimEnd('.')
        if (trimmed.isEmpty() || '/' in trimmed || '\\' in trimmed || trimmed.equals("pdf", ignoreCase = true)) return null
        return if (trimmed.endsWith(".pdf", ignoreCase = true)) trimmed else "$trimmed.pdf"
    }

    /** The name to suggest in the rename box: [name] without its ".pdf", so the extension is kept by [cleanName]. */
    fun editableName(name: String): String = name.removeSuffix(".pdf").removeSuffix(".PDF")

    fun canRename(context: Context, uri: Uri): Boolean = supports(context, uri, DocumentsContract.Document.FLAG_SUPPORTS_RENAME)

    fun canDelete(context: Context, uri: Uri): Boolean = supports(context, uri, DocumentsContract.Document.FLAG_SUPPORTS_DELETE)

    fun rename(context: Context, uri: Uri, newName: String): RenameResult = runCatching {
        when (uri.scheme) {
            "file" -> {
                val file = File(uri.path ?: return RenameResult.Failed)
                val target = File(file.parentFile, newName)
                if (target.exists()) RenameResult.Failed
                else if (file.renameTo(target)) RenameResult.Renamed(Uri.fromFile(target), newName)
                else RenameResult.Failed
            }
            "content" -> {
                if (!canRename(context, uri)) return RenameResult.Unsupported
                val renamed = DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                if (renamed != null) RenameResult.Renamed(renamed, newName, persistGrant(context, uri, renamed)) else RenameResult.Failed
            }
            else -> RenameResult.Unsupported
        }
    }.getOrDefault(RenameResult.Failed)

    /**
     * A rename can give the document a new URI, and the lasting grant the app holds is for the old
     * one: take one for [new] (writable if possible) and let the old one go. False if the provider
     * would not grant it, in which case the new URI will not reopen after a restart.
     */
    private fun persistGrant(context: Context, old: Uri, new: Uri): Boolean {
        if (new == old) return true
        val resolver = context.contentResolver
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val taken = runCatching { resolver.takePersistableUriPermission(new, read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .recoverCatching { resolver.takePersistableUriPermission(new, read) }
            .isSuccess
        if (taken) {
            val flags = resolver.persistedUriPermissions.firstOrNull { it.uri == old }
                ?.let { (if (it.isReadPermission) read else 0) or (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0) }
            if (flags != null && flags != 0) runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
        return taken
    }

    fun delete(context: Context, uri: Uri): DeleteResult = runCatching {
        when (uri.scheme) {
            "file" -> if (File(uri.path ?: return DeleteResult.Failed).delete()) DeleteResult.Deleted else DeleteResult.Failed
            "content" -> when {
                !canDelete(context, uri) -> DeleteResult.Unsupported
                DocumentsContract.deleteDocument(context.contentResolver, uri) -> DeleteResult.Deleted
                else -> DeleteResult.Failed
            }
            else -> DeleteResult.Unsupported
        }
    }.getOrDefault(DeleteResult.Failed)

    private fun supports(context: Context, uri: Uri, flag: Int): Boolean = runCatching {
        if (uri.scheme == "file") return@runCatching true
        if (!DocumentsContract.isDocumentUri(context, uri)) return@runCatching false
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
            it.moveToFirst() && (it.getInt(0) and flag) != 0
        } ?: false
    }.getOrDefault(false)
}
