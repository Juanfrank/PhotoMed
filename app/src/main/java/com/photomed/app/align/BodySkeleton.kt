package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import com.photomed.app.i18n.trn
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Esqueleto del cuerpo al estilo del de las manos (cadenas de articulaciones con un punto en cada una y
 * «falanges» transversales de borde a borde de la piel), con medidas que no dependen de la distancia de la
 * cámara (grados y proporciones):
 * - **Brazos y piernas**: hombro–codo–muñeca y cadera–rodilla–tobillo–talón–dedos; en cada segmento, el ancho
 *   de la piel al 25, 50 y 75 % de su largo. Ángulo de codo y de rodilla; grosor relativo de cada segmento.
 * - **Tronco (abdomen, tórax, espalda, glúteos)**: línea de hombros, de caderas y columna (punto medio de los
 *   hombros → de las caderas) y el ancho del tronco a seis alturas. Inclinación de hombros, de caderas y del
 *   tronco; cintura (la falange más estrecha) respecto de los hombros y de las caderas.
 * - **Cuello**: nariz → base del cuello (punto medio de los hombros), hombros y el ancho del cuello a tres
 *   alturas. Inclinación del cuello.
 * - **Pies**: eje del pie (centro del antepié → punta), rayos del centro a cada dedo y filas de ancho. Largo
 *   del antepié respecto de su ancho.
 *
 * Usa los puntos de la pose y los bordes de la piel que ya calcula [BodyPoints] (los mismos de la alineación).
 * Coordenadas en unidades del ancho de la foto.
 */
object BodySkeleton {
    private val L = Landmarks
    private val ARMS = listOf(Triple("d", L.SHOULDER_R, Pair(L.ELBOW_R, L.WRIST_R)), Triple("i", L.SHOULDER_L, Pair(L.ELBOW_L, L.WRIST_L)))
    private val LEGS = listOf(Triple("d", L.HIP_R, Pair(L.KNEE_R, L.ANKLE_R)), Triple("i", L.HIP_L, Pair(L.KNEE_L, L.ANKLE_L)))
    private val TS = listOf(25, 50, 75)
    private val TORSO_TS = listOf(10, 25, 40, 55, 70, 85)

    private fun d(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)

