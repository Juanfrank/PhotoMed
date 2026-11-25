package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import com.photomed.app.data.Zone
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trData
import com.photomed.app.i18n.trn
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Capas de anotación estética que se pueden mostrar sobre una foto (visor y comparar). */
enum class AnnotLayer(private val es: String) {
    CONTOUR(trn("Contorno")), HAIRLINE(trn("Línea del pelo")), EYES(trn("Ojos y cejas")), NOSE(trn("Nariz")), LIPS(trn("Labios")), FOLDS(trn("Surcos")),
    FOREHEAD(trn("Frente")), GLABELLA(trn("Ceño")), CROWS_FEET(trn("Patas de gallo")), DARK_CIRCLES(trn("Ojeras")), EYE_BAGS(trn("Bolsas palpebrales")),
    JAWLINE(trn("Línea mandibular")), PAPADA(trn("Papada")),
    INFRAORBITAL(trn("Líneas infraorbitarias")), PERIORAL(trn("Líneas periorales")),
    // diagrama del cuerpo (manos, pies, tronco y miembros: ver BodyDiagram)
    BODY_AXES(trn("Ejes y articulaciones")), FINGERS(trn("Dedos")), NAILS(trn("Uñas")), BODY_POINTS(trn("Puntos de referencia")),
    // cuero cabelludo (ver HairDensity)
    SCALP_MAP(trn("Cuero cabelludo visible")), HAIR_PART(trn("Franja de la raya")),
    // hallazgos de la piel, orientativos (ver SkinLesions)
    SPOTS(trn("Manchas")), SPIDER_VEINS(trn("Arañitas")), VARICES(trn("Várices")), CELLULITE(trn("Celulitis"));

    val label: String get() = tr(es)
}

/** Una línea de anotación (x del ancho, y en unidades del ancho). [estimated]: posición estimada, no medida. */
data class AnnotLine(val layer: AnnotLayer, val points: List<Pt>, val closed: Boolean, val estimated: Boolean = false,
                     /** Zona (relleno translúcido) en lugar de solo trazo, p. ej. ojeras. */
                     val filled: Boolean = false,
                     /** Medidas orientativas (longitud, ancho, profundidad, grado; color en ojeras). */
                     val metrics: LineMetrics? = null,
                     /** Segmentos de lo que se mide (ancho, alto, distancias): se ven, punteados, al seleccionar la línea. */
                     val guides: List<MeasureGuide> = emptyList()) {
    /** La misma línea con sus puntos y guías llevados por [f] (otro encuadre, perspectiva…). */
    fun mapped(f: (Pt) -> Pt) = copy(points = points.map(f), guides = guides.map { it.copy(a = f(it.a), b = f(it.b)) })
}

/**
 * Segmento medido de una línea: de [a] a [b] (mismas coordenadas que la línea). [label]: la etiqueta de
 * la medida en [LineMetrics] (rasgo, «Longitud», «Ancho»…), para mostrar su valor junto al segmento.
 */
data class MeasureGuide(val label: String, val a: Pt, val b: Pt,
                        /** Fila de la ficha que representa (etiqueta de [metricRows]); vacía si no tiene. */
                        val key: String = "")

/**
 * Líneas estéticas a partir de los puntos del rostro (iguales con ML Kit y MediaPipe), la
 * segmentación pelo / piel y la luminancia de la foto:
 * - contorno: óvalo ajustado al límite real de la cara (o silueta del sujeto en zonas corporales);
 * - línea del pelo: implantación frontal;
 * - ojos (párpados) y cejas; nariz (dorso lateral, alas y base); labios (bermellón y línea de cierre);
 * - surcos: nasogenianos, líneas de marioneta y valle lagrimal. No los da ningún detector: se
 *   estiman con la geometría del rostro y se ajustan al valle de sombra real de la piel (mínimo de
 *   luminancia perpendicular a la línea) si hay contraste suficiente; si no, se marcan como estimados.
 */
object FaceAnnotations {
    fun face(points: Map<String, Pt>, labels: FaceLabels?, lum: GrayImage?, lab: LabImage? = null, info: MutableMap<String, Float>? = null): List<AnnotLine> {
        var p = FaceKeys.canonical(points)
        if (labels != null) p = HairSkin.snapOval(labels, p)
        val out = ArrayList<AnnotLine>()
        fun g(prefix: String, n: Int) = (0 until n).mapNotNull { p["$prefix$it"] }.takeIf { it.size == n }
        g("oval_", 36)?.let { out += AnnotLine(AnnotLayer.CONTOUR, it, true) }
        labels?.let { HairSkin.hairline(it, p)?.points?.let { h -> out += AnnotLine(AnnotLayer.HAIRLINE, h, false) } }
        for (e in listOf("eye_l_", "eye_r_")) g(e, 16)?.let { out += AnnotLine(AnnotLayer.EYES, it, true) }
        for (b in listOf("brow_l_", "brow_r_")) {
            val top = g("${b}top_", 5); val bottom = g("${b}bottom_", 5)
            if (top != null && bottom != null) out += AnnotLine(AnnotLayer.EYES, top + bottom.reversed(), true)
        }
        val el = p[Landmarks.EYE_L]; val er = p[Landmarks.EYE_R]
        if (el == null || er == null) return out
        val ipd = dist(el, er).takeIf { it > 1e-4f } ?: return out
        val ex = Pt((el.x - er.x) / ipd, (el.y - er.y) / ipd)            // hacia la derecha de la imagen
        val up = Pt(ex.y, -ex.x).let { if (it.y > 0) Pt(-it.x, -it.y) else it }
        fun at(o: Pt, dx: Float, dy: Float) = Pt(o.x + ex.x * dx * ipd + up.x * dy * ipd, o.y + ex.y * dx * ipd + up.y * dy * ipd)
        // nariz
        val nasion = p["nose_bridge_0"]; val tip = p["nose_bridge_1"]
        val alaL = p["nose_bottom_0"]; val sub = p["nose_bottom_1"]; val alaR = p["nose_bottom_2"]
        if (nasion != null && alaL != null && alaR != null && sub != null) {
            val (aL, aR) = if (alaL.x <= alaR.x) alaL to alaR else alaR to alaL
            out += AnnotLine(AnnotLayer.NOSE, curve(at(nasion, -0.12f, -0.02f), at(lerp(nasion, aL, 0.6f), -0.06f, 0f), at(aL, 0.02f, 0.08f)), false)
            out += AnnotLine(AnnotLayer.NOSE, curve(at(nasion, 0.12f, -0.02f), at(lerp(nasion, aR, 0.6f), 0.06f, 0f), at(aR, -0.02f, 0.08f)), false)
            out += AnnotLine(AnnotLayer.NOSE, curve(at(aL, 0f, 0.03f), at(aL, 0.02f, -0.04f), sub, at(aR, -0.02f, -0.04f), at(aR, 0f, 0.03f)), false)
            tip?.let { t -> out += AnnotLine(AnnotLayer.NOSE, curve(at(t, -0.06f, 0.02f), at(t, 0f, -0.02f), at(t, 0.06f, 0.02f)), false) }
        }
        // labios
        val upTop = g("lip_upper_top_", 11); val lowBot = g("lip_lower_bottom_", 9)
        if (upTop != null && lowBot != null) out += AnnotLine(AnnotLayer.LIPS, upTop + lowBot, true)
        g("lip_upper_bottom_", 9)?.let { inner -> if (upTop != null) out += AnnotLine(AnnotLayer.LIPS, listOf(upTop.first()) + inner + upTop.last(), false) }
        // surcos (estimados y ajustados al valle de sombra), con medidas si se ajustaron a la sombra
        // (escala por la distancia interpupilar media; lado según la imagen)
        val scale = (LineMeasure.IPD_MM / ipd) to "interpupilar"
        // barba y bigote (bajo la nariz): el ajuste a la sombra no puede ir al vello oscuro
        val hair = run {
            val nb = p["nose_bottom_1"]; val ov = g("oval_", 36)
            if (lab == null || nb == null || ov == null) null else runCatching {
                val lipsPoly = g("lip_upper_top_", 11)?.let { t -> g("lip_lower_bottom_", 9)?.let { b -> t + b } }
                val cheeks = listOf(Pt(el.x, (el.y + nb.y) / 2), Pt(er.x, (er.y + nb.y) / 2))
                val forehead = at(Pt((el.x + er.x) / 2, (el.y + er.y) / 2), 0f, 0.55f)
                FacialHair.detect(lab, ov, nb.y, lipsPoly, cheeks + forehead, (ov.maxOf { it.x } - ov.minOf { it.x }).coerceAtLeast(ipd * 1.5f))
            }.getOrNull()
        }?.takeIf { it.fraction >= 0.02f }
        hair?.let { info?.put("vello", it.fraction) }
        fun measured(l: AnnotLine, name: String, s: Float): AnnotLine {
            if (l.estimated || lum == null) return l
            val covered = hair?.cover(l.points) ?: 0f
            // casi todo sobre vello: no se mide (sería el borde de la barba, no un surco)
            if (covered > 0.35f) return l.copy(estimated = true)
            return l.copy(metrics = LineMeasure.line("$name (${if (s < 0) "izquierda" else "derecha"} de la imagen)", l.points, lum, scale,
                halfMm = if (name == "Nasogeniano") 5f else 4f).copy(note = if (covered > 0.1f) trn("Parcialmente sobre barba o bigote") else null))
        }
        val oval = g("oval_", 36)
        if (alaL != null && alaR != null && upTop != null) {
            val (aL, aR) = if (alaL.x <= alaR.x) alaL to alaR else alaR to alaL
            val cL = upTop.first(); val cR = upTop.last()
            for ((ala, corner, s) in listOf(Triple(aL, cL, -1f), Triple(aR, cR, 1f))) {
                // nasogeniano: del ala, por fuera de la comisura
                val nl = curve(at(ala, s * 0.10f, 0.02f), at(lerp(ala, corner, 0.5f), s * 0.20f, 0f), at(corner, s * 0.13f, -0.10f))
                out += measured(refine(AnnotLayer.FOLDS, nl, lum, ipd, hair), "Nasogeniano", s)
                // línea de marioneta: de la comisura hacia el borde mandibular
                if (oval != null) {
                    val target = at(corner, s * 0.05f, -0.75f)
                    val jaw = oval.minBy { dist(it, target) }
                    val e = lerp(corner, jaw, 0.6f)
                    val ml = curve(at(corner, s * 0.03f, -0.04f), at(lerp(corner, e, 0.5f), s * 0.03f, 0f), e)
                    out += measured(refine(AnnotLayer.FOLDS, ml, lum, ipd, hair), "Marioneta", s)
                }
            }
        }
        // valle lagrimal: del canto interno, bajo el ojo hacia la mejilla
        val eyeR = g("eye_r_", 16); val eyeL = g("eye_l_", 16)
        if (eyeR != null && eyeL != null) {
            val (left, right) = if (eyeR.map { it.x }.average() <= eyeL.map { it.x }.average()) eyeR to eyeL else eyeL to eyeR
            for ((eye, s) in listOf(left to -1f, right to 1f)) {
                val inner = if (s < 0) eye.maxBy { it.x } else eye.minBy { it.x }
                val ctr = Pt(eye.map { it.x }.average().toFloat(), eye.map { it.y }.average().toFloat())
                val tt = curve(at(inner, 0f, -0.10f), at(ctr, -s * 0.05f, -0.30f), at(ctr, s * 0.22f, -0.36f))
                out += measured(refine(AnnotLayer.FOLDS, tt, lum, ipd), "Valle lagrimal", s)
            }
        }
        return out
    }

