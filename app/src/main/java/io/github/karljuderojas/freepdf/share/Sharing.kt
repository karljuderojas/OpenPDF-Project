package io.github.karljuderojas.freepdf.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.karljuderojas.freepdf.R
import java.io.File
import java.util.UUID

/** Sends a PDF to any app through Android's share sheet. Nothing goes through a FreePDF server. */
object Sharing {

    /**
     * Shares a file the app already has access to, such as one from the Files tab. A file:// URI
     * (a PDF handed over by an older file manager) cannot leave the app on Android 7 and later,
     * so it is copied and shared through the FileProvider instead.
     */
    fun shareUri(context: Context, uri: Uri, name: String) {
        if (uri.scheme == "file") {
            val copy = sharedCopy(context, name)
            File(uri.path.orEmpty()).copyTo(copy, overwrite = true)
            shareFile(context, copy)
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, name)
            // ClipData is what carries the read grant through the chooser to the target app.
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_title, name))
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Shares a file in the app's cache/shared folder (see res/xml/file_paths.xml). */
    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        shareUri(context, uri, file.name)
    }

    /** Shares page images (see [sharedFolder]) together, as one message where the app allows. */
    fun shareImages(context: Context, files: List<File>, title: String) {
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.apply {
            type = "image/jpeg"
            clipData = ClipData.newRawUri(title, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_title, title))
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Where to put a copy for sharing, named [name] so the recipient sees a sensible file name. */
    fun sharedCopy(context: Context, name: String): File {
        val safe = safeName(name).ifBlank { "document.pdf" }
        return File(sharedFolder(context), if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf")
    }

    /**
     * A new, empty folder for the files of one share, under cache/shared (which file_paths.xml
     * covers, subfolders included). Each share gets its own so a share sheet or receiving app
     * still reading an earlier share's file does not lose it; folders older than [MAX_SHARED_AGE_MS]
     * are cleared out here, as by then the share is over.
     */
    fun sharedFolder(context: Context, now: Long = System.currentTimeMillis()): File {
        val root = File(context.cacheDir, "shared")
        root.listFiles()?.forEach { entry ->
            if (now - entry.lastModified() > MAX_SHARED_AGE_MS) entry.deleteRecursively()
        }
        return File(root, "$now-${UUID.randomUUID().toString().take(8)}").apply { mkdirs() }
    }

    /** How long a shared copy stays in the cache: long enough for any share sheet to finish with it. */
    const val MAX_SHARED_AGE_MS = 60 * 60 * 1000L

    /** [name] without characters that file systems refuse. */
    fun safeName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}
