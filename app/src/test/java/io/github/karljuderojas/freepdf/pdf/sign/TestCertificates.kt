package io.github.karljuderojas.freepdf.pdf.sign

import com.sun.net.httpserver.HttpServer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequest
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

/** Software keys and certificates for tests, standing in for the Keystore and real authorities. */
object TestCertificates {

    private val serial = AtomicLong(System.currentTimeMillis())

    fun keyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    /** A certificate for [subject] signed by [issuerKeys] (itself when null, so self-signed). */
    fun certificate(
        subject: String,
        keys: KeyPair,
        issuer: X509Certificate? = null,
        issuerKeys: KeyPair? = null,
        ca: Boolean = false,
        timestamping: Boolean = false,
        validFrom: Date = Date(System.currentTimeMillis() - 86_400_000),
        validTo: Date = Date(System.currentTimeMillis() + 365L * 86_400_000),
    ): X509Certificate {
        val issuerName = issuer?.let { X500Name.getInstance(it.subjectX500Principal.encoded) } ?: X500Name(subject)
        val builder = JcaX509v3CertificateBuilder(
            issuerName, BigInteger.valueOf(serial.incrementAndGet()), validFrom, validTo, X500Name(subject), keys.public,
        )
        if (ca) builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        if (timestamping) {
            builder.addExtension(Extension.extendedKeyUsage, true, ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
        }
        val signer = JcaContentSignerBuilder("SHA256withRSA").build((issuerKeys ?: keys).private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    /** A self-signed identity, like FreePDF's device certificate. */
    fun selfSigned(name: String): SigningIdentity {
        val keys = keyPair()
        return SigningIdentity(keys.private, listOf(certificate("CN=$name, O=FreePDF self-signed", keys)))
    }

    /** A root authority and an identity it issued to [name]. */
    class Authority(val name: String = "Test Root CA") {
        val keys = keyPair()
        val root = certificate("CN=$name, O=Test Trust", keys, ca = true)

        fun issue(person: String, validTo: Date = Date(System.currentTimeMillis() + 365L * 86_400_000)): SigningIdentity {
            val keys = keyPair()
            val leaf = certificate("CN=$person, O=Example Ltd", keys, issuer = root, issuerKeys = this.keys, validTo = validTo)
            return SigningIdentity(keys.private, listOf(leaf, root))
        }
    }

    /** A local RFC 3161 timestamp authority on a free port. Close it when done. */
    class FakeTimestampServer : AutoCloseable {
        private val keys = keyPair()
        private val certificate = certificate("CN=Test Timestamp Authority, O=Test Trust", keys, timestamping = true)
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requests = 0
            private set

        init {
            server.createContext("/tsr") { exchange ->
                requests++
                val request = TimeStampRequest(exchange.requestBody.readBytes())
                val generator = TimeStampTokenGenerator(
                    JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withRSA", keys.private, certificate),
                    JcaDigestCalculatorProviderBuilder().build().get(
                        org.bouncycastle.asn1.x509.AlgorithmIdentifier(org.bouncycastle.asn1.oiw.OIWObjectIdentifiers.idSHA1),
                    ),
                    ASN1ObjectIdentifier("1.2.3.4.1"),
                ).apply { addCertificates(JcaCertStore(listOf(certificate))) }
                val response = TimeStampResponseGenerator(generator, TSPAlgorithms.ALLOWED)
                    .generate(request, BigInteger.valueOf(serial.incrementAndGet()), Date())
                val body = response.encoded
                exchange.responseHeaders.add("Content-Type", "application/timestamp-reply")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
        }

        val url: String get() = "http://127.0.0.1:${server.address.port}/tsr"

        override fun close() = server.stop(0)
    }

    /** A URL nothing listens on, for "offline". */
    fun deadUrl(): String {
        val socket = java.net.ServerSocket(0)
        val port = socket.localPort
        socket.close()
        return "http://127.0.0.1:$port/tsr"
    }
}
