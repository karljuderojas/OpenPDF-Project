package io.github.karljuderojas.freepdf.files

import android.content.ContentResolver
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
    /** The file is no longer there, or the app can no longer reach it. */
    data object Gone : RenameResult
    /** Something else is already called that, or the provider refused. */
    data object Failed : RenameResult
}

/** What happened when a file was deleted from the Files tab. */
enum class DeleteResult {
    Deleted,
    /** The provider does not allow deleting; the caller can offer to only remove it from the list. */
    Unsupported,
    /** The file is no longer there, or the app can no longer reach it; only the list entry is left. */
    Gone,
    Failed,
}

/** Whether a file can be renamed or deleted from here. */
enum class Support {
    Supported,
    /** The file is there but its provider does not allow the action from another app. */
    Unsupported,
    /** The file is no longer there, or the app can no longer reach it (moved, deleted, grant lost, storage removed). */
    Gone,
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

    /** Asks the provider; call it off the main thread, as it can be slow (SD cards, cloud providers). */
    fun renameSupport(context: Context, uri: Uri): Support = supports(context, uri, DocumentsContract.Document.FLAG_SUPPORTS_RENAME)

    /** Asks the provider; call it off the main thread, as it can be slow (SD cards, cloud providers). */
    fun deleteSupport(context: Context, uri: Uri): Support = supports(context, uri, DocumentsContract.Document.FLAG_SUPPORTS_DELETE)

    fun rename(context: Context, uri: Uri, newName: String): RenameResult = runCatching {
        when (uri.scheme) {
            "file" -> {
                val file = File(uri.path ?: return RenameResult.Failed)
                val target = File(file.parentFile, newName)
                if (!file.exists()) RenameResult.Gone
                else if (target.exists()) RenameResult.Failed
                else if (file.renameTo(target)) RenameResult.Renamed(Uri.fromFile(target), newName)
                else RenameResult.Failed
            }
            "content" -> {
                when (renameSupport(context, uri)) {
                    Support.Unsupported -> return RenameResult.Unsupported
                    Support.Gone -> return RenameResult.Gone
                    Support.Supported -> Unit
                }
                val resolver = context.contentResolver
                val renamed = DocumentsContract.renameDocument(resolver, uri, newName) ?: return RenameResult.Failed
                // The provider may have changed the name (FAT volumes replace characters such as ':' and '?').
                val actualName = displayName(resolver, renamed) ?: newName
                RenameResult.Renamed(renamed, actualName, persistGrant(resolver, uri, renamed))
            }
            else -> RenameResult.Unsupported
        }
    }.getOrDefault(RenameResult.Failed)

    private fun displayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * A rename can give the document a new URI, and the lasting grant the app holds is for the old
     * one. A document reached through a folder the app holds a lasting grant on (a tree URI) needs
     * nothing: the folder's grant covers it, and the system would refuse a grant of its own. Any
     * other document gets one taken for [new] (writable if possible) and the old one is let go.
     * False if the provider would not grant it, in which case the new URI will not reopen after a restart.
     */
    internal fun persistGrant(resolver: ContentResolver, old: Uri, new: Uri): Boolean {
        if (new == old) return true
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val persisted = runCatching { resolver.persistedUriPermissions }.getOrDefault(emptyList())
        if (persisted.any { it.isReadPermission && Documents.covers(it.uri, new) }) return true
        val taken = runCatching { resolver.takePersistableUriPermission(new, read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .recoverCatching { resolver.takePersistableUriPermission(new, read) }
            .isSuccess
        if (taken) {
            val flags = persisted.firstOrNull { it.uri == old }
                ?.let { (if (it.isReadPermission) read else 0) or (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0) }
            if (flags != null && flags != 0) runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
        return taken
    }

    fun delete(context: Context, uri: Uri): DeleteResult = runCatching {
        when (uri.scheme) {
            "file" -> {
                val file = File(uri.path ?: return DeleteResult.Failed)
                when {
                    !file.exists() -> DeleteResult.Gone
                    file.delete() -> DeleteResult.Deleted
                    else -> DeleteResult.Failed
                }
            }
            "content" -> when (deleteSupport(context, uri)) {
                Support.Unsupported -> DeleteResult.Unsupported
                Support.Gone -> DeleteResult.Gone
                Support.Supported ->
                    if (DocumentsContract.deleteDocument(context.contentResolver, uri)) DeleteResult.Deleted else DeleteResult.Failed
            }
            else -> DeleteResult.Unsupported
        }
    }.getOrDefault(DeleteResult.Failed)

    /**
     * Asks the provider whether the document has [flag]. A missing row, a null cursor or any
     * exception (the provider throws for a document that is gone, and the system for one the app
     * may no longer read) all mean the file is [Support.Gone], not that the provider refuses.
     */
    private fun supports(context: Context, uri: Uri, flag: Int): Support = runCatching {
        if (uri.scheme == "file") {
            return@runCatching if (uri.path?.let { File(it).exists() } == true) Support.Supported else Support.Gone
        }
        if (!DocumentsContract.isDocumentUri(context, uri)) return@runCatching Support.Unsupported
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
            when {
                !it.moveToFirst() -> Support.Gone
                (it.getInt(0) and flag) != 0 -> Support.Supported
                else -> Support.Unsupported
            }
        } ?: Support.Gone
    }.getOrDefault(Support.Gone)
}
