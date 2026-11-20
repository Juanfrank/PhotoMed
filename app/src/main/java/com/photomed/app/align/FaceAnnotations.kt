package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import com.photomed.app.data.Zone
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Capas de anotación estética que se pueden mostrar sobre una foto (visor y comparar). */
enum class AnnotLayer(val label: String) {
    CONTOUR("Contorno"), HAIRLINE("Línea del pelo"), EYES("Ojos y cejas"), NOSE("Nariz"), LIPS("Labios"), FOLDS("Surcos"),
    FOREHEAD("Frente"), GLABELLA("Ceño"), CROWS_FEET("Patas de gallo"), DARK_CIRCLES("Ojeras"),
}

/** Una línea de anotación (x del ancho, y en unidades del ancho). [estimated]: posición estimada, no medida. */
data class AnnotLine(val layer: AnnotLayer, val points: List<Pt>, val closed: Boolean, val estimated: Boolean = false,
                     /** Zona (relleno translúcido) en lugar de solo trazo, p. ej. ojeras. */
                     val filled: Boolean = false,
                     /** Medidas orientativas (longitud, ancho, profundidad, grado; color en ojeras). */
                     val metrics: LineMetrics? = null)

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
                halfMm = if (name == "Nasogeniano") 5f else 4f).copy(note = if (covered > 0.1f) "Parcialmente sobre barba o bigote" else null))
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
    fun fromMesh(mesh: List<Pt>, labels: FaceLabels?, lum: GrayImage?, lab: LabImage? = null, extras: MeasureExtras? = null,
                 /** Datos del análisis para el aviso al usuario (p. ej. "vello": fracción con barba o bigote). */
                 info: MutableMap<String, Float>? = null): List<AnnotLine> {
        if (mesh.size < 468) return face(keysFromMesh(mesh), labels, lum)
        val keys = FaceKeys.canonical(keysFromMesh(mesh))
        val out = ArrayList<AnnotLine>()
        val p = if (labels != null) HairSkin.snapOval(labels, keys) else keys
        // medidas de los rasgos (mm con la escala del iris o del sensor)
        val sc = scaleOf(mesh, extras)
        fun fm(f: (Float, String) -> LineMetrics?): LineMetrics? = sc?.let { runCatching { f(it.first, it.second) }.getOrNull() }
        (0 until 36).mapNotNull { p["oval_$it"] }.takeIf { it.size == 36 }?.let {
            out += AnnotLine(AnnotLayer.CONTOUR, it, true, metrics = fm { mm, s -> FeatureMetrics.contour(mesh, mm, s) })
        }
        labels?.let { HairSkin.hairline(it, keys)?.points?.let { h ->
            out += AnnotLine(AnnotLayer.HAIRLINE, h, false, metrics = fm { mm, s -> FeatureMetrics.hairline(mesh, h, mm, s) })
        } }
        fun chain(idx: IntArray) = idx.map { mesh[it] }
        out += AnnotLine(AnnotLayer.EYES, chain(EYE_R), true, metrics = fm { mm, s -> FeatureMetrics.eye(mesh, true, mm, s) })
        out += AnnotLine(AnnotLayer.EYES, chain(EYE_L), true, metrics = fm { mm, s -> FeatureMetrics.eye(mesh, false, mm, s) })
        out += AnnotLine(AnnotLayer.EYES, chain(BROW_R), true, metrics = fm { mm, s -> FeatureMetrics.brow(mesh, true, mm, s) })
        out += AnnotLine(AnnotLayer.EYES, chain(BROW_L), true, metrics = fm { mm, s -> FeatureMetrics.brow(mesh, false, mm, s) })
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_DORSAL_R).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_DORSAL_L).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_ALA_R).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_ALA_L).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_TIP).toTypedArray(), perSeg = 6), false,
            metrics = fm { mm, s -> FeatureMetrics.nose(mesh, mm, s) })
        // labios: bermellón superior e inferior, cada uno con sus medidas
        out += AnnotLine(AnnotLayer.LIPS, FeatureMetrics.upperLipPoly(mesh), true, metrics = fm { mm, s -> FeatureMetrics.upperLip(mesh, mm, s) })
        out += AnnotLine(AnnotLayer.LIPS, FeatureMetrics.lowerLipPoly(mesh), true, metrics = fm { mm, s -> FeatureMetrics.lowerLip(mesh, mm, s) })
        if (lum != null) out += folds(mesh, lum, labels, lab, extras, info)
        return out
    }

    /** Surcos detectados en la imagen (solo los encontrados), con el ancho del pasillo según la cara y sus medidas. */
    fun folds(mesh: List<Pt>, lum: GrayImage, labels: FaceLabels? = null, lab: LabImage? = null, extras: MeasureExtras? = null,
              info: MutableMap<String, Float>? = null): List<AnnotLine> {
        val faceW = dist(mesh[234], mesh[454]).coerceAtLeast(1e-3f)
        // barba y bigote: la segmentación los da como piel; los surcos solo se trazan sobre piel visible
        val hair = lab?.let { runCatching { FacialHair.fromMesh(it, mesh) }.getOrNull() }
        hair?.let { info?.put("vello", it.fraction) }
        val skinLab = labels?.let { l -> { q: Pt -> l[(q.x * l.width).roundToInt(), (q.y * l.width).roundToInt()] == FaceLabels.FACE_SKIN } }
        val skin: ((Pt) -> Boolean)? = if (hair == null || hair.fraction < 0.02f) skinLab
            else { q: Pt -> (skinLab?.invoke(q) ?: true) && !hair[q] }
        val sc = scaleOf(mesh, extras)
        val folds = FOLD_CORRIDORS.mapIndexedNotNull { i, (name, idx) ->
            val label = "${name.replaceFirstChar { it.uppercase() }} ${if (i % 2 == 0) "derecho" else "izquierdo"}"
            val full = curve(*idx.map { mesh[it] }.toTypedArray(), perSeg = 6)
            // con barba o bigote, el pasillo se acorta a su parte visible ANTES de buscar el surco (si no,
            // el tramo visible no llega al mínimo de longitud del pasillo completo y el surco se descarta
            // aunque se vea); el nasogeniano, desde el ala hasta donde empieza el vello
            val axis = if (hair == null || hair.fraction < 0.02f) full else offHair(full, hair, fromStart = name == "nasogeniano")
            val covered = if (axis === full) 0f else 1f - LineMeasure.length(axis) / LineMeasure.length(full).coerceAtLeast(1e-6f)
            // tramo visible demasiado corto (menos de ~6 mm o del 20 % del recorrido): no hay surco medible
            if (axis.size < 4 || LineMeasure.length(axis) < 0.04f * faceW || covered > 0.8f) return@mapIndexedNotNull null
            val naso = name == "nasogeniano"
            // nasogeniano más sensible (umbrales menores): es el surco clínico de referencia
            RidgeTracker.track(lum, axis, corridorHalf(name) * faceW, stations = if (axis === full) 28 else 20, skin = skin,
                minStrength = if (naso) NASO_MIN_STRENGTH else RidgeTracker.MIN_STRENGTH,
                minContrast = if (naso) NASO_MIN_CONTRAST else RidgeTracker.MIN_CONTRAST,
                anchorStart = naso)?.let { r ->
                // sin colas sobre el vello (el trazado se mueve a los lados del eje)
                val pts = hair?.let { offHair(r.points, it, fromStart = name == "nasogeniano") } ?: r.points
                if (pts.size < 5) return@mapIndexedNotNull null
                val note = if (covered > 0.2f) "Parcialmente cubierto por barba o bigote (${(covered * 100).roundToInt()} % del recorrido): " +
                    "se traza y mide solo la parte visible" else null
                AnnotLine(AnnotLayer.FOLDS, pts, false,
                    metrics = sc?.let { LineMeasure.line(label, pts, lum, it, halfMm = if (name == "nasogeniano") 5f else 4f, relief = extras?.relief).copy(note = note) })
            }
        }
        return folds + wrinkles(mesh, lum, labels, faceW, lab, extras)
    }

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
     * raíz nasal), patas de gallo (abanico de rayos desde el canto externo) y ojeras (zona
     * infraorbitaria más oscura que la mejilla). Candidatos múltiples con supresión de no máximos.
     */
    fun wrinkles(mesh: List<Pt>, lum: GrayImage, labels: FaceLabels?, faceW: Float, lab: LabImage? = null, extras: MeasureExtras? = null): List<AnnotLine> {
        val out = ArrayList<AnnotLine>()
        val sc = scaleOf(mesh, extras)
        fun withMetrics(layer: AnnotLayer, name: String, r: RidgeTracker.Result) =
            AnnotLine(layer, r.points, false, metrics = sc?.let { LineMeasure.line(name, r.points, lum, it, halfMm = 2f, relief = extras?.relief) })
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
                for (seg in listOf(xl to xr, xl to xm + 0.04f * faceW, xm - 0.04f * faceW to xr)) {
                    var y = lo
                    while (y <= hi) {
                        // arco de las cejas atenuado con la altura
                        val bend = (1f - (y - lo) / (hi - lo)).coerceIn(0f, 1f) * 0.7f
                        val axis = (0..6).map { j ->
                            val x = seg.first + (seg.second - seg.first) * j / 6f
                            along(mid, x, y + (browAt(x) - browTop) * bend)
                        }
                        RidgeTracker.track(lum, axis, step * 0.55f, stations = 28, offsets = 8, minStrength = 0.016f, minContrast = 1.8f, skin = skin)
                            ?.let { cands += uv(it, vertical = false) }
                        y += step * 0.4f
                    }
                }
                nms(cands, step * 0.7f, 8).sortedByDescending { it.v }.forEachIndexed { i, c ->
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
            for (t in floatArrayOf(0.12f, 0.26f, 0.4f, 0.6f, 0.74f, 0.88f)) for (deg in floatArrayOf(-14f, 0f, 14f)) {
                val x = hR.first + (hL.first - hR.first) * t
                val tilt = kotlin.math.tan(Math.toRadians(deg.toDouble())).toFloat() * len / 2
                val axis = listOf(along(mid, x - tilt, base), along(mid, x, (base + topY) / 2), along(mid, x + tilt, topY))
                RidgeTracker.track(lum, axis, 0.02f * faceW, stations = 22, offsets = 6, minStrength = 0.022f, minContrast = 2.0f, skin = skin)
                    ?.let { cands += uv(it, vertical = true) }
            }
            nms(cands, 0.045f * faceW, 4).sortedBy { it.v }.forEachIndexed { i, c ->
                out += withMetrics(AnnotLayer.GLABELLA, "Ceño, vertical ${i + 1}", c.res)
            }
            // raíz nasal: horizontales del prócer
            val yN = proj(mesh[168], mid).second; val y6 = proj(mesh[6], mid).second
            val half = (hL.first - hR.first) * 0.32f
            val hc = ArrayList<Cand>()
            for (f in floatArrayOf(-0.2f, 0.15f, 0.5f)) {
                val y = yN + (y6 - yN) * f
                val axis = listOf(along(mid, -half, y), along(mid, 0f, y), along(mid, half, y))
                RidgeTracker.track(lum, axis, 0.015f * faceW, stations = 16, offsets = 5, minStrength = 0.025f, minContrast = 2.2f, skin = skin)
                    ?.let { hc += uv(it, vertical = false) }
            }
            nms(hc, 0.03f * faceW, 1).forEach { out += withMetrics(AnnotLayer.GLABELLA, "Ceño, raíz nasal", it.res) }
        }
        // patas de gallo: abanico de rayos desde el canto externo hacia la sien, fuera de las cejas
        val brows = listOf(BROW_R, BROW_L).map { b -> b.map { mesh[it] }.let { pts -> val c = Pt(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
            pts.map { Pt(c.x + (it.x - c.x) * 1.25f, c.y + (it.y - c.y) * 1.6f) } } }
        val skinNoBrow = { q: Pt -> (skin?.invoke(q) ?: true) && brows.none { inside(q, it) } }
        for ((side, pair) in listOf("derecha" to (mesh[33] to mid(mesh[33], mesh[133])), "izquierda" to (mesh[263] to mid(mesh[263], mesh[362])))) {
            val (canthus, center) = pair
            val ox = canthus.x - center.x; val oy = canthus.y - center.y; val on = hypot(ox, oy).coerceAtLeast(1e-5f)
            val dx = ox / on; val dy = oy / on
            val cands = ArrayList<Cand>()
            for (deg in -50..50 step 10) {
                val a = Math.toRadians(deg.toDouble()); val s = if (dx > 0) 1f else -1f
                val rx = (dx * kotlin.math.cos(a) - dy * kotlin.math.sin(a) * s).toFloat(); val ry = (dx * kotlin.math.sin(a) * s + dy * kotlin.math.cos(a)).toFloat()
                val p0 = Pt(canthus.x + rx * 0.035f * faceW, canthus.y + ry * 0.035f * faceW)
                val p1 = Pt(canthus.x + rx * 0.16f * faceW, canthus.y + ry * 0.16f * faceW)
                RidgeTracker.track(lum, listOf(p0, Pt((p0.x + p1.x) / 2, (p0.y + p1.y) / 2), p1), 0.016f * faceW, stations = 18, offsets = 5,
                    minStrength = 0.018f, minContrast = 1.9f, skin = skinNoBrow)?.let { r ->
                    // coordenadas del rayo: u a lo largo, v = ángulo (en unidades de la cara para la separación)
                    val dd = r.points.map { hypot(it.x - canthus.x, it.y - canthus.y) }
                    cands += Cand(r, dd.min(), dd.max(), deg / 60f * faceW * 0.1f)
                }
            }
            // rayos separados al menos ~13° entre sí
            nms(cands, 0.022f * faceW, 4).sortedBy { it.v }.forEachIndexed { i, c ->
                out += withMetrics(AnnotLayer.CROWS_FEET, "Pata de gallo $side ${i + 1}", c.res)
            }
        }
        // ojeras: entre el párpado inferior y el surco palpebromalar, si es más oscura que la mejilla
        val under = listOf(
            intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133) to intArrayOf(245, 128, 121, 120, 119, 118, 117, 111, 143),
            intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362) to intArrayOf(465, 357, 350, 349, 348, 347, 346, 340, 372),
        )
        val cheeks = listOf(50, 280)
        for ((i, pair) in under.withIndex()) {
            val poly = pair.first.map { mesh[it] } + pair.second.map { mesh[it] }
            val region = meanLum(lum, poly) ?: continue
            val cheek = meanLumDisc(lum, mesh[cheeks[i]], 0.035f * faceW) ?: continue
            // zona infraorbitaria al menos un 12 % más oscura que la mejilla (sombra o pigmento)
            if (region < cheek * 0.88f) {
                val name = "Ojera ${if (i == 0) "derecha" else "izquierda"}"
                val m = if (lab != null && sc != null) {
                    val rl = LineMeasure.meanLab(lab, poly); val cl = LineMeasure.meanLabDisc(lab, mesh[cheeks[i]], 0.035f * faceW)
                    if (rl != null && cl != null) LineMeasure.darkCircle(name, rl, cl, LineMeasure.area(poly), sc) else null
                } else null
                out += AnnotLine(AnnotLayer.DARK_CIRCLES, poly, true, filled = true,
                    metrics = m ?: sc?.let { LineMetrics(name, areaMm2 = LineMeasure.area(poly) * it.first * it.first,
                        deltaL = LineMeasure.lstar(cheek) - LineMeasure.lstar(region), scale = it.second) })
            }
        }
        return out
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)

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
        val y0 = (ys.min() - 0.55f * faceW).coerceAtLeast(0f); val y1 = (ys.max() + 0.08f * faceW).coerceAtMost(srcH.toFloat())
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
                 info: MutableMap<String, Float>? = null): List<AnnotLine> {
        val c = crop(mesh, src.width, src.height)
        val b = bitmap(src, c)
        val m = mesh.map { c.toCrop(it) }
        val labels = runCatching { labelsOf(b, c) }.getOrNull()
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        val ex = extras?.let { e -> MeasureExtras(e.relief?.let { relief(it, c, b.width, b.height) }, e.meta) }
        val lines = FaceAnnotations.fromMesh(m, labels, lum(b), LabImage.fromArgb(px, b.width, b.height), ex, info)
        if (b !== src) b.recycle()
        return lines.map { l -> l.copy(points = l.points.map { c.toPhoto(it) }) }
    }
}

