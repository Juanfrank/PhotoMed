package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Ajuste de las cejas a la imagen. La malla (MediaPipe o ML Kit) a veces deja la ceja por encima o
 * por debajo del pelo de la ceja (cejas canosas, pobladas, gafas, gorra, piel oscura), y con ella la
 * altura de la frente (cejas → línea del pelo) y la de la ceja sobre la pupila.
 *
 * Se busca la **franja de la ceja** en 13 columnas perpendiculares a la línea de los ojos, cerca de
 * la malla (±8 % de la distancia interpupilar), con el grosor que da la malla: más textura fina (pelo)
 * que la piel de encima y de debajo, y más oscura (o algo más clara, si es canosa). Comparar con las
 * dos franjas de piel evita confundirla con una arruga de la frente (fina) o con la sombra del pliegue
 * del párpado (lisa). Solo se mueve si más de la mitad de las columnas encuentran la ceja y coinciden
 * (mediana de las desviaciones < 2 % de la interpupilar); entonces toda la ceja se desplaza la mediana,
 * conservando la forma de la malla (como mucho un 6 % de la interpupilar). Las columnas que tocan gafas,
 * pelo (flequillo) o fondo según la segmentación no cuentan. Sin evidencia clara, la malla no se toca.
 */
object BrowRefine {
    val TOP_R = intArrayOf(70, 63, 105, 66, 107); val BOT_R = intArrayOf(46, 53, 52, 65, 55)
    val TOP_L = intArrayOf(300, 293, 334, 296, 336); val BOT_L = intArrayOf(276, 283, 282, 295, 285)
    private const val COLS = 13
    private const val RANGE = 0.08f
    private const val MIN_SCORE = 0.5f
    /** Columnas en desacuerdo (mediana de las desviaciones) por encima de esto: no se mueve. */
    private const val MAX_MAD = 0.02f
    /** Textura mínima en la franja de la ceja y desplazamiento máximo aceptado (fracción de la interpupilar). */
    private const val MIN_TEX = 0f
    private const val MAX_SHIFT = 0.06f
    /** Escalas de contraste (luminancia 0..1): 12 y 3 niveles de gris. */
    private const val LUM_UNIT = 12f / 255f
    private const val TEX_UNIT = 3f / 255f

    /** Imágenes de trabajo: luminancia suavizada y textura fina (pelo). */
    class Maps(val lum: GrayImage, val tex: GrayImage)

    fun maps(lum: GrayImage): Maps {
        val l = lum.boxBlur(1)
        val hp = GrayImage(lum.width, lum.height, FloatArray(lum.data.size) { abs(lum.data[it] - l.data[it]) })
        return Maps(l, hp.boxBlur(2))
    }

    /** Malla (unidades del ancho de [lum]) con las cejas ajustadas; la misma si no hay evidencia. */
    fun refineMesh(mesh: List<Pt>, lum: GrayImage, labels: FaceLabels? = null, m: Maps = maps(lum)): List<Pt> {
        if (mesh.size < 468) return mesh
        val (er, el) = if (mesh.size >= 478) mesh[468] to mesh[473]
            else mid(mesh[33], mesh[133]) to mid(mesh[263], mesh[362])
        val out = mesh.toMutableList()
        for ((top, bot) in listOf(TOP_R to BOT_R, TOP_L to BOT_L)) {
            val r = refineBrow(top.map { mesh[it] }, bot.map { mesh[it] }, er, el, lum.width, m, labels) ?: continue
            top.forEachIndexed { k, i -> out[i] = r.first[k] }
            bot.forEachIndexed { k, i -> out[i] = r.second[k] }
        }
        return out
    }

    /** Lo mismo con los puntos por nombre (`brow_r_top_0`…; ML Kit o malla), unidades del ancho de [lum]. */
    fun refineKeys(keys: Map<String, Pt>, lum: GrayImage, labels: FaceLabels? = null, m: Maps = maps(lum)): Map<String, Pt> {
        val c = FaceKeys.canonical(keys)
        val er = c[Landmarks.EYE_R] ?: return keys; val el = c[Landmarks.EYE_L] ?: return keys
        val out = LinkedHashMap(keys)
        for (s in listOf("r", "l")) {
            val tk = (0 until 5).map { "brow_${s}_top_$it" }; val bk = (0 until 5).map { "brow_${s}_bottom_$it" }
            val top = tk.map { keys[it] ?: return@map null }; val bot = bk.map { keys[it] ?: return@map null }
            if (top.any { it == null } || bot.any { it == null }) continue
            val r = refineBrow(top.map { it!! }, bot.map { it!! }, er, el, lum.width, m, labels) ?: continue
            tk.forEachIndexed { k, n -> out[n] = r.first[k] }
            bk.forEachIndexed { k, n -> out[n] = r.second[k] }
        }
        return out
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)

