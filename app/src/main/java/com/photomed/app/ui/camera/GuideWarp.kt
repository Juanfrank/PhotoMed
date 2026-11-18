package com.photomed.app.ui.camera

import androidx.compose.ui.geometry.Offset
import com.photomed.app.align.Landmarks
import com.photomed.app.align.Pt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Adaptación de la guía a la foto de referencia.
 *
 * 1. **Inclinación (roll)**: ángulo de la línea entre pupilas (u hombros en el cuerpo) en la foto.
 *    Los puntos se enderezan alrededor del punto medio antes de ajustar, y la guía ya ajustada se
 *    dibuja girada ese ángulo ([rollDeg], [pivot]): sigue a la cabeza inclinada.
 * 2. **Vertical**: las anclas de la ilustración (cejas, pupilas, base nasal, boca, mentón; hombros,
 *    caderas, rodillas, codos…) se llevan a su altura detectada; interpolación lineal por tramos
 *    (monótona, extrapolada con la pendiente de los extremos).
 * 3. **Horizontal fila a fila** (rostro): el contorno de la cabeza de la ilustración (cráneo,
 *    sienes, mejillas, mandíbula, mentón) se lleva al óvalo facial detectado a la misma altura, con
 *    escala independiente a cada lado de la línea media. Por encima del óvalo (cráneo) y por debajo
 *    del mentón se conserva la escala de la fila válida más cercana, así el cráneo mantiene la forma
 *    de la ilustración con el ancho de la frente del paciente. Sin óvalo: escala uniforme por la
 *    distancia entre pupilas (u hombros) y centro en la línea media.
 *
 * Coordenadas: x ∈ [0,1] del ancho, y ∈ [0,1] del alto del encuadre 3:4 (sin girar).
 */
