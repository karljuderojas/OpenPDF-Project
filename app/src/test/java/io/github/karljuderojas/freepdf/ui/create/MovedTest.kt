package io.github.karljuderojas.freepdf.ui.create

import org.junit.Assert.assertEquals
import org.junit.Test

class MovedTest {

    @Test
    fun movesOneItemAndKeepsTheRestInOrder() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "a", "c", "d"), list.moved(0, 1))
        assertEquals(listOf("a", "c", "d", "b"), list.moved(1, 3))
        assertEquals(listOf("d", "a", "b", "c"), list.moved(3, 0))
    }

    @Test
    fun movesOutOfRangeOrToTheSameSpotChangeNothing() {
        val list = listOf("a", "b")
        assertEquals(list, list.moved(0, -1))
        assertEquals(list, list.moved(1, 2))
        assertEquals(list, list.moved(1, 1))
        assertEquals(list, list.moved(5, 0))
    }

    @Test
    fun theSamePictureCanAppearTwice() {
        assertEquals(listOf("x", "y", "x"), listOf("x", "x", "y").moved(2, 1))
    }
}
