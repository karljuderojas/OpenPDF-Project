package io.github.karljuderojas.freepdf.pdf.sign

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import java.util.Date

/**
 * Signs the sample agreement and checks it the way the viewer does when a signed PDF is opened.
 * Runs under Robolectric so FreePdfApp initialises PdfBox-Android's resources.
 */
@RunWith(AndroidJUnit4::class)
class SignatureVerifierTest {

    private val original: ByteArray =
        javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf")!!.use { it.readBytes() }

    @Test
    fun unsignedFileHasNoReport() {
        assertTrue(SignatureVerifier(emptySet()).verify(original).isEmpty())
        assertNull(SignaturesVerdict.of(emptyList()))
    }

    @Test
    fun selfSignedCopyIsUnchangedAndNamesTheSigner() {
        val signed = sign(original, TestCertificates.selfSigned("Dana Whitfield"))
        val report = SignatureVerifier(emptySet()).verify(signed).single()
        assertEquals(SignatureReport.Integrity.Intact, report.integrity)
        assertTrue(report.coversWholeFile)
        assertEquals("Dana Whitfield", report.signer)
        assertEquals(SignatureReport.Trust.SelfSigned, report.trust)
        assertNull(report.issuer?.takeIf { it != "Dana Whitfield" })
        assertNotNull(report.signingTime)
        assertNull(report.timestamp)
        assertEquals(SignaturesVerdict.Unchanged, SignaturesVerdict.of(listOf(report)))
    }

    @Test
    fun changedByteBreaksTheSignature() {
        val signed = sign(original, TestCertificates.selfSigned("Dana Whitfield"))
        // A byte inside the first page, well before the signature.
        signed[signed.size / 4] = (signed[signed.size / 4] + 1).toByte()
        val reports = SignatureVerifier(emptySet()).verify(signed)
        assertEquals(SignatureReport.Integrity.Broken, reports.single().integrity)
        assertEquals(SignaturesVerdict.Invalid, SignaturesVerdict.of(reports))
    }

    @Test
    fun aSignatureWithABrokenByteRangeIsMalformedNotFatal() {
        val signed = sign(original, TestCertificates.selfSigned("Dana Whitfield"))
        // Rewrite "/ByteRange [0 a b c]" in place as three entries, keeping the file the same length.
        val text = String(signed, Charsets.ISO_8859_1)
        val start = text.indexOf("/ByteRange [")
        val end = text.indexOf(']', start)
        val broken = text.substring(0, start) + "/ByteRange [0 10 20".padEnd(end - start, ' ') + text.substring(end)
        val reports = SignatureVerifier(emptySet()).verify(broken.toByteArray(Charsets.ISO_8859_1))
        assertEquals(SignatureReport.Integrity.Malformed, reports.single().integrity)
        assertEquals(SignaturesVerdict.CannotCheck, SignaturesVerdict.of(reports))
    }

    @Test
    fun changesSavedAfterSigningAreFlagged() {
        val signed = sign(original, TestCertificates.selfSigned("Dana Whitfield"))
        val edited = ByteArrayOutputStream().also { out ->
            PDDocument.load(signed).use { document ->
                document.documentInformation.title = "Edited after signing"
                document.documentInformation.cosObject.isNeedToBeUpdated = true
                document.saveIncremental(out)
            }
        }.toByteArray()
        val report = SignatureVerifier(emptySet()).verify(edited).single()
        // The signed part still matches, but something was added after it.
        assertTrue(report.intact)
        assertFalse(report.coversWholeFile)
        assertEquals(SignaturesVerdict.ChangedAfterSigning, SignaturesVerdict.of(listOf(report)))
    }

    @Test
    fun secondSignatureKeepsTheFirstValid() {
        val once = sign(original, TestCertificates.selfSigned("Dana Whitfield"))
        val twice = sign(once, TestCertificates.selfSigned("Sam Ortiz"))
        val reports = SignatureVerifier(emptySet()).verify(twice)
        assertEquals(listOf("Dana Whitfield", "Sam Ortiz"), reports.map { it.signer })
        assertTrue(reports.all { it.intact })
        assertFalse(reports[0].coversWholeFile)
        assertTrue(reports[1].coversWholeFile)
        assertEquals(SignaturesVerdict.Unchanged, SignaturesVerdict.of(reports))
    }