    /**
     * Una ceja: borde superior [top] (en orden a lo largo de la ceja) e inferior [bot]. Devuelve los
     * puntos desplazados, o null si no hay evidencia suficiente y coherente.
     */
    fun refineBrow(top: List<Pt>, bot: List<Pt>, er: Pt, el: Pt, w: Int, m: Maps,
                   /** Segmentación (opcional): las columnas con gafas, pelo o fondo en la franja no cuentan. */
                   labels: FaceLabels? = null): Pair<List<Pt>, List<Pt>>? {
        val wf = w.toFloat()
        val ipd = hypot(el.x - er.x, el.y - er.y) * wf
        if (ipd < 20f) return null
        // ejes: a lo largo de los ojos y hacia abajo (de la ceja al ojo)
        val ex = (el.x - er.x) * wf / ipd; val ey = (el.y - er.y) * wf / ipd
        var dx = -ey; var dy = ex
        if (dy < 0) { dx = -dx; dy = -dy }
        val range = (RANGE * ipd).roundToInt().coerceAtLeast(3)
        val half = (0.012f * ipd).coerceAtLeast(2f)
        val tp = top.map { Pt(it.x * wf, it.y * wf) }; val bp = bot.map { Pt(it.x * wf, it.y * wf) }
        val found = ArrayList<Float>()
        for (c in 0 until COLS) {
            val seg = c / (COLS - 1f) * (tp.size - 1)
            val i = seg.toInt().coerceAtMost(tp.size - 2); val f = seg - i
            val px = tp[i].x + (tp[i + 1].x - tp[i].x) * f; val py = tp[i].y + (tp[i + 1].y - tp[i].y) * f
            val qx = bp[i].x + (bp[i + 1].x - bp[i].x) * f; val qy = bp[i].y + (bp[i + 1].y - bp[i].y) * f
            // grosor de la ceja (malla) y franjas de piel encima y debajo
            val th = hypot(qx - px, qy - py).coerceIn(4f, 0.12f * ipd).roundToInt()
            val h = (th * 0.7f).coerceIn(3f, 0.06f * ipd).roundToInt()
            val n = 2 * range + th + 2 * h + 1
            val pl = FloatArray(n); val pt = FloatArray(n); var ok = true
            for (j in 0 until n) {
                val t = (j - range - h).toFloat()
                var sl = 0f; var st = 0f; var cnt = 0
                var o = -half
                while (o <= half + 1e-3f) {
                    val x = px + dx * t + ex * o; val y = py + dy * t + ey * o
                    val a = m.lum.sample(x, y); val b = m.tex.sample(x, y)
                    if (!a.isNaN() && !b.isNaN()) { sl += a; st += b; cnt++ }
                    o += 1f
                }
                if (cnt == 0) { ok = false; break }
                if (labels != null) {
                    val lk = labels.width / wf
                    if (labels[((px + dx * t) * lk).toInt(), ((py + dy * t) * lk).toInt()] != FaceLabels.FACE_SKIN) { ok = false; break }
                }
                pl[j] = sl / cnt; pt[j] = st / cnt
            }
            if (!ok) continue
            fun mean(a: FloatArray, from: Int, len: Int): Float { var s = 0f; for (k in from until from + len) s += a[k]; return s / len }
            var best = -Float.MAX_VALUE; var bestDy = 0; var bestTex = 0f
            for (d in -range..range) {
                val j = d + range + h
                val la = mean(pl, j - h, h); val li = mean(pl, j, th); val lb = mean(pl, j + th, h)
                val ta = mean(pt, j - h, h); val ti = mean(pt, j, th); val tb = mean(pt, j + th, h)
                // la ceja es una franja con más textura (pelo) que la piel de encima y de debajo, y más
                // oscura (o, si es canosa, algo más clara)
                val dark = (minOf(la, lb) - li) / LUM_UNIT; val light = (li - maxOf(la, lb)) / LUM_UNIT
                val tex = (ti - maxOf(ta, tb)) / TEX_UNIT
                val sc = tex + maxOf(dark, 0.5f * light) - 0.6f * abs(d) / range
                if (sc > best) { best = sc; bestDy = d; bestTex = tex }
            }
            // con pelo en la franja (una montura de gafas o una sombra son lisas)
            if (best >= MIN_SCORE && bestTex >= MIN_TEX) found += bestDy.toFloat()
        }
        // evidencia suficiente (más de la mitad de las columnas) y coherente (todas dicen lo mismo)
        if (found.size < COLS / 2 + 1) return null
        val med = found.sorted()[found.size / 2]
        val mad = found.map { abs(it - med) }.sorted()[found.size / 2]
        if (mad > MAX_MAD * ipd || abs(med) < 0.5f || abs(med) > MAX_SHIFT * ipd) return null
        fun shift(p: Pt) = Pt((p.x + dx * med) / wf, (p.y + dy * med) / wf)
        // toda la ceja se mueve igual: la forma de la malla es fiable, el error suele ser de altura
        return tp.map(::shift) to bp.map(::shift)
    }

