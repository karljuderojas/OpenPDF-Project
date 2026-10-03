package io.github.karljuderojas.freepdf.ui.create

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanFilesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun sweepingLeftoversKeepsThePhotoTheCameraIsStillWriting() {
        val old = ScanFiles.newPhoto(context)
        val older = ScanFiles.newPhoto(context)
        val pending = ScanFiles.newPhoto(context)

        ScanFiles.sweep(context, keep = pending)

        assertEquals(listOf(pending.name), ScanFiles.dir(context).list()!!.toList())
        assertEquals(false, old.exists() || older.exists())
    }

    @Test
    fun sweepingWithNothingToKeepEmptiesTheFolder() {
        ScanFiles.newPhoto(context)
        ScanFiles.newPhoto(context)
        ScanFiles.sweep(context)
        assertEquals(emptyList<String>(), ScanFiles.dir(context).list()!!.toList())
    }
}
