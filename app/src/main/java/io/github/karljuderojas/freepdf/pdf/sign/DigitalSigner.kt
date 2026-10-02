package io.github.karljuderojas.freepdf.pdf.sign

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
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
 * Timestamping (RFC 3161) and long-term validation are planned; see docs/signing-design.md.
 */
class DigitalSigner(private val identity: SigningIdentity) : SignatureInterface {

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
        document.addSignature(signature, this)
        document.saveIncremental(output)
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
        return generator.generate(CMSProcessableByteArray(content.readBytes()), false).encoded
    }
}
