package io.github.karljuderojas.freepdf.pdf.sign

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import org.bouncycastle.tsp.TimeStampToken
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Gets an RFC 3161 timestamp from a free public timestamp authority. Only a SHA-256 fingerprint
 * of the signature leaves the phone, never the document. The authority signs that fingerprint
 * with the time, which proves when the document was signed without trusting the phone's clock.
 *
 * Servers are tried in order, so the fallback is used only when the first cannot be reached.
 */
class TimestampClient(
    private val servers: List<String> = DEFAULT_SERVERS,
    private val timeoutMs: Int = 10_000,
) {

    /** A token over the SHA-256 of [data]. Throws [IOException] if no server gives a valid one. */
    fun stamp(data: ByteArray): TimeStampToken {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val nonce = BigInteger(64, SecureRandom())
        val request = TimeStampRequestGenerator().apply { setCertReq(true) }
            .generate(NISTObjectIdentifiers.id_sha256, digest, nonce)
        var lastError: Exception? = null
        for (server in servers) {
            try {
                val response = TimeStampResponse(post(server, request.encoded))
                // Checks the status, that the token answers this request and its nonce, and the imprint.
                response.validate(request)
                return response.timeStampToken ?: throw IOException("$server sent no token")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("No timestamp server answered", lastError)
    }

    private fun post(server: String, body: ByteArray): ByteArray {
        val connection = URL(server).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Content-Type", "application/timestamp-query")
            connection.setRequestProperty("Accept", "application/timestamp-reply")
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("$server answered ${connection.responseCode}")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /**
         * Free, public authorities that need no account. FreeTSA first; DigiCert's public
         * server as the fallback. Both are free to use; neither is paid for by FreePDF.
         */
        val DEFAULT_SERVERS = listOf(
            "https://freetsa.org/tsr",
            "http://timestamp.digicert.com",
        )
    }
}
