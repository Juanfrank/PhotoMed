package com.photomed.app.align

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** Región anatómica cuyos contornos guían la alineación (elipse gaussiana en coordenadas normalizadas). */
data class ContourRegion(val name: String, val center: Pt, val radius: Pt, val weight: Float = 1f)

/**
 * Coincidencia contornal: busca la semejanza T (después → antes) que maximiza la correlación de
 * orientación de contornos entre ambas fotos, ponderada por las regiones anatómicas de la zona
 * (p. ej. mandíbula, frente y orejas en el rostro; caderas en el tronco; falanges en las manos).
 *
 * 1. Pirámide de 3 niveles (p. ej. 192×256 → 96×128 → 48×64).
 * 2. Campo de orientación en ángulo doble (invariante a la polaridad del borde).
 * 3. Puntuación: Σ w·⟨b, R(2θ)·a(T⁻¹p)⟩ / √(Σ w|b|² · Σ w|a|²), con penalización por cobertura.
 * 4. Sin estimación inicial: búsqueda exhaustiva gruesa de desplazamiento × escala × rotación.
 * 5. Descenso por coordenadas sobre (tx, ty, θ, ln s) de grueso a fino, escalando alrededor del
 *    pivote anatómico (centroide de las regiones).
 */
class EdgeAligner(
    private val regions: List<ContourRegion> = listOf(ContourRegion("zona", Pt(0.5f, 0.66f), Pt(0.32f, 0.42f))),
    private val baseWeight: Float = 0.15f,
) {
    constructor(roiCenter: Pt, roiRadius: Pt) : this(listOf(ContourRegion("zona", roiCenter, roiRadius)))

    data class Window(val translation: Float, val rotationRad: Float, val logScale: Float) {
        companion object {
            val WIDE = Window(0.18f, Math.toRadians(12.0).toFloat(), 0.25f)
            val NARROW = Window(0.06f, Math.toRadians(5.0).toFloat(), 0.08f)
            val LIVE = Window(0.25f, Math.toRadians(15.0).toFloat(), 0.3f)
        }
    }

    data class Result(val transform: Similarity, val score: Float, val regionScores: List<Pair<String, Float>>)

    private val pivot: Pt = run {
        val ws = regions.sumOf { it.weight.toDouble() }.toFloat()
        Pt(regions.sumOf { (it.center.x * it.weight).toDouble() }.toFloat() / ws,
            regions.sumOf { (it.center.y * it.weight).toDouble() }.toFloat() / ws)
    }

    private class Level(val before: OrientationField, val after: OrientationField, val weights: FloatArray,
                        val regionW: List<FloatArray>)

    fun align(before: GrayImage, after: GrayImage, init: Similarity?, window: Window, levels: Int = 3): Result {
        require(before.width == after.width && before.height == after.height)
        val lv = buildLevels(before, after, levels)
        val start = init ?: Similarity.IDENTITY
        val p = floatArrayOf(start.tx, start.ty, start.rotation, ln(start.scale))
        val radius = floatArrayOf(window.translation, window.translation, window.rotationRad, window.logScale)
        val lo = FloatArray(4) { p[it] - radius[it] }
        val hi = FloatArray(4) { p[it] + radius[it] }

        val coarse = lv.last()
        if (init == null) {
            var best = score(coarse, p)
            val bestP = p.copyOf()
            val stepT = 1.5f / coarse.before.width
            val maxRot = Math.toDegrees(window.rotationRad.toDouble()).toInt()
            val c = pivot
            for (deg in (-maxRot..maxRot step 4)) {
                val th = Math.toRadians(deg.toDouble()).toFloat()
                val cs = cos(th); val sn = sin(th)
                for (ls in floatArrayOf(-0.16f, -0.08f, 0f, 0.08f, 0.16f)) {
                    val sc = exp(ls)
                    val bx = c.x - sc * (cs * c.x - sn * c.y)
                    val by = c.y - sc * (sn * c.x + cs * c.y)
                    var dy = -window.translation
                    while (dy <= window.translation) {
                        var dx = -window.translation
                        while (dx <= window.translation) {
                            val q = floatArrayOf(bx + dx, by + dy, th, ls)
                            val v = score(coarse, q)
                            if (v > best) { best = v; q.copyInto(bestP) }
                            dx += stepT
                        }
                        dy += stepT
                    }
                }
            }
            bestP.copyInto(p)
            for (k in 0 until 4) { lo[k] = p[k] - radius[k]; hi[k] = p[k] + radius[k] }
        }

        var steps = floatArrayOf(0.02f, 0.02f, Math.toRadians(2.0).toFloat(), 0.03f)
        for (li in lv.indices.reversed()) {
            val level = lv[li]
            var best = score(level, p)
            var iter = 0
            val minT = 0.5f / level.before.width
            while (iter++ < 80 && steps[0] >= minT) {
                var improved = false
                for (k in 0 until 4) {
                    for (dir in floatArrayOf(1f, -1f)) {
                        val q = p.copyOf()
                        q[k] = (q[k] + dir * steps[k]).coerceIn(lo[k], hi[k])
                        if (k == 3) {
                            // escalar alrededor del pivote: t' = t + (s − s')·R·c
                            val ds = exp(p[3]) - exp(q[3])
                            val rcx = cos(p[2]) * pivot.x - sin(p[2]) * pivot.y
                            val rcy = sin(p[2]) * pivot.x + cos(p[2]) * pivot.y
                            q[0] = (q[0] + ds * rcx).coerceIn(lo[0], hi[0])
                            q[1] = (q[1] + ds * rcy).coerceIn(lo[1], hi[1])
                        }
                        val sc = score(level, q)
                        if (sc > best + 1e-5f) { best = sc; q.copyInto(p); improved = true }
                    }
                }
                if (!improved) steps = FloatArray(4) { steps[it] * 0.5f }
            }
            steps = FloatArray(4) { steps[it] * 2f }
        }
        val fine = lv.first()
        val regionScores = regions.mapIndexed { i, r -> r.name to score(fine, p, fine.regionW[i], penalize = false) }
        return Result(Similarity(exp(p[3]), p[2], p[0], p[1]), score(fine, p), regionScores)
    }

    private fun buildLevels(before: GrayImage, after: GrayImage, maxLevels: Int): List<Level> {
        val out = mutableListOf<Level>()
        var b = before; var a = after
        while (true) {
            val blur = if (b.width >= 128) 2 else 1
            val rw = regions.map { regionWeights(it, b.width, b.height) }
            val w = FloatArray(b.width * b.height)
            val margin = (b.width * 0.04f).toInt().coerceAtLeast(2)
            for (y in margin until b.height - margin) for (x in margin until b.width - margin) {
                var s = baseWeight
                for (r in rw) s += r[y * b.width + x]
                w[y * b.width + x] = s
            }
            out += Level(OrientationField.of(b, blur), OrientationField.of(a, blur), w, rw)
            if (b.width / 2 < 32 || out.size == maxLevels) break
            b = b.half(); a = a.half()
        }
        return out
    }

    private fun regionWeights(r: ContourRegion, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val u = (x.toFloat() / w - r.center.x) / r.radius.x
            val v = (y.toFloat() / w - r.center.y) / r.radius.y
            out[y * w + x] = r.weight * exp(-0.5f * (u * u + v * v))
        }
        return out
    }

    private fun score(level: Level, p: FloatArray, weights: FloatArray = level.weights, penalize: Boolean = true): Float {
        val t = Similarity(exp(p[3]), p[2], p[0], p[1]).inverse() // antes → después
        val b = level.before; val a = level.after
        val w = b.width
        val c = cos(t.rotation) * t.scale; val s = sin(t.rotation) * t.scale
        val c2 = cos(2 * p[2]); val s2 = sin(2 * p[2]) // el campo de "después" rota 2θ al llevarlo a "antes"
        val tmp = FloatArray(2)
        var sbr = 0.0; var sbb = 0.0; var srr = 0.0
        var covered = 0.0; var total = 0.0
        for (y in 0 until b.height) {
            val v = y.toFloat() / w
            for (x in 0 until w) {
                val i = y * w + x
                val wt = weights[i]
                if (wt <= 1e-4f) continue
                total += wt
                val u = x.toFloat() / w
                if (!a.sample((c * u - s * v + t.tx) * w, (s * u + c * v + t.ty) * w, tmp)) continue
                covered += wt
                val r1 = tmp[0] * c2 - tmp[1] * s2
                val r2 = tmp[0] * s2 + tmp[1] * c2
                val b1 = b.cx[i]; val b2 = b.cy[i]
                sbr += wt * (b1 * r1 + b2 * r2); sbb += wt * (b1 * b1 + b2 * b2); srr += wt * (r1 * r1 + r2 * r2)
            }
        }
        if (total <= 0.0 || covered / total < 0.6 || sbb <= 1e-12 || srr <= 1e-12) return -1f
        val corr = sbr / sqrt(sbb * srr)
        return (if (penalize) corr - 0.2 * (1 - covered / total) else corr).toFloat()
    }

    companion object {
        fun isConfident(score: Float) = score >= 0.35f
    }
}
