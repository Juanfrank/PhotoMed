package com.photomed.app.align

import android.graphics.Bitmap
import com.photomed.app.data.Zone
import java.io.Closeable
import kotlin.math.exp
import kotlin.math.roundToInt

/** Resultado de analizar un cuadro: evaluación + puntos para dibujar (normalizados, unidad = ancho). */
data class LiveFrame(
    val eval: MatchEval,
    val referencePoints: List<Pt> = emptyList(),
    val currentPoints: List<Pt> = emptyList(),
    val method: String = "",
    val totalPoints: Int = 0,
)

/**
 * Motor de coincidencia en vivo entre la foto de referencia (superpuesta) y la cámara.
 * Por cuadro:
 * - Puntos anatómicos (ML Kit continuo): % de puntos a ≤ 1,5 % del ancho de su posición de
 *   referencia, semejanza actual → referencia y perspectiva vertical por proporciones.
 * - Sin puntos: puntos de contorno de la referencia (bordes fuertes en las regiones anatómicas)
 *   y % de ellos que encuentran borde en el cuadro actual a esa distancia; semejanza por
 *   coincidencia de contornos.
 * Se llama desde el hilo de análisis (bloqueante).
 */
class LiveMatcher(zone: Zone) : Closeable {
    private val spec = ZoneAlignment.of(zone)
    private val detector = LandmarkDetector(stream = true)
    private val edges = EdgeAligner(spec.contours)
    private var refPts: Map<String, Pt> = emptyMap()
    private var refGray: GrayImage? = null
    private var refEdgePts: List<Pair<Int, Int>> = emptyList()
    private var last: Similarity? = null
    private var matched = false

    /** Umbral de puntos coincidentes (0..1). */
    @Volatile var threshold: Float = LiveMatch.DEFAULT_THRESHOLD

    fun setReference(bmp: Bitmap) {
        refPts = detector.detect(spec.detector, bmp)
        val g = ImageOps.gray(bmp, EDGE_W)
        refGray = g
        refEdgePts = strongEdgePoints(g)
        last = null; matched = false
    }

    fun analyze(frame: Bitmap): LiveFrame {
        val ref = refGray ?: return LiveFrame(LiveMatch.evaluate(null, spec.pivot, threshold = threshold))
        val cur = detector.detect(spec.detector, frame)
        val keys = spec.landmarks.filter { it in refPts && it in cur }
        val eval: MatchEval
        val method: String
        var drawKeys: List<String> = emptyList()
        val total: Int
        if (keys.size >= 2) {
            val rp = keys.map { refPts[it]!! }; val cp = keys.map { cur[it]!! }
            val lm = landmarkTransform(spec.landmarks, refPts, cur)
            val ks = KeystoneFit.fromLandmarks(rp, cp)?.keystoneDeg ?: 0f
            last = lm?.first ?: last
            eval = LiveMatch.evaluate(lm?.first, spec.pivot, matched, LiveMatch.pointFraction(rp, cp), threshold, ks)
            method = "puntos anatómicos"; drawKeys = keys; total = keys.size
        } else {
            val g = ImageOps.gray(frame, EDGE_W)
            val r = edges.align(ref, g, last, if (last != null) EdgeAligner.Window.NARROW else EdgeAligner.Window.LIVE, levels = 2)
            val t = if (EdgeAligner.isConfident(r.score)) r.transform else null
            last = t ?: last
            eval = LiveMatch.evaluate(t, spec.pivot, matched, edgeFraction(g), threshold)
            method = "puntos de contorno"; total = refEdgePts.size
        }
        matched = eval.status == MatchStatus.MATCH
        val shown = if (drawKeys.size > MAX_DRAWN) drawKeys.filterIndexed { i, _ -> i % (drawKeys.size / MAX_DRAWN + 1) == 0 } else drawKeys
        return LiveFrame(eval, shown.map { refPts[it]!! }, shown.map { cur[it]!! }, method, total)
    }

    /** Bordes más fuertes de la referencia dentro de las regiones anatómicas (supresión por celdas). */
    private fun strongEdgePoints(g: GrayImage): List<Pair<Int, Int>> {
        val mag = g.boxBlur(1).sobel()
        val w = g.width; val h = g.height
        val cand = ArrayList<Triple<Int, Int, Float>>()
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            var rw = 0f
            for (r in spec.contours) {
                val u = (x.toFloat() / w - r.center.x) / r.radius.x; val v = (y.toFloat() / w - r.center.y) / r.radius.y
                rw += r.weight * exp(-0.5f * (u * u + v * v))
            }
            if (rw > 0.3f) cand += Triple(x, y, mag[x, y] * rw)
        }
        cand.sortByDescending { it.third }
        val cell = 3
        val used = HashSet<Int>()
        val out = ArrayList<Pair<Int, Int>>()
        for ((x, y, _) in cand) {
            val c = (y / cell) * 1000 + x / cell
            if (used.add(c)) out += x to y
            if (out.size >= 300) break
        }
        return out
    }

    /** % de puntos de contorno de la referencia con borde en el cuadro actual a ≤ tolerancia. */
    private fun edgeFraction(g: GrayImage): Float {
        if (refEdgePts.isEmpty()) return 0f
        val mag = g.boxBlur(1).sobel()
        val sorted = mag.data.sortedArray()
        val thr = sorted[(sorted.size * 0.85f).toInt()]
        val r = (LiveMatch.POINT_TOLERANCE * g.width).roundToInt().coerceAtLeast(1)
        return refEdgePts.count { (x, y) ->
            var hit = false
            loop@ for (dy in -r..r) for (dx in -r..r) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until g.width && yy in 0 until g.height && mag[xx, yy] >= thr) { hit = true; break@loop }
            }
            hit
        } / refEdgePts.size.toFloat()
    }

    override fun close() = detector.close()

    companion object {
        const val EDGE_W = 96
        const val MAX_DRAWN = 36
    }
}
