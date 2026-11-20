package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Medidas de una línea facial (surco, arruga) o de una zona (ojera). Todas son **orientativas**:
 * se estiman a partir de una sola foto 2D y no sustituyen a la perfilometría ni a la escala clínica.
 *
 * - [lengthMm]: longitud proyectada del trazado (escala por el diámetro del iris).
 * - [widthMm]: ancho del valle de sombra a media profundidad (FWHM del perfil transversal).
 * - [contrastL]: caída de luminosidad CIE L* del fondo del valle respecto a la piel a ambos lados.
 * - [depthMm]: profundidad estimada por sombreado con un modelo de surco en V bajo luz difusa.
 * - [grade]: grado 0–5 orientativo al estilo de la escala de Lemperle, desde [depthMm].
 * - Ojeras: [areaMm2], ΔL*, ΔE*ab e ITA° frente a la mejilla, y el [type] probable.
 */
data class LineMetrics(
    val name: String,
    val lengthMm: Float? = null,
    val widthMm: Float? = null,
    val contrastL: Float? = null,
    val depthMm: Float? = null,
    val grade: Int? = null,
    val areaMm2: Float? = null,
    val deltaL: Float? = null,
    val deltaE: Float? = null,
    val ita: Float? = null,
    val itaRef: Float? = null,
    val type: String? = null,
    /** Referencia de la escala en mm: "iris", "interpupilar" o "sensor de profundidad". */
    val scale: String = "iris",
    /** Origen de la profundidad: "sombreado" (una foto) o "relieve" (estéreo fotométrico). */
    val depthSource: String = "sombreado",
    /** Advertencia sobre la medida (p. ej. surco parcialmente cubierto por barba o bigote). */
    val note: String? = null,
    /** Medidas de un rasgo (etiqueta con unidad, valor): labios, ojos, cejas, nariz, línea del pelo… */
    val features: List<Pair<String, Float>> = emptyList(),
) {
    /** Medida de un rasgo (no de una arruga ni de una zona). */
    val isFeature: Boolean get() = features.isNotEmpty()
}

/** Imagen en CIE L*a*b* (D65), para medir el color de la piel. */
class LabImage(val width: Int, val height: Int, val l: FloatArray, val a: FloatArray, val b: FloatArray) {
    companion object {
        fun fromArgb(px: IntArray, w: Int, h: Int): LabImage {
            val l = FloatArray(px.size); val a = FloatArray(px.size); val b = FloatArray(px.size)
            for (i in px.indices) {
                val lab = LineMeasure.srgbToLab(px[i])
                l[i] = lab[0]; a[i] = lab[1]; b[i] = lab[2]
            }
            return LabImage(w, h, l, a, b)
        }
    }
}

object LineMeasure {
    /** Diámetro horizontal visible del iris (blanco a blanco) de un adulto, mm. */
    const val HVID_MM = 11.7f
    /** Distancia interpupilar media de un adulto, mm (respaldo si el iris no es fiable). */
    const val IPD_MM = 63f

    /**
     * Milímetros por unidad (unidades del ancho de la imagen) y la referencia usada. Iris: puntos
     * 469–471 (derecho) y 474–476 (izquierdo) de MediaPipe; se toma el mayor (el giro de la cabeza
     * lo acorta). Si la proporción iris / interpupilar no es plausible, la interpupilar.
     */
    fun scale(mesh: List<Pt>): Pair<Float, String>? {
        if (mesh.size < 468) return null
        val pR = if (mesh.size > 473) mesh[468] else mid(mesh[33], mesh[133])
        val pL = if (mesh.size > 473) mesh[473] else mid(mesh[263], mesh[362])
        val ipd = d(pR, pL).takeIf { it > 1e-4f } ?: return null
        if (mesh.size >= 478) {
            val iris = max(d(mesh[469], mesh[471]), d(mesh[474], mesh[476]))
            if (iris / ipd in 0.14f..0.25f) return HVID_MM / iris to "iris"
        }
        return IPD_MM / ipd to "interpupilar"
    }

