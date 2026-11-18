package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Silueta del sujeto por filas (de la segmentación de la foto de referencia): para cada fila, el
 * tramo de la máscara que contiene el eje del cuerpo/cabeza (se toleran huecos pequeños).
 * Coordenadas normalizadas: x ∈ [0,1] del ancho, y ∈ [0,1] del alto del encuadre.
 *
 * La guía se ajusta fila a fila a estos bordes (cuerpo: hombros, cintura, caderas, muslos…;
 * perfil: nuca y borde facial) y en el perfil da anclas verticales (punta nasal y pogonion) con el
 * mismo algoritmo que se usa con el dibujo (`tools/guides/build_guides.py`).
 */
class Silhouette(val left: FloatArray, val right: FloatArray) {
    val rows: Int get() = left.size

    private fun rowOf(y: Float) = (y * rows - 0.5f).roundToInt().coerceIn(0, rows - 1)
    fun rowY(i: Int) = (i + 0.5f) / rows

    /** Bordes a la altura [y] (null si no hay sujeto en esa fila). */
    fun span(y: Float): Pair<Float, Float>? {
        if (y < 0f || y > 1f) return null
        val i = rowOf(y)
        return if (left[i].isNaN()) null else left[i] to right[i]
    }

    /** Fila superior con sujeto (null si la máscara toca el borde superior: el vértice queda fuera). */
    val top: Float? get() {
        val i = left.indexOfFirst { !it.isNaN() }
        return if (i <= 0) null else rowY(i)
    }

    /** Fila inferior con sujeto (null si la máscara toca el borde inferior). */
    val bottom: Float? get() {
        val i = left.indexOfLast { !it.isNaN() }
        return if (i < 0 || i >= rows - 1) null else rowY(i)
    }

    /**
     * Perfil: punta nasal (máxima protrusión del borde anterior en el tramo 30–75 % de las filas con
     * sujeto) y pogonion (máxima protrusión tras el surco mentolabial, antes de que el borde
     * retroceda al cuello). [frontRight]: la cara mira a la derecha de la imagen.
     */
    fun profileAnchors(frontRight: Boolean): Map<String, Float> {
        val idx = (0 until rows).filter { !left[it].isNaN() }
        if (idx.size < 12) return emptyMap()
        val fx = idx.map { if (frontRight) right[it] else -left[it] }
        val wd = idx.map { right[it] - left[it] }
        val n = idx.size
        val lo = (n * 0.30f).toInt(); val hi = (n * 0.75f).toInt()
        val iNose = (lo until hi).maxBy { fx[it] }
        val out = mutableMapOf("punta nasal" to rowY(idx[iNose]))
        val neck = (iNose + 2 until n).firstOrNull { fx[it - 1] - fx[it] > 0.12f * wd[iNose] } ?: n
        val a = iNose + maxOf(1, ((neck - iNose) * 0.2f).toInt())
        if (neck - a >= 3) {
            val iMin = (a until neck).minBy { fx[it] }
            val iPog = (iMin until neck).maxBy { fx[it] }
            out["pogonion"] = rowY(idx[iPog])
        }
        return out
    }

    companion object {
        const val ROWS = 96

        /**
         * Desde una máscara (resolución cualquiera, encuadre 3:4). [centerX]: eje del sujeto (x
         * normalizada); si es null, el centroide de la máscara en cada fila más cercano al centro.
         */
        fun fromMask(mask: Mask, centerX: Float? = null, rows: Int = ROWS): Silhouette? {
            val l = FloatArray(rows) { Float.NaN }; val r = FloatArray(rows) { Float.NaN }
            val w = mask.width; val h = mask.height
            val gapMax = (w * 0.05f).toInt().coerceAtLeast(2)
            for (i in 0 until rows) {
                val y = ((i + 0.5f) / rows * h).toInt().coerceIn(0, h - 1)
                var c = ((centerX ?: 0.5f) * w).toInt().coerceIn(0, w - 1)
                if (!mask[c, y]) {
                    // el píxel más cercano al eje con sujeto (hasta un 15 % del ancho)
                    val near = (1..(w * 0.15f).toInt()).firstNotNullOfOrNull { d ->
                        when { mask[c - d, y] -> c - d; mask[c + d, y] -> c + d; else -> null }
                    } ?: continue
                    c = near
                }
                fun walk(step: Int): Int {
                    var x = c; var gap = 0
                    while (x + step in 0 until w && gap < gapMax) { x += step; gap = if (mask[x, y]) 0 else gap + 1 }
                    return x - step * gap
                }
                val a = walk(-1); val b = walk(1)
                if (b - a < 3) continue
                // filas con huecos dentro del tramo (dedos separados): no miden el ancho del sujeto
                var holes = 0
                for (x in a..b) if (!mask[x, y]) holes++
                if (holes > (b - a) * 0.15f) continue
                l[i] = a / w.toFloat(); r[i] = (b + 1) / w.toFloat()
            }
            return if (l.count { !it.isNaN() } < 8) null else Silhouette(l, r)
        }

        /** Eje del sujeto a partir de los puntos (pose u ojos); null si no hay. */
        fun axisOf(points: Map<String, Pt>): Float? {
            val pairs = listOf(Landmarks.SHOULDER_L to Landmarks.SHOULDER_R, Landmarks.HIP_L to Landmarks.HIP_R, Landmarks.EYE_L to Landmarks.EYE_R)
            val xs = pairs.mapNotNull { (a, b) -> val pa = points[a]; val pb = points[b]; if (pa != null && pb != null) (pa.x + pb.x) / 2 else null }
            return xs.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        }
    }
}
