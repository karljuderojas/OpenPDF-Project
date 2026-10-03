package io.github.karljuderojas.freepdf.pdf.sign

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/** Made-up photos of signatures on paper, for tests and screenshots. */
object SignaturePhotos {

    /** Paper lit unevenly (a shadow on the left) with a looping blue-black signature on it. */
    fun make(width: Int = 900, height: Int = 400): Bitmap {
        val photo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(photo)
        val light = Paint().apply { shader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.rgb(170, 168, 160), Color.rgb(238, 236, 230), Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), light)
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 35, 80)
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path().apply {
            moveTo(200f, 260f)
            cubicTo(260f, 80f, 330f, 80f, 330f, 200f)
            cubicTo(330f, 300f, 420f, 100f, 520f, 190f)
            cubicTo(580f, 250f, 650f, 120f, 700f, 170f)
            moveTo(190f, 300f)
            lineTo(710f, 285f)
        }
        canvas.drawPath(path, pen)
        return photo
    }
}
