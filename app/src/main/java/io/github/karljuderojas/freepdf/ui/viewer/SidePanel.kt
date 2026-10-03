package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.annotate.Mark
import io.github.karljuderojas.freepdf.pdf.render.OutlineItem
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.settings.PageColors

/** How wide the viewer's side panel is on a large screen. */
internal val SIDE_PANEL_WIDTH = 300.dp

/**
 * The panel beside the document on a large screen: thumbnails of the pages, the table of contents
 * (when the PDF has one) and the comments, so none is a dialog to open and close. Choosing a page,
 * an entry or a mark goes there in the document.
 */
@Composable
internal fun ViewerSidePanel(
    pageSizes: List<PageSize>,
    revision: Int,
    currentPage: Int,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    pageColors: PageColors,
    outline: List<OutlineItem>,
    marks: List<Mark>,
    onGoToPage: (Int) -> Unit,
    onOpenMark: (Mark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = buildList {
        add(PanelTab.Pages)
        if (outline.isNotEmpty()) add(PanelTab.Contents)
        add(PanelTab.Comments)
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tab = tabs[selected.coerceIn(0, tabs.lastIndex)]
    Surface(modifier.width(SIDE_PANEL_WIDTH).fillMaxHeight().testTag("side-panel"), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tabs.indexOf(tab)) {
                tabs.forEachIndexed { index, t ->
                    Tab(
                        selected = t == tab,
                        onClick = { selected = index },
                        text = {
                            Text(
                                if (t == PanelTab.Comments && marks.isNotEmpty()) "${stringResource(t.label)} (${marks.size})" else stringResource(t.label),
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
            when (tab) {
                PanelTab.Pages -> Thumbnails(pageSizes, revision, currentPage, loadPage, pageColors, onGoToPage)
                PanelTab.Contents -> OutlineList(outline, onGoToPage, Modifier.fillMaxSize().padding(horizontal = 12.dp))
                PanelTab.Comments -> CommentsList(marks, onOpenMark, Modifier.fillMaxSize())
            }
        }
    }
}

private enum class PanelTab(val label: Int) {
    Pages(R.string.mode_pages),
    Contents(R.string.contents),
    Comments(R.string.tool_comments),
}

@Composable
private fun Thumbnails(
    pageSizes: List<PageSize>,
    revision: Int,
    currentPage: Int,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    pageColors: PageColors,
    onGoToPage: (Int) -> Unit,
) {
    val state: LazyListState = rememberLazyListState()
    // Keep the page being read in view in the strip.
    LaunchedEffect(currentPage) {
        val visible = state.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || currentPage < visible.first().index || currentPage > visible.last().index) {
            state.animateScrollToItem(currentPage.coerceAtMost((pageSizes.size - 1).coerceAtLeast(0)))
        }
    }
    val thumbnailWidth: Dp = 160.dp
    val widthPx = with(LocalDensity.current) { thumbnailWidth.roundToPx() }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag("thumbnails"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(pageSizes) { index, size ->
            val current = index == currentPage
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PageImage(
                    index, size, revision, widthPx, loadPage,
                    Modifier
                        .width(thumbnailWidth)
                        .border(if (current) 3.dp else 1.dp, if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        .clickable { onGoToPage(index) },
                    pageColors,
                )
                Text(
                    (index + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
