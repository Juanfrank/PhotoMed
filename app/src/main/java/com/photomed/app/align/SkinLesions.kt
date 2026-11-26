package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import com.photomed.app.i18n.trn
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Hallazgos de la piel en una foto clínica, **orientativos** (no diagnósticos): cada píxel de piel se compara
 * con la piel de alrededor (fondo local: media gaussiana sobre la piel natural, sin la propia lesión).
 *
 * - **Manchas** (hiperpigmentación, nevus, léntigos): más oscuras (ΔL* > 5) y con más color (ΔC* > 2) que la
 *   piel vecina. Validado píxel a píxel con 80 lesiones con máscara de dermatólogo (dermatoscopia, estilo ISIC):
 *   IoU media 0,59, mediana 0,63; 55/80 con IoU > 0,5.
 * - **Arañitas** (telangiectasias): trazos finos y alargados más rojos (Δa* > 8) que la piel vecina. El mapa
 *   rojo separa fotos vasculares/acné de pigmentadas con AUC ≈ 0,73 (DermNet); la forma alargada es heurística.
 * - **Várices** (muslos y piernas): trazos alargados y más anchos, azulados (Δb* < −4) y algo más oscuros.
 *   Heurístico: sin datos etiquetados para validarlo.
 * - **Celulitis** (glúteos, muslos): textura de hoyuelos (paso de banda de la luminosidad, escala de 1–4 % del
 *   ancho) y un grado 0–3 orientativo por la superficie con hoyuelos. Heurístico: depende mucho de la luz
 *   (la luz rasante la exagera y la frontal la oculta).
 *
 * La **fibrosis** no se detecta: es un cambio de la dermis (rigidez, retracción) que una foto no muestra de forma
 * fiable; necesita palpación, elastografía o ecografía.
 *
 * Coordenadas de salida en unidades del ancho de la imagen analizada.
 */
object SkinLesions {
    enum class Kind { SPOTS, SPIDER, VARICES, CELLULITE }

    /** Capas de los hallazgos (en los menús de la cara y del cuerpo). */
    val LAYERS = listOf(AnnotLayer.SPOTS, AnnotLayer.SPIDER_VEINS, AnnotLayer.VARICES, AnnotLayer.CELLULITE)

    /** Umbrales de las manchas (ajustados con las máscaras de lesiones; ver la cabecera). */
    const val SPOT_DL = 5f
    const val SPOT_DC = 2f
    /** Máximo de hallazgos de cada tipo que se dibujan (los mayores). */
    const val MAX_ITEMS = 25

    /** Nota para el usuario (también explica por qué no hay fibrosis). */
    val NOTE = trn("Hallazgos de la piel orientativos (manchas, arañitas, várices, celulitis), comparando cada zona con la piel " +
        "vecina; no son un diagnóstico. La fibrosis no se puede detectar en una foto (necesita palpación o ecografía).")

    /** Qué se busca en cada zona. */
    fun kindsFor(zone: Zone): Set<Kind> = when (zone.guide) {
        GuideType.LEGS -> setOf(Kind.SPOTS, Kind.SPIDER, Kind.VARICES, Kind.CELLULITE)
        GuideType.GLUTES -> setOf(Kind.SPOTS, Kind.SPIDER, Kind.CELLULITE)
        GuideType.FEET -> setOf(Kind.SPOTS, Kind.SPIDER, Kind.VARICES)
        GuideType.SCALP, GuideType.SCALP_CROWN, GuideType.SCALP_PARIETAL, GuideType.SCALP_FRONT, GuideType.HANDS -> setOf(Kind.SPOTS)
        else -> setOf(Kind.SPOTS, Kind.SPIDER)
    }

    /** Piel natural (más permisiva que la del alineador de color: incluye lesiones pardas y rojizas). */
    fun naturalSkin(l: Float, a: Float, b: Float): Boolean {
        if (l < 20f || l > 95f) return false
        val c = hypot(a, b); if (c < 4f || c > 55f) return false
        val h = Math.toDegrees(kotlin.math.atan2(b.toDouble(), a.toDouble()))
        return h > -10 && h < 85
    }

