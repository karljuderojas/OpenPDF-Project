package io.github.karljuderojas.freepdf.print

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Prints through Android's print framework, which lists every printer the phone knows about
 * (Wi-Fi printers, the Default Print Service, Save as PDF). Nothing goes through a FreePDF server.
 */
object Printing {

    /** Thrown by [printableCopy] when the PDF's owner has turned printing off. */
    class NotAllowed : Exception()

    /**
     * Writes a copy of [source] that the print system can read to [target]. Android renders print
     * jobs with its own PDF renderer, which cannot open encrypted files, so the copy has its
     * encryption removed; a PDF whose permissions forbid printing throws [NotAllowed] instead.
     */
    fun printableCopy(source: File, target: File, password: String = "") {
        target.parentFile?.mkdirs()
        PDDocument.load(source, password).use { document ->
            if (!document.isEncrypted) {
                source.copyTo(target, overwrite = true)
                return
            }
            if (!document.currentAccessPermission.canPrint()) throw NotAllowed()
            document.isAllSecurityToBeRemoved = true
            document.save(target)
        }
    }

    /** Opens the print dialog for [file], which is deleted once the dialog closes. */
    fun print(context: Context, file: File, jobName: String, pageCount: Int) {
        val printManager = context.getSystemService(PrintManager::class.java)
        printManager.print(jobName, FileAdapter(file, jobName, pageCount), null)
    }

    /** Hands the print system the finished PDF as is; it picks the pages and lays them out. */
    private class FileAdapter(private val file: File, private val name: String, private val pageCount: Int) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(if (pageCount > 0) pageCount else PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            // The PDF is the same whatever the paper size, so it only counts as changed the first time.
            callback.onLayoutFinished(info, oldAttributes == null)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            // onWrite is called on the main thread; copying a large PDF there would freeze the app.
            thread(name = "print-write") {
                try {
                    FileOutputStream(destination.fileDescriptor).use { output -> file.inputStream().use { it.copyTo(output) } }
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                    } else {
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    }
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }

        override fun onFinish() {
            file.delete()
        }
    }
}
