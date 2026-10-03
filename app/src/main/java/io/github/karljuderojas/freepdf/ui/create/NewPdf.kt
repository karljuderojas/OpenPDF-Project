package io.github.karljuderojas.freepdf.ui.create

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.karljuderojas.freepdf.pdf.edit.PdfDocuments
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Helpers shared by the tools that make a brand-new PDF (images to PDF, the scanner). */
object NewPdf {

    /** A file name for a new PDF such as `Scan 2026-10-03 14.05.pdf`. */
    fun defaultName(prefix: String, now: Date = Date()): String =
        "$prefix ${SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(now)}.pdf"

    /**
     * Writes [document] to [uri], a file the user just made with Android's save dialog, and keeps
     * lasting access to it so it can be listed in Recent. Closes [document] either way.
     */
    fun write(context: Context, document: PDDocument, uri: Uri) {
        document.use { PdfDocuments.save(context, it, uri) }
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .recoverCatching { context.contentResolver.takePersistableUriPermission(uri, read) }
    }
}
