package com.photomed.app.align

import android.content.Context
import android.graphics.Bitmap
import com.photomed.app.data.Zone
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Capas de anotación estética que se pueden mostrar sobre una foto (visor y comparar). */
enum class AnnotLayer(val label: String) {
    CONTOUR("Contorno"), HAIRLINE("Línea del pelo"), EYES("Ojos y cejas"), NOSE("Nariz"), LIPS("Labios"), FOLDS("Surcos"),
}

/** Una línea de anotación (x del ancho, y en unidades del ancho). [estimated]: posición estimada, no medida. */
data class AnnotLine(val layer: AnnotLayer, val points: List<Pt>, val closed: Boolean, val estimated: Boolean = false)

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
