package io.github.karljuderojas.freepdf.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Remembers Android's file picker for PDFs. Call the returned function to show it; [onPicked]
 * gets the chosen file, with lasting access taken so it can appear in Recent.
 */
@Composable
fun rememberPdfPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Keep access across restarts so the file can appear in Recent. Some providers only
            // grant read access, in which case asking for write as well would lose both.
            val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .recoverCatching { context.contentResolver.takePersistableUriPermission(uri, read) }
            onPicked(uri)
        }
    }
    return { picker.launch(arrayOf("application/pdf")) }
}
