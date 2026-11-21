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
 * - **cejas**: longitud y altura sobre la pupila (en la vertical de la pupila hasta el borde superior);
 * - **nariz**: ancho alar, ancho del puente (raíz, a la altura de los cantos internos) y del dorso (a
 *   media altura), longitud del dorso (raíz → punta) y alto (entre los ojos → subnasal);
 * - **contorno**: ancho bicigomático aproximado (234 ↔ 454), tercio medio (cejas → subnasal) y
 *   tercio inferior (subnasal → mentón);
 * - **línea del pelo**: altura de la frente (cejas → pelo) en líneas rectas perpendiculares a la línea
 *   de los ojos: en el centro, en la vertical de cada pupila (el centro de la ceja lo marca el del ojo)
 *   y en la de cada canto externo (cola), y entradas temporales (cola − centro).
 * Cada medida lleva su segmento ([MeasureGuide]) para dibujarlo punteado al seleccionarla.
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
        /** Punto de coordenadas de la cara (u, v). */
        fun at(u: Float, v: Float) = Pt(o.x + ex.x * u + up.x * v, o.y + ex.y * u + up.y * v)
    }

    /** Contorno del bermellón superior / inferior (borde externo + borde interno). */
    fun upperLipPoly(mesh: List<Pt>) = LIP_UP_OUT.map { mesh[it] } + LIP_UP_IN.map { mesh[it] }
    fun lowerLipPoly(mesh: List<Pt>) = LIP_LO_OUT.map { mesh[it] } + LIP_LO_IN.map { mesh[it] }

    /** Medidas de un rasgo con sus segmentos. */
    class Measured(val metrics: LineMetrics, val guides: List<MeasureGuide>)

    /** Texto de una distancia para la guía (p. ej. «ancho 34,2 mm»). */
    fun mmText(label: String, v: Float) = "$label " + String.format(java.util.Locale.getDefault(), "%.1f mm", v)

    private class B(val mm: Float) {
        val f = ArrayList<Pair<String, Float>>(); val g = ArrayList<MeasureGuide>()
        /** Distancia [a]–[b] como medida [label] y su segmento con el texto [short]. */
        fun dist(label: String, short: String, a: Pt, b: Pt) { val v = d(a, b) * mm; f += label to v; g += MeasureGuide(mmText(short, v), a, b) }
        fun value(label: String, v: Float) { f += label to v }
        fun guide(short: String, v: Float, a: Pt, b: Pt) { g += MeasureGuide(mmText(short, v), a, b) }
        fun build(name: String, scale: String) = Measured(LineMetrics(name, scale = scale, features = f.toList()), g.toList())
    }

    fun upperLipM(mesh: List<Pt>, mm: Float, scale: String) = B(mm).run {
        dist("Ancho (comisuras), mm", "ancho", mesh[61], mesh[291])
        dist("Alto en el centro, mm", "alto", mesh[0], mesh[13])
        value("Área del bermellón, mm²", LineMeasure.area(upperLipPoly(mesh)) * mm * mm)
        dist("Filtrum (subnasal → labio), mm", "filtrum", mesh[2], mesh[0])
        // arco de Cupido: picos (37, 267) frente al centro (0), hacia arriba
        value("Arco de Cupido (pico − centro), mm", ((d(mesh[37], mesh[17]) + d(mesh[267], mesh[17])) / 2 - d(mesh[0], mesh[17])) * mm)
        build("Labio superior", scale)
    }

    fun lowerLipM(mesh: List<Pt>, mm: Float, scale: String) = B(mm).run {
        val hu = d(mesh[0], mesh[13]); val hl = d(mesh[14], mesh[17])
        dist("Alto en el centro, mm", "alto", mesh[14], mesh[17])
        value("Área del bermellón, mm²", LineMeasure.area(lowerLipPoly(mesh)) * mm * mm)
        value("Proporción inferior / superior", if (hu > 1e-5f) hl / hu else 0f)
        build("Labio inferior", scale)
    }

    fun eyeM(mesh: List<Pt>, right: Boolean, mm: Float, scale: String) = B(mm).run {
        val a = Axes(mesh)
        val (inner, outer, top, bottom) = if (right) listOf(133, 33, 159, 145) else listOf(362, 263, 386, 374)
        dist("Ancho hendidura, mm", "ancho", mesh[inner], mesh[outer])
        dist("Alto hendidura, mm", "alto", mesh[top], mesh[bottom])
        // inclinación cantal: canto externo sobre el interno (en el eje de la cara)
        val du = kotlin.math.abs(a.u(mesh[outer]) - a.u(mesh[inner])); val dv = a.v(mesh[outer]) - a.v(mesh[inner])
        value("Inclinación cantal, °", Math.toDegrees(atan2(dv.toDouble(), du.toDouble())).toFloat())
        build("Ojo ${if (right) "derecho" else "izquierdo"}", scale)
    }

    private val BROW_TOP_R = intArrayOf(70, 63, 105, 66, 107)
    private val BROW_TOP_L = intArrayOf(300, 293, 334, 296, 336)

    /** Borde superior de las dos cejas (u, v en el sistema de la cara), ordenado a lo largo de los ojos. */
    private fun browTop(mesh: List<Pt>, a: Axes) = (BROW_TOP_R + BROW_TOP_L).map { a.u(mesh[it]) to a.v(mesh[it]) }.sortedBy { it.first }

    /** v de la polilínea [h] (ordenada por u) en [u], o null fuera de su extensión. */
    private fun vAt(h: List<Pair<Float, Float>>, u: Float): Float? {
        if (h.size < 2 || u < h.first().first || u > h.last().first) return null
        for (i in 1 until h.size) if (u <= h[i].first) {
            val (u0, v0) = h[i - 1]; val (u1, v1) = h[i]
            val t = if (u1 - u0 < 1e-6f) 0f else (u - u0) / (u1 - u0)
            return v0 + (v1 - v0) * t
        }
        return null
    }

    fun browM(mesh: List<Pt>, right: Boolean, mm: Float, scale: String) = B(mm).run {
        val a = Axes(mesh)
        val pts = (if (right) BROW_TOP_R else BROW_TOP_L).map { mesh[it] }
        val pupil = if (right) a.r else a.l
        value("Longitud, mm", LineMeasure.length(pts) * mm)
        // altura en la vertical de la pupila (el centro de la ceja lo marca el del ojo)
        val side = pts.map { a.u(it) to a.v(it) }.sortedBy { it.first }
        vAt(side, a.u(pupil))?.let { vb ->
            val h = (vb - a.v(pupil)) * mm
            value("Altura sobre la pupila, mm", h)
            guide("altura", h, pupil, a.at(a.u(pupil), vb))
        }
        build("Ceja ${if (right) "derecha" else "izquierda"}", scale)
    }

    fun noseM(mesh: List<Pt>, mm: Float, scale: String) = B(mm).run {
        dist("Ancho alar, mm", "ancho alar", mesh[98], mesh[327])
        dist("Ancho del puente (raíz), mm", "puente", mesh[122], mesh[351])
        dist("Ancho del dorso (mitad), mm", "dorso", mesh[196], mesh[419])
        dist("Longitud del dorso (raíz → punta), mm", "longitud", mesh[6], mesh[1])
        dist("Alto (entre los ojos → subnasal), mm", "alto", mesh[168], mesh[2])
        build("Nariz", scale)
    }

    fun contourM(mesh: List<Pt>, mm: Float, scale: String) = B(mm).run {
        val a = Axes(mesh)
        dist("Ancho de la cara, mm", "ancho", mesh[234], mesh[454])
        // tercios en la línea media, perpendiculares a la línea de los ojos
        val uc = a.u(mesh[2])
        val vb = vAt(browTop(mesh, a), a.u(a.o)) ?: intArrayOf(107, 336).maxOf { a.v(mesh[it]) }
        val mid = (vb - a.v(mesh[2])) * mm; val low = (a.v(mesh[2]) - a.v(mesh[152])) * mm
        value("Tercio medio (cejas → subnasal), mm", mid); guide("tercio medio", mid, a.at(uc, vb), mesh[2])
        value("Tercio inferior (subnasal → mentón), mm", low); guide("tercio inferior", low, mesh[2], a.at(uc, a.v(mesh[152])))
        build("Contorno facial", scale)
    }

    /**
     * Altura de la frente (cejas → línea del pelo) en líneas rectas perpendiculares a la línea de los
     * ojos: en el centro (entre las pupilas), en la vertical de cada pupila y en la de cada canto
     * externo (cola de la ceja); entradas temporales (cola − centro: positivo = el pelo retrocede más en
     * las sienes). Cada una con su segmento: pupila → ceja → pelo.
     */
    fun hairlineM(mesh: List<Pt>, hair: List<Pt>, mm: Float, scale: String): Measured? {
        if (hair.size < 3) return null
        val a = Axes(mesh)
        val h = hair.map { a.u(it) to a.v(it) }.sortedBy { it.first }
        val brow = browTop(mesh, a)
        val b = B(mm)
        /** Altura en la vertical de [from] (pupila, centro o canto): ceja → pelo, con sus segmentos. */
        fun column(from: Pt, short: String): Float? {
            val u = a.u(from)
            val vb = vAt(brow, u) ?: return null; val vh = vAt(h, u) ?: return null
            val v = (vh - vb) * mm
            if (v <= 0f) return null
            b.g += MeasureGuide("", from, a.at(u, vb))           // pupila → ceja (sin texto)
            b.guide(short, v, a.at(u, vb), a.at(u, vh))           // ceja → pelo
            return v
        }
        val center = column(a.o, "frente")
        val pr = column(a.r, "frente der."); val pl = column(a.l, "frente izq.")
        val tr = column(mesh[33], "cola der."); val tl = column(mesh[263], "cola izq.")
        center?.let { b.value("Frente central (cejas → pelo), mm", it) }
        pr?.let { b.value("Sobre la ceja derecha (vertical de la pupila), mm", it) }
        pl?.let { b.value("Sobre la ceja izquierda (vertical de la pupila), mm", it) }
        tr?.let { b.value("Sobre la cola de la ceja derecha (canto externo), mm", it) }
        tl?.let { b.value("Sobre la cola de la ceja izquierda (canto externo), mm", it) }
        if (center != null && tr != null) b.value("Entrada temporal derecha, mm", tr - center)
        if (center != null && tl != null) b.value("Entrada temporal izquierda, mm", tl - center)
        center?.let { b.value("Frente central / interpupilar", it / (a.ipd * mm)) }
        return if (b.f.isEmpty()) null else b.build("Línea del pelo", scale)
    }

    // compatibilidad: solo las medidas
    fun upperLip(mesh: List<Pt>, mm: Float, scale: String) = upperLipM(mesh, mm, scale).metrics
    fun lowerLip(mesh: List<Pt>, mm: Float, scale: String) = lowerLipM(mesh, mm, scale).metrics
    fun eye(mesh: List<Pt>, right: Boolean, mm: Float, scale: String) = eyeM(mesh, right, mm, scale).metrics
    fun brow(mesh: List<Pt>, right: Boolean, mm: Float, scale: String) = browM(mesh, right, mm, scale).metrics
    fun nose(mesh: List<Pt>, mm: Float, scale: String) = noseM(mesh, mm, scale).metrics
    fun contour(mesh: List<Pt>, mm: Float, scale: String) = contourM(mesh, mm, scale).metrics
    fun hairline(mesh: List<Pt>, hair: List<Pt>, mm: Float, scale: String) = hairlineM(mesh, hair, mm, scale)?.metrics
}
