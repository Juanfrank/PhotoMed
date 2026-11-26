package com.photomed.app.align

import com.photomed.app.data.GuideType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Referencias anatómicas del tronco para alinear y medir: **axilas**, **pezones** y **ombligo**.
 *
 * - **Axila**: el vértice del hueco entre el brazo y el tronco en la silueta (segmentación): la fila más alta,
 *   bajo el hombro, en la que entre el tronco y el brazo aparece fondo. Sirve de frente, de espaldas y de lado.
 * - **Ombligo**: mancha redonda más oscura que la piel en las ocho direcciones (un surco abdominal, alargado, no
 *   lo es en todas), en la línea media, hacia el 80 % del tramo hombros → caderas (antropometría: ombligo ≈ 0,60
 *   de la estatura, hombros ≈ 0,82, caderas ≈ 0,53).
 * - **Pezones** (areolas): manchas redondas más oscuras y más rojas que la piel, hacia el 30 % del tramo y a
 *   ≈ ¼ del ancho de hombros de la línea media. Solo si la zona es piel (no ropa) y se encuentran los dos, a una
 *   altura parecida y con una separación plausible; si no, ninguno (mejor sin punto que con uno falso).
 *
 * Solo de frente para pezones y ombligo (el hombro izquierdo a la derecha de la imagen). Coordenadas en unidades
 * del ancho.
 */
object TorsoMarks {
    const val AXILLA_A = "axila_a"; const val AXILLA_B = "axila_b"
    const val NIPPLE_A = "pezon_a"; const val NIPPLE_B = "pezon_b"
    const val NAVEL = "ombligo"
    val FRONT_KEYS = setOf(AXILLA_A, AXILLA_B, NIPPLE_A, NIPPLE_B, NAVEL)
    val AXILLA_KEYS = setOf(AXILLA_A, AXILLA_B)
    /** Guías del tronco con estas referencias. */
    val GUIDES = setOf(GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK, GuideType.TORSO_SIDE)

    /** Contraste mínimo (L*) de la mancha frente a su alrededor, en todas las direcciones. */
    const val MIN_BLOB = 4f
    /** Ancho de trabajo para pezones y ombligo. */
    private const val WORK_W = 480

    private val L = Landmarks

    fun of(guide: GuideType, c: ZoneCriteria.Classes, pose: Map<String, Pt>, hi: ZoneCriteria.Hi?): Map<String, Pt> {
        val sl = pose[L.SHOULDER_L]; val sr = pose[L.SHOULDER_R]; val hl = pose[L.HIP_L]; val hr = pose[L.HIP_R]
        // primer plano sin hombros ni caderas (abdomen, tórax): pezones y ombligo solo por la piel
        if (sl == null || sr == null || hl == null || hr == null)
            return if (guide == GuideType.TORSO_FRONT || guide == GuideType.BREASTS) frontMarks(c, hi, null, null, null) else emptyMap()
        val out = HashMap<String, Pt>()
        val ms = mid(sl, sr); val mh = mid(hl, hr)
        // axilas: lado de la imagen (a = izquierda)
        val sides = listOf(Triple(sl, pose[L.ELBOW_L], hl), Triple(sr, pose[L.ELBOW_R], hr)).sortedBy { it.first.x }
        sides.forEachIndexed { i, (s, e, h) -> if (e != null) axilla(c, s, h, ms, abs(sl.x - sr.x))?.let { out[if (i == 0) AXILLA_A else AXILLA_B] = it } }
        val front = sl.x > sr.x
        if (!front || (guide != GuideType.TORSO_FRONT && guide != GuideType.BREASTS)) return out
        out.putAll(frontMarks(c, hi, ms, mh, hypot(sl.x - sr.x, sl.y - sr.y)))
        return out
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)

