package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pose de la cabeza (guiñada, cabeceo y alabeo) por **ajuste geométrico** de los puntos detectados
 * al modelo 3D canónico del rostro de MediaPipe ([HeadModel]): proyección de perspectiva débil
 * (escala + traslación en la imagen) y rotación R = Rz(alabeo)·Ry(guiñada)·Rx(cabeceo), por
 * mínimos cuadrados con búsqueda por coordenadas.
 *
 * Funciona igual con ML Kit y MediaPipe (mismas claves de puntos) y no depende del ángulo de
 * Euler del modo rápido de ML Kit, que es ruidoso. Validado con 798 rostros de LFW frente a la
 * pose de la matriz de MediaPipe (`tools/eval/head_model.py`): correlación 0,96 en cabeceo
 * (error medio ≈ 2°, tras la calibración) y 0,996 en guiñada.
 *
 * Convención de salida (la de ML Kit): cabeceo **positivo = barbilla arriba** (mira hacia arriba).
 */
object HeadPose {
    data class Fit(
        val yaw: Float, val pitch: Float, val roll: Float,
        /** Escala (unidades del ancho de la imagen por cm) y traslación del modelo en la imagen. */
        val scale: Float, val tx: Float, val ty: Float,
        /** Error cuadrático medio relativo (en cm del modelo). */
        val rms: Float,
        private val r: FloatArray,
    ) {
        /** Proyección de un punto del modelo (cm) a la imagen (x del ancho, y en unidades del ancho). */
        fun project(p: FloatArray): Pt {
            val x = r[0] * p[0] + r[1] * p[1] + r[2] * p[2]
            val y = r[3] * p[0] + r[4] * p[1] + r[5] * p[2]
            return Pt(tx + scale * x, ty - scale * y)
        }
    }

    const val MIN_POINTS = 12

    // Calibración lineal geométrico → pose de MediaPipe (LFW): g = 0,85·m + 2,44 (cabeceo), g = 1,16·m (guiñada).
    private const val PITCH_GAIN = 0.85f
    private const val PITCH_OFFSET = 2.44f
    private const val YAW_GAIN = 1.16f

    private fun rot(yawDeg: Float, pitchDeg: Float, rollDeg: Float): FloatArray {
        val y = Math.toRadians(yawDeg.toDouble()); val p = Math.toRadians(pitchDeg.toDouble()); val r = Math.toRadians(rollDeg.toDouble())
        val cy = cos(y); val sy = sin(y); val cp = cos(p); val sp = sin(p); val cr = cos(r); val sr = sin(r)
        // Rz·Ry·Rx
        return floatArrayOf(
            (cr * cy).toFloat(), (cr * sy * sp - sr * cp).toFloat(), (cr * sy * cp + sr * sp).toFloat(),
            (sr * cy).toFloat(), (sr * sy * sp + cr * cp).toFloat(), (sr * sy * cp - cr * sp).toFloat(),
            (-sy).toFloat(), (cy * sp).toFloat(), (cy * cp).toFloat(),
        )
    }

    private class Solve(val cost: Float, val s: Float, val tx: Float, val ty: Float)

    private fun solve(r: FloatArray, X: Array<FloatArray>, U: Array<Pt>): Solve {
        val n = X.size
        val xs = FloatArray(n); val ys = FloatArray(n)
        var mx = 0f; var my = 0f; var mu = 0f; var mv = 0f
        for (i in 0 until n) {
            val p = X[i]
            xs[i] = r[0] * p[0] + r[1] * p[1] + r[2] * p[2]
            ys[i] = -(r[3] * p[0] + r[4] * p[1] + r[5] * p[2])
            mx += xs[i]; my += ys[i]; mu += U[i].x; mv += U[i].y
        }
        mx /= n; my /= n; mu /= n; mv /= n
        var num = 0f; var den = 0f
        for (i in 0 until n) {
            val xc = xs[i] - mx; val yc = ys[i] - my
            num += (U[i].x - mu) * xc + (U[i].y - mv) * yc; den += xc * xc + yc * yc
        }
        if (den <= 0f || num <= 0f) return Solve(Float.MAX_VALUE, 0f, 0f, 0f)
        val s = num / den
        var c = 0f
        for (i in 0 until n) {
            val ex = s * (xs[i] - mx) - (U[i].x - mu); val ey = s * (ys[i] - my) - (U[i].y - mv)
            c += ex * ex + ey * ey
        }
        return Solve(c / n / (s * s), s, mu - s * mx, mv - s * my)
    }

    /** Ajuste crudo (ángulos geométricos sin calibrar, convención del modelo: cabeceo + = mira abajo). */
    private fun fitRaw(points: Map<String, Pt>): Triple<FloatArray, Solve, FloatArray>? {
        val keys = points.keys.filter { it in HeadModel.POINTS }
        if (keys.size < MIN_POINTS) return null
        val X = Array(keys.size) { HeadModel.POINTS.getValue(keys[it]) }
        val U = Array(keys.size) { points.getValue(keys[it]) }
        val rollInit = HeadRoll.of(points, 45f)?.first ?: 0f
        var best: FloatArray? = null; var bestC = Float.MAX_VALUE
        for (y0 in floatArrayOf(-35f, 0f, 35f)) {
            val a = floatArrayOf(y0, 0f, rollInit)
            var c = solve(rot(a[0], a[1], a[2]), X, U).cost
            var step = 8f
            while (step > 0.05f) {
                var improved = false
                for (k in 0 until 3) for (d in floatArrayOf(step, -step)) {
                    val b = a.copyOf(); b[k] += d
                    if (abs(b[0]) > 80f || abs(b[1]) > 60f) continue
                    val cb = solve(rot(b[0], b[1], b[2]), X, U).cost
                    if (cb < c) { c = cb; b.copyInto(a); improved = true }
                }
                if (!improved) step /= 2f
            }
            if (c < bestC) { bestC = c; best = a }
        }
        val a = best ?: return null
        val r = rot(a[0], a[1], a[2])
        return Triple(a, solve(r, X, U), r)
    }

    /** Pose calibrada (convención ML Kit: cabeceo + = barbilla arriba); null si hay pocos puntos. */
    fun fit(points: Map<String, Pt>): Fit? {
        val (a, s, r) = fitRaw(points) ?: return null
        if (s.s <= 0f) return null
        val pitchMp = (a[1] - PITCH_OFFSET) / PITCH_GAIN
        return Fit(yaw = a[0] / YAW_GAIN, pitch = -pitchMp, roll = a[2], scale = s.s, tx = s.tx, ty = s.ty,
            rms = sqrt(s.cost), r = r)
    }

    /** Proyección del vértice del cráneo según la pose (para adaptar la guía al cabeceo). */
    fun vertex(fit: Fit): Pt = fit.project(HeadModel.VERTEX)
}