    // Topología de la malla de MediaPipe (478 puntos; conexiones oficiales FACE_LANDMARKS_*)
    private val EYE_R = intArrayOf(33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7)
    private val EYE_L = intArrayOf(362, 398, 384, 385, 386, 387, 388, 466, 263, 249, 390, 373, 374, 380, 381, 382)
    private val BROW_R = intArrayOf(70, 63, 105, 66, 107, 55, 65, 52, 53, 46)
    private val BROW_L = intArrayOf(300, 293, 334, 296, 336, 285, 295, 282, 283, 276)
    // Nariz con trazado estético: líneas estéticas del dorso (de la cabeza de la ceja a los puntos
    // de definición de la punta), borde de cada ala hasta el subnasal y arco de la punta
    private val NOSE_DORSAL_R = intArrayOf(55, 193, 122, 196, 3, 51, 45)
    private val NOSE_DORSAL_L = intArrayOf(285, 417, 351, 419, 248, 281, 275)
    private val NOSE_ALA_R = intArrayOf(115, 48, 64, 98, 97, 2)
    private val NOSE_ALA_L = intArrayOf(344, 278, 294, 327, 326, 2)
    private val NOSE_TIP = intArrayOf(45, 1, 275)
    private val LIPS_OUTER = intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146)
    private val LIPS_INNER = intArrayOf(78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308, 324, 318, 402, 317, 14, 87, 178, 88, 95)
    /** Pasillos de los surcos (eje por puntos de la malla, a cada lado): nasogeniano, marioneta, valle lagrimal. */
    val FOLD_CORRIDORS: List<Pair<String, IntArray>> = listOf(
        // nasogeniano: nace en el pliegue alar-facial (129 / 358) y baja hasta el lateral de la comisura (57 / 287)
        "nasogeniano" to intArrayOf(129, 203, 206, 186, 57), "nasogeniano" to intArrayOf(358, 423, 426, 410, 287),
        "marioneta" to intArrayOf(43, 202, 210, 169), "marioneta" to intArrayOf(273, 422, 430, 394),
        "valle lagrimal" to intArrayOf(128, 121, 120, 119), "valle lagrimal" to intArrayOf(357, 350, 349, 348),
    )

    /** Claves de los grupos (como ML Kit) a partir de la malla, para óvalo, línea del pelo y puntos. */
    fun keysFromMesh(mesh: List<Pt>): Map<String, Pt> {
        val out = LinkedHashMap<String, Pt>()
        for ((g, idx) in FaceMeshMap.GROUPS) idx.forEachIndexed { k, i -> mesh.getOrNull(i)?.let { out["$g$k"] = it } }
        for ((k, i) in FaceMeshMap.SINGLE) mesh.getOrNull(i)?.let { out[k] = it }
        return out
    }

    /**
     * Líneas a partir de la malla completa de MediaPipe (478 puntos): ojos, cejas, nariz (dorso y
     * contorno de las alas) y labios con su topología real; surcos detectados en la imagen
     * ([RidgeTracker]) dentro de pasillos definidos por la malla, solo si se encuentran.
     */
    fun fromMesh(meshIn: List<Pt>, labels: FaceLabels?, lum: GrayImage?, lab: LabImage? = null, extras: MeasureExtras? = null,
                 /** Datos del análisis para el aviso al usuario (p. ej. "vello": fracción con barba o bigote). */
                 info: MutableMap<String, Float>? = null,
                 /** Arrugas y surcos de fotos anteriores del paciente (coordenadas de la cara), como referencia. */
                 priors: List<PriorLine> = emptyList(),
                 /** Trazos añadidos a mano (mismas coordenadas que [mesh]). */
                 added: List<List<Pt>> = emptyList(),
                 /** Corrección de las alturas de la frente por el cabeceo ([FeatureMetrics.hairTiltScale]). */
                 hairVScale: Float = 1f): List<AnnotLine> {
        if (meshIn.size < 468) return face(keysFromMesh(meshIn), labels, lum)
        // un trazo a mano: solo su medida (antes se repetía el análisis completo de arrugas para medir una línea)
        if (extras?.onlyAdded == true) {
            val sc = scaleOf(meshIn, extras) ?: return emptyList()
            val l = lum ?: return emptyList()
            return added.mapIndexedNotNull { i, pts ->
                if (pts.size < 2) null else AnnotLine(AnnotLayer.FOLDS, pts, false, metrics = LineMeasure.line("Surco añadido ${i + 1}", pts, l, sc, halfMm = 4f, relief = extras.relief)
                    .copy(note = trn("Trazado a mano en una zona elegida"))).let { withWidthGuide(it, sc.first) }
            }
        }
        // cejas ajustadas a su borde en la imagen (la malla a veces las deja arriba o abajo del pelo)
        val mesh = if (lum != null) runCatching { BrowRefine.refineMesh(meshIn, lum, labels) }.getOrDefault(meshIn) else meshIn
        val keys = FaceKeys.canonical(keysFromMesh(mesh))
        val out = ArrayList<AnnotLine>()
        val snapped = if (labels != null) HairSkin.snapOval(labels, keys) else keys
        // mandíbula y mentón: desde la malla, al borde de sombra de la mandíbula (la segmentación lleva el
        // contorno al cuello o al borde de la barba)
        // barba y bigote: una sola vez por foto (contorno, surcos y referencia)
        val hairM by lazy { lab?.let { l -> runCatching { FacialHair.fromMesh(l, mesh) }.getOrNull() } }
        val p = if (lum != null) JawContour.refine(keys, snapped, lum, labels, hairM, lab) else snapped
        // medidas de los rasgos (mm con la escala del iris o del sensor)
        val sc = scaleOf(mesh, extras)
        fun fm(f: (Float, String) -> LineMetrics?): LineMetrics? = sc?.let { runCatching { f(it.first, it.second) }.getOrNull() }
        /** Línea de un rasgo con sus medidas y los segmentos medidos. */
        fun feat(layer: AnnotLayer, pts: List<Pt>, closed: Boolean, f: (Float, String) -> FeatureMetrics.Measured?): AnnotLine {
            val m = sc?.let { runCatching { f(it.first, it.second) }.getOrNull() }
            return AnnotLine(layer, pts, closed, metrics = m?.metrics, guides = m?.guides.orEmpty())
        }
        // línea del pelo antes que el contorno: da el tercio superior de las proporciones faciales
        val hairPts = labels?.let { runCatching { HairSkin.hairline(it, keys)?.points }.getOrNull() }
        (0 until 36).mapNotNull { p["oval_$it"] }.takeIf { it.size == 36 }?.let {
            out += feat(AnnotLayer.CONTOUR, it, true) { mm, s -> FeatureMetrics.contourM(mesh, mm, s, hairPts) }
        }
        hairPts?.let { h ->
            out += feat(AnnotLayer.HAIRLINE, h, false) { mm, s -> FeatureMetrics.hairlineM(mesh, h, mm, s, hairVScale) }
        }
        fun chain(idx: IntArray) = idx.map { mesh[it] }
        out += feat(AnnotLayer.EYES, chain(EYE_R), true) { mm, s -> FeatureMetrics.eyeM(mesh, true, mm, s) }
        out += feat(AnnotLayer.EYES, chain(EYE_L), true) { mm, s -> FeatureMetrics.eyeM(mesh, false, mm, s) }
        out += feat(AnnotLayer.EYES, chain(BROW_R), true) { mm, s -> FeatureMetrics.browM(mesh, true, mm, s) }
        out += feat(AnnotLayer.EYES, chain(BROW_L), true) { mm, s -> FeatureMetrics.browM(mesh, false, mm, s) }
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_DORSAL_R).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_DORSAL_L).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_ALA_R).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_ALA_L).toTypedArray(), perSeg = 4), false)
        out += feat(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_TIP).toTypedArray(), perSeg = 6), false) { mm, s -> FeatureMetrics.noseM(mesh, mm, s) }
        // labios: bermellón superior e inferior, cada uno con sus medidas
        out += feat(AnnotLayer.LIPS, FeatureMetrics.upperLipPoly(mesh), true) { mm, s -> FeatureMetrics.upperLipM(mesh, mm, s) }
        out += feat(AnnotLayer.LIPS, FeatureMetrics.lowerLipPoly(mesh), true) { mm, s -> FeatureMetrics.lowerLipM(mesh, mm, s) }
        // línea mandibular (nitidez, rectitud, ángulo goníaco) y papada bajo el mentón
        if (lum != null) {
            val ovalPts = (0 until 36).mapNotNull { p["oval_$it"] }
            runCatching { Jawline.front(mesh, ovalPts, lum, sc) }.getOrNull()?.let { out += it }
            val skinJ = labels?.let { l -> { q: Pt -> l[(q.x * l.width).roundToInt(), (q.y * l.width).roundToInt()] == FaceLabels.FACE_SKIN } }
            runCatching { Jawline.papada(mesh, ovalPts, lum, sc, skinJ) }.getOrNull()?.let { out += it }
        }
        if (lum != null) {
            out += folds(mesh, lum, labels, lab, extras, info, hairOf = { hairM })
            out += withReference(mesh, lum, labels, lab, extras, out, priors, added, hairOf = { hairM })
        }
        // arrugas y surcos: el ancho medido, como segmento perpendicular en el centro de la línea
        sc?.let { (mm, _) -> for (i in out.indices) out[i] = withWidthGuide(out[i], mm) }
        return out
    }

    /** Surcos detectados en la imagen (solo los encontrados), con el ancho del pasillo según la cara y sus medidas. */
    fun folds(mesh: List<Pt>, lum: GrayImage, labels: FaceLabels? = null, lab: LabImage? = null, extras: MeasureExtras? = null,
              info: MutableMap<String, Float>? = null,
              /** Barba y bigote ya calculados (si no, se calculan aquí). */
              hairOf: (() -> FacialHair.Result?)? = null): List<AnnotLine> {
        val faceW = dist(mesh[234], mesh[454]).coerceAtLeast(1e-3f)
        // barba y bigote: la segmentación los da como piel; los surcos solo se trazan sobre piel visible
        val hair = if (hairOf != null) hairOf() else lab?.let { runCatching { FacialHair.fromMesh(it, mesh) }.getOrNull() }
        hair?.let { info?.put("vello", it.fraction) }
        val skinLab = labels?.let { l -> { q: Pt -> l[(q.x * l.width).roundToInt(), (q.y * l.width).roundToInt()] == FaceLabels.FACE_SKIN } }
        val skin: ((Pt) -> Boolean)? = if (hair == null || hair.fraction < 0.02f) skinLab
            else { q: Pt -> (skinLab?.invoke(q) ?: true) && !hair[q] }
        val sc = scaleOf(mesh, extras)
        // un surco por núcleo (independientes; mismo orden que en serie)
        val folds = Par.map(FOLD_CORRIDORS.withIndex().toList()) { (i, corridor) -> run foldOne@{
            val (name, idx) = corridor
            val label = "${name.replaceFirstChar { it.uppercase() }} ${if (i % 2 == 0) "derecho" else "izquierdo"}"
            val full = curve(*idx.map { mesh[it] }.toTypedArray(), perSeg = 6)
            // con barba o bigote, el pasillo se acorta a su parte visible ANTES de buscar el surco (si no,
            // el tramo visible no llega al mínimo de longitud del pasillo completo y el surco se descarta
            // aunque se vea); el nasogeniano, desde el ala hasta donde empieza el vello
            val axis = if (hair == null || hair.fraction < 0.02f) full else offHair(full, hair, fromStart = name == "nasogeniano")
            val covered = if (axis === full) 0f else 1f - LineMeasure.length(axis) / LineMeasure.length(full).coerceAtLeast(1e-6f)
            // tramo visible demasiado corto (menos de ~6 mm o del 20 % del recorrido): no hay surco medible
            if (axis.size < 4 || LineMeasure.length(axis) < 0.04f * faceW || covered > 0.8f) return@foldOne null
            val naso = name == "nasogeniano"
            // nasogeniano más sensible (umbrales menores): es el surco clínico de referencia
            RidgeTracker.track(lum, axis, corridorHalf(name) * faceW, stations = if (axis === full) 28 else 20, skin = skin,
                minStrength = if (naso) NASO_MIN_STRENGTH else RidgeTracker.MIN_STRENGTH,
                minContrast = if (naso) NASO_MIN_CONTRAST else RidgeTracker.MIN_CONTRAST,
                anchorStart = naso)?.let { r ->
                // sin colas sobre el vello (el trazado se mueve a los lados del eje)
                val pts0 = hair?.let { offHair(r.points, it, fromStart = name == "nasogeniano") } ?: r.points
                // sin curvas irregulares: suavizado y corte donde el trazado se aparta de la dirección del surco
                val pts = regularize(pts0, axis)
                if (pts.size < 5) return@foldOne null
                val note = if (covered > 0.2f) "Parcialmente cubierto por barba o bigote (${(covered * 100).roundToInt()} % del recorrido): " +
                    "se traza y mide solo la parte visible" else null
                AnnotLine(AnnotLayer.FOLDS, pts, false,
                    metrics = sc?.let { LineMeasure.line(label, pts, lum, it, halfMm = if (name == "nasogeniano") 5f else 4f, relief = extras?.relief).copy(note = note) })
            }
        } }.filterNotNull()
        return folds + wrinkles(mesh, lum, labels, faceW, lab, extras, existing = folds)
    }

    /**
     * Referencia de fotos anteriores: cada arruga o surco que había antes y que ahora no se ha encontrado
     * se busca de nuevo en un pasillo estrecho alrededor de donde estaba (llevado a esta foto con las
     * coordenadas de la cara), con umbrales más bajos; si aparece, se añade con su mismo nombre (así la
     * comparación lo empareja). Además, los trazos añadidos a mano, con sus medidas.
     */
    private fun withReference(mesh: List<Pt>, lum: GrayImage, labels: FaceLabels?, lab: LabImage?, extras: MeasureExtras?,
                              found: List<AnnotLine>, priors: List<PriorLine>, added: List<List<Pt>>,
                              hairOf: (() -> FacialHair.Result?)? = null): List<AnnotLine> {
        val out = ArrayList<AnnotLine>()
        val faceW = dist(mesh[234], mesh[454]).coerceAtLeast(1e-3f)
        val sc = scaleOf(mesh, extras) ?: return out
        val hair = if (hairOf != null) hairOf() else lab?.let { runCatching { FacialHair.fromMesh(it, mesh) }.getOrNull() }
        val skinLab = labels?.let { l -> { q: Pt -> l[(q.x * l.width).roundToInt(), (q.y * l.width).roundToInt()] == FaceLabels.FACE_SKIN } }
        val skin: ((Pt) -> Boolean)? = if (hair == null || hair.fraction < 0.02f) skinLab else { q: Pt -> (skinLab?.invoke(q) ?: true) && !hair[q] }
        val names = found.mapNotNull { it.metrics?.name }.toMutableSet()
        fun meanDist(a: List<Pt>, b: List<Pt>) = a.map { p -> b.minOf { dist(p, it) } }.average().toFloat()
        for (p in priors) {
            val mapped = FaceFrame.fromFace(mesh, p.points) ?: continue
            if (mapped.size < 3) continue
            // ya encontrada (misma capa, cerca)
            if (found.any { it.layer == p.layer && it.points.size >= 2 && meanDist(mapped, it.points) < 0.025f * faceW }) continue
            val r = RidgeTracker.track(lum, mapped, 0.025f * faceW, stations = 20, offsets = 6,
                minStrength = PRIOR_MIN_STRENGTH, minContrast = PRIOR_MIN_CONTRAST, skin = skin) ?: continue
            val name = if (p.name in names) "${p.name} (ref.)" else p.name
            names += name
            val half = if (p.layer == AnnotLayer.FOLDS) 4f else 2f
            out += AnnotLine(p.layer, r.points, false, metrics = LineMeasure.line(name, r.points, lum, sc, halfMm = half, relief = extras?.relief)
                .copy(note = trn("Hallada con la foto anterior como referencia")))
        }
        added.forEachIndexed { i, pts ->
            if (pts.size < 2) return@forEachIndexed
            out += AnnotLine(AnnotLayer.FOLDS, pts, false, metrics = LineMeasure.line("Surco añadido ${i + 1}", pts, lum, sc, halfMm = 4f, relief = extras?.relief)
                .copy(note = trn("Trazado a mano en una zona elegida")))
        }
        return out
    }
    const val PRIOR_MIN_STRENGTH = 0.012f
    const val PRIOR_MIN_CONTRAST = 1.4f

    private class Cand(val res: RidgeTracker.Result, val u0: Float, val u1: Float, val v: Float)

    /**
     * Supresión de no máximos: de los candidatos (ordenados por intensidad × longitud) se conservan los
     * que no se solapan con uno ya elegido (misma posición transversal ±[sep] y solape longitudinal).
     */
    private fun nms(c: List<Cand>, sep: Float, max: Int): List<Cand> {
        val kept = ArrayList<Cand>()
        for (x in c.sortedByDescending { it.res.strength * (it.u1 - it.u0) }) {
            val clash = kept.any { k ->
                val ov = minOf(k.u1, x.u1) - maxOf(k.u0, x.u0)
                kotlin.math.abs(k.v - x.v) < sep && ov > 0.3f * minOf(k.u1 - k.u0, x.u1 - x.u0)
            }
            if (!clash) kept += x
            if (kept.size >= max) break
        }
        return kept
    }

    /**
     * Arrugas de la frente (horizontales, siguiendo el arco de las cejas, enteras o por mitades), del
     * ceño (verticales entre las cejas en abanico de posiciones e inclinaciones, y horizontales en la
     * raíz nasal), patas de gallo (abanico de rayos desde el canto externo) y ojeras y bolsas
     * palpebrales ([Periorbital]). Candidatos múltiples con supresión de no máximos.
     */
    fun wrinkles(mesh: List<Pt>, lumIn: GrayImage, labels: FaceLabels?, faceW: Float, lab: LabImage? = null, extras: MeasureExtras? = null,
                 /** Surcos ya encontrados en sus pasillos (para no repetirlos como accesorios). */
                 existing: List<AnnotLine> = emptyList()): List<AnnotLine> {
        val out = ArrayList<AnnotLine>()
        // luz normalizada para buscar (campo plano con la misma escala de brillo); las medidas usan la original
        val lumRaw = lumIn
        val lum = if (WrinkleTuning.flat) WrinkleField.flatScaled(lumIn, 0.06f * faceW * lumIn.width) else lumIn
        val sc = scaleOf(mesh, extras)
        /** Alargar por los extremos mientras siga la cresta (se define más abajo, con la piel). */
        var extendFn: (RidgeTracker.Result) -> RidgeTracker.Result = { it }
        fun withMetrics(layer: AnnotLayer, name: String, r0: RidgeTracker.Result): AnnotLine {
            val r = if (WrinkleTuning.extend && layer != AnnotLayer.FOLDS) extendFn(r0) else r0
            return AnnotLine(layer, r.points, false, metrics = sc?.let { LineMeasure.line(name, r.points, lumRaw, it, halfMm = 2f, relief = extras?.relief) })
        }
        val skin = labels?.let { l -> { q: Pt -> l[(q.x * l.width).roundToInt(), (q.y * l.width).roundToInt()] == FaceLabels.FACE_SKIN } }
        val eR = mesh[468].takeIf { mesh.size > 473 } ?: mesh[159]; val eL = mesh[473].takeIf { mesh.size > 473 } ?: mesh[386]
        val ipd = dist(eR, eL).coerceAtLeast(1e-4f)
        val ex = Pt((eL.x - eR.x) / ipd, (eL.y - eR.y) / ipd)
        val up = Pt(ex.y, -ex.x).let { if (it.y > 0) Pt(-it.x, -it.y) else it }
        fun along(p: Pt, a: Float, b: Float) = Pt(p.x + ex.x * a + up.x * b, p.y + ex.y * a + up.y * b)
        fun proj(p: Pt, o: Pt) = Pair((p.x - o.x) * ex.x + (p.y - o.y) * ex.y, (p.x - o.x) * up.x + (p.y - o.y) * up.y)
        val mid = Pt((eR.x + eL.x) / 2, (eR.y + eL.y) / 2)
        fun uv(r: RidgeTracker.Result, vertical: Boolean): Cand {
            val q = r.points.map { proj(it, mid) }
            val u = q.map { if (vertical) it.second else it.first }; val v = q.map { if (vertical) it.first else it.second }
            return Cand(r, u.min(), u.max(), v.average().toFloat())
        }
        // cejas ampliadas: fuera de la búsqueda de ceño y patas de gallo (su borde confunde)
        val brows = listOf(BROW_R, BROW_L).map { b -> b.map { mesh[it] }.let { pts -> val c = Pt(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
            pts.map { Pt(c.x + (it.x - c.x) * 1.25f, c.y + (it.y - c.y) * 1.6f) } } }
        val skinNoBrow = { q: Pt -> (skin?.invoke(q) ?: true) && brows.none { inside(q, it) } }
        // ---- detección libre (toda la piel, cualquier orientación) y puntuación de candidatos ----
        val eyes = listOf(EYE_R, EYE_L).map { e -> e.map { mesh[it] }.let { pts -> val c = Pt(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
            pts.map { Pt(c.x + (it.x - c.x) * 1.3f, c.y + (it.y - c.y) * 1.8f) } } }
        val lips = (FeatureMetrics.upperLipPoly(mesh) + FeatureMetrics.lowerLipPoly(mesh)).let { pts ->
            val c = Pt(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
            listOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146).map { mesh[it] }
                .map { Pt(c.x + (it.x - c.x) * 1.08f, c.y + (it.y - c.y) * 1.15f) } }
        fun regionMin(r: String) = when (r) { "frente" -> 0.016f to 1.8f; "ceño" -> 0.022f to 2.0f; "perioral" -> 0.02f to 2.0f; else -> 0.018f to 1.9f }
        /** Puntuación de un candidato en su zona, con la orientación esperada [dir] (null = cualquiera). */
        // mapa de textura (método de FFHQ-Wrinkle) y su media en la piel, para puntuar
        val tex = if (WrinkleTuning.textureW != 0f) WrinkleField.textureMap(lum, 0.0083f * faceW * lum.width) else null
        val texBg = tex?.let { t -> var sum = 0.0; var n = 0
            for (y in 0 until t.height step 3) for (x in 0 until t.width step 3) {
                val q = Pt((x + 0.5f) / t.width, (y + 0.5f) / t.width)
                if (skin?.invoke(q) != false) { sum += t[x, y]; n++ }
            }
            if (n > 0) (sum / n).toFloat() else 0f } ?: 0f
        fun scoreOf(r: RidgeTracker.Result, region: String, dir: Pt?): Float {
            val (ms, mc) = regionMin(region)
            val f = WrinkleField.Features(r.contrast, r.strength, LineMeasure.length(r.points) / faceW,
                dir?.let { WrinkleField.agreement(r.points, it) } ?: 1f, WrinkleField.straightness(r.points),
                tex?.let { WrinkleField.textureRatio(it, r.points, texBg) } ?: 1f)
            return WrinkleField.score(f, ms * WrinkleTuning.k, mc * WrinkleTuning.kc)
        }
        fun accepted(r: RidgeTracker.Result, region: String, dir: Pt?) = scoreOf(r, region, dir) > WrinkleTuning.scoreT
        /** Candidatos libres: más exigentes donde hay bordes que se confunden (pelo y cejas, labios). */
        fun acceptedFree(r: RidgeTracker.Result, region: String, dir: Pt?) =
            scoreOf(r, region, dir) > WrinkleTuning.scoreT + (WrinkleTuning.freeBias[region] ?: 0f)
        val wPx = lum.width
        val free: List<RidgeTracker.Result> = if (!WrinkleTuning.free || skin == null) emptyList() else runCatching {
            val allowed = { x: Int, y: Int ->
                val q = Pt((x + 0.5f) / wPx, (y + 0.5f) / wPx)
                skin(q) && eyes.none { inside(q, it) } && brows.none { inside(q, it) } && !inside(q, lips)
            }
            val cands = WrinkleField.candidates(lum, faceW * wPx, allowed, 0.035f * faceW * wPx)
            Par.map(cands) { pts ->
                val l = LineMeasure.length(pts)
                RidgeTracker.track(lum, pts, 0.008f * faceW, stations = (l / (0.006f * faceW)).toInt().coerceIn(10, 40), offsets = 4,
                    minStrength = 0.008f, minContrast = 1.2f, skin = skinNoBrow)
            }.filterNotNull()
        }.getOrDefault(emptyList())
        fun midOf(r: RidgeTracker.Result) = r.points[r.points.size / 2]
        // alargar: eje prolongado en recta un 35 % por cada extremo; el rastreador conserva solo el tramo
        // continuo con cresta (si no la hay, la línea queda igual)
        extendFn = { r ->
            val p = r.points
            if (p.size < 3) r else {
                val len = LineMeasure.length(p)
                val a = p.first(); val a2 = p[minOf(2, p.size - 1)]; val b = p.last(); val b2 = p[maxOf(0, p.size - 3)]
                fun ext(from: Pt, toward: Pt): Pt { val vx = from.x - toward.x; val vy = from.y - toward.y; val n = hypot(vx, vy).coerceAtLeast(1e-6f)
                    return Pt(from.x + vx / n * len * 0.35f, from.y + vy / n * len * 0.35f) }
                val axis = listOf(ext(a, a2)) + p + ext(b, b2)
                val t = runCatching { RidgeTracker.track(lum, axis, 0.006f * faceW, stations = (len * 1.7f / (0.006f * faceW)).toInt().coerceIn(12, 50),
                    offsets = 3, minStrength = 0.6f * r.strength, minContrast = 1.3f, skin = skinNoBrow) }.getOrNull()
                if (t != null && LineMeasure.length(t.points) > len * 1.05f && t.points.map { q -> p.minOf { dist(q, it) } }.sorted()
                        .let { it[it.size / 2] } < 0.01f * faceW) t else r
            }
        }
        /** Tope de líneas de una zona; por encima, solo las de puntuación alta (C). */
        fun pick(cands: List<Cand>, sep: Float, baseCap: Int, region: String, dir: (Cand) -> Pt?): List<Cand> {
            val cap = WrinkleTuning.cap(baseCap)
            if (WrinkleTuning.strongCapK <= 1f) return nms(cands, sep, cap)
            return nms(cands, sep, (cap * WrinkleTuning.strongCapK).toInt()).filterIndexed { i, c ->
                i < cap || scoreOf(c.res, region, dir(c)) > WrinkleTuning.scoreT + 1f }
        }
        // frente: franjas que siguen el arco de las cejas (que se aplana hacia arriba), enteras o por mitades
        run {
            val browIdx = intArrayOf(70, 63, 105, 66, 107, 336, 296, 334, 293, 300)
            val brow = browIdx.map { proj(mesh[it], mid) }.sortedBy { it.first }
            fun browAt(x: Float): Float {
                if (x <= brow.first().first) return brow.first().second
                for (i in 1 until brow.size) if (x <= brow[i].first) {
                    val a = brow[i - 1]; val b = brow[i]; val t = (x - a.first) / (b.first - a.first).coerceAtLeast(1e-6f)
                    return a.second + (b.second - a.second) * t
                }
                return brow.last().second
            }
            val browTop = brow.maxOf { it.second }
            // borde superior: la malla acaba a media frente; se sube por la piel (segmentación) hasta el
            // pelo, o un margen fijo sin segmentación
            var top = proj(mesh[10], mid).second
            if (skin != null) {
                val x10 = proj(mesh[10], mid).first
                var y = top
                while (y < top + 0.3f * faceW && listOf(-0.12f, 0f, 0.12f).count { skin(along(mid, x10 + it * faceW, y + 0.01f * faceW)) } >= 2) y += 0.005f * faceW
                top = y
            } else top += 0.05f * faceW
            val lo = browTop + 0.03f * faceW; val hi = top - 0.03f * faceW
            val xl = proj(mesh[70], mid).first + 0.01f * faceW; val xr = proj(mesh[300], mid).first - 0.01f * faceW
            val step = 0.03f * faceW
            if (hi - lo > step && xr - xl > 0.2f * faceW) {
                val cands = ArrayList<Cand>()
                val xm = (xl + xr) / 2
                val axes = ArrayList<List<Pt>>()
                for (seg in listOf(xl to xr, xl to xm + 0.04f * faceW, xm - 0.04f * faceW to xr)) {
                    var y = lo
                    while (y <= hi) {
                        // arco de las cejas atenuado con la altura
                        val bend = (1f - (y - lo) / (hi - lo)).coerceIn(0f, 1f) * 0.7f
                        axes += (0..6).map { j ->
                            val x = seg.first + (seg.second - seg.first) * j / 6f
                            along(mid, x, y + (browAt(x) - browTop) * bend)
                        }
                        y += step * 0.4f
                    }
                }
                // un rastreo por franja, en varios núcleos (mismo orden que en serie)
                Par.map(axes) { axis -> RidgeTracker.track(lum, axis, step * 0.55f, stations = 28, offsets = 8, minStrength = 0.016f * WrinkleTuning.k, minContrast = 1.8f * WrinkleTuning.kc, skin = skin) }
                    .forEach { r -> r?.let { if (accepted(it, "frente", ex)) cands += uv(it, vertical = false) } }
                // libres en la frente (encima de las cejas, entre sus colas)
                free.filter { r -> val (u, v) = proj(midOf(r), mid); v > browTop + 0.02f * faceW && v < top && u in xl..xr }
                    .filter { WrinkleField.agreement(it.points, ex) >= WrinkleTuning.foreheadAgree && acceptedFree(it, "frente", ex) }.forEach { cands += uv(it, vertical = false) }
                pick(cands, step * 0.7f, 8, "frente") { ex }.sortedByDescending { it.v }.forEachIndexed { i, c ->
                    out += withMetrics(AnnotLayer.FOREHEAD, "Frente, línea ${i + 1}", c.res)
                }
            }
        }
        // ceño: verticales (corrugador, "líneas del 11") en abanico de posiciones e inclinaciones entre
        // las cabezas de las cejas, y horizontales en la raíz nasal (prócer)
        run {
            val hR = proj(mesh[107], mid); val hL = proj(mesh[336], mid)
            val base = proj(mesh[168], mid).second
            val topY = maxOf(hR.second, hL.second) + 0.08f * faceW
            val len = topY - base
            val cands = ArrayList<Cand>()
            val axes = ArrayList<List<Pt>>()
            for (t in floatArrayOf(0.12f, 0.26f, 0.4f, 0.6f, 0.74f, 0.88f)) for (deg in floatArrayOf(-14f, 0f, 14f)) {
                val x = hR.first + (hL.first - hR.first) * t
                val tilt = kotlin.math.tan(Math.toRadians(deg.toDouble())).toFloat() * len / 2
                axes += listOf(along(mid, x - tilt, base), along(mid, x, (base + topY) / 2), along(mid, x + tilt, topY))
            }
            // fuera del borde de las cejas y solo casi verticales (± 30°)
            val vertOk = { r: RidgeTracker.Result -> WrinkleField.agreement(r.points, up) >= 0.75f }
            Par.map(axes) { axis -> RidgeTracker.track(lum, axis, 0.02f * faceW, stations = 22, offsets = 6, minStrength = 0.022f * WrinkleTuning.k, minContrast = 2.0f * WrinkleTuning.kc, skin = skinNoBrow) }
                .forEach { r -> r?.let { if (vertOk(it) && accepted(it, "ceño", up)) cands += uv(it, vertical = true) } }
            free.filter { r -> val (u, v) = proj(midOf(r), mid); u in minOf(hR.first, hL.first) - 0.01f * faceW..maxOf(hR.first, hL.first) + 0.01f * faceW && v in base..topY }
                .filter { vertOk(it) && acceptedFree(it, "ceño", up) }.forEach { cands += uv(it, vertical = true) }
            pick(cands, 0.045f * faceW, 4, "ceño") { up }.sortedBy { it.v }.forEachIndexed { i, c ->
                out += withMetrics(AnnotLayer.GLABELLA, "Ceño, vertical ${i + 1}", c.res)
            }
            // raíz nasal: horizontales del prócer
            val yN = proj(mesh[168], mid).second; val y6 = proj(mesh[6], mid).second
            val half = (hL.first - hR.first) * 0.32f
            val hc = ArrayList<Cand>()
            Par.map(floatArrayOf(-0.2f, 0.15f, 0.5f).map { f -> yN + (y6 - yN) * f }) { y ->
                val axis = listOf(along(mid, -half, y), along(mid, 0f, y), along(mid, half, y))
                RidgeTracker.track(lum, axis, 0.015f * faceW, stations = 16, offsets = 5, minStrength = 0.025f, minContrast = 2.2f, skin = skin)
            }.forEach { r -> r?.let { hc += uv(it, vertical = false) } }
            nms(hc, 0.03f * faceW, 1).forEach { out += withMetrics(AnnotLayer.GLABELLA, "Ceño, raíz nasal", it.res) }
        }
        // patas de gallo: abanico de rayos desde el canto externo hacia la sien, fuera de las cejas
        for ((side, pair) in listOf("derecha" to (mesh[33] to mid(mesh[33], mesh[133])), "izquierda" to (mesh[263] to mid(mesh[263], mesh[362])))) {
            val (canthus, center) = pair
            val ox = canthus.x - center.x; val oy = canthus.y - center.y; val on = hypot(ox, oy).coerceAtLeast(1e-5f)
            val dx = ox / on; val dy = oy / on
            val cands = ArrayList<Cand>()
            val degs = (-50..50 step 10).toList()
            val rays = Par.map(degs) { deg ->
                val a = Math.toRadians(deg.toDouble()); val s = if (dx > 0) 1f else -1f
                val rx = (dx * kotlin.math.cos(a) - dy * kotlin.math.sin(a) * s).toFloat(); val ry = (dx * kotlin.math.sin(a) * s + dy * kotlin.math.cos(a)).toFloat()
                val p0 = Pt(canthus.x + rx * 0.035f * faceW, canthus.y + ry * 0.035f * faceW)
                val p1 = Pt(canthus.x + rx * 0.16f * faceW, canthus.y + ry * 0.16f * faceW)
                RidgeTracker.track(lum, listOf(p0, Pt((p0.x + p1.x) / 2, (p0.y + p1.y) / 2), p1), 0.016f * faceW, stations = 18, offsets = 5,
                    minStrength = 0.018f * WrinkleTuning.k, minContrast = 1.9f * WrinkleTuning.kc, skin = skinNoBrow)
            }
            /** Coordenadas del rayo: u a lo largo, v = ángulo respecto a la dirección del canto (unidades de cara). */
            fun rayCand(r: RidgeTracker.Result): Cand {
                val dd = r.points.map { hypot(it.x - canthus.x, it.y - canthus.y) }
                val m = midOf(r); val ang = Math.toDegrees(kotlin.math.atan2((dx * (m.y - canthus.y) - dy * (m.x - canthus.x)).toDouble(),
                    (dx * (m.x - canthus.x) + dy * (m.y - canthus.y)).toDouble())).toFloat()
                return Cand(r, dd.min(), dd.max(), ang / 60f * faceW * 0.1f)
            }
            fun radial(r: RidgeTracker.Result) = midOf(r).let { m -> Pt(m.x - canthus.x, m.y - canthus.y) }
            for (ray in rays) ray?.let { r -> if (accepted(r, "patas", radial(r))) cands += rayCand(r) }
            // libres: por fuera del canto, a 0,03–0,2 de cara y no más de 0,15 por encima o debajo
            free.filter { r ->
                val m = midOf(r); val px = m.x - canthus.x; val py = m.y - canthus.y
                val along = px * dx + py * dy; val across = kotlin.math.abs(-px * dy + py * dx)
                // lejos de la cola del ojo (pestañas, delineado): ningún punto a menos de 0,045 de cara del canto
                along > 0.05f * faceW && hypot(px, py) < 0.2f * faceW && across < 0.15f * faceW &&
                    r.points.none { hypot(it.x - canthus.x, it.y - canthus.y) < 0.045f * faceW }
            }.filter { acceptedFree(it, "patas", radial(it)) }.forEach { cands += rayCand(it) }
            // rayos separados al menos ~13° entre sí
            pick(cands, 0.022f * faceW, 4, "patas") { c -> radial(c.res) }.sortedBy { it.v }.forEachIndexed { i, c ->
                out += withMetrics(AnnotLayer.CROWS_FEET, "Pata de gallo $side ${i + 1}", c.res)
            }
        }
        // surcos accesorios junto al nasogeniano y la marioneta (líneas paralelas o ramas que los pasillos no
        // recorren): libres cerca de sus ejes, con la dirección del eje más cercano y sin repetir los ya hallados
        if (WrinkleTuning.cheeks) for ((side, corrs) in listOf("derecho" to listOf(0, 2), "izquierdo" to listOf(1, 3))) {
            val axes = corrs.map { i -> FOLD_CORRIDORS[i].second.map { mesh[it] } }
            fun nearestDir(q: Pt): Pair<Float, Pt> {
                var best = Float.MAX_VALUE; var dir = Pt(0f, 1f)
                for (ax in axes) for (i in 1 until ax.size) {
                    val dd = segPointDist(q, ax[i - 1], ax[i]); if (dd < best) { best = dd; dir = Pt(ax[i].x - ax[i - 1].x, ax[i].y - ax[i - 1].y) }
                }
                return best to dir
            }
            fun meanDist(a: List<Pt>, b: List<Pt>) = a.map { p -> b.minOf { dist(p, it) } }.average().toFloat()
            val taken = existing.filter { it.layer == AnnotLayer.FOLDS }
            val cands = free.mapNotNull { r ->
                val (d, dir) = nearestDir(midOf(r))
                if (d > WrinkleTuning.cheekDist * faceW) return@mapNotNull null
                if (WrinkleField.agreement(r.points, dir) < WrinkleTuning.cheekAgree) return@mapNotNull null
                if (LineMeasure.length(r.points) < WrinkleTuning.cheekLen * faceW) return@mapNotNull null
                if (taken.any { it.points.size >= 2 && meanDist(r.points, it.points) < 0.015f * faceW }) return@mapNotNull null
                if (!acceptedFree(r, "surcos", dir)) return@mapNotNull null
                r to scoreOf(r, "surcos", dir)
            }.sortedByDescending { it.second }
            val kept = ArrayList<RidgeTracker.Result>()
            for ((r, _) in cands) {
                if (kept.size >= WrinkleTuning.cap(3)) break
                if (kept.none { meanDist(r.points, it.points) < 0.015f * faceW }) kept += r
            }
            kept.forEachIndexed { i, r -> out += withMetrics(AnnotLayer.FOLDS, "Surco accesorio $side ${i + 1}", r) }
        }
        // líneas infraorbitarias (bajo el párpado inferior, entre los cantos): casi horizontales
        for ((side, idx) in listOf("derecha" to intArrayOf(133, 33, 145), "izquierda" to intArrayOf(362, 263, 374))) {
            val (inner, outer, lid) = idx.map { proj(mesh[it], mid) }
            val ul = minOf(inner.first, outer.first); val ur = maxOf(inner.first, outer.first)
            val cands = free.filter { r -> val (u, v) = proj(midOf(r), mid); u in ul..ur && v in lid.second - 0.13f * faceW..lid.second - 0.015f * faceW }
                .filter { acceptedFree(it, "infraorbital", ex) }.map { uv(it, vertical = false) }
            pick(cands, 0.02f * faceW, WrinkleTuning.infraCap, "infraorbital") { ex }.sortedByDescending { it.v }.forEachIndexed { i, c ->
                out += withMetrics(AnnotLayer.INFRAORBITAL, "Infraorbitaria $side ${i + 1}", c.res)
            }
        }
        // líneas periorales («código de barras»): verticales sobre el labio superior y bajo el inferior
        run {
            val cl = proj(mesh[61], mid).first; val cr = proj(mesh[291], mid).first
            val ul = minOf(cl, cr) + 0.01f * faceW; val ur = maxOf(cl, cr) - 0.01f * faceW
            val vTop = proj(mesh[0], mid).second; val vSn = proj(mesh[2], mid).second; val vBot = proj(mesh[17], mid).second
            for ((part, range) in listOf("superior" to (vTop + 0.004f * faceW..vSn - 0.008f * faceW), "inferior" to (vBot - 0.08f * faceW..vBot - 0.004f * faceW))) {
                val cands = free.filter { r -> val (u, v) = proj(midOf(r), mid); u in ul..ur && v in range }
                    .filter { WrinkleField.agreement(it.points, up) >= 0.8f && acceptedFree(it, "perioral", up) }.map { uv(it, vertical = true) }
                nms(cands, 0.015f * faceW, WrinkleTuning.cap(if (part == "superior") 6 else 4)).sortedBy { it.v }.forEachIndexed { i, c ->
                    out += withMetrics(AnnotLayer.PERIORAL, "Perioral $part ${i + 1}", c.res)
                }
            }
        }
        // ojeras y bolsas palpebrales bajo cada ojo (ver Periorbital)
        val pm = Periorbital.maps(lumRaw, lab, faceW * lum.width)
        for (right in listOf(true, false)) out += runCatching { Periorbital.analyze(mesh, right, pm, labels, faceW, sc) }.getOrDefault(emptyList())
        return out
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)

    private fun segPointDist(p: Pt, a: Pt, b: Pt): Float {
        val vx = b.x - a.x; val vy = b.y - a.y; val l2 = vx * vx + vy * vy
        val t = if (l2 < 1e-12f) 0f else (((p.x - a.x) * vx + (p.y - a.y) * vy) / l2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
    }

    /**
     * Trazado regular de un surco: media móvil de 5 puntos y corte del final donde la dirección se
     * aparta más de 40° de la del pasillo ([axis]) en dos puntos seguidos (a partir de la mitad del
     * recorrido, para no perder el arranque), y sin ganchos en los extremos.
     */
    fun regularize(pts: List<Pt>, axis: List<Pt>): List<Pt> {
        if (pts.size < 5 || axis.size < 2) return pts
        val sm = pts.indices.map { i ->
            if (i == 0 || i == pts.lastIndex) pts[i] else {
                val r = (maxOf(0, i - 2)..minOf(pts.lastIndex, i + 2)).map { pts[it] }
                Pt(r.map { it.x }.average().toFloat(), r.map { it.y }.average().toFloat())
            }
        }
        fun axisDir(q: Pt): Pt {
            var bi = 0; var bd = Float.MAX_VALUE
            for (i in 0 until axis.size - 1) { val d = hypot(axis[i].x - q.x, axis[i].y - q.y); if (d < bd) { bd = d; bi = i } }
            val a = axis[bi]; val b = axis[bi + 1]; val n = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1e-6f)
            return Pt((b.x - a.x) / n, (b.y - a.y) / n)
        }
        val total = LineMeasure.length(sm)
        var acc = 0f; var bad = 0; var cut = sm.size
        for (i in 1 until sm.size - 1) {
            acc += hypot(sm[i].x - sm[i - 1].x, sm[i].y - sm[i - 1].y)
            val a = sm[i - 1]; val b = sm[i + 1]; val n = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1e-6f)
            val d = axisDir(sm[i]); val cos = ((b.x - a.x) / n * d.x + (b.y - a.y) / n * d.y).coerceIn(-1f, 1f)
            if (acc > 0.5f * total && cos < 0.766f) { bad++; if (bad >= 2) { cut = i - 1; break } } else bad = 0
        }
        return RegionTrace.trimHooks(sm.subList(0, cut.coerceAtLeast(2)))
    }

    /** Segmento del ancho (perpendicular, centrado en la mitad de la línea) de una arruga o surco medidos. */
    fun withWidthGuide(l: AnnotLine, mmPerUnit: Float): AnnotLine {
        val m = l.metrics ?: return l
        val w = m.widthMm ?: return l
        if (m.isFeature || l.filled || l.points.size < 3 || l.guides.isNotEmpty() || mmPerUnit <= 0f) return l
        val k = l.points.size / 2
        val a = l.points[k - 1]; val b = l.points[k + 1]; val c = l.points[k]
        val tn = hypot(b.x - a.x, b.y - a.y).takeIf { it > 1e-7f } ?: return l
        val nx = -(b.y - a.y) / tn; val ny = (b.x - a.x) / tn
        val h = w / mmPerUnit / 2
        return l.copy(guides = listOf(MeasureGuide(FeatureMetrics.mmText("ancho", w), Pt(c.x - nx * h, c.y - ny * h), Pt(c.x + nx * h, c.y + ny * h), "Ancho (a media profundidad)")))
    }

    /**
     * Tramo de [pts] fuera del vello (con un margen de ~2 px): el más largo, o con [fromStart] el
     * que empieza en el primer punto (si el primero no está sobre vello).
     */
    private fun offHair(pts: List<Pt>, hair: FacialHair.Result, fromStart: Boolean): List<Pt> {
        val d = 2f / hair.width
        val bad = pts.map { q -> hair[q] || hair[Pt(q.x + d, q.y)] || hair[Pt(q.x - d, q.y)] || hair[Pt(q.x, q.y + d)] || hair[Pt(q.x, q.y - d)] }
        if (bad.none { it }) return pts
        if (fromStart && !bad[0]) return pts.subList(0, bad.indexOf(true))
        var best = 0 until 0; var a = -1
        for (i in 0..pts.size) {
            if (i < pts.size && !bad[i]) { if (a < 0) a = i } else if (a >= 0) { if (i - a > best.count()) best = a until i; a = -1 }
        }
        return pts.slice(best)
    }

    /** Escala en mm: la del iris, corregida con el sensor de profundidad si se midió al tomar la foto. */
    private fun scaleOf(mesh: List<Pt>, extras: MeasureExtras?): Pair<Float, String>? {
        val s = LineMeasure.scale(mesh) ?: return null
        val k = extras?.meta?.irisFactor?.takeIf { it in 0.6f..1.6f } ?: return s
        return s.first * k to "sensor de profundidad"
    }

    private fun inside(q: Pt, poly: List<Pt>): Boolean {
        var c = false; var j = poly.size - 1
        for (i in poly.indices) {
            if ((poly[i].y > q.y) != (poly[j].y > q.y) && q.x < (poly[j].x - poly[i].x) * (q.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x) c = !c
            j = i
        }
        return c
    }

    /** Luminancia media dentro de un polígono (píxeles de [lum]). */
    private fun meanLum(lum: GrayImage, poly: List<Pt>): Float? {
        val w = lum.width.toFloat()
        val xs = poly.map { it.x * w }; val ys = poly.map { it.y * w }
        var s = 0f; var n = 0
        for (y in ys.min().toInt()..ys.max().toInt()) for (x in xs.min().toInt()..xs.max().toInt()) {
            if (x !in 0 until lum.width || y !in 0 until lum.height) continue
            var inside = false; var j = poly.size - 1
            for (i in poly.indices) {
                if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
                j = i
            }
            if (inside) { s += lum[x, y]; n++ }
        }
        return if (n < 10) null else s / n
    }

    private fun meanLumDisc(lum: GrayImage, c: Pt, r: Float): Float? {
        val w = lum.width.toFloat(); val cx = c.x * w; val cy = c.y * w; val rr = r * w
        var s = 0f; var n = 0
        for (y in (cy - rr).toInt()..(cy + rr).toInt()) for (x in (cx - rr).toInt()..(cx + rr).toInt()) {
            if (x !in 0 until lum.width || y !in 0 until lum.height) continue
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= rr * rr) { s += lum[x, y]; n++ }
        }
        return if (n < 10) null else s / n
    }

    /** Semiancho del pasillo de cada surco, relativo al ancho de la cara. */
    fun corridorHalf(name: String) = when (name) { "valle lagrimal" -> 0.035f; "nasogeniano" -> 0.06f; else -> 0.05f }
    const val NASO_MIN_STRENGTH = 0.018f
    const val NASO_MIN_CONTRAST = 1.6f

    /** Contorno del sujeto (zonas corporales) desde una máscara. */
    fun body(mask: Mask): List<AnnotLine> {
        // outlineOf ya está en unidades del ancho (x e y divididas por el ancho de la máscara)
        val o = FreeContour.outlineOf(mask) ?: return emptyList()
        return listOf(AnnotLine(AnnotLayer.CONTOUR, o, true))
    }

    /**
     * Ajusta una línea estimada al valle de luminancia más cercano (perpendicular a la línea,
     * ±6 % de la interpupilar). Se acepta si el valle tiene contraste (≥ 3 niveles de 255 de media).
     */
    fun refine(layer: AnnotLayer, line: List<Pt>, lum: GrayImage?, ipd: Float, hair: FacialHair.Result? = null): AnnotLine {
        if (lum == null || line.size < 4) return AnnotLine(layer, line, false, estimated = true)
        val w = lum.width.toFloat()
        fun l(q: Pt): Float {
            var s = 0f; var n = 0
            val cx = (q.x * w).roundToInt(); val cy = (q.y * w).roundToInt()
            for (dy in -2..2) for (dx in -2..2) {
                val x = cx + dx; val y = cy + dy
                if (x in 0 until lum.width && y in 0 until lum.height) { s += lum[x, y]; n++ }
            }
            return if (n == 0) Float.NaN else s / n
        }
        val r = 0.06f * ipd; val steps = 12
        val offs = FloatArray(line.size); val contrast = ArrayList<Float>()
        for (i in 1 until line.size - 1) {
            val a = line[i - 1]; val b = line[i + 1]
            val tx = b.x - a.x; val ty = b.y - a.y; val tn = hypot(tx, ty).takeIf { it > 1e-6f } ?: continue
            val nx = -ty / tn; val ny = tx / tn
            var best = 0f; var bestL = Float.MAX_VALUE; var sum = 0f; var cnt = 0
            for (k in -steps..steps) {
                val d = r * k / steps
                val q = Pt(line[i].x + nx * d, line[i].y + ny * d)
                // el vello (más oscuro que la piel) no cuenta como valle de sombra
                if (hair?.get(q) == true) continue
                val v = l(q); if (v.isNaN()) continue
                sum += v; cnt++
                if (v < bestL) { bestL = v; best = d }
            }
            if (cnt > 0) { offs[i] = best; contrast += sum / cnt - bestL }
        }
        val c = contrast.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
        if (c < 3f / 255f) return AnnotLine(layer, line, false, estimated = true)
        // desplazamientos suavizados (mediana de 3 y dos pasadas de media de 5) y atenuados en los
        // extremos, para que la línea siga el surco sin dientes
        var sm = FloatArray(line.size) { i -> if (i == 0 || i == line.size - 1) 0f else listOf(offs[i - 1], offs[i], offs[(i + 1).coerceAtMost(line.size - 1)]).sorted()[1] }
        repeat(2) {
            val prev = sm
            sm = FloatArray(line.size) { i -> (-2..2).map { prev[(i + it).coerceIn(0, line.size - 1)] }.average().toFloat() }
        }
        val n = line.size - 1
        for (i in sm.indices) sm[i] *= kotlin.math.sin(Math.PI * i / n).toFloat().coerceIn(0f, 1f).let { kotlin.math.sqrt(it) }
        val moved = line.mapIndexed { i, q ->
            if (i == 0 || i == line.size - 1) q else {
                val a = line[i - 1]; val b = line[i + 1]
                val tx = b.x - a.x; val ty = b.y - a.y; val tn = hypot(tx, ty).coerceAtLeast(1e-6f)
                Pt(q.x - ty / tn * sm[i], q.y + tx / tn * sm[i])
            }
        }
        return AnnotLine(layer, moved, false, estimated = false)
    }

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
    private fun lerp(a: Pt, b: Pt, t: Float) = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    /** Curva suave (Catmull-Rom) por los puntos de control, muestreada. */
    fun curve(vararg c: Pt, perSeg: Int = 6): List<Pt> {
        if (c.size < 2) return c.toList()
        val out = ArrayList<Pt>()
        for (i in 0 until c.size - 1) {
            val p0 = c[(i - 1).coerceAtLeast(0)]; val p1 = c[i]; val p2 = c[i + 1]; val p3 = c[(i + 2).coerceAtMost(c.size - 1)]
            for (k in 0 until perSeg) {
                val t = k / perSeg.toFloat(); val t2 = t * t; val t3 = t2 * t
                fun f(a: Float, b: Float, cc: Float, d: Float) = 0.5f * (2 * b + (-a + cc) * t + (2 * a - 5 * b + 4 * cc - d) * t2 + (-a + 3 * b - 3 * cc + d) * t3)
                out += Pt(f(p0.x, p1.x, p2.x, p3.x), f(p0.y, p1.y, p2.y, p3.y))
            }
        }
        out += c.last()
        return out
    }
}

