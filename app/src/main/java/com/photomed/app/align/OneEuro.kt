package com.photomed.app.align

import kotlin.math.PI
import kotlin.math.abs

/**
 * Filtro "One-Euro" (Casiez, Roussel y Vogel, CHI 2012): paso bajo con frecuencia de corte que
 * crece con la velocidad. En reposo quita el temblor de los puntos (corte bajo); al moverse la
 * cabeza o el teléfono sigue el movimiento sin retraso apreciable (corte alto).
 */
class OneEuro(private val minCutoff: Double, private val beta: Double, private val dCutoff: Double = 1.0) {
    private var x: Double? = null
    private var dx = 0.0
    private var t: Double? = null

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    /** [value] en el instante [tSec] (segundos). */
    fun filter(value: Double, tSec: Double): Double {
        val px = x; val pt = t
        if (px == null || pt == null || tSec <= pt) { x = value; t = tSec; dx = 0.0; return value }
        val dt = tSec - pt
        val d = (value - px) / dt
        dx += alpha(dCutoff, dt) * (d - dx)
        val cutoff = minCutoff + beta * abs(dx)
        val out = px + alpha(cutoff, dt) * (value - px)
        x = out; t = tSec
        return out
    }

    fun reset() { x = null; t = null; dx = 0.0 }
}

/**
 * Suavizado temporal de una detección en vivo: cada punto (x, y) y los ángulos de la cabeza con
 * su filtro One-Euro. Se reinicia si se pierde la detección o pasa demasiado tiempo entre cuadros.
 */
class LandmarkSmoother(
    /** Puntos en unidades del ancho: el temblor típico es ~0,2 %, un movimiento real ≥ 5 %/s. */
    private val minCutoff: Double = 1.5,
    private val beta: Double = 20.0,
    /** Ángulos (°). */
    private val angleMinCutoff: Double = 1.0,
    private val angleBeta: Double = 0.05,
) {
    private val filters = HashMap<String, Pair<OneEuro, OneEuro>>()
    private val angles = HashMap<String, OneEuro>()
    private var lastT = -1.0

    fun reset() { filters.clear(); angles.clear(); lastT = -1.0 }

    fun smooth(det: Detection, tMillis: Long): Detection {
        val t = tMillis / 1000.0
        if (det.points.isEmpty() || (lastT >= 0 && t - lastT > MAX_GAP_S)) reset()
        if (det.points.isEmpty()) return det
        lastT = t
        filters.keys.retainAll(det.points.keys)
        val pts = det.points.mapValues { (k, p) ->
            val (fx, fy) = filters.getOrPut(k) { OneEuro(minCutoff, beta) to OneEuro(minCutoff, beta) }
            Pt(fx.filter(p.x.toDouble(), t).toFloat(), fy.filter(p.y.toDouble(), t).toFloat())
        }
        fun ang(name: String, v: Float?): Float? =
            v?.let { angles.getOrPut(name) { OneEuro(angleMinCutoff, angleBeta) }.filter(it.toDouble(), t).toFloat() }
        // pose geométrica recalculada con los puntos suavizados (sin el temblor del cabeceo)
        val pose = if (det.pose != null) runCatching { HeadPose.fit(pts) }.getOrNull() ?: det.pose else null
        return det.copy(points = pts, yaw = ang("yaw", det.yaw), pitch = ang("pitch", det.pitch), roll = ang("roll", det.roll), pose = pose)
    }

    companion object { const val MAX_GAP_S = 0.6 }
}
