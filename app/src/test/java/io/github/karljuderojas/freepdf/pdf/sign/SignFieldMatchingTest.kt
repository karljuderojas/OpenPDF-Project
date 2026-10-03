package io.github.karljuderojas.freepdf.pdf.sign

import io.github.karljuderojas.freepdf.pdf.DisplayRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Finding the current place to sign again once the fields have been found afresh, as they are
 * after pages move, and going on to the next unsigned one.
 */
class SignFieldMatchingTest {

    private fun field(page: Int, top: Float, left: Float = 0.1f, source: SignField.Source = SignField.Source.TextCue) =
        SignField(page, DisplayRect(left, top, left + 0.3f, top + 0.05f), source)

    // Two places on page 1 and one on page 2, in reading order.
    private val a = field(0, 0.2f)
    private val b = field(0, 0.6f)
    private val c = field(1, 0.4f)
    private val fields = listOf(a, b, c)

    @Test
    fun theSameFieldIsFoundByWhereItIs() {
        assertEquals(1, SignatureFields.indexOf(fields, b))
        // Found again from text, its box can differ a little; the same page and an overlap is enough.
        assertEquals(1, SignatureFields.indexOf(fields, field(0, 0.62f, left = 0.12f, source = SignField.Source.FormField)))
    }

    @Test
    fun theFieldOverlappingMostWins() {
        val close = listOf(field(0, 0.20f), field(0, 0.23f))
        assertEquals(1, SignatureFields.indexOf(close, field(0, 0.24f)))
        assertEquals(0, SignatureFields.indexOf(close, field(0, 0.19f)))
    }

    @Test
    fun aFieldOnAnotherPageOrElsewhereOnThePageIsNotTheSame() {
        assertNull(SignatureFields.indexOf(fields, field(2, 0.4f)))
        assertNull(SignatureFields.indexOf(fields, field(0, 0.4f)))
        assertNull(SignatureFields.indexOf(fields, null))
        assertNull(SignatureFields.indexOf(emptyList(), a))
    }

    @Test
    fun boxesBelowOneAnotherOrOnOtherPagesDoNotOverlap() {
        assertEquals(0f, a.overlapWith(field(0, 0.3f)), 0f)
        assertEquals(1f, a.overlapWith(a), 1e-6f)
        assertEquals(0f, a.overlapWith(field(1, 0.2f)), 0f)
        // Half the height shared: half the box.
        assertEquals(0.5f, a.overlapWith(field(0, 0.225f)), 1e-3f)
    }

    @Test
    fun nextGoesToTheFirstUnsignedFieldAfterTheCurrentOne() {
        assertEquals(b, SignatureFields.nextUnsigned(fields, emptySet(), a))
        assertEquals(c, SignatureFields.nextUnsigned(fields, emptySet(), b))
    }

    @Test
    fun nextSkipsSignedFieldsAndWrapsRound() {
        assertEquals(c, SignatureFields.nextUnsigned(fields, setOf(1), a))
        assertEquals(a, SignatureFields.nextUnsigned(fields, emptySet(), c))
        assertEquals(b, SignatureFields.nextUnsigned(fields, setOf(0, 2), c))
        // Only the current one is left: Next stays on it.
        assertEquals(b, SignatureFields.nextUnsigned(fields, setOf(0, 2), b))
    }

    @Test
    fun nextStartsFromTheFirstUnsignedFieldWithoutACurrentOne() {
        assertEquals(a, SignatureFields.nextUnsigned(fields, emptySet(), null))
        assertEquals(b, SignatureFields.nextUnsigned(fields, setOf(0), null))
        // The current field is gone (its page was deleted, say): the same as having none.
        assertEquals(a, SignatureFields.nextUnsigned(fields, emptySet(), field(3, 0.5f)))
    }

    @Test
    fun nextIsNullOnceEveryFieldIsSigned() {
        assertNull(SignatureFields.nextUnsigned(fields, setOf(0, 1, 2), a))
        assertNull(SignatureFields.nextUnsigned(emptyList(), emptySet(), null))
    }

    @Test
    fun theCurrentFieldFollowsItsPlaceWhenTheFieldsAreFoundAgainInAnotherOrder() {
        // A page inserted before: the fields on page 1 are now on page 2, so the current one is
        // only found if it is still on the page it was on. Here page 2's field is unchanged.
        val afterInsert = listOf(field(1, 0.2f), field(1, 0.6f), field(2, 0.4f))
        assertNull(SignatureFields.indexOf(afterInsert, a))
        assertEquals(0, SignatureFields.indexOf(afterInsert, field(1, 0.2f)))
        // Page 2 moved before page 1: c's old position now holds page 1's first field, b sits lower.
        val afterMove = listOf(field(0, 0.4f), field(1, 0.2f), field(1, 0.6f))
        assertEquals(0, SignatureFields.indexOf(afterMove, field(0, 0.4f)))
        assertEquals(field(1, 0.2f), SignatureFields.nextUnsigned(afterMove, emptySet(), field(0, 0.4f)))
    }
}
