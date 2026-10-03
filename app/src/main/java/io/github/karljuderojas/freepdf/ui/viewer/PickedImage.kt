package io.github.karljuderojas.freepdf.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build

/** Loads a picture the user picked for Edit mode's Add image. */
object PickedImage {

    /**
     * Big enough to print sharply at the half-page size it is placed at, small enough that a
     * 50-megapixel photo neither runs the phone out of memory nor bloats the PDF.
     */
    const val MAX_SIDE = 2048

    /**
     * Decodes [uri] into a software bitmap (PdfBox reads its pixels) no larger than [MAX_SIDE] on
     * its long side. On Android 9 and later the photo is also turned the way the camera held it.
     */
    fun load(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = scaleFor(info.size.width, info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Cannot decode $uri")
        val scale = scaleFor(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun scaleFor(width: Int, height: Int): Float = minOf(1f, MAX_SIDE.toFloat() / maxOf(width, height, 1))
}
