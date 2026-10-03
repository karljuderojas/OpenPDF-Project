package io.github.karljuderojas.freepdf.ui.create

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.FileProvider
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.create.ImagesToPdf
import io.github.karljuderojas.freepdf.pdf.create.PageFit
import io.github.karljuderojas.freepdf.pdf.scan.PageDetector
import io.github.karljuderojas.freepdf.pdf.scan.Quad
import io.github.karljuderojas.freepdf.pdf.scan.ScanFilter
import io.github.karljuderojas.freepdf.pdf.scan.ScanImages
import io.github.karljuderojas.freepdf.pdf.scan.ScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The longest side, in pixels, of a finished page: sharp at A4 print size without a huge file. */
private const val SCAN_SIDE = 2200

/** The photo shown while dragging corners, and the small previews in the page list. */
private const val ADJUST_SIDE = 1600
private const val THUMB_SIDE = 480

/** Every scan photo lives here until the PDF is saved; the system may clear a cache folder at any time. */
private fun scanDir(context: android.content.Context) = File(context.cacheDir, "scans").apply { mkdirs() }

/** Which preview a page needs: it changes when the corners or the look do. */
fun thumbKey(page: ScanPage, filter: ScanFilter): String = "${page.id}-${page.quad.encode()}-${filter.name}"

/**
 * The document scanner. Take a photo of each page with the camera app or pick photos from the
 * gallery, check the corners the app found, choose a look, and save a PDF. Everything happens on
 * the phone: edge finding, straightening and the filters are in the app, not on a server.
 */
@Composable
fun ScannerScreen(onBack: () -> Unit, onCreated: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Pages are kept as text so they survive Android recreating the screen or the app while the camera is open.
    var encoded by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val pages = remember(encoded) { encoded.mapNotNull(ScanPage::decode) }
    var filter by rememberSaveable { mutableStateOf(ScanFilter.Color) }
    var adjusting by rememberSaveable { mutableIntStateOf(-1) }
    var adjustingNew by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    var adjustImage by remember { mutableStateOf<ImageBitmap?>(null) }

    fun setPages(list: List<ScanPage>) {
        encoded = list.map { it.encode() }
    }

    // Leftovers from an earlier visit that never got saved.
    LaunchedEffect(Unit) {
        if (encoded.isEmpty()) withContext(Dispatchers.IO) { scanDir(context).listFiles()?.forEach { it.delete() } }
    }

    LaunchedEffect(pages, filter) {
        pages.filter { thumbKey(it, filter) !in thumbs }.forEach { page ->
            val thumb = withContext(Dispatchers.IO) {
                runCatching { ScanImages.render(page, filter, THUMB_SIDE).asImageBitmap() }.getOrNull()
            }
            if (thumb != null) thumbs[thumbKey(page, filter)] = thumb
        }
    }

    val adjustedPage = pages.getOrNull(adjusting)
    LaunchedEffect(adjustedPage?.id) {
        adjustImage = null
        val page = adjustedPage ?: return@LaunchedEffect
        adjustImage = withContext(Dispatchers.IO) {
            runCatching { ScanImages.load(File(page.file), ADJUST_SIDE).asImageBitmap() }.getOrNull()
        }
    }

    /** Adds the photos in [files] as pages, with the corners the detector found; a single new photo opens for adjusting. */
    fun addPhotos(files: List<File>, thenAdjust: Boolean) {
        finding = true
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                files.mapNotNull { file ->
                    runCatching {
                        val small = ScanImages.load(file, 384)
                        val quad = try { PageDetector.detect(small) } finally { small.recycle() }
                        ScanPage(System.nanoTime(), file.path, quad ?: Quad.inset(0.04f))
                    }.getOrNull()
                }
            }
            finding = false
            if (added.isEmpty()) {
                Toast.makeText(context, R.string.scan_photo_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            setPages(pages + added)
            if (thenAdjust) {
                adjusting = pages.size
                adjustingNew = true
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pendingPhoto?.let(::File)
        pendingPhoto = null
        if (file != null && taken) addPhotos(listOf(file), thenAdjust = true) else file?.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        finding = true
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        File.createTempFile("page-", ".jpg", scanDir(context)).also { file ->
                            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read $uri")
                            input.use { src -> file.outputStream().use { src.copyTo(it) } }
                        }
                    }.getOrNull()
                }
            }
            finding = false
            addPhotos(files, thenAdjust = false)
        }
    }

    fun takePhoto() {
        val file = File.createTempFile("page-", ".jpg", scanDir(context))
        pendingPhoto = file.path
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        runCatching { camera.launch(uri) }.onFailure {
            pendingPhoto = null
            file.delete()
            Toast.makeText(context, R.string.scan_no_camera, Toast.LENGTH_SHORT).show()
        }
    }

    fun remove(index: Int) {
        val page = pages.getOrNull(index) ?: return
        File(page.file).delete()
        setPages(pages.filterIndexed { i, _ -> i != index })
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        if (target == null) return@rememberLauncherForActivityResult
        val chosen = pages
        val look = filter
        failed = false
        progress = 0
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val document = ImagesToPdf.build(chosen.size, PageFit.Picture, onProgress = { progress = it }) { i ->
                        ScanImages.render(chosen[i], look, SCAN_SIDE)
                    }
                    NewPdf.write(context, document, target)
                    chosen.forEach { File(it.file).delete() }
                }
            }
            progress = null
            if (result.isSuccess) onCreated(target) else failed = true
        }
    }
    val defaultName = stringResource(R.string.scan_default_name)

    fun closeAdjust(discard: Boolean) {
        if (discard && adjustingNew) remove(adjusting)
        adjusting = -1
        adjustingNew = false
    }

    BackHandler(enabled = progress != null) {}
    BackHandler(enabled = progress == null && adjusting >= 0) { closeAdjust(discard = true) }

    ScannerContent(
        pages = pages,
        thumbs = thumbs,
        filter = filter,
        adjusting = adjusting.takeIf { it in pages.indices },
        adjustingNew = adjustingNew,
        adjustImage = adjustImage,
        finding = finding,
        progress = progress,
        failed = failed,
        onTakePhoto = ::takePhoto,
        onFromGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAdjust = { adjusting = it; adjustingNew = false },
        onQuad = { quad -> pages.getOrNull(adjusting)?.let { page -> setPages(pages.map { if (it.id == page.id) it.copy(quad = quad) else it }) } },
        onAdjustDone = { closeAdjust(discard = false) },
        onRetake = {
            closeAdjust(discard = true)
            takePhoto()
        },
        onRemove = ::remove,
        onMove = { from, to -> setPages(pages.moved(from, to)) },
        onFilter = { filter = it },
        onSave = { save.launch(NewPdf.defaultName(defaultName)) },
        onBack = { if (adjusting >= 0) closeAdjust(discard = true) else onBack() },
    )
}