    /**
     * [px]: ARGB [w]×[h]. [skin]: piel de la segmentación en la misma rejilla (null = solo por el color).
     * [exclude]: polígonos (unidades del ancho) que no son piel lisa (ojos, cejas, labios). [mmPerUnit]: mm por
     * unidad del ancho, si hay escala (cara: iris); si no, las medidas van en proporciones de la foto.
     */
    fun detect(px: IntArray, w: Int, h: Int, skin: BooleanArray?, kinds: Set<Kind>, mmPerUnit: Float? = null,
               exclude: List<List<Pt>> = emptyList(),
               /** Discos (centro y radio, unidades del ancho) que no se analizan: ombligo, areolas, axilas. */
               excludeDiscs: List<Pair<Pt, Float>> = emptyList(),
               /** Primer plano de una lesión (la piel llena el encuadre): admite manchas grandes. */
               closeUp: Boolean = false): List<AnnotLine> {
        if (kinds.isEmpty() || w < 32 || h < 32) return emptyList()
        val n = w * h
        val lab = LabImage.fromArgb(px, w, h)
        val col = BooleanArray(n) { naturalSkin(lab.l[it], lab.a[it], lab.b[it]) }
        // región: la piel de la segmentación (cerrada) o la de color; sin el borde (sombras de la silueta) ni lo excluido
        // con los huecos rellenos: una lesión muy oscura o azulada no deja de ser piel
        var region = fillHoles(if (skin != null) close(skin, w, h, 2) else close(col, w, h, 3), w, h)
        region = erode(region, w, h, max(1, w / 80))
        for ((c0, r0) in excludeDiscs) {
            val cx = c0.x * w; val cy = c0.y * w; val rr = r0 * w
            for (y in max(0, (cy - rr).toInt())..min(h - 1, (cy + rr).toInt())) for (x in max(0, (cx - rr).toInt())..min(w - 1, (cx + rr).toInt()))
                if (hypot(x - cx, y - cy) <= rr) region[y * w + x] = false
        }
        if (exclude.isNotEmpty()) {
            val ex = BooleanArray(n)
            exclude.filter { it.size >= 3 }.forEach { poly -> fillPoly(ex, w, h, poly.map { Pt(it.x * w, it.y * w) }) }
            val exd = dilate(ex, w, h, max(2, w / 60))
            for (i in 0 until n) if (exd[i]) region[i] = false
        }
        val nRegion = region.count { it }
        if (nRegion < n / 50) return emptyList()
        val base = BooleanArray(n) { region[it] && col[it] }
        val out = ArrayList<AnnotLine>()
        val mmPerPx = mmPerUnit?.let { it / w }
        val chroma = FloatArray(n) { hypot(lab.a[it], lab.b[it]) }
        if (Kind.SPOTS in kinds) out += spots(lab, chroma, w, h, region, base, nRegion, mmPerPx, closeUp)
        if (Kind.SPIDER in kinds || Kind.VARICES in kinds) {
            val s = w / 12f
            val bl = blurNorm(lab.l, base, w, h, s); val ba = blurNorm(lab.a, base, w, h, s); val bb = blurNorm(lab.b, base, w, h, s)
            if (Kind.SPIDER in kinds) {
                val red = BooleanArray(n) { region[it] && lab.a[it] - ba[it] > 8f && bl[it] - lab.l[it] > -5f }
                out += streaks(red, w, h, nRegion, mmPerPx, AnnotLayer.SPIDER_VEINS, trn("Arañita"), minFrac = 0.0001f, minElong = 3f,
                    maxWidthFrac = 0.012f, edgeL = lab.a.map { -it }.toFloatArray(), region = region) { i -> lab.a[i] - ba[i] }
            }
            if (Kind.VARICES in kinds) {
                val blue = close(BooleanArray(n) { region[it] && lab.b[it] - bb[it] < -5f && bl[it] - lab.l[it] > 3f }, w, h, 1)
                out += streaks(blue, w, h, nRegion, mmPerPx, AnnotLayer.VARICES, trn("Várice"), minFrac = 0.0005f, minElong = 2.5f,
                    maxWidthFrac = 0.05f, edgeL = lab.b, region = region, minEdge = 3f, minLenFrac = 0.06f) { i -> bb[i] - lab.b[i] }
            }
        }
        if (Kind.CELLULITE in kinds) cellulite(lab, w, h, region, base, nRegion)?.let { out += it }
        return out
    }

