package io.github.karljuderojas.freepdf.pdf.edit

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import io.github.karljuderojas.freepdf.files.SafeWrite
import java.io.File
import java.io.IOException
import java.util.UUID

/** Loading and saving PdfBox documents through Android's storage access framework. */
object PdfDocuments {

    fun load(context: Context, uri: Uri, password: String = ""): PDDocument {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read $uri")
        return input.use { PDDocument.load(it, password) }
    }

    /** Overwrites [uri] with [document], leaving the old content in place if the write fails (see [SafeWrite]). */
    fun save(context: Context, document: PDDocument, uri: Uri) {
        SafeWrite.write(context, uri) { document.save(it) }
    }

    /** True if [file] opens with [password]; false if it needs a different one. */
    fun opens(file: File, password: String): Boolean = try {
        PDDocument.load(file, password).close()
        true
    } catch (e: InvalidPasswordException) {
        false
    }

    /** What a PDF's permissions hold back from someone who opened it with [password]. */
    enum class Restriction { Print, Copy, Edit, Annotate, FillForms, Assemble }

    /** The [Restriction]s the permissions of [file] set; none for an unlocked file. See [restrictions]. */
    fun restrictions(file: File, password: String): List<Restriction> = PDDocument.load(file, password).use { restrictions(it) }

    /**
     * What the PDF's permissions (its /P flags) hold back. They are read from the encryption
     * dictionary even when [document] was opened as owner, because a file whose owner password
     * is empty opens as owner here (PdfBox tries the owner password first) while other viewers
     * open it as user and enforce the flags; [isOwnerPassword] says which case this is.
     */
    fun restrictions(document: PDDocument): List<Restriction> {
        if (!document.isEncrypted) return emptyList()
        val current = document.currentAccessPermission
        val permissions = if (current.isOwnerPermission) AccessPermission(document.encryption.permissions) else current
        return buildList {
            if (!permissions.canPrint()) add(Restriction.Print)
            if (!permissions.canExtractContent()) add(Restriction.Copy)
            if (!permissions.canModify()) add(Restriction.Edit)
            if (!permissions.canModifyAnnotations()) add(Restriction.Annotate)
            if (!permissions.canFillInForm()) add(Restriction.FillForms)
            if (!permissions.canAssembleDocument()) add(Restriction.Assemble)
        }
    }

    /**
     * True if [ownerPassword] is the PDF's owner password, which alone may lift its restrictions.
     * False for any other password, and for a file that cannot be read with it at all.
     */
    fun isOwnerPassword(file: File, ownerPassword: String): Boolean = try {
        PDDocument.load(file, ownerPassword).use { it.isEncrypted && it.currentAccessPermission.isOwnerPermission }
    } catch (e: IOException) {
        // InvalidPasswordException, or a failure to read or decrypt the file (an unusable password for AES-256 among them).
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
