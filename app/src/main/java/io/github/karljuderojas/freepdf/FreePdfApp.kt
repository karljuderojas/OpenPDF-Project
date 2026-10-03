package io.github.karljuderojas.freepdf

import android.app.Application
import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.github.karljuderojas.freepdf.files.Documents
import io.github.karljuderojas.freepdf.settings.AppSettings
import io.github.karljuderojas.freepdf.settings.Tips

class FreePdfApp : Application() {

    /** Open and recent PDFs for the Files tab. */
    val documents by lazy { Documents(getSharedPreferences("documents", Context.MODE_PRIVATE)) }

    /** Choices from the Settings tab. */
    val settings by lazy { AppSettings(getSharedPreferences("settings", Context.MODE_PRIVATE)) }

    /** Which one-time tips were shown. */
    val tips by lazy { Tips(getSharedPreferences("tips", Context.MODE_PRIVATE)) }

    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android needs its bundled fonts and glyph lists before any document is touched.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
