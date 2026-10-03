package io.github.karljuderojas.freepdf.ui.tools

import androidx.annotation.StringRes
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.ui.viewer.ViewerMode

/** One tool in the Tools tab: the viewer [mode] it lives in and its [label] there. */
data class ToolEntry(val mode: ViewerMode, @StringRes val label: Int) {
    /** A short line saying what the tool does, if one is written. */
    @get:StringRes val description: Int? get() = descriptions[label]

    /** Other words people search for, comma-separated, if any are written. */
    @get:StringRes val synonyms: Int? get() = synonymLists[label]

    /**
     * Annotate and Sign tools, and Edit's Add text, stay selected; Pages and More tools and Add
     * image act once, so only the mode opens.
     */
    val preselects: Boolean get() = mode == ViewerMode.Annotate || mode == ViewerMode.Sign || label == R.string.tool_add_text || label == R.string.tool_edit_text
}

/**
 * A tool that makes a new PDF, so it needs no open document and has no viewer mode. They are
 * listed above the viewer's tools and open their own screen.
 */
enum class CreateTool(@StringRes val label: Int, @StringRes val description: Int, @StringRes val synonyms: Int) {
    ImagesToPdf(R.string.tool_images_to_pdf, R.string.tool_about_images_to_pdf, R.string.tool_synonyms_images_to_pdf),
}

/**
 * Every working tool, grouped like the viewer's mode bar with signing first. It is read from
 * [ViewerMode], so a tool shows up here as soon as it ships there.
 */
val toolCatalog: List<ToolEntry>
    get() = listOf(ViewerMode.Sign, ViewerMode.Annotate, ViewerMode.Edit, ViewerMode.Pages, ViewerMode.More)
        .filter { it in ViewerMode.barModes }
        .flatMap { mode -> mode.tools.map { ToolEntry(mode, it) } }

/**
 * True when every word of [query] starts a word in one of [names] (a tool's label, its mode and its
 * synonyms). So "sig" finds Signature, "cross out" finds Strikeout, and an empty query finds all.
 */
fun matchesToolQuery(query: String, names: List<String>): Boolean {
    val wanted = words(query)
    if (wanted.isEmpty()) return true
    val known = names.flatMap(::words)
    return wanted.all { w -> known.any { it.startsWith(w) } }
}

private fun words(text: String): List<String> =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

private val descriptions = mapOf(
    R.string.tool_signature to R.string.tool_about_signature,
    R.string.tool_initials to R.string.tool_about_initials,
    R.string.tool_date to R.string.tool_about_date,
    R.string.tool_text to R.string.tool_about_text,
    R.string.tool_checkmark to R.string.tool_about_checkmark,
    R.string.tool_highlight to R.string.tool_about_highlight,
    R.string.tool_underline to R.string.tool_about_underline,
    R.string.tool_strikeout to R.string.tool_about_strikeout,
    R.string.tool_pen to R.string.tool_about_pen,
    R.string.tool_note to R.string.tool_about_note,
    R.string.tool_shapes to R.string.tool_about_shapes,
    R.string.tool_eraser to R.string.tool_about_eraser,
    R.string.tool_edit_text to R.string.tool_about_edit_text,
    R.string.tool_add_text to R.string.tool_about_add_text,
    R.string.tool_add_image to R.string.tool_about_add_image,
    R.string.tool_rotate to R.string.tool_about_rotate,
    R.string.tool_crop to R.string.tool_about_crop,
    R.string.tool_move_earlier to R.string.tool_about_move_earlier,
    R.string.tool_move_later to R.string.tool_about_move_later,
    R.string.tool_insert to R.string.tool_about_insert,
    R.string.tool_delete to R.string.tool_about_delete,
    R.string.tool_merge to R.string.tool_about_merge,
    R.string.tool_watermark to R.string.tool_about_watermark,
    R.string.tool_share to R.string.tool_about_share,
    R.string.tool_password to R.string.tool_about_password,
    R.string.tool_info to R.string.tool_about_info,
    R.string.tool_print to R.string.tool_about_print,
)

private val synonymLists = mapOf(
    R.string.tool_signature to R.string.tool_synonyms_signature,
    R.string.tool_initials to R.string.tool_synonyms_initials,
    R.string.tool_date to R.string.tool_synonyms_date,
    R.string.tool_text to R.string.tool_synonyms_text,
    R.string.tool_checkmark to R.string.tool_synonyms_checkmark,
    R.string.tool_highlight to R.string.tool_synonyms_highlight,
    R.string.tool_underline to R.string.tool_synonyms_underline,
    R.string.tool_strikeout to R.string.tool_synonyms_strikeout,
    R.string.tool_pen to R.string.tool_synonyms_pen,
    R.string.tool_note to R.string.tool_synonyms_note,
    R.string.tool_shapes to R.string.tool_synonyms_shapes,
    R.string.tool_eraser to R.string.tool_synonyms_eraser,
    R.string.tool_edit_text to R.string.tool_synonyms_edit_text,
    R.string.tool_add_text to R.string.tool_synonyms_add_text,
    R.string.tool_add_image to R.string.tool_synonyms_add_image,
    R.string.tool_rotate to R.string.tool_synonyms_rotate,
    R.string.tool_crop to R.string.tool_synonyms_crop,
    R.string.tool_move_earlier to R.string.tool_synonyms_reorder,
    R.string.tool_move_later to R.string.tool_synonyms_reorder,
    R.string.tool_insert to R.string.tool_synonyms_insert,
    R.string.tool_delete to R.string.tool_synonyms_delete,
    R.string.tool_merge to R.string.tool_synonyms_merge,
    R.string.tool_watermark to R.string.tool_synonyms_watermark,
    R.string.tool_share to R.string.tool_synonyms_share,
    R.string.tool_password to R.string.tool_synonyms_password,
    R.string.tool_info to R.string.tool_synonyms_info,
    R.string.tool_print to R.string.tool_synonyms_print,
)
