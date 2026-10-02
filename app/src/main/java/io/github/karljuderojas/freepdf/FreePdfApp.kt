package io.github.karljuderojas.freepdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class FreePdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android needs its bundled fonts and glyph lists before any document is touched.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
