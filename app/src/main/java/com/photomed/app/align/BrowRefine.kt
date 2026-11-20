package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.hypot
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
}
