package io.github.karljuderojas.freepdf.print

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class PrintingTest {

    private val dir = Files.createTempDirectory("printing").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun anUnlockedPdfIsPrintedAsIs() {
        val source = sample()
        val target = File(dir, "print/copy.pdf")
        Printing.printableCopy(source, target)
        assertArrayEquals(source.readBytes(), target.readBytes())
    }

    @Test
    fun anEncryptedPdfIsDecryptedForThePrintSystem() {
        val source = protectedSample(AccessPermission())
        val target = File(dir, "copy.pdf")
        Printing.printableCopy(source, target)
        PDDocument.load(target).use {
            assertFalse(it.isEncrypted)
            assertEquals(2, it.numberOfPages)
        }
    }

    @Test
    fun aPdfWithAnOpenPasswordIsPrintedWithIt() {
        val source = protectedSample(AccessPermission(), userPassword = "open sesame")
        val target = File(dir, "copy.pdf")
        Printing.printableCopy(source, target, "open sesame")
        PDDocument.load(target).use {
            assertFalse(it.isEncrypted)
            assertEquals(2, it.numberOfPages)
        }
    }

    @Test(expected = Printing.NotAllowed::class)
    fun aPdfThatForbidsPrintingIsNotPrinted() {
        val source = protectedSample(AccessPermission().apply { setCanPrint(false) })
        Printing.printableCopy(source, File(dir, "copy.pdf"))
    }

    private fun sample(): File = File(dir, "agreement.pdf").also { file ->
        javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf")!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    }

    /** The sample locked with an owner password, and by default no open password, as many PDFs are. */
    private fun protectedSample(permissions: AccessPermission, userPassword: String = ""): File = File(dir, "protected.pdf").also { file ->
        PDDocument.load(sample()).use { document ->
            document.protect(StandardProtectionPolicy("owner-secret", userPassword, permissions).apply { encryptionKeyLength = 128 })
            document.save(file)
        }
    }
}