    // ---------------------------------------------------------------- manchas

    private fun spots(lab: LabImage, chroma: FloatArray, w: Int, h: Int, region: BooleanArray, base: BooleanArray, nRegion: Int,
                      mmPerPx: Float?, closeUp: Boolean): List<AnnotLine> {
        val n = w * h; val s = w / 3f
        // fondo en dos pasadas: la segunda sin lo que ya es claramente más oscuro (la propia mancha)
        var bl = blurNorm(lab.l, base, w, h, s)
        val base2 = BooleanArray(n) { base[it] && bl[it] - lab.l[it] < SPOT_DL }
        bl = blurNorm(lab.l, base2, w, h, s)
        val ba = blurNorm(lab.a, base2, w, h, s); val bb = blurNorm(lab.b, base2, w, h, s); val bc = blurNorm(chroma, base2, w, h, s)
        var pig = BooleanArray(n) { region[it] && bl[it] - lab.l[it] > SPOT_DL && chroma[it] - bc[it] > SPOT_DC }
        pig = dilate(erode(pig, w, h, 1), w, h, 1)        // apertura: quita píxeles sueltos
        pig = erode(dilate(pig, w, h, 2), w, h, 2)        // cierre: une la mancha
        for (i in 0 until n) if (!region[i]) pig[i] = false
        val minA = max(8, (0.0003f * nRegion).roundToInt())
        // tamaño máximo: en una foto de la zona entera, una «mancha» enorme es sombra; con la lesión llenando el
        // encuadre (primer plano, dermatoscopia) la piel visible es poca y el límite relativo la admite
        val maxA = if (closeUp) nRegion * 0.3f else nRegion * MAX_SPOT_FRAC
        val comps = components(pig, w, h).filter { it.size >= minA && it.size < maxA &&
            edgeContrast(it, lab.l, region, w, h) >= MIN_EDGE_DL &&
            // una sombra pegada al borde de la piel (bajo la mandíbula, el pliegue de la axila) o una banda alargada no es una mancha
            borderFraction(it, region, w, h) < MAX_BORDER && elongation(it, w) < MAX_SPOT_ELONG }
        val ranked = comps.map { c ->
            var dl = 0f; var de = 0f
            for (i in c) {
                val d = bl[i] - lab.l[i]; dl += d
                de += sqrt(d * d + (lab.a[i] - ba[i]).let { it * it } + (lab.b[i] - bb[i]).let { it * it })
            }
            Triple(c, dl / c.size, de / c.size)
        }.sortedByDescending { it.first.size * it.second }.take(MAX_ITEMS)
        return ranked.mapIndexed { k, (c, dl, de) ->
            val hull = hullOf(c, w)
            val diam = diameter(hull)                     // px
            val f = ArrayList<Pair<String, Float>>()
            if (mmPerPx != null) {
                f += trn("Área, mm²") to c.size * mmPerPx * mmPerPx
                f += trn("Diámetro mayor, mm") to diam * mmPerPx
            } else {
                f += trn("Área, % de la piel visible") to 100f * c.size / nRegion
                f += trn("Diámetro mayor, % del ancho") to 100f * diam / w
            }
            f += trn("Contraste ΔL* frente a la piel vecina") to dl
            f += trn("ΔE*ab frente a la piel vecina") to de
            AnnotLine(AnnotLayer.SPOTS, hull.map { Pt(it.x / w, it.y / w) }, closed = true, filled = true,
                metrics = LineMetrics("${trn("Mancha")} ${k + 1}", scale = if (mmPerPx != null) "iris" else "foto", features = f))
        }
    }

    /** Fracción máxima del borde de un hallazgo que puede tocar el límite de la piel analizada. */
    const val MAX_BORDER = 0.2f
    /** Alargamiento máximo de una mancha (eje mayor / menor). */
    const val MAX_SPOT_ELONG = 3f

