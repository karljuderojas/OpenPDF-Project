package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.first
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.render.PageSize

/**
 * Pages mode: every page as a thumbnail. Tap a page to select it, tap the circles to select
 * several, and hold a page then drag it to move it. The tool strip acts on the selection.
 */
@Composable
fun PageGrid(
    pageSizes: List<PageSize>,
    revision: Int,
    selectedPages: Set<Int>,
    onPageTapped: (Int) -> Unit,
    onSelectionToggled: (Int) -> Unit,
    onPageMoved: (from: Int, to: Int) -> Unit,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) { gridState.scrollToItem(selectedPages.minOrNull() ?: 0) }
    val onMoved by rememberUpdatedState(onPageMoved)
    // A fresh order after every edit; a drop is shown in its new place until the edit lands.
    val drag = remember(revision, pageSizes.size) { PageDrag(gridState, revision, pageSizes.size) }

    // Holding a page near the top or bottom edge scrolls the grid, so it can travel any distance.
    val edgePx = with(LocalDensity.current) { 64.dp.toPx() }
    LaunchedEffect(drag) {
        while (true) {
            // Waits without asking for frames until a held page reaches an edge.
            val speed = snapshotFlow { edgeSpeed(drag.center()?.y, gridState.layoutInfo.viewportSize.height, edgePx) }
                .first { it != 0f }
            gridState.scrollBy(speed)
            drag.swapIfOver()
            withFrameNanos { }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth < 600.dp) 2 else 4
        val thumbWidthPx = with(LocalDensity.current) { (maxWidth / columns).roundToPx() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.pages_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(drag.order, key = { pageKey(revision, it) }) { page ->
                val selected = page in selectedPages
                val dragged = drag.page == page
                val shape = RoundedCornerShape(4.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .pointerInput(drag, page) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { drag.start(page) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag.dragBy(amount)
                                },
                                onDragEnd = { drag.end()?.let { (from, to) -> onMoved(from, to) } },
                                onDragCancel = { drag.cancel() },
                            )
                        }
                        .clickable { onPageTapped(page) }
                        // The dragged page follows the finger; the others slide out of its way.
                        .animateItem(
                            fadeInSpec = null,
                            placementSpec = if (dragged) null else spring(
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                            fadeOutSpec = null,
                        )
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer {
                            if (dragged) {
                                val shift = drag.translation()
                                translationX = shift.x
                                translationY = shift.y
                                scaleX = 1.05f
                                scaleY = 1.05f
                                shadowElevation = 8.dp.toPx()
                            }
                        },
                ) {
                    PageImage(
                        page, pageSizes[page], revision, thumbWidthPx, loadPage,
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = shape,
                            ),
                    ) {
                        SelectionCircle(
                            page = page,
                            selected = selected,
                            onToggle = { onSelectionToggled(page) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                    Text(
                        stringResource(R.string.page_number, page + 1),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else null,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Pixels per frame to scroll while a held page at [y] is within [edge] of the top or bottom. */
private fun edgeSpeed(y: Float?, height: Int, edge: Float): Float = when {
    y == null -> 0f
    y < edge -> -(edge - y) / 4
    y > height - edge -> (y - (height - edge)) / 4
    else -> 0f
}

/** The tick in a thumbnail's corner that adds the page to the selection or takes it out. */
@Composable
private fun SelectionCircle(page: Int, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.select_page, page + 1)
    Box(
        modifier
            .size(48.dp)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp).background(Color.White, CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(24.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

/**
 * Grid keys change with every edit, so a page number is never mistaken for the page it replaced.
 * Strings, because Android needs lazy-grid keys it can put in a Bundle.
 */
private fun pageKey(revision: Int, page: Int) = "page:$revision:$page"

private fun pageOfKey(key: Any): Int? = (key as? String)?.takeIf { it.startsWith("page:") }?.substringAfterLast(':')?.toIntOrNull()

/**
 * A page being dragged to a new place. [order] is how the grid shows the pages (by their index in
 * the file) while the drag lasts; the document itself changes once, when the page is dropped.
 * Positions are relative to the grid's viewport, so scrolling under a held page keeps it in place.
 */
@Stable
private class PageDrag(private val grid: LazyGridState, private val revision: Int, pageCount: Int) {
    var order by mutableStateOf(List(pageCount) { it })
        private set
    var page by mutableStateOf<Int?>(null)
        private set
    private var from = -1
    private var startOffset = Offset.Zero
    private var delta by mutableStateOf(Offset.Zero)

    private fun slot(page: Int) = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pageKey(revision, page) }

    fun start(page: Int) {
        val slot = slot(page) ?: return
        this.page = page
        from = order.indexOf(page)
        startOffset = slot.offset.toOffset()
        delta = Offset.Zero
    }

    fun dragBy(amount: Offset) {
        if (page == null) return
        delta += amount
        swapIfOver()
    }

    /** Middle of the held page, where the finger has taken it. */
    fun center(): Offset? {
        val slot = slot(page ?: return null) ?: return null
        return startOffset + delta + Offset(slot.size.width / 2f, slot.size.height / 2f)
    }

    /** How far the held page is drawn from its current slot in the grid. */
    fun translation(): Offset {
        val slot = slot(page ?: return Offset.Zero) ?: return Offset.Zero
        return startOffset + delta - slot.offset.toOffset()
    }

    /** Once the held page is over another one, it takes that page's place. */
    fun swapIfOver() {
        val held = page ?: return
        val center = center() ?: return
        val target = grid.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { slot ->
            pageOfKey(slot.key)?.takeIf { it != held && Rect(slot.offset.toOffset(), slot.size.toSize()).contains(center) }
        } ?: return
        val to = order.indexOf(target)
        order = order.toMutableList().apply {
            remove(held)
            add(to, held)
        }
    }

    /** Drops the page. Returns where it came from and where it went, or null if it ended where it began. */
    fun end(): Pair<Int, Int>? {
        val held = page ?: return null
        page = null
        val to = order.indexOf(held)
        return if (to != from) from to to else null
    }

    fun cancel() {
        page = null
        order = order.sorted()
    }
}