    /**
     * Ganancia del sombreado: en fotos clínicas la caída de luz del surco incluye sombra proyectada,
     * interreflexiones y cambios de pigmento, que el modelo lambertiano interpreta como pendiente; se
     * atenúa con un factor global ajustado para que las medianas caigan en los órdenes de magnitud
     * publicados (arrugas finas de décimas de mm; surco nasogeniano de 1–3 mm). Pendiente de
     * calibración frente a perfilometría.
     */
    const val SHADING_GAIN = 0.25f

    /** Perfil transversal de una línea: ancho, caída de L* y profundidad por sombreado. */
    data class Profile(val widthMm: Float, val openMm: Float, val contrastL: Float, val depthMm: Float)

    /**
     * Mide el valle a lo largo de [pts] (unidades del ancho) sobre [lum] (luminancia 0..1, gamma
     * sRGB). En cada punto se toma el perfil perpendicular (±[halfMm]); el fondo es el mínimo cerca
     * del trazado y la piel de referencia, la media de los dos hombros exteriores. Se usa la mediana.
     *
     * Profundidad (forma a partir del sombreado, en 1D): con luz frontal sobre piel lambertiana, la
     * luminancia lineal relativa a la piel vecina es ρ = cos θ (θ, inclinación de la pared del surco),
     * así que la pendiente es tan θ = √(1/ρ² − 1); integrándola desde el borde del valle hasta el
     * fondo se obtiene la profundidad (media de las dos paredes).
     */
    fun profile(lum: GrayImage, pts: List<Pt>, mmPerUnit: Float, halfMm: Float = 3f): Profile? {
        if (pts.size < 3) return null
        val w = lum.width.toFloat()
        val pxPerMm = w / mmPerUnit
        val half = max(5f, halfMm * pxPerMm)
        val n = (half * 2).roundToInt().coerceIn(10, 80)
        val widths = ArrayList<Float>(); val opens = ArrayList<Float>(); val drops = ArrayList<Float>(); val depths = ArrayList<Float>()
        for (i in 1 until pts.size - 1) {
            val a = pts[i - 1]; val b = pts[i + 1]
            val tx = b.x - a.x; val ty = b.y - a.y; val tn = hypot(tx, ty).takeIf { it > 1e-7f } ?: continue
            val nx = -ty / tn; val ny = tx / tn
            val cx = pts[i].x * w; val cy = pts[i].y * w
            val prof = FloatArray(n + 1) { k ->
                val o = -half + 2 * half * k / n
                lstar(lum.sample(cx + nx * o, cy + ny * o))
            }
            if (prof.any { it.isNaN() }) continue
            val c = n / 2
            // fondo: mínimo dentro de ±1/3 del perfil
            val r = n / 6
            var mi = c; for (k in c - r..c + r) if (prof[k] < prof[mi]) mi = k
            val q = max(2, n / 6)
            val left = (0 until q).map { prof[it] }.average().toFloat()
            val right = (n - q + 1..n).map { prof[it] }.average().toFloat()
            val base = (left + right) / 2
            val drop = base - prof[mi]
            if (drop <= 0.3f) { drops += 0f; continue }
            fun span(level: Float): Float {
                var lo = mi; while (lo > 0 && prof[lo - 1] < level) lo--
                var hi = mi; while (hi < n && prof[hi + 1] < level) hi++
                return (hi - lo + 1) * (2 * half / n)
            }
            widths += span(base - drop / 2) / pxPerMm
            opens += span(base - drop * 0.15f) / pxPerMm
            drops += drop
            // forma por sombreado (1D): con luz frontal, Y/Y_piel = cos θ, pendiente tan θ = √(1/ρ² − 1);
            // la profundidad es la integral de la pendiente desde donde empieza el valle hasta el fondo,
            // a cada lado (media de ambos)
            val dx = 2 * half / n / pxPerMm
            // cada pared, respecto a la piel de su propio lado
            fun side(dir: Int): Float {
                val yb = yOf(if (dir < 0) left else right).coerceAtLeast(1e-4f)
                var k = mi; var acc = 0f
                while (k in 0..n) {
                    val rho = (yOf(prof[k]) / yb).coerceIn(0.2f, 1f)
                    if (rho >= 0.985f) break
                    acc += sqrt(1f / (rho * rho) - 1f).coerceAtMost(3f) * dx
                    k += dir
                }
                return acc
            }
            depths += (side(-1) + side(1)) / 2 * SHADING_GAIN
        }
        if (drops.size < 2) return null
        val drop = median(drops)
        if (widths.isEmpty() || drop < 0.5f) return Profile(0f, 0f, drop, 0f)
        val width = median(widths); val open = median(opens)
        val depth = median(depths).coerceIn(0f, 8f)
        return Profile(width, open, drop, depth)
    }

