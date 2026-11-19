package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale

/** Carga, recorte 3:4, corrección de perspectiva y escala de grises. */
object ImageOps {

    suspend fun load34(context: Context, uri: Uri, width: Int): Bitmap {
        val request = ImageRequest.Builder(context)
            .data(uri).size(width, width * 4 / 3).scale(Scale.FILL).precision(Precision.EXACT)
            .allowHardware(false).build()
        val bmp = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            ?.let { (it as BitmapDrawable).bitmap } ?: error("No se pudo abrir la imagen")
        return centerCrop34(bmp, width)
    }

    fun centerCrop34(src: Bitmap, width: Int): Bitmap {
        val targetH = width * 4 / 3
        val scale = maxOf(width.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaled = Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(width),
            (src.height * scale).toInt().coerceAtLeast(targetH), true)
        val out = Bitmap.createBitmap(scaled, (scaled.width - width) / 2, (scaled.height - targetH) / 2, width, targetH)
        if (scaled !== src && scaled !== out) scaled.recycle()
        return out
    }

    /** Endereza la perspectiva vertical (ver [Perspective]); con 0° devuelve la misma imagen. */
    fun correctPerspective(src: Bitmap, cameraPitchDeg: Float, focalNorm: Float): Bitmap {
        if (cameraPitchDeg == 0f) return src
        val w = src.width.toDouble(); val h = src.height.toDouble()
        val dst = Perspective.cornerTargets(w, h, cameraPitchDeg.toDouble(), focalNorm.toDouble())
        val m = Matrix()
        m.setPolyToPoly(
            floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat()), 0,
            dst.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray(), 0, 4,
        )
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Gris estandarizado con el fondo aplanado fuera de [body] (ver [BodyRegion]); sin máscara, normal. */
    fun gray(bmp: Bitmap, width: Int, body: Mask?): GrayImage =
        if (body == null) gray(bmp, width) else BodyRegion.flattenBackground(grayRaw(bmp, width), body)

    fun gray(bmp: Bitmap, width: Int): GrayImage = grayRaw(bmp, width).standardized()

    private fun grayRaw(bmp: Bitmap, width: Int): GrayImage {
        val small = Bitmap.createScaledBitmap(bmp, width, width * 4 / 3, true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val g = GrayImage(small.width, small.height)
        for (i in px.indices) {
            val c = px[i]
            g.data[i] = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        }
        return g
    }
}
