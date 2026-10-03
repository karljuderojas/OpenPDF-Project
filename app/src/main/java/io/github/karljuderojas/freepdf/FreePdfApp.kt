package io.github.karljuderojas.freepdf

import android.app.Application
import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.github.karljuderojas.freepdf.files.Documents

class FreePdfApp : Application() {

    /** Open and recent PDFs for the Files tab. */
    val documents by lazy { Documents(getSharedPreferences("documents", Context.MODE_PRIVATE)) }

    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android needs its bundled fonts and glyph lists before any document is touched.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
