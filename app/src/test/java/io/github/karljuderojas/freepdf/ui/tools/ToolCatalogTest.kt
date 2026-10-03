package io.github.karljuderojas.freepdf.ui.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {

    private val checkmark = listOf("Checkmark", "Sign", "tick", " check", " checkbox", " form")
    private val strikeout = listOf("Strikeout", "Annotate", "strike", " strikethrough", " cross out")

    @Test
    fun anEmptyQueryMatchesEverything() {
        assertTrue(matchesToolQuery("", checkmark))
        assertTrue(matchesToolQuery("   ", checkmark))
    }

    @Test
    fun matchesTheStartOfTheLabelInAnyCase() {
        assertTrue(matchesToolQuery("CHECK", checkmark))
        assertTrue(matchesToolQuery("strike", strikeout))
        assertFalse(matchesToolQuery("mark", checkmark))
    }

    @Test
    fun matchesSynonymsAndTheMode() {
        assertTrue(matchesToolQuery("tick", checkmark))
        assertTrue(matchesToolQuery("sign", checkmark))
        assertTrue(matchesToolQuery("cross out", strikeout))
        assertTrue(matchesToolQuery("cross-out", strikeout))
    }

    @Test
    fun everyWordHasToMatch() {
        assertFalse(matchesToolQuery("cross box", strikeout))
        assertFalse(matchesToolQuery("merge", checkmark))
    }
}