/**
 * Análisis de la cara a resolución fija: las arrugas finas (1–2 mm) solo se ven si la cara mide unos
 * [FACE_PX] px de ancho en la imagen analizada. La foto completa (con pelo, cuello y margen) a 480 px
 * deja la cara en 150–250 px y las arrugas por debajo de 1–2 px: no se detectaban. Se recorta la cara
 * (con la frente hasta el pelo) de la foto a su resolución, se analiza a esa escala y las líneas
 * vuelven a las coordenadas de la foto.
 */
object FaceCrop {
    const val FACE_PX = 400f
    const val MAX_SIDE = 1400

    /** Recorte en px de la foto ([x0], [y0], ancho, alto) y escala de salida (px de salida por px de la foto). */
    class Crop(val x0: Float, val y0: Float, val w: Float, val h: Float, val scale: Float, val srcW: Int) {
        val outW: Int get() = (w * scale).roundToInt().coerceAtLeast(16)
        val outH: Int get() = (h * scale).roundToInt().coerceAtLeast(16)
        /** Punto de la foto (unidades de su ancho) → recorte (unidades del ancho del recorte). */
        fun toCrop(p: Pt) = Pt((p.x * srcW - x0) / w, (p.y * srcW - y0) / w)
        fun toPhoto(p: Pt) = Pt((p.x * w + x0) / srcW, (p.y * w + y0) / srcW)
    }

