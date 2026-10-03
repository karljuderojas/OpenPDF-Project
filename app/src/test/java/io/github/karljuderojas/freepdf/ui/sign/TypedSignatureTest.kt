package io.github.karljuderojas.freepdf.ui.sign

import org.junit.Assert.assertEquals
import org.junit.Test

class TypedSignatureTest {

    @Test
    fun initialsTakeTheFirstLetterOfEachName() {
        assertEquals("DW", TypedSignature.initialsOf("Dana Whitfield"))
        assertEquals("JPS", TypedSignature.initialsOf("  jean-paul  Sartre "))
        assertEquals("", TypedSignature.initialsOf(""))
    }
}
