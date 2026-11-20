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
                     val filled: Boolean = false)

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
    fun face(points: Map<String, Pt>, labels: FaceLabels?, lum: GrayImage?): List<AnnotLine> {
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
        // surcos (estimados y ajustados al valle de sombra)
        val oval = g("oval_", 36)
        if (alaL != null && alaR != null && upTop != null) {
            val (aL, aR) = if (alaL.x <= alaR.x) alaL to alaR else alaR to alaL
            val cL = upTop.first(); val cR = upTop.last()
            for ((ala, corner, s) in listOf(Triple(aL, cL, -1f), Triple(aR, cR, 1f))) {
                // nasogeniano: del ala, por fuera de la comisura
                val nl = curve(at(ala, s * 0.10f, 0.02f), at(lerp(ala, corner, 0.5f), s * 0.20f, 0f), at(corner, s * 0.13f, -0.10f))
                out += refine(AnnotLayer.FOLDS, nl, lum, ipd)
                // línea de marioneta: de la comisura hacia el borde mandibular
                if (oval != null) {
                    val target = at(corner, s * 0.05f, -0.75f)
                    val jaw = oval.minBy { dist(it, target) }
                    val e = lerp(corner, jaw, 0.6f)
                    val ml = curve(at(corner, s * 0.03f, -0.04f), at(lerp(corner, e, 0.5f), s * 0.03f, 0f), e)
                    out += refine(AnnotLayer.FOLDS, ml, lum, ipd)
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
                out += refine(AnnotLayer.FOLDS, tt, lum, ipd)
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
    fun fromMesh(mesh: List<Pt>, labels: FaceLabels?, lum: GrayImage?): List<AnnotLine> {
        if (mesh.size < 468) return face(keysFromMesh(mesh), labels, lum)
        val keys = FaceKeys.canonical(keysFromMesh(mesh))
        val out = ArrayList<AnnotLine>()
        val p = if (labels != null) HairSkin.snapOval(labels, keys) else keys
        (0 until 36).mapNotNull { p["oval_$it"] }.takeIf { it.size == 36 }?.let { out += AnnotLine(AnnotLayer.CONTOUR, it, true) }
        labels?.let { HairSkin.hairline(it, keys)?.points?.let { h -> out += AnnotLine(AnnotLayer.HAIRLINE, h, false) } }
        fun chain(idx: IntArray) = idx.map { mesh[it] }
        out += AnnotLine(AnnotLayer.EYES, chain(EYE_R), true); out += AnnotLine(AnnotLayer.EYES, chain(EYE_L), true)
        out += AnnotLine(AnnotLayer.EYES, chain(BROW_R), true); out += AnnotLine(AnnotLayer.EYES, chain(BROW_L), true)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_DORSAL_R).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_DORSAL_L).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_ALA_R).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_ALA_L).toTypedArray(), perSeg = 4), false)
        out += AnnotLine(AnnotLayer.NOSE, FaceAnnotations.curve(*chain(NOSE_TIP).toTypedArray(), perSeg = 6), false)
        out += AnnotLine(AnnotLayer.LIPS, chain(LIPS_OUTER), true); out += AnnotLine(AnnotLayer.LIPS, chain(LIPS_INNER), true)
        if (lum != null) out += folds(mesh, lum, labels)
        return out
    }

    /** Surcos detectados en la imagen (solo los encontrados), con el ancho del pasillo según la cara. */
    fun folds(mesh: List<Pt>, lum: GrayImage, labels: FaceLabels? = null): List<AnnotLine> {
        val faceW = dist(mesh[234], mesh[454]).coerceAtLeast(1e-3f)
        val skin = labels?.let { l -> { q: Pt -> l[(q.x * l.width).roundToInt(), (q.y * l.width).roundToInt()] == FaceLabels.FACE_SKIN } }
        val folds = FOLD_CORRIDORS.mapNotNull { (name, idx) ->
            RidgeTracker.track(lum, idx.map { mesh[it] }, corridorHalf(name) * faceW, skin = skin,
                anchorStart = name == "nasogeniano")?.let { AnnotLine(AnnotLayer.FOLDS, it.points, false) }
        }
        return folds + wrinkles(mesh, lum, labels, faceW)
    }

    /**
     * Arrugas de la frente (horizontales), del ceño (verticales entre las cejas), patas de gallo
     * (rayos desde el canto externo) y ojeras (zona infraorbitaria más oscura que la mejilla).
     */
    fun wrinkles(mesh: List<Pt>, lum: GrayImage, labels: FaceLabels?, faceW: Float): List<AnnotLine> {
        val out = ArrayList<AnnotLine>()
        val skin = labels?.let { l -> { q: Pt -> l[(q.x * l.width).roundToInt(), (q.y * l.width).roundToInt()] == FaceLabels.FACE_SKIN } }
        val eR = mesh[468].takeIf { mesh.size > 473 } ?: mesh[159]; val eL = mesh[473].takeIf { mesh.size > 473 } ?: mesh[386]
        val ipd = dist(eR, eL).coerceAtLeast(1e-4f)
        val ex = Pt((eL.x - eR.x) / ipd, (eL.y - eR.y) / ipd)
        val up = Pt(ex.y, -ex.x).let { if (it.y > 0) Pt(-it.x, -it.y) else it }
        fun along(p: Pt, a: Float, b: Float) = Pt(p.x + ex.x * a + up.x * b, p.y + ex.y * a + up.y * b)
        fun proj(p: Pt, o: Pt) = Pair((p.x - o.x) * ex.x + (p.y - o.y) * ex.y, (p.x - o.x) * up.x + (p.y - o.y) * up.y)
        val mid = Pt((eR.x + eL.x) / 2, (eR.y + eL.y) / 2)
        // frente: franjas horizontales entre las cejas y la línea del pelo (o el borde superior del óvalo)
        run {
            val browTop = intArrayOf(70, 63, 105, 66, 107, 336, 296, 334, 293, 300).maxOf { proj(mesh[it], mid).second }
            // hasta el borde superior del rostro (la línea del pelo puede caer baja con flequillo o canas)
            val top = proj(mesh[10], mid).second
            val lo = browTop + 0.035f * faceW; val hi = top - 0.015f * faceW
            val xl = proj(mesh[70], mid).first + 0.02f * faceW; val xr = proj(mesh[300], mid).first - 0.02f * faceW
            val step = 0.03f * faceW
            if (hi - lo > step && xr - xl > 0.2f * faceW) {
                val found = ArrayList<Pair<Float, RidgeTracker.Result>>()
                var y = lo
                while (y <= hi) {
                    val axis = listOf(along(mid, xl, y), along(mid, (xl + xr) / 2, y), along(mid, xr, y))
                    RidgeTracker.track(lum, axis, step * 0.6f, stations = 30, offsets = 8, minStrength = 0.016f, minContrast = 1.8f, skin = skin)
                        ?.let { found += y to it }
                    y += step * 0.5f
                }
                val kept = ArrayList<Pair<Float, RidgeTracker.Result>>()
                for (f in found.sortedByDescending { it.second.strength }) {
                    val fy = f.second.points.map { proj(it, mid).second }.average().toFloat()
                    if (kept.none { k -> kotlin.math.abs(k.second.points.map { proj(it, mid).second }.average().toFloat() - fy) < step * 0.8f }) kept += f
                }
                kept.take(6).forEach { out += AnnotLine(AnnotLayer.FOREHEAD, it.second.points, false) }
            }
        }
        // ceño: líneas verticales junto a la cabeza de cada ceja
        run {
            val hR = mesh[107]; val hL = mesh[336]
            val base = proj(mesh[168], mid).second
            val topY = maxOf(proj(hR, mid).second, proj(hL, mid).second) + 0.09f * faceW
            for (t in floatArrayOf(0.28f, 0.72f)) {
                val x = proj(hR, mid).first + (proj(hL, mid).first - proj(hR, mid).first) * t
                val axis = listOf(along(mid, x, base), along(mid, x, (base + topY) / 2), along(mid, x, topY))
                RidgeTracker.track(lum, axis, 0.028f * faceW, stations = 20, offsets = 8, minStrength = 0.025f, minContrast = 2.0f, skin = skin)
                    ?.let { out += AnnotLine(AnnotLayer.GLABELLA, it.points, false) }
            }
        }
        // patas de gallo: rayos desde el canto externo hacia la sien
        for ((canthus, center) in listOf(mesh[33] to Pt((mesh[33].x + mesh[133].x) / 2, (mesh[33].y + mesh[133].y) / 2),
                                         mesh[263] to Pt((mesh[263].x + mesh[362].x) / 2, (mesh[263].y + mesh[362].y) / 2))) {
            val ox = canthus.x - center.x; val oy = canthus.y - center.y; val on = hypot(ox, oy).coerceAtLeast(1e-5f)
            val dx = ox / on; val dy = oy / on
            for (deg in floatArrayOf(-38f, -14f, 10f, 34f)) {
                val a = Math.toRadians(deg.toDouble()); val s = if (dx > 0) 1f else -1f
                val rx = (dx * kotlin.math.cos(a) - dy * kotlin.math.sin(a) * s).toFloat(); val ry = (dx * kotlin.math.sin(a) * s + dy * kotlin.math.cos(a)).toFloat()
                val p0 = Pt(canthus.x + rx * 0.05f * faceW, canthus.y + ry * 0.05f * faceW)
                val p1 = Pt(canthus.x + rx * 0.17f * faceW, canthus.y + ry * 0.17f * faceW)
                RidgeTracker.track(lum, listOf(p0, Pt((p0.x + p1.x) / 2, (p0.y + p1.y) / 2), p1), 0.02f * faceW, stations = 16, offsets = 6,
                    minStrength = 0.022f, minContrast = 2.0f, skin = skin)?.let { out += AnnotLine(AnnotLayer.CROWS_FEET, it.points, false) }
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
            if (region < cheek * 0.88f) out += AnnotLine(AnnotLayer.DARK_CIRCLES, poly, true, filled = true)
        }
        return out
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
    fun corridorHalf(name: String) = if (name == "valle lagrimal") 0.035f else 0.05f

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
    fun refine(layer: AnnotLayer, line: List<Pt>, lum: GrayImage?, ipd: Float): AnnotLine {
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
                val v = l(Pt(line[i].x + nx * d, line[i].y + ny * d)); if (v.isNaN()) continue
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

/** Analiza una foto (ya editada y en su encuadre) y devuelve sus líneas; con caché por clave. */
class PhotoAnnotator(private val context: Context) {
    suspend fun annotate(bmp: Bitmap, zone: Zone): List<AnnotLine> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val spec = ZoneAlignment.of(zone)
        if (spec.detector == Detector.FACE) {
            // malla completa de MediaPipe (nariz, labios, ojos y pasillos de los surcos con su topología real)
            val mesh = runCatching { MediaPipeFace(context).use { it.mesh(bmp) } }.getOrNull()
            if (mesh != null && mesh.size >= 468) {
                val labels = HairSkinSegmenter.createOrNull(context)?.use { it.labels(bmp) }
                return@withContext FaceAnnotations.fromMesh(mesh, labels, lumOf(bmp))
            }
            val pts = LandmarkDetector(stream = false, context, FaceEngine.get(context)).use { it.detect(Detector.FACE, bmp) }
            if (pts.size >= LiveDecision.MIN_POINTS) {
                val labels = HairSkinSegmenter.createOrNull(context)?.use { it.labels(bmp) }
                val lum = lumOf(bmp)
                return@withContext FaceAnnotations.face(pts, labels, lum)
            }
        }
        val mask = BodyRegion.of(zone, bmp) { b -> SubjectSegmenter().use { it.segment(b) } } ?: return@withContext emptyList()
        FaceAnnotations.body(mask)
    }

    /** Luminancia (0..1) sin estandarizar, para buscar valles de sombra. */
    private fun lumOf(bmp: Bitmap): GrayImage {
        val w = minOf(bmp.width, 480); val h = (bmp.height * w / bmp.width.toFloat()).roundToInt()
        val s = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h).also { s.getPixels(it, 0, w, 0, 0, w, h) }
        return GrayImage(w, h, FloatArray(px.size) { i -> val c = px[i]; (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f })
    }

    companion object {
        private val cache = object : LinkedHashMap<String, List<AnnotLine>>(16, 0.75f, true) {
            override fun removeEldestEntry(e: MutableMap.MutableEntry<String, List<AnnotLine>>) = size > 40
        }
        fun cached(key: String): List<AnnotLine>? = synchronized(cache) { cache[key] }
        fun put(key: String, v: List<AnnotLine>) = synchronized(cache) { cache[key] = v }
    }
}
