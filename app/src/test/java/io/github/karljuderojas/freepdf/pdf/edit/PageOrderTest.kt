package io.github.karljuderojas.freepdf.pdf.edit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Moving, rotating and deleting several pages at once, as Pages mode's selection does. */
@RunWith(AndroidJUnit4::class)
class PageOrderTest {

    /** Six pages, told apart by their width: page n is 100 + n points wide. */
    private fun sixPages() = PDDocument().apply {
        repeat(6) { addPage(PDPage(PDRectangle(100f + it, 100f))) }
    }

    private fun PDDocument.order() = pages.map { (it.mediaBox.width - 100f).toInt() }

    @Test
    fun dragAPageForwardAndBack() {
        sixPages().use { document ->
            PageEditor.move(document, 1, 4)
            assertEquals(listOf(0, 2, 3, 4, 1, 5), document.order())
            PageEditor.move(document, 4, 0)
            assertEquals(listOf(1, 0, 2, 3, 4, 5), document.order())
            PageEditor.move(document, 0, 5)
            assertEquals(listOf(0, 2, 3, 4, 5, 1), document.order())
        }
    }

    @Test
    fun selectedPagesMoveTogetherKeepingTheirGaps() {
        sixPages().use { document ->
            PageEditor.shift(document, setOf(1, 3), -1)
            assertEquals(listOf(1, 0, 3, 2, 4, 5), document.order())
        }
        sixPages().use { document ->
            PageEditor.shift(document, setOf(3, 4), 1)
            assertEquals(listOf(0, 1, 2, 5, 3, 4), document.order())
        }
        sixPages().use { document ->
            PageEditor.shift(document, setOf(1, 3), 2)
            assertEquals(listOf(0, 2, 4, 1, 5, 3), document.order())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun shiftingPastTheStartIsRefused() {
        sixPages().use { PageEditor.shift(it, setOf(0, 2), -1) }
    }

    @Test
    fun rotateEverySelectedPage() {
        sixPages().use { document ->
            PageEditor.rotate(document, setOf(0, 2, 5), 90)
            assertEquals(listOf(90, 0, 90, 0, 0, 90), document.pages.map { it.rotation })
        }
    }

    @Test
    fun deleteEverySelectedPage() {
        sixPages().use { document ->
            PageEditor.delete(document, listOf(4, 1, 2))
            assertEquals(listOf(0, 3, 5), document.order())
        }
    }
}
