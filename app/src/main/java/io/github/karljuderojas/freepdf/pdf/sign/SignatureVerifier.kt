package io.github.karljuderojas.freepdf.pdf.sign

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider
import org.bouncycastle.tsp.TimeStampToken
import org.bouncycastle.util.Selector
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.time.Instant
import javax.security.auth.x500.X500Principal

/**
 * Checks the digital signatures in a PDF someone sent: whether each one still matches the bytes
 * it covers, whether anything was added to the file after it, who the certificate names and who
 * vouches for that, and any trusted timestamp. Runs entirely on the phone.
 *
 * Trust is judged against [trustAnchors], which on a phone are the system's trusted certificate
 * authorities. Those are the web's authorities, not the Adobe Approved Trust List, so a certificate
 * that desktop PDF readers trust can show here as "issuer not recognised". The wording in the app says so.
 */
class SignatureVerifier(private val trustAnchors: Set<X509Certificate> = systemTrustAnchors()) {

    /**
     * As [verify] for bytes, but reads the whole file into memory only when it has signatures.
     * [password] opens a protected PDF; without it a locked file reports no signatures.
     */
    fun verify(file: File, password: String = ""): List<SignatureReport> {
        val signed = runCatching { PDDocument.load(file, password).use { it.signatureDictionaries.isNotEmpty() } }.getOrDefault(false)
        return if (signed) verify(file.readBytes(), password) else emptyList()
    }

    /**
     * Every signature in [bytes], oldest first. Empty when the file has none or is not a PDF.
     * A signature dictionary that cannot even be read (a broken /ByteRange, say) is reported as
     * malformed rather than thrown, so one bad signature never stops the document opening.
     */
    fun verify(bytes: ByteArray, password: String = ""): List<SignatureReport> {
        val signatures = runCatching {
            PDDocument.load(bytes, password).use { document ->
                document.signatureDictionaries.map { it to runCatching { it.getContents(bytes) }.getOrNull() }
            }
        }.getOrElse { return emptyList() }
        val sorted = signatures.sortedBy { (signature, _) -> runCatching { signedEnd(signature.byteRange) }.getOrDefault(0L) }
        return sorted.map { (signature, contents) ->
            runCatching { check(signature, contents, bytes) }.getOrElse { malformed(signature) }
        }
    }

    private fun malformed(signature: PDSignature) = runCatching { base(signature) }
        .getOrElse { SignatureReport(null, null, null, null, null, SignatureReport.Kind.Unsupported) }
        .copy(integrity = SignatureReport.Integrity.Malformed)

    private fun base(signature: PDSignature) = SignatureReport(
        fieldName = signature.name,
        claimedSigner = signature.name,
        claimedTime = signature.signDate?.toInstant(),
        reason = signature.reason,
        location = signature.location,
        kind = SignatureReport.Kind.forSubFilter(signature.subFilter),
    )

    private fun signedEnd(range: IntArray): Long = if (range.size == 4) range[2].toLong() + range[3] else 0L

    private fun check(signature: PDSignature, contents: ByteArray?, file: ByteArray): SignatureReport {
        val range = signature.byteRange
        val base = base(signature)
        if (contents == null || range.size != 4 || !rangeIsWellFormed(range, file)) {
            return base.copy(integrity = SignatureReport.Integrity.Malformed)
        }
        val signedEnd = range[2] + range[3]
        val signed = file.copyOfRange(range[0], range[1]) + file.copyOfRange(range[2], signedEnd)
        val withCoverage = base.copy(coversWholeFile = signedEnd == file.size, signedRevisionLength = signedEnd)
        return runCatching {
            when (withCoverage.kind) {
                SignatureReport.Kind.DocumentTimestamp -> checkDocumentTimestamp(withCoverage, contents, signed)
                SignatureReport.Kind.Unsupported -> withCoverage.copy(integrity = SignatureReport.Integrity.Unsupported)
                else -> checkCms(withCoverage, contents, signed)
            }
        }.getOrElse { withCoverage.copy(integrity = SignatureReport.Integrity.Malformed) }
    }

