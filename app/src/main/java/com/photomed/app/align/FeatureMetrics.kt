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
 * - **contorno**: ancho bicigomático aproximado (234 ↔ 454) y proporciones faciales: tercios
 *   (pelo → cejas → subnasal → mentón, ideal 33 % cada uno), subdivisión del tercio inferior
 *   (subnasal → estomion : estomion → mentón, ideal 1 : 2), índice facial (nasion → mentón /
 *   bicigomático × 100, tipo según Martin–Saller) y, de frente, quintos faciales (ideal 20 % cada uno)
 *   y ancho mandibular (bigonial aproximado) frente al bicigomático;
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

    /** Etiqueta de la fila de la ficha para una medida de rasgo (sin la unidad, como en metricRows). */
    fun rowKey(label: String) = if (label.isEmpty()) "" else label.substringBeforeLast(",")

    /** Texto de una distancia para la guía (p. ej. «ancho 34,2 mm»). */
    fun mmText(label: String, v: Float) = "$label " + String.format(java.util.Locale.getDefault(), "%.1f mm", v)

    private class B(val mm: Float) {
        val f = ArrayList<Pair<String, Float>>(); val g = ArrayList<MeasureGuide>()
        /** Distancia [a]–[b] como medida [label] y su segmento con el texto [short]. */
        fun dist(label: String, short: String, a: Pt, b: Pt) { val v = d(a, b) * mm; f += label to v; g += MeasureGuide(mmText(short, v), a, b, rowKey(label)) }
        fun value(label: String, v: Float) { f += label to v }
        fun guide(short: String, v: Float, a: Pt, b: Pt, label: String = "") { g += MeasureGuide(mmText(short, v), a, b, rowKey(label)) }
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
            guide("altura", h, pupil, a.at(a.u(pupil), vb), "Altura sobre la pupila, mm")
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

    fun contourM(mesh: List<Pt>, mm: Float, scale: String, hair: List<Pt>? = null) = B(mm).run {
        val a = Axes(mesh)
        dist("Ancho de la cara, mm", "ancho", mesh[234], mesh[454])
        // tercios en la línea media, perpendiculares a la línea de los ojos
        val uc = a.u(mesh[2])
        val vb = vAt(browTop(mesh, a), a.u(a.o)) ?: intArrayOf(107, 336).maxOf { a.v(mesh[it]) }
        val mid = (vb - a.v(mesh[2])) * mm; val low = (a.v(mesh[2]) - a.v(mesh[152])) * mm
        // tercio superior (línea del pelo → cejas), si se ve la línea del pelo
        val up = hair?.takeIf { it.size >= 3 }?.let { h -> vAt(h.map { a.u(it) to a.v(it) }.sortedBy { it.first }, uc) }
            ?.let { vh -> ((vh - vb) * mm).takeIf { it > 0f }?.also { guide("tercio superior", it, a.at(uc, vh), a.at(uc, vb), "Tercio superior (pelo → cejas), mm") } }
        up?.let { value("Tercio superior (pelo → cejas), mm", it) }
        value("Tercio medio (cejas → subnasal), mm", mid); guide("tercio medio", mid, a.at(uc, vb), mesh[2], "Tercio medio (cejas → subnasal), mm")
        value("Tercio inferior (subnasal → mentón), mm", low); guide("tercio inferior", low, mesh[2], a.at(uc, a.v(mesh[152])), "Tercio inferior (subnasal → mentón), mm")
        // proporción de los tercios (ideal: un tercio cada uno, 33 %)
        val total = (up ?: 0f) + mid + low
        if (up != null && total > 0f) {
            value("Tercio superior (ideal 33 %), %", up / total * 100f)
            value("Tercio medio (ideal 33 %), %", mid / total * 100f)
            value("Tercio inferior (ideal 33 %), %", low / total * 100f)
        } else if (low > 0f) value("Tercio medio / inferior (ideal 1)", mid / low)
        // tercio inferior: subnasal → estomion (labio superior) frente a estomion → mentón (ideal 1 : 2)
        val sto = Pt((mesh[13].x + mesh[14].x) / 2, (mesh[13].y + mesh[14].y) / 2)
        val snSto = (a.v(mesh[2]) - a.v(sto)) * mm; val stoMe = (a.v(sto) - a.v(mesh[152])) * mm
        if (snSto > 0f && stoMe > 0f) {
            value("Subnasal → estomion, mm", snSto); guide("labio sup.", snSto, mesh[2], a.at(uc, a.v(sto)), "Subnasal → estomion, mm")
            value("Estomion → mentón, mm", stoMe); guide("labio inf. y mentón", stoMe, a.at(uc, a.v(sto)), a.at(uc, a.v(mesh[152])), "Estomion → mentón, mm")
            value("Estomion → mentón / subnasal → estomion (ideal 2)", stoMe / snSto)
        }
        // índice facial (antropometría): altura nasion → mentón / ancho bicigomático × 100
        val faceW = d(mesh[234], mesh[454]); val faceH = d(mesh[168], mesh[152])
        if (faceW > 1e-5f) {
            guide("altura facial", faceH * mm, mesh[168], mesh[152], "Altura facial (nasion → mentón), mm")
            value("Altura facial (nasion → mentón), mm", faceH * mm)
            value(FACIAL_INDEX, faceH / faceW * 100f)
        }
        // de frente: quintos (ancho de cada ojo, entre los ojos y de cada canto externo al borde) y mandíbula
        if (frontal(mesh)) {
            val vEye = (a.v(mesh[33]) + a.v(mesh[263]) + a.v(mesh[133]) + a.v(mesh[362])) / 4
            val cuts = listOf(mesh[234], mesh[33], mesh[133], mesh[362], mesh[263], mesh[454]).map { a.u(it) }.sorted()
            val wTot = (cuts.last() - cuts.first()).coerceAtLeast(1e-5f)
            listOf("Quinto 1 (borde → canto externo)", "Quinto 2 (ojo)", "Quinto 3 (entre los ojos)", "Quinto 4 (ojo)", "Quinto 5 (canto externo → borde)")
                .forEachIndexed { i, name ->
                    val w = cuts[i + 1] - cuts[i]
                    value("$name (ideal 20 %), %", w / wTot * 100f)
                    guide("quinto ${i + 1}", w * mm, a.at(cuts[i], vEye), a.at(cuts[i + 1], vEye), "$name (ideal 20 %), %")
                }
            // ancho mandibular (bigonial aproximado: ángulo de la mandíbula en la malla) frente al bicigomático
            val big = d(mesh[172], mesh[397])
            guide("bigonial", big * mm, mesh[172], mesh[397], "Ancho mandibular (bigonial aprox.), mm")
            value("Ancho mandibular (bigonial aprox.), mm", big * mm)
            if (faceW > 1e-5f) value("Bigonial / bicigomático, %", big / faceW * 100f)
        }
        build("Contorno facial", scale)
    }

    /** ¿Vista de frente? (la punta nasal equidista de los dos bordes de la cara, ± 20 %). */
    private fun frontal(mesh: List<Pt>): Boolean {
        val r = d(mesh[1], mesh[234]); val l = d(mesh[1], mesh[454])
        return minOf(r, l) / maxOf(r, l).coerceAtLeast(1e-6f) >= 0.8f
    }

    /** Etiqueta del índice facial (el valor se muestra con su tipo, ver [facialType]). */
    const val FACIAL_INDEX = "Índice facial aprox. (nasion → mentón / ancho facial × 100)"

    /** Tipo facial por el índice facial (clasificación de Martin–Saller, sin distinción por sexo). */
    fun facialType(i: Float) = when {
        i < 80f -> "hipereuriprosopo (muy ancho)"
        i < 85f -> "euriprosopo (ancho)"
        i < 90f -> "mesoprosopo (medio)"
        i < 95f -> "leptoprosopo (largo)"
        else -> "hiperleptoprosopo (muy largo)"
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
        fun column(from: Pt, short: String, label: String): Float? {
            val u = a.u(from)
            val vb = vAt(brow, u) ?: return null; val vh = vAt(h, u) ?: return null
            val v = (vh - vb) * mm
            if (v <= 0f) return null
            b.g += MeasureGuide("", from, a.at(u, vb))           // pupila → ceja (sin texto)
            b.guide(short, v, a.at(u, vb), a.at(u, vh), label)    // ceja → pelo
            return v
        }
        val center = column(a.o, "frente", "Frente central (cejas → pelo), mm")
        val pr = column(a.r, "frente der.", "Sobre la ceja derecha (vertical de la pupila), mm")
        val pl = column(a.l, "frente izq.", "Sobre la ceja izquierda (vertical de la pupila), mm")
        val tr = column(mesh[33], "cola der.", "Sobre la cola de la ceja derecha (canto externo), mm")
        val tl = column(mesh[263], "cola izq.", "Sobre la cola de la ceja izquierda (canto externo), mm")
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
