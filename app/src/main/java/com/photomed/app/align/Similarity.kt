package com.photomed.app.align

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Punto en coordenadas normalizadas: x ∈ [0,1], y ∈ [0, alto/ancho] (unidad = ancho de la imagen). */
data class Pt(val x: Float, val y: Float) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
}

/**
 * Transformación de semejanza (escala uniforme, rotación, traslación):
 *   p' = s·R(θ)·p + t
 * Con la convención de pantalla (y hacia abajo), θ > 0 gira en sentido horario,
 * igual que `graphicsLayer.rotationZ`.
 */
data class Similarity(val scale: Float = 1f, val rotation: Float = 0f, val tx: Float = 0f, val ty: Float = 0f) {

    val rotationDegrees: Float get() = Math.toDegrees(rotation.toDouble()).toFloat()

    fun apply(p: Pt): Pt {
        val c = cos(rotation) * scale
        val s = sin(rotation) * scale
        return Pt(c * p.x - s * p.y + tx, s * p.x + c * p.y + ty)
    }

    fun inverse(): Similarity {
        val inv = 1f / scale
        val c = cos(-rotation) * inv
        val s = sin(-rotation) * inv
        return Similarity(inv, -rotation, -(c * tx - s * ty), -(s * tx + c * ty))
    }

    /** this ∘ other: primero [other], después this. */
    fun compose(other: Similarity): Similarity {
        val t = apply(Pt(other.tx, other.ty))
        return Similarity(scale * other.scale, rotation + other.rotation, t.x, t.y)
    }

    companion object {
        val IDENTITY = Similarity()

        /**
         * Estimación por mínimos cuadrados (Umeyama, 1991) de la semejanza que lleva [src] sobre [dst].
         * Requiere ≥ 2 pares de puntos no coincidentes. Con 2 pares la solución es exacta.
         */
        fun estimate(src: List<Pt>, dst: List<Pt>, weights: List<Float>? = null): Similarity? {
            require(src.size == dst.size)
            if (src.size < 2) return null
            val w = weights ?: List(src.size) { 1f }
            val wSum = w.sum().takeIf { it > 0f } ?: return null
            var msx = 0f; var msy = 0f; var mdx = 0f; var mdy = 0f
            for (i in src.indices) {
                msx += w[i] * src[i].x; msy += w[i] * src[i].y
                mdx += w[i] * dst[i].x; mdy += w[i] * dst[i].y
            }
            msx /= wSum; msy /= wSum; mdx /= wSum; mdy /= wSum
            var a = 0.0; var b = 0.0; var varS = 0.0
            for (i in src.indices) {
                val sx = src[i].x - msx; val sy = src[i].y - msy
                val dx = dst[i].x - mdx; val dy = dst[i].y - mdy
                a += w[i] * (sx * dx + sy * dy)
                b += w[i] * (sx * dy - sy * dx)
                varS += w[i] * (sx * sx + sy * sy)
            }
            if (varS < 1e-9) return null
            val rot = atan2(b, a).toFloat()
            val scale = (sqrt(a * a + b * b) / varS).toFloat()
            val c = cos(rot) * scale
            val s = sin(rot) * scale
            return Similarity(scale, rot, mdx - (c * msx - s * msy), mdy - (s * msx + c * msy))
        }

        /** Error cuadrático medio (en unidades normalizadas) de [t] sobre los pares. */
        fun rms(t: Similarity, src: List<Pt>, dst: List<Pt>): Float {
            if (src.isEmpty()) return 0f
            var e = 0f
            for (i in src.indices) {
                val d = t.apply(src[i]) - dst[i]
                e += d.x * d.x + d.y * d.y
            }
            return sqrt(e / src.size)
        }
    }
}
