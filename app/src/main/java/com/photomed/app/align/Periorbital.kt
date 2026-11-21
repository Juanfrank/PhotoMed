package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Ojeras y bolsas palpebrales, bajo cada ojo, a partir del borde del párpado inferior (malla).
 *
 * Se recorre la zona en 15 columnas perpendiculares a la línea de los ojos, desde el borde del párpado
 * hacia la mejilla, con el perfil de L* (CIE) suavizado:
 * - **Ojera**: tramo, desde el párpado (como mucho un 12 % del ancho de la cara, ~15 mm), más oscuro que la piel de la **misma columna** más abajo
 *   (mejilla, a 23–30 % del ancho de la cara), en más de 4 L*. La referencia local compensa la
 *   iluminación lateral (antes se comparaba con un punto fijo de la mejilla, a veces en otra luz). La
 *   colorimetría clínica mide lo mismo: ΔL* y ΔE*ab de la zona periocular frente a la mejilla. El
 *   contorno es el de la zona oscura (mediana entre columnas), no un polígono fijo; las gafas, el pelo
 *   y el fondo (segmentación) la cortan. Tipo probable (Huang et al.): pigmentaria, vascular o
 *   estructural (sombra, p. ej. de una bolsa), e intensidad 0–4 por ΔE*ab.
 * - **Bolsa** (pseudohernia de la grasa orbitaria): relieve convexo bajo el párpado. En el perfil, un
 *   claro (la bolsa recibe la luz) y debajo un surco (su borde inferior) con piel más clara bajo él. Se
 *   acepta si el surco aparece en la mayoría de las columnas y forma una curva suave casi paralela al
 *   párpado (ajuste cuadrático robusto); así no se confunde con arrugas finas ni con la montura de
 *   unas gafas. Medidas: altura (párpado → surco), longitud y profundidad del surco, relieve (ΔL* del
 *   claro al surco) y un grado orientativo 0–3.
 */
