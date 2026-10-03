package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.text.PageWord
import io.github.karljuderojas.freepdf.pdf.text.Reflow
import io.github.karljuderojas.freepdf.settings.PageColors
import io.github.karljuderojas.freepdf.speech.ReadAloudState

/**
 * Reading mode: the text of every page as wrapping paragraphs at a size the reader picks, in the
 * page colors, so a PDF made for print reads comfortably on a small screen. Pictures and layout
 * are left out; a page with no text (a scan) says so.
 */
@Composable
internal fun ReflowView(
    pageCount: Int,
    revision: Int,
    loadWords: suspend (page: Int) -> List<PageWord>,
    textSize: Int,
    pageColors: PageColors,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    spoken: ReadAloudState? = null,
) {
    Box(modifier.fillMaxSize().background(pageColors.paper), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxHeight().widthIn(max = READING_WIDTH).fillMaxWidth().testTag("reflow-list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(pageCount, key = { it }) { page ->
                val paragraphs by produceState<List<String>?>(null, page, revision) {
                    value = Reflow.paragraphs(loadWords(page))
                }
                ReflowPage(page, paragraphs, textSize, pageColors, spoken?.takeIf { it.page == page }?.sentence)
            }
        }
    }
}

@Composable
private fun ReflowPage(page: Int, paragraphs: List<String>?, textSize: Int, pageColors: PageColors, spokenSentence: Int?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy((textSize * 0.7f).dp)) {
        Text(
            stringResource(R.string.page_label, page + 1),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = pageColors.ink.copy(alpha = 0.6f),
        )
        when {
            paragraphs == null -> Unit
            paragraphs.isEmpty() -> Text(
                stringResource(R.string.reading_mode_no_text),
                fontSize = textSize.sp,
                color = pageColors.ink.copy(alpha = 0.6f),
            )
            else -> {
                // The sentence being read aloud is marked; sentences count on through the paragraphs.
                var before = 0
                paragraphs.forEach { paragraph ->
                    val ranges = Reflow.sentenceRanges(paragraph)
                    val marked = spokenSentence?.minus(before)?.let { ranges.getOrNull(it) }
                    before += ranges.size
                    Text(
                        buildAnnotatedString {
                            append(paragraph)
                            if (marked != null) addStyle(SpanStyle(background = SPOKEN), marked.first, marked.last + 1)
                        },
                        fontSize = textSize.sp,
                        lineHeight = (textSize * 1.5f).sp,
                        color = pageColors.ink,
                    )
                }
            }
        }
    }
}

/** Top-bar buttons for reading mode's text size; each stops at its end of the range. */
@Composable
internal fun TextSizeButtons(textSize: Int, onTextSize: (Int) -> Unit, min: Int, max: Int, step: Int) {
    val smaller = stringResource(R.string.reading_mode_smaller)
    val larger = stringResource(R.string.reading_mode_larger)
    TextButton(
        onClick = { onTextSize(textSize - step) },
        enabled = textSize > min,
        modifier = Modifier.semantics { contentDescription = smaller },
    ) { Text("A", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    TextButton(
        onClick = { onTextSize(textSize + step) },
        enabled = textSize < max,
        modifier = Modifier.semantics { contentDescription = larger },
    ) { Text("A", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

/** The "Aa" button in Read mode's top bar that opens reading mode. */
@Composable
internal fun ReadingModeButton(onClick: () -> Unit) {
    val label = stringResource(R.string.reading_mode)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }.testTag("reading-mode")) {
        Text("Aa", fontWeight = FontWeight.Bold)
    }
}

private val READING_WIDTH = 640.dp
private val SPOKEN = Color(0x66FFD600)
