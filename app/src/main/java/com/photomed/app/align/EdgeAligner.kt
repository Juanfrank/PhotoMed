package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Coincidencia contornal: busca la semejanza T (después → antes) que maximiza la
 * correlación cruzada normalizada (NCC) entre los mapas de bordes de ambas fotos.
 *
 * 1. Pirámide de 3 niveles de cada imagen (p. ej. 192×256 → 96×128 → 48×64).
 * 2. En cada nivel: magnitud de gradiente Sobel + desenfoque → "mapa de contornos".
 * 3. Sin estimación inicial: búsqueda exhaustiva de traslación×escala en el nivel grueso.
 * 4. Descenso por coordenadas sobre (tx, ty, θ, ln s) de grueso a fino, dentro de una
 *    ventana alrededor de la estimación inicial (p. ej. la de los puntos anatómicos).
 * 5. Pesos gaussianos centrados en la zona de interés: el fondo pesa menos que el paciente.
 */
class EdgeAligner(
    /** Centro (normalizado) y radios de la región de interés de la zona. */
    private val roiCenter: Pt = Pt(0.5f, 0.66f),
    private val roiRadius: Pt = Pt(0.32f, 0.42f),
) {
    data class Window(val translation: Float, val rotationRad: Float, val logScale: Float) {
        companion object {
            /** Sin puntos anatómicos: ventana amplia. */
            val WIDE = Window(0.18f, Math.toRadians(12.0).toFloat(), 0.25f)
            /** Afinado alrededor de una estimación anatómica. */
            val NARROW = Window(0.06f, Math.toRadians(5.0).toFloat(), 0.08f)
        }
    }

    data class Result(val transform: Similarity, val score: Float)

    private class Level(val before: GrayImage, val after: GrayImage, val weights: FloatArray)

    fun align(before: GrayImage, after: GrayImage, init: Similarity?, window: Window): Result {
        require(before.width == after.width && before.height == after.height)
        val levels = buildLevels(before, after)
        val start = init ?: Similarity.IDENTITY
        val p = floatArrayOf(start.tx, start.ty, start.rotation, ln(start.scale))
        val lo = floatArrayOf(p[0] - window.translation, p[1] - window.translation, p[2] - window.rotationRad, p[3] - window.logScale)
        val hi = floatArrayOf(p[0] + window.translation, p[1] + window.translation, p[2] + window.rotationRad, p[3] + window.logScale)

        val coarse = levels.last()
        if (init == null) {
            // Búsqueda exhaustiva gruesa de desplazamiento × escala × rotación, con escala y
            // rotación alrededor del centro c de la ROI:  p' = s·R·(p − c) + c + d
            var best = score(coarse, p)
            val bestP = p.copyOf()
            val stepT = 1.5f / coarse.before.width
            val maxRot = Math.toDegrees(window.rotationRad.toDouble()).toInt()
            val c = roiCenter
            for (deg in (-maxRot..maxRot step 4)) {
                val th = Math.toRadians(deg.toDouble()).toFloat()
                val cos = kotlin.math.cos(th)
                val sin = kotlin.math.sin(th)
                for (ls in floatArrayOf(-0.16f, -0.08f, 0f, 0.08f, 0.16f)) {
                    val sc = exp(ls)
                    val baseX = c.x - sc * (cos * c.x - sin * c.y)
                    val baseY = c.y - sc * (sin * c.x + cos * c.y)
                    var dy = -window.translation
                    while (dy <= window.translation) {
                        var dx = -window.translation
                        while (dx <= window.translation) {
                            val q = floatArrayOf(baseX + dx, baseY + dy, th, ls)
                            val v = score(coarse, q)
                            if (v > best) { best = v; q.copyInto(bestP) }
                            dx += stepT
                        }
                        dy += stepT
                    }
                }
            }
            bestP.copyInto(p)
            for (k in 0 until 4) {
                val r = floatArrayOf(window.translation, window.translation, window.rotationRad, window.logScale)[k]
                lo[k] = p[k] - r; hi[k] = p[k] + r
            }
        }

        var steps = floatArrayOf(0.02f, 0.02f, Math.toRadians(2.0).toFloat(), 0.03f)
        for (li in levels.indices.reversed()) {
            val level = levels[li]
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
                            // Escalar alrededor del centro de la ROI: t' = t + (s - s')·R·c
                            val ds = exp(p[3]) - exp(q[3])
                            val rcx = kotlin.math.cos(p[2]) * roiCenter.x - kotlin.math.sin(p[2]) * roiCenter.y
                            val rcy = kotlin.math.sin(p[2]) * roiCenter.x + kotlin.math.cos(p[2]) * roiCenter.y
                            q[0] = (q[0] + ds * rcx).coerceIn(lo[0], hi[0])
                            q[1] = (q[1] + ds * rcy).coerceIn(lo[1], hi[1])
                        }
                        val sc = score(level, q)
                        if (sc > best + 1e-5f) { best = sc; q.copyInto(p); improved = true }
                    }
                }
                if (!improved) steps = FloatArray(4) { steps[it] * 0.5f }
            }
            steps = FloatArray(4) { steps[it] * 2f } // reabrir un poco al pasar al nivel más fino
        }
        val result = Similarity(exp(p[3]), p[2], p[0], p[1])
        return Result(result, score(levels.first(), p))
    }

    private fun buildLevels(before: GrayImage, after: GrayImage): List<Level> {
        val out = mutableListOf<Level>()
        var b = before
        var a = after
        while (true) {
            val blur = if (b.width >= 128) 2 else 1
            out += Level(edges(b, blur), edges(a, blur), weights(b.width, b.height))
            if (b.width / 2 < 32 || out.size == 3) break
            b = b.half(); a = a.half()
        }
        return out
    }

    private fun edges(img: GrayImage, blur: Int): GrayImage = img.boxBlur(1).sobel().boxBlur(blur)

    private fun weights(w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        val margin = (w * 0.04f).toInt().coerceAtLeast(2)
        for (y in margin until h - margin) for (x in margin until w - margin) {
            val u = (x.toFloat() / w - roiCenter.x) / roiRadius.x
            val v = (y.toFloat() / w - roiCenter.y) / roiRadius.y
            out[y * w + x] = 0.25f + 0.75f * exp(-0.5f * (u * u + v * v))
        }
        return out
    }

    /** NCC ponderada entre el mapa de bordes "antes" y el "después" deformado por T. */
    private fun score(level: Level, p: FloatArray): Float {
        val t = Similarity(exp(p[3]), p[2], p[0], p[1]).inverse() // antes → después
        val b = level.before
        val a = level.after
        val w = b.width
        val c = kotlin.math.cos(t.rotation) * t.scale
        val s = kotlin.math.sin(t.rotation) * t.scale
        var sw = 0.0; var sa = 0.0; var sb = 0.0; var saa = 0.0; var sbb = 0.0; var sab = 0.0
        var covered = 0.0; var total = 0.0
        for (y in 0 until b.height) {
            val v = y.toFloat() / w
            for (x in 0 until w) {
                val wt = level.weights[y * w + x]
                if (wt == 0f) continue
                total += wt
                val u = x.toFloat() / w
                val qx = (c * u - s * v + t.tx) * w
                val qy = (s * u + c * v + t.ty) * w
                val av = a.sample(qx, qy)
                if (av.isNaN()) continue
                val bv = b.data[y * w + x]
                covered += wt
                sw += wt; sa += wt * av; sb += wt * bv
                saa += wt * av * av; sbb += wt * bv * bv; sab += wt * av * bv
            }
        }
        if (sw <= 0.0 || covered / total < 0.6) return -1f
        val ma = sa / sw; val mb = sb / sw
        val va = saa / sw - ma * ma
        val vb = sbb / sw - mb * mb
        if (va <= 1e-12 || vb <= 1e-12) return -1f
        val ncc = (sab / sw - ma * mb) / sqrt(va * vb)
        // Penaliza levemente la pérdida de cobertura.
        return (ncc - 0.2 * (1 - covered / total)).toFloat()
    }

    companion object {
        fun isConfident(score: Float) = score >= 0.35f
        fun approxEquals(a: Float, b: Float, eps: Float) = abs(a - b) <= eps
    }
}
