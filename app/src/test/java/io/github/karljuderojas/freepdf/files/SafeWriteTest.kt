package io.github.karljuderojas.freepdf.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
class SafeWriteTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun original(): File = temp.newFile("original.pdf").apply { writeText("the original bytes") }

    @Test
    fun aFileKeepsItsContentWhenTheWriteFailsHalfWay() {
        val target = original()
        assertThrows(IOException::class.java) {
            SafeWrite.writeFile(target) { out ->
                out.write("half of the new".toByteArray())
                throw IOException("disk full")
            }
        }
        assertEquals("the original bytes", target.readText())
        assertEquals(listOf("original.pdf"), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun aFileIsReplacedWhenTheWriteCompletes() {
        val target = original()
        SafeWrite.writeFile(target) { it.write("new".toByteArray()) }
        assertEquals("new", target.readText())
        assertEquals(listOf("original.pdf"), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun aStreamTargetIsRestoredFromTheBackupWhenTheCopyFails() {
        val target = StringBuilder("the original bytes")
        var opens = 0
        assertThrows(IOException::class.java) {
            SafeWrite.writeStream(
                scratchDir = temp.newFolder("cache"),
                openInput = { ByteArrayInputStream(target.toString().toByteArray()) },
                openOutput = {
                    target.setLength(0) // "wt" truncates on open
                    if (++opens == 1) FailingOutput(target, failAfter = 4) else AppendingOutput(target)
                },
                write = { it.write("brand new content".toByteArray()) },
            )
        }
        assertEquals("the original bytes", target.toString())
        assertEquals(emptyList<String>(), File(temp.root, "cache/save").list()!!.toList())
    }

    @Test
    fun aStreamTargetIsNotOpenedWhenProducingTheContentFails() {
        val target = StringBuilder("the original bytes")
        assertThrows(IllegalStateException::class.java) {
            SafeWrite.writeStream(
                scratchDir = temp.newFolder("cache"),
                openInput = { ByteArrayInputStream(target.toString().toByteArray()) },
                openOutput = { target.setLength(0); AppendingOutput(target) },
                write = { error("cannot render") },
            )
        }
        assertEquals("the original bytes", target.toString())
        assertEquals(emptyList<String>(), File(temp.root, "cache/save").list()!!.toList())
    }

    private open class AppendingOutput(private val target: StringBuilder) : OutputStream() {
        override fun write(b: Int) {
            target.append(b.toChar())
        }
    }

    /** Takes [failAfter] bytes, then fails like a connection to a document provider dropping. */
    private class FailingOutput(target: StringBuilder, private val failAfter: Int) : AppendingOutput(target) {
        private var written = 0
        override fun write(b: Int) {
            if (++written > failAfter) throw IOException("connection lost")
            super.write(b)
        }
    }
}
