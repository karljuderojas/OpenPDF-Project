package io.github.karljuderojas.freepdf.ui.viewer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.pdf.edit.PageRanges

/** What to send, from the UX blueprint's share sheet (screen Q). */
enum class ShareOption(@StringRes val title: Int, @StringRes val detail: Int, val needsPages: Boolean = false) {
    WithChanges(R.string.share_with_changes, R.string.share_with_changes_detail),
    Locked(R.string.share_locked, R.string.share_locked_detail),
    SomePages(R.string.share_some_pages, R.string.share_some_pages_detail, needsPages = true),
    Images(R.string.share_images, R.string.share_images_detail, needsPages = true),
}

/** Most page images one share makes; each is a full-size JPEG. */
const val MAX_SHARED_IMAGES = 50

/**
 * Asks what to share, then hands it to Android's share menu. [currentPage] is zero-based and is
 * the starting choice for "Some pages only"; "As images" starts with every page of a short PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    pageCount: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onShare: (ShareOption, pages: List<Int>) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    fun defaultPages(option: ShareOption) = when {
        option == ShareOption.Images && pageCount <= 10 -> PageRanges.format((0 until pageCount).toList())
        else -> PageRanges.format(listOf(currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))))
    }

    var option by rememberSaveable { mutableStateOf(ShareOption.WithChanges) }
    var pagesText by rememberSaveable { mutableStateOf("") }
    val pages = if (option.needsPages) PageRanges.parse(pagesText, pageCount) else null
    val tooMany = option == ShareOption.Images && (pages?.size ?: 0) > MAX_SHARED_IMAGES
    val ready = !option.needsPages || (pages != null && !tooMany)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.share_sheet_title), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.selectableGroup()) {
                ShareOption.entries.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = entry == option,
                                onClick = {
                                    if (entry != option) pagesText = defaultPages(entry)
                                    option = entry
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = entry == option, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(entry.title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(entry.detail),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (option.needsPages) {
                val invalid = pagesText.isNotBlank() && pages == null
                OutlinedTextField(
                    value = pagesText,
                    onValueChange = { pagesText = it },
                    label = { Text(stringResource(R.string.share_pages_label)) },
                    singleLine = true,
                    isError = invalid || tooMany,
                    supportingText = {
                        Text(
                            when {
                                tooMany -> stringResource(R.string.share_images_too_many, MAX_SHARED_IMAGES)
                                invalid -> stringResource(R.string.share_pages_invalid, pageCount)
                                else -> stringResource(R.string.share_pages_hint)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("share-pages"),
                )
            }
            Button(
                onClick = { onShare(option, pages.orEmpty()) },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.tool_share)) }
        }
    }
}
