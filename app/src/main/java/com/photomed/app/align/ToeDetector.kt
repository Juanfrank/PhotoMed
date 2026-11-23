package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Dedos del pie en el extremo distal (abajo, en la orientación de la guía), probado con fotos reales
 * de pies (docs/CAPTURA_AUTOMATICA.md):
 *
 * 1. **Contorno en nube**: el borde de la punta del pie es una sucesión de curvas, una por dedo. Se
 *    traza el contorno, se envuelve por fuera (envolvente convexa) y cada hueco entre la envolvente y el
 *    borde es un **valle entre dos dedos** (su punto más hondo).
 * 2. **Surco**: de cada valle nace la línea de separación entre los dedos, hacia dentro del pie, con la
 *    inclinación (± 25°) en la que la piel es más oscura que a sus lados (el borde de cada dedo se ve como
 *    una arruga) o hay hueco entre los dedos.
 * 3. **Completar desde el dedo gordo**: si el contorno ya muestra varios dedos, los tramos demasiado
 *    anchos para un solo dedo (el más ancho es el gordo) se revisan buscando un surco inclinado que no
 *    abra valle en el contorno (dedos muy juntos). Con un solo dedo recortado no se reparte nada.
 *
 * Coordenadas de entrada y salida en píxeles de la máscara.
 */
object ToeDetector {
    class Toes(
        /** Dedos distinguidos (curvas del borde distal). */
        val count: Int,
        /** Punta de cada dedo (el punto más distal de cada curva), en píxeles. */
        val tips: List<Pair<Float, Float>>,
        /** Línea de separación entre dedos (del valle hacia dentro), en píxeles. */
        val lines: List<Pair<Pair<Float, Float>, Pair<Float, Float>>>,
        /** Contorno distal (para dibujar la nube), en píxeles. */
        val edge: List<Pair<Float, Float>>,
        /**
         * Profundidad de los surcos entre dedos, en anchos del pie / de la mano (mediana): los dedos de la mano
         * son largos (surcos hondos, ≥ 0,3), los del pie cortos (≈ 0,1).
         */
        val depth: Float = 0f,
    )

    const val SMOOTH = 0.015f
    const val BAND = 0.40f
    const val DEPTH = 0.006f
    const val MIN_SEP = 0.06f
    const val LINE_LEN = 0.30f
    const val MAX_ANG = 25
    const val FILL_Z = 2.0f