    /**
     * The byte range must start at 0 and its one gap must be exactly the /Contents hex string.
     * Otherwise someone could leave other parts of the file unsigned. Sums are done in Long so
     * a hostile range cannot overflow past the checks.
     */
    private fun rangeIsWellFormed(range: IntArray, file: ByteArray): Boolean {
        if (range[0] != 0 || range[1] <= 0 || range[2] <= range[1] || range[3] < 0) return false
        if (range[1] >= file.size || range[2] > file.size) return false
        if (range[2].toLong() + range[3] > file.size) return false
        if (file[range[1]] != '<'.code.toByte() || file[range[2] - 1] != '>'.code.toByte()) return false
        for (i in range[1] + 1 until range[2] - 1) {
            val c = file[i].toInt().toChar()
            if (!(c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F')) return false
        }
        return true
    }

    private fun checkCms(report: SignatureReport, contents: ByteArray, signed: ByteArray): SignatureReport {
        val info = ContentInfo.getInstance(ASN1InputStream(contents).use { it.readObject() })
        // adbe.pkcs7.sha1 signs the SHA-1 of the range, carried inside the signature.
        val cms = if (report.kind == SignatureReport.Kind.Pkcs7Sha1) {
            CMSSignedData(info)
        } else {
            CMSSignedData(CMSProcessableByteArray(signed), info)
        }
        val signer = cms.signerInfos.signers.first()
        val certificates = cms.certificates.getMatches(null).map { converter.getCertificate(it) }
        @Suppress("UNCHECKED_CAST")
        val signerHolder = cms.certificates.getMatches(signer.sid as Selector<X509CertificateHolder>).firstOrNull()
            ?: return report.copy(integrity = SignatureReport.Integrity.Malformed)
        val signerCertificate = converter.getCertificate(signerHolder)

        var intact = runCatching { signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(signerHolder)) }.getOrDefault(false)
        if (intact && report.kind == SignatureReport.Kind.Pkcs7Sha1) {
            val carried = (cms.signedContent?.content as? ByteArray)
                ?: ASN1OctetString.getInstance(cms.signedContent?.content).octets
            intact = MessageDigest.getInstance("SHA-1").digest(signed).contentEquals(carried)
        }

        val timestamp = timestampOf(signer)
        val signingTime = signer.signedAttributes?.get(CMSAttributes.signingTime)?.let {
            Time.getInstance(it.attrValues.getObjectAt(0)).date.toInstant()
        }
        val checkAt = timestamp?.time ?: signingTime ?: report.claimedTime ?: Instant.now()
        val chain = orderChain(signerCertificate, certificates)
        return report.copy(
            integrity = if (intact) SignatureReport.Integrity.Intact else SignatureReport.Integrity.Broken,
            signer = commonName(signerCertificate) ?: report.claimedSigner,
            signerEmail = emailOf(signerCertificate),
            issuer = commonName(signerCertificate.issuerX500Principal),
            signingTime = signingTime ?: report.claimedTime,
            timestamp = timestamp,
            certificateValidAtSigning = runCatching { signerCertificate.checkValidity(java.util.Date.from(checkAt)) }.isSuccess,
            trust = trustOf(chain),
            chain = chain.map { CertificateSummary(commonName(it) ?: it.subjectX500Principal.name, commonName(it.issuerX500Principal), it.notAfter.toInstant()) },
        )
    }

    /** ETSI.RFC3161: the whole "signature" is a timestamp token over the byte range. */
    private fun checkDocumentTimestamp(report: SignatureReport, contents: ByteArray, signed: ByteArray): SignatureReport {
        val token = TimeStampToken(CMSSignedData(ASN1InputStream(contents).use { it.readObject() }.encoded))
        val stamp = checkToken(token, signed)
        return report.copy(
            integrity = if (stamp.valid) SignatureReport.Integrity.Intact else SignatureReport.Integrity.Broken,
            signer = stamp.authority,
            timestamp = stamp,
            signingTime = stamp.time,
            trust = SignatureReport.Trust.Unknown,
        )
    }

    /**
     * The signature's timestamp, if it carries one. A token that is present but cannot be read
     * is reported as an invalid timestamp, not as no timestamp: a damaged or forged token must
     * not quietly fall back to the signer's own clock.
     */
    private fun timestampOf(signer: SignerInformation): TimestampReport? {
        val attribute: Attribute = signer.unsignedAttributes?.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken) ?: return null
        return runCatching {
            val token = TimeStampToken(CMSSignedData(attribute.attrValues.getObjectAt(0).toASN1Primitive().encoded))
            checkToken(token, signer.signature)
        }.getOrElse { TimestampReport(time = Instant.EPOCH, authority = null, valid = false) }
    }

