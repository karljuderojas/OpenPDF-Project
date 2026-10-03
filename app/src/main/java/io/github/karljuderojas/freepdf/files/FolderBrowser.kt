package io.github.karljuderojas.freepdf.files

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A PDF found in the folder the user chose. [modified] and [size] are 0 when the provider does not say. */
data class FolderFile(val uri: String, val name: String, val modified: Long, val size: Long)

enum class FolderSort { Name, Date }

/** [files] ordered by [sort]: names A to Z ignoring case, dates newest first. */
fun sortFolderFiles(files: List<FolderFile>, sort: FolderSort): List<FolderFile> = when (sort) {
    FolderSort.Name -> files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    FolderSort.Date -> files.sortedWith(compareByDescending<FolderFile> { it.modified }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

/** True for a PDF, going by its type and falling back to the file name when the provider gives a generic type. */
internal fun isPdf(mimeType: String?, name: String): Boolean =
    mimeType == "application/pdf" ||
        (name.endsWith(".pdf", ignoreCase = true) && (mimeType == null || mimeType == "application/octet-stream"))

/**
 * The one folder the Files tab browses, chosen with the system folder picker. Only the picker's
 * grant to that folder is used: the app asks for no broad storage permission. The choice is kept
 * in app preferences on this device.
 */
class FolderStore(private val prefs: SharedPreferences) {

    private val _folder = MutableStateFlow(prefs.getString(KEY, null))

    /** The chosen folder's tree URI, or null when none is chosen. */
    val folder: StateFlow<String?> = _folder.asStateFlow()

    fun choose(context: Context, tree: Uri) {
        // Keep access across restarts. Reading is all that is needed to browse and open.
        runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val old = _folder.value
        if (old != null && old != tree.toString()) release(context, old)
        prefs.edit().putString(KEY, tree.toString()).apply()
        _folder.value = tree.toString()
    }

    /** Stops browsing the folder and gives its access back. Files in it are not touched. */
    fun forget(context: Context) {
        _folder.value?.let { release(context, it) }
        prefs.edit().remove(KEY).apply()
        _folder.value = null
    }

    private fun release(context: Context, tree: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private companion object {
        const val KEY = "folder"
    }
}

/** What reading the chosen folder gave. [name] is the folder's own name, [files] its PDFs. */
data class FolderListing(val name: String, val files: List<FolderFile>)

object FolderReader {

    /** Reads the PDFs directly inside [tree] (subfolders are not entered). Null if the folder cannot be read any more. */
    fun read(resolver: ContentResolver, tree: Uri): FolderListing? = runCatching {
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val folderName = resolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, treeId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null } ?: treeId.substringAfterLast(':').ifEmpty { treeId }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val files = resolver.query(children, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (!isPdf(cursor.getString(2), name)) continue
                    add(
                        FolderFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)).toString(),
                            name = name,
                            modified = if (cursor.isNull(3)) 0 else cursor.getLong(3),
                            size = if (cursor.isNull(4)) 0 else cursor.getLong(4),
                        ),
                    )
                }
            }
        } ?: return null
        FolderListing(folderName, files)
    }.getOrNull()
}
