package com.photomed.app.align

import com.photomed.app.data.GuideType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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
        val sl = pose[L.SHOULDER_L] ?: return emptyMap(); val sr = pose[L.SHOULDER_R] ?: return emptyMap()
        val hl = pose[L.HIP_L] ?: return emptyMap(); val hr = pose[L.HIP_R] ?: return emptyMap()
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

    /** Pezones y ombligo (de frente), en la foto a más resolución si la hay (si no, en el color de [c]). */
    private fun frontMarks(c: ZoneCriteria.Classes, hi: ZoneCriteria.Hi?, ms: Pt, mh: Pt, sw: Float): Map<String, Pt> {
        // imagen de trabajo: la foto reducida a WORK_W (o la rejilla de la segmentación con su color)
        val (px, w, h) = when {
            hi != null -> {
                val w = min(WORK_W, hi.w); val h = (hi.h * w / hi.w.toFloat()).toInt().coerceAtLeast(1)
                Triple(IntArray(w * h) { i -> hi.px[((i / w) * hi.h / h) * hi.w + (i % w) * hi.w / w] }, w, h)
            }
            c.rgb != null -> Triple(c.rgb, c.width, c.height)
            else -> return emptyMap()
        }
        val k = w.toFloat()
        val swPx = sw * k
        val r = 0.03f * swPx
        if (r < 1.5f) return emptyMap()                               // demasiado pequeño para verlos
        val skin = BooleanArray(w * h) { i -> c.at((i % w) * c.width / w, (i / w) * c.height / h) == FaceLabels.BODY_SKIN }
        val lab = LabImage.fromArgb(px, w, h)
        val sx = ms.x * k; val sy = ms.y * k
        val tx = (mh.x - ms.x) * k; val ty = (mh.y - ms.y) * k; val tl = hypot(tx, ty).takeIf { it > 1f } ?: return emptyMap()
        val ux = tx / tl; val uy = ty / tl
        val nx = -uy; val ny = ux                                      // normal: hacia la derecha de la imagen si el tronco va hacia abajo
        val out = HashMap<String, Pt>()
        fun skinFrac(cx: Float, cy: Float, hw: Float, hh: Float): Float {
            var n = 0; var s = 0
            for (y in max(0, (cy - hh).toInt())..min(h - 1, (cy + hh).toInt())) for (x in max(0, (cx - hw).toInt())..min(w - 1, (cx + hw).toInt())) { n++; if (skin[y * w + x]) s++ }
            return if (n == 0) 0f else s / n.toFloat()
        }
        // ombligo
        run {
            val cx = sx + 0.8f * tx; val cy = sy + 0.8f * ty
            if (skinFrac(cx, cy, 0.2f * swPx, 0.2f * tl) < 0.6f) return@run
            val b = blob(lab, skin, w, h, cx, cy, 0.2f * swPx, 0.22f * tl, max(1.5f, 0.022f * swPx), 0f) ?: return@run
            val lat = abs((b.first - sx) * uy - (b.second - sy) * ux)
            if (lat <= 0.12f * swPx && b.third >= MIN_BLOB) out[NAVEL] = Pt(b.first / k, b.second / k)
        }
        // pezones: los dos o ninguno
        val nips = listOf(-1f, 1f).map { sg ->
            val cx = sx + 0.3f * tx + sg * 0.27f * swPx * nx; val cy = sy + 0.3f * ty + sg * 0.27f * swPx * ny
            if (skinFrac(cx, cy, 0.15f * swPx, 0.16f * tl) < 0.6f) null
            else blob(lab, skin, w, h, cx, cy, 0.15f * swPx, 0.16f * tl, r, 0.7f)
        }
        val a = nips[0]; val b = nips[1]
        if (a != null && b != null && abs(a.second - b.second) < 0.1f * tl && abs(a.first - b.first) in 0.3f * swPx..0.8f * swPx &&
            min(a.third, b.third) >= MIN_BLOB) {
            val (left, right) = if (a.first <= b.first) a to b else b to a
            out[NIPPLE_A] = Pt(left.first / k, left.second / k); out[NIPPLE_B] = Pt(right.first / k, right.second / k)
        }
        return out
    }

    /**
     * La mancha redonda más marcada en la caja ([cx], [cy]) ± ([hw], [hh]) px: más oscura que la piel en las ocho
     * direcciones a 1,9·[r] (el mínimo de las ocho), más [red] × su exceso de rojo; con una ligera preferencia por
     * el centro esperado. (x, y, contraste) o null.
     */
    private fun blob(lab: LabImage, skin: BooleanArray, w: Int, h: Int, cx: Float, cy: Float, hw: Float, hh: Float, r: Float, red: Float): Triple<Float, Float, Float>? {
        val sg = max(0.8f, 0.6f * r)
        val lb = SkinLesions.blurNorm(lab.l, skin, w, h, sg)
        val ab = if (red > 0f) SkinLesions.blurNorm(lab.a, skin, w, h, sg) else null
        fun at(a: FloatArray, x: Float, y: Float): Float {
            val xi = x.toInt().coerceIn(0, w - 1); val yi = y.toInt().coerceIn(0, h - 1); return a[yi * w + xi]
        }
        val dirs = (0 until 8).map { val t = it * Math.PI / 4; (1.9f * r * cos(t)).toFloat() to (1.9f * r * sin(t)).toFloat() }
        var best: Triple<Float, Float, Float>? = null; var bestS = -Float.MAX_VALUE
        for (y in max(0, (cy - hh).toInt())..min(h - 1, (cy + hh).toInt())) for (x in max(0, (cx - hw).toInt())..min(w - 1, (cx + hw).toInt())) {
            if (!skin[y * w + x]) continue
            val c0 = lb[y * w + x]
            var mn = Float.MAX_VALUE; var asum = 0f
            for ((dx, dy) in dirs) { mn = min(mn, at(lb, x + dx, y + dy)); if (ab != null) asum += at(ab, x + dx, y + dy) }
            val sc = mn - c0 + (if (ab != null) red * (ab[y * w + x] - asum / 8f) else 0f)
            val pen = sc - 3f * hypot(x - cx, y - cy) / (2 * hw)
            if (pen > bestS) { bestS = pen; best = Triple(x.toFloat(), y.toFloat(), sc) }
        }
        return best
    }
}