    /** A timestamp counts only if its imprint matches [stamped] and its own signature checks out. */
    private fun checkToken(token: TimeStampToken, stamped: ByteArray): TimestampReport {
        val info = token.timeStampInfo
        val digest = BcDigestCalculatorProvider().get(AlgorithmIdentifier(info.messageImprintAlgOID)).run {
            outputStream.use { it.write(stamped) }
            this.digest
        }
        @Suppress("UNCHECKED_CAST")
        val holder = token.certificates.getMatches(token.sid as Selector<X509CertificateHolder>).firstOrNull()
        val signatureOk = holder != null &&
            runCatching { token.validate(JcaSimpleSignerInfoVerifierBuilder().build(holder)); true }.getOrDefault(false)
        val authority = holder?.let { commonName(converter.getCertificate(it)) }
            ?: info.tsa?.name?.let { runCatching { commonName(X500Name.getInstance(it)) }.getOrNull() }
        return TimestampReport(
            time = info.genTime.toInstant(),
            authority = authority,
            valid = signatureOk && digest.contentEquals(info.messageImprintDigest),
        )
    }

    private fun trustOf(chain: List<X509Certificate>): SignatureReport.Trust {
        val leaf = chain.first()
        if (chain.size == 1 && leaf.subjectX500Principal == leaf.issuerX500Principal) return SignatureReport.Trust.SelfSigned
        if (trustAnchors.isEmpty()) return SignatureReport.Trust.Unknown
        val trusted = runCatching {
            val path = CertificateFactory.getInstance("X.509").generateCertPath(chain.filter { it !in trustAnchors })
            val parameters = PKIXParameters(trustAnchors.map { TrustAnchor(it, null) }.toSet()).apply {
                // Revocation needs the network; checking it is part of long-term validation, later.
                isRevocationEnabled = false
            }
            CertPathValidator.getInstance("PKIX").validate(path, parameters)
        }.isSuccess
        return if (trusted) SignatureReport.Trust.Trusted else SignatureReport.Trust.Unknown
    }

    /** The signer's certificate first, then each issuer found among [all]. */
    private fun orderChain(leaf: X509Certificate, all: List<X509Certificate>): List<X509Certificate> {
        val chain = mutableListOf(leaf)
        while (chain.size < 10) {
            val last = chain.last()
            if (last.subjectX500Principal == last.issuerX500Principal) break
            val issuer = all.firstOrNull { it.subjectX500Principal == last.issuerX500Principal && it !in chain } ?: break
            chain += issuer
        }
        return chain
    }

    private val converter = JcaX509CertificateConverter()

