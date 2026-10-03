package io.github.karljuderojas.freepdf.pdf.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlin.math.max

/** Loads scan photos and turns a [ScanPage] into the picture that goes in the PDF. */
object ScanImages {

    /**
     * Decodes [file] no larger than [maxSide] on its long side, turned the way the camera held it.
     * Reads the EXIF turn itself so it works the same on every Android version.
     */
    fun load(file: File, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Cannot decode ${file.path}")
        val orientation = runCatching {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = matrixFor(orientation)
        val scale = minOf(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
        if (scale < 1f) matrix.postScale(scale, scale)
        if (matrix.isIdentity) return decoded
        val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (turned !== decoded) decoded.recycle()
        return turned
    }

    /** The matrix that undoes an EXIF orientation tag. */
    internal fun matrixFor(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
        }
    }

    /** The finished page: [page]'s photo cut to its corners, straightened, with [filter] applied, at most [maxSide] across. */
    fun render(page: ScanPage, filter: ScanFilter, maxSide: Int): Bitmap {
        val photo = load(File(page.file), maxSide)
        val flat = try {
            PerspectiveWarp.warp(photo, page.quad, maxSide)
        } finally {
            photo.recycle()
        }
        return try {
            ScanFilters.apply(flat, filter)
        } finally {
            flat.recycle()
        }
    }
}
