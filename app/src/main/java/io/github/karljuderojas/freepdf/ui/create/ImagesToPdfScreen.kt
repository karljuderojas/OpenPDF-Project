package io.github.karljuderojas.freepdf.ui.create

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.create.ImagesToPdf
import io.github.karljuderojas.freepdf.pdf.create.PageFit
import io.github.karljuderojas.freepdf.ui.viewer.PickedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The longest side, in pixels, a picture keeps when it goes into the PDF: sharp at A4 print size. */
private const val PAGE_SIDE = 2560

/** The longest side of the small previews in the list. */
private const val THUMB_SIDE = 192

/** Moves the item at [from] to [to], leaving the others in order. Out-of-range moves change nothing. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Images to PDF: pick pictures from the gallery, put them in order, choose the page size, and save
 * a PDF with one page per picture. [onCreated] gets the new file.
 */
@Composable
fun ImagesToPdfScreen(onBack: () -> Unit, onCreated: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Kept as strings so the order survives Android recreating the screen.
    var photos by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var fit by rememberSaveable { mutableStateOf(PageFit.A4) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }

    LaunchedEffect(photos) {
        photos.filter { it !in thumbs }.distinct().forEach { uri ->
            val thumb = withContext(Dispatchers.IO) {
                runCatching { PickedImage.load(context, Uri.parse(uri), THUMB_SIDE).asImageBitmap() }.getOrNull()
            }
            if (thumb != null) thumbs[uri] = thumb
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            failed = false
            photos = photos + uris.map { it.toString() }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        if (target == null) return@rememberLauncherForActivityResult
        val chosen = photos
        failed = false
        progress = 0
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val document = ImagesToPdf.build(chosen.size, fit, onProgress = { progress = it }) { i ->
                        PickedImage.load(context, Uri.parse(chosen[i]), PAGE_SIDE)
                    }
                    NewPdf.write(context, document, target)
                }
            }
            progress = null
            if (result.isSuccess) onCreated(target) else failed = true
        }
    }
    val defaultName = stringResource(R.string.images_default_name)

    BackHandler(enabled = progress != null) {}

    ImagesToPdfContent(
        photos = photos,
        thumbs = thumbs,
        fit = fit,
        progress = progress,
        failed = failed,
        onAdd = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onMove = { from, to -> photos = photos.moved(from, to) },
        onRemove = { photos = photos.filterIndexed { i, _ -> i != it } },
        onFit = { fit = it },
        onCreate = { save.launch(NewPdf.defaultName(defaultName)) },
        onBack = onBack,
    )
}

/** The stateless Images to PDF screen. [progress] is the number of pages made while saving, else null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfContent(
    photos: List<String>,
    thumbs: Map<String, ImageBitmap>,
    fit: PageFit,
    progress: Int?,
    failed: Boolean,
    onAdd: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onFit: (PageFit) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = progress != null
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.images_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !working) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            if (photos.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.images_page_size), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = fit == PageFit.A4,
                                onClick = { onFit(PageFit.A4) },
                                label = { Text(stringResource(R.string.images_fit_a4)) },
                                enabled = !working,
                            )
                            FilterChip(
                                selected = fit == PageFit.Picture,
                                onClick = { onFit(PageFit.Picture) },
                                label = { Text(stringResource(R.string.images_fit_picture)) },
                                enabled = !working,
                            )
                        }
                        if (failed) {
                            Text(
                                stringResource(R.string.images_failed),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress.toFloat() / photos.size }, modifier = Modifier.fillMaxWidth())
                            Text(
                                stringResource(R.string.images_making, progress, photos.size),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onAdd, enabled = !working, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.images_add_more))
                            }
                            Button(onClick = onCreate, enabled = !working, modifier = Modifier.weight(1f).testTag("images-create")) {
                                Text(stringResource(R.string.images_create))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (photos.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.images_empty_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.images_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(24.dp))
                Button(onClick = onAdd, modifier = Modifier.testTag("images-choose")) { Text(stringResource(R.string.images_choose)) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        pluralStringResource(R.plurals.images_count, photos.size, photos.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(photos) { index, uri ->
                    PhotoRow(index, photos.size, thumbs[uri], !working, onMove, onRemove)
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(
    index: Int,
    count: Int,
    thumb: ImageBitmap?,
    enabled: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            stringResource(R.string.images_page_n, index + 1),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        IconButton(onClick = { onMove(index, index - 1) }, enabled = enabled && index > 0) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.images_move_earlier, index + 1))
        }
        IconButton(onClick = { onMove(index, index + 1) }, enabled = enabled && index < count - 1) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.images_move_later, index + 1))
        }
        IconButton(onClick = { onRemove(index) }, enabled = enabled) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.images_remove, index + 1))
        }
    }
}
