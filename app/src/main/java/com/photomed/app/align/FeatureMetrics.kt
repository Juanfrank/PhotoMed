package com.photomed.app.align

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Medidas de los rasgos con la malla de MediaPipe (en mm, con la escala del iris o del sensor):
 * - **labios**: ancho (comisura a comisura), alto del labio superior e inferior en el centro,
 *   proporción inferior / superior (referencia estética ≈ 1,6), área del bermellón superior e inferior
 *   y longitud del filtrum (subnasal → borde del labio superior);
 * - **ojos**: ancho y alto de la hendidura palpebral e inclinación cantal (° respecto al eje entre
 *   pupilas; positiva si el canto externo está más alto);
 * - **cejas**: longitud y altura sobre la pupila (pupila → borde superior de la ceja);
 * - **nariz**: ancho entre los pliegues alares y alto (entre los ojos → subnasal);
 * - **contorno**: ancho bicigomático aproximado (234 ↔ 454), tercio medio (cejas → subnasal) y
 *   tercio inferior (subnasal → mentón);
 * - **línea del pelo**: altura de la frente (cejas → pelo) en el centro, sobre cada ceja y sobre su
 *   cola, y entradas temporales (cola − centro), para seguir los patrones de calvicie.
 * Todas son proyecciones 2D en la foto (dependen del giro de la cabeza).
 */
