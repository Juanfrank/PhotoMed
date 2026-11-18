package com.photomed.app.align

import kotlin.math.sqrt

/**
 * Campo de orientación de contornos en "ángulo doble": (gx² − gy², 2·gx·gy) / |g|.
 * Conserva la magnitud del borde y su dirección, pero es invariante a la polaridad
 * (piel clara sobre fondo oscuro o al revés), lo que hace la coincidencia robusta a cambios de
 * fondo e iluminación entre sesiones. Al rotar la imagen θ, el campo rota 2θ.
 */
class OrientationField(val width: Int, val height: Int, val cx: FloatArray, val cy: FloatArray) {
    fun sample(px: Float, py: Float, out: FloatArray): Boolean {
        if (px < 0f || py < 0f || px > width - 1f || py > height - 1f) return false
        val x0 = px.toInt().coerceAtMost(width - 2); val y0 = py.toInt().coerceAtMost(height - 2)
        val fx = px - x0; val fy = py - y0
        val i = y0 * width + x0
        fun bil(a: FloatArray): Float {
            val t = a[i] + (a[i + 1] - a[i]) * fx
            val b = a[i + width] + (a[i + width + 1] - a[i + width]) * fx
            return t + (b - t) * fy
        }
        out[0] = bil(cx); out[1] = bil(cy)
        return true
    }

    companion object {
        fun of(img: GrayImage, blur: Int): OrientationField {
            val w = img.width; val h = img.height
            val s = img.boxBlur(1)
            val gx = GrayImage(w, h); val gy = GrayImage(w, h)
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                gx.data[y * w + x] = -s[x - 1, y - 1] - 2 * s[x - 1, y] - s[x - 1, y + 1] +
                    s[x + 1, y - 1] + 2 * s[x + 1, y] + s[x + 1, y + 1]
                gy.data[y * w + x] = -s[x - 1, y - 1] - 2 * s[x, y - 1] - s[x + 1, y - 1] +
                    s[x - 1, y + 1] + 2 * s[x, y + 1] + s[x + 1, y + 1]
            }
            val c1 = GrayImage(w, h); val c2 = GrayImage(w, h)
            for (i in 0 until w * h) {
                val a = gx.data[i]; val b = gy.data[i]
                val m = sqrt(a * a + b * b) + 1e-6f
                c1.data[i] = (a * a - b * b) / m
                c2.data[i] = 2 * a * b / m
            }
            return OrientationField(w, h, c1.boxBlur(blur).data, c2.boxBlur(blur).data)
        }
    }
}