    fun crop(mesh: List<Pt>, srcW: Int, srcH: Int): Crop {
        val faceW = hypot(mesh[234].x - mesh[454].x, mesh[234].y - mesh[454].y) * srcW
        val xs = mesh.map { it.x * srcW }; val ys = mesh.map { it.y * srcW }
        val x0 = (xs.min() - 0.2f * faceW).coerceAtLeast(0f); val x1 = (xs.max() + 0.2f * faceW).coerceAtMost(srcW.toFloat())
        val y0 = (ys.min() - 0.55f * faceW).coerceAtLeast(0f); val y1 = (ys.max() + 0.25f * faceW).coerceAtMost(srcH.toFloat())
        var s = (FACE_PX / faceW.coerceAtLeast(1f)).coerceAtMost(2f)
        val big = maxOf(x1 - x0, y1 - y0) * s
        if (big > MAX_SIDE) s *= MAX_SIDE / big
        return Crop(x0, y0, x1 - x0, y1 - y0, s, srcW)
    }

    fun bitmap(src: Bitmap, c: Crop): Bitmap {
        val m = android.graphics.Matrix().apply { postScale(c.scale, c.scale) }
        val x = c.x0.toInt().coerceIn(0, src.width - 1); val y = c.y0.toInt().coerceIn(0, src.height - 1)
        val w = c.w.toInt().coerceIn(1, src.width - x); val h = c.h.toInt().coerceIn(1, src.height - y)
        return Bitmap.createBitmap(src, x, y, w, h, m, true)
    }