    /** Vértice del hueco brazo–tronco bajo el hombro [s] (fila más alta con persona → fondo → persona). */
    fun axilla(c: ZoneCriteria.Classes, s: Pt, hip: Pt, mid: Pt, shoulderW: Float): Pt? {
        val k = c.width.toFloat()
        fun person(x: Int, y: Int) = c.at(x, y) != FaceLabels.BG
        val d = if (s.x > mid.x) 1 else -1
        val sw = shoulderW * k
        val y0 = (s.y * k).toInt(); val y1 = ((s.y + 0.45f * (hip.y - s.y)) * k).toInt()
        val sx = s.x * k
        for (y in max(0, y0)..min(c.height - 1, y1)) {
            val xa = (sx - d * 0.3f * sw).toInt(); val steps = (1.2f * sw).toInt()
            var st = 0; var inner = -1
            for (i in 0..steps) {
                val x = xa + d * i
                val v = x in 0 until c.width && person(x, y)
                if (st == 0 && v) st = 1
                else if (st == 1 && !v) { st = 2; inner = x - d }
                else if (st == 2 && v) {
                    if (abs(inner - sx) < 0.45f * sw) return Pt((inner + 0.5f) / k, (y + 0.5f) / k)
                    break
                }
            }
        }
        return null
    }

    /** Candidato: mancha redonda más oscura que la piel alrededor (px de la imagen de trabajo). */
    class Blob(val x: Float, val y: Float, val r: Float, val score: Float, val red: Float)

