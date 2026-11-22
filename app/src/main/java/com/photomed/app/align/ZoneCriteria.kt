package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trn

/**
 * Criterios clínicos de cada zona del cuerpo (lo que debe verse y cómo), además del encuadre: la toma
 * no se dispara hasta cumplirlos. Siguen las guías dibujadas de la app y la fotografía clínica
 * estándar (zona descubierta, completa, en la postura de la guía):
 *
 * - **Manos (dorso y palma)**: las dos manos, completas (los 21 puntos de cada una dentro del cuadro),
 *   los cinco dedos extendidos, los dedos hacia abajo como en la guía y la cara pedida (dorso o
 *   palma) hacia la cámara.
 * - **Pies (dorso y planta)**: los dos pies completos: la piel de cada pie no toca los bordes del
 *   cuadro (salvo arriba, por donde siguen las piernas).
 * - **Abdomen, tórax, espalda, glúteos, muslos, brazo, cuello**: la zona **descubierta** (piel del
 *   cuerpo, no ropa, en su región) y la postura de la guía: hombros y caderas nivelados, brazos
 *   separados del tronco (frontal y espalda), de lado en las vistas laterales, de espaldas en espalda
 *   y glúteos, piernas completas con las rodillas extendidas y los pies algo separados, y el brazo
 *   completo con el codo extendido y separado del cuerpo 10–30° (como la guía, ≈ 20°).
 *
 * Datos ([Scene]): puntos del cuerpo (ML Kit Pose / BlazePose, en unidades del ancho), manos
 * (MediaPipe Hand Landmarker, 21 puntos) y clases por píxel (segmentación multiclase: piel, ropa…).
 * Si falta un dato que la zona necesita, se pide (no se acepta por defecto).
 */
object ZoneCriteria {
    /** Mano: 21 puntos (unidades del ancho) y la etiqueta de MediaPipe («Left» / «Right»). */
    class Hand(val points: List<Pt>, val label: String)

    /** Clases por píxel (como [FaceLabels]) sobre el cuadro completo. */
    class Classes(val width: Int, val height: Int, val data: ByteArray) {
        fun at(x: Int, y: Int): Int = if (x in 0 until width && y in 0 until height) data[y * width + x].toInt() else FaceLabels.BG
        companion object { fun of(l: FaceLabels) = Classes(l.width, l.height, l.data) }
    }

    /** Lo que se ve en el cuadro; [aspect] = alto / ancho (3:4 → 4/3). */
    class Scene(val pose: Map<String, Pt>, val hands: List<Hand>?, val classes: Classes?, val aspect: Float = 4f / 3f)

    /** Datos que necesita cada zona (para calcular solo lo necesario en cada cuadro). */
    fun needsHands(zone: Zone) = zone.guide == GuideType.HANDS
    fun needsClasses(zone: Zone) = zone.guide in SKIN_ZONES || zone.guide == GuideType.FEET
    fun applies(zone: Zone) = needsHands(zone) || needsClasses(zone) || zone.guide in POSE_RULES

    private val SKIN_ZONES = setOf(GuideType.TORSO_FRONT, GuideType.TORSO_SIDE, GuideType.BREASTS, GuideType.BACK,
        GuideType.GLUTES, GuideType.LEGS, GuideType.ARM, GuideType.NECK)
    private val POSE_RULES = SKIN_ZONES

    // ---- umbrales (documentados en docs/CAPTURA_AUTOMATICA.md) ----
    const val MIN_SKIN = 0.70f
    const val MAX_CLOTHES = 0.15f
    const val LEVEL_DEG = 5f
    const val FINGER_PIP_DEG = 155f
    const val FINGER_DIP_DEG = 150f
    const val THUMB_DEG = 140f
    const val FINGERS_DOWN_DEG = 35f
    const val HAND_MARGIN = 0.02f
    const val FEET_MARGIN = 0.015f
    const val KNEE_DEG = 165f
    const val ELBOW_DEG = 165f
    const val ARM_ABD_MIN = 10f
    const val ARM_ABD_MAX = 30f

