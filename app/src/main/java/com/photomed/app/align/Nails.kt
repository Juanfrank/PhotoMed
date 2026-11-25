package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.min
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Uñas de los dedos (de la mano y del pie): solo se ven por el dorso, en la falange distal, junto a la
 * punta. Una uña es una placa de color distinto al de la piel del dedo (más clara y rosada, o del color
 * del esmalte), lisa y de borde nítido. Sirven para confirmar cada dedo, como punto de referencia preciso
 * y para distinguir el dorso de la palma (mano) o de la planta (pie).
 *
 * Coordenadas en píxeles de la imagen [rgb] (w×h).
 */
object Nails {
    /** Una uña: centro (px), radio (px) y contraste de color con la piel del dedo (ΔE, CIELAB). */
    class Nail(val x: Float, val y: Float, val r: Float, val contrast: Float)

    /** Contraste mínimo (ΔE) entre la placa de la uña y la piel del mismo dedo. */
    const val MIN_DE = 7f

    /**
     * Uña del dedo cuya punta está en ([tx], [ty]), con dirección distal unitaria ([ux], [uy]) (de la
     * articulación hacia la punta) y ancho [width] (px). Se busca a lo largo del dedo, por detrás de la
     * punta, la placa más distinta de la piel del dedo (más atrás, en la falange media o el antepié);
     * null si no hay ninguna con contraste suficiente o si se sale de la imagen. [inside]: piel del dedo
     * (opcional: la placa y la piel de referencia deben caer dentro).
     */
    fun find(rgb: IntArray, w: Int, h: Int, tx: Float, ty: Float, ux: Float, uy: Float, width: Float,
             inside: ((Int, Int) -> Boolean)? = null, minDe: Float = MIN_DE): Nail? {
        if (width < 4f) return null
        val r = max(1.5f, 0.2f * width)
        // color de la piel de este dedo, más atrás (falange media o base del dedo)
        val skin = disc(rgb, w, h, tx - ux * 1.2f * width, ty - uy * 1.2f * width, 0f, r * 1.2f, inside, null)?.first ?: return null
        val isSkin = { l: FloatArray -> deltaE(l, skin) < SKIN_DE }
        var best: Nail? = null
        for (d in floatArrayOf(0.25f, 0.35f, 0.45f)) {
            val cx = tx - ux * d * width; val cy = ty - uy * d * width
            val c = disc(rgb, w, h, cx, cy, 0f, r, null, null) ?: continue
            // piel que rodea la uña en el mismo dedo (pliegues lateral y proximal): anillo alrededor de la placa,
            // solo con los píxeles de piel (no el fondo junto a la punta). En la yema (palma, planta) el anillo
            // es de la misma piel y no hay contraste
            val ring = disc(rgb, w, h, cx, cy, r * 1.3f, r * 2f, inside, isSkin, minFrac = 0.35f) ?: continue
            val de = deltaE(c.first, ring.first)
            // placa lisa: su dispersión de color no puede ser mucho mayor que la diferencia con la piel
            if (c.second > de * 0.9f + 4f) continue
            if (best == null || de > best.contrast) best = Nail(cx, cy, r, de)
        }
        return best?.takeIf { it.contrast >= minDe }
    }

    /** Diferencia de color máxima (ΔE) con la piel del dedo para contar un píxel como piel. */
    const val SKIN_DE = 14f

    /**
     * Media (L*, a*, b*) y dispersión (ΔE medio a la media) de un disco o anillo (radios [r0]–[r1]), con los
     * píxeles dentro de [inside] y que cumplen [keep]; null si se sale de la imagen o quedan menos de [minFrac].
     */
    private fun disc(rgb: IntArray, w: Int, h: Int, cx: Float, cy: Float, r0: Float, r1: Float, inside: ((Int, Int) -> Boolean)?,
                     keep: ((FloatArray) -> Boolean)?, minFrac: Float = 0.5f): Pair<FloatArray, Float>? {
        val labs = ArrayList<FloatArray>()
        val ri = r1.toInt().coerceAtLeast(1)
        var total = 0
        for (dy in -ri..ri) for (dx in -ri..ri) {
            val d2 = (dx * dx + dy * dy).toFloat()
            if (d2 > r1 * r1 || d2 < r0 * r0) continue
            total++
            val x = (cx + dx).toInt(); val y = (cy + dy).toInt()
            if (x !in 0 until w || y !in 0 until h) return null
            if (inside != null && !inside(x, y)) continue
            val l = lab(rgb[y * w + x])
            if (keep != null && !keep(l)) continue
            labs += l
        }
        if (labs.size < max(3, (total * minFrac).toInt())) return null
        val m = FloatArray(3) { k -> labs.map { it[k] }.average().toFloat() }
        val spread = labs.map { deltaE(it, m) }.average().toFloat()
        return m to spread
    }