    /**
     * Dedos de la mancha [inside] (w×h) con la luminosidad [luma] (w×h, opcional: sin ella, solo el
     * contorno y los huecos entre dedos). null si no hay borde distal que analizar.
     */
    fun detect(w: Int, h: Int, inside0: (Int, Int) -> Boolean, rgb: IntArray?): Toes? {
        val luma = rgb?.let { px -> FloatArray(w * h) { i -> val c = px[i]; 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF) } }
        // piel del pie refinada con su propio color (la máscara de la segmentación es de baja resolución)
        // (unida al interior de la máscara original: los surcos marcan el borde sin separar los dedos del pie)
        val refined = rgb?.let { refine(w, h, inside0, it, luma!!) }?.let { r ->
            var core = BooleanArray(w * h) { inside0(it % w, it / w) }
            repeat((w * 0.012f).toInt().coerceAtLeast(2)) { core = erode(w, h, core) }
            BooleanArray(w * h) { r[it] || core[it] }
        }
        val inside: (Int, Int) -> Boolean = if (refined != null) { x, y -> refined[y * w + x] } else inside0
        // extremo distal y ancho del pie (percentil 90 del ancho por fila en el antepié)
        var tip = -1; var x0 = w; var x1 = -1
        for (y in 0 until h) for (x in 0 until w) if (inside(x, y)) { if (y > tip) tip = y; if (x < x0) x0 = x; if (x > x1) x1 = x }
        if (tip < 0) return null
        val rows = (max(0, tip - (0.6f * (x1 - x0 + 1)).toInt())..tip).map { y -> (0 until w).count { inside(it, y) } }.filter { it > 0 }.sorted()
        if (rows.isEmpty()) return null
        val fw = max(10f, rows[(rows.size * 0.9f).toInt().coerceAtMost(rows.size - 1)].toFloat())
        // componente de la punta (una tira de sandalia puede separar el resto del pie)
        var sx = -1
        for (x in 0 until w) if (inside(x, tip)) { sx = x; break }
        if (sx < 0) return null
        val comp = BooleanArray(w * h); val stack = IntArray(w * h); var sp = 0
        comp[tip * w + sx] = true; stack[sp++] = tip * w + sx
        while (sp > 0) {
            val q = stack[--sp]; val x = q % w; val y = q / w
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && !comp[yy * w + xx] && inside(xx, yy)) { comp[yy * w + xx] = true; stack[sp++] = yy * w + xx }
            }
        }
        val raw = trace(w, h, comp) ?: return null
        // contorno suavizado (circular)
        val k = max(2, (SMOOTH * fw).toInt()); val n = raw.size
        val cx = FloatArray(n); val cy = FloatArray(n)
        for (i in 0 until n) {
            var sxf = 0f; var syf = 0f
            for (j in -k / 2 until -k / 2 + k) { val p = raw[((i + j) % n + n) % n]; sxf += p.first; syf += p.second }
            cx[i] = sxf / k; cy[i] = syf / k
        }
        // tramo distal contiguo más largo
        // franja distal: se amplía hasta que el borde sea un tramo continuo (con dedos largos, una franja
        // corta los cortaría por la mitad y quedarían las puntas sueltas)
        var seg: List<Int> = emptyList()
        for (band in floatArrayOf(BAND, 0.6f, 0.8f, 1.0f, 1.4f, 1.8f)) {
            val distal = BooleanArray(n) { cy[it] > tip - band * fw }
            seg = longestRun(distal) ?: return null
            if (seg.size >= 0.85f * distal.count { it }) break
        }
        if (seg.size < 10) return null
        val px = FloatArray(seg.size) { cx[seg[it]] }; val py = FloatArray(seg.size) { cy[seg[it]] }
        // valles: punto más hondo bajo cada lado de la envolvente convexa (lados distales, no laterales)
        // valles: punto más hondo bajo cada lado de la envolvente convexa (lados distales, no laterales);
        // recursivo: con dedos de distinta longitud un lado de la envolvente cubre varios valles, así que
        // tras cada valle se vuelve a envolver cada mitad
        val valleys = ArrayList<Pair<Int, Float>>()
        fun split(lo: Int, hi: Int, depth: Int) {
            if (hi - lo < 4 || depth > 6) return
            val sub = (lo..hi).toList()
            val hv = hull(FloatArray(sub.size) { px[sub[it]] }, FloatArray(sub.size) { py[sub[it]] }).map { sub[it] }.sorted()
            for (t in 0 until hv.size - 1) {
                val a = hv[t]; val b = hv[t + 1]; if (b - a < 3) continue
                val ex = px[b] - px[a]; val ey = py[b] - py[a]; val l = hypot(ex, ey) + 1e-6f
                if (abs(ex) <= abs(ey) * 0.6f) continue
                var best = -1; var bd = 0f
                for (i in a + 1 until b) { val d = abs((px[i] - px[a]) * -ey / l + (py[i] - py[a]) * ex / l); if (d > bd) { bd = d; best = i } }
                if (best < 0 || bd <= DEPTH * fw) continue
                valleys += best to bd
                split(a, best, depth + 1); split(best, b, depth + 1)
            }
        }
        split(0, seg.size - 1, 0)
        valleys.distinctBy { it.first }.let { valleys.clear(); valleys.addAll(it) }
        // valles demasiado juntos: el más hondo; como mucho 4 (cinco dedos)
        valleys.sortBy { px[it.first] }
        val keep = ArrayList<Pair<Int, Float>>()
        for (v in valleys) {
            val last = keep.lastOrNull()
            if (last != null && abs(px[v.first] - px[last.first]) < MIN_SEP * fw) { if (v.second > last.second) keep[keep.size - 1] = v; continue }
            keep += v
        }
        val vs = (if (keep.size > 4) keep.sortedByDescending { it.second }.take(4) else keep).sortedBy { px[it.first] }

        fun dark(x: Float, y: Float, nx: Float, ny: Float, d: Float): Float {
            val xi = x.toInt(); val yi = y.toInt()
            if (xi !in 0 until w || yi !in 0 until h) return 0f
            if (!comp[yi * w + xi]) return 1f                       // hueco entre dedos
            val l = luma ?: return 0f
            fun at(ax: Float, ay: Float): Float { val a = ax.toInt().coerceIn(0, w - 1); val b = ay.toInt().coerceIn(0, h - 1); return l[b * w + a] }
            val c0 = at(x, y); val side = (at(x + nx * d, y + ny * d) + at(x - nx * d, y - ny * d)) / 2
            return ((side - c0) / (side + 1f) * 10f).coerceIn(0f, 1f)   // surco: más oscuro que a los lados
        }
        val len = LINE_LEN * fw; val d = max(1.5f, 0.03f * fw)
        /** Mejor línea de surco que pasa por (x, y) hacia dentro (arriba): puntuación media, ángulo y extremo. */
        fun bestLine(x: Float, y: Float, fromTip: Boolean): Triple<Float, Int, Pair<Float, Float>> {
            var best = Triple(-1f, 0, x to y)
            for (ang in -MAX_ANG..MAX_ANG step 3) {
                val t = Math.toRadians(ang.toDouble()); val ux = sin(t).toFloat(); val uy = -cos(t).toFloat()
                var s = 0f; var m = 0
                for (i in 1..20) {
                    val f = i / 20f
                    // desde el valle hacia dentro; o centrada en (x, y) para los surcos sin valle
                    val o = if (fromTip) f * len else (f - 0.5f) * len
                    s += dark(x + ux * o, y + uy * o, -uy, ux, d); m++
                }
                val sc = s / m
                if (sc > best.first) best = Triple(sc, ang, (x + ux * len) to (y + uy * len))
            }
            return best
        }
        val lines = ArrayList<Pair<Pair<Float, Float>, Pair<Float, Float>>>()
        val cuts = ArrayList<Float>()     // x de cada separación en el borde distal
        for ((i, _) in vs) {
            val (_, _, end) = bestLine(px[i], py[i], true)
            lines += (px[i] to py[i]) to end; cuts += px[i]
        }
        // completar desde el dedo gordo (el tramo más ancho): tramos anchos para un dedo con un surco sin valle
        val left = px.min(); val right = px.max()
        if (luma != null && cuts.size >= 2 && cuts.size < 4) {
            val bounds = (listOf(left) + cuts + listOf(right))
            val widths = bounds.zipWithNext { a, b -> b - a }
            val hallux = widths.indices.maxBy { widths[it] }
            // línea base: contraste de surco en posiciones repartidas del antepié
            val yMid = tip - 0.12f * fw
            val base = (1..15).map { j -> bestLine(left + (right - left) * j / 16f, yMid, false).first }.sorted()
            val med = base[base.size / 2]; val mad = base.map { abs(it - med) }.sorted()[base.size / 2] + 1e-3f
            val typical = widths.filterIndexed { i, _ -> i != hallux }.sorted().let { it[it.size / 2] }
            for ((i, wd) in widths.withIndex()) {
                if (cuts.size >= 4) break
                val limit = if (i == hallux) 2.2f * typical else 1.7f * typical
                if (wd < limit) continue
                val a = bounds[i]; val b = bounds[i + 1]
                var best: Triple<Float, Int, Pair<Float, Float>>? = null; var bx = 0f
                var x = a + 0.3f * wd
                while (x <= b - 0.3f * wd) { val r = bestLine(x, yMid, false); if (best == null || r.first > best.first) { best = r; bx = x }; x += 1f }
                if (best != null && (best.first - med) / mad >= FILL_Z) {
                    val t = Math.toRadians(best.second.toDouble()); val ux = sin(t).toFloat(); val uy = -cos(t).toFloat()
                    lines += ((bx - ux * len / 2) to (yMid - uy * len / 2)) to ((bx + ux * len / 2) to (yMid + uy * len / 2))
                    cuts += bx
                }
            }
        }
        cuts.sort()
        // punta de cada dedo: el punto más distal de cada tramo del borde entre separaciones
        val bounds = listOf(Float.NEGATIVE_INFINITY) + cuts + listOf(Float.POSITIVE_INFINITY)
        val tips = bounds.zipWithNext().mapNotNull { (a, b) ->
            seg.indices.filter { px[it] > a && px[it] < b }.maxByOrNull { py[it] }?.let { px[it] to py[it] }
        }
        // profundidad de cada valle: de la punta más corta de sus dos vecinas al fondo del valle
        val depths = vs.mapNotNull { (i, _) ->
            val l = tips.lastOrNull { it.first < px[i] }; val r = tips.firstOrNull { it.first > px[i] }
            if (l == null || r == null) null else (minOf(l.second, r.second) - py[i]) / fw
        }.sorted()
        return Toes(cuts.size + 1, tips, lines, seg.indices.map { px[it] to py[it] }, depths.getOrNull(depths.size / 2) ?: 0f)
    }

    /**
     * Piel del pie con su propio color: modelo de cromaticidad (r, g normalizados) y luminosidad de su
     * núcleo (la máscara erosionada); se aceptan los píxeles cercanos a la máscara con ese color y no
     * más oscuros que la piel (así los surcos y huecos entre dedos, en sombra, quedan fuera).
     */
    private fun refine(w: Int, h: Int, inside: (Int, Int) -> Boolean, rgb: IntArray, luma: FloatArray): BooleanArray? {
        val m = BooleanArray(w * h) { inside(it % w, it / w) }
        if (m.count { it } < 50) return null
        val e = (w * 0.0125f).toInt().coerceAtLeast(2)
        var core = m; repeat(e) { core = erode(w, h, core) }
        if (core.count { it } < 50) core = m
        var n = 0; var mr = 0.0; var mg = 0.0
        val rr = FloatArray(w * h); val gg = FloatArray(w * h)
        for (i in 0 until w * h) {
            val c = rgb[i]; val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val s = (r + g + b + 1e-3f); rr[i] = r / s; gg[i] = g / s
            if (core[i]) { n++; mr += rr[i]; mg += gg[i] }
        }
        mr /= n; mg /= n
        var srr = 0.0; var sgg = 0.0; var srg = 0.0; var ml = 0.0; var sl = 0.0
        for (i in 0 until w * h) if (core[i]) { val a = rr[i] - mr; val b = gg[i] - mg; srr += a * a; sgg += b * b; srg += a * b; ml += luma[i] }
        srr = srr / n + 1e-5; sgg = sgg / n + 1e-5; srg /= n; ml /= n
        for (i in 0 until w * h) if (core[i]) { val d = luma[i] - ml; sl += d * d }
        sl = Math.sqrt(sl / n) + 1
        val det = srr * sgg - srg * srg; if (det <= 0) return null
        val i11 = sgg / det; val i22 = srr / det; val i12 = -srg / det
        var near = m; repeat((w * 0.01f).toInt().coerceAtLeast(2)) { near = dilate(w, h, near) }
        val col = BooleanArray(w * h) { i ->
            if (!near[i]) false else {
                val a = rr[i] - mr; val b = gg[i] - mg
                val md = a * a * i11 + 2 * a * b * i12 + b * b * i22
                md < 12 && luma[i] > ml - 2.2 * sl
            }
        }
        // apertura 1 y solo las partes que tocan la máscara
        val op = dilate(w, h, erode(w, h, col))
        val out = BooleanArray(w * h); val stack = IntArray(w * h)
        for (s0 in 0 until w * h) {
            if (!op[s0] || !m[s0] || out[s0]) continue
            var sp = 0; stack[sp++] = s0; out[s0] = true
            while (sp > 0) {
                val q = stack[--sp]; val x = q % w; val y = q / w
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx in 0 until w && yy in 0 until h) { val j = yy * w + xx; if (op[j] && !out[j]) { out[j] = true; stack[sp++] = j } }
                }
            }
        }
        // la mayor parte (el pie)
        return out.takeIf { o -> o.count { it } >= 50 }
    }

    private fun erode(w: Int, h: Int, m: BooleanArray) = BooleanArray(w * h) { i ->
        val x = i % w; val y = i / w
        m[i] && x > 0 && x < w - 1 && y > 0 && y < h - 1 && m[i - 1] && m[i + 1] && m[i - w] && m[i + w]
    }
    private fun dilate(w: Int, h: Int, m: BooleanArray) = BooleanArray(w * h) { i ->
        val x = i % w; val y = i / w
        m[i] || (x > 0 && m[i - 1]) || (x < w - 1 && m[i + 1]) || (y > 0 && m[i - w]) || (y < h - 1 && m[i + w])
    }

    /** Contorno exterior (recorrido de Moore) de la máscara [m]. */
    private fun trace(w: Int, h: Int, m: BooleanArray): List<Pair<Int, Int>>? {
        val start = m.indexOfFirst { it }; if (start < 0) return null
        val sx = start % w; val sy = start / w
        val dxs = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1); val dys = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)
        fun inC(x: Int, y: Int) = x in 0 until w && y in 0 until h && m[y * w + x]
        val out = ArrayList<Pair<Int, Int>>()
        var cx = sx; var cy = sy; var back = 0; var steps = 0
        do {
            out += cx to cy
            var moved = false
            for (i in 1..8) {
                val dd = (back + i) % 8; val nx = cx + dxs[dd]; val ny = cy + dys[dd]
                if (inC(nx, ny)) {
                    val pd = (back + i - 1) % 8; val bx = cx + dxs[pd] - nx; val by = cy + dys[pd] - ny
                    back = (0 until 8).first { dxs[it] == bx && dys[it] == by }
                    cx = nx; cy = ny; moved = true; break
                }
            }
            if (!moved) break
            steps++
        } while ((cx != sx || cy != sy) && steps < 16 * (w + h))
        return out.takeIf { it.size >= 8 }
    }

    /** Índices del tramo verdadero contiguo más largo de una secuencia circular. */
    private fun longestRun(b: BooleanArray): List<Int>? {
        val n = b.size; if (n == 0) return null
        if (b.all { it }) return (0 until n).toList()
        val first = b.indexOfFirst { !it }
        var best: List<Int>? = null; var cur = ArrayList<Int>()
        for (j in 1..n) {
            val i = (first + j) % n
            if (b[i]) cur += i else { if (best == null || cur.size > best.size) best = cur; cur = ArrayList() }
        }
        if (best == null || cur.size > best.size) best = cur
        return best?.takeIf { it.isNotEmpty() }
    }

    /** Vértices (índices) de la envolvente convexa (cadena monótona de Andrew). */
    private fun hull(x: FloatArray, y: FloatArray): List<Int> {
        val idx = x.indices.sortedWith(compareBy({ x[it] }, { y[it] }))
        if (idx.size < 3) return idx
        fun cross(o: Int, a: Int, b: Int) = (x[a] - x[o]) * (y[b] - y[o]) - (y[a] - y[o]) * (x[b] - x[o])
        val lower = ArrayList<Int>(); for (p in idx) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1); lower += p }
        val upper = ArrayList<Int>(); for (p in idx.reversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1); upper += p }
        return (lower.dropLast(1) + upper.dropLast(1)).distinct()
    }

    @Suppress("unused") private fun clamp(v: Int, lo: Int, hi: Int) = min(max(v, lo), hi)
}
