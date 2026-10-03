package io.github.karljuderojas.freepdf

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.karljuderojas.freepdf.settings.ThemeChoice
import io.github.karljuderojas.freepdf.ui.FreePdfNavHost
import io.github.karljuderojas.freepdf.ui.theme.FreePdfTheme

class MainActivity : ComponentActivity() {

    /** A PDF handed to us by another app (file manager, mail, browser). */
    private val incomingPdf = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: after a rotation the navigation stack already holds the viewer,
        // and getIntent() still returns the same ACTION_VIEW, which would push a second one.
        if (savedInstanceState == null) incomingPdf.value = intent.pdfUri()
        setContent {
            val theme by (application as FreePdfApp).settings.theme.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeChoice.System -> isSystemInDarkTheme()
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }
            // Status and navigation bar icons follow the app's theme, not only the phone's.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            FreePdfTheme(darkTheme = dark) {
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

    /** The PDF in an open-with (ACTION_VIEW) or share-to (ACTION_SEND) intent, if any. */
    private fun Intent.pdfUri(): Uri? = when (action) {
        Intent.ACTION_VIEW -> data
        Intent.ACTION_SEND ->
            @Suppress("DEPRECATION")
            (getParcelableExtra(Intent.EXTRA_STREAM) as? Uri) ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        else -> null
    }
}