object FeatureMetrics {
    private val LIP_UP_OUT = intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291)
    private val LIP_UP_IN = intArrayOf(308, 415, 310, 311, 312, 13, 82, 81, 80, 191, 78)
    private val LIP_LO_OUT = intArrayOf(291, 375, 321, 405, 314, 17, 84, 181, 91, 146, 61)
    private val LIP_LO_IN = intArrayOf(78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308)

    private fun d(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /** Sistema de la cara: eje entre pupilas (x) y "arriba" (perpendicular). */
    private class Axes(mesh: List<Pt>) {
        val r = if (mesh.size > 473) mesh[468] else Pt((mesh[33].x + mesh[133].x) / 2, (mesh[33].y + mesh[133].y) / 2)
        val l = if (mesh.size > 473) mesh[473] else Pt((mesh[263].x + mesh[362].x) / 2, (mesh[263].y + mesh[362].y) / 2)
        val ipd = d(r, l).coerceAtLeast(1e-5f)
        val ex = Pt((l.x - r.x) / ipd, (l.y - r.y) / ipd)
        val up = Pt(ex.y, -ex.x).let { if (it.y > 0) Pt(-it.x, -it.y) else it }
        val o = Pt((r.x + l.x) / 2, (r.y + l.y) / 2)
        fun u(p: Pt) = (p.x - o.x) * ex.x + (p.y - o.y) * ex.y
        fun v(p: Pt) = (p.x - o.x) * up.x + (p.y - o.y) * up.y
    }

    /** Contorno del bermellón superior / inferior (borde externo + borde interno). */
    fun upperLipPoly(mesh: List<Pt>) = LIP_UP_OUT.map { mesh[it] } + LIP_UP_IN.map { mesh[it] }
    fun lowerLipPoly(mesh: List<Pt>) = LIP_LO_OUT.map { mesh[it] } + LIP_LO_IN.map { mesh[it] }

    fun upperLip(mesh: List<Pt>, mm: Float, scale: String) = LineMetrics("Labio superior", scale = scale, features = listOf(
        "Ancho (comisuras), mm" to d(mesh[61], mesh[291]) * mm,
        "Alto en el centro, mm" to d(mesh[0], mesh[13]) * mm,
        "Área del bermellón, mm²" to LineMeasure.area(upperLipPoly(mesh)) * mm * mm,
        "Filtrum (subnasal → labio), mm" to d(mesh[2], mesh[0]) * mm,
        // arco de Cupido: picos (37, 267) frente al centro (0), hacia arriba
        "Arco de Cupido (pico − centro), mm" to ((d(mesh[37], mesh[17]) + d(mesh[267], mesh[17])) / 2 - d(mesh[0], mesh[17])) * mm,
    ))

    fun lowerLip(mesh: List<Pt>, mm: Float, scale: String): LineMetrics {
        val hu = d(mesh[0], mesh[13]); val hl = d(mesh[14], mesh[17])
        return LineMetrics("Labio inferior", scale = scale, features = listOf(
            "Alto en el centro, mm" to hl * mm,
            "Área del bermellón, mm²" to LineMeasure.area(lowerLipPoly(mesh)) * mm * mm,
            "Proporción inferior / superior" to (if (hu > 1e-5f) hl / hu else 0f),
        ))
    }

    fun eye(mesh: List<Pt>, right: Boolean, mm: Float, scale: String): LineMetrics {
        val a = Axes(mesh)
        val (inner, outer, top, bottom) = if (right) listOf(133, 33, 159, 145) else listOf(362, 263, 386, 374)
        // inclinación cantal: canto externo sobre el interno (en el eje de la cara)
        val du = kotlin.math.abs(a.u(mesh[outer]) - a.u(mesh[inner])); val dv = a.v(mesh[outer]) - a.v(mesh[inner])
        return LineMetrics("Ojo ${if (right) "derecho" else "izquierdo"}", scale = scale, features = listOf(
            "Ancho hendidura, mm" to d(mesh[inner], mesh[outer]) * mm,
            "Alto hendidura, mm" to d(mesh[top], mesh[bottom]) * mm,
            "Inclinación cantal, °" to Math.toDegrees(atan2(dv.toDouble(), du.toDouble())).toFloat(),
        ))
    }

    fun brow(mesh: List<Pt>, right: Boolean, mm: Float, scale: String): LineMetrics {
        val a = Axes(mesh)
        val top = if (right) intArrayOf(70, 63, 105, 66, 107) else intArrayOf(300, 293, 334, 296, 336)
        val pupil = if (right) a.r else a.l
        val pts = top.map { mesh[it] }
        val peak = pts.maxOf { a.v(it) }
        return LineMetrics("Ceja ${if (right) "derecha" else "izquierda"}", scale = scale, features = listOf(
            "Longitud, mm" to LineMeasure.length(pts) * mm,
            "Altura sobre la pupila, mm" to (peak - a.v(pupil)) * mm,
        ))
    }

    fun nose(mesh: List<Pt>, mm: Float, scale: String) = LineMetrics("Nariz", scale = scale, features = listOf(
        "Ancho alar, mm" to d(mesh[98], mesh[327]) * mm,
        "Alto (entre los ojos → subnasal), mm" to d(mesh[168], mesh[2]) * mm,
    ))

    fun contour(mesh: List<Pt>, mm: Float, scale: String): LineMetrics {
        val a = Axes(mesh)
        val browTop = intArrayOf(107, 336).maxOf { a.v(mesh[it]) }
        return LineMetrics("Contorno facial", scale = scale, features = listOf(
            "Ancho de la cara, mm" to d(mesh[234], mesh[454]) * mm,
            "Tercio medio (cejas → subnasal), mm" to (browTop - a.v(mesh[2])) * mm,
            "Tercio inferior (subnasal → mentón), mm" to (a.v(mesh[2]) - a.v(mesh[152])) * mm,
        ))
    }

    /**
     * Altura de la frente (cejas → línea del pelo) en el centro, sobre el pico y la cola de cada ceja,
     * y entradas temporales (cola − centro: positivo = el pelo retrocede más en las sienes).
     */
    fun hairline(mesh: List<Pt>, hair: List<Pt>, mm: Float, scale: String): LineMetrics? {
        if (hair.size < 3) return null
        val a = Axes(mesh)
        val h = hair.map { a.u(it) to a.v(it) }.sortedBy { it.first }
        fun hairAt(u: Float): Float? {
            if (u < h.first().first || u > h.last().first) return null
            for (i in 1 until h.size) if (u <= h[i].first) {
                val (u0, v0) = h[i - 1]; val (u1, v1) = h[i]
                val t = if (u1 - u0 < 1e-6f) 0f else (u - u0) / (u1 - u0)
                return v0 + (v1 - v0) * t
            }
            return null
        }
        fun height(browIdx: Int, at: Pt = mesh[browIdx]) = hairAt(a.u(at))?.let { (it - a.v(mesh[browIdx])) * mm }
        val midBrow = Pt((mesh[107].x + mesh[336].x) / 2, (mesh[107].y + mesh[336].y) / 2)
        val center = hairAt(a.u(midBrow))?.let { (it - maxOf(a.v(mesh[107]), a.v(mesh[336]))) * mm }
        val peakR = height(105); val peakL = height(334); val tailR = height(70); val tailL = height(300)
        val out = listOfNotNull(
            center?.let { "Frente central (cejas → pelo), mm" to it },
            peakR?.let { "Sobre la ceja derecha, mm" to it },
            peakL?.let { "Sobre la ceja izquierda, mm" to it },
            tailR?.let { "Sobre la cola de la ceja derecha, mm" to it },
            tailL?.let { "Sobre la cola de la ceja izquierda, mm" to it },
            if (center != null && tailR != null) "Entrada temporal derecha, mm" to (tailR - center) else null,
            if (center != null && tailL != null) "Entrada temporal izquierda, mm" to (tailL - center) else null,
            center?.let { "Frente central / interpupilar" to it / (a.ipd * mm) },
        )
        return if (out.isEmpty()) null else LineMetrics("Línea del pelo", scale = scale, features = out)
    }
}
