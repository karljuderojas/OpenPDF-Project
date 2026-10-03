package io.github.karljuderojas.freepdf.ui.create

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Where photos taken or picked for scanning and signature photos wait until they are used. */
object ScanFiles {

    /** The folder shared with the camera app through FileProvider (see res/xml/file_paths.xml). */
    fun dir(context: Context): File = File(context.cacheDir, "scans").apply { mkdirs() }

    /** A new empty file for the camera app to write a photo into. */
    fun newPhoto(context: Context): File = File.createTempFile("page-", ".jpg", dir(context))

    /** The address to hand the camera app for [file]. */
    fun cameraUri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Copies the picture at [uri] (from the gallery picker) into a new file here. */
    fun copyFrom(context: Context, uri: Uri): File = newPhoto(context).also { file ->
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read $uri")
        input.use { src -> file.outputStream().use { src.copyTo(it) } }
    }
}
