package com.photomed.app.ui.camera

import androidx.compose.ui.geometry.Offset
import com.photomed.app.align.Landmarks
import com.photomed.app.align.Pt
import kotlin.math.abs

/**
 * Adaptación de la guía a la foto de referencia: las anclas anatómicas de la ilustración
 * (cejas, pupilas, base nasal, boca, mentón; hombros, caderas, rodillas, codos…) se llevan a la
 * posición detectada en la foto (ML Kit). Vertical: interpolación lineal por tramos entre anclas
 * (monótona, extrapolada con la pendiente de los extremos). Horizontal: escala por la distancia
 * entre pupilas u hombros y centro por la línea media.
 * Coordenadas: x ∈ [0,1] del ancho, y ∈ [0,1] del alto del encuadre 3:4.
 */
class GuideWarp(private val ys: List<Pair<Float, Float>>, val sx: Float, private val cxArt: Float, private val cxRef: Float) {

    fun y(a: Float): Float {
        if (ys.size == 1) return ys[0].second + (a - ys[0].first) * sx
        val i = ys.indexOfLast { it.first <= a }.coerceIn(0, ys.size - 2)
        val (a0, b0) = ys[i]; val (a1, b1) = ys[i + 1]
        return b0 + (a - a0) * (b1 - b0) / (a1 - a0)
    }

    fun x(a: Float): Float = cxRef + (a - cxArt) * sx

    fun map(o: Offset) = Offset(x(o.x), y(o.y))

    /** Ancla detectada para una línea horizontal de la guía (si coincide con un ancla nombrada). */
    companion object {
        /** Posiciones anatómicas detectadas en la foto (mismas claves que las anclas de la guía). */
        fun detected(points: Map<String, Pt>): Map<String, Float> {
            val h = 4f / 3f
            fun avgY(keys: List<String>) = keys.mapNotNull { points[it] }.takeIf { it.isNotEmpty() }?.map { it.y / h }?.average()?.toFloat()
            fun grp(prefix: String) = points.keys.filter { it.startsWith(prefix) }
            val out = mutableMapOf<String, Float>()
            (avgY(grp("brow_l_top_") + grp("brow_r_top_")))?.let { out["cejas"] = it }
            avgY(listOf(Landmarks.EYE_L, Landmarks.EYE_R))?.let { out["pupilas"] = it }
            (avgY(grp("nose_bottom_")) ?: avgY(listOf(Landmarks.NOSE_BASE)))?.let { out["base nasal"] = it }
            avgY(listOf(Landmarks.MOUTH_L, Landmarks.MOUTH_R))?.let { out["boca"] = it }
            grp("oval_").mapNotNull { points[it] }.maxOfOrNull { it.y / h }?.let { out["mentón"] = it }
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

        /** Deformación de la ilustración (anclas) a la foto (detectadas); null si no hay anclas comunes. */
        fun fit(art: Map<String, Float>, ref: Map<String, Float>): GuideWarp? {
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
            return GuideWarp(pairs, sx.coerceIn(0.3f, 3f), cxArt, cxRef)
        }
    }

    /** Coloca una línea de la guía: si es horizontal y coincide con un ancla, a su altura detectada. */
    fun line(a: Offset, b: Offset, artAnchors: Map<String, Float>, ref: Map<String, Float>): Pair<Offset, Offset> {
        if (abs(a.y - b.y) < 1e-4f) {
            val name = artAnchors.entries.firstOrNull { !it.key.startsWith("x:") && abs(it.value - a.y) < 0.004f }?.key
            val yy = name?.let { ref[it] } ?: y(a.y)
            return Offset(x(a.x), yy) to Offset(x(b.x), yy)
        }
        return map(a) to map(b)
    }
}
