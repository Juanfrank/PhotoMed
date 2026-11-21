package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Línea mandibular y papada.
 *
 * - **De frente u oblicua** ([front]): la línea mandibular es el borde inferior del contorno facial
 *   (ya ajustado a la sombra de la mandíbula, de ángulo a ángulo pasando por el mentón). Medidas:
 *   - nitidez del borde: contraste cara / cuello a cada lado del borde (%), y ancho de la transición
 *     (distancia en que la luz pasa del 10 al 90 % del cambio; menor = borde más definido);
 *   - rectitud de cada lado (ángulo → mentón): desviación máxima del borde respecto a la recta
 *     (hacia abajo = flacidez, papada lateral) y rectitud en % (100 = recta);
 *   - ángulo goníaco (rama → ángulo → mentón) en el lado visible, con la cara girada (de frente la
 *     rama se ve de canto y el ángulo no es medible).
 * - **Papada de frente** ([papada]): pliegue oscuro bajo el mentón (surco submentoniano), separado de la
 *   línea mandibular, con sus medidas de surco y la distancia al mentón.
 * - **De perfil** ([profile], silueta del paciente): ángulo cervicomentoniano (submentón → punto
 *   cervical → cuello; ideal ≈ 90–120°) y papada como el contorno submentoniano, con su convexidad
 *   respecto a la recta mentón → punto cervical.
 *
 * Coordenadas: unidades del ancho de la imagen analizada (como el resto de líneas).
 */
object Jawline {
    /** Índices de la mandíbula en el óvalo (36 puntos, orden de la malla): de un ángulo al otro por el mentón. */
    private val JAW = 10..26
    private const val CHIN = 18; private const val GONION_A = 12; private const val GONION_B = 24

