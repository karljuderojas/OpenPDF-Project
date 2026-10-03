package io.github.karljuderojas.freepdf.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import io.github.karljuderojas.freepdf.pdf.scan.ScanImages

/** Loads a picture the user picked for Edit mode's Add image. */
object PickedImage {

    /**
     * Big enough to print sharply at the half-page size it is placed at, small enough that a
     * 50-megapixel photo neither runs the phone out of memory nor bloats the PDF.
     */
    const val MAX_SIDE = 2048

    /**
     * Decodes [uri] into a software bitmap (PdfBox reads its pixels) no larger than [maxSide] (by default
     * [MAX_SIDE]) on its long side, turned the way the camera held it.
     */
    fun load(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = scaleFor(info.size.width, info.size.height, maxSide)
                if (scale < 1f) {
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Cannot decode $uri")
        // Before Android 9 BitmapFactory ignores the EXIF turn, so a portrait photo would come out on its side.
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = ScanImages.matrixFor(orientation)
        val scale = scaleFor(decoded.width, decoded.height, maxSide)
        if (scale < 1f) matrix.postScale(scale, scale)
        if (matrix.isIdentity) return decoded
        val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (turned !== decoded) decoded.recycle()
        return turned
    }

    private fun scaleFor(width: Int, height: Int, maxSide: Int): Float = minOf(1f, maxSide.toFloat() / maxOf(width, height, 1))
}
