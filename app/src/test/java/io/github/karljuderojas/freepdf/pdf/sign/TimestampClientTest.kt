package io.github.karljuderojas.freepdf.pdf.sign

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch

/** The client gives up on time, whatever the servers do, so an offline Finish is never a long wait. */
class TimestampClientTest {

    @Test
    fun givesUpAfterTheOverallTimeoutWhenAServerNeverAnswers() {
        // Accepts the request and then keeps the connection open without replying.
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/tsr") { exchange ->
                exchange.requestBody.readBytes()
                release.await()
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
            }
            start()
        }
        try {
            val stalled = "http://127.0.0.1:${server.address.port}/tsr"
            // Two stalled servers would take two full timeouts each if the limit were per request.
            val client = TimestampClient(listOf(stalled, stalled), timeoutMs = 1_000)
            val started = System.currentTimeMillis()
            val error = runCatching { client.stamp("signature".toByteArray()) }.exceptionOrNull()
            val elapsed = System.currentTimeMillis() - started
            assertNotNull(error)
            assertTrue(error is IOException)
            assertTrue("gave up after $elapsed ms", elapsed < 3_000)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    @Test
    fun failsAtOnceWhenNothingListens() {
        val client = TimestampClient(listOf(TestCertificates.deadUrl(), TestCertificates.deadUrl()), timeoutMs = 2_000)
        val started = System.currentTimeMillis()
        val error = runCatching { client.stamp("signature".toByteArray()) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(System.currentTimeMillis() - started < 2_000)
    }

    @Test
    fun takesTheFirstServerThatAnswers() {
        TestCertificates.FakeTimestampServer().use { server ->
            val client = TimestampClient(listOf(TestCertificates.deadUrl(), server.url), timeoutMs = 5_000)
            val token = client.stamp("signature".toByteArray())
            assertEquals(1, server.requests)
            assertNotNull(token.timeStampInfo.genTime)
        }
    }
}
