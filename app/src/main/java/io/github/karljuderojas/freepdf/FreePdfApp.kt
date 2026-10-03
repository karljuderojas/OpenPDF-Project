package io.github.karljuderojas.freepdf

import android.app.Application
import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.github.karljuderojas.freepdf.files.Documents
import io.github.karljuderojas.freepdf.settings.AppSettings
import io.github.karljuderojas.freepdf.settings.Tips
import io.github.karljuderojas.freepdf.ui.viewer.DocumentSessions
import java.io.File

class FreePdfApp : Application() {

    /** Open and recent PDFs for the Files tab. A PDF leaving the open list ends its session; one with unsaved changes stays. */
    val documents by lazy {
        Documents(
            getSharedPreferences("documents", Context.MODE_PRIVATE),
            onClosed = sessions::close,
            hasUnsavedChanges = { uri -> sessions.get(uri)?.hasUnsavedChanges == true },
        )
    }

    /** The live working copies of the open PDFs, which the viewer attaches to; see [DocumentSessions]. */
    val sessions by lazy { DocumentSessions(File(cacheDir, "edit")) }

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