    @Test
    fun certificateFromATrustedAuthorityIsTrusted() {
        val authority = TestCertificates.Authority("Example Root CA")
        val signed = sign(original, authority.issue("Dana Whitfield"))

        val trusted = SignatureVerifier(setOf(authority.root)).verify(signed).single()
        assertEquals(SignatureReport.Trust.Trusted, trusted.trust)
        assertEquals("Example Root CA", trusted.issuer)
        assertEquals(listOf("Dana Whitfield", "Example Root CA"), trusted.chain.map { it.name })

        val unknown = SignatureVerifier(setOf(TestCertificates.Authority("Other Root").root)).verify(signed).single()
        assertEquals(SignatureReport.Trust.Unknown, unknown.trust)
        assertTrue(unknown.intact)
    }

    @Test
    fun timestampIsAddedAndChecked() {
        TestCertificates.FakeTimestampServer().use { server ->
            val client = TimestampClient(listOf(TestCertificates.deadUrl(), server.url), timeoutMs = 2_000)
            val (signed, timestamped) = signWithTimestamp(original, TestCertificates.selfSigned("Dana Whitfield"), client)
            assertTrue(timestamped)
            assertEquals(1, server.requests)
            val report = SignatureVerifier(emptySet()).verify(signed).single()
            assertTrue(report.intact)
            val stamp = report.timestamp!!
            assertTrue(stamp.valid)
            assertEquals("Test Timestamp Authority", stamp.authority)
            assertTrue(Date().time - stamp.time.toEpochMilli() < 60_000)
        }
    }

    @Test
    fun signsWithoutATimestampWhenOffline() {
        val client = TimestampClient(listOf(TestCertificates.deadUrl()), timeoutMs = 2_000)
        val (signed, timestamped) = signWithTimestamp(original, TestCertificates.selfSigned("Dana Whitfield"), client)
        assertFalse(timestamped)
        val report = SignatureVerifier(emptySet()).verify(signed).single()
        assertTrue(report.intact)
        assertNull(report.timestamp)
    }

    @Test
    fun importsAPkcs12File() {
        val authority = TestCertificates.Authority("Example Root CA")
        val identity = authority.issue("Dana Whitfield")
        val password = "correct horse".toCharArray()
        val p12 = ByteArrayOutputStream().also { out ->
            KeyStore.getInstance("PKCS12", BouncyCastleProvider()).apply {
                load(null)
                setKeyEntry("dana", identity.privateKey, password, identity.chain.toTypedArray())
            }.store(out, password)
        }.toByteArray()

        val imported = SigningIdentity.fromPkcs12(p12, password)
        assertEquals("Dana Whitfield", imported.name)
        assertEquals("Example Root CA", imported.issuer)
        assertEquals(2, imported.chain.size)
        assertThrows(Exception::class.java) { SigningIdentity.fromPkcs12(p12, "wrong".toCharArray()) }

        // A signature made with it names the person and the authority.
        val report = SignatureVerifier(setOf(authority.root)).verify(sign(original, imported)).single()
        assertEquals("Dana Whitfield", report.signer)
        assertEquals(SignatureReport.Trust.Trusted, report.trust)
    }

    @Test
    fun auditPageDescribesWhoIssuedTheCertificate() {
        val device = SignedCopy.notes(TestCertificates.selfSigned("Dana Whitfield")).last()
        assertTrue(device.contains("created on the signer's device"))
        val issued = SignedCopy.notes(TestCertificates.Authority("Example Root CA").issue("Dana Whitfield")).last()
        assertTrue(issued.contains("issued to Dana Whitfield by Example Root CA"))
        assertEquals(2, SignedCopy.notes(null).size)
    }

    private fun sign(bytes: ByteArray, identity: SigningIdentity): ByteArray =
        signWithTimestamp(bytes, identity, null).first

    private fun signWithTimestamp(bytes: ByteArray, identity: SigningIdentity, client: TimestampClient?): Pair<ByteArray, Boolean> {
        // Incremental signing needs the document loaded from a file.
        val file = File(Files.createTempDirectory("verify").toFile(), "in.pdf").apply { writeBytes(bytes) }
        val out = ByteArrayOutputStream()
        val signer = DigitalSigner(identity, client)
        PDDocument.load(file).use { signer.sign(it, out, identity.name) }
        file.parentFile!!.deleteRecursively()
        return out.toByteArray() to signer.timestamped
    }
}