    fun deltaE(a: FloatArray, b: FloatArray) = sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2))

    /** sRGB → CIELAB (D65). */
    fun lab(c: Int): FloatArray {
        fun lin(v: Int): Float { val s = v / 255f; return if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f) }
        val r = lin((c shr 16) and 0xFF); val g = lin((c shr 8) and 0xFF); val b = lin(c and 0xFF)
        val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f
        fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
        return floatArrayOf(116f * f(y) - 16f, 500f * (f(x) - f(y)), 200f * (f(y) - f(z)))
    }

    /**
     * Uñas de una mano (21 puntos en unidades del ancho de una imagen de [w] px): una por dedo (pulgar a
     * meñique), null donde no se ve uña.
     */
    fun ofHand(rgb: IntArray, w: Int, h: Int, pts: List<Pt>, minDe: Float = MIN_DE): List<Nail?> {
        if (pts.size < 21) return List(5) { null }
        fun px(p: Pt) = (p.x * w) to (p.y * w)
        return (0 until 5).map { f ->
            val tip = 4 + 4 * f; val dip = tip - 1
            val (tx, ty) = px(pts[tip]); val (dx, dy) = px(pts[dip])
            val len = hypot(tx - dx, ty - dy).coerceAtLeast(1e-3f)
            // ancho del dedo ≈ largo de su falange distal (el pulgar, algo más)
            find(rgb, w, h, tx, ty, (tx - dx) / len, (ty - dy) / len, len * (if (f == 0) 1.1f else 0.95f), minDe = minDe)
        }
    }

    /**
     * Placa de la uña: centro (px), eje del dedo ([ux], [uy], hacia la punta), semiejes a lo largo ([a]) y de
     * través ([b]) en px, y separación de color con la piel que la rodea.
     */
    class Plate(val x: Float, val y: Float, val ux: Float, val uy: Float, val a: Float, val b: Float, val contrast: Float) {
        /** Contorno (elipse orientada) en px. */
        fun outline(n: Int = 16): List<Pt> = (0 until n).map { i ->
            val t = i * 2 * Math.PI / n; val ca = kotlin.math.cos(t).toFloat() * a; val sb = kotlin.math.sin(t).toFloat() * b
            Pt(x + ca * ux - sb * uy, y + ca * uy + sb * ux)
        }
    }

    /** Separación mínima (mediana de la puntuación de la placa frente a la de la piel que la rodea). */
    const val PLATE_SEP = 6f

    /**
     * Uñas de una mano por el **esqueleto** (falange distal: de la articulación interfalángica distal a la punta)
     * y la **variación natural de color**: la placa es más clara y menos roja que la piel del propio dedo (o de
     * otro color, con esmalte). En una rejilla alineada con el dedo se puntúa cada punto frente a la piel de la
     * falange (junto a la articulación); umbral adaptativo (Otsu) dentro de la zona esperada de la placa (centro
     * del dedo, 30–110 % de la falange distal); se acepta si la placa se separa de la piel que la rodea, y su
     * forma crece hacia el borde libre sin entrar en el fondo. null donde no se ve uña (palma, dedo de perfil).
     */
    fun plates(rgb: IntArray, w: Int, h: Int, pts: List<Pt>): List<Plate?> {
        if (pts.size < 21) return List(5) { null }
        return (0 until 5).map { f -> runCatching { plate(rgb, w, h, pts, f) }.getOrNull() }
    }

    private fun plate(rgb: IntArray, w: Int, h: Int, pts: List<Pt>, f: Int): Plate? {
        val tip = 4 + 4 * f
        val dx0 = pts[tip - 1].x * w; val dy0 = pts[tip - 1].y * w; val tx = pts[tip].x * w; val ty = pts[tip].y * w
        val l = hypot(tx - dx0, ty - dy0); if (l < 5f) return null
        val ux = (tx - dx0) / l; val uy = (ty - dy0) / l; val vx = -uy; val vy = ux
        val wd = l * (if (f == 0) 1.1f else 0.9f)
        val st = max(0.35f, l / 36f)
        val s0 = -0.25f * l; val ns = ((1.25f * l - s0) / st).toInt() + 1
        val t0 = -0.55f * wd; val nt = ((1.1f * wd) / st).toInt() + 2
        val n = ns * nt
        val L = FloatArray(n); val A = FloatArray(n); val B = FloatArray(n)
        for (i in 0 until ns) for (j in 0 until nt) {
            val s = s0 + i * st; val t = t0 + j * st
            val x = dx0 + s * ux + t * vx; val y = dy0 + s * uy + t * vy
            if (x < 0 || y < 0 || x > w - 1 || y > h - 1) return null
            val c = bilinear(rgb, w, h, x, y); val lab = lab(c); val k = i * nt + j
            L[k] = lab[0]; A[k] = lab[1]; B[k] = lab[2]
        }
        smooth(L, ns, nt); smooth(A, ns, nt); smooth(B, ns, nt)
        fun sAt(k: Int) = s0 + (k / nt) * st
        fun tAt(k: Int) = t0 + (k % nt) * st
        // piel del propio dedo: junto a la articulación distal, en el centro
        val ref = (0 until n).filter { sAt(it) > -0.2f * l && sAt(it) < 0.2f * l && abs(tAt(it)) < 0.25f * wd }
        if (ref.size < 5) return null
        val sk = floatArrayOf(median(ref.map { L[it] }), median(ref.map { A[it] }), median(ref.map { B[it] }))
        val de = FloatArray(n) { sqrt((L[it] - sk[0]).pow(2) + (A[it] - sk[1]).pow(2) + (B[it] - sk[2]).pow(2)) }
        // más clara y menos roja que la piel, o de otro color (esmalte)
        val score = FloatArray(n) { max(L[it] - sk[0] - 1.5f * (A[it] - sk[1]), de[it] - 8f) }
        val prior = BooleanArray(n) { sAt(it) > 0.3f * l && sAt(it) < 1.1f * l && abs(tAt(it)) < 0.38f * wd }
        val vals = (0 until n).filter { prior[it] }.map { score[it] }
        if (vals.size < 10) return null
        val thr = max(4f, otsu(vals))
        var cand = BooleanArray(n) { prior[it] && score[it] > thr }
        cand = morph(morph(cand, ns, nt, false), ns, nt, true)          // apertura
        val comps = SkinLesions.components(cand, nt, ns)
        val seed = comps.maxByOrNull { it.size } ?: return null
        if (seed.size * st * st < 0.06f * l * wd) return null
        fun extent(c: IntArray): FloatArray {
            var smin = Float.MAX_VALUE; var smax = -Float.MAX_VALUE; var tmin = Float.MAX_VALUE; var tmax = -Float.MAX_VALUE; var tsum = 0f
            for (k in c) { val s = sAt(k); val t = tAt(k); smin = min(smin, s); smax = max(smax, s); tmin = min(tmin, t); tmax = max(tmax, t); tsum += t }
            return floatArrayOf(smin, smax, tmin, tmax, tsum / c.size)
        }
        val e = extent(seed)
        val es = e[1] - e[0] + st; val et = e[3] - e[2] + st
        // separación con la piel que la rodea (lados y pliegue proximal)
        val m = BooleanArray(n).also { for (k in seed) it[k] = true }
        var big = m; repeat(max(2, (0.12f * wd / st).toInt())) { big = morph(big, ns, nt, true) }
        val near = morph(m, ns, nt, true)
        val ring = (0 until n).filter { big[it] && !near[it] && abs(tAt(it)) < 0.5f * wd && sAt(it) < 1.0f * l }
        val sep = if (ring.size > 5) median(seed.map { score[it] }) - median(ring.map { score[it] }) else 0f
        if (et < 0.35f * wd || et > 1.0f * wd || es < 0.25f * l || es > 1.3f * l || sep < PLATE_SEP || abs(e[4]) > 0.25f * wd) return null
        // forma: crece desde la semilla hacia el borde libre, sin el fondo (claro y sin color, o pegado al borde de la rejilla)
        val cs = hypot(sk[1], sk[2])
        val bg = BooleanArray(n) { hypot(A[it], B[it]) < 0.45f * cs && L[it] - sk[0] > 8f }
        val far = BooleanArray(n) { bg[it] || de[it] > 30f }
        SkinLesions.components(far, nt, ns).forEach { c ->
            if (c.any { k -> k % nt == 0 || k % nt == nt - 1 || k / nt == ns - 1 }) for (k in c) if (de[k] > 18f) bg[k] = true
        }
        val grow = BooleanArray(n) { (score[it] > 0.7f * thr && !bg[it] && abs(tAt(it)) < 0.45f * wd && sAt(it) > 0.2f * l) || m[it] }
        val grown = SkinLesions.components(grow, nt, ns).filter { c -> c.any { m[it] } }.flatMap { it.asIterable() }.toIntArray()
        var fin = e
        if (grown.size <= 2.2f * seed.size) {
            val g = extent(grown)
            if (g[3] - g[2] + st <= 1.0f * wd && g[1] - g[0] + st <= 1.3f * l) fin = g
        }
        val sc = (fin[0] + fin[1]) / 2; val tc = fin[4]
        return Plate(dx0 + sc * ux + tc * vx, dy0 + sc * uy + tc * vy, ux, uy, (fin[1] - fin[0] + st) / 2, (fin[3] - fin[2] + st) / 2, sep)
    }

    private fun bilinear(rgb: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val x0 = x.toInt().coerceIn(0, w - 2); val y0 = y.toInt().coerceIn(0, h - 2); val fx = x - x0; val fy = y - y0
        fun ch(c: Int, sh: Int) = (c shr sh) and 0xFF
        val p00 = rgb[y0 * w + x0]; val p10 = rgb[y0 * w + x0 + 1]; val p01 = rgb[(y0 + 1) * w + x0]; val p11 = rgb[(y0 + 1) * w + x0 + 1]
        fun mix(sh: Int) = ((ch(p00, sh) * (1 - fx) + ch(p10, sh) * fx) * (1 - fy) + (ch(p01, sh) * (1 - fx) + ch(p11, sh) * fx) * fy).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Suavizado 3×3 (ponderado 1-2-1) en una rejilla [ns]×[nt]. */
    private fun smooth(a: FloatArray, ns: Int, nt: Int) {
        val o = a.copyOf()
        for (i in 0 until ns) for (j in 0 until nt) {
            var s = 0f; var wsum = 0f
            for (di in -1..1) for (dj in -1..1) {
                val ii = i + di; val jj = j + dj; if (ii !in 0 until ns || jj !in 0 until nt) continue
                val k = (2 - abs(di)) * (2 - abs(dj)).toFloat(); s += o[ii * nt + jj] * k; wsum += k
            }
            a[i * nt + j] = s / wsum
        }
    }

    /** Erosión o dilatación con la cruz 3×3 (vecinos de 4). */
    private fun morph(m: BooleanArray, ns: Int, nt: Int, dil: Boolean): BooleanArray = BooleanArray(m.size) { k ->
        val i = k / nt; val j = k % nt
        val nb = listOf(k to true, (k - nt) to (i > 0), (k + nt) to (i < ns - 1), (k - 1) to (j > 0), (k + 1) to (j < nt - 1))
        if (dil) nb.any { (q, ok) -> ok && m[q] } else nb.all { (q, ok) -> ok && m[q] }
    }

    private fun median(v: List<Float>): Float = v.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }

    /** Umbral de Otsu buscado entre los cuantiles 10–90 %. */
    private fun otsu(v: List<Float>): Float {
        val x = v.sorted(); var best = -1.0; var thr = x[x.size / 2]
        for (q in 0..32) {
            val t = x[((0.1 + 0.8 * q / 32) * (x.size - 1)).toInt()]
            var na = 0; var sa = 0.0; var nb = 0; var sb = 0.0
            for (y in x) if (y <= t) { na++; sa += y } else { nb++; sb += y }
            if (na < 3 || nb < 3) continue
            val d = sa / na - sb / nb; val vv = na.toDouble() * nb * d * d
            if (vv > best) { best = vv; thr = t }
        }
        return thr
    }
}
