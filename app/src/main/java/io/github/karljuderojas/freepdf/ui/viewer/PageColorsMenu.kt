package io.github.karljuderojas.freepdf.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.settings.PageColors

/** Read mode's top-bar button for Normal, Night and Sepia pages. */
@Composable
fun PageColorsButton(pageColors: PageColors, onPageColors: (PageColors) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(ContrastIcon, contentDescription = stringResource(R.string.page_colors))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PageColors.entries.forEach { colors ->
                DropdownMenuItem(
                    text = { Text(stringResource(colors.label)) },
                    leadingIcon = { RadioButton(selected = colors == pageColors, onClick = null) },
                    onClick = {
                        open = false
                        onPageColors(colors)
                    },
                )
            }
        }
    }
}

/** A circle, half filled; the core icon set has no contrast icon. */
private val ContrastIcon: ImageVector = ImageVector.Builder(
    name = "Contrast",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
    // Outline ring: the outer circle minus the inner one.
    moveTo(12f, 2f)
    arcTo(10f, 10f, 0f, false, true, 12f, 22f)
    arcTo(10f, 10f, 0f, false, true, 12f, 2f)
    close()
    moveTo(12f, 4f)
    arcTo(8f, 8f, 0f, false, false, 12f, 20f)
    arcTo(8f, 8f, 0f, false, false, 12f, 4f)
    close()
}.path(fill = SolidColor(Color.Black)) {
    // The filled right half.
    moveTo(12f, 4f)
    arcTo(8f, 8f, 0f, false, true, 12f, 20f)
    close()
}.build()
