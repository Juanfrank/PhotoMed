package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Coincidencia por puntos de contorno (zonas o fotos sin puntos anatómicos):
 * - Puntos de referencia: los bordes más fuertes dentro de las regiones anatómicas, con su
 *   orientación.
 * - Un punto coincide si en el cuadro actual hay un borde fuerte a ≤ tolerancia **con la misma
 *   orientación** (±20°).
 * - Corrección por azar: en superficies muy texturizadas (piso, césped, grava) casi cualquier punto
 *   "encuentra" un borde. Se mide la tasa de aciertos con los mismos puntos desplazados lejos de su
 *   sitio (azar) y se corrige: (f − azar)/(1 − azar).
 */
object ContourMatch {
    data class EdgePt(val x: Int, val y: Int, val angle: Float)

    private class Grad(val w: Int, val h: Int, val mag: FloatArray, val ang: FloatArray)

    private fun grad(g: GrayImage): Grad {
        val s = g.boxBlur(1); val w = g.width; val h = g.height
        val mag = FloatArray(w * h); val ang = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val gx = -s[x - 1, y - 1] - 2 * s[x - 1, y] - s[x - 1, y + 1] + s[x + 1, y - 1] + 2 * s[x + 1, y] + s[x + 1, y + 1]
            val gy = -s[x - 1, y - 1] - 2 * s[x, y - 1] - s[x + 1, y - 1] + s[x - 1, y + 1] + 2 * s[x, y + 1] + s[x + 1, y + 1]
            mag[y * w + x] = sqrt(gx * gx + gy * gy); ang[y * w + x] = atan2(gy, gx)
        }
        return Grad(w, h, mag, ang)
    }

    fun referencePoints(g: GrayImage, regions: List<ContourRegion>, max: Int = 300): List<EdgePt> {
        val gr = grad(g); val w = g.width; val h = g.height
        val cand = ArrayList<Triple<Int, Float, Float>>()
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            var rw = 0f
            for (r in regions) {
                val u = (x.toFloat() / w - r.center.x) / r.radius.x; val v = (y.toFloat() / w - r.center.y) / r.radius.y
                rw += r.weight * exp(-0.5f * (u * u + v * v))
            }
            if (rw > 0.3f) cand += Triple(y * w + x, gr.mag[y * w + x] * rw, gr.ang[y * w + x])
        }
        cand.sortByDescending { it.second }
        val used = HashSet<Int>(); val out = ArrayList<EdgePt>()
        for ((i, _, a) in cand) {
            val x = i % w; val y = i / w
            if (used.add((y / 3) * 1000 + x / 3)) out += EdgePt(x, y, a)
            if (out.size >= max) break
        }
        return out
    }

    /** Fracción corregida por azar (0..1) de puntos de referencia con borde coincidente. */
    fun fraction(ref: List<EdgePt>, cur: GrayImage, tol: Float = LiveMatch.POINT_TOLERANCE): Float {
        if (ref.isEmpty()) return 0f
        val gr = grad(cur)
        val sorted = gr.mag.sortedArray()
        val thr = sorted[(sorted.size * 0.85f).toInt()].coerceAtLeast(1e-3f)
        val r = (tol * cur.width).roundToInt().coerceAtLeast(1)
        fun hits(dx: Int, dy: Int): Float = ref.count { p ->
            val x0 = p.x + dx; val y0 = p.y + dy
            var hit = false
            loop@ for (yy in y0 - r..y0 + r) for (xx in x0 - r..x0 + r) {
                if (xx !in 0 until cur.width || yy !in 0 until cur.height) continue
                val i = yy * cur.width + xx
                if (gr.mag[i] >= thr && sameAxis(gr.ang[i], p.angle)) { hit = true; break@loop }
            }
            hit
        } / ref.size.toFloat()
        val f = hits(0, 0)
        // azar: mismos puntos desplazados ~30 % del ancho/alto (con vuelta)
        val chance = listOf(cur.width * 3 / 10 to cur.height / 4, -cur.width / 4 to cur.height * 3 / 10).map { (dx, dy) ->
            ref.count { p ->
                val x0 = ((p.x + dx) % cur.width + cur.width) % cur.width; val y0 = ((p.y + dy) % cur.height + cur.height) % cur.height
                var hit = false
                loop@ for (yy in y0 - r..y0 + r) for (xx in x0 - r..x0 + r) {
                    if (xx !in 0 until cur.width || yy !in 0 until cur.height) continue
                    val i = yy * cur.width + xx
                    if (gr.mag[i] >= thr && sameAxis(gr.ang[i], p.angle)) { hit = true; break@loop }
                }
                hit
            } / ref.size.toFloat()
        }.average().toFloat()
        return if (chance >= 0.999f) 0f else ((f - chance) / (1f - chance)).coerceIn(0f, 1f)
    }

    /** Misma orientación de borde (±20°), sin distinguir polaridad. */
    private fun sameAxis(a: Float, b: Float): Boolean {
        var d = abs(a - b) % Math.PI.toFloat()
        if (d > Math.PI.toFloat() / 2) d = Math.PI.toFloat() - d
        return d <= TOL_ANGLE
    }

    private const val TOL_ANGLE = 0.35f // ≈ 20°
}