/** Analiza una foto (ya editada y en su encuadre) y devuelve sus líneas; con caché por clave. */
class PhotoAnnotator(private val context: Context) {
    suspend fun annotate(bmp: Bitmap, zone: Zone, extras: MeasureExtras? = null): List<AnnotLine> = analyze(bmp, zone, extras).first

    /**
     * Líneas y, si no hay arrugas medibles, el motivo para el usuario (rostro no detectado entero,
     * zona corporal…).
     */
    suspend fun analyze(bmp: Bitmap, zone: Zone, extras: MeasureExtras? = null): Pair<List<AnnotLine>, String?> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        if (!enabled) return@withContext emptyList<AnnotLine>() to "Análisis no disponible"
        val spec = ZoneAlignment.of(zone)
        if (spec.detector == Detector.FACE) {
            // malla completa de MediaPipe (nariz, labios, ojos y pasillos de los surcos con su topología real)
            val mesh = meshRobust(bmp)
            if (mesh != null && mesh.size >= 468) {
                // análisis en el recorte de la cara a resolución fija (ver FaceCrop)
                val seg = HairSkinSegmenter.createOrNull(context)
                val info = HashMap<String, Float>()
                val lines = try {
                    FaceCrop.annotate(mesh, bmp, { b, _ -> seg?.labels(b) }, extras, info)
                } finally { seg?.close() }
                val note = if (lines.none { it.metrics != null }) "No se encontraron arrugas ni surcos medibles en esta foto (piel lisa, " +
                    "poca resolución de la cara o luz muy plana)." else beardNote(info)
                return@withContext lines to note
            }
            // sin malla: puntos de ML Kit; surcos estimados y ajustados a la sombra, con medidas (escala
            // por la distancia interpupilar), sin frente, ceño, patas de gallo ni ojeras
            val pts = mlkitPoints(bmp)
            val err = MediaPipeFace.lastError?.let { " (detalle: $it)" } ?: ""
            if (pts.size >= LiveDecision.MIN_POINTS) {
                val labels = HairSkinSegmenter.createOrNull(context)?.use { it.labels(bmp) }
                val info = HashMap<String, Float>()
                val lines = FaceAnnotations.face(pts, labels, lumOf(bmp), labOf(bmp), info)
                return@withContext lines to ("No se pudo obtener la malla facial completa$err: solo se miden los surcos " +
                    "(nasogeniano, marioneta, valle lagrimal); frente, ceño, patas de gallo y ojeras necesitan la malla." +
                    (beardNote(info)?.let { " $it" } ?: ""))
            }
            return@withContext emptyList<AnnotLine>() to "No se detectó ninguna cara en la foto$err."
        }
        val mask = BodyRegion.of(zone, bmp) { b -> SubjectSegmenter().use { it.segment(b) } }
        (mask?.let { FaceAnnotations.body(it) } ?: emptyList()) to "Las medidas de arrugas y surcos son solo para zonas del rostro."
    }

    /** Aviso si hay barba o bigote: los surcos de la zona solo se trazan sobre la piel visible. */
    private fun beardNote(info: Map<String, Float>): String? = info["vello"]?.takeIf { it >= 0.08f }?.let {
        "Barba o bigote en ${(it * 100).roundToInt()} % de la parte inferior de la cara: los surcos de esa zona se trazan y " +
            "miden solo sobre la piel visible (las líneas marcadas con ✱ son parciales)."
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
            .also { if (it == null && MediaPipeFace.lastError == null) MediaPipeFace.lastError = "MediaPipe no encontró la cara" }
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
        private val notes = HashMap<String, String?>()
        fun note(key: String): String? = synchronized(cache) { notes[key] }
        fun putNote(key: String, n: String?) = synchronized(cache) { notes[key] = n }
        fun cached(key: String): List<AnnotLine>? = synchronized(cache) { cache[key] }
        fun put(key: String, v: List<AnnotLine>) = synchronized(cache) { cache[key] = v }
    }
}
