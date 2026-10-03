package io.github.karljuderojas.freepdf.pdf.sign

import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.karljuderojas.freepdf.pdf.edit.PdfDocuments
import java.io.File
import java.io.OutputStream

/**
 * Builds the signed copy that Finish saves: the document as signed, a "Signing certificate" page
 * at the end, and optionally a digital signature over all of it, which makes the copy
 * tamper-evident. The user's original file is never touched.
 */
object SignedCopy {

    /** What [write] is busy with, reported through its `onStep` as each begins. */
    enum class Step { Signing, Timestamping }

    /**
     * What the audit page says the record does and does not prove, for a seal by [identity] and
     * signatures that are [locked] into the page or left editable.
     */
    fun notes(identity: SigningIdentity?, locked: Boolean = true): List<String> = buildList {
        add(
            "This record was made on the signer's own device by FreePDF. The signer's identity was not " +
                "checked by email, phone or ID.",
        )
        add("Original SHA-256 is the fingerprint of the file before it was signed.")
        if (!locked) {
            add("The signer chose to keep the signatures editable, so they can still be moved or removed in a PDF app.")
        }
        val issuer = identity?.issuer
        when {
            identity == null -> Unit
            issuer == null -> add(
                "This file carries a digital signature from a certificate created on the signer's device. " +
                    "Any change to the file after signing makes that signature show as invalid. Other apps " +
                    "may list the certificate as not verified, because no certificate authority issued it.",
            )
            else -> add(
                "This file carries a digital signature from a certificate issued to ${identity.name} by $issuer. " +
                    "Any change to the file after signing makes that signature show as invalid.",
            )
        }
    }

    /**
     * Writes [source] plus the audit page for [trail] to [output]. When [identity] is given, the
     * result is signed by it in [signerName]'s name, with a trusted timestamp if [timestamps] is
     * given and reachable. With [lock], signatures placed as annotations are drawn into their
     * pages first (see [SignatureAnnotation]). [scratch] is a file this may overwrite. A locked
     * [source] opens with [password] and the copy stays locked with it. [onStep] hears, on the
     * calling thread, when each [Step] begins. Returns whether the signature got a timestamp.
     */
    fun write(
        source: File,
        trail: AuditTrail,
        signerName: String,
        identity: SigningIdentity?,
        output: OutputStream,
        scratch: File,
        password: String = "",
        lock: Boolean = true,
        timestamps: TimestampClient? = null,
        onStep: (Step) -> Unit = {},
    ): Boolean {
        onStep(Step.Signing)
        PDDocument.load(source, password).use { document ->
            if (lock) SignatureAnnotation.lock(document)
            AuditPageWriter.append(document, trail, notes(identity, locked = lock))
            PdfDocuments.keepProtection(document, password)
            document.save(scratch)
        }
        try {
            if (identity == null) {
                scratch.inputStream().use { it.copyTo(output) }
                return false
            }
            // Signing appends an incremental update, so PdfBox must read from a file.
            return PDDocument.load(scratch, password).use { document ->
                val signer = DigitalSigner(identity, timestamps) { onStep(Step.Timestamping) }
                signer.sign(document, output, signerName, reason = "Signed with FreePDF")
                signer.timestamped
            }
        } finally {
            scratch.delete()
        }
    }

    /** "Lease.pdf" becomes "Lease (signed).pdf". */
    fun suggestedName(original: String): String {
        val base = if (original.endsWith(".pdf", ignoreCase = true)) original.dropLast(4) else original
        return "$base (signed).pdf"
    }
}