    /**
     * Pezones y ombligo (de frente). Con hombros y caderas de la pose, se buscan donde la anatomía los espera; sin
     * ellos (primer plano del abdomen o del tórax, lo habitual en la consulta), solo con la piel: el ombligo, la
     * mancha redonda más marcada junto a la línea media de la piel (si el tronco entra entero en el encuadre) y fuera
     * del quinto superior; los pezones, la mejor pareja simétrica por encima del ombligo.
     */
    fun frontMarks(c: ZoneCriteria.Classes, hi: ZoneCriteria.Hi?, ms: Pt?, mh: Pt?, sw: Float?): Map<String, Pt> {
        val px: IntArray; val pw: Int; val ph: Int
        when {
            hi != null -> { px = hi.px; pw = hi.w; ph = hi.h }
            c.rgb != null -> { px = c.rgb; pw = c.width; ph = c.height }
            else -> return emptyMap()
        }
        fun skinAt(x: Int, y: Int) = c.at(x * c.width / pw, y * c.height / ph).let { it == FaceLabels.BODY_SKIN || it == FaceLabels.FACE_SKIN }
        // recorte: el tronco de la pose (con margen) o la piel; a un ancho fijo
        var x0: Int; var y0: Int; var x1: Int; var y1: Int
        val k0 = pw.toFloat()
        if (ms != null && mh != null && sw != null) {
            val tl = hypot(mh.x - ms.x, mh.y - ms.y)
            x0 = ((min(ms.x, mh.x) - 0.75f * sw) * k0).toInt(); x1 = ((max(ms.x, mh.x) + 0.75f * sw) * k0).toInt()
            y0 = ((min(ms.y, mh.y) - 0.1f * tl) * k0).toInt(); y1 = ((max(ms.y, mh.y) + 0.15f * tl) * k0).toInt()
        } else {
            val xs = ArrayList<Int>(); val ys = ArrayList<Int>()
            for (y in 0 until ph step 4) for (x in 0 until pw step 4) if (skinAt(x, y)) { xs += x; ys += y }
            if (xs.size < 50) return emptyMap()
            xs.sort(); ys.sort()
            x0 = xs[(xs.size * 0.005f).toInt()] - 5; x1 = xs[(xs.size * 0.995f).toInt()] + 5
            y0 = ys[(ys.size * 0.005f).toInt()] - 5; y1 = ys[(ys.size * 0.995f).toInt()] + 5
        }
        x0 = x0.coerceIn(0, pw - 1); y0 = y0.coerceIn(0, ph - 1); x1 = x1.coerceIn(x0 + 1, pw); y1 = y1.coerceIn(y0 + 1, ph)
        val w = min(WORK_W, x1 - x0); val sc = w / (x1 - x0).toFloat(); val h = ((y1 - y0) * sc).toInt().coerceAtLeast(1)
        if (w < 32 || h < 32) return emptyMap()
        val sub = IntArray(w * h) { i -> px[(y0 + ((i / w) / sc).toInt()).coerceAtMost(ph - 1) * pw + (x0 + ((i % w) / sc).toInt()).coerceAtMost(pw - 1)] }
        val skin = BooleanArray(w * h) { i -> skinAt((x0 + ((i % w) / sc).toInt()).coerceAtMost(pw - 1), (y0 + ((i / w) / sc).toInt()).coerceAtMost(ph - 1)) }
        val lab = LabImage.fromArgb(sub, w, h)
        fun toPt(x: Float, y: Float) = Pt((x0 + x / sc) / k0, (y0 + y / sc) / k0)
        fun toW(p: Pt) = Pt((p.x * k0 - x0) * sc, (p.y * k0 - y0) * sc)
        // filas de piel: ancho y centro
        val rows = (0 until h).mapNotNull { y ->
            var a = -1; var b = -1; var n = 0
            for (x in 0 until w) if (skin[y * w + x]) { if (a < 0) a = x; b = x; n++ }
            if (n > 0.1f * w) Triple(y, a, b) else null
        }
        if (rows.size < 10) return emptyMap()
        val swW = if (sw != null) sw * k0 * sc else rows.map { (it.third - it.second).toFloat() }.sorted()[rows.size / 2]
        val cands = candidates(lab, skin, w, h, swW)
        val out = HashMap<String, Pt>()
        var navel: Blob? = null
        if (ms != null && mh != null && sw != null) {
            // con la pose: ventanas anatómicas
            val a = toW(ms); val b = toW(mh); val tx = b.x - a.x; val ty = b.y - a.y; val tl = hypot(tx, ty)
            fun t(c: Blob) = ((c.x - a.x) * tx + (c.y - a.y) * ty) / (tl * tl)
            fun lat(c: Blob) = ((c.x - a.x) * ty - (c.y - a.y) * tx) / tl
            navel = cands.filter { t(it) in 0.55f..1.05f && abs(lat(it)) < 0.15f * swW && it.score >= NAVEL_MIN }
                .maxByOrNull { it.score * it.r.pow(0.3f) - 0.5f * max(0f, it.red) }
            pair(cands.filter { t(it) in 0.1f..0.55f && abs(lat(it)) in 0.1f * swW..0.45f * swW }, swW, null, navel)?.let { (l, r) ->
                out[NIPPLE_A] = toPt(l.x, l.y); out[NIPPLE_B] = toPt(r.x, r.y) }
        } else {
            // sin pose: la línea media solo si el tronco entra entero (la piel no toca los bordes)
            val touch = max(rows.count { (_, l, _) -> l < 3 }, rows.count { (_, _, r) -> r > w - 4 }) / rows.size.toFloat()
            val mid = if (touch > 0.25f) null else rows.subList(rows.size / 4, 3 * rows.size / 4).map { (it.second + it.third) / 2f }.sorted().let { it[it.size / 2] }
            val top = rows.first().first; val bot = rows.last().first
            navel = cands.filter { (mid == null || abs(it.x - mid) < 0.12f * swW) && it.y > top + 0.2f * (bot - top) && it.score >= NAVEL_MIN }
                .maxByOrNull { it.score * it.r.pow(0.3f) - 0.5f * max(0f, it.red) }
            pair(cands, swW, mid, navel)?.let { (l, r) -> out[NIPPLE_A] = toPt(l.x, l.y); out[NIPPLE_B] = toPt(r.x, r.y) }
        }
        navel?.let { out[NAVEL] = toPt(it.x, it.y) }
        return out
    }

