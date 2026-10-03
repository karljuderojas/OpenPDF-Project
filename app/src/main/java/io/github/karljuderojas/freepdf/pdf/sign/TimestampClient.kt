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
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Gets an RFC 3161 timestamp from a free public timestamp authority. Only a SHA-256 fingerprint
 * of the signature leaves the phone, never the document. The authority signs that fingerprint
 * with the time, which proves when the document was signed without trusting the phone's clock.
 *
 * Servers are tried in order, so the fallback is used only when the first cannot be reached.
 * The whole attempt, every server included, gives up after [timeoutMs], so an offline phone
 * waits about ten seconds at most rather than a connect and a read timeout per server.
 */
class TimestampClient(
    private val servers: List<String> = DEFAULT_SERVERS,
    private val timeoutMs: Long = 10_000,
) {

    /**
     * A token over the SHA-256 of [data]. Throws [IOException] if no server gives a valid one
     * within [timeoutMs] in all.
     */
    fun stamp(data: ByteArray): TimeStampToken {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val nonce = BigInteger(64, SecureRandom())
        val request = TimeStampRequestGenerator().apply { setCertReq(true) }
            .generate(NISTObjectIdentifiers.id_sha256, digest, nonce)
        val deadline = System.currentTimeMillis() + timeoutMs
        // A daemon thread does the networking, so a server that keeps a socket open past its own
        // timeouts cannot hold up signing: the deadline is enforced here regardless.
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "timestamp").apply { isDaemon = true } }
        try {
            val future = executor.submit<TimeStampToken> {
                var lastError: Exception? = null
                for (server in servers) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) break
                    try {
                        val response = TimeStampResponse(post(server, request.encoded, remaining.toInt()))
                        // Checks the status, that the token answers this request and its nonce, and the imprint.
                        response.validate(request)
                        return@submit response.timeStampToken ?: throw IOException("$server sent no token")
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
                throw IOException("No timestamp server answered", lastError)
            }
            return try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                throw IOException("No timestamp server answered within $timeoutMs ms", e)
            } catch (e: ExecutionException) {
                throw e.cause as? IOException ?: IOException("Timestamp request failed", e.cause)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun post(server: String, body: ByteArray, timeoutMs: Int): ByteArray {
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
