package com.photomed.app.align

import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Uñas de los dedos (de la mano y del pie): solo se ven por el dorso, en la falange distal, junto a la
 * punta. Una uña es una placa de color distinto al de la piel del dedo (más clara y rosada, o del color
 * del esmalte), lisa y de borde nítido. Sirven para confirmar cada dedo, como punto de referencia preciso
 * y para distinguir el dorso de la palma (mano) o de la planta (pie).
 *
 * Coordenadas en píxeles de la imagen [rgb] (w×h).
 */
object Nails {
    /** Una uña: centro (px), radio (px) y contraste de color con la piel del dedo (ΔE, CIELAB). */
    class Nail(val x: Float, val y: Float, val r: Float, val contrast: Float)

    /** Contraste mínimo (ΔE) entre la placa de la uña y la piel del mismo dedo. */
    const val MIN_DE = 7f

    /**
     * Uña del dedo cuya punta está en ([tx], [ty]), con dirección distal unitaria ([ux], [uy]) (de la
     * articulación hacia la punta) y ancho [width] (px). Se busca a lo largo del dedo, por detrás de la
     * punta, la placa más distinta de la piel del dedo (más atrás, en la falange media o el antepié);
     * null si no hay ninguna con contraste suficiente o si se sale de la imagen. [inside]: piel del dedo
     * (opcional: la placa y la piel de referencia deben caer dentro).
     */
    fun find(rgb: IntArray, w: Int, h: Int, tx: Float, ty: Float, ux: Float, uy: Float, width: Float,
             inside: ((Int, Int) -> Boolean)? = null, minDe: Float = MIN_DE): Nail? {
        if (width < 4f) return null
        val r = max(1.5f, 0.2f * width)
        // color de la piel de este dedo, más atrás (falange media o base del dedo)
        val skin = disc(rgb, w, h, tx - ux * 1.2f * width, ty - uy * 1.2f * width, 0f, r * 1.2f, inside, null)?.first ?: return null
        val isSkin = { l: FloatArray -> deltaE(l, skin) < SKIN_DE }
        var best: Nail? = null
        for (d in floatArrayOf(0.25f, 0.35f, 0.45f)) {
            val cx = tx - ux * d * width; val cy = ty - uy * d * width
            val c = disc(rgb, w, h, cx, cy, 0f, r, null, null) ?: continue
            // piel que rodea la uña en el mismo dedo (pliegues lateral y proximal): anillo alrededor de la placa,
            // solo con los píxeles de piel (no el fondo junto a la punta). En la yema (palma, planta) el anillo
            // es de la misma piel y no hay contraste
            val ring = disc(rgb, w, h, cx, cy, r * 1.3f, r * 2f, inside, isSkin, minFrac = 0.35f) ?: continue
            val de = deltaE(c.first, ring.first)
            // placa lisa: su dispersión de color no puede ser mucho mayor que la diferencia con la piel
            if (c.second > de * 0.9f + 4f) continue
            if (best == null || de > best.contrast) best = Nail(cx, cy, r, de)
        }
        return best?.takeIf { it.contrast >= minDe }
    }

    /** Diferencia de color máxima (ΔE) con la piel del dedo para contar un píxel como piel. */
    const val SKIN_DE = 14f

    /**
     * Media (L*, a*, b*) y dispersión (ΔE medio a la media) de un disco o anillo (radios [r0]–[r1]), con los
     * píxeles dentro de [inside] y que cumplen [keep]; null si se sale de la imagen o quedan menos de [minFrac].
     */
    private fun disc(rgb: IntArray, w: Int, h: Int, cx: Float, cy: Float, r0: Float, r1: Float, inside: ((Int, Int) -> Boolean)?,
                     keep: ((FloatArray) -> Boolean)?, minFrac: Float = 0.5f): Pair<FloatArray, Float>? {
        val labs = ArrayList<FloatArray>()
        val ri = r1.toInt().coerceAtLeast(1)
        var total = 0
        for (dy in -ri..ri) for (dx in -ri..ri) {
            val d2 = (dx * dx + dy * dy).toFloat()
            if (d2 > r1 * r1 || d2 < r0 * r0) continue
            total++
            val x = (cx + dx).toInt(); val y = (cy + dy).toInt()
            if (x !in 0 until w || y !in 0 until h) return null
            if (inside != null && !inside(x, y)) continue
            val l = lab(rgb[y * w + x])
            if (keep != null && !keep(l)) continue
            labs += l
        }
        if (labs.size < max(3, (total * minFrac).toInt())) return null
        val m = FloatArray(3) { k -> labs.map { it[k] }.average().toFloat() }
        val spread = labs.map { deltaE(it, m) }.average().toFloat()
        return m to spread
    }

    fun deltaE(a: FloatArray, b: FloatArray) = sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2))

    /** sRGB → CIELAB (D65). */
    fun lab(c: Int): FloatArray {
        fun lin(v: Int): Float { val s = v / 255f; return if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f) }
        val r = lin((c shr 16) and 0xFF); val g = lin((c shr 8) and 0xFF); val b = lin(c and 0xFF)
        val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f
        fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
        return floatArrayOf(116f * f(y) - 16f, 500f * (f(x) - f(y)), 200f * (f(y) - f(z)))
    }

    /**
     * Uñas de una mano (21 puntos en unidades del ancho de una imagen de [w] px): una por dedo (pulgar a
     * meñique), null donde no se ve uña.
     */
    fun ofHand(rgb: IntArray, w: Int, h: Int, pts: List<Pt>, minDe: Float = MIN_DE): List<Nail?> {
        if (pts.size < 21) return List(5) { null }
        fun px(p: Pt) = (p.x * w) to (p.y * w)
        return (0 until 5).map { f ->
            val tip = 4 + 4 * f; val dip = tip - 1
            val (tx, ty) = px(pts[tip]); val (dx, dy) = px(pts[dip])
            val len = hypot(tx - dx, ty - dy).coerceAtLeast(1e-3f)
            // ancho del dedo ≈ largo de su falange distal (el pulgar, algo más)
            find(rgb, w, h, tx, ty, (tx - dx) / len, (ty - dy) / len, len * (if (f == 0) 1.1f else 0.95f), minDe = minDe)
        }
    }
}