    /** Ángulo en [b] (grados, 0–180) entre b→a y b→c. */
    fun angle(a: Pt, b: Pt, c: Pt): Float {
        val ux = a.x - b.x; val uy = a.y - b.y; val vx = c.x - b.x; val vy = c.y - b.y
        val n = hypot(ux, uy) * hypot(vx, vy); if (n < 1e-9f) return Float.NaN
        return Math.toDegrees(acos(((ux * vx + uy * vy) / n).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    /** Inclinación de la recta a–b respecto de la horizontal (grados, −90…90; + = el punto derecho de la imagen más bajo). */
    fun tilt(a: Pt, b: Pt): Float {
        val (l, r) = if (a.x <= b.x) a to b else b to a
        return Math.toDegrees(atan2((r.y - l.y).toDouble(), (r.x - l.x).toDouble())).toFloat()
    }

    /** Inclinación de la recta arriba→abajo respecto de la vertical (grados; + = la parte de abajo hacia la derecha). */
    fun lean(top: Pt, bottom: Pt): Float = Math.toDegrees(atan2((bottom.x - top.x).toDouble(), (bottom.y - top.y).toDouble())).toFloat()

    private fun dot(layer: AnnotLayer, c: Pt, r: Float) =
        AnnotLine(layer, (0 until 10).map { i -> val a = i * Math.PI * 2 / 10; Pt(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat()) },
            closed = true, filled = true)

    private fun metrics(name: String, f: List<Pair<String, Float>>) =
        f.filter { it.second.isFinite() }.takeIf { it.isNotEmpty() }?.let { LineMetrics(name, scale = "foto", features = it) }

    /**
     * [pts]: los puntos de [BodyPoints.of] para esta escena (bordes de la piel y de los pies). [r]: radio de los
     * puntos de las articulaciones (unidades del ancho).
     */
    fun lines(zone: Zone, s: ZoneCriteria.Scene, pts: Map<String, Pt>, r: Float = 0.008f): List<AnnotLine> {
        val out = ArrayList<AnnotLine>()
        val p = s.pose
        fun joint(q: Pt?) { if (q != null) out += dot(AnnotLayer.BODY_AXES, q, r * 1.3f) }
        /** Falanges transversales del segmento [seg]: de un borde al otro, con un punto en cada borde. */
        fun ribsAt(seg: String, ts: List<Int>, edges: Map<String, Pt> = pts): Map<Int, Float> {
            val widths = LinkedHashMap<Int, Float>()
            for (t in ts) {
                val a = edges[BodyPoints.edge(seg, t, 1)]; val b = edges[BodyPoints.edge(seg, t, 2)]
                if (a != null && b != null) {
                    out += AnnotLine(AnnotLayer.BODY_POINTS, listOf(a, b), closed = false)
                    widths[t] = d(a, b)
                }
            }
            return widths
        }
        fun ribs(seg: String, ts: List<Int>): List<Float> = ribsAt(seg, ts).values.toList()
        val g = zone.guide
        // miembros
        val limbs = when (g) {
            GuideType.ARM, GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK, GuideType.TORSO_SIDE -> ARMS.map { it to listOf("brazo_", "antebrazo_") }
            GuideType.LEGS, GuideType.GLUTES -> LEGS.map { it to listOf("muslo_", "pierna_") }
            else -> emptyList()
        }
        val isArm = limbs.isNotEmpty() && g != GuideType.LEGS && g != GuideType.GLUTES
        for ((limb, segNames) in limbs) {
            val (side, rootK, rest) = limb
            val root = p[rootK]; val midJ = p[rest.first]; val end = p[rest.second]
            val chain = listOfNotNull(root, midJ, end) + if (!isArm) listOfNotNull(p[if (side == "d") L.HEEL_R else L.HEEL_L], p[if (side == "d") L.FOOT_INDEX_R else L.FOOT_INDEX_L]) else emptyList()
            if (chain.size < 2) continue
            val w1 = ribs(segNames[0] + side, TS); val w2 = ribs(segNames[1] + side, TS)
            val f = ArrayList<Pair<String, Float>>()
            if (root != null && midJ != null && end != null) f += (if (isArm) trn("Ángulo del codo, °") else trn("Ángulo de la rodilla, °")) to angle(root, midJ, end)
            if (root != null && midJ != null && w1.isNotEmpty()) f += (if (isArm) trn("Grosor del brazo / su largo, %") else trn("Grosor del muslo / su largo, %")) to w1.average().toFloat() / d(root, midJ) * 100
            if (midJ != null && end != null && w2.isNotEmpty()) f += (if (isArm) trn("Grosor del antebrazo / su largo, %") else trn("Grosor de la pierna / su largo, %")) to w2.average().toFloat() / d(midJ, end) * 100
            val name = when { isArm && side == "d" -> trn("Brazo derecho"); isArm -> trn("Brazo izquierdo"); side == "d" -> trn("Pierna derecha"); else -> trn("Pierna izquierda") }
            out += AnnotLine(AnnotLayer.BODY_AXES, chain, closed = false, metrics = metrics(name, f))
            chain.forEach(::joint)
        }
        // primer plano del brazo sin pose: eje de la piel (componente principal de la mancha de piel mayor)
        if (g == GuideType.ARM && out.none { it.layer == AnnotLayer.BODY_AXES && it.metrics != null }) s.classes?.let { c ->
            skinAxis(c)?.let { (a, b) ->
                val edges = BodyPoints.segmentEdges(c, "brazo_eje", a, b, maxReach = d(a, b) * 0.6f, ts = TS)
                val w = ribsAt("brazo_eje", TS, edges).values
                out += AnnotLine(AnnotLayer.BODY_AXES, listOf(a, b), false, metrics = metrics(trn("Brazo (eje de la piel)"),
                    listOfNotNull(w.takeIf { it.isNotEmpty() }?.let { trn("Grosor del brazo / largo visible, %") to it.average().toFloat() / d(a, b) * 100 },
                        trn("Eje del brazo respecto de la vertical, °") to lean(a, b))))
                joint(a); joint(b)
            }
        }
        // tronco
        if (g in setOf(GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK, GuideType.TORSO_SIDE, GuideType.GLUTES)) {
            val sl = p[L.SHOULDER_L]; val sr = p[L.SHOULDER_R]; val hl = p[L.HIP_L]; val hr = p[L.HIP_R]
            val f = ArrayList<Pair<String, Float>>()
            // de perfil los dos hombros (y las dos caderas) se superponen: su inclinación no tiene sentido
            val frontal = g != GuideType.TORSO_SIDE
            if (sl != null && sr != null) { out += AnnotLine(AnnotLayer.BODY_AXES, listOf(sl, sr), false); if (frontal) f += trn("Inclinación de los hombros, °") to tilt(sl, sr); joint(sl); joint(sr) }
            if (hl != null && hr != null) { out += AnnotLine(AnnotLayer.BODY_AXES, listOf(hl, hr), false); if (frontal) f += trn("Inclinación de las caderas, °") to tilt(hl, hr); joint(hl); joint(hr) }
            val top = if (sl != null && sr != null) mid(sl, sr) else null
            val bottom = if (hl != null && hr != null) mid(hl, hr) else null
            val wAt = ribsAt("tronco", TORSO_TS)
            // tronco del revés (hombros por debajo de las caderas: foto girada o persona tumbada): sin medidas
            if (top != null && bottom != null && top.y >= bottom.y) f.clear()
            if (top != null && bottom != null && top.y < bottom.y) {
                f += trn("Inclinación del tronco, °") to lean(top, bottom)
                // anchos de piel (no de las articulaciones: la cadera de la pose es la articulación, no el borde)
                val waist = wAt.filterKeys { it in 25..70 }.values.minOrNull()
                val chest = wAt[10] ?: wAt[25]
                val hips = wAt.filterKeys { it >= 70 }.values.maxOrNull()
                if (waist != null && chest != null) f += trn("Cintura / tórax (axilas), %") to waist / chest * 100
                if (waist != null && hips != null) f += trn("Cintura / caderas (piel), %") to waist / hips * 100
                out += AnnotLine(AnnotLayer.BODY_AXES, listOf(top, bottom), false, metrics = metrics(trn("Tronco"), f))
                joint(top); joint(bottom)
                // referencias anatómicas (TorsoMarks): pezones, ombligo y axilas, medidos respecto de hombros y caderas
                val len = hypot(bottom.x - top.x, bottom.y - top.y)
                val sw = if (sl != null && sr != null) hypot(sl.x - sr.x, sl.y - sr.y) else 0f
                /** Posición a lo largo del tronco (0 = hombros, 100 = caderas) y desvío lateral (+ = derecha de la imagen). */
                fun along(q: Pt) = ((q.x - top.x) * (bottom.x - top.x) + (q.y - top.y) * (bottom.y - top.y)) / (len * len) * 100f
                fun lateral(q: Pt) = ((q.x - top.x) * (bottom.y - top.y) - (q.y - top.y) * (bottom.x - top.x)) / len
                val na = pts[TorsoMarks.NIPPLE_A]; val nb = pts[TorsoMarks.NIPPLE_B]
                if (na != null && nb != null && sw > 0f) {
                    out += AnnotLine(AnnotLayer.BODY_AXES, listOf(na, nb), false, metrics = metrics(trn("Pezones"), listOf(
                        trn("Inclinación de los pezones, °") to tilt(na, nb),
                        trn("Distancia entre pezones / ancho de hombros, %") to hypot(na.x - nb.x, na.y - nb.y) / sw * 100,
                        trn("Altura de los pezones (hombros → caderas), %") to (along(na) + along(nb)) / 2,
                        trn("Diferencia de altura de los pezones, % del tronco") to abs(along(na) - along(nb)))))
                }
                pts[TorsoMarks.NAVEL]?.let { nv ->
                    if (sw > 0f) out += AnnotLine(AnnotLayer.BODY_AXES, listOf(Pt(top.x + (bottom.x - top.x) * along(nv) / 100f, top.y + (bottom.y - top.y) * along(nv) / 100f), nv),
                        false, metrics = metrics(trn("Ombligo"), listOf(
                            trn("Altura del ombligo (hombros → caderas), %") to along(nv),
                            trn("Desvío del ombligo de la línea media, % del ancho de hombros") to lateral(nv) / sw * 100)))
                }
                val xa = pts[TorsoMarks.AXILLA_A]; val xb = pts[TorsoMarks.AXILLA_B]
                if (xa != null && xb != null && frontal) out += AnnotLine(AnnotLayer.BODY_AXES, listOf(xa, xb), false, metrics = metrics(trn("Axilas"), listOfNotNull(
                    trn("Inclinación de las axilas, °") to tilt(xa, xb),
                    trn("Altura de las axilas (hombros → caderas), %") to (along(xa) + along(xb)) / 2,
                    (if (sw > 0f) trn("Distancia entre axilas / ancho de hombros, %") to hypot(xa.x - xb.x, xa.y - xb.y) / sw * 100 else null))))
            } else if (f.isNotEmpty()) out.lastOrNull { it.layer == AnnotLayer.BODY_AXES && !it.filled }?.let { l ->
                out[out.indexOf(l)] = l.copy(metrics = metrics(trn("Tronco"), f))
            }
        }
        // cuello
        if (g == GuideType.NECK) {
            val sl = p[L.SHOULDER_L]; val sr = p[L.SHOULDER_R]; val nose = p[L.NOSE]
            val chin = listOfNotNull(p[L.MOUTH_L], p[L.MOUTH_R]).takeIf { it.size == 2 }?.let { mid(it[0], it[1]) }
            if (sl != null && sr != null) {
                val base = mid(sl, sr)
                out += AnnotLine(AnnotLayer.BODY_AXES, listOf(sl, sr), false); joint(sl); joint(sr)
                val head = chin ?: nose
                if (head != null) {
                    ribs("cuello", BodyPoints.NECK_TS)
                    val f = listOf(trn("Inclinación del cuello, °") to lean(head, base), trn("Inclinación de los hombros, °") to tilt(sl, sr))
                    out += AnnotLine(AnnotLayer.BODY_AXES, listOfNotNull(nose, chin?.takeIf { nose != null }, base).ifEmpty { listOf(head, base) }, false,
                        metrics = metrics(trn("Cuello"), f))
                    joint(head); joint(base)
                }
            }
        }
        // pies: eje, rayos a cada dedo y filas de ancho
        if (g == GuideType.FEET) for (side in listOf('a', 'b')) {
            fun fp(part: String) = pts[BodyPoints.foot(side, part)]
            val c = fp("centro") ?: continue; val tip = fp("punta") ?: continue
            out += AnnotLine(AnnotLayer.BODY_AXES, listOf(c, tip), false)
            (1..5).mapNotNull { fp("dedo_$it") }.forEach { t -> out += AnnotLine(AnnotLayer.FINGERS, listOf(c, t), false); joint(t) }
            val widths = (1..5).mapNotNull { i -> val a = fp("izq_$i"); val b = fp("der_$i"); if (a != null && b != null) { out += AnnotLine(AnnotLayer.BODY_POINTS, listOf(a, b), false); d(a, b) } else null }
            joint(c); joint(tip)
            val f = listOfNotNull(widths.maxOrNull()?.let { trn("Antepié: largo / ancho, %") to d(c, tip) / it * 100 },
                trn("Eje del pie respecto de la vertical, °") to lean(c, tip))
            out[out.indexOfLast { it.layer == AnnotLayer.BODY_AXES && !it.filled && it.points.size == 2 && it.points[0] == c }] =
                AnnotLine(AnnotLayer.BODY_AXES, listOf(c, tip), false, metrics = metrics(if (side == 'a') trn("Pie (izquierda de la imagen)") else trn("Pie (derecha de la imagen)"), f))
        }
        return out
    }

    /**
     * Eje de la mancha de piel mayor (componente principal) recortado al 5–95 % de su extensión, en unidades del
     * ancho; null sin piel suficiente o sin una dirección dominante (mancha redonda).
     */
    fun skinAxis(c: ZoneCriteria.Classes): Pair<Pt, Pt>? {
        val k = c.width.toFloat()
        var n = 0; var sx = 0.0; var sy = 0.0
        val xs = ArrayList<Int>(); val ys = ArrayList<Int>()
        for (y in 0 until c.height) for (x in 0 until c.width) {
            val v = c.at(x, y); if (v != FaceLabels.BODY_SKIN && v != FaceLabels.FACE_SKIN) continue
            xs += x; ys += y; sx += x; sy += y; n++
        }
        if (n < 0.05f * c.width * c.height) return null
        val mx = sx / n; val my = sy / n
        var cxx = 0.0; var cyy = 0.0; var cxy = 0.0
        for (i in xs.indices) { val dx = xs[i] - mx; val dy = ys[i] - my; cxx += dx * dx; cyy += dy * dy; cxy += dx * dy }
        val tr = cxx + cyy; val det = cxx * cyy - cxy * cxy
        val l1 = tr / 2 + kotlin.math.sqrt((tr * tr / 4 - det).coerceAtLeast(0.0)); val l2 = tr - l1
        if (l2 > 0.6 * l1) return null
        val ang = 0.5 * kotlin.math.atan2(2 * cxy, cxx - cyy)
        val ux = kotlin.math.cos(ang); val uy = kotlin.math.sin(ang)
        val proj = xs.indices.map { (xs[it] - mx) * ux + (ys[it] - my) * uy }.sorted()
        val t0 = proj[(proj.size * 0.05).toInt()]; val t1 = proj[(proj.size * 0.95).toInt().coerceAtMost(proj.size - 1)]
        val a = Pt(((mx + ux * t0 + 0.5) / k).toFloat(), ((my + uy * t0 + 0.5) / k).toFloat())
        val b = Pt(((mx + ux * t1 + 0.5) / k).toFloat(), ((my + uy * t1 + 0.5) / k).toFloat())
        return if (a.y <= b.y) a to b else b to a
    }

    /** Diferencia de un ángulo con 180° (miembro recto = 0). */
    fun flexion(angleDeg: Float) = abs(180f - angleDeg)
}
