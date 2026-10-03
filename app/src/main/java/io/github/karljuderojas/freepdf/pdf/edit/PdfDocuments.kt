package io.github.karljuderojas.freepdf.pdf.edit

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
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

    /**
     * Locks [document] so that [password] opens it, or takes the password off when [password] is
     * empty, with AES-256. If it was not locked, or was opened with its owner password, [password]
     * becomes the owner password too and everything is allowed. Otherwise only its permissions
     * stay as they were, under a random owner password as in [keepProtection], so restrictions set
     * by someone else are not dropped.
     */
    fun setProtection(document: PDDocument, password: String) {
        val permissions = document.currentAccessPermission
        val isOwner = !document.isEncrypted || permissions.isOwnerPermission
        when {
            isOwner && password.isEmpty() -> document.isAllSecurityToBeRemoved = true
            isOwner -> document.protect(StandardProtectionPolicy(password, password, AccessPermission()).apply { encryptionKeyLength = 256 })
            else -> document.protect(
                StandardProtectionPolicy(UUID.randomUUID().toString(), password, permissions).apply { encryptionKeyLength = 256 },
            )
        }
    }
}
