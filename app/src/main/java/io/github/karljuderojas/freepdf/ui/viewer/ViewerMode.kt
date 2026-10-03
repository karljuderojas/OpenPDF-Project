package io.github.karljuderojas.freepdf.ui.viewer

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.karljuderojas.freepdf.R

/**
 * The viewer's modes, following the UX blueprint: every PDF opens in Read, and the labeled mode
 * bar swaps in one mode's tools at a time, with Done to go back to reading.
 *
 * Only tools that work are listed. Unfinished ones (see docs/ROADMAP.md) are added here when they
 * ship rather than shown as "Coming soon", and a mode with no working tools stays off the bar.
 */
enum class ViewerMode(@StringRes val label: Int, val icon: ImageVector?, val tools: List<Int>) {
    Read(R.string.mode_read, null, emptyList()),
    Annotate(
        R.string.mode_annotate, Icons.Filled.Create,
        listOf(R.string.tool_highlight, R.string.tool_underline, R.string.tool_strikeout, R.string.tool_pen,
            R.string.tool_note, R.string.tool_shapes, R.string.tool_eraser),
    ),
    Sign(
        R.string.mode_sign, Icons.Filled.Edit,
        listOf(R.string.tool_signature, R.string.tool_initials, R.string.tool_date, R.string.tool_text,
            R.string.tool_checkmark),
    ),
    // Hidden until its first tools (add text, add image) ship.
    Edit(R.string.mode_edit, Icons.Filled.Build, emptyList()),
    Pages(
        R.string.mode_pages, Icons.AutoMirrored.Filled.List,
        listOf(R.string.tool_rotate, R.string.tool_move_earlier, R.string.tool_move_later, R.string.tool_insert,
            R.string.tool_delete, R.string.tool_merge),
    ),
    More(
        R.string.mode_more, Icons.Filled.MoreVert,
        listOf(R.string.tool_share, R.string.tool_print),
    );

    companion object {
        val barModes = entries.filter { it != Read && it.tools.isNotEmpty() }
    }
}