    /** Piel de [labels] (clases [classes]) en una rejilla [w]×[h] (vecino más cercano). */
    fun skinOf(labels: FaceLabels, w: Int, h: Int, vararg classes: Int): BooleanArray = BooleanArray(w * h) { i ->
        labels[(i % w) * labels.width / w, (i / w) * labels.height / h] in classes
    }

    /** Luminancia (0..1) de un mapa de bits sin reescalar. */
    fun lum(b: Bitmap): GrayImage {
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return GrayImage(b.width, b.height, FloatArray(px.size) { i -> val c = px[i]; (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f })
    }

    /** Relieve (en coordenadas de la foto) muestreado en el recorte. */
    fun relief(r: GrayImage, c: Crop, outW: Int, outH: Int): GrayImage {
        val k = r.width / c.srcW.toFloat()
        return GrayImage(outW, outH, FloatArray(outW * outH) { i ->
            val u = i % outW; val v = i / outW
            r.sample((c.x0 + (u + 0.5f) / c.scale) * k, (c.y0 + (v + 0.5f) / c.scale) * k)
        })
    }

    /**
     * Líneas de la cara de [src] (malla [mesh] en unidades de su ancho), analizadas en el recorte.
     * [labelsOf]: segmentación pelo / piel del recorte.
     */
    fun annotate(mesh: List<Pt>, src: Bitmap, labelsOf: (Bitmap, Crop) -> FaceLabels?, extras: MeasureExtras? = null,
                 info: MutableMap<String, Float>? = null, priors: List<PriorLine> = emptyList(),
                 /** Trazos añadidos a mano, en coordenadas de [src] (unidades de su ancho). */
                 added: List<List<Pt>> = emptyList()): List<AnnotLine> {
        val c = crop(mesh, src.width, src.height)
        val b = bitmap(src, c)
        val labels = runCatching { labelsOf(b, c) }.getOrNull()
        return finish(mesh, src, c, b, labels, extras, info, priors, added)
    }

    /** Parte de cálculo de [annotate] (sin modelos nativos): recorte [b] ya hecho y su segmentación. */
    fun finish(mesh: List<Pt>, src: Bitmap, c: Crop, b: Bitmap, labels: FaceLabels?, extras: MeasureExtras? = null,
               info: MutableMap<String, Float>? = null, priors: List<PriorLine> = emptyList(),
               added: List<List<Pt>> = emptyList(), hairVScale: Float = 1f): List<AnnotLine> {
        val m = mesh.map { c.toCrop(it) }
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        val ex = extras?.let { e -> MeasureExtras(e.relief?.let { relief(it, c, b.width, b.height) }, e.meta, e.onlyAdded) }
        val lines = FaceAnnotations.fromMesh(m, labels, lum(b), LabImage.fromArgb(px, b.width, b.height), ex, info, priors,
            added.map { l -> l.map { c.toCrop(it) } }, hairVScale)
        // hallazgos de la piel (manchas, arañitas) en la piel de la cara, sin ojos, cejas ni labios; escala por el iris
        val lesions = if (labels == null || extras?.onlyAdded == true) emptyList() else runCatching {
            val skin = skinOf(labels, b.width, b.height, FaceLabels.FACE_SKIN)
            SkinLesions.detect(px, b.width, b.height, skin, setOf(SkinLesions.Kind.SPOTS, SkinLesions.Kind.SPIDER),
                LineMeasure.scale(m)?.first, lines.filter { it.closed && (it.layer == AnnotLayer.EYES || it.layer == AnnotLayer.LIPS) }.map { it.points })
        }.getOrDefault(emptyList())
        if (b !== src) b.recycle()
        return (lines + lesions).map { l -> l.mapped { c.toPhoto(it) } }
    }
}

/** Analiza una foto (ya editada y en su encuadre) y devuelve sus líneas; con caché por clave. */
class PhotoAnnotator(private val context: Context) {
    suspend fun annotate(bmp: Bitmap, zone: Zone, extras: MeasureExtras? = null): List<AnnotLine> = analyze(bmp, zone, extras).first

