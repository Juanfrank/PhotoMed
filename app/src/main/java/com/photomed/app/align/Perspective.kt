package com.photomed.app.align

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Corrección de perspectiva vertical (keystone).
 *
 * Si la cámara estaba inclinada un ángulo φ respecto de la horizontal (φ > 0: apuntando hacia
 * arriba), las verticales del paciente convergen. La foto se "re-proyecta" como si la cámara
 * hubiera estado nivelada: rayo d = ((x−cx)/f, (y−cy)/f, 1) → R_x(φ)·d → proyección. Es una
 * homografía H = K·R·K⁻¹ (coordenadas de imagen con y hacia abajo).
 *
 * Después se recorta el mayor rectángulo 3:4 centrado dentro del trapecio resultante y se escala
 * al tamaño original, para que la imagen corregida siga llenando el encuadre.
 */
object Perspective {

    /** Focal típica de la cámara principal en unidades de alto de imagen (≈ 26 mm eq. sobre 36 mm). */
    const val DEFAULT_FOCAL = 0.75f

    data class P(val x: Double, val y: Double)

    /** Aplica la rotación virtual a un punto en píxeles. */
    fun level(x: Double, y: Double, w: Double, h: Double, pitchDeg: Double, focalNorm: Double): P {
        val f = focalNorm * h
        val cx = w / 2; val cy = h / 2
        val phi = Math.toRadians(pitchDeg)
        val dx = (x - cx) / f; val dy = (y - cy) / f; val dz = 1.0
        val ry = cos(phi) * dy - sin(phi) * dz
        val rz = sin(phi) * dy + cos(phi) * dz
        return P(cx + f * dx / rz, cy + f * ry / rz)
    }

    /**
     * Esquinas destino (TL, TR, BR, BL) en píxeles de la imagen de salida para las esquinas de la
     * imagen de entrada: corrección + recorte 3:4 + escalado. Con 4 pares de puntos la homografía
     * queda determinada (Matrix.setPolyToPoly).
     */
    fun cornerTargets(w: Double, h: Double, pitchDeg: Double, focalNorm: Double): List<P> {
        val src = listOf(P(0.0, 0.0), P(w, 0.0), P(w, h), P(0.0, h))
        if (pitchDeg == 0.0) return src
        val q = src.map { level(it.x, it.y, w, h, pitchDeg, focalNorm) }
        var left = max(q[0].x, q[3].x); var right = min(q[1].x, q[2].x)
        var top = max(q[0].y, q[1].y); var bottom = min(q[2].y, q[3].y)
        // ajustar a la proporción original, centrado
        val aspect = w / h
        val rw = right - left; val rh = bottom - top
        if (rw / rh > aspect) {
            val nw = rh * aspect; val c = (left + right) / 2; left = c - nw / 2; right = c + nw / 2
        } else {
            val nh = rw / aspect; val c = (top + bottom) / 2; top = c - nh / 2; bottom = c + nh / 2
        }
        val sx = w / (right - left); val sy = h / (bottom - top)
        return q.map { P((it.x - left) * sx, (it.y - top) * sy) }
    }

    /** Ángulo de la cámara respecto de la horizontal a partir del cabeceo del teléfono (sensor de gravedad). */
    fun cameraPitchFromDevice(devicePitchDeg: Float, frontCamera: Boolean): Float =
        if (frontCamera) devicePitchDeg else -devicePitchDeg
}
