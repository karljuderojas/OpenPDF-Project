package io.github.karljuderojas.freepdf.ui.sign

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.scan.ScanImages
import io.github.karljuderojas.freepdf.pdf.sign.SignatureCutout
import io.github.karljuderojas.freepdf.ui.create.CropBox
import io.github.karljuderojas.freepdf.ui.create.CropEditor
import io.github.karljuderojas.freepdf.ui.create.ScanFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The longest side the photo is shown and cropped at. */
private const val PHOTO_SIDE = 1600

/**
 * The Photo tab of the signature pad: take or pick a photo of a signature on paper, drag the crop
 * box around it, and see the signature with its background removed in [ink]. The photo and crop
 * box are kept by the caller so they survive switching tabs; [onResult] hears each new cut-out, or
 * null when no signature can be found in the box.
 */
@Composable
fun SignaturePhotoPane(
    photoPath: String?,
    onPhotoPath: (String?) -> Unit,
    crop: CropBox,
    onCrop: (CropBox) -> Unit,
    ink: Color,
    result: Bitmap?,
    onResult: (Bitmap?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var source by remember(photoPath) { mutableStateOf<Bitmap?>(null) }
    var looked by remember(photoPath) { mutableStateOf(false) }

    LaunchedEffect(photoPath) {
        val path = photoPath ?: return@LaunchedEffect
        source = withContext(Dispatchers.IO) { runCatching { ScanImages.load(File(path), PHOTO_SIDE) }.getOrNull() }
        if (source == null) onPhotoPath(null)
    }

    // Wait a moment after each drag so the cut-out is not redone for every pixel moved.
    LaunchedEffect(source, crop, ink) {
        val photo = source ?: return@LaunchedEffect
        // The previous crop's cut-out must not be saved while this one is still being worked out.
        looked = false
        onResult(null)
        delay(150)
        val cut = withContext(Dispatchers.Default) {
            runCatching {
                val x = (crop.left * photo.width).toInt().coerceIn(0, photo.width - 1)
                val y = (crop.top * photo.height).toInt().coerceIn(0, photo.height - 1)
                val w = ((crop.right - crop.left) * photo.width).toInt().coerceIn(1, photo.width - x)
                val h = ((crop.bottom - crop.top) * photo.height).toInt().coerceIn(1, photo.height - y)
                val part = Bitmap.createBitmap(photo, x, y, w, h)
                try {
                    SignatureCutout.extract(part, ink.toArgb())
                } finally {
                    if (part !== photo) part.recycle()
                }
            }.getOrNull()
        }
        looked = true
        onResult(cut)
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pendingPhoto?.let(::File)
        pendingPhoto = null
        if (file != null && taken) {
            onResult(null)
            onCrop(CropBox.Whole)
            onPhotoPath(file.path)
        } else {
            file?.delete()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { ScanFiles.copyFrom(context, uri) }.getOrNull() }
            if (file == null) {
                Toast.makeText(context, R.string.scan_photo_failed, Toast.LENGTH_SHORT).show()
            } else {
                onResult(null)
                onCrop(CropBox.Whole)
                onPhotoPath(file.path)
            }
        }
    }
    fun takePhoto() {
        val file = ScanFiles.newPhoto(context)
        pendingPhoto = file.path
        runCatching { camera.launch(ScanFiles.cameraUri(context, file)) }.onFailure {
            pendingPhoto = null
            file.delete()
            Toast.makeText(context, R.string.scan_no_camera, Toast.LENGTH_SHORT).show()
        }
    }

    SignaturePhotoContent(
        hasPhoto = photoPath != null,
        photo = source,
        crop = crop,
        onCrop = onCrop,
        result = result,
        looked = looked,
        onTakePhoto = ::takePhoto,
        onChoosePhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onUseAnother = {
            onPhotoPath(null)
            onResult(null)
        },
        modifier = modifier,
    )
}

/** The stateless Photo tab. [looked] is true once the cut-out has been tried for the current photo and crop. */
@Composable
fun SignaturePhotoContent(
    hasPhoto: Boolean,
    photo: Bitmap?,
    crop: CropBox,
    onCrop: (CropBox) -> Unit,
    result: Bitmap?,
    looked: Boolean,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onUseAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (!hasPhoto) {
            Text(
                stringResource(R.string.pad_photo_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth().testTag("pad-photo-camera")) {
                    Text(stringResource(R.string.scan_take_photo))
                }
                OutlinedButton(onClick = onChoosePhoto, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_from_gallery))
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(240.dp).background(Color.Black, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) CropEditor(photo.asImageBitmap(), crop, onCrop, Modifier.fillMaxWidth().height(240.dp)) else CircularProgressIndicator()
            }
            Text(
                stringResource(R.string.pad_photo_crop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(8.dp)
                    .testTag("pad-photo-preview"),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    result != null -> Image(result.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(80.dp))
                    looked -> Text(
                        stringResource(R.string.pad_photo_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    else -> CircularProgressIndicator()
                }
            }
            TextButton(onClick = onUseAnother) { Text(stringResource(R.string.pad_photo_another)) }
        }
    }
}
