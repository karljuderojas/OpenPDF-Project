package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.render.PageSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(uri: Uri, onBack: () -> Unit, viewModel: ViewerViewModel = viewModel()) {
    LaunchedEffect(uri) { viewModel.open(uri) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    var mode by rememberSaveable { mutableStateOf(ViewerMode.Read) }
    var selectedTool by rememberSaveable { mutableStateOf<Int?>(null) }
    val ready = state is ViewerState.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val pages = (state as? ViewerState.Ready)?.pageSizes?.size
                    Text(
                        when {
                            mode != ViewerMode.Read -> stringResource(mode.label)
                            pages != null -> stringResource(R.string.page_of, currentPage, pages)
                            else -> stringResource(R.string.app_name)
                        },
                    )
                },
                navigationIcon = {
                    if (mode == ViewerMode.Read) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (mode != ViewerMode.Read) {
                        TextButton(onClick = { mode = ViewerMode.Read; selectedTool = null }) {
                            Text(stringResource(R.string.done))
                        }
                    }
                },
            )
        },
        bottomBar = {
            when {
                !ready -> Unit
                mode == ViewerMode.Read -> ModeBar(onModeSelected = { mode = it })
                else -> ToolStrip(mode, selectedTool, onToolSelected = { selectedTool = it })
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                ViewerState.Loading -> CircularProgressIndicator()
                is ViewerState.Failed -> Text(stringResource(R.string.error_open))
                is ViewerState.Ready -> PageList(s.pageSizes, viewModel, listState)
            }
        }
    }
}

@Composable
private fun PageList(
    pageSizes: List<PageSize>,
    viewModel: ViewerViewModel,
    listState: LazyListState,
) {
    var zoom by remember { mutableFloatStateOf(1f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Pinch to zoom; single-finger drags fall through to the list's scrolling.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer { scaleX = zoom; scaleY = zoom },
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(pageSizes) { index, size ->
                PageItem(index, size, widthPx, viewModel)
            }
        }
    }
}

@Composable
private fun PageItem(index: Int, size: PageSize, widthPx: Int, viewModel: ViewerViewModel) {
    val bitmap by produceState<Bitmap?>(null, index, widthPx) {
        value = viewModel.page(index, widthPx)
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(size.aspectRatio).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}
