package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Inclinación (roll) de la cabeza medida en la imagen: ángulo de la línea entre los centros de los
 * contornos de los ojos (más estable que un punto aislado y que el ángulo de Euler del modo rápido
 * de ML Kit); si no hay contornos, entre pupilas; en el cuerpo, entre hombros.
 * Grados, positivo = horario en pantalla (el lado derecho de la imagen más bajo).
 */
object HeadRoll {
    fun of(points: Map<String, Pt>, maxDeg: Float = 25f): Pair<Float, Pt>? {
        fun centroid(prefix: String): Pt? = points.filterKeys { it.startsWith(prefix) }.values
            .takeIf { it.size >= 6 }?.let { v -> Pt(v.map { it.x }.average().toFloat(), v.map { it.y }.average().toFloat()) }
        val pair = listOf(
            centroid("eye_r_") to centroid("eye_l_"),
            points[Landmarks.EYE_R] to points[Landmarks.EYE_L],
            points[Landmarks.SHOULDER_R] to points[Landmarks.SHOULDER_L],
        ).firstOrNull { it.first != null && it.second != null } ?: return null
        val a = pair.first!!; val b = pair.second!!
        val (l, r) = if (a.x <= b.x) a to b else b to a
        if (r.x - l.x < 1e-3f) return null
        val deg = Math.toDegrees(atan2((r.y - l.y).toDouble(), (r.x - l.x).toDouble())).toFloat()
        if (abs(deg) > maxDeg) return null
        return deg to Pt((l.x + r.x) / 2, (l.y + r.y) / 2)
    }
}
