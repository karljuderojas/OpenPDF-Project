package io.github.karljuderojas.freepdf.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.karljuderojas.freepdf.R
import java.io.File

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

    /** Where to put a copy for sharing, named [name] so the recipient sees a sensible file name. */
    fun sharedCopy(context: Context, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // One shared copy at a time; older ones are only useful until the share completes.
        dir.listFiles()?.forEach { it.delete() }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "document.pdf" }
        return File(dir, if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf")
    }
}