/**
 * The stateless scanner screen. With [adjusting] set it shows that page's photo with draggable
 * corners; otherwise the list of pages with the look chips and the Camera, Gallery and Save buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerContent(
    pages: List<ScanPage>,
    thumbs: Map<String, ImageBitmap>,
    filter: ScanFilter,
    adjusting: Int?,
    adjustingNew: Boolean,
    adjustImage: ImageBitmap?,
    finding: Boolean,
    progress: Int?,
    failed: Boolean,
    onTakePhoto: () -> Unit,
    onFromGallery: () -> Unit,
    onAdjust: (Int) -> Unit,
    onQuad: (Quad) -> Unit,
    onAdjustDone: () -> Unit,
    onRetake: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onFilter: (ScanFilter) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = progress != null || finding
    val page = adjusting?.let { pages.getOrNull(it) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (page != null) R.string.scan_adjust_title else R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = progress == null) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            if (page != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (adjustingNew) {
                            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scan_retake)) }
                        }
                        Button(onClick = onAdjustDone, modifier = Modifier.weight(1f).testTag("scan-adjust-done")) {
                            Text(stringResource(R.string.done))
                        }
                    }
                }
            } else if (pages.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(filter == ScanFilter.Color, { onFilter(ScanFilter.Color) }, { Text(stringResource(R.string.scan_filter_color)) }, enabled = !working)
                            FilterChip(filter == ScanFilter.Grayscale, { onFilter(ScanFilter.Grayscale) }, { Text(stringResource(R.string.scan_filter_gray)) }, enabled = !working)
                            FilterChip(filter == ScanFilter.BlackWhite, { onFilter(ScanFilter.BlackWhite) }, { Text(stringResource(R.string.scan_filter_bw)) }, enabled = !working)
                        }
                        if (failed) {
                            Text(stringResource(R.string.images_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress.toFloat() / pages.size }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.images_making, progress, pages.size), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onTakePhoto, enabled = !working, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.scan_camera))
                            }
                            OutlinedButton(onClick = onFromGallery, enabled = !working, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.scan_gallery))
                            }
                            Button(onClick = onSave, enabled = !working, modifier = Modifier.weight(1f).testTag("scan-save")) {
                                Text(stringResource(R.string.scan_save))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            page != null -> Column(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    stringResource(R.string.scan_adjust_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onQuad(Quad.inset(0f)) }) { Text(stringResource(R.string.scan_whole_photo)) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (adjustImage != null) {
                        QuadEditor(adjustImage, page.quad, onQuad, Modifier.fillMaxSize())
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
            pages.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.scan_empty_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.scan_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                if (finding) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth().testTag("scan-camera")) { Text(stringResource(R.string.scan_take_photo)) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onFromGallery, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_from_gallery)) }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        pluralStringResource(R.plurals.scan_page_count, pages.size, pages.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(pages, key = { _, p -> p.id }) { index, p ->
                    ScanRow(index, pages.size, thumbs[thumbKey(p, filter)], !working, onAdjust, onMove, onRemove)
                }
                if (finding) {
                    item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}

@Composable
private fun ScanRow(
    index: Int,
    count: Int,
    thumb: ImageBitmap?,
    enabled: Boolean,
    onAdjust: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onAdjust(index) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 64.dp, height = 84.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) Image(thumb, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(R.string.images_page_n, index + 1), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.scan_tap_to_adjust),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