    /** Fracción de los píxeles del borde del componente [c] que lindan con lo que no es [region] (o con el marco). */
    fun borderFraction(c: IntArray, region: BooleanArray, w: Int, h: Int): Float {
        val inC = HashSet<Int>(c.size * 2); c.forEach { inC += it }
        var edge = 0; var touch = 0
        for (i in c) {
            val x = i % w; val y = i / w
            var isEdge = false; var t = false
            for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val xx = x + dx; val yy = y + dy
                if (xx !in 0 until w || yy !in 0 until h) { isEdge = true; t = true; continue }
                val j = yy * w + xx
                if (j !in inC) { isEdge = true; if (!region[j]) t = true }
            }
            if (isEdge) { edge++; if (t) touch++ }
        }
        return if (edge == 0) 0f else touch / edge.toFloat()
    }

    /** Alargamiento (raíz del cociente de los momentos principales) del componente [c]. */
    fun elongation(c: IntArray, w: Int): Float {
        if (c.size < 3) return 1f
        var mx = 0f; var my = 0f
        for (i in c) { mx += i % w; my += i / w }
        mx /= c.size; my /= c.size
        var sxx = 0f; var syy = 0f; var sxy = 0f
        for (i in c) { val dx = i % w - mx; val dy = i / w - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        val tr = sxx + syy; val det = sxx * syy - sxy * sxy; val disc = sqrt(max(0f, tr * tr / 4 - det))
        return sqrt((tr / 2 + disc) / max(1e-3f, tr / 2 - disc))
    }

    /** Mayor área de una mancha en una foto de la zona entera (fracción de la piel visible). */
    const val MAX_SPOT_FRAC = 0.02f
    /** Contraste mínimo (L*) de la mancha frente al anillo de piel inmediato: la sombra se difumina, la mancha tiene borde. */
    const val MIN_EDGE_DL = 4f

    /**
     * Cuánto más bajo es [ch] dentro del componente [c] que en el anillo de piel justo alrededor (2–4 px fuera).
     * Una mancha o un vaso tienen borde (contraste alto); un sombreado amplio sigue igual de oscuro al lado.
     */
    fun edgeContrast(c: IntArray, ch: FloatArray, region: BooleanArray, w: Int, h: Int): Float {
        var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
        for (i in c) { val x = i % w; val y = i / w; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
        val pad = 5; val bx0 = max(0, x0 - pad); val by0 = max(0, y0 - pad); val bx1 = min(w - 1, x1 + pad); val by1 = min(h - 1, y1 + pad)
        val bw = bx1 - bx0 + 1; val bh = by1 - by0 + 1
        val m = BooleanArray(bw * bh); var inS = 0.0
        for (i in c) { m[(i / w - by0) * bw + (i % w - bx0)] = true; inS += ch[i] }
        val d1 = dilate(m, bw, bh, 1); val d4 = dilate(m, bw, bh, 4)
        var rs = 0.0; var rn = 0
        for (k in 0 until bw * bh) if (d4[k] && !d1[k]) {
            val i = (k / bw + by0) * w + (k % bw + bx0)
            if (region[i]) { rs += ch[i]; rn++ }
        }
        if (rn < 4) return 0f
        return (rs / rn - inS / c.size).toFloat()
    }

    // ---------------------------------------------------------------- arañitas y várices

    /** Componentes alargados de [mask] como trazos (eje mayor con su curvatura), con longitud, ancho y contraste. */
    private fun streaks(mask: BooleanArray, w: Int, h: Int, nRegion: Int, mmPerPx: Float?, layer: AnnotLayer, name: String,
                        minFrac: Float, minElong: Float, maxWidthFrac: Float, edgeL: FloatArray? = null, region: BooleanArray? = null,
                        minEdge: Float = 2f, minLenFrac: Float = 0f, contrast: (Int) -> Float): List<AnnotLine> {
        val minA = max(5, (minFrac * nRegion).roundToInt())
        val items = components(mask, w, h).filter { it.size >= minA && (edgeL == null || edgeContrast(it, edgeL, region!!, w, h) >= minEdge) &&
            (region == null || borderFraction(it, region, w, h) < MAX_BORDER) }.mapNotNull { c ->
            val xs = FloatArray(c.size) { (c[it] % w).toFloat() }; val ys = FloatArray(c.size) { (c[it] / w).toFloat() }
            val mx = xs.average().toFloat(); val my = ys.average().toFloat()
            var sxx = 0f; var syy = 0f; var sxy = 0f
            for (i in xs.indices) { val dx = xs[i] - mx; val dy = ys[i] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
            sxx /= c.size; syy /= c.size; sxy /= c.size
            val tr = sxx + syy; val det = sxx * syy - sxy * sxy
            val disc = sqrt(max(0f, tr * tr / 4 - det))
            val l1 = tr / 2 + disc; val l2 = max(1e-3f, tr / 2 - disc)
            if (sqrt(l1 / l2) < minElong) return@mapNotNull null
            // eje mayor
            val (ux, uy) = if (abs(sxy) > 1e-6f) (l1 - syy to sxy) else if (sxx >= syy) (1f to 0f) else (0f to 1f)
            val un = hypot(ux, uy); val ex = ux / un; val ey = uy / un
            val t = FloatArray(c.size) { (xs[it] - mx) * ex + (ys[it] - my) * ey }
            val tMin = t.min(); val tMax = t.max(); val len = tMax - tMin
            if (len < max(4f, minLenFrac * w)) return@mapNotNull null
            val width = c.size / len
            if (width > maxWidthFrac * w) return@mapNotNull null
            // línea media: media perpendicular por tramos del eje
            val bins = (len / 4f).roundToInt().coerceIn(2, 8)
            val sumO = FloatArray(bins); val cnt = IntArray(bins)
            for (i in xs.indices) {
                val b = ((t[i] - tMin) / len * bins).toInt().coerceIn(0, bins - 1)
                sumO[b] += -(xs[i] - mx) * ey + (ys[i] - my) * ex; cnt[b]++
            }
            val pts = (0 until bins).filter { cnt[it] > 0 }.map { b ->
                val tt = tMin + (b + 0.5f) * len / bins; val o = sumO[b] / cnt[b]
                Pt((mx + tt * ex - o * ey) / w, (my + tt * ey + o * ex) / w)
            }
            if (pts.size < 2) return@mapNotNull null
            var ct = 0f; for (i in c) ct += contrast(i)
            Triple(pts, len to width, ct / c.size)
        }.sortedByDescending { it.second.first }.take(MAX_ITEMS)
        return items.mapIndexed { k, (pts, lw, ct) ->
            val (len, width) = lw
            val f = ArrayList<Pair<String, Float>>()
            if (mmPerPx != null) { f += trn("Longitud, mm") to len * mmPerPx; f += trn("Ancho, mm") to width * mmPerPx }
            else { f += trn("Longitud, % del ancho") to 100f * len / w; f += trn("Ancho, % del ancho") to 100f * width / w }
            f += (if (layer == AnnotLayer.VARICES) trn("Tono azulado −Δb* frente a la piel vecina") else trn("Enrojecimiento Δa* frente a la piel vecina")) to ct
            AnnotLine(layer, pts, closed = false, metrics = LineMetrics("$name ${k + 1}", scale = if (mmPerPx != null) "iris" else "foto", features = f))
        }
    }

    // ---------------------------------------------------------------- celulitis

    /** Grado orientativo 0–3 por la superficie con hoyuelos (%). */
    fun celluliteGrade(pct: Float): Int = when { pct < 3f -> 0; pct < 8f -> 1; pct < 15f -> 2; else -> 3 }

    private fun cellulite(lab: LabImage, w: Int, h: Int, region: BooleanArray, base: BooleanArray, nRegion: Int): List<AnnotLine>? {
        val n = w * h
        val inner = erode(region, w, h, max(2, w / 40))
        val nInner = inner.count { it }; if (nInner < nRegion / 4) return null
        // paso de banda: detalle fino (σ ≈ 0,7 % del ancho) menos el relieve grande (σ ≈ 3 %): hoyuelos = valles
        val fine = blurNorm(lab.l, base, w, h, max(1f, w / 150f))
        val coarse = blurNorm(lab.l, base, w, h, w / 30f)
        var dimple = BooleanArray(n) { inner[it] && coarse[it] - fine[it] > 2.5f }
        dimple = dilate(erode(dimple, w, h, 1), w, h, 1)
        val minA = max(6, (0.0002f * nInner).roundToInt())
        val comps = components(dimple, w, h).filter { it.size >= minA && it.size < nInner * 0.05f }
        val area = comps.sumOf { it.size }
        val pct = 100f * area / nInner
        val grade = celluliteGrade(pct)
        val out = ArrayList<AnnotLine>()
        comps.sortedByDescending { it.size }.take(60).forEach { c ->
            out += AnnotLine(AnnotLayer.CELLULITE, hullOf(c, w).map { Pt(it.x / w, it.y / w) }, closed = true, filled = true)
        }
        // la ficha: en la envolvente (punteada) de la piel analizada
        val hull = hullOf((0 until n).filter { inner[it] }, w).map { Pt(it.x / w, it.y / w) }
        if (hull.size < 3) return out
        out += AnnotLine(AnnotLayer.CELLULITE, hull, closed = true, estimated = true,
            metrics = LineMetrics(trn("Celulitis (textura)"), scale = "foto", type = trn("Grado orientativo por la textura; depende de la luz"),
                features = listOf(trn("Superficie con hoyuelos, %") to pct, trn("Hoyuelos, n.º") to comps.size.toFloat(),
                    trn("Grado orientativo (0–3)") to grade.toFloat())))
        return out
    }

    // ---------------------------------------------------------------- utilidades (públicas para las pruebas)

    /** Media gaussiana de [ch] solo sobre [m] (tres cajas sucesivas ≈ gauss de σ = [sigma] px). */
    fun blurNorm(ch: FloatArray, m: BooleanArray, w: Int, h: Int, sigma: Float): FloatArray {
        val num = FloatArray(w * h) { if (m[it]) ch[it] else 0f }
        val den = FloatArray(w * h) { if (m[it]) 1f else 0f }
        val r = ((sqrt(4f * sigma * sigma + 1f) - 1f) / 2f).roundToInt().coerceAtLeast(1)
        repeat(3) { boxH(num, w, h, r); boxV(num, w, h, r); boxH(den, w, h, r); boxV(den, w, h, r) }
        // sin piel cerca: la media global de la piel
        var gs = 0.0; var gn = 0
        for (i in ch.indices) if (m[i]) { gs += ch[i]; gn++ }
        val g = if (gn > 0) (gs / gn).toFloat() else 0f
        return FloatArray(w * h) { if (den[it] > 1e-3f) num[it] / den[it] else g }
    }

    private fun boxH(a: FloatArray, w: Int, h: Int, r: Int) {
        val row = FloatArray(w); val k = 2 * r + 1
        for (y in 0 until h) {
            val o = y * w; var s = 0f
            for (x in -r..r) s += a[o + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                row[x] = s / k
                s += a[o + (x + r + 1).coerceAtMost(w - 1)] - a[o + (x - r).coerceAtLeast(0)]
            }
            System.arraycopy(row, 0, a, o, w)
        }
    }

    private fun boxV(a: FloatArray, w: Int, h: Int, r: Int) {
        val col = FloatArray(h); val k = 2 * r + 1
        for (x in 0 until w) {
            var s = 0f
            for (y in -r..r) s += a[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                col[y] = s / k
                s += a[(y + r + 1).coerceAtMost(h - 1) * w + x] - a[(y - r).coerceAtLeast(0) * w + x]
            }
            for (y in 0 until h) a[y * w + x] = col[y]
        }
    }

    /** Dilatación cuadrada de radio [r] (separable). */
    fun dilate(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray = morph(m, w, h, r, true)
    fun erode(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray = morph(m, w, h, r, false)
    fun close(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray = erode(dilate(m, w, h, r), w, h, r)

    private fun morph(m: BooleanArray, w: Int, h: Int, r: Int, dil: Boolean): BooleanArray {
        if (r <= 0) return m.copyOf()
        // con sumas acumuladas: un píxel es «true» si hay alguno (dilatar) o si lo son todos (erosionar) en la ventana
        fun pass(src: BooleanArray, horizontal: Boolean): BooleanArray {
            val out = BooleanArray(w * h)
            val len = if (horizontal) w else h; val lines = if (horizontal) h else w
            val acc = IntArray(len + 1)
            for (li in 0 until lines) {
                for (k in 0 until len) acc[k + 1] = acc[k] + if (src[if (horizontal) li * w + k else k * w + li]) 1 else 0
                for (k in 0 until len) {
                    val a = max(0, k - r); val b = min(len - 1, k + r)
                    val cnt = acc[b + 1] - acc[a]
                    // fuera de la imagen cuenta como «false» al erosionar
                    val v = if (dil) cnt > 0 else cnt == 2 * r + 1
                    out[if (horizontal) li * w + k else k * w + li] = v
                }
            }
            return out
        }
        return pass(pass(m, true), false)
    }

    /** [m] con los huecos rellenos (lo que no se alcanza desde el borde por fuera de [m]). */
    fun fillHoles(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val outside = BooleanArray(m.size); val stack = IntArray(m.size); var sp = 0
        fun push(i: Int) { if (!m[i] && !outside[i]) { outside[i] = true; stack[sp++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (sp > 0) {
            val i = stack[--sp]; val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }
        return BooleanArray(m.size) { !outside[it] }
    }

    /** Componentes 8-conexos (índices de píxel). */
    fun components(m: BooleanArray, w: Int, h: Int): List<IntArray> {
        val seen = BooleanArray(m.size); val out = ArrayList<IntArray>()
        val stack = IntArray(m.size); val buf = IntArray(m.size)
        for (s in m.indices) {
            if (!m[s] || seen[s]) continue
            var sp = 0; var nb = 0
            stack[sp++] = s; seen[s] = true
            while (sp > 0) {
                val i = stack[--sp]; buf[nb++] = i
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                    val j = yy * w + xx
                    if (m[j] && !seen[j]) { seen[j] = true; stack[sp++] = j }
                }
            }
            out += buf.copyOf(nb)
        }
        return out
    }

    /** Envolvente convexa (px) de los píxeles [idx] (cadena monótona; esquinas de cada píxel). */
    fun hullOf(idx: Collection<Int>, w: Int): List<Pt> = hullOf(idx.toIntArray(), w)

    fun hullOf(idx: IntArray, w: Int): List<Pt> {
        // por fila, el píxel más a la izquierda y el más a la derecha bastan para la envolvente
        val rows = HashMap<Int, IntArray>()
        for (i in idx) { val x = i % w; val y = i / w
            val r = rows.getOrPut(y) { intArrayOf(x, x) }; if (x < r[0]) r[0] = x; if (x > r[1]) r[1] = x }
        val pts = ArrayList<Pt>(rows.size * 4)
        rows.forEach { (y, r) ->
            pts += Pt(r[0].toFloat(), y.toFloat()); pts += Pt(r[0].toFloat(), y + 1f)
            pts += Pt(r[1] + 1f, y.toFloat()); pts += Pt(r[1] + 1f, y + 1f)
        }
        val s = pts.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (s.size < 3) return s
        fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Pt>(); for (p in s) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1); lower += p }
        val upper = ArrayList<Pt>(); for (p in s.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1); upper += p }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Mayor distancia entre dos vértices. */
    fun diameter(p: List<Pt>): Float {
        var d = 0f
        for (i in p.indices) for (j in i + 1 until p.size) d = max(d, hypot(p[i].x - p[j].x, p[i].y - p[j].y))
        return d
    }

    /** Rellena el polígono [poly] (px) en [m] (regla par-impar, por filas). */
    fun fillPoly(m: BooleanArray, w: Int, h: Int, poly: List<Pt>) {
        val y0 = max(0, poly.minOf { it.y }.toInt()); val y1 = min(h - 1, poly.maxOf { it.y }.toInt() + 1)
        val xs = ArrayList<Float>()
        for (y in y0..y1) {
            val yc = y + 0.5f; xs.clear()
            for (i in poly.indices) {
                val a = poly[i]; val b = poly[(i + 1) % poly.size]
                if ((a.y <= yc) != (b.y <= yc)) xs += a.x + (yc - a.y) / (b.y - a.y) * (b.x - a.x)
            }
            xs.sort()
            var k = 0
            while (k + 1 < xs.size) {
                val xa = max(0, xs[k].roundToInt()); val xb = min(w - 1, xs[k + 1].roundToInt())
                for (x in xa..xb) m[y * w + x] = true
                k += 2
            }
        }
    }
}