    // ---------------------------------------------------------------- forma

    /**
     * Corrección de la forma de la ceja de la malla, medida frente a máscaras de ceja por píxel (29 cejas): la malla
     * la deja demasiado fina y alta (su borde inferior pasa por el medio del pelo) y con la cola un 13 % más larga
     * que la ceja real. Desvío de cada punto (de la cola a la cabeza) en grosores de la ceja (+ = hacia abajo) y
     * recorte a lo largo (0 = cabeza, 1 = cola). Con ella y el ajuste por columnas ([snapColumns]), el solapamiento
     * (IoU) con la ceja real pasa de 0,42 a 0,44 dejando cada ceja fuera del cálculo de la corrección.
     */
    private val TOP_DV = floatArrayOf(-0.02f, 0.09f, 0.01f, 0.01f, 0.11f)
    private val BOT_DV = floatArrayOf(-0.07f, 0.25f, 0.39f, 0.33f, -0.34f)
    private const val TAIL_END = 0.873f
    /** Ajuste por columnas: movimiento máximo (grosores) y contraste mínimo (L*) del pelo frente a la frente. */
    private const val SNAP_MOVE = 0.3f
    private const val SNAP_DEPTH = 4f

    /** Malla con la forma de las dos cejas corregida y ajustada al pelo ([lum]: luminancia de la imagen de la malla). */
    fun reshapeMesh(mesh: List<Pt>, lum: GrayImage?): List<Pt> {
        if (mesh.size < 468) return mesh
        val out = mesh.toMutableList()
        val w = lum?.width?.toFloat() ?: 1f
        val ipd = if (mesh.size >= 478) hypot(mesh[468].x - mesh[473].x, mesh[468].y - mesh[473].y) else 0f
        for ((top, bot) in listOf(TOP_R to BOT_R, TOP_L to BOT_L)) {
            val (t, b) = reshape(top.map { mesh[it] }, bot.map { mesh[it] })
            val (t2, b2) = if (lum != null && ipd > 0f) snapColumns(t, b, lum, ipd * w) else t to b
            top.forEachIndexed { k, i -> out[i] = t2[k] }
            bot.forEachIndexed { k, i -> out[i] = b2[k] }
        }
        return out
    }

    /** Marco de la ceja: cabeza, eje hacia la cola, normal hacia abajo, largo y grosor (mediana). */
    private class Frame(val a: Pt, val ux: Float, val uy: Float, val vx: Float, val vy: Float, val len: Float, val th: Float) {
        fun s(p: Pt) = ((p.x - a.x) * ux + (p.y - a.y) * uy) / len
        fun t(p: Pt) = ((p.x - a.x) * vx + (p.y - a.y) * vy) / th
        fun at(s: Float, t: Float) = Pt(a.x + ux * s * len + vx * t * th, a.y + uy * s * len + vy * t * th)
    }

    private fun frame(top: List<Pt>, bot: List<Pt>): Frame? {
        val a = mid(top[4], bot[4]); val b = mid(top[0], bot[0])
        val len = hypot(b.x - a.x, b.y - a.y).takeIf { it > 1e-6f } ?: return null
        val ux = (b.x - a.x) / len; val uy = (b.y - a.y) / len
        var vx = -uy; var vy = ux; if (vy < 0) { vx = -vx; vy = -vy }
        val th = top.indices.map { hypot(top[it].x - bot[it].x, top[it].y - bot[it].y) }.sorted()[2].takeIf { it > 1e-6f } ?: return null
        return Frame(a, ux, uy, vx, vy, len, th)
    }

