package io.github.karljuderojas.freepdf.pdf.sign

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentHashTest {
    @Test
    fun sha256MatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DocumentHash.sha256("abc".toByteArray()),
        )
    }
}
