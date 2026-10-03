package io.github.karljuderojas.freepdf.ui.viewer

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import io.github.karljuderojas.freepdf.pdf.edit.PdfText
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore

/**
 * Something placed in Sign mode that can still be moved, resized or deleted. It is drawn over the
 * page until the user leaves Sign mode or finishes signing, and only then written into the PDF.
 */
data class PlacedStamp(val id: Long, val page: Int, val content: StampContent, val box: StampBox)

sealed interface StampContent {
    data class Signature(val kind: SignatureStore.Kind, val image: Bitmap) : StampContent

    /** Typed text or today's date; [what] names it on the audit page. */
    data class Text(val text: String, val what: String) : StampContent {
        val lines: List<String> get() = PdfText.lines(text)
    }

    data object Checkmark : StampContent
}

/**
 * Where a stamp sits, as fractions of the page as displayed (0..1, origin top-left), so it does
 * not depend on zoom or screen size.
 */
data class StampBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    /** Moved by [dx], [dy] (fractions of the page), stopping at the page's edges. */
    fun moved(dx: Float, dy: Float) = copy(
        left = (left + dx).coerceIn(0f, (1f - width).coerceAtLeast(0f)),
        top = (top + dy).coerceIn(0f, (1f - height).coerceAtLeast(0f)),
    )

    /**
     * Grown or shrunk by [factor] from its top-left corner, keeping its shape. It stays on the
     * page and never gets smaller than [MIN_POINTS] on its short side, so it can still be read.
     */
    fun scaled(factor: Float, page: PageSize): StampBox {
        val shortSide = minOf(width * page.widthPt, height * page.heightPt)
        val smallest = MIN_POINTS / shortSide
        val largest = minOf((1f - left) / width, (1f - top) / height)
        if (largest < smallest) return this
        val f = factor.coerceIn(smallest, largest)
        return copy(width = width * f, height = height * f)
    }

    companion object {
        const val MIN_POINTS = 6f
    }
}

/**
 * Sizes and anchors for stamps, in page points. The PDF layer draws each stamp from an anchor
 * point (see SignatureStamper and PageEditor), so these convert between that and a [StampBox].
 */
object StampGeometry {

    const val TEXT_SIZE = 11f
    const val CHECKMARK_SIZE = 10f
    private const val LEADING = 1.2f

    // Where the first baseline sits below the top of a text box, in ems.
    private const val BASELINE = 0.95f

    /** The largest a signature or initials are placed at first, in points. */
    fun maxSize(kind: SignatureStore.Kind): Pair<Float, Float> =
        if (kind == SignatureStore.Kind.Initials) 60f to 32f else 160f to 56f

    /** A signature image fitted into its default size, centred over [at] with its bottom on it. */
    fun signatureBox(at: Offset, imageWidth: Int, imageHeight: Int, kind: SignatureStore.Kind, page: PageSize): StampBox {
        val (maxWidth, maxHeight) = maxSize(kind)
        val scale = minOf(maxWidth / imageWidth, maxHeight / imageHeight)
        val width = imageWidth * scale / page.widthPt
        val height = imageHeight * scale / page.heightPt
        return StampBox(at.x - width / 2, at.y - height, width, height).moved(0f, 0f)
    }

    /**
     * Text with the first line's baseline on [at], starting there. [emWidth] measures one line in
     * ems (its width at a font size of 1).
     */
    fun textBox(at: Offset, lines: List<String>, page: PageSize, emWidth: (String) -> Float): StampBox {
        val width = lines.maxOf(emWidth) * TEXT_SIZE / page.widthPt
        val height = lines.size * LEADING * TEXT_SIZE / page.heightPt
        return StampBox(at.x, at.y - BASELINE * TEXT_SIZE / page.heightPt, width, height).moved(0f, 0f)
    }

    /** A checkmark with its bottom point on [at]. */
    fun checkmarkBox(at: Offset, page: PageSize): StampBox {
        val width = CHECKMARK_SIZE / page.widthPt
        val height = CHECKMARK_SIZE / page.heightPt
        return StampBox(at.x - 0.4f * width, at.y - height, width, height).moved(0f, 0f)
    }

    /** Where SignatureStamper anchors a signature filling [box]: the middle of its bottom edge. */
    fun signatureAnchor(box: StampBox) = Offset(box.left + box.width / 2, box.bottom)

    /** The font size, in points, at which [lines] of text fill [box]. */
    fun fontSize(box: StampBox, lines: Int, page: PageSize) = box.height * page.heightPt / (lines * LEADING)

    /** The first line's baseline at its left end, where PageEditor.addText starts. */
    fun textAnchor(box: StampBox, fontSize: Float, page: PageSize) =
        Offset(box.left, box.top + BASELINE * fontSize / page.heightPt)

    /** A checkmark's height in points when it fills [box]. */
    fun checkmarkSize(box: StampBox, page: PageSize) = minOf(box.width * page.widthPt, box.height * page.heightPt)

    /** Where PageEditor.addCheckmark puts the checkmark's bottom point. */
    fun checkmarkAnchor(box: StampBox, page: PageSize) =
        Offset(box.left + 0.4f * checkmarkSize(box, page) / page.widthPt, box.bottom)
}