class GuideWarp(
    private val ys: List<Pair<Float, Float>>,
    val sx: Float,
    private val cxArt: Float,
    private val cxRef: Float,
    /** Por fila de la ilustración (ROWS + 1 muestras en y ∈ [0,1]): escala izquierda, derecha y centro destino. */
    private val rowScaleL: FloatArray? = null,
    private val rowScaleR: FloatArray? = null,
    private val rowCenter: FloatArray? = null,
    /** Giro de la cabeza en la foto (°, horario en pantalla) y centro del giro (x del ancho, y del alto). */
    val rollDeg: Float = 0f,
    val pivot: Offset = Offset(0.5f, 0.5f),
) {

    fun y(a: Float): Float {
        if (ys.size == 1) return ys[0].second + (a - ys[0].first) * sx
        val i = ys.indexOfLast { it.first <= a }.coerceIn(0, ys.size - 2)
        val (a0, b0) = ys[i]; val (a1, b1) = ys[i + 1]
        return b0 + (a - a0) * (b1 - b0) / (a1 - a0)
    }

    /** x uniforme (sin contorno): escala por pupilas/hombros. */
    fun x(a: Float): Float = cxRef + (a - cxArt) * sx

    /** x en la fila de la ilustración [ay]: escala de su lado (izquierdo/derecho de la línea media). */
    fun x(a: Float, ay: Float): Float {
        val l = rowScaleL ?: return x(a)
        val i = ((ay.coerceIn(0f, 1f)) * ROWS).toInt().coerceIn(0, ROWS)
        val s = if (a < cxArt) l[i] else rowScaleR!![i]
        return rowCenter!![i] + (a - cxArt) * s
    }

    /** Escala y centro de una fila (para dibujar la franja de la ilustración por mitades). */
    fun rowLeftScale(ay: Float) = rowScaleL?.let { it[(ay.coerceIn(0f, 1f) * ROWS).toInt().coerceIn(0, ROWS)] } ?: sx
    fun rowRightScale(ay: Float) = rowScaleR?.let { it[(ay.coerceIn(0f, 1f) * ROWS).toInt().coerceIn(0, ROWS)] } ?: sx
    fun rowCenter(ay: Float) = rowCenter?.let { it[(ay.coerceIn(0f, 1f) * ROWS).toInt().coerceIn(0, ROWS)] } ?: cxRef
    val artCenter: Float get() = cxArt
    val followsOutline: Boolean get() = rowScaleL != null

    fun map(o: Offset) = Offset(x(o.x, o.y), y(o.y))

    /** Coloca una línea de la guía: si es horizontal y coincide con un ancla, a su altura detectada. */
    fun line(a: Offset, b: Offset, artAnchors: Map<String, Float>, ref: Map<String, Float>): Pair<Offset, Offset> {
        if (abs(a.y - b.y) < 1e-4f) {
            val name = artAnchors.entries.firstOrNull { !it.key.startsWith("x:") && abs(it.value - a.y) < 0.004f }?.key
            val yy = name?.let { ref[it] } ?: y(a.y)
            return Offset(x(a.x, a.y), yy) to Offset(x(b.x, b.y), yy)
        }
        return map(a) to map(b)
    }

    /** Puntos de la foto enderezados y medidas para ajustar la guía. */
    class Detected(
        /** Anclas (mismas claves que las de la ilustración), con los puntos ya enderezados. */
        val anchors: Map<String, Float>,
        /** Óvalo facial enderezado (x del ancho, y del alto), si se detectó. */
        val oval: List<Offset>,
        val rollDeg: Float,
        val pivot: Offset,
    )

    companion object {
        const val ROWS = 160
        const val MAX_ROLL = 25f
        private const val H = 4f / 3f

        /**
         * Inclinación de la cabeza (o de los hombros) en la imagen, en grados (horario positivo, en
         * pantalla) y centro del giro, en unidades del ancho; null si faltan los puntos.
         * Usa los centros de los contornos de los ojos (más estables que un punto aislado) y, si no
         * hay, las pupilas; en el cuerpo, los hombros.
         */
        fun roll(points: Map<String, Pt>): Pair<Float, Pt>? = com.photomed.app.align.HeadRoll.of(points, MAX_ROLL)

        private fun rotate(p: Pt, c: Pt, deg: Float): Pt {
            val t = Math.toRadians(deg.toDouble()); val cs = cos(t).toFloat(); val sn = sin(t).toFloat()
            val dx = p.x - c.x; val dy = p.y - c.y
            return Pt(c.x + cs * dx - sn * dy, c.y + sn * dx + cs * dy)
        }

        /** Enderezar los puntos (girar −roll) y medir anclas y óvalo. */
        fun analyze(points: Map<String, Pt>): Detected {
            val r = roll(points)
            val upright = if (r == null || abs(r.first) < 0.3f) points else points.mapValues { rotate(it.value, r.second, -r.first) }
            val oval = (0 until 64).mapNotNull { upright["oval_$it"] }.map { Offset(it.x, it.y / H) }
            return Detected(detected(upright), if (oval.size >= 12) oval else emptyList(),
                r?.first ?: 0f, r?.second?.let { Offset(it.x, it.y / H) } ?: Offset(0.5f, 0.5f))
        }

        /** Posiciones anatómicas detectadas en la foto (mismas claves que las anclas de la guía). */
        fun detected(points: Map<String, Pt>): Map<String, Float> {
            fun avgY(keys: List<String>) = keys.mapNotNull { points[it] }.takeIf { it.isNotEmpty() }?.map { it.y / H }?.average()?.toFloat()
            fun grp(prefix: String) = points.keys.filter { it.startsWith(prefix) }
            val out = mutableMapOf<String, Float>()
            (avgY(grp("brow_l_top_") + grp("brow_r_top_")))?.let { out["cejas"] = it }
            avgY(listOf(Landmarks.EYE_L, Landmarks.EYE_R))?.let { out["pupilas"] = it }
            (avgY(grp("nose_bottom_")) ?: avgY(listOf(Landmarks.NOSE_BASE)))?.let { out["base nasal"] = it }
            avgY(listOf(Landmarks.MOUTH_L, Landmarks.MOUTH_R))?.let { out["boca"] = it }
            grp("oval_").mapNotNull { points[it] }.maxOfOrNull { it.y / H }?.let { out["mentón"] = it }
            points[Landmarks.EYE_R]?.let { out["x:pupila_der"] = it.x }
            points[Landmarks.EYE_L]?.let { out["x:pupila_izq"] = it.x }
            avgY(listOf(Landmarks.SHOULDER_L, Landmarks.SHOULDER_R))?.let { out["hombros"] = it }
            avgY(listOf(Landmarks.HIP_L, Landmarks.HIP_R))?.let { out["caderas"] = it }
            avgY(listOf(Landmarks.KNEE_L, Landmarks.KNEE_R))?.let { out["rodillas"] = it }
            avgY(listOf(Landmarks.ELBOW_L, Landmarks.ELBOW_R))?.let { out["codos"] = it }
            points[Landmarks.SHOULDER_R]?.let { out["x:hombro_der"] = it.x }
            points[Landmarks.SHOULDER_L]?.let { out["x:hombro_izq"] = it.x }
            val cx = listOfNotNull(out["x:pupila_der"], out["x:pupila_izq"]).takeIf { it.size == 2 }?.average()
                ?: listOfNotNull(out["x:hombro_der"], out["x:hombro_izq"]).takeIf { it.size == 2 }?.average()
            cx?.let { out["x:centro"] = it.toFloat() }
            return out
        }

        /** Bordes izquierdo y derecho de un polígono a la altura [y] (null si no lo corta). */
        fun span(poly: List<Offset>, y: Float): Pair<Float, Float>? {
            var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
            for (i in poly.indices) {
                val a = poly[i]; val b = poly[(i + 1) % poly.size]
                if ((a.y <= y && b.y > y) || (b.y <= y && a.y > y)) {
                    val x = a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x)
                    lo = min(lo, x); hi = max(hi, x)
                }
            }
            return if (hi > lo) lo to hi else null
        }

        /** Contorno de la ilustración (y, xL, xR por fila) a la altura [ay]; null fuera de su rango. */
        private fun artSpan(outline: FloatArray, ay: Float): Pair<Float, Float>? {
            val n = outline.size / 3
            if (n < 2 || ay < outline[0] || ay > outline[(n - 1) * 3]) return null
            for (k in 0 until n - 1) {
                val y0 = outline[k * 3]; val y1 = outline[(k + 1) * 3]
                if (ay in y0..y1) {
                    val t = if (y1 > y0) (ay - y0) / (y1 - y0) else 0f
                    return (outline[k * 3 + 1] + t * (outline[(k + 1) * 3 + 1] - outline[k * 3 + 1])) to
                        (outline[k * 3 + 2] + t * (outline[(k + 1) * 3 + 2] - outline[k * 3 + 2]))
                }
            }
            return null
        }

        /** Deformación de la ilustración (anclas) a la foto (detectadas); null si no hay anclas comunes. */
        fun fit(art: Map<String, Float>, ref: Map<String, Float>): GuideWarp? = fit(art, Detected(ref, emptyList(), 0f, Offset(0.5f, 0.5f)))

        fun fit(art: Map<String, Float>, det: Detected, outline: FloatArray = FloatArray(0)): GuideWarp? {
            val ref = det.anchors
            val pairs = art.filterKeys { !it.startsWith("x:") && it in ref }.map { (k, v) -> v to ref[k]!! }
                .sortedBy { it.first }
                // monotonía: descartar pares que invierten el orden
                .fold(mutableListOf<Pair<Float, Float>>()) { acc, p -> if (acc.isEmpty() || (p.first > acc.last().first + 1e-3f && p.second > acc.last().second)) acc += p; acc }
            if (pairs.isEmpty()) return null
            val sx = listOf("x:pupila_der" to "x:pupila_izq", "x:hombro_der" to "x:hombro_izq").firstNotNullOfOrNull { (a, b) ->
                val da = (art[a] ?: return@firstNotNullOfOrNull null) - (art[b] ?: return@firstNotNullOfOrNull null)
                val dr = (ref[a] ?: return@firstNotNullOfOrNull null) - (ref[b] ?: return@firstNotNullOfOrNull null)
                if (abs(da) > 1e-3f) abs(dr / da) else null
            } ?: if (pairs.size >= 2) (pairs.last().second - pairs.first().second) / (pairs.last().first - pairs.first().first) * (4f / 3f) * 0.75f else 1f
            val cxArt = art["x:centro"] ?: 0.5f
            val cxRef = ref["x:centro"] ?: cxArt
            val base = GuideWarp(pairs, sx.coerceIn(0.3f, 3f), cxArt, cxRef, rollDeg = det.rollDeg, pivot = det.pivot)
            if (outline.size < 6 || det.oval.isEmpty()) return base

            // Escalas por fila: borde de la ilustración → borde del óvalo a la misma altura.
            val sxc = base.sx
            val l = FloatArray(ROWS + 1) { Float.NaN }; val r = FloatArray(ROWS + 1) { Float.NaN }; val c = FloatArray(ROWS + 1) { Float.NaN }
            var maxHalf = 0f
            for (k in 0 until outline.size / 3) maxHalf = max(maxHalf, min(cxArt - outline[k * 3 + 1], outline[k * 3 + 2] - cxArt))
            // El ápice del óvalo detectado (frente alta) es estrecho y no corresponde al cráneo de la
            // ilustración: solo se usan las filas desde las cejas hacia abajo y con el óvalo ancho.
            var detMaxHalf = 0f
            for (o in det.oval) detMaxHalf = max(detMaxHalf, abs(o.x - (ref["x:centro"] ?: 0.5f)))
            val browArt = art["cejas"] ?: art["pupilas"]
            for (i in 0..ROWS) {
                val ay = i / ROWS.toFloat()
                if (browArt != null && ay < browArt) continue
                val a = artSpan(outline, ay) ?: continue
                val hl = cxArt - a.first; val hr = a.second - cxArt
                // cerca del mentón el ancho tiende a 0: escala inestable, se usa la vecina
                if (hl < 0.3f * maxHalf || hr < 0.3f * maxHalf) continue
                val d = span(det.oval, base.y(ay)) ?: continue
                if ((d.second - d.first) / 2 < 0.45f * detMaxHalf) continue
                val center = (d.first + d.second) / 2
                // centro del óvalo y escala de cada lado, acotados respecto de la escala por pupilas
                l[i] = ((center - d.first) / hl).coerceIn(sxc * 0.6f, sxc * 1.6f)
                r[i] = ((d.second - center) / hr).coerceIn(sxc * 0.6f, sxc * 1.6f)
                c[i] = center
            }
            if (l.none { !it.isNaN() }) return base
            fun fill(v: FloatArray) {
                val first = v.indexOfFirst { !it.isNaN() }; val last = v.indexOfLast { !it.isNaN() }
                for (i in 0 until first) v[i] = v[first]
                for (i in last + 1..ROWS) v[i] = v[last]
                var prev = first
                for (i in first + 1..last) if (!v[i].isNaN()) {
                    for (j in prev + 1 until i) v[j] = v[prev] + (v[i] - v[prev]) * (j - prev) / (i - prev)
                    prev = i
                }
            }
            fun smooth(v: FloatArray, k: Int = 8): FloatArray = FloatArray(v.size) { i ->
                var s = 0f; var n = 0
                for (j in max(0, i - k)..min(v.size - 1, i + k)) { s += v[j]; n++ }
                s / n
            }
            fill(l); fill(r); fill(c)
            return GuideWarp(pairs, base.sx, cxArt, cxRef, smooth(l), smooth(r), smooth(c), det.rollDeg, det.pivot)
        }
    }
}