    companion object {
        /** The phone's trusted root certificates, or none where there is no such store. */
        fun systemTrustAnchors(): Set<X509Certificate> = runCatching {
            val store = java.security.KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            store.aliases().toList().mapNotNull { store.getCertificate(it) as? X509Certificate }.toSet()
        }.getOrDefault(emptySet())

        internal fun commonName(certificate: X509Certificate): String? = commonName(certificate.subjectX500Principal)

        /** The CN of [principal], or its organisation when it has no CN. */
        internal fun commonName(principal: X500Principal): String? =
            runCatching { commonName(X500Name.getInstance(principal.encoded)) }.getOrNull()

        private fun commonName(name: X500Name): String? =
            (name.getRDNs(BCStyle.CN).firstOrNull() ?: name.getRDNs(BCStyle.O).firstOrNull())
                ?.first?.value?.let { IETFUtils.valueToString(it) }

        private fun emailOf(certificate: X509Certificate): String? =
            certificate.subjectAlternativeNames?.firstOrNull { it[0] == 1 }?.get(1) as? String
                ?: runCatching {
                    X500Name.getInstance(certificate.subjectX500Principal.encoded).getRDNs(BCStyle.EmailAddress).firstOrNull()
                        ?.first?.value?.let { IETFUtils.valueToString(it) }
                }.getOrNull()
    }
}

/** What [SignatureVerifier] found about one signature. */
data class SignatureReport(
    val fieldName: String?,
    val claimedSigner: String?,
    val claimedTime: Instant?,
    val reason: String?,
    val location: String?,
    val kind: Kind,
    val integrity: Integrity = Integrity.Malformed,
    /** False when bytes were added to the file after this signature. */
    val coversWholeFile: Boolean = false,
    val signedRevisionLength: Int = 0,
    /** Who the certificate names; the typed name in the PDF if there is no certificate. */
    val signer: String? = null,
    val signerEmail: String? = null,
    val issuer: String? = null,
    val signingTime: Instant? = null,
    val timestamp: TimestampReport? = null,
    val certificateValidAtSigning: Boolean = true,
    val trust: Trust = Trust.Unknown,
    val chain: List<CertificateSummary> = emptyList(),
) {
    enum class Kind {
        Pkcs7Detached, CadesDetached, Pkcs7Sha1, DocumentTimestamp, Unsupported;

        companion object {
            fun forSubFilter(subFilter: String?): Kind = when (subFilter) {
                "adbe.pkcs7.detached" -> Pkcs7Detached
                "ETSI.CAdES.detached" -> CadesDetached
                "adbe.pkcs7.sha1" -> Pkcs7Sha1
                "ETSI.RFC3161" -> DocumentTimestamp
                else -> Unsupported
            }
        }
    }

    /** Whether the signed bytes still match the signature. */
    enum class Integrity { Intact, Broken, Malformed, Unsupported }

    /** Who vouches for the signer's name. */
    enum class Trust {
        /** The chain ends at a root this phone trusts. */
        Trusted,

        /** Made by the signer themselves, as FreePDF's device certificate is. */
        SelfSigned,

        /** Issued by someone this phone does not know. */
        Unknown,
    }

    val intact: Boolean get() = integrity == Integrity.Intact
}

data class TimestampReport(val time: Instant, val authority: String?, val valid: Boolean)

data class CertificateSummary(val name: String, val issuer: String?, val expires: Instant)

/** The one-line verdict for the banner, over every signature in a file. */
enum class SignaturesVerdict {
    /** Every signature matches, and nothing unsigned was added after the last one. */
    Unchanged,

    /** Signatures match, but the file was changed after the last one was applied. */
    ChangedAfterSigning,

    /** At least one signature no longer matches what it signed. */
    Invalid,

    /** Could not be checked, for example an old or unusual signature format. */
    CannotCheck;

    companion object {
        fun of(reports: List<SignatureReport>): SignaturesVerdict? = when {
            reports.isEmpty() -> null
            reports.any { it.integrity == SignatureReport.Integrity.Broken } -> Invalid
            reports.any { !it.intact } -> CannotCheck
            !reports.last().coversWholeFile -> ChangedAfterSigning
            else -> Unchanged
        }
    }
}