    /**
     * Grado orientativo 0–5 (al estilo de Lemperle: 0 sin arruga, 1 apenas perceptible, 2 superficial,
     * 3 moderadamente profunda, 4 profunda, 5 muy profunda) desde la profundidad estimada en mm.
     * Umbrales propios de la app, pendientes de calibración con fotos graduadas por clínicos.
     */
    fun grade(depthMm: Float): Int = GRADE_MM.indexOfFirst { depthMm < it }.let { if (it < 0) 5 else it }
    val GRADE_MM = floatArrayOf(0.1f, 0.25f, 0.5f, 0.9f, 1.5f)

    /** Longitud de una polilínea (unidades del ancho). */
    fun length(pts: List<Pt>): Float = (1 until pts.size).sumOf { d(pts[it - 1], pts[it]).toDouble() }.toFloat()

    /** Área de un polígono (unidades del ancho²). */
    fun area(poly: List<Pt>): Float {
        var s = 0f; var j = poly.size - 1
        for (i in poly.indices) { s += (poly[j].x + poly[i].x) * (poly[j].y - poly[i].y); j = i }
        return abs(s) / 2
    }

    /** Medidas de una línea detectada; con [relief] (mm), la profundidad sale del relieve medido. */
    fun line(name: String, pts: List<Pt>, lum: GrayImage, scale: Pair<Float, String>, halfMm: Float = 3f, relief: GrayImage? = null): LineMetrics {
        val mm = scale.first
        val p = profile(lum, pts, mm, halfMm)
        val rd = relief?.let { reliefDepth(it, pts, mm, halfMm) }
        val depth = rd ?: p?.depthMm
        return LineMetrics(name, lengthMm = length(pts) * mm, widthMm = p?.widthMm?.takeIf { it > 0f },
            contrastL = p?.contrastL, depthMm = depth, grade = depth?.let { grade(it) }, scale = scale.second,
            depthSource = if (rd != null) "relieve" else "sombreado")
    }

