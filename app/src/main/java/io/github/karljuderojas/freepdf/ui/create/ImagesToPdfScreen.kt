package io.github.karljuderojas.freepdf.ui.create

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.create.PageFit
import io.github.karljuderojas.freepdf.ui.viewer.PickedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
fun ImagesToPdfScreen(onBack: () -> Unit, onCreated: (Uri) -> Unit, viewModel: PdfBuildViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Each picked picture is copied into the app's cache (the picker's access to the original
    // ends with the process), and the paths are kept as strings so the order survives Android
    // recreating the screen or the app.
    var photos by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var fit by rememberSaveable { mutableStateOf(PageFit.A4) }
    var copying by remember { mutableStateOf(false) }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    // Pictures the phone cannot decode (a format it has no codec for, or a damaged file). Found
    // again by the preview pass after a recreation, so it need not be saved.
    var unreadable by remember { mutableStateOf(emptySet<String>()) }
    // The save lives in the view model so it carries on, and is heard, across a rotation.
    val build by viewModel.state.collectAsStateWithLifecycle()
    val progress = (build as? BuildState.Making)?.done
    val failed = build is BuildState.Failed

    fun setPhotos(list: List<String>) {
        photos = list
        thumbs.keys.filter { it !in list }.forEach { thumbs.remove(it) }
        unreadable = unreadable.filterTo(HashSet()) { it in list }
    }

    // Leftovers from an earlier visit that never got saved.
    LaunchedEffect(Unit) {
        if (photos.isEmpty()) withContext(Dispatchers.IO) { ScanFiles.sweep(context) }
    }

    LaunchedEffect(build) {
        val done = build as? BuildState.Done ?: return@LaunchedEffect
        val sources = photos
        viewModel.finished()
        onCreated(done.uri)
        sources.forEach { File(it).delete() }
    }

    LaunchedEffect(photos) {
        photos.filter { it !in thumbs && it !in unreadable }.forEach { path ->
            val thumb = withContext(Dispatchers.IO) {
                runCatching { PickedImage.load(context, Uri.fromFile(File(path)), THUMB_SIDE).asImageBitmap() }.getOrNull()
            }
            if (thumb != null) thumbs[path] = thumb else unreadable = unreadable + path
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        viewModel.clearFailure()
        copying = true
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> runCatching { ScanFiles.copyFrom(context, uri) }.getOrNull() }
            }
            copying = false
            if (files.size < uris.size) Toast.makeText(context, R.string.scan_photo_failed, Toast.LENGTH_SHORT).show()
            setPhotos(photos + files.map { it.path })
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        if (target == null) return@rememberLauncherForActivityResult
        val chosen = photos
        val app = context.applicationContext
        viewModel.build(chosen.size, fit, target) { i -> PickedImage.load(app, Uri.fromFile(File(chosen[i])), PAGE_SIDE) }
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
        onMove = { from, to -> setPhotos(photos.moved(from, to)) },
        onRemove = { index ->
            photos.getOrNull(index)?.let { File(it).delete() }
            setPhotos(photos.filterIndexed { i, _ -> i != index })
        },
        onFit = { fit = it },
        onCreate = { save.launch(NewPdf.defaultName(defaultName)) },
        onBack = onBack,
        unreadable = unreadable,
        copying = copying,
    )
}

/**
 * The stateless Images to PDF screen. [progress] is the number of pages made while saving, else
 * null. [unreadable] are pictures the phone could not decode: their rows say so, and the PDF cannot
 * be created until they are removed. [copying] is true while just-picked pictures are being brought in.
 */
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
    unreadable: Set<String> = emptySet(),
    copying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val working = progress != null || copying
    val blocked = photos.any { it in unreadable }
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
                        if (blocked) {
                            Text(
                                stringResource(R.string.images_unreadable_remove),
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
                            Button(onClick = onCreate, enabled = !working && !blocked, modifier = Modifier.weight(1f).testTag("images-create")) {
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
                if (copying) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = onAdd, modifier = Modifier.testTag("images-choose")) { Text(stringResource(R.string.images_choose)) }
                }
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
                itemsIndexed(photos) { index, path ->
                    PhotoRow(index, photos.size, thumbs[path], path in unreadable, !working, onMove, onRemove)
                }
                if (copying) {
                    item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
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
    unreadable: Boolean,
    enabled: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when {
                thumb != null -> Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                unreadable -> Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(R.string.images_page_n, index + 1), style = MaterialTheme.typography.bodyLarge)
            if (unreadable) {
                Text(
                    stringResource(R.string.images_unreadable, index + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
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