    /**
     * Líneas y, si no hay arrugas medibles, el motivo para el usuario (rostro no detectado entero,
     * zona corporal…).
     */
    suspend fun analyze(bmp: Bitmap, zone: Zone, extras: MeasureExtras? = null,
                        /** Correcciones del usuario: líneas eliminadas y trazos añadidos (en coordenadas de [bmp]). */
                        user: AnnotUserEdit? = null,
                        /** Arrugas y surcos de fotos anteriores del paciente, como referencia. */
                        priors: List<PriorLine> = emptyList(),
                        /** Foto cuya memoria de líneas se guarda (para las fotos posteriores); null = no guardar. */
                        memoryUri: String? = null): Pair<List<AnnotLine>, String?> =
        if (!enabled) emptyList<AnnotLine>() to tr("Análisis no disponible")
        else analyzeSplit(bmp, zone, extras, user, priors, memoryUri)

    /** Lo que dan los modelos nativos para una cara: malla con su recorte y segmentación, o puntos de ML Kit. */
    private class NativeFace(val mesh: List<Pt>? = null, val crop: FaceCrop.Crop? = null, val cropBmp: Bitmap? = null,
                             val labels: FaceLabels? = null, val points: Map<String, Pt> = emptyMap(), val err: String = "",
                             /** Perfil: máscara del paciente (silueta) para el cuello y la papada. */
                             val profileMask: Mask? = null)

