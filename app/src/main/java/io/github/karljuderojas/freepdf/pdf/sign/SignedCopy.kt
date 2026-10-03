package io.github.karljuderojas.freepdf.pdf.sign

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.OutputStream

/**
 * Builds the signed copy that Finish saves: the document as signed, a "Signing certificate" page
 * at the end, and optionally a digital signature over all of it, which makes the copy
 * tamper-evident. The user's original file is never touched.
 */
object SignedCopy {

    /** What the audit page says the record does and does not prove. */
    fun notes(sealed: Boolean): List<String> = buildList {
        add(
            "This record was made on the signer's own device by FreePDF. The signer's identity was not " +
                "checked by email, phone or ID.",
        )
        add("Original SHA-256 is the fingerprint of the file before it was signed.")
        if (sealed) {
            add(
                "This file carries a digital signature from a certificate created on the signer's device. " +
                    "Any change to the file after signing makes that signature show as invalid. Other apps " +
                    "may list the certificate as not verified, because no certificate authority issued it.",
            )
        }
    }

    /**
     * Writes [source] plus the audit page for [trail] to [output]. When [identity] is given, the
     * result is signed by it in [signerName]'s name. [scratch] is a file this may overwrite.
     */
    fun write(
        source: File,
        trail: AuditTrail,
        signerName: String,
        identity: SigningIdentity?,
        output: OutputStream,
        scratch: File,
    ) {
        PDDocument.load(source).use { document ->
            AuditPageWriter.append(document, trail, notes(sealed = identity != null))
            document.save(scratch)
        }
        try {
            if (identity == null) {
                scratch.inputStream().use { it.copyTo(output) }
            } else {
                // Signing appends an incremental update, so PdfBox must read from a file.
                PDDocument.load(scratch).use { document ->
                    DigitalSigner(identity).sign(document, output, signerName, reason = "Signed with FreePDF")
                }
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
