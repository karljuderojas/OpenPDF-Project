package io.github.karljuderojas.freepdf.pdf.sign

import java.time.Instant
import java.util.UUID

/**
 * The record that makes a signature defensible: who did what, when, on which exact file.
 * See docs/signing-design.md for how this is captured and printed.
 */
data class AuditTrail(
    val documentId: String = UUID.randomUUID().toString(),
    val documentName: String,
    val originalSha256: String,
    val events: List<AuditEvent> = emptyList(),
    val signers: List<SignerRecord> = emptyList(),
) {
    fun record(event: AuditEvent): AuditTrail = copy(events = events + event)

    fun withSigner(signer: SignerRecord): AuditTrail = copy(signers = signers + signer)
}

data class AuditEvent(
    val type: Type,
    val actor: String,
    val at: Instant = Instant.now(),
    val detail: String? = null,
) {
    enum class Type { Opened, Viewed, FieldFilled, Signed, Declined, Completed, Verified }
}

data class SignerRecord(
    val name: String,
    val email: String?,
    val signedAt: Instant,
    val method: SignatureMethod,
    val reason: String?,
    val device: String,
    val consentText: String,
)

enum class SignatureMethod { Drawn, Typed, Uploaded }