    /**
     * Los modelos nativos (malla, ML Kit, segmentación) van de uno en uno en toda la app ([NativeGate]);
     * el cálculo de arrugas y medidas, fuera de la puerta y en varios núcleos ([Par]): así, en Comparar,
     * mientras una foto usa los modelos otra ya calcula sus arrugas.
     */
    private suspend fun analyzeSplit(bmp: Bitmap, zone: Zone, extras: MeasureExtras?, user: AnnotUserEdit?, priors: List<PriorLine>,
                                     memoryUri: String?): Pair<List<AnnotLine>, String?> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val spec = ZoneAlignment.of(zone)
        // primer plano de un ojo: con la sección del ojo de la malla facial (foto + su espejo, ver EyeComposite)
        if (zone.guide == com.photomed.app.data.GuideType.EYE) return@withContext analyzeEye(bmp, zone, user, extras?.onlyAdded == true)
        if (spec.detector != Detector.FACE)
            return@withContext NativeGate.run("análisis de una foto (${zone.id}, ${bmp.width}×${bmp.height})") { analyzeGated(bmp, zone) }
        val nat = NativeGate.run("análisis de una foto: malla y segmentación (${zone.id}, ${bmp.width}×${bmp.height})") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                // perfil: silueta del paciente (ángulo cervicomentoniano y papada)
                val pm = if (zone.guide == com.photomed.app.data.GuideType.FACE_PROFILE)
                    runCatching { SubjectSegmenter().use { it.segment(bmp) } }.getOrNull() else null
                // malla completa de MediaPipe (nariz, labios, ojos y pasillos de los surcos con su topología real)
                val mesh = meshRobust(bmp)
                if (mesh != null && mesh.size >= 468) {
                    // análisis en el recorte de la cara a resolución fija (ver FaceCrop)
                    val c = FaceCrop.crop(mesh, bmp.width, bmp.height)
                    val b = FaceCrop.bitmap(bmp, c)
                    val labels = HairSkinSegmenter.sharedOrNull(context)?.let { seg -> runCatching { seg.labels(b) }.getOrNull() }
                    NativeFace(mesh, c, b, labels, profileMask = pm)
                } else {
                    val pts = mlkitPoints(bmp)
                    val err = MediaPipeFace.lastError?.let { tr(" (detalle: %1\$s)", it) } ?: ""
                    val labels = if (pts.size >= LiveDecision.MIN_POINTS) HairSkinSegmenter.sharedOrNull(context)?.let { it.labels(bmp) } else null
                    NativeFace(labels = labels, points = pts, err = err, profileMask = pm)
                }
            }
        }
        val scalpFront = zone.guide == com.photomed.app.data.GuideType.SCALP_FRONT
        // perfil: cuello y papada por la silueta (fuera de la puerta)
        val prof = nat.profileMask?.let { m -> runCatching { Jawline.profile(m, bmp.width, bmp.height) }.getOrNull() }.orEmpty()
        val mesh = nat.mesh
        if (mesh != null && nat.crop != null && nat.cropBmp != null) {
            run {
                val info = HashMap<String, Float>()
                // capilar con la cabeza inclinada: alturas de la frente corregidas por el cabeceo medido en la malla
                val tilt = if (scalpFront) tiltOf(mesh) else null
                val all = try {
                    FaceCrop.finish(mesh, bmp, nat.crop, nat.cropBmp, nat.labels, extras, info, priors, user?.added.orEmpty(),
                        hairVScale = tilt?.let { FeatureMetrics.hairTiltScale(it) } ?: 1f)
                } finally { if (nat.cropBmp !== bmp) nat.cropBmp.recycle() }
                if (scalpFront) {
                    val kept = scalpFrontLines(all.filter { l -> l.metrics?.name?.let { it in user?.dismissed.orEmpty() } != true }, measured = true)
                    return@withContext (kept + frontHair(bmp, kept)) to scalpFrontNote(tilt)
                }
                // falsos positivos eliminados por el usuario
                val lines = all.filter { l -> l.metrics?.name?.let { it in user?.dismissed.orEmpty() } != true }
                if (memoryUri != null) LineMemory.save(context, memoryUri, mesh, lines)
                val note = if (lines.none { it.metrics != null }) tr("No se encontraron arrugas ni surcos medibles en esta foto (piel lisa, " +
                    "poca resolución de la cara o luz muy plana).") else beardNote(info)
                // de perfil: además, el cuello y la papada por la silueta (la papada de frente no aplica)
                val withProf = if (prof.isEmpty()) lines else lines.filter { it.layer != AnnotLayer.PAPADA } + prof
                return@withContext withProf to note
            }
        }
        // sin malla: puntos de ML Kit; surcos estimados y ajustados a la sombra, con medidas (escala
        // por la distancia interpupilar), sin frente, ceño, patas de gallo ni ojeras
        run {
            val pts = nat.points; val err = nat.err; val labels = nat.labels
            if (pts.size >= LiveDecision.MIN_POINTS) {
                val info = HashMap<String, Float>()
                val lines = FaceAnnotations.face(pts, labels, lumOf(bmp), labOf(bmp), info)
                    .filter { l -> l.metrics?.name?.let { it in user?.dismissed.orEmpty() } != true }
                if (scalpFront) return@withContext scalpFrontLines(lines, measured = false) to
                    tr("Cabeza inclinada sin malla facial completa: solo el contorno, los ojos y la línea del pelo, sin medidas.")
                return@withContext lines to (tr("No se pudo obtener la malla facial completa%1\$s: solo se miden los surcos " +
                    "(nasogeniano, marioneta, valle lagrimal); frente, ceño, patas de gallo y ojeras necesitan la malla.", err) +
                    (beardNote(info)?.let { " $it" } ?: ""))
            }
            if (prof.isNotEmpty()) prof to tr("Perfil: sin malla facial; el cuello y la papada se miden por la silueta.")
            else emptyList<AnnotLine>() to tr("No se detectó ninguna cara en la foto%1\$s.", err)
        }
    }

    private fun scalpFrontNote(tiltDeg: Float?): String = if (tiltDeg == null)
        tr("Cabeza inclinada: contorno, ojos, cejas y línea del pelo; no se pudo medir el cabeceo y las alturas de la frente van sin corregir.")
    else tr("Cabeza inclinada %1\$s°: contorno, ojos, cejas y línea del pelo; las alturas de la frente están corregidas a las de una toma frontal a nivel.",
        "%.0f".format(tiltDeg))

    /**
     * Ojo: la foto y su espejo forman una «cara» superior en la que se busca la malla y se hace el análisis del
     * rostro (periocular); se conservan las líneas del ojo real, en las coordenadas de la foto. Los trazos
     * añadidos a mano van y vuelven igual. Sin malla: aviso, sin medidas.
     */
    private suspend fun analyzeEye(bmp: Bitmap, zone: Zone, user: AnnotUserEdit?, onlyAdded: Boolean = false): Pair<List<AnnotLine>, String?> {
        val (comp, l) = EyeComposite.build(bmp, zone.mirrored)
        try {
            val u = user?.copy(added = user.added.map { t -> t.map(l::toComposite) })
            val (lines, note) = analyzeSplit(comp, Zone.PERIOCULAR, if (onlyAdded) MeasureExtras(null, null, onlyAdded = true) else null, u, emptyList(), null)
            val eye = lines.filter { it.layer in EyeComposite.LAYERS && l.isReal(it.points) }.map { it.mapped(l::toPhoto) }
            if (!onlyAdded && eye.none { it.layer == AnnotLayer.EYES }) return emptyList<AnnotLine>() to
                tr("No se encontró el ojo en la foto (encuadre el ojo con la ceja y el canto interno, como en la guía).")
            return eye to (listOfNotNull(tr("Ojo: medidas con la sección del ojo de la malla facial (escala por el iris, 11,7 mm)."),
                note?.takeIf { it.contains("barba", ignoreCase = true).not() }).joinToString(" "))
        } finally { comp.recycle() }
    }

    /** Capilar frontal inclinada: densidad del cuero por encima de la línea de implantación (nativo, con su puerta). */
    private suspend fun frontHair(bmp: Bitmap, lines: List<AnnotLine>): List<AnnotLine> {
        val hl = lines.firstOrNull { it.layer == AnnotLayer.HAIRLINE }?.points?.sortedBy { it.x }?.takeIf { it.size >= 3 } ?: return emptyList()
        val margin = 0.01f
        fun above(p: Pt): Boolean {
            val y = when {
                p.x <= hl.first().x -> hl.first().y
                p.x >= hl.last().x -> hl.last().y
                else -> {
                    val k = hl.indexOfFirst { it.x >= p.x }
                    val a = hl[k - 1]; val b = hl[k]
                    a.y + (b.y - a.y) * (p.x - a.x) / (b.x - a.x).coerceAtLeast(1e-6f)
                }
            }
            return p.y < y - margin
        }
        return runCatching {
            NativeGate.run("densidad capilar (frontal inclinada)") { hairLines(bmp, HairDensity.View.FRONT_TILT, ::above) }
        }.getOrNull().orEmpty()
    }

    /** Flexión de la cabeza (barbilla abajo, grados positivos) por el ajuste de la malla al modelo 3D. */
    private fun tiltOf(mesh: List<Pt>): Float? =
        HeadPose.fit(FaceAnnotations.keysFromMesh(mesh))?.let { -(it.pitch + Standardization.PITCH_ZERO) }

    /** Zonas corporales: contorno con el segmentador nativo (dentro de la puerta). */
    private suspend fun analyzeGated(bmp: Bitmap, zone: Zone): Pair<List<AnnotLine>, String?> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        // cuero cabelludo (vértice, coronilla, parietales): densidad capilar aparente y grados orientativos
        if (zone.category == com.photomed.app.data.ZoneCategory.CAPILAR) {
            val view = when (zone.guide) {
                com.photomed.app.data.GuideType.SCALP -> HairDensity.View.VERTEX
                com.photomed.app.data.GuideType.SCALP_CROWN -> HairDensity.View.CROWN
                else -> HairDensity.View.PARIETAL
            }
            val lines = runCatching { hairLines(bmp, view, null) }.getOrNull().orEmpty()
            return@withContext lines to (if (lines.isEmpty()) tr("No se encontró la cabeza en la foto (pelo y piel de la segmentación).")
                else HAIR_NOTE)
        }
        // diagrama del cuerpo con los mismos detectores que la cámara: puntos del cuerpo, manos y segmentación
        val diagram = runCatching { bodyDiagram(bmp, zone) }.getOrNull().orEmpty()
        if (diagram.isNotEmpty()) return@withContext diagram to (tr("Diagrama del cuerpo: contorno, ejes, dedos, uñas y puntos de referencia. " +
            "Las medidas de arrugas y surcos son solo para zonas del rostro.") + " " + trData(SkinLesions.NOTE))
        val mask = BodyRegion.of(zone, bmp) { b -> SubjectSegmenter().use { it.segment(b) } }
        (mask?.let { FaceAnnotations.body(it) } ?: emptyList()) to tr("Las medidas de arrugas y surcos son solo para zonas del rostro.")
    }

    /**
     * Densidad capilar aparente ([HairDensity]) como líneas: contorno de la región con las medidas y los
     * grados, celdas con el cuero cabelludo visible (relleno) y la franja de la raya. Usa el segmentador
     * nativo: llamar dentro de [NativeGate].
     */
    private fun hairLines(bmp: Bitmap, view: HairDensity.View, keep: ((Pt) -> Boolean)?): List<AnnotLine> {
        val w = HAIR_W; val h = (w * bmp.height / bmp.width.toFloat()).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true).let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
        val labels = HairSkinSegmenter.sharedOrNull(context)?.let { it.labels(small) } ?: return emptyList()
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== bmp) small.recycle()
        val r = HairDensity.analyze(px, w, h, labels, view, keep) ?: return emptyList()
        return hairLinesOf(r, view)
    }

    /** Diagrama del cuerpo de la foto ([BodyDiagram]); vacío si no se ve la zona. */
    private fun bodyDiagram(bmp: Bitmap, zone: Zone): List<AnnotLine> {
        if (zone == Zone.FREE) return emptyList()
        val spec = ZoneAlignment.of(zone)
        // segmentación a baja resolución (como en la cámara: los criterios y el contorno están hechos para ella)
        val w = 192; val h = (w * bmp.height / bmp.width.toFloat()).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val labels = HairSkinSegmenter.sharedOrNull(context)?.let { it.labels(small) }
        val classes = labels?.let { lb ->
            val src = if (lb.width == w && lb.height == h) small else Bitmap.createScaledBitmap(bmp, lb.width, lb.height, true)
            ZoneCriteria.Classes(lb.width, lb.height, lb.data, IntArray(lb.width * lb.height).also { src.getPixels(it, 0, lb.width, 0, 0, lb.width, lb.height) })
        }
        val pose = if (spec.detector == Detector.POSE && zone.guide != com.photomed.app.data.GuideType.HANDS && zone.guide != com.photomed.app.data.GuideType.FEET)
            LandmarkDetector(stream = false, context, FaceEngine.ML_KIT).use { it.detect(Detector.POSE, bmp) } else emptyMap()
        val hands = if (zone.guide == com.photomed.app.data.GuideType.HANDS) HandDetector.createOrNull(context)?.use { it.detect(bmp) } else null
        // una sola mano en la foto (toma de una mano): se dibuja esa
        val single = hands?.size == 1
        val diagram = BodyDiagram.lines(zone, ZoneCriteria.Scene(pose, hands?.ifEmpty { null }, classes, bmp.height / bmp.width.toFloat(), single = single))
        return diagram + bodyLesions(bmp, zone, labels)
    }

    /** Hallazgos de la piel ([SkinLesions]) de una zona del cuerpo, a resolución media, en la piel de la segmentación. */
    private fun bodyLesions(bmp: Bitmap, zone: Zone, labels: FaceLabels?): List<AnnotLine> = runCatching {
        val w = LESION_W; val h = (w * bmp.height / bmp.width.toFloat()).roundToInt().coerceAtLeast(1)
        val b = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        b.recycleIfCopy(bmp)
        // solo la piel del cuerpo: una cara pequeña en la foto daría ojos y cejas como «manchas»
        val skin = labels?.let { FaceCrop.skinOf(it, w, h, FaceLabels.BODY_SKIN) }
        SkinLesions.detect(px, w, h, skin, SkinLesions.kindsFor(zone))
    }.getOrDefault(emptyList())

    private fun Bitmap.recycleIfCopy(src: Bitmap) { if (this !== src && !isRecycled) recycle() }


    /**
     * Medidas de un trazo hecho a mano ([pts] en coordenadas de [bmp], unidades de su ancho), con la
     * misma escala y método que las líneas detectadas. null si no se pueden medir (sin cara).
     */
    suspend fun measureTrace(bmp: Bitmap, zone: Zone, extras: MeasureExtras?, pts: List<Pt>): LineMetrics? =
        analyze(bmp, zone, MeasureExtras(extras?.relief, extras?.meta, onlyAdded = true), user = AnnotUserEdit(added = listOf(pts))).first
            .firstOrNull { it.metrics?.name == "Surco añadido 1" }?.metrics

    /** Aviso si hay barba o bigote: los surcos de la zona solo se trazan sobre la piel visible. */
    private fun beardNote(info: Map<String, Float>): String? = info["vello"]?.takeIf { it >= 0.08f }?.let {
        tr("Barba o bigote en %1\$d %% de la parte inferior de la cara: los surcos de esa zona se trazan y " +
            "miden solo sobre la piel visible (las líneas marcadas con ✱ son parciales).", (it * 100).roundToInt())
    }

    private fun labOf(bmp: Bitmap): LabImage {
        val w = minOf(bmp.width, 480); val h = (bmp.height * w / bmp.width.toFloat()).roundToInt()
        val s = Bitmap.createScaledBitmap(bmp, w, h, true)
        return LabImage.fromArgb(IntArray(w * h).also { s.getPixels(it, 0, w, 0, 0, w, h) }, w, h)
    }

    /** Ojos de la malla (centros de iris 468 / 473 o párpados) frente a los de ML Kit. */
    private fun consistent(mesh: List<Pt>, ml: Map<String, Pt>): Boolean {
        val l = ml[Landmarks.EYE_L]; val r = ml[Landmarks.EYE_R]
        if (mesh.size < 468 || l == null || r == null) return true // sin ML Kit no se puede contrastar
        val mr = if (mesh.size > 473) mesh[468] else Pt((mesh[33].x + mesh[133].x) / 2, (mesh[33].y + mesh[133].y) / 2)
        val mlft = if (mesh.size > 473) mesh[473] else Pt((mesh[263].x + mesh[362].x) / 2, (mesh[263].y + mesh[362].y) / 2)
        val ipd = hypot(l.x - r.x, l.y - r.y).coerceAtLeast(1e-4f)
        // los dos emparejamientos (los nombres izquierda / derecha pueden ir cruzados entre motores)
        val a = maxOf(hypot(mr.x - r.x, mr.y - r.y), hypot(mlft.x - l.x, mlft.y - l.y))
        val b = maxOf(hypot(mr.x - l.x, mr.y - l.y), hypot(mlft.x - r.x, mlft.y - r.y))
        return minOf(a, b) < 0.25f * ipd
    }

    private fun mlkitPoints(bmp: Bitmap): Map<String, Pt> =
        runCatching { LandmarkDetector(stream = false, context, FaceEngine.ML_KIT).use { it.detect(Detector.FACE, bmp) } }.getOrDefault(emptyMap())

    /**
     * Malla de MediaPipe con reintentos: la foto reducida (≤ 1280 px), un recorte alrededor de la
     * cara que encuentra ML Kit y la foto dentro de un marco más amplio (primeros planos).
     */
    private suspend fun meshRobust(bmp: Bitmap): List<Pt>? {
        MediaPipeFace.lastError = null
        fun scaled(b: Bitmap, maxSide: Int): Bitmap {
            val s = maxSide / maxOf(b.width, b.height).toFloat()
            return if (s >= 1f && b.config == Bitmap.Config.ARGB_8888) b
            else Bitmap.createScaledBitmap(b, (b.width * minOf(1f, s)).roundToInt(), (b.height * minOf(1f, s)).roundToInt(), true)
                .let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
        }
        // 1) foto reducida (mismas unidades: x e y / ancho)
        val first = MediaPipeFace.withShared(context) { mp -> mp.mesh(scaled(bmp, 1280)) }
        // ML Kit como complemento de precisión: los centros de los ojos de la malla deben coincidir con
        // los de ML Kit (± 25 % de la interpupilar); si no, la malla se ha ajustado mal (otra cara,
        // oclusión, giro) y se rehace sobre la cara que encuentra ML Kit
        val ml = mlkitPoints(bmp)
        if (first != null && consistent(first, ml)) return first
        // 2) recorte alrededor de la cara de ML Kit
        val pts = ml.values
        if (pts.size >= 10) {
            val minX = pts.minOf { it.x } * bmp.width; val maxX = pts.maxOf { it.x } * bmp.width
            val minY = pts.minOf { it.y } * bmp.width; val maxY = pts.maxOf { it.y } * bmp.width
            val side = maxOf(maxX - minX, maxY - minY) * 1.8f
            val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
            val x0 = (cx - side / 2).coerceIn(0f, bmp.width - 1f); val y0 = (cy - side / 2).coerceIn(0f, bmp.height - 1f)
            val w = side.coerceAtMost(bmp.width - x0); val h = side.coerceAtMost(bmp.height - y0)
            if (w > 32 && h > 32) {
                val crop = scaled(Bitmap.createBitmap(bmp, x0.toInt(), y0.toInt(), w.toInt(), h.toInt()), 720)
                val k = w.toInt() / crop.width.toFloat()
                MediaPipeFace.withShared(context) { mp -> mp.mesh(crop) }?.let { m ->
                    val mm = m.map { Pt((it.x * crop.width * k + x0.toInt()) / bmp.width, (it.y * crop.width * k + y0.toInt()) / bmp.width) }
                    if (consistent(mm, ml)) return mm
                }
            }
        }
        // la primera malla, aunque ML Kit no la confirme (puede ser ML Kit el que falla)
        if (first != null) return first
        // 3) marco ampliado
        return MediaPipeFace.withShared(context) { mp -> meshPadded(mp, scaled(bmp, 1024)) }
            .also { if (it == null && MediaPipeFace.lastError == null) MediaPipeFace.lastError = tr("MediaPipe no encontró la cara") }
    }

    /** Malla con la foto centrada en un lienzo 1,8 veces mayor (la cara cabe entera en el detector). */
    private fun meshPadded(mp: MediaPipeFace, bmp: Bitmap): List<Pt>? {
        val f = 1.8f
        val w = (bmp.width * f).roundToInt(); val h = (bmp.height * f).roundToInt()
        val ox = (w - bmp.width) / 2f; val oy = (h - bmp.height) / 2f
        val big = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(big).apply { drawColor(android.graphics.Color.rgb(128, 128, 128)); drawBitmap(bmp, ox, oy, null) }
        val m = mp.mesh(big)
        big.recycle()
        return m?.map { Pt((it.x * w - ox) / bmp.width, (it.y * w - oy) / bmp.width) }
    }

    /** Luminancia (0..1) sin estandarizar, para buscar valles de sombra. */
    private fun lumOf(bmp: Bitmap): GrayImage {
        val w = minOf(bmp.width, 480); val h = (bmp.height * w / bmp.width.toFloat()).roundToInt()
        val s = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h).also { s.getPixels(it, 0, w, 0, 0, w, h) }
        return GrayImage(w, h, FloatArray(px.size) { i -> val c = px[i]; (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f })
    }

    companion object {
        /**
         * Toma capilar frontal con la cabeza inclinada: solo el contorno, la línea del pelo, los ojos y las
         * cejas. Las arrugas, surcos y proporciones medidos sobre una cara escorzada 30° no son comparables
         * con los del rostro frontal; el contorno queda sin medidas (sus tercios dependen del escorzo).
         * [measured] false (sin malla): ninguna línea lleva medidas.
         */
        internal fun scalpFrontLines(all: List<AnnotLine>, measured: Boolean): List<AnnotLine> =
            all.filter { it.layer in SCALP_FRONT_LAYERS }
                .map { l ->
                    when {
                        l.layer == AnnotLayer.CONTOUR -> l.copy(metrics = null, guides = emptyList())
                        !measured -> l.copy(metrics = null, guides = emptyList())
                        else -> l
                    }
                }

        val SCALP_FRONT_LAYERS = setOf(AnnotLayer.CONTOUR, AnnotLayer.HAIRLINE, AnnotLayer.EYES, AnnotLayer.SCALP_MAP, AnnotLayer.HAIR_PART)

        /** Ancho del análisis capilar (px). */
        const val HAIR_W = 384
        /** Ancho de la imagen en la que se buscan manchas, arañitas, várices y celulitis en el cuerpo. */
        const val LESION_W = 384

        val HAIR_NOTE: String get() = tr("Densidad capilar aparente: cuero cabelludo visible entre el pelo (segmentación y color), " +
            "no pelos por cm² (eso es tricoscopia). Grados BASP, Norwood, Ludwig y Sinclair orientativos, para seguir al mismo " +
            "paciente con tomas iguales (luz, peinado, cabello seco); no son un diagnóstico.")

        /** Líneas de un resultado de [HairDensity] (contorno con las medidas, celdas y raya). */
        fun hairLinesOf(r: HairDensity.Result, view: HairDensity.View): List<AnnotLine> {
            val pct = { v: Float -> v * 100f }
            val f = buildList {
                add(trn("Cuero cabelludo visible, %") to pct(r.visible))
                if (view != HairDensity.View.FRONT_TILT) {
                    add(trn("Centro de la zona, %") to pct(r.center))
                    add(trn("Periferia, %") to pct(r.periphery))
                }
                add(trn("Contraste pelo–piel (ΔE)") to r.contrast)
            }
            val m = LineMetrics(trn("Densidad capilar aparente"), scale = "foto", features = f,
                type = HairDensity.grades(r, view),
                note = if (r.contrast < HairDensity.MIN_CONTRAST) trn("Pelo y piel de color parecido: solo cuenta la segmentación.") else null)
            return buildList {
                if (r.outline.size >= 3) add(AnnotLine(AnnotLayer.SCALP_MAP, r.outline, closed = true, metrics = m))
                r.cells.forEach { add(AnnotLine(AnnotLayer.SCALP_MAP, it, closed = true, filled = true)) }
                // franja de la raya: banda central (cerrada) con sus medidas; al tocarla se explica qué es
                if (r.strip.size == 2 && r.strip.all { it.size >= 2 }) {
                    val pm = LineMetrics(trn("Franja de la raya"), scale = "foto",
                        features = listOf(trn("Cuero visible en la franja central, %") to pct(r.midline),
                            trn("Cuero visible a los lados, %") to pct(r.lateral),
                            trn("Diferencia centro − lados, puntos") to pct(r.midline - r.lateral)),
                        note = trn("Banda central de la cabeza (línea media) donde se mide la raya: si su cuero visible supera al de los lados, la raya se ensancha (Sinclair, Olsen)."))
                    add(AnnotLine(AnnotLayer.HAIR_PART, r.strip[0] + r.strip[1].reversed(), closed = true, metrics = pm))
                }
            }
        }

        /**
         * En las pruebas en la JVM (Robolectric) no hay modelos nativos: las llamadas a ML Kit no
         * terminan y ocupan los hilos de fondo de las pruebas siguientes. Ahí el análisis no se hace
         * (las pruebas ponen las líneas en la caché).
         */
        @Volatile var enabled: Boolean = android.os.Build.FINGERPRINT != "robolectric"

        /**
         * Relieve y escala guardados de la foto [uri], cargados como la foto (misma edición; [width]
         * con recorte 3:4 si [crop34], o lado mayor [width] si no).
         */
        suspend fun extrasFor(context: Context, uri: android.net.Uri, width: Int, crop34: Boolean): MeasureExtras? {
            val meta = ReliefStore.meta(context, uri.toString()) ?: return null
            val edit = PhotoEdits.of(context, uri.toString())
            val relief = if (!meta.hasRelief) null else ReliefStore.reliefUri(context, uri.toString())?.let { r ->
                runCatching { if (crop34) ImageOps.load34(context, r, width, edit) else ImageOps.loadFull(context, r, width, edit) }.getOrNull()
            }?.let { Relief.decode(it) }
            return MeasureExtras(relief, meta)
        }

        private val cache = object : LinkedHashMap<String, List<AnnotLine>>(16, 0.75f, true) {
            override fun removeEldestEntry(e: MutableMap.MutableEntry<String, List<AnnotLine>>) = size > 40
        }
        // notas de cada análisis: acotadas como los análisis (antes sin límite)
        private val notes = object : LinkedHashMap<String, String?>(16, 0.75f, true) {
            override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String?>) = size > 200
        }
        // la sensibilidad de las arrugas forma parte de la clave: al cambiarla se vuelve a analizar
        fun note(key: String): String? = synchronized(cache) { notes[WrinkleTuning.key + key] }
        fun putNote(key: String, n: String?) = synchronized(cache) { notes[WrinkleTuning.key + key] = n }
        fun cached(key: String): List<AnnotLine>? = synchronized(cache) { cache[WrinkleTuning.key + key] }
        /** Vacía los análisis en memoria (al actualizar la app). */
        fun clearCache() = synchronized(cache) { cache.clear(); notes.clear() }
        fun put(key: String, v: List<AnnotLine>) = synchronized(cache) { cache[WrinkleTuning.key + key] = v }
    }
}

