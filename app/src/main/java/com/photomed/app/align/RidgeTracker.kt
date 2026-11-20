package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Detección de surcos (pliegues oscuros alargados) en la piel, dentro de un pasillo:
 *
 * 1. **Respuesta de valle multiescala (Hessiana)**: en cada píxel, el autovalor mayor de la matriz
 *    Hessiana de la luminancia suavizada (σ² · λ₁, normalizado por escala) es grande y positivo en
 *    un valle oscuro; se pondera por la alineación del valle con el pasillo (cos² entre la
 *    dirección del valle y la del pasillo). Como el filtro de Frangi / Hessiano híbrido usados en la
 *    literatura de detección de arrugas.
 * 2. **Trazado por programación dinámica**: el pasillo se muestrea en estaciones; en cada una se
 *    elige el desplazamiento lateral que maximiza la respuesta acumulada con penalización de
 *    curvatura, lo que da una línea continua que sigue el surco.
 * 3. **Aceptación**: solo si la respuesta media del trazado supera claramente la del pasillo y un
 *    mínimo absoluto; se recortan los extremos sin surco. Si no, no hay surco (no se inventa).
 */
object RidgeTracker {
    data class Result(val points: List<Pt>, val strength: Float, val contrast: Float)

    /** Escalas (px, para una imagen de 720 de ancho; se escalan con el ancho real). */
    private val SIGMAS = floatArrayOf(2.0f, 3.5f, 5.5f)
    const val MIN_STRENGTH = 0.030f
    const val MIN_CONTRAST = 2.2f
    /** Fracción mínima del trazado sobre piel del rostro (sin barba, manos, gafas ni fondo). */
    const val MIN_SKIN = 0.75f