    /** Pezones: contraste (L*) y exceso de rojo (a*) mínimos de cada uno. */
    const val NIPPLE_MIN = 6f
    const val NIPPLE_RED = -99f

    /** Contraste mínimo del ombligo (más marcado que una mancha cualquiera). */
    const val NAVEL_MIN = 5f

    /** La mejor pareja de pezones: a una altura parecida, separados lo plausible, centrados y por encima del ombligo. */
    private fun pair(c: List<Blob>, sw: Float, mid: Float?, navel: Blob?): Pair<Blob, Blob>? {
        var best: Triple<Float, Blob, Blob>? = null
        for (i in c.indices) for (j in i + 1 until c.size) {
            val a = c[i]; val b = c[j]
            if (abs(a.y - b.y) > 0.08f * sw) continue
            if (abs(a.x - b.x) !in 0.25f * sw..0.75f * sw) continue
            val m = (a.x + b.x) / 2
            if (mid != null && abs(m - mid) > 0.1f * sw) continue
            // por encima del ombligo lo que la anatomía da (≈ 0,35–0,9 anchos del tronco) y centrados con él
            if (navel != null && ((navel.y - (a.y + b.y) / 2) !in 0.35f * sw..0.9f * sw || abs(m - navel.x) > 0.12f * sw)) continue
            // la areola es más roja (o parda) que la piel: un borde muscular o el vello, no
            if (min(a.red, b.red) < NIPPLE_RED || min(a.score, b.score) < NIPPLE_MIN) continue
            val s = min(a.score, b.score)
            if (best == null || s > best.first) best = Triple(s, a, b)
        }
        return best?.let { (_, a, b) -> if (a.x <= b.x) a to b else b to a }
    }

    /**
     * Manchas redondas más oscuras que la piel en las ocho direcciones (a 1,9 radios; el mínimo de las ocho), con la
     * piel alrededor en todas, a tres escalas (1,2 %, 2 % y 3 % del ancho del tronco); máximos locales, sin repetir.
     */
    fun candidates(lab: LabImage, skin: BooleanArray, w: Int, h: Int, sw: Float): List<Blob> {
        val all = ArrayList<Blob>()
        for (f in floatArrayOf(0.012f, 0.02f, 0.03f)) {
            val r = max(1.5f, f * sw); val sg = max(0.8f, 0.6f * r)
            val lb = SkinLesions.blurNorm(lab.l, skin, w, h, sg); val ab = SkinLesions.blurNorm(lab.a, skin, w, h, sg)
            val dirs = (0 until 8).map { val t = it * Math.PI / 4; Math.round(1.9f * r * cos(t).toFloat()) to Math.round(1.9f * r * sin(t).toFloat()) }
            val sc = FloatArray(w * h) { -99f }; val rd = FloatArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x; if (!skin[i]) continue
                var mn = Float.MAX_VALUE; var asum = 0f; var ok = true
                for ((dx, dy) in dirs) {
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h || !skin[yy * w + xx]) { ok = false; break }
                    mn = min(mn, lb[yy * w + xx]); asum += ab[yy * w + xx]
                }
                if (!ok) continue
                sc[i] = mn - lb[i]; rd[i] = ab[i] - asum / 8f
            }
            // máximos locales (radio 2r)
            val rr = max(1, (2 * r).toInt())
            val idx = (0 until w * h).filter { sc[it] >= 3f }.sortedByDescending { sc[it] }
            val taken = ArrayList<Blob>()
            for (i in idx) {
                val x = (i % w).toFloat(); val y = (i / w).toFloat()
                if (taken.any { hypot(it.x - x, it.y - y) <= rr }) continue
                taken += Blob(x, y, r, sc[i], rd[i])
                if (taken.size > 200) break
            }
            all += taken
        }
        val keep = ArrayList<Blob>()
        for (b in all.sortedByDescending { it.score }) if (keep.none { hypot(it.x - b.x, it.y - b.y) <= 2 * max(it.r, b.r) }) keep += b
        return keep
    }
}
