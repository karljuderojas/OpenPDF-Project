package io.github.karljuderojas.freepdf.pdf.scan

import kotlin.math.abs

/** A point in a picture, as a fraction of its width and height (0 is the left or top edge). */
data class Corner(val x: Float, val y: Float)

/** The four corners of a page in a photo, in reading order from the top left, clockwise. */
data class Quad(val topLeft: Corner, val topRight: Corner, val bottomRight: Corner, val bottomLeft: Corner) {

    val corners: List<Corner> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** A copy with the corner at [index] (0 top left, then clockwise) moved to [corner], kept inside the photo. */
    fun withCorner(index: Int, corner: Corner): Quad {
        val c = Corner(corner.x.coerceIn(0f, 1f), corner.y.coerceIn(0f, 1f))
        return when (index) {
            0 -> copy(topLeft = c)
            1 -> copy(topRight = c)
            2 -> copy(bottomRight = c)
            else -> copy(bottomLeft = c)
        }
    }

    /** The area as a fraction of the photo. */
    val area: Float
        get() {
            var sum = 0f
            for (i in corners.indices) {
                val a = corners[i]
                val b = corners[(i + 1) % 4]
                sum += a.x * b.y - b.x * a.y
            }
            return abs(sum) / 2f
        }

    /** True if the corners form a convex four-sided shape that is big enough to be a page, not a twisted bow tie. */
    val isUsable: Boolean
        get() {
            var sign = 0
            for (i in corners.indices) {
                val a = corners[i]
                val b = corners[(i + 1) % 4]
                val c = corners[(i + 2) % 4]
                val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                val s = if (cross > 0f) 1 else if (cross < 0f) -1 else 0
                if (s == 0) return false
                if (sign == 0) sign = s else if (sign != s) return false
            }
            return area >= MIN_AREA
        }

    fun encode(): String = corners.joinToString(",") { "${it.x};${it.y}" }

    companion object {
        /** Smallest share of the photo a page may cover. */
        const val MIN_AREA = 0.02f

        /** The whole photo, pulled in by [margin] on each side. */
        fun inset(margin: Float = 0f): Quad = Quad(
            Corner(margin, margin),
            Corner(1f - margin, margin),
            Corner(1f - margin, 1f - margin),
            Corner(margin, 1f - margin),
        )

        fun decode(text: String): Quad? = runCatching {
            val c = text.split(',').map { pair ->
                val (x, y) = pair.split(';')
                Corner(x.toFloat(), y.toFloat())
            }
            if (c.size == 4) Quad(c[0], c[1], c[2], c[3]) else null
        }.getOrNull()
    }
}

/** One scanned page: the photo's file, which corners to straighten, and an id that survives reordering. */
data class ScanPage(val id: Long, val file: String, val quad: Quad) {

    fun encode(): String = "$id|$file|${quad.encode()}"

    companion object {
        fun decode(text: String): ScanPage? = runCatching {
            val parts = text.split('|', limit = 3)
            val quad = Quad.decode(parts[2]) ?: return null
            ScanPage(parts[0].toLong(), parts[1], quad)
        }.getOrNull()
    }
}
