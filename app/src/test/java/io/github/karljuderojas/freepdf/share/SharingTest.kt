package io.github.karljuderojas.freepdf.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SharingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun eachShareGetsItsOwnFolderUnderTheSharedRoot() {
        val root = File(context.cacheDir, "shared")
        val now = System.currentTimeMillis()
        val first = Sharing.sharedFolder(context, now = now)
        val second = Sharing.sharedFolder(context, now = now)
        assertNotEquals(first, second)
        assertEquals(root, first.parentFile)
        assertEquals(root, second.parentFile)
        assertTrue(first.isDirectory && second.isDirectory)
    }

    @Test
    fun anEarlierShareStillInProgressKeepsItsFile() {
        val now = System.currentTimeMillis()
        val earlier = Sharing.sharedFolder(context, now = now - 60_000L)
        val shared = File(earlier, "Lease.pdf").apply { writeText("pdf") }
        Sharing.sharedFolder(context, now = now)
        assertTrue(shared.exists())
    }

    @Test
    fun sharesOlderThanAnHourAreClearedOut() {
        val now = System.currentTimeMillis()
        val old = Sharing.sharedFolder(context, now = now - Sharing.MAX_SHARED_AGE_MS - 60_000L)
        File(old, "Old.pdf").writeText("pdf")
        old.setLastModified(now - Sharing.MAX_SHARED_AGE_MS - 60_000L)
        Sharing.sharedFolder(context, now = now)
        assertTrue(!old.exists())
    }

    @Test
    fun sharedCopyGetsAPdfNameTheFileSystemAccepts() {
        val copy = Sharing.sharedCopy(context, "Q1/Q2: report?")
        assertEquals("Q1_Q2_ report_.pdf", copy.name)
        assertEquals(File(context.cacheDir, "shared"), copy.parentFile!!.parentFile)
    }
}