    /**
     * Profundidad en el relieve medido (mm, hacia la cámara): en cada punto, perfil perpendicular
     * ±[halfMm]; profundidad = media de los dos hombros − mínimo cerca del trazado. Mediana; null si
     * menos de la mitad de los puntos tienen relieve.
     */
    fun reliefDepth(relief: GrayImage, pts: List<Pt>, mmPerUnit: Float, halfMm: Float): Float? {
        if (pts.size < 3) return null
        val w = relief.width.toFloat()
        val pxPerMm = w / mmPerUnit
        val half = max(4f, halfMm * pxPerMm)
        val n = (half * 2).roundToInt().coerceIn(8, 80)
        val out = ArrayList<Float>()
        for (i in 1 until pts.size - 1) {
            val a = pts[i - 1]; val b = pts[i + 1]
            val tx = b.x - a.x; val ty = b.y - a.y; val tn = hypot(tx, ty).takeIf { it > 1e-7f } ?: continue
            val nx = -ty / tn; val ny = tx / tn
            val cx = pts[i].x * w; val cy = pts[i].y * w
            val prof = FloatArray(n + 1) { k -> val o = -half + 2 * half * k / n; relief.sample(cx + nx * o, cy + ny * o) }
            if (prof.count { it.isNaN() } > n / 4) continue
            val c = n / 2; val r = n / 6
            val mid = (c - r..c + r).map { prof[it] }.filter { !it.isNaN() }.minOrNull() ?: continue
            val q = max(2, n / 6)
            val l = (0 until q).map { prof[it] }.filter { !it.isNaN() }; val rr = (n - q + 1..n).map { prof[it] }.filter { !it.isNaN() }
            if (l.isEmpty() || rr.isEmpty()) continue
            out += ((l.average() + rr.average()) / 2 - mid).toFloat().coerceAtLeast(0f)
        }
        if (out.size < (pts.size - 2) / 2 || out.isEmpty()) return null
        return median(out)
    }

    /** Color medio L*a*b* dentro de un polígono o null si hay pocos píxeles. */
    fun meanLab(lab: LabImage, poly: List<Pt>): FloatArray? {
        val w = lab.width.toFloat()
        val xs = poly.map { it.x * w }; val ys = poly.map { it.y * w }
        val acc = DoubleArray(3); var n = 0
        for (y in max(0, ys.min().toInt())..min(lab.height - 1, ys.max().toInt())) for (x in max(0, xs.min().toInt())..min(lab.width - 1, xs.max().toInt())) {
            var inside = false; var j = poly.size - 1
            for (i in poly.indices) {
                if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
                j = i
            }
            if (inside) { val k = y * lab.width + x; acc[0] += lab.l[k].toDouble(); acc[1] += lab.a[k].toDouble(); acc[2] += lab.b[k].toDouble(); n++ }
        }
        return if (n < 10) null else floatArrayOf((acc[0] / n).toFloat(), (acc[1] / n).toFloat(), (acc[2] / n).toFloat())
    }

    fun meanLabDisc(lab: LabImage, c: Pt, r: Float): FloatArray? =
        meanLab(lab, (0 until 16).map { k -> val t = 2 * Math.PI * k / 16; Pt(c.x + r * kotlin.math.cos(t).toFloat(), c.y + r * kotlin.math.sin(t).toFloat()) })

    /** Ángulo tipológico individual (ITA°) = atan((L* − 50) / b*). */
    fun ita(lab: FloatArray): Float = Math.toDegrees(atan2((lab[0] - 50f).toDouble(), lab[2].toDouble())).toFloat()

    /**
     * Medidas de una ojera frente a la mejilla: ΔL* (más oscura, positivo), ΔE*ab, ITA° de ambas y
     * tipo probable por el tono: vascular (azul-violáceo: a* sube o b* baja), pigmentaria (marrón:
     * ITA menor con b* que no baja) o estructural / sombra (solo luminosidad).
     */
    fun darkCircle(name: String, region: FloatArray, cheek: FloatArray, areaUnits: Float, scale: Pair<Float, String>): LineMetrics {
        val dl = cheek[0] - region[0]; val da = region[1] - cheek[1]; val db = region[2] - cheek[2]
        val de = sqrt(dl * dl + da * da + db * db)
        val type = when {
            db < -1.5f || (da > 1.5f && db < 0.5f) -> "vascular (azulado o violáceo)"
            db >= -0.5f && sqrt(da * da + db * db) >= 1.5f -> "pigmentaria (marrón)"
            else -> "estructural o sombra"
        }
        return LineMetrics(name, areaMm2 = areaUnits * scale.first * scale.first, deltaL = dl, deltaE = de,
            ita = ita(region), itaRef = ita(cheek), type = type, scale = scale.second)
    }

