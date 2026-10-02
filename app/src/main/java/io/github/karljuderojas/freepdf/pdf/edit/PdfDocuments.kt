package io.github.karljuderojas.freepdf.pdf.edit

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument

/** Loading and saving PdfBox documents through Android's storage access framework. */
object PdfDocuments {

    fun load(context: Context, uri: Uri, password: String = ""): PDDocument {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read $uri")
        return input.use { PDDocument.load(it, password) }
    }

    /** Overwrites [uri] with [document]. "wt" truncates so a shorter file leaves no stale bytes. */
    fun save(context: Context, document: PDDocument, uri: Uri) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write $uri")
        output.use { document.save(it) }
    }
}
