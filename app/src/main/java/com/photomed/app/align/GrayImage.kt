package com.photomed.app.align

import kotlin.math.floor
import kotlin.math.sqrt

/** Imagen en escala de grises (valores 0..1), fila mayor. */
class GrayImage(val width: Int, val height: Int, val data: FloatArray = FloatArray(width * height)) {

    operator fun get(x: Int, y: Int) = data[y * width + x]

    /** Muestreo bilineal en píxeles; NaN fuera de la imagen. */
    fun sample(px: Float, py: Float): Float {
        if (px < 0f || py < 0f || px > width - 1f || py > height - 1f) return Float.NaN
        val x0 = floor(px).toInt().coerceAtMost(width - 2)
        val y0 = floor(py).toInt().coerceAtMost(height - 2)
        val fx = px - x0
        val fy = py - y0
        val i = y0 * width + x0
        val top = data[i] + (data[i + 1] - data[i]) * fx
        val bottom = data[i + width] + (data[i + width + 1] - data[i + width]) * fx
        return top + (bottom - top) * fy
    }

    /** Reducción a la mitad (promedio 2×2). */
    fun half(): GrayImage {
        val w = width / 2
        val h = height / 2
        val out = GrayImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (2 * y) * width + 2 * x
            out.data[y * w + x] = (data[i] + data[i + 1] + data[i + width] + data[i + width + 1]) * 0.25f
        }
        return out
    }

    /** Magnitud del gradiente (Sobel): resalta contornos anatómicos y bordes corporales. */
    fun sobel(): GrayImage {
        val out = GrayImage(width, height)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val gx = -get(x - 1, y - 1) - 2 * get(x - 1, y) - get(x - 1, y + 1) +
                get(x + 1, y - 1) + 2 * get(x + 1, y) + get(x + 1, y + 1)
            val gy = -get(x - 1, y - 1) - 2 * get(x, y - 1) - get(x + 1, y - 1) +
                get(x - 1, y + 1) + 2 * get(x, y + 1) + get(x + 1, y + 1)
            out.data[y * width + x] = sqrt(gx * gx + gy * gy)
        }
        return out
    }

    /** Desenfoque de caja separable de radio [r] (amplía la cuenca de convergencia). */
    fun boxBlur(r: Int): GrayImage {
        if (r <= 0) return this
        val tmp = FloatArray(width * height)
        val out = GrayImage(width, height)
        for (y in 0 until height) for (x in 0 until width) {
            var s = 0f; var n = 0
            for (k in -r..r) { val xx = x + k; if (xx in 0 until width) { s += data[y * width + xx]; n++ } }
            tmp[y * width + x] = s / n
        }
        for (y in 0 until height) for (x in 0 until width) {
            var s = 0f; var n = 0
            for (k in -r..r) { val yy = y + k; if (yy in 0 until height) { s += tmp[yy * width + x]; n++ } }
            out.data[y * width + x] = s / n
        }
        return out
    }

    /** Ecualización simple: media 0, desviación 1 (compensa diferencias de exposición). */
    fun standardized(): GrayImage {
        val mean = data.average().toFloat()
        var v = 0f
        for (d in data) v += (d - mean) * (d - mean)
        val sd = sqrt(v / data.size).takeIf { it > 1e-6f } ?: 1f
        return GrayImage(width, height, FloatArray(data.size) { (data[it] - mean) / sd })
    }
}