    /** Corrección sistemática de la forma (ver [TOP_DV]); los puntos van de la cola (0) a la cabeza (4). */
    fun reshape(top: List<Pt>, bot: List<Pt>): Pair<List<Pt>, List<Pt>> {
        val f = frame(top, bot) ?: return top to bot
        fun fix(p: Pt, dv: Float) = f.at(f.s(p) * TAIL_END, f.t(p) + dv)
        return top.mapIndexed { i, p -> fix(p, TOP_DV[i]) } to bot.mapIndexed { i, p -> fix(p, BOT_DV[i]) }
    }

    /**
     * Ajuste por columnas (una por par de puntos): en el perfil perpendicular, el pelo es la franja más oscura que la
     * piel de la frente justo encima; sus bordes (40 % del contraste) mueven los de la ceja como mucho [SNAP_MOVE]
     * grosores, y solo con contraste claro ([SNAP_DEPTH]).
     */
    fun snapColumns(top: List<Pt>, bot: List<Pt>, lum: GrayImage, ipdPx: Float): Pair<List<Pt>, List<Pt>> {
        val f = frame(top, bot) ?: return top to bot
        val wf = lum.width.toFloat()
        val th = f.th * wf
        val st = max(0.5f, ipdPx / 250f)
        val nt = ArrayList<Pt>(); val nb = ArrayList<Pt>()
        for (i in top.indices) {
            val pt = top[i]; val pb = bot[i]
            val cx = (pt.x + pb.x) / 2 * wf; val cy = (pt.y + pb.y) / 2 * wf
            val tc = ((pt.x * wf - cx) * f.vx + (pt.y * wf - cy) * f.vy); val bc = ((pb.x * wf - cx) * f.vx + (pb.y * wf - cy) * f.vy)
            val ts = generateSequence(-2.2f * th) { it + st }.takeWhile { it < 1.6f * th }.toList()
            val prof = ts.map { t -> lstar(lum, cx + f.vx * t, cy + f.vy * t) }
            if (prof.any { it.isNaN() }) { nt += pt; nb += pb; continue }
            val skyV = ts.indices.filter { ts[it] < tc - 0.6f * th && ts[it] > tc - 1.4f * th }.map { prof[it] }.sorted()
            val sky = if (skyV.isEmpty()) prof[0] else skyV[skyV.size / 2]
            val d = prof.map { sky - it }
            var j0 = -1
            for (j in ts.indices) if (ts[j] > tc - 0.3f * th && ts[j] < bc + 0.3f * th && (j0 < 0 || d[j] > d[j0])) j0 = j
            if (j0 < 0 || d[j0] < SNAP_DEPTH) { nt += pt; nb += pb; continue }
            val thr = 0.4f * d[j0]
            var j1 = j0; while (j1 > 0 && d[j1 - 1] > thr) j1--
            var j2 = j0; while (j2 < ts.size - 1 && d[j2 + 1] > thr) j2++
            val tNew = ts[j1].coerceIn(tc - SNAP_MOVE * th, tc + SNAP_MOVE * th)
            val bNew = ts[j2].coerceIn(bc - SNAP_MOVE * th, bc + SNAP_MOVE * th)
            if (bNew - tNew < 0.3f * th) { nt += pt; nb += pb; continue }
            nt += Pt((cx + f.vx * tNew) / wf, (cy + f.vy * tNew) / wf); nb += Pt((cx + f.vx * bNew) / wf, (cy + f.vy * bNew) / wf)
        }
        return nt to nb
    }

    /** L* (0–100) de la luminancia (0..1, gamma sRGB) suavizada en 3×3, en px. */
    private fun lstar(lum: GrayImage, x: Float, y: Float): Float {
        var s = 0f; var n = 0
        for (dy in -1..1) for (dx in -1..1) { val v = lum.sample(x + dx, y + dy); if (!v.isNaN()) { s += v; n++ } }
        if (n == 0) return Float.NaN
        val g = s / n; val lin = if (g <= 0.04045f) g / 12.92f else ((g + 0.055f) / 1.055f).pow(2.4f)
        return if (lin > 0.008856f) 116f * kotlin.math.cbrt(lin) - 16f else 903.3f * lin
    }
}