    private fun d(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
    private fun fmt(v: Float) = String.format(java.util.Locale.getDefault(), "%.1f", v)

    /** Distancia con signo de [p] a la recta [a]→[b]; positiva del lado contrario a [ref]. */
    private fun signedDist(p: Pt, a: Pt, b: Pt, ref: Pt): Float {
        val vx = b.x - a.x; val vy = b.y - a.y; val n = hypot(vx, vy).coerceAtLeast(1e-6f)
        val s = ((p.x - a.x) * vy - (p.y - a.y) * vx) / n
        val sr = ((ref.x - a.x) * vy - (ref.y - a.y) * vx) / n
        return if (sr > 0) -s else s
    }

    fun angleDeg(a: Pt, v: Pt, b: Pt): Float? {
        val ax = a.x - v.x; val ay = a.y - v.y; val bx = b.x - v.x; val by = b.y - v.y
        val na = hypot(ax, ay); val nb = hypot(bx, by)
        if (na < 1e-6f || nb < 1e-6f) return null
        return Math.toDegrees(kotlin.math.acos(((ax * bx + ay * by) / (na * nb)).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    /** Línea mandibular de frente u oblicua, con sus medidas; null sin óvalo o luz. */
    fun front(mesh: List<Pt>, oval: List<Pt>, lum: GrayImage, sc: Pair<Float, String>?): AnnotLine? {
        if (oval.size < 36 || mesh.size < 468) return null
        val jaw = JAW.map { oval[it] }
        val faceW = d(mesh[234], mesh[454]).coerceAtLeast(1e-4f)
        val center = mesh[1]
        val w = lum.width.toFloat()
        val mm = sc?.first
        val f = ArrayList<Pair<String, Float>>(); val g = ArrayList<MeasureGuide>()
        fun len(label: String, v: Float) = if (mm != null) "$label, mm" to v * mm else "$label, % del ancho facial" to v / faceW * 100f

        // nitidez: perfil de luz perpendicular al borde (hacia dentro = hacia la nariz)
        val step = 0.0025f * faceW; val k = 12
        val contrasts = ArrayList<Float>(); val widths = ArrayList<Float>()
        for (i in 2 until jaw.size - 2) {
            val p = jaw[i]; val a = jaw[i - 1]; val b = jaw[i + 1]
            val tx = b.x - a.x; val ty = b.y - a.y; val tn = hypot(tx, ty).coerceAtLeast(1e-6f)
            var nx = -ty / tn; var ny = tx / tn
            if ((center.x - p.x) * nx + (center.y - p.y) * ny < 0) { nx = -nx; ny = -ny }
            val v = FloatArray(2 * k + 1) { j -> val s = (j - k) * step; lum.sample((p.x + nx * s) * w, (p.y + ny * s) * w) }
            val lin = (k + k / 2..2 * k).map { v[it] }.average().toFloat()
            val lout = (0..k / 2).map { v[it] }.average().toFloat()
            val total = lin - lout
            if (lin <= 1e-3f || abs(total) < 0.015f) continue
            // magnitud: la cara puede ser más clara o más oscura que el cuello según la luz
            contrasts += abs(total) / maxOf(lin, lout) * 100f
            // transición del 10 al 90 % del cambio (de fuera hacia dentro)
            val frac = v.map { (it - lout) / total }
            val i10 = frac.indexOfFirst { it >= 0.1f }; val i90 = frac.indexOfFirst { it >= 0.9f }
            if (i10 >= 0 && i90 > i10) widths += (i90 - i10) * step
        }
        if (contrasts.isNotEmpty()) f += "Nitidez: contraste cara / cuello, %" to contrasts.average().toFloat()
        if (widths.isNotEmpty()) f += len("Nitidez: transición del borde (10–90 %)", widths.sorted()[widths.size / 2])

        // rectitud de cada lado (ángulo → mentón)
        val chin = oval[CHIN]
        for (gi in listOf(GONION_A, GONION_B)) {
            val gon = oval[gi]
            val seg = if (gi < CHIN) (gi..CHIN).map { oval[it] } else (CHIN..gi).map { oval[it] }
            val l = d(gon, chin).coerceAtLeast(1e-6f)
            val ds = seg.map { signedDist(it, gon, chin, center) }
            val maxAbs = ds.maxOf { abs(it) }; val sag = ds.max().coerceAtLeast(0f)
            val side = if (gon.x < chin.x) "izquierda" else "derecha"
            val rect = "Rectitud ($side de la imagen), %"
            f += rect to (100f * (1f - maxAbs / l)).coerceIn(0f, 100f)
            f += len("Desviación hacia abajo ($side de la imagen)", sag)
            g += MeasureGuide("", gon, chin, FeatureMetrics.rowKey(rect))
        }

        // ángulo goníaco en el lado visible (cara girada)
        val dr = d(mesh[1], mesh[234]); val dl = d(mesh[1], mesh[454])
        if (minOf(dr, dl) / maxOf(dr, dl).coerceAtLeast(1e-6f) < 0.8f) {
            val nearR = dr > dl
            val gon = if (nearR) oval[GONION_B] else oval[GONION_A]
            val ramus = if (nearR) mesh[234] else mesh[454]
            angleDeg(ramus, gon, chin)?.let { ang ->
                val side = if (gon.x < chin.x) "izquierda" else "derecha"
                val label = "Ángulo goníaco ($side de la imagen), °"
                f += label to ang
                g += MeasureGuide("${fmt(ang)}°", ramus, gon, FeatureMetrics.rowKey(label))
                g += MeasureGuide("", gon, chin, FeatureMetrics.rowKey(label))
            }
        }
        if (f.isEmpty()) return null
        return AnnotLine(AnnotLayer.JAWLINE, jaw, false,
            metrics = LineMetrics("Línea mandibular", scale = sc?.second ?: "relativa", features = f), guides = g)
    }

    /** Pliegue de papada bajo el mentón (de frente); null si no se encuentra. */
    fun papada(mesh: List<Pt>, oval: List<Pt>, lum: GrayImage, sc: Pair<Float, String>?, skin: ((Pt) -> Boolean)?): AnnotLine? {
        if (oval.size < 36 || mesh.size < 468) return null
        val faceW = d(mesh[234], mesh[454]).coerceAtLeast(1e-4f)
        val r = if (mesh.size > 473) mesh[468] else mesh[33]; val l = if (mesh.size > 473) mesh[473] else mesh[263]
        val ipd = d(r, l).coerceAtLeast(1e-5f)
        val ex = Pt((l.x - r.x) / ipd, (l.y - r.y) / ipd)
        val down = Pt(-ex.y, ex.x).let { if (it.y < 0) Pt(-it.x, -it.y) else it }
        val chin = oval[CHIN]
        val h = lum.height / lum.width.toFloat()
        val axes = listOf(0.05f, 0.075f, 0.10f, 0.125f, 0.15f, 0.175f).mapNotNull { t ->
            val c = Pt(chin.x + down.x * t * faceW, chin.y + down.y * t * faceW)
            val half = 0.17f * faceW
            val a = Pt(c.x - ex.x * half, c.y - ex.y * half); val b = Pt(c.x + ex.x * half, c.y + ex.y * half)
            // dentro de la imagen (el recorte llega poco por debajo del mentón)
            if (listOf(a, b).any { it.x < 0.01f || it.x > 0.99f || it.y < 0.01f || it.y > h - 0.01f }) null else t to listOf(a, c, b)
        }
        if (axes.isEmpty()) return null
        val found = Par.map(axes) { (t, axis) ->
            RidgeTracker.track(lum, axis, 0.014f * faceW, stations = 20, offsets = 6, minStrength = 0.02f, minContrast = 2.0f, skin = skin)?.let { t to it }
        }.filterNotNull()
        val best = found.maxByOrNull { (_, res) -> res.strength * LineMeasure.length(res.points) } ?: return null
        val pts = best.second.points
        if (LineMeasure.length(pts) < 0.12f * faceW) return null
        val m = sc?.let { LineMeasure.line("Papada (pliegue submentoniano)", pts, lum, it, halfMm = 3f) }
            ?: LineMetrics("Papada (pliegue submentoniano)")
        return AnnotLine(AnnotLayer.PAPADA, pts, false, metrics = m)
    }

    /**
     * Perfil desde la máscara del paciente ([mask], encuadre de la foto de [imgW]×[imgH]): ángulo
     * cervicomentoniano (línea mandibular de perfil) y contorno submentoniano (papada) con su convexidad.
     */
    fun profile(mask: Mask, imgW: Int, imgH: Int): List<AnnotLine> {
        val mw = mask.width; val mh = mask.height
        val ky = imgH / imgW.toFloat()
        // borde de cada fila (izquierdo y derecho del tramo del sujeto más ancho)
        val lft = FloatArray(mh) { Float.NaN }; val rgt = FloatArray(mh) { Float.NaN }
        for (y in 0 until mh) {
            var bestA = -1; var bestB = -1; var x = 0
            while (x < mw) {
                if (mask[x, y]) { val a = x; while (x < mw && mask[x, y]) x++; if (x - a > bestB - bestA) { bestA = a; bestB = x } } else x++
            }
            if (bestB - bestA > 2) { lft[y] = bestA.toFloat(); rgt[y] = bestB.toFloat() }
        }
        val rows = (0 until mh).filter { !lft[it].isNaN() }
        if (rows.size < mh / 4) return emptyList()
        val y0 = rows.first(); val y1 = rows.last()
        // la cara mira hacia el lado con la nariz (mayor saliente en la parte media)
        val midRows = rows.filter { it in y0 + (y1 - y0) * 3 / 10..y0 + (y1 - y0) * 6 / 10 }
        if (midRows.size < 5) return emptyList()
        fun med(a: List<Float>) = a.sorted()[a.size / 2]
        val protR = midRows.maxOf { rgt[it] } - med(midRows.map { rgt[it] })
        val protL = med(midRows.map { lft[it] }) - midRows.minOf { lft[it] }
        val frontRight = protR >= protL
        fun fx(y: Int) = if (frontRight) rgt[y] else -lft[y]
        fun pt(y: Int) = Pt((if (frontRight) rgt[y] else lft[y]) / mw, (y + 0.5f) / mh * ky)
        // punta nasal (máxima protrusión en la parte media) y proporciones de la cabeza: el mentón queda
        // a ≈ 0,45–1,3 veces la distancia vértice → nariz por debajo de la nariz, y el cuello más abajo
        val n = rows.size
        val iNose = (n * 3 / 10 until n * 3 / 4).maxByOrNull { fx(rows[it]) } ?: return emptyList()
        val width = rgt[rows[iNose]] - lft[rows[iNose]]
        val yNose = rows[iNose]; val lHead = (yNose - y0).coerceAtLeast(8)
        val neckStart = yNose + (lHead * 1.3f).toInt(); val neckEnd = minOf(y1, yNose + (lHead * 1.8f).toInt())
        val neckRows = rows.filter { it in neckStart..neckEnd }
        if (neckRows.size < 3) return emptyList()   // la foto no llega al cuello
        val neckFx = med(neckRows.map { fx(it) })
        val chinWin = rows.filter { it in yNose + (lHead * 0.45f).toInt() until neckStart }
        if (chinWin.size < 4) return emptyList()
        val chinRow = chinWin.maxBy { fx(it) }
        val chinFx = fx(chinRow)
        val depth = chinFx - neckFx
        if (depth < 0.04f * width) return emptyList()   // sin mentón marcado frente al cuello: no hay ángulo
        val region = rows.filter { it in chinRow..neckEnd }
        // submentón: del mentón (15 % del recorrido hacia el cuello) a donde falta un 35 %; cuello: desde
        // que llega a él (5 %) hacia abajo. El ángulo es el de las dos rectas en su cruce.
        val mRow = region.firstOrNull { fx(it) <= chinFx - 0.15f * depth } ?: return emptyList()
        val sRow = region.firstOrNull { it > mRow && fx(it) <= neckFx + 0.35f * depth } ?: return emptyList()
        val cRow = region.firstOrNull { it > sRow && fx(it) <= neckFx + 0.05f * depth } ?: return emptyList()
        val nRow = minOf(region.last(), cRow + maxOf(6, (cRow - mRow) * 2))
        if (nRow <= cRow + 2 || sRow <= mRow) return emptyList()
        val m = pt(mRow); val sp = pt(sRow); val cc = pt(cRow); val nk = pt(nRow)
        // cruce de las rectas M–S (submentón) y C–N (cuello)
        val c = run {
            val d1x = sp.x - m.x; val d1y = sp.y - m.y; val d2x = nk.x - cc.x; val d2y = nk.y - cc.y
            val den = d1x * d2y - d1y * d2x
            if (abs(den) < 1e-9f) cc else {
                val t = ((cc.x - m.x) * d2y - (cc.y - m.y) * d2x) / den
                Pt(m.x + d1x * t, m.y + d1y * t).takeIf { d(it, cc) < 3f * d(m, cc) } ?: cc
            }
        }
        val ang = angleDeg(Pt(c.x + (m.x - sp.x), c.y + (m.y - sp.y)), c, Pt(c.x + (nk.x - cc.x), c.y + (nk.y - cc.y))) ?: return emptyList()
        val labelA = "Ángulo cervicomentoniano, °"
        val jaw = AnnotLine(AnnotLayer.JAWLINE, listOf(m, c, nk), false,
            metrics = LineMetrics("Línea mandibular (perfil)", scale = "relativa", features = listOf(labelA to ang)),
            guides = listOf(MeasureGuide("${fmt(ang)}°", m, c, FeatureMetrics.rowKey(labelA)), MeasureGuide("", c, nk, FeatureMetrics.rowKey(labelA))))
        // papada de perfil: contorno submentoniano (mentón → punto cervical) y su convexidad
        val sub = (mRow..cRow).filter { !lft[it].isNaN() }.map(::pt)
        val out = arrayListOf(jaw)
        if (sub.size >= 3) {
            val l = d(m, c).coerceAtLeast(1e-6f)
            // hacia fuera = lado contrario al interior del cuello (punto detrás del cuello)
            val inside = Pt(c.x + (if (frontRight) -1f else 1f) * 0.05f, c.y)
            val conv = sub.maxOf { signedDist(it, m, c, inside) }.coerceAtLeast(0f)
            val labelC = "Convexidad submentoniana, % del submentón"
            out += AnnotLine(AnnotLayer.PAPADA, sub, false,
                metrics = LineMetrics("Papada (perfil)", scale = "relativa", features = listOf(labelC to conv / l * 100f)),
                guides = listOf(MeasureGuide("", m, c, FeatureMetrics.rowKey(labelC))))
        }
        return out
    }
}