    /** Indicaciones que impiden el disparo (vacía: la zona cumple). */
    fun hints(zone: Zone, s: Scene): List<String> = when (zone.guide) {
        GuideType.HANDS -> hands(zone, s)
        GuideType.FEET -> feet(s)
        in SKIN_ZONES -> body(zone, s)
        else -> emptyList()
    }

    // ---------------- manos ----------------

    private val FINGER_NAMES = listOf(tr("pulgar"), "índice", "medio", "anular", "meñique")

    /** ¿La palma mira a la cámara? (ver la documentación de la etiqueta de MediaPipe: vale con y sin espejo). */
    fun palmFacing(h: Hand): Boolean {
        val w = h.points[0]; val a = h.points[5] - w; val b = h.points[17] - w
        val z = a.x * b.y - a.y * b.x
        return (h.label == "Left" && z < 0) || (h.label == "Right" && z > 0)
    }

    /** Ángulo (°) en [b] entre [a] y [c]: 180 = recto. */
    fun angle(a: Pt, b: Pt, c: Pt): Float {
        val ux = a.x - b.x; val uy = a.y - b.y; val vx = c.x - b.x; val vy = c.y - b.y
        val n = hypot(ux, uy) * hypot(vx, vy)
        if (n < 1e-9f) return 0f
        return Math.toDegrees(acos(((ux * vx + uy * vy) / n).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    /** Dedos doblados de una mano (nombres). */
    fun bentFingers(h: Hand): List<String> {
        val p = h.points; val out = ArrayList<String>()
        if (angle(p[2], p[3], p[4]) < THUMB_DEG || angle(p[1], p[2], p[3]) < THUMB_DEG) out += FINGER_NAMES[0]
        for (f in 1..4) {
            val m = 1 + 4 * f   // MCP 5, 9, 13, 17
            if (angle(p[m], p[m + 1], p[m + 2]) < FINGER_PIP_DEG || angle(p[m + 1], p[m + 2], p[m + 3]) < FINGER_DIP_DEG) out += FINGER_NAMES[f]
        }
        return out
    }

    private fun hands(zone: Zone, s: Scene): List<String> {
        val hs = s.hands ?: return listOf(HINT_HANDS_SEARCH)
        if (hs.size < 2) return listOf(HINT_BOTH_HANDS)
        val out = ArrayList<String>()
        // de izquierda a derecha de la imagen
        hs.sortedBy { it.points[0].x }.take(2).forEachIndexed { i, h ->
            val side = if (i == 0) tr("izquierda") else tr("derecha")
            if (h.points.any { it.x < HAND_MARGIN || it.x > 1f - HAND_MARGIN || it.y < HAND_MARGIN * s.aspect || it.y > s.aspect * (1f - HAND_MARGIN) })
                out += tr("Mano de la %1\$s: entera dentro del cuadro", side)
            val bent = bentFingers(h)
            if (bent.isNotEmpty()) out += tr("Mano de la %1\$s: extienda los cinco dedos (%2\$s)", side, bent.joinToString(", ") { tr(it) })
            // dedos hacia abajo (muñeca arriba), como la guía
            val d = h.points[9] - h.points[0]
            val fromDown = abs(Math.toDegrees(atan2(d.x.toDouble(), d.y.toDouble()))).toFloat()
            if (d.y <= 0f || fromDown > FINGERS_DOWN_DEG) out += tr("Mano de la %1\$s: dedos hacia abajo, como en la guía", side)
            val wantPalm = zone == Zone.HANDS_PALMAR
            if (palmFacing(h) != wantPalm) out += if (wantPalm) tr("Mano de la %1\$s: muestre la palma", side) else tr("Mano de la %1\$s: muestre el dorso", side)
        }
        return out
    }

    // ---------------- pies ----------------

    private fun isSkin(c: Int) = c == FaceLabels.BODY_SKIN || c == FaceLabels.FACE_SKIN

    /** Manchas de piel (componentes conexos) con su caja; descarta las pequeñas (< 2 % del cuadro). */
    data class Blob(val area: Int, val x0: Int, val y0: Int, val x1: Int, val y1: Int)

    fun skinBlobs(c: Classes, minFrac: Float = 0.02f): List<Blob> {
        val w = c.width; val h = c.height; val seen = BooleanArray(w * h); val out = ArrayList<Blob>()
        val stack = IntArray(w * h)
        for (i in 0 until w * h) {
            if (seen[i] || !isSkin(c.data[i].toInt())) continue
            var sp = 0; stack[sp++] = i; seen[i] = true
            var n = 0; var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
            while (sp > 0) {
                val k = stack[--sp]; n++
                val x = k % w; val y = k / w
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val j = yy * w + xx
                    if (!seen[j] && isSkin(c.data[j].toInt())) { seen[j] = true; stack[sp++] = j }
                }
            }
            if (n >= minFrac * w * h) out += Blob(n, x0, y0, x1, y1)
        }
        return out
    }

    private fun feet(s: Scene): List<String> {
        val c = s.classes ?: return listOf(HINT_ANALYZING)
        val blobs = skinBlobs(c).sortedByDescending { it.area }
        if (blobs.isEmpty()) return listOf(tr("Encuadre los dos pies descalzos"))
        val mx = (FEET_MARGIN * c.width).toInt().coerceAtLeast(1); val my = (FEET_MARGIN * c.height).toInt().coerceAtLeast(1)
        val out = ArrayList<String>()
        // dos pies: dos manchas o una muy ancha (pies juntos)
        // un mismo pie puede quedar partido (tira de una sandalia): se agrupan las manchas que se solapan en x
        val groups = ArrayList<Blob>()
        for (b in blobs) {
            val i = groups.indexOfFirst { g -> minOf(g.x1, b.x1) - maxOf(g.x0, b.x0) > 0.5f * minOf(g.x1 - g.x0, b.x1 - b.x0) }
            if (i < 0) groups += b else groups[i] = groups[i].let { g -> Blob(g.area + b.area, minOf(g.x0, b.x0), minOf(g.y0, b.y0), maxOf(g.x1, b.x1), maxOf(g.y1, b.y1)) }
        }
        val feet = groups.filter { it.y1 - it.y0 >= 0.25f * c.height }.sortedByDescending { it.area }
        val two = feet.size >= 2 && feet[1].area > feet[0].area * 0.35f &&
            kotlin.math.abs((feet[0].x0 + feet[0].x1) - (feet[1].x0 + feet[1].x1)) / 2f >= 0.2f * c.width
        if (!two) out += tr("Muestre los dos pies, algo separados")
        (if (feet.isNotEmpty()) feet else blobs).take(2).forEach { b ->
            if (b.x0 < mx || b.x1 > c.width - 1 - mx || b.y1 > c.height - 1 - my) { out += tr("Pies completos: los dedos y los bordes dentro del cuadro"); return@forEach }
        }
        return out.distinct()
    }

    // ---------------- cuerpo ----------------

    private fun has(p: Map<String, Pt>, vararg k: String) = k.all { it in p }

    /** Fracción de piel y de ropa en un rectángulo (unidades del ancho; y hasta [Scene.aspect]). */
    fun skinClothes(c: Classes, x0: Float, y0: Float, x1: Float, y1: Float): Pair<Float, Float> {
        // puntos en unidades del ancho: la misma escala (px por unidad) en x y en y
        val k = c.width.toFloat()
        val a = (x0 * k).toInt().coerceIn(0, c.width - 1); val b = (x1 * k).toInt().coerceIn(0, c.width - 1)
        val t = (y0 * k).toInt().coerceIn(0, c.height - 1); val u = (y1 * k).toInt().coerceIn(0, c.height - 1)
        var skin = 0; var clothes = 0; var n = 0
        for (y in t..u) for (x in a..b) { val q = c.at(x, y); n++; if (isSkin(q)) skin++ else if (q == FaceLabels.CLOTHES) clothes++ }
        return if (n == 0) 0f to 0f else skin / n.toFloat() to clothes / n.toFloat()
    }

    /** Piel y ropa a lo largo de un segmento (brazo, muslo) con [halfW] de ancho a cada lado. */
    fun skinClothesAlong(c: Classes, a: Pt, b: Pt, halfW: Float): Pair<Float, Float> {
        var skin = 0; var clothes = 0; var n = 0
        val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1e-4f)
        val nx = -(b.y - a.y) / len; val ny = (b.x - a.x) / len
        for (i in 0..20) for (j in -3..3) {
            val t = i / 20f; val o = halfW * j / 3f
            val x = a.x + (b.x - a.x) * t + nx * o; val y = a.y + (b.y - a.y) * t + ny * o
            val k = c.at((x * c.width).toInt(), (y * c.width).toInt()); n++
            if (isSkin(k)) skin++ else if (k == FaceLabels.CLOTHES) clothes++
        }
        return skin / n.toFloat() to clothes / n.toFloat()
    }

    /**
     * ¿Se ven los dos costados a la altura [yFrac] del cuadro? En los bordes izquierdo y derecho (3 %
     * del ancho) hay fondo, no piel: la zona está entera y no recortada por los lados.
     */
    fun sidesClear(c: Classes, yFrac: Float): Boolean {
        val y0 = ((yFrac - 0.03f) * c.height).toInt().coerceIn(0, c.height - 1); val y1 = ((yFrac + 0.03f) * c.height).toInt().coerceIn(0, c.height - 1)
        val bw = (0.03f * c.width).toInt().coerceAtLeast(1)
        fun bg(xs: IntRange): Boolean { var n = 0; var b = 0; for (y in y0..y1) for (x in xs) { n++; if (!isSkin(c.at(x, y))) b++ }; return b >= 0.7f * n }
        return bg(0 until bw) && bg(c.width - bw until c.width)
    }

    private fun tilt(a: Pt, b: Pt): Float {
        val d = abs(Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()))).toFloat()
        return if (d > 90f) 180f - d else d
    }

    private fun uncovered(c: Classes?, name: String, f: Pair<Float, Float>?): String? = when {
        c == null -> HINT_ANALYZING
        f == null -> null
        f.first < MIN_SKIN || f.second > MAX_CLOTHES -> tr("Descubra %1\$s (se ve ropa o no se ve la piel)", tr(name))
        else -> null
    }

    private fun body(zone: Zone, s: Scene): List<String> {
        val p = s.pose; val L = Landmarks; val c = s.classes
        val out = ArrayList<String>()
        when (zone.guide) {
            GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK -> {
                // de espaldas el detector de cuerpo no suele ver a la persona (la localiza por la cara):
                // la espalda descubierta se comprueba en la región de la guía
                if (zone.guide == GuideType.BACK && !has(p, L.SHOULDER_L, L.SHOULDER_R, L.HIP_L, L.HIP_R)) {
                    if (L.NOSE in p) out += tr("De espaldas a la cámara")
                    if (c != null && !(sidesClear(c, 0.5f) && sidesClear(c, 0.7f))) out += tr("Encuadre la espalda entera: hombros, cintura y los dos costados")
                    uncovered(c, trn("la espalda"), c?.let { skinClothes(it, 0.30f, 0.25f * s.aspect, 0.70f, 0.62f * s.aspect) })?.let { out += it }
                    return out
                }
                if (!has(p, L.SHOULDER_L, L.SHOULDER_R, L.HIP_L, L.HIP_R)) return listOf(tr("Encuadre el tronco: hombros y caderas"))
                val sl = p[L.SHOULDER_L]!!; val sr = p[L.SHOULDER_R]!!; val hl = p[L.HIP_L]!!; val hr = p[L.HIP_R]!!
                if (tilt(sl, sr) > LEVEL_DEG) out += tr("Hombros nivelados")
                if (tilt(hl, hr) > LEVEL_DEG) out += tr("Caderas niveladas")
                val left = minOf(hl.x, hr.x); val right = maxOf(hl.x, hr.x)
                val sy = (sl.y + sr.y) / 2; val hy = (hl.y + hr.y) / 2
                // brazos separados: las muñecas fuera del tronco (a la altura del abdomen)
                listOfNotNull(p[L.WRIST_L], p[L.WRIST_R]).forEach { w ->
                    if (w.x > left && w.x < right && w.y > sy && w.y < hy + 0.05f) out += tr("Separe los brazos del cuerpo")
                }
                if (zone.guide == GuideType.BACK) {
                    if (L.NOSE in p) out += tr("De espaldas a la cámara")
                } else if (L.NOSE !in p && !has(p, L.MOUTH_L, L.MOUTH_R)) out += tr("De frente a la cámara")
                val (y0, y1, name) = when (zone.guide) {
                    GuideType.BREASTS -> Triple(sy, sy + (hy - sy) * 0.55f, trn("el tórax"))
                    GuideType.BACK -> Triple(sy, hy, trn("la espalda"))
                    // abdomen: hasta algo por encima de la cadera (la ropa interior en la cadera es correcta)
                    else -> Triple(sy + (hy - sy) * 0.4f, sy + (hy - sy) * 0.9f, trn("el abdomen"))
                }
                val m = (right - left) * 0.15f
                uncovered(c, name, c?.let { skinClothes(it, left + m, y0, right - m, y1) })?.let { out += it }
            }
            GuideType.TORSO_SIDE -> {
                if (!has(p, L.HIP_L, L.HIP_R) || (L.SHOULDER_L !in p && L.SHOULDER_R !in p)) return listOf(tr("Encuadre el tronco de lado: hombro y cadera"))
                val sh = listOfNotNull(p[L.SHOULDER_L], p[L.SHOULDER_R]); val hl = p[L.HIP_L]!!; val hr = p[L.HIP_R]!!
                val sy = sh.map { it.y }.average().toFloat(); val hy = (hl.y + hr.y) / 2
                // de lado: hombros (y caderas) casi superpuestos en la imagen
                val shoulderW = if (sh.size == 2) abs(sh[0].x - sh[1].x) else 0f
                if (shoulderW > 0.35f * abs(hy - sy) || abs(hl.x - hr.x) > 0.3f * abs(hy - sy)) out += tr("Póngase de lado a la cámara")
                // brazos fuera del flanco
                val cx = (hl.x + hr.x) / 2; val half = 0.12f
                listOfNotNull(p[L.WRIST_L], p[L.WRIST_R], p[L.ELBOW_L], p[L.ELBOW_R]).forEach { w ->
                    if (abs(w.x - cx) < half && w.y > sy + (hy - sy) * 0.3f && w.y < hy) { out += tr("Suba o cruce los brazos: que no tapen el abdomen"); return@forEach }
                }
                uncovered(c, trn("el abdomen"), c?.let { skinClothes(it, cx - half, sy + (hy - sy) * 0.4f, cx + half, hy) })?.let { out += it }
            }
            GuideType.GLUTES -> {
                if (!has(p, L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R)) {
                    // de espaldas: región de la guía (los glúteos en el centro del cuadro)
                    if (L.NOSE in p) out += tr("De espaldas a la cámara")
                    if (c != null && !sidesClear(c, 0.5f)) out += tr("Encuadre los glúteos enteros: las dos caderas")
                    uncovered(c, trn("los glúteos"), c?.let { skinClothes(it, 0.22f, 0.38f * s.aspect, 0.78f, 0.68f * s.aspect) })?.let { out += it }
                    return out
                }
                if (L.NOSE in p) out += tr("De espaldas a la cámara")
                val hl = p[L.HIP_L]!!; val hr = p[L.HIP_R]!!; val kl = p[L.KNEE_L]!!; val kr = p[L.KNEE_R]!!
                if (tilt(hl, hr) > LEVEL_DEG) out += tr("Caderas niveladas")
                val hy = (hl.y + hr.y) / 2; val ky = (kl.y + kr.y) / 2; val w = abs(hl.x - hr.x)
                uncovered(c, trn("los glúteos"), c?.let { skinClothes(it, minOf(hl.x, hr.x) - w * 0.2f, hy - (ky - hy) * 0.1f, maxOf(hl.x, hr.x) + w * 0.2f, hy + (ky - hy) * 0.45f) })?.let { out += it }
            }
            GuideType.LEGS -> {
                if (!has(p, L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R, L.ANKLE_L, L.ANKLE_R)) return listOf(tr("Encuadre las piernas enteras: caderas, rodillas y tobillos"))
                val ok = listOf(L.HIP_L to L.KNEE_L to L.ANKLE_L, L.HIP_R to L.KNEE_R to L.ANKLE_R).all { (hk, a) ->
                    angle(p[hk.first]!!, p[hk.second]!!, p[a]!!) >= KNEE_DEG
                }
                if (!ok) out += tr("Rodillas extendidas")
                val hipW = abs(p[L.HIP_L]!!.x - p[L.HIP_R]!!.x)
                val feetW = abs(p[L.ANKLE_L]!!.x - p[L.ANKLE_R]!!.x)
                if (feetW < hipW * 0.3f) out += tr("Separe un poco los pies")
                if (feetW > hipW * 1.8f) out += tr("Junte un poco los pies")
                val ankleY = maxOf(p[L.ANKLE_L]!!.y, p[L.ANKLE_R]!!.y)
                if (ankleY > s.aspect * 0.97f) out += tr("Los pies dentro del cuadro")
                if (c == null) out += HINT_ANALYZING else {
                    // dentro del muslo (del tercio superior a la rodilla: la ropa interior es correcta), sin tocar el borde
                    val f = listOf(L.HIP_L to L.KNEE_L, L.HIP_R to L.KNEE_R).map { (h, k) ->
                        val a = p[h]!!; val b = p[k]!!
                        skinClothesAlong(c, Pt(a.x + (b.x - a.x) * 0.35f, a.y + (b.y - a.y) * 0.35f), b, hipW * 0.15f)
                    }
                    uncovered(c, trn("los muslos"), f.first().first.coerceAtMost(f.last().first) to maxOf(f.first().second, f.last().second))?.let { out += it }
                }
            }
            GuideType.ARM -> {
                val r = zone == Zone.ARM_R
                val sh = if (r) L.SHOULDER_R else L.SHOULDER_L; val el = if (r) L.ELBOW_R else L.ELBOW_L
                val wr = if (r) L.WRIST_R else L.WRIST_L; val hp = if (r) L.HIP_R else L.HIP_L
                if (!has(p, sh, el, wr)) return listOf(tr("Encuadre el brazo entero: hombro, codo y muñeca"))
                val a = p[sh]!!; val e = p[el]!!; val w = p[wr]!!
                if (angle(a, e, w) < ELBOW_DEG) out += tr("Codo extendido")
                // separación del cuerpo: ángulo entre el tronco (hombro → cadera) y el brazo (hombro → muñeca)
                p[hp]?.let { h ->
                    val abd = angle(h, a, w)
                    if (abd < ARM_ABD_MIN) out += tr("Separe un poco el brazo del cuerpo (≈ 20°)")
                    if (abd > ARM_ABD_MAX) out += tr("Baje el brazo junto al cuerpo (≈ 20°)")
                } ?: run { out += tr("Encuadre el brazo con la cadera de ese lado") }
                val hw = hypot(e.x - a.x, e.y - a.y) * 0.18f
                uncovered(c, trn("el brazo"), c?.let { skinClothesAlong(it, a, w, hw) })?.let { out += it }
            }
            GuideType.NECK -> {
                if (!has(p, L.SHOULDER_L, L.SHOULDER_R)) return listOf(tr("Encuadre cuello y hombros"))
                val sl = p[L.SHOULDER_L]!!; val sr = p[L.SHOULDER_R]!!
                if (tilt(sl, sr) > LEVEL_DEG) out += tr("Hombros nivelados")
                val chin = listOfNotNull(p[L.MOUTH_L], p[L.MOUTH_R]).takeIf { it.isNotEmpty() }?.let { m -> m.map { it.y }.average().toFloat() }
                if (chin == null) out += tr("De frente: que se vea la cara") else {
                    val cx = (sl.x + sr.x) / 2; val half = abs(sl.x - sr.x) * 0.18f; val sy = (sl.y + sr.y) / 2
                    uncovered(c, trn("el cuello y el escote"), c?.let { skinClothes(it, cx - half, chin + (sy - chin) * 0.35f, cx + half, sy + (sy - chin) * 0.3f) })?.let { out += it }
                }
            }
            else -> {}
        }
        return out.distinct()
    }

    val HINT_ANALYZING get() = tr("Comprobando la zona…")
    val HINT_HANDS_SEARCH get() = tr("Buscando las manos…")
    val HINT_BOTH_HANDS get() = tr("Muestre las dos manos enteras")

    private operator fun Pt.minus(o: Pt) = Pt(x - o.x, y - o.y)
    @Suppress("unused") private fun len(p: Pt) = sqrt(p.x * p.x + p.y * p.y)
}