    /**
     * [lum]: luminancia 0..1; [center]: eje del pasillo (unidades del ancho); [halfWidth]: semiancho
     * del pasillo (unidades del ancho). Devuelve el surco o null si no hay uno claro.
     */
    fun track(lum: GrayImage, center: List<Pt>, halfWidth: Float, stations: Int = 28, offsets: Int = 14,
              minStrength: Float = MIN_STRENGTH, minContrast: Float = MIN_CONTRAST,
              /** Piel del rostro (opcional): solo cuenta la respuesta sobre piel; el trazado debe ir por piel. */
              skin: ((Pt) -> Boolean)? = null,
              /**
               * El surco nace en el primer punto del eje (p. ej. el nasogeniano en el ala de la nariz): el
               * trazado empieza ahí y solo se recorta por el final.
               */
              anchorStart: Boolean = false): Result? {
        if (center.size < 2) return null
        val w = lum.width.toFloat()
        val scale = lum.width / 720f
        // eje muestreado por longitud de arco, en píxeles
        val axis = resample(center.map { Pt(it.x * w, it.y * w) }, stations)
        val hw = halfWidth * w
        // recuadro del pasillo con margen para los filtros
        val pad = (hw + 3 * SIGMAS.last() * scale + 4).roundToInt()
        val x0 = max(0, axis.minOf { it.x }.roundToInt() - pad); val x1 = min(lum.width - 1, axis.maxOf { it.x }.roundToInt() + pad)
        val y0 = max(0, axis.minOf { it.y }.roundToInt() - pad); val y1 = min(lum.height - 1, axis.maxOf { it.y }.roundToInt() + pad)
        if (x1 - x0 < 8 || y1 - y0 < 8) return null
        val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
        val sub = FloatArray(bw * bh) { i -> lum[x0 + i % bw, y0 + i / bw] }
        // respuesta de valle y su dirección (componentes del vector del valle)
        val resp = FloatArray(bw * bh); val vx = FloatArray(bw * bh); val vy = FloatArray(bw * bh)
        for (s0 in SIGMAS) {
            val s = s0 * scale
            val g = blur(sub, bw, bh, s)
            for (y in 1 until bh - 1) for (x in 1 until bw - 1) {
                val i = y * bw + x
                val ixx = g[i + 1] - 2 * g[i] + g[i - 1]
                val iyy = g[i + bw] - 2 * g[i] + g[i - bw]
                val ixy = (g[i + bw + 1] - g[i + bw - 1] - g[i - bw + 1] + g[i - bw - 1]) / 4
                val tr = ixx + iyy; val det = ixx * iyy - ixy * ixy
                val disc = sqrt(max(0f, tr * tr / 4 - det))
                val l1 = tr / 2 + disc   // mayor: curvatura a través del valle
                val l2 = tr / 2 - disc
                if (l1 <= 0f) continue
                // valle alargado: |λ2| pequeño frente a λ1
                val r = s * s * (l1 - max(0f, abs(l2)) * 0.5f)
                if (r > resp[i]) {
                    resp[i] = r
                    // vector propio de λ1 (normal al valle); el valle va en la perpendicular
                    var nx = ixy; var ny = l1 - ixx
                    if (abs(nx) + abs(ny) < 1e-9f) { nx = l1 - iyy; ny = ixy }
                    val n = hypot(nx, ny).coerceAtLeast(1e-9f)
                    vx[i] = -ny / n; vy[i] = nx / n
                }
            }
        }
        fun sample(px: Float, py: Float, tx: Float, ty: Float): Float {
            val xi = (px - x0).roundToInt(); val yi = (py - y0).roundToInt()
            if (xi !in 1 until bw - 1 || yi !in 1 until bh - 1) return 0f
            val i = yi * bw + xi
            val c = vx[i] * tx + vy[i] * ty
            return resp[i] * c * c
        }
        // matriz de respuesta por estación y desplazamiento
        val K = offsets; val nS = axis.size
        val tang = axis.indices.map { s ->
            val a = axis[max(0, s - 1)]; val b = axis[min(nS - 1, s + 1)]
            val n = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1e-6f); Pt((b.x - a.x) / n, (b.y - a.y) / n)
        }
        val R = Array(nS) { FloatArray(2 * K + 1) }
        for (s in 0 until nS) for (k in -K..K) {
            val d = hw * k / K
            val t = tang[s]
            val qx = axis[s].x - t.y * d; val qy = axis[s].y + t.x * d
            R[s][k + K] = if (skin != null && !skin(Pt(qx / w, qy / w))) 0f else sample(qx, qy, t.x, t.y)
        }
        val all = R.flatMap { it.toList() }.sorted()
        // fondo: respuesta media del pasillo (estable también en piel lisa, donde la mediana es ~0)
        val bg = all.average().toFloat().coerceAtLeast(1e-6f)
        val norm = all[(all.size * 0.98f).toInt().coerceAtMost(all.size - 1)].coerceAtLeast(1e-6f)
        // programación dinámica: máxima respuesta con penalización de cambios bruscos de desplazamiento
        val lam = 0.06f * norm
        val acc = Array(nS) { FloatArray(2 * K + 1) }; val from = Array(nS) { IntArray(2 * K + 1) }
        for (k in 0..2 * K) acc[0][k] = if (anchorStart && kotlin.math.abs(k - K) > 2) -1e9f else R[0][k]
        for (s in 1 until nS) for (k in 0..2 * K) {
            var best = -Float.MAX_VALUE; var bi = k
            for (j in max(0, k - 3)..min(2 * K, k + 3)) {
                val v = acc[s - 1][j] - lam * (k - j) * (k - j)
                if (v > best) { best = v; bi = j }
            }
            acc[s][k] = best + R[s][k]; from[s][k] = bi
        }
        var k = (0..2 * K).maxBy { acc[nS - 1][it] }
        val path = IntArray(nS)
        for (s in nS - 1 downTo 0) { path[s] = k; k = from[s][k] }
        val vals = FloatArray(nS) { R[it][path[it]] }
        // recorte de extremos sin surco: el tramo continuo más largo con respuesta suficiente
        val mean = vals.average().toFloat()
        val on = BooleanArray(nS) { vals[it] >= 0.45f * mean && vals[it] >= 1.2f * bg }
        var bestA = 0; var bestB = -1; var a = -1
        if (anchorStart) {
            // desde el origen hasta la última estación con surco, si la mayor parte del tramo lo tiene
            val last = (nS - 1 downTo 0).firstOrNull { on[it] } ?: -1
            if (last >= 0 && (0..last).count { on[it] } >= (last + 1) * 0.6f) bestB = last
        } else for (s in 0..nS) {
            if (s < nS && on[s]) { if (a < 0) a = s } else if (a >= 0) { if (s - 1 - a > bestB - bestA) { bestA = a; bestB = s - 1 }; a = -1 }
        }
        if (bestB - bestA + 1 < nS * 0.4f) return null
        val seg = (bestA..bestB)
        val strength = seg.map { vals[it] }.average().toFloat()
        val contrast = strength / bg
        if (strength < minStrength || contrast < minContrast) return null
        val pts = seg.map { s ->
            val d = hw * (path[s] - K) / K; val t = tang[s]
            Pt((axis[s].x - t.y * d) / w, (axis[s].y + t.x * d) / w)
        }
        if (skin != null && pts.count(skin) < pts.size * MIN_SKIN) return null
        return Result(smooth(pts), strength, contrast)
    }

    private fun resample(p: List<Pt>, n: Int): List<Pt> {
        val d = FloatArray(p.size); for (i in 1 until p.size) d[i] = d[i - 1] + hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
        val total = d.last().coerceAtLeast(1e-6f)
        return (0 until n).map { j ->
            val t = total * j / (n - 1)
            var i = 1; while (i < p.size - 1 && d[i] < t) i++
            val u = ((t - d[i - 1]) / (d[i] - d[i - 1]).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
            Pt(p[i - 1].x + (p[i].x - p[i - 1].x) * u, p[i - 1].y + (p[i].y - p[i - 1].y) * u)
        }
    }

    private fun smooth(p: List<Pt>): List<Pt> = p.indices.map { i ->
        val a = p[max(0, i - 1)]; val b = p[i]; val c = p[min(p.size - 1, i + 1)]
        if (i == 0 || i == p.size - 1) b else Pt((a.x + 2 * b.x + c.x) / 4, (a.y + 2 * b.y + c.y) / 4)
    }

    /** Desenfoque gaussiano separable. */
    private fun blur(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val r = (3 * sigma).roundToInt().coerceAtLeast(1)
        val k = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }.let { kk -> val s = kk.sum(); FloatArray(kk.size) { kk[it] / s } }
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f; for (j in -r..r) s += k[j + r] * src[y * w + (x + j).coerceIn(0, w - 1)]; tmp[y * w + x] = s
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f; for (j in -r..r) s += k[j + r] * tmp[(y + j).coerceIn(0, h - 1) * w + x]; out[y * w + x] = s
        }
        return out
    }
}
