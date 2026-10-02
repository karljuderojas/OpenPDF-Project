package io.github.karljuderojas.freepdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.karljuderojas.freepdf.ui.FreePdfNavHost
import io.github.karljuderojas.freepdf.ui.theme.FreePdfTheme

class MainActivity : ComponentActivity() {

    /** A PDF handed to us by another app (file manager, mail, browser). */
    private val incomingPdf = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingPdf.value = intent.pdfUri()
        setContent {
            FreePdfTheme {
                FreePdfNavHost(
                    incomingPdf = incomingPdf.value,
                    onIncomingPdfHandled = { incomingPdf.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.pdfUri()?.let { incomingPdf.value = it }
    }

    private fun Intent.pdfUri(): Uri? = if (action == Intent.ACTION_VIEW) data else null
}
