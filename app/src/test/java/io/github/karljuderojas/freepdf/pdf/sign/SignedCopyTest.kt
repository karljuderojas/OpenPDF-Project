package io.github.karljuderojas.freepdf.pdf.sign

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Date

/**
 * Signs the sample agreement the way Finish does. Runs under Robolectric so FreePdfApp initialises
 * PdfBox-Android's resources. Also writes the result to build/outputs/signed/ so CI can render
 * its signing certificate page and check the signature with poppler's pdfsig.
 */
@RunWith(AndroidJUnit4::class)
class SignedCopyTest {

    private val dir = Files.createTempDirectory("signed-copy").toFile()
    private val source = File(dir, "agreement.pdf").apply {
        outputStream().use { out -> SignedCopyTest::class.java.classLoader!!.getResourceAsStream("sample/agreement.pdf")!!.use { it.copyTo(out) } }
    }
    private val originalSha256 = source.inputStream().use { DocumentHash.sha256(it) }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun sealedCopyHasAnAuditPageAndAValidSignatureOverTheWholeFile() {
        val bytes = write(identity = testIdentity("Dana Whitfield"))
        File("build/outputs/signed").apply { mkdirs() }.resolve("agreement-signed.pdf").writeBytes(bytes)

        PDDocument.load(bytes).use { document ->
            assertEquals(3, document.numberOfPages)
            val auditPage = auditText(document)
            assertTrue(auditPage.contains("Signing certificate"))
            assertTrue(auditPage.contains("Dana Whitfield"))
            assertTrue(auditPage.contains(originalSha256))
            assertTrue(auditPage.contains("I agree to sign this document electronically"))
            assertTrue(auditPage.contains("Any change to the file after signing"))

            val signature = document.signatureDictionaries.single()
            assertEquals("Dana Whitfield", signature.name)
            // The signed byte range runs to the end of the file, so nothing was added unsigned.
            val range = signature.byteRange
            assertEquals(bytes.size, range[2] + range[3])
            assertTrue(verifies(signature.getContents(bytes), signature.getSignedContent(bytes)))
        }
    }

    @Test
    fun anyChangeAfterSigningBreaksTheSignature() {
        val bytes = write(identity = testIdentity("Dana Whitfield"))
        PDDocument.load(bytes).use { document ->
            val signature = document.signatureDictionaries.single()
            val tampered = bytes.copyOf()
            // A byte inside the first page's content, well before the signature.
            tampered[signature.byteRange[1] / 2] = (tampered[signature.byteRange[1] / 2] + 1).toByte()
            assertFalse(verifies(signature.getContents(bytes), signature.getSignedContent(tampered)))
        }
    }

    @Test
    fun unsealedCopyStillGetsTheAuditPage() {
        val bytes = write(identity = null)
        PDDocument.load(bytes).use { document ->
            assertEquals(3, document.numberOfPages)
            assertTrue(document.signatureDictionaries.isEmpty())
            val auditPage = auditText(document)
            assertTrue(auditPage.contains(originalSha256))
            assertFalse(auditPage.contains("Any change to the file after signing"))
        }
    }

    @Test
    fun namesTheSignedCopy() {
        assertEquals("Lease (signed).pdf", SignedCopy.suggestedName("Lease.pdf"))
        assertEquals("Scan (signed).pdf", SignedCopy.suggestedName("Scan.PDF"))
        assertEquals("notes (signed).pdf", SignedCopy.suggestedName("notes"))
    }

    /** The last page's text, with line breaks from wrapping folded into spaces. */
    private fun auditText(document: PDDocument): String =
        PDFTextStripper().apply { startPage = 3; endPage = 3 }.getText(document).replace(Regex("\\s+"), " ")

    private fun write(identity: SigningIdentity?): ByteArray {
        val signedAt = Instant.parse("2026-10-03T15:04:00Z")
        val trail = AuditTrail(
            documentName = "agreement.pdf",
            originalSha256 = originalSha256,
            events = listOf(
                AuditEvent(AuditEvent.Type.Opened, "Dana Whitfield", Instant.parse("2026-10-03T15:01:12Z")),
                AuditEvent(AuditEvent.Type.Signed, "Dana Whitfield", Instant.parse("2026-10-03T15:02:40Z"), "signature on page 2"),
                AuditEvent(AuditEvent.Type.FieldFilled, "Dana Whitfield", Instant.parse("2026-10-03T15:02:51Z"), "date on page 2"),
                AuditEvent(AuditEvent.Type.Completed, "Dana Whitfield", signedAt),
            ),
        ).withSigner(
            SignerRecord(
                name = "Dana Whitfield",
                email = null,
                signedAt = signedAt,
                method = SignatureMethod.Drawn,
                reason = null,
                device = "Google Pixel 7, Android 16",
                consentText = "I agree to sign this document electronically, and my electronic signature is as " +
                    "binding as my handwritten one.",
            ),
        )
        val output = ByteArrayOutputStream()
        SignedCopy.write(source, trail, "Dana Whitfield", identity, output, File(dir, "scratch.pdf"))
        return output.toByteArray()
    }

    /** A software key with a self-signed certificate, standing in for the Keystore one. */
    private fun testIdentity(name: String): SigningIdentity {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=$name, O=FreePDF self-signed")
        val now = System.currentTimeMillis()
        val holder: X509CertificateHolder = JcaX509v3CertificateBuilder(
            subject, BigInteger.valueOf(now), Date(now - 60_000), Date(now + 86_400_000), subject, keys.public,
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(keys.private))
        return SigningIdentity(keys.private, listOf(JcaX509CertificateConverter().getCertificate(holder)))
    }

    private fun verifies(contents: ByteArray, signedContent: ByteArray): Boolean {
        // /Contents is zero-padded to its reserved size, so read just the first DER object.
        val info = ContentInfo.getInstance(ASN1InputStream(contents).use { it.readObject() })
        val cms = CMSSignedData(CMSProcessableByteArray(signedContent), info)
        val signer = cms.signerInfos.signers.single()
        val certificate = cms.certificates.getMatches(null).first()
        return runCatching { signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(certificate)) }.getOrDefault(false)
    }
}