/**
 * Sensibilidad de la detección de arrugas (frente, ceño, patas de gallo): factores sobre los umbrales
 * de fuerza ([k]) y contraste ([kc]) del surco y sobre el número máximo de líneas por zona ([capK]).
 * 1 = valores calibrados; menor = más sensible (más líneas, menos precisión). Ver docs/BENCHMARK.md.
 */
object WrinkleTuning {
    @Volatile var k = 1f
    @Volatile var kc = 1f
    @Volatile var capK = 1f
    /** Umbral de la puntuación de candidatos ([WrinkleField.score]); menor = más sensible. */
    @Volatile var scoreT = 0f
    /** Detección libre además de los pasillos. */
    @Volatile var free = true
    /** Peso del mapa de textura en la puntuación (0 = sin usarlo) y su valor de referencia (ln de la razón). */
    @Volatile var textureW = 0f
    @Volatile var textureRef = 0f
    /** Detección libre: suavizado de la normalización de luz (fracción del ancho de la cara) o el mapa de textura del artículo. */
    @Volatile var freeSigma = 0.06f
    @Volatile var paperTexture = true
    /** Buscar con la luz normalizada (campo plano). */
    @Volatile var flat = false
    /** Alargar las líneas aceptadas por sus extremos mientras siga la cresta. */
    @Volatile var extend = false
    /** Líneas extra por encima del tope de cada zona, solo con puntuación alta (1 = sin extra). */
    @Volatile var strongCapK = 1f
    /** Surcos accesorios junto al nasogeniano y la marioneta: distancia máxima al eje, acuerdo mínimo con su
     *  dirección (cos²) y longitud mínima (fracción del ancho de la cara). */
    @Volatile var cheeks = false
    @Volatile var cheekDist = 0.07f
    @Volatile var cheekAgree = 0.85f
    @Volatile var cheekLen = 0.06f
    /** Frente: acuerdo mínimo con la horizontal de los candidatos libres. Infraorbitarias: tope por lado. */
    @Volatile var foreheadAgree = 0.8f
    @Volatile var infraCap = 3
    /** Exigencia extra (puntuación) para los candidatos libres por zona. */
    @Volatile var freeBias: Map<String, Float> = mapOf("frente" to 1.0f, "perioral" to 1.8f, "ceño" to 0.2f, "patas" to 0.2f, "infraorbital" to 0f, "surcos" to 0.6f)
    /** Sensibilidad elegida en los ajustes. */
    enum class Level(private val labelEs: String, private val descEs: String) {
        CONSERVATIVE(trn("Conservadora"), trn("Menos líneas, casi todas seguras (más precisión).")),
        BALANCED(trn("Equilibrada"), trn("Recomendada: equilibrio entre precisión y líneas encontradas.")),
        EXHAUSTIVE(trn("Exhaustiva"), trn("Más líneas: surcos accesorios, más frente y bajo los ojos (más hallazgos, algunos falsos).")),
        ;
        val label: String get() = tr(labelEs)
        val desc: String get() = tr(descEs)
    }
    const val PREF = "wrinkle_sensitivity"

    fun apply(level: Level) {
        // equilibrada y conservadora: sin las búsquedas extra (no perder precisión); exhaustiva: con ellas
        val extra = level == Level.EXHAUSTIVE
        cheeks = extra; foreheadAgree = if (extra) 0.9f else 0.8f; infraCap = if (extra) 5 else 3
        freeBias = freeBias + ("frente" to (if (extra) 0.6f else 1.0f))
        when (level) {
            Level.CONSERVATIVE -> { k = 1.15f; kc = 1.1f; capK = 0.8f; scoreT = 0.6f }
            Level.BALANCED -> { k = 1f; kc = 1f; capK = 1f; scoreT = 0f }
            Level.EXHAUSTIVE -> { k = 0.85f; kc = 0.92f; capK = 1.3f; scoreT = -0.3f }
        }
    }

    /** Aplica la sensibilidad guardada en las preferencias. */
    fun load(context: android.content.Context) = apply(runCatching {
        Level.valueOf(context.getSharedPreferences("photomed", android.content.Context.MODE_PRIVATE).getString(PREF, Level.BALANCED.name)!!)
    }.getOrDefault(Level.BALANCED))

    /** Clave para las cachés del análisis (cambia con la sensibilidad). */
    val key: String get() = "$k|$kc|$capK|$scoreT|$free|$flat|$textureW|$freeSigma|$paperTexture|$cheeks|$cheekDist|$cheekAgree|$cheekLen|$foreheadAgree|$infraCap|${freeBias["frente"]}|${freeBias["surcos"]}|$extend|$strongCapK"
    fun cap(n: Int) = (n * capK).toInt().coerceAtLeast(1)
}
