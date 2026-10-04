package io.github.karljuderojas.freepdf.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderBrowserTest {

    private val files = listOf(
        FolderFile("c://1", "beta.pdf", 300, 1),
        FolderFile("c://2", "Alpha.pdf", 100, 1),
        FolderFile("c://3", "gamma.pdf", 200, 1),
    )

    @Test
    fun sortsByNameIgnoringCase() {
        assertEquals(listOf("Alpha.pdf", "beta.pdf", "gamma.pdf"), sortFolderFiles(files, FolderSort.Name).map { it.name })
    }

    @Test
    fun sortsByDateNewestFirst() {
        assertEquals(listOf("beta.pdf", "gamma.pdf", "Alpha.pdf"), sortFolderFiles(files, FolderSort.Date).map { it.name })
    }

    @Test
    fun recognisesPdfsByTypeOrByNameWhenTheTypeIsGeneric() {
        assertTrue(isPdf("application/pdf", "scan"))
        assertTrue(isPdf("application/octet-stream", "Scan.PDF"))
        assertTrue(isPdf(null, "a.pdf"))
        assertFalse(isPdf("image/png", "a.pdf"))
        assertFalse(isPdf(null, "notes.txt"))
    }
}
