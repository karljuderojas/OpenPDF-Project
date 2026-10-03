package io.github.karljuderojas.freepdf.pdf.sign

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.SignerInformationStore
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar

/**
 * Applies a standard PDF digital signature (detached PKCS#7 / CMS over the byte range), the kind
 * Acrobat shows in its signature panel. Any change to the file after signing breaks it, which is
 * what makes the signed document tamper-evident.
 *
 * With a [timestamps] client, the signature also carries an RFC 3161 timestamp, which proves when
 * it was made independently of the phone's clock. If no timestamp server can be reached the file
 * is still signed, without one, and [timestamped] says so. Long-term validation is planned; see
 * docs/signing-design.md.
 */
class DigitalSigner(
    private val identity: SigningIdentity,
    private val timestamps: TimestampClient? = null,
) : SignatureInterface {

    /** After [sign]: whether the signature got a timestamp. */
    var timestamped = false
        private set

    /**
     * Signs [document] and writes the result to [output] as an incremental update, so earlier
     * signatures stay valid. [document] must have been loaded from a file or stream.
     */
    fun sign(
        document: PDDocument,
        output: OutputStream,
        signerName: String,
        reason: String? = null,
        location: String? = null,
    ) {
        val signature = PDSignature().apply {
            setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
            setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
            name = signerName
            this.reason = reason
            this.location = location
            signDate = Calendar.getInstance()
        }
        // A timestamp token carries its authority's certificates, and an imported identity may
        // bring a chain of its own, so reserve more room than PdfBox's default in those cases.
        val options = SignatureOptions().apply {
            if (timestamps != null || identity.chain.size > 1) preferredSignatureSize = LARGE_SIGNATURE_BYTES
        }
        options.use {
            document.addSignature(signature, this, it)
            document.saveIncremental(output)
        }
    }

    /** Called by PdfBox with the bytes to sign (everything except the signature placeholder). */
    override fun sign(content: InputStream): ByteArray {
        val certificate = identity.chain.first()
        val algorithm = when (identity.privateKey.algorithm) {
            "EC" -> "SHA256withECDSA"
            else -> "SHA256withRSA"
        }
        val contentSigner = JcaContentSignerBuilder(algorithm).build(identity.privateKey)
        val generator = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                    .build(contentSigner, certificate),
            )
            addCertificates(JcaCertStore(identity.chain))
        }
        val signed = generator.generate(CMSProcessableByteArray(content.readBytes()), false)
        timestamped = false
        val client = timestamps ?: return signed.encoded
        return runCatching { withTimestamp(signed, client) }
            .onSuccess { timestamped = true }
            .getOrDefault(signed)
            .encoded
    }

    /** Adds a signature timestamp token (RFC 3161) over the signature value as an unsigned attribute. */
    private fun withTimestamp(signed: CMSSignedData, client: TimestampClient): CMSSignedData {
        val signers = signed.signerInfos.signers.map { signer ->
            val token = client.stamp(signer.signature)
            val attributes = ASN1EncodableVector().apply {
                signer.unsignedAttributes?.toASN1EncodableVector()?.let { addAll(it) }
                add(
                    Attribute(
                        PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                        DERSet(token.toCMSSignedData().toASN1Structure()),
                    ),
                )
            }
            SignerInformation.replaceUnsignedAttributes(signer, AttributeTable(attributes))
        }
        return CMSSignedData.replaceSigners(signed, SignerInformationStore(signers))
    }

    private companion object {
        // Bytes; written as twice as many hex digits. PdfBox's default is 9472.
        const val LARGE_SIGNATURE_BYTES = 32_768
    }
}