    /** L* desde la luminancia gamma sRGB (0..1). */
    fun lstar(v: Float): Float = if (v.isNaN()) Float.NaN else labF(lin(v)) * 116f - 16f
    private fun yOf(lstar: Float): Float { val f = (lstar + 16f) / 116f; return if (f > 6f / 29f) f * f * f else 3 * (6f / 29f).pow(2) * (f - 4f / 29f) }
    private fun lin(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    private fun labF(t: Float): Float = if (t > 0.008856f) t.pow(1f / 3f) else 7.787f * t + 16f / 116f

    fun srgbToLab(c: Int): FloatArray {
        val r = lin(((c shr 16) and 0xFF) / 255f); val g = lin(((c shr 8) and 0xFF) / 255f); val b = lin((c and 0xFF) / 255f)
        val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f
        val fx = labF(x); val fy = labF(y); val fz = labF(z)
        return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    private fun median(v: List<Float>) = v.sorted()[v.size / 2]
    private fun d(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
}

/**
 * Numeración de las líneas medidas para relacionar imagen y tabla: el mismo número en la foto y en
 * su fila. Orden: arrugas y surcos, zonas (ojeras) y rasgos; dentro de cada grupo, por capa y nombre.
 * Con varias fotos (comparar) se numera la unión de nombres, igual en todas.
 */
object MeasureNumbering {
    fun group(m: LineMetrics) = when { m.isFeature -> 2; m.areaMm2 != null || m.deltaE != null -> 1; else -> 0 }

    fun of(sets: List<List<AnnotLine>>): Map<String, Int> {
        val byName = LinkedHashMap<String, AnnotLine>()
        for (s in sets) for (l in s) { val m = l.metrics ?: continue; byName.putIfAbsent(m.name, l) }
        return byName.values.sortedWith(compareBy({ group(it.metrics!!) }, { it.layer.ordinal }, { it.metrics!!.name }))
            .mapIndexed { i, l -> l.metrics!!.name to i + 1 }.toMap()
    }

    /** Punto donde se dibuja el número de una línea: centro de las zonas, mitad de las abiertas, lado derecho de los contornos cerrados. */
    fun anchor(l: AnnotLine): Pt = candidates(l).first()

    /** Posiciones posibles del número sobre la línea, en orden de preferencia. */
    private fun candidates(l: AnnotLine): List<Pt> {
        val p = l.points
        if (l.filled) return listOf(Pt(p.map { it.x }.average().toFloat(), p.map { it.y }.average().toFloat())) + p
        if (l.closed) return listOf(p.maxBy { it.x }, p.minBy { it.y }, p.maxBy { it.y }, p.minBy { it.x }) +
            listOf(0.125f, 0.375f, 0.625f, 0.875f).map { p[(it * (p.size - 1)).toInt()] }
        return listOf(0.5f, 0.3f, 0.7f, 0.15f, 0.85f, 0f, 1f).map { p[(it * (p.size - 1)).toInt()] }
    }

    /**
     * Posición de cada número sin solaparse con los ya colocados ([r]: radio de la pastilla en
     * unidades del ancho); si ninguna posición de la línea queda libre, la menos solapada.
     */
    fun place(lines: List<AnnotLine>, numbers: Map<String, Int>, r: Float): List<Pair<AnnotLine, Pt>> {
        val placed = ArrayList<Pair<AnnotLine, Pt>>()
        for (l in lines.filter { it.metrics?.name?.let { n -> numbers[n] } != null && it.points.size >= 2 }.sortedBy { numbers[it.metrics!!.name] }) {
            val c = candidates(l)
            fun clearance(q: Pt) = placed.minOfOrNull { kotlin.math.hypot(it.second.x - q.x, it.second.y - q.y) } ?: Float.MAX_VALUE
            val best = c.firstOrNull { clearance(it) >= 2.1f * r } ?: c.maxBy { clearance(it) }
            placed += l to best
        }
        return placed
    }
}
