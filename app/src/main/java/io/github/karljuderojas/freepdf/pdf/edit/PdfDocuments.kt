package io.github.karljuderojas.freepdf.pdf.edit

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.util.UUID

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

    /** True if [file] opens with [password]; false if it needs a different one. */
    fun opens(file: File, password: String): Boolean = try {
        PDDocument.load(file, password).close()
        true
    } catch (e: InvalidPasswordException) {
        false
    }

    /**
     * PdfBox refuses to save an encrypted document without a protection policy, so this sets one
     * that keeps the file locked as before: [password] (the one it was opened with) still opens
     * it, with the same permissions. If [password] was the owner password it stays the owner
     * password; otherwise the original owner password is unknown and a random one replaces it.
     */
    fun keepProtection(document: PDDocument, password: String) {
        if (!document.isEncrypted) return
        val permissions = document.currentAccessPermission
        val owner = if (permissions.isOwnerPermission) password else UUID.randomUUID().toString()
        document.protect(StandardProtectionPolicy(owner, password, permissions).apply { encryptionKeyLength = 256 })
    }
}