object Periorbital {
    val LID_R = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133)
    val LID_L = intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362)
    private const val NC = 15

    // distancias en fracción del ancho de la cara
    private const val T0 = 0.012f; private const val T1 = 0.12f
    private const val R0 = 0.23f; private const val R1 = 0.30f
    private const val BAG0 = 0.035f; private const val BAG1 = 0.15f

    /** Umbral de oscurecimiento de la ojera (L*). */
    const val DARK_DL = 4f

    class Maps(val fine: GrayImage, val broad: GrayImage, val lab: LabImage?)

    /** Mapas de L* suavizados (fino para la ojera, ancho para el relieve de la bolsa). [facePx]: ancho de la cara en px. */
    fun maps(lum: GrayImage, lab: LabImage?, facePx: Float): Maps {
        val w = lum.width; val h = lum.height
        val l = if (lab != null && lab.width == w && lab.height == h) lab.l else FloatArray(w * h) { LineMeasure.lstar(lum.data[it]) }
        val k = (facePx / 400f).coerceAtLeast(0.4f)
        return Maps(GrayImage(w, h, PhotometricStereo.blur(l, w, h, 2.0f * k)), GrayImage(w, h, PhotometricStereo.blur(l, w, h, 3.5f * k)), lab)
    }

    private class Col(val p: Pt, val fine: FloatArray, val broad: FloatArray, val skin: BooleanArray)

    private fun median(v: List<Float>) = v.sorted().let { it[it.size / 2] }
    private fun medFilter(a: FloatArray, k: Int = 5) = FloatArray(a.size) { i ->
        val w = (maxOf(0, i - k / 2)..minOf(a.size - 1, i + k / 2)).map { a[it] }.filter { !it.isNaN() }
        if (w.isEmpty()) Float.NaN else median(w)
    }

    /**
     * Ojera y bolsa de un ojo ([right]: el derecho del paciente). [faceW]: ancho de la cara (unidades del
     * ancho de la imagen); [sc]: escala en mm por unidad.
     */
    fun analyze(mesh: List<Pt>, right: Boolean, m: Maps, labels: FaceLabels?, faceW: Float, sc: Pair<Float, String>?): List<AnnotLine> {
        val img = m.fine; val wf = img.width.toFloat()
        val fpx = faceW * wf
        val lid = (if (right) LID_R else LID_L).map { Pt(mesh[it].x * wf, mesh[it].y * wf) }
        var ex = lid.last().x - lid.first().x; var ey = lid.last().y - lid.first().y
        val en = hypot(ex, ey).coerceAtLeast(1e-3f); ex /= en; ey /= en
        var nx = -ey; var ny = ex
        if (ny < 0) { nx = -nx; ny = -ny }
        val t0 = (T0 * fpx).roundToInt(); val t1 = (R1 * fpx).roundToInt()
        val n = t1 - t0
        val lat = (0.0075f * fpx).coerceAtLeast(1.5f)
        val lk = labels?.let { it.width / wf }
        val cols = (0 until NC).map { c ->
            val s = (0.06f + 0.88f * c / (NC - 1)) * (lid.size - 1)
            val i = s.toInt().coerceAtMost(lid.size - 2); val f = s - i
            val p = Pt(lid[i].x + (lid[i + 1].x - lid[i].x) * f, lid[i].y + (lid[i + 1].y - lid[i].y) * f)
            val fine = FloatArray(n); val broad = FloatArray(n); val skin = BooleanArray(n)
            for (j in 0 until n) {
                val t = (t0 + j).toFloat()
                var a = 0f; var b = 0f; var cnt = 0
                for (o in floatArrayOf(-lat, 0f, lat)) {
                    val x = p.x + nx * t + ex * o; val y = p.y + ny * t + ey * o
                    val va = img.sample(x, y); val vb = m.broad.sample(x, y)
                    if (!va.isNaN() && !vb.isNaN()) { a += va; b += vb; cnt++ }
                }
                fine[j] = if (cnt > 0) a / cnt else Float.NaN; broad[j] = if (cnt > 0) b / cnt else Float.NaN
                val x = p.x + nx * t; val y = p.y + ny * t
                skin[j] = cnt > 0 && (lk == null || labels!![(x * lk).toInt(), (y * lk).toInt()] == FaceLabels.FACE_SKIN)
            }
            Col(p, fine, broad, skin)
        }
        val lidPts = cols.map { it.p }; val down = Pt(nx, ny)
        // referencia: mejilla de la misma columna (mediana entre columnas vecinas)
        val r0 = (R0 * fpx).roundToInt() - t0
        val refs = medFilter(FloatArray(NC) { c ->
            val v = (r0 until n).filter { cols[c].skin[it] && !cols[c].fine[it].isNaN() }.map { cols[c].fine[it] }
            if (v.size > 5) median(v) else Float.NaN
        })
        val out = ArrayList<AnnotLine>()
        val side = if (right) "derecha" else "izquierda"
        fun toImg(p: Pt) = Pt(p.x / wf, p.y / wf)
        fun at(c: Int, t: Float) = Pt(cols[c].p.x + nx * t, cols[c].p.y + ny * t)

        // --- bolsa ---
        val valley = FloatArray(NC) { Float.NaN }; val relief = FloatArray(NC) { Float.NaN }
        val b0 = (BAG0 * fpx).roundToInt() - t0; val b1 = (BAG1 * fpx).roundToInt() - t0
        val half = (0.0125f * fpx).roundToInt().coerceAtLeast(2); val below = (0.06f * fpx).roundToInt()
        for (c in 0 until NC) {
            val pr = cols[c].broad
            var best = 0f
            for (j in b0 until minOf(b1, n - 1)) {
                if (!cols[c].skin[j]) break
                val v = pr[j]; if (v.isNaN()) continue
                if ((maxOf(0, j - half)..minOf(n - 1, j + half)).any { pr[it] < v }) continue
                var ja = 0; for (q in 0 until j) if (pr[q] > pr[ja]) ja = q
                val up = pr[ja] - v
                val dn = ((j until minOf(n, j + below)).maxOfOrNull { pr[it] } ?: v) - v
                if (ja >= 3 && up >= 3f && dn >= 2.5f) {
                    val s = minOf(up, dn)
                    if (s > best) { best = s; valley[c] = (t0 + j).toFloat(); relief[c] = up }
                }
            }
        }
        // curva suave casi paralela al párpado: ajuste cuadrático robusto de la profundidad del surco
        val bag = run {
            val idx = (0 until NC).filter { !valley[it].isNaN() }
            if (idx.size < NC * 0.6f) return@run null
            val mid = (3 until NC - 3).count { !valley[it].isNaN() }
            if (mid < (NC - 6) * 0.7f) return@run null
            val fit = quadFit(idx.map { it.toFloat() }, idx.map { valley[it] }) ?: return@run null
            val inl = idx.filter { abs(valley[it] - fit(it.toFloat())) < 0.012f * fpx }
            if (inl.size < NC * 0.55f) return@run null
            val fit2 = quadFit(inl.map { it.toFloat() }, inl.map { valley[it] }) ?: return@run null
            val span = inl.first()..inl.last()
            val curve = span.map { c -> at(c, fit2(c.toFloat()).coerceIn(BAG0 * fpx, BAG1 * fpx)) }
            // forma: sin saltos bruscos entre columnas
            if (curve.zipWithNext().any { (a, b) -> hypot(b.x - a.x, b.y - a.y) > 0.06f * fpx }) return@run null
            Triple(span, curve, inl) to fit2
        }
        if (bag != null) {
            val (span, curve, inl) = bag.first; val fit2 = bag.second
            // media luna: del párpado al surco, con los extremos que se cierran sobre el párpado
            val (poly, _) = crescent(lidPts, down, span.first - 0.5f, span.last + 0.5f, t0.toFloat()) { cf -> fit2(cf).coerceIn(BAG0 * fpx, BAG1 * fpx) }
            val name = "Bolsa palpebral $side"
            var guides = emptyList<MeasureGuide>()
            val metrics = sc?.let { s ->
                val mm = s.first / wf
                val height = median(span.map { c -> hypot(curve[c - span.first].x - cols[c].p.x, curve[c - span.first].y - cols[c].p.y) }) * mm
                val lenMm = LineMeasure.length(curve) * mm
                val cm = (span.first + span.last) / 2
                guides = listOf(
                    MeasureGuide(FeatureMetrics.mmText("altura", height), toImg(cols[cm].p), toImg(at(cm, fit2(cm.toFloat()))), "Altura (párpado → surco inferior)"),
                    MeasureGuide(FeatureMetrics.mmText("surco", lenMm), toImg(curve.first()), toImg(curve.last()), "Longitud del surco inferior"))
                val rel = median(inl.map { relief[it] })
                val area = LineMeasure.area(poly.map(::toImg)) * s.first * s.first
                val grade = when { height < 4.5f || rel < 4f -> 1; height >= 8f && rel >= 7f -> 3; else -> 2 }
                LineMetrics(name, scale = s.second, features = listOf(
                    "Altura (párpado → surco inferior), mm" to height,
                    "Longitud del surco inferior, mm" to lenMm,
                    "Relieve (claro sobre la bolsa → surco), ΔL*" to rel,
                    "Área, mm²" to area,
                    "Grado orientativo (0–3)" to grade.toFloat(),
                ))
            }
            out += AnnotLine(AnnotLayer.EYE_BAGS, poly.map(::toImg), true, filled = true, metrics = metrics, guides = guides)
        }

        // --- ojera ---
        val tEnd = (T1 * fpx).roundToInt() - t0
        val ends = FloatArray(NC) { Float.NaN }; val dls = ArrayList<Float>()
        for (c in 0 until NC) {
            val ref = refs[c]; if (ref.isNaN()) continue
            var end = -1; var gap = 0; var sum = 0f; var cnt = 0
            for (j in 0 until tEnd) {
                if (!cols[c].skin[j]) break
                val d = ref - cols[c].fine[j]
                if (d > DARK_DL) { end = j; gap = 0; sum += d; cnt++ } else { gap++; if (gap > 2) break }
            }
            if (end >= 0) { ends[c] = (t0 + end).toFloat(); dls += sum / cnt }
        }
        val e = medFilter(ends)
        val med = e.filter { !it.isNaN() }.let { if (it.isEmpty()) Float.NaN else median(it) }
        val deep = e.count { !it.isNaN() && it > t0 + 0.03f * fpx }
        // borde inferior suave: ajuste cuadrático robusto de la profundidad de la zona oscura
        val dfit = run {
            val idx = (0 until NC).filter { !e[it].isNaN() }
            if (idx.size < 4) return@run null
            val f1 = quadFit(idx.map { it.toFloat() }, idx.map { e[it] }) ?: return@run null
            val inl = idx.filter { abs(e[it] - f1(it.toFloat())) < 0.025f * fpx }.takeIf { it.size >= 4 } ?: idx
            quadFit(inl.map { it.toFloat() }, inl.map { e[it] })?.let { it to (inl.first()..inl.last()) }
        }
        if (deep >= NC / 2 && dls.isNotEmpty() && dls.average() >= 4.0 && !med.isNaN() && dfit != null) {
            val (fitD, spanD) = dfit
            // media luna bajo el párpado (forma característica de la ojera): profundidad ajustada que se
            // cierra hacia los cantos, sin picos de columnas sueltas
            val (poly, deepest) = crescent(lidPts, down, spanD.first - 0.5f, spanD.last + 0.5f, t0.toFloat()) { cf ->
                fitD(cf).coerceIn(t0 + 0.02f * fpx, T1 * fpx) }
            val name = "Ojera $side"
            val polyImg = poly.map(::toImg)
            val metrics = sc?.let { s ->
                val rl = m.lab?.let { LineMeasure.meanLab(it, polyImg) }
                // mejilla: franja de referencia bajo la ojera
                val refPoly = ((0 until NC).map { at(it, R0 * fpx) } + (0 until NC).reversed().map { at(it, R1 * fpx) }).map(::toImg)
                val cl = m.lab?.let { LineMeasure.meanLab(it, refPoly) }
                val base = if (rl != null && cl != null) LineMeasure.darkCircle(name, rl, cl, LineMeasure.area(polyImg), s)
                    else LineMetrics(name, areaMm2 = LineMeasure.area(polyImg) * s.first * s.first, deltaL = dls.average().toFloat(), scale = s.second)
                val de = base.deltaE ?: base.deltaL ?: 0f
                val grade = when { de < 3f -> 0; de < 6f -> 1; de < 9f -> 2; de < 12f -> 3; else -> 4 }
                // con bolsa y la parte más oscura en su surco: estructural (sombra de la bolsa)
                val shadow = bag != null && base.type?.startsWith("estructural") == true
                base.copy(grade = grade, type = (if (shadow) "estructural (sombra de la bolsa)" else base.type ?: "—") + " · intensidad $grade/4")
            }
            val guides = sc?.let { s ->
                val mm = s.first / wf
                val top = poly.first(); val topEnd = poly[poly.size / 2 - 1]
                listOf(MeasureGuide(FeatureMetrics.mmText("alto", hypot(deepest.second.x - deepest.first.x, deepest.second.y - deepest.first.y) * mm),
                        toImg(deepest.first), toImg(deepest.second)),
                    MeasureGuide(FeatureMetrics.mmText("ancho", hypot(topEnd.x - top.x, topEnd.y - top.y) * mm), toImg(top), toImg(topEnd)))
            }.orEmpty()
            out += AnnotLine(AnnotLayer.DARK_CIRCLES, polyImg, true, filled = true, metrics = metrics, guides = guides)
        }
        return out
    }

    /**
     * Media luna bajo el párpado entre las columnas fraccionarias [c0] y [c1]: arriba, el borde del
     * párpado (a [top] px); abajo, la profundidad [depth] (px desde el párpado) atenuada hacia los
     * extremos (seno), de modo que la zona se cierra en los cantos. Devuelve el polígono (21 + 21
     * puntos, en px) y el segmento de mayor altura (arriba, abajo).
     */
    private fun crescent(cols: List<Pt>, n: Pt, c0: Float, c1: Float, top: Float, depth0: (Float) -> Float): Pair<List<Pt>, Pair<Pt, Pt>> {
        // sin extremos disparados por el ajuste: como mucho 1,35 × la profundidad mediana
        val med = median((0..20).map { depth0(c0 + (c1 - c0) * it / 20f) - top })
        val depth = { cf: Float -> minOf(depth0(cf), top + 1.35f * med) }
        fun lidAt(cf: Float): Pt {
            val x = cf.coerceIn(0f, cols.size - 1f); val i = x.toInt().coerceAtMost(cols.size - 2); val f = x - i
            return Pt(cols[i].x + (cols[i + 1].x - cols[i].x) * f, cols[i].y + (cols[i + 1].y - cols[i].y) * f)
        }
        val k = 20
        val tops = ArrayList<Pt>(); val bots = ArrayList<Pt>(); var best = 0f; var seg = Pt(0f, 0f) to Pt(0f, 0f)
        for (i in 0..k) {
            val cf = c0 + (c1 - c0) * i / k
            val taper = kotlin.math.sin(Math.PI * i / k).toFloat()
            val p = lidAt(cf)
            val d = top + (depth(cf) - top).coerceAtLeast(0f) * taper
            val a = Pt(p.x + n.x * top, p.y + n.y * top); val b = Pt(p.x + n.x * d, p.y + n.y * d)
            tops += a; bots += b
            if (d - top > best) { best = d - top; seg = a to b }
        }
        return (tops + bots.reversed()) to seg
    }

    /** Ajuste por mínimos cuadrados y = a + b x + c x²; null si es degenerado. */
    private fun quadFit(xs: List<Float>, ys: List<Float>): ((Float) -> Float)? {
        if (xs.size < 3) return null
        val s = DoubleArray(5); val t = DoubleArray(3)
        for (i in xs.indices) { val x = xs[i].toDouble(); val y = ys[i].toDouble(); var p = 1.0
            for (k in 0..4) { s[k] += p; if (k <= 2) t[k] += p * y; p *= x } }
        val m = arrayOf(doubleArrayOf(s[0], s[1], s[2], t[0]), doubleArrayOf(s[1], s[2], s[3], t[1]), doubleArrayOf(s[2], s[3], s[4], t[2]))
        for (c in 0..2) {
            val piv = (c..2).maxBy { abs(m[it][c]) }; val tmp = m[c]; m[c] = m[piv]; m[piv] = tmp
            if (abs(m[c][c]) < 1e-9) return null
            for (r in 0..2) if (r != c) { val f = m[r][c] / m[c][c]; for (k in c..3) m[r][k] -= f * m[c][k] }
        }
        val a = m[0][3] / m[0][0]; val b = m[1][3] / m[1][1]; val cc = m[2][3] / m[2][2]
        return { x -> (a + b * x + cc * x * x).toFloat() }
    }
}
