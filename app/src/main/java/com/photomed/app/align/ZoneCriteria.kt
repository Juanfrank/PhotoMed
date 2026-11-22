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
    class Classes(val width: Int, val height: Int, val data: ByteArray,
                  /** Color (ARGB) en la misma rejilla, para los dedos del pie (piel refinada y surcos); opcional. */
                  val rgb: IntArray? = null) {
        fun at(x: Int, y: Int): Int = if (x in 0 until width && y in 0 until height) data[y * width + x].toInt() else FaceLabels.BG
        companion object { fun of(l: FaceLabels) = Classes(l.width, l.height, l.data) }
    }

    /** Lo que se ve en el cuadro; [aspect] = alto / ancho (3:4 → 4/3). */
    /**
     * Lo que se ve en el cuadro; [aspect] = alto / ancho (3:4 → 4/3). [single]: modo de una sola
     * extremidad (selector «Derecha / Izquierda» de manos, pies y piernas): se exige una, no las dos.
     */
    class Scene(val pose: Map<String, Pt>, val hands: List<Hand>?, val classes: Classes?, val aspect: Float = 4f / 3f,
                val single: Boolean = false)

    /**
     * Escena girada 90° × [q] en sentido antihorario: la del cuadro pasada a la orientación de la guía
     * cuando la guía se gira (botón «Girar 90°»: se gira en pantalla en sentido horario). Así los
     * criterios («dedos hacia abajo», dedos del pie en el borde distal, hombros nivelados…) se miden
     * respecto a la guía girada. Puntos en unidades del ancho; clases por píxel giradas igual.
     */
    fun Scene.rotatedCcw(q: Int): Scene {
        var sc = this
        repeat(((q % 4) + 4) % 4) {
            val a = sc.aspect
            fun r(p: Pt) = Pt(p.y / a, (1f - p.x) / a)
            val cl = sc.classes?.let { c ->
                val w2 = c.height; val h2 = c.width; val d = ByteArray(w2 * h2)
                for (y2 in 0 until h2) for (x2 in 0 until w2) d[y2 * w2 + x2] = c.data[x2 * c.width + (c.width - 1 - y2)]
                val lm = c.rgb?.let { l -> IntArray(w2 * h2).also { o -> for (y2 in 0 until h2) for (x2 in 0 until w2) o[y2 * w2 + x2] = l[x2 * c.width + (c.width - 1 - y2)] } }
                Classes(w2, h2, d, lm)
            }
            sc = Scene(sc.pose.mapValues { r(it.value) }, sc.hands?.map { h -> Hand(h.points.map(::r), h.label) }, cl, 1f / a, sc.single)
        }
        return sc
    }

    /** Punto de la escena girada ([rotatedCcw] con [q]) devuelto al cuadro original de alto/ancho [aspect]. */
    fun unrotate(p: Pt, q: Int, aspect: Float): Pt {
        val n = ((q % 4) + 4) % 4
        // aspecto de cada paso intermedio: alterna entre el del cuadro y su inverso
        var pt = p
        for (i in n - 1 downTo 0) {
            val a = if (i % 2 == 0) aspect else 1f / aspect   // aspecto ANTES del paso i
            pt = Pt(1f - pt.y * a, pt.x * a)
        }
        return pt
    }

    /** Datos que necesita cada zona (para calcular solo lo necesario en cada cuadro). */
    fun needsHands(zone: Zone) = zone.guide == GuideType.HANDS
    /** En las manos, la piel sirve para que el contorno del esquema siga el borde real de cada mano. */
    fun needsClasses(zone: Zone) = zone.guide in SKIN_ZONES || zone.guide == GuideType.FEET || zone.guide == GuideType.HANDS
    fun applies(zone: Zone) = needsHands(zone) || needsClasses(zone) || zone.guide in POSE_RULES

    private val SKIN_ZONES = setOf(GuideType.TORSO_FRONT, GuideType.TORSO_SIDE, GuideType.BREASTS, GuideType.BACK,
        GuideType.GLUTES, GuideType.LEGS, GuideType.ARM, GuideType.NECK)
    private val POSE_RULES = SKIN_ZONES

    // ---- umbrales (documentados en docs/CAPTURA_AUTOMATICA.md) ----
    const val MIN_SKIN = 0.70f
    const val MAX_CLOTHES = 0.15f
    const val LEVEL_DEG = 8f
    /**
     * Dedos: basta con que se vean sus falanges (no recogidos ni en un puño). El ángulo entre dedos y su
     * longitud son los del paciente: el esquema se ajusta a ellos ([BodySchema]), no es incumplimiento.
     */
    const val FINGER_PIP_DEG = 120f
    const val FINGER_DIP_DEG = 115f
    const val THUMB_DEG = 110f
    /** Longitud mínima de cada dedo (de la base a la punta) respecto de la palma (muñeca → base del medio). */
    const val FINGER_MIN_LEN = 0.45f
    /** Orientación de la mano: dedos hacia abajo como la guía, con margen (el esquema gira con la mano). */
    const val FINGERS_DOWN_DEG = 60f
    const val HAND_MARGIN = 0.02f
    const val FEET_MARGIN = 0.015f
    /** Rodillas y codo casi extendidos (la flexión natural leve no es incumplimiento). */
    const val KNEE_DEG = 150f
    const val ELBOW_DEG = 150f
    /** Brazo separado del cuerpo (la guía ≈ 20°; el esquema sigue el ángulo real dentro de este margen). */
    const val ARM_ABD_MIN = 5f
    const val ARM_ABD_MAX = 45f
    /** Dedos del pie visibles (mínimo por pie: el pequeño puede quedar oculto por el cuarto). */
    /** Fracción máxima del borde distal del pie que puede lindar con ropa o calzado. */
    const val COVERED_TIP = 0.35f
    /** Dedos que deben distinguirse como mínimo en cada pie (curvas del contorno en nube y surcos). */
    const val MIN_TOES = 3

    /** Indicaciones que impiden el disparo (vacía: la zona cumple). */
    fun hints(zone: Zone, s: Scene): List<String> {
        val h = when (zone.guide) {
            GuideType.HANDS -> hands(zone, s)
            GuideType.FEET -> feet(s)
            in SKIN_ZONES -> body(zone, s)
            else -> emptyList()
        }
        return h + listOfNotNull(centerHint(zone, s))
    }

    /** Tolerancia del centrado de la zona: desvío máximo de su centro respecto al del cuadro (fracción de cada lado). */
    const val CENTER_TOL = 0.13f

    /**
     * Caja de la parte del cuerpo de la zona (unidades del ancho; y hasta [Scene.aspect]) y qué ejes
     * deben quedar centrados (x, y); null si no se ha encontrado la parte.
     */
    fun partBox(zone: Zone, s: Scene): Pair<FloatArray, BooleanArray>? {
        val p = s.pose; val L = Landmarks
        fun box(pts: List<Pt>, x: Boolean = true, y: Boolean = true) = pts.takeIf { it.size >= 2 }?.let { q ->
            floatArrayOf(q.minOf { it.x }, q.minOf { it.y }, q.maxOf { it.x }, q.maxOf { it.y }) to booleanArrayOf(x, y)
        }
        fun pts(vararg k: String) = k.mapNotNull { p[it] }
        return when (zone.guide) {
            GuideType.HANDS -> {
                val hs = s.hands ?: return null
                val chosen = if (s.single) hs.maxByOrNull { dist(it.points[0], it.points[9]) }?.let { listOf(it) } ?: return null
                    else hs.sortedBy { it.points[0].x }.take(2)
                box(chosen.flatMap { it.points })
            }
            GuideType.FEET -> {
                val c = s.classes ?: return null
                val fs = feetOf(c).sortedByDescending { it.box.area }.take(if (s.single) 1 else 2)
                if (fs.isEmpty()) return null
                val k = c.width.toFloat()
                // en vertical las piernas salen por arriba: solo se centra en horizontal
                floatArrayOf(fs.minOf { it.box.x0 } / k, fs.minOf { it.box.y0 } / k, (fs.maxOf { it.box.x1 } + 1) / k, (fs.maxOf { it.box.y1 } + 1) / k) to booleanArrayOf(true, false)
            }
            GuideType.ARM -> {
                val arms = listOf(pts(L.SHOULDER_R, L.ELBOW_R, L.WRIST_R), pts(L.SHOULDER_L, L.ELBOW_L, L.WRIST_L)).filter { it.size == 3 }
                val want = if (zone == Zone.ARM_R) 0 else 1
                val arm = listOf(pts(L.SHOULDER_R, L.ELBOW_R, L.WRIST_R), pts(L.SHOULDER_L, L.ELBOW_L, L.WRIST_L))[want].takeIf { it.size == 3 } ?: arms.firstOrNull()
                arm?.let { box(it) }
            }
            GuideType.LEGS -> if (s.single) {
                listOf(pts(L.HIP_L, L.KNEE_L, L.ANKLE_L), pts(L.HIP_R, L.KNEE_R, L.ANKLE_R)).filter { it.size == 3 }
                    .maxByOrNull { dist(it[0], it[2]) }?.let { box(it) }
            } else box(pts(L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R, L.ANKLE_L, L.ANKLE_R))
            GuideType.TORSO_FRONT, GuideType.BREASTS, GuideType.BACK, GuideType.TORSO_SIDE ->
                pts(L.SHOULDER_L, L.SHOULDER_R, L.HIP_L, L.HIP_R).takeIf { it.size >= 3 }?.let { box(it) }
            GuideType.GLUTES -> pts(L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R).takeIf { it.size == 4 }?.let { box(it) }
            GuideType.NECK -> pts(L.SHOULDER_L, L.SHOULDER_R).takeIf { it.size == 2 }?.let { box(it, y = false) }
            else -> null
        }
    }

    /** «Centre la zona»: el centro de la parte del cuerpo lejos del centro del cuadro (en la orientación de la guía). */
    fun centerHint(zone: Zone, s: Scene): String? {
        val (b, axes) = partBox(zone, s) ?: return null
        val cx = (b[0] + b[2]) / 2; val cy = (b[1] + b[3]) / 2
        val offX = axes[0] && abs(cx - 0.5f) > CENTER_TOL
        val offY = axes[1] && abs(cy - s.aspect / 2) > CENTER_TOL * s.aspect
        return if (offX || offY) tr("Centre la zona en el cuadro") else null
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
    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)

    fun angle(a: Pt, b: Pt, c: Pt): Float {
        val ux = a.x - b.x; val uy = a.y - b.y; val vx = c.x - b.x; val vy = c.y - b.y
        val n = hypot(ux, uy) * hypot(vx, vy)
        if (n < 1e-9f) return 0f
        return Math.toDegrees(acos(((ux * vx + uy * vy) / n).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /**
     * Dedos cuyas falanges no se ven (nombres): recogidos o doblados del todo (articulación cerrada) o
     * demasiado cortos para la palma (ocultos o en un puño). El ángulo entre dedos y su longitud dentro
     * de lo anatómico no cuentan: el esquema se ajusta.
     */
    fun bentFingers(h: Hand): List<String> {
        val p = h.points; val out = ArrayList<String>()
        val palm = dist(p[0], p[9]).coerceAtLeast(1e-4f)
        fun len(vararg i: Int) = (1 until i.size).sumOf { dist(p[i[it - 1]], p[i[it]]).toDouble() }.toFloat()
        if (angle(p[2], p[3], p[4]) < THUMB_DEG || angle(p[1], p[2], p[3]) < THUMB_DEG || len(1, 2, 3, 4) < FINGER_MIN_LEN * palm) out += FINGER_NAMES[0]
        for (f in 1..4) {
            val m = 1 + 4 * f   // MCP 5, 9, 13, 17
            if (angle(p[m], p[m + 1], p[m + 2]) < FINGER_PIP_DEG || angle(p[m + 1], p[m + 2], p[m + 3]) < FINGER_DIP_DEG ||
                len(m, m + 1, m + 2, m + 3) < FINGER_MIN_LEN * palm) out += FINGER_NAMES[f]
        }
        return out
    }

    private fun hands(zone: Zone, s: Scene): List<String> {
        val hs = s.hands ?: return listOf(HINT_HANDS_SEARCH)
        if (s.single && hs.isEmpty()) return listOf(tr("Muestre la mano entera"))
        if (!s.single && hs.size < 2) return listOf(HINT_BOTH_HANDS)
        val out = ArrayList<String>()
        // una sola mano: la más grande del cuadro; dos: de izquierda a derecha de la imagen
        val chosen = if (s.single) listOf(hs.maxBy { dist(it.points[0], it.points[9]) }) else hs.sortedBy { it.points[0].x }.take(2)
        chosen.forEachIndexed { i, h ->
            val side = if (s.single) (if (h.points[0].x < 0.5f) tr("izquierda") else tr("derecha")) else if (i == 0) tr("izquierda") else tr("derecha")
            if (h.points.any { it.x < HAND_MARGIN || it.x > 1f - HAND_MARGIN || it.y < HAND_MARGIN * s.aspect || it.y > s.aspect * (1f - HAND_MARGIN) })
                out += tr("Mano de la %1\$s: entera dentro del cuadro", side)
            val bent = bentFingers(h)
            if (bent.isNotEmpty()) out += tr("Mano de la %1\$s: que se vean las falanges de todos los dedos (%2\$s)", side, bent.joinToString(", ") { tr(it) })
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

    fun skinBlobs(c: Classes, minFrac: Float = 0.02f): List<Blob> = labeled(c, minFrac).first

    /** Componentes de piel: cajas de las grandes y etiqueta (índice en la lista, o −1) de cada píxel. */
    private fun labeled(c: Classes, minFrac: Float = 0.02f): Pair<List<Blob>, IntArray> {
        val w = c.width; val h = c.height; val lab = IntArray(w * h) { -2 }; val out = ArrayList<Blob>()
        val stack = IntArray(w * h); val members = ArrayList<Int>()
        for (i in 0 until w * h) {
            if (lab[i] != -2 || !isSkin(c.data[i].toInt())) continue
            var sp = 0; stack[sp++] = i; lab[i] = -3; members.clear()
            var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
            while (sp > 0) {
                val k = stack[--sp]; members += k
                val x = k % w; val y = k / w
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val j = yy * w + xx
                    if (lab[j] == -2 && isSkin(c.data[j].toInt())) { lab[j] = -3; stack[sp++] = j }
                }
            }
            val id = if (members.size >= minFrac * w * h) { out += Blob(members.size, x0, y0, x1, y1); out.size - 1 } else -1
            for (k in members) lab[k] = id
        }
        return out to lab
    }

    /**
     * Un pie: silueta (contorno en unidades del ancho), caja y puntas de los dedos (falanges distales)
     * halladas en el borde distal (abajo, como en la guía) como salientes separados por los surcos
     * entre dedos.
     */
    class Foot(val outline: List<Pt>, val toes: List<Pt>, val box: Blob,
               /** Fracción del borde distal que linda con ropa o calzado (dedos tapados: calcetín, zapato). */
               val coveredTip: Float = 0f,
               /** Dedos distinguidos en el borde distal ([ToeDetector]); null si no se pudo analizar. */
               val toeCount: Int? = null,
               /** Líneas de separación entre dedos (unidades del ancho). */
               val toeLines: List<Pair<Pt, Pt>> = emptyList())

    @Volatile private var feetMemo: Pair<Classes, List<Foot>>? = null

    /** Pies de [c] (se calcula una vez por máscara: los criterios y el esquema la comparten). */
    fun feetOf(c: Classes): List<Foot> {
        feetMemo?.let { (k, v) -> if (k === c) return v }
        return computeFeet(c).also { feetMemo = c to it }
    }

    private fun computeFeet(c: Classes): List<Foot> {
        val (blobs, lab) = labeled(c)
        // un mismo pie puede quedar partido (tira de una sandalia): se agrupan las manchas que se solapan en x
        val groupOf = IntArray(blobs.size); val groups = ArrayList<Blob>()
        blobs.indices.sortedByDescending { blobs[it].area }.forEach { bi ->
            val b = blobs[bi]
            val i = groups.indexOfFirst { g -> minOf(g.x1, b.x1) - maxOf(g.x0, b.x0) > 0.5f * minOf(g.x1 - g.x0, b.x1 - b.x0) }
            if (i < 0) { groups += b; groupOf[bi] = groups.size - 1 }
            else { groups[i] = groups[i].let { g -> Blob(g.area + b.area, minOf(g.x0, b.x0), minOf(g.y0, b.y0), maxOf(g.x1, b.x1), maxOf(g.y1, b.y1)) }; groupOf[bi] = i }
        }
        val w = c.width; val k = 1f / w
        return groups.mapIndexedNotNull { gi, g ->
            if (g.y1 - g.y0 < 0.25f * c.height) return@mapIndexedNotNull null
            fun inG(x: Int, y: Int) = lab[y * w + x].let { it >= 0 && groupOf[it] == gi }
            // borde distal por columna y contorno por filas
            val bottom = IntArray(g.x1 - g.x0 + 1) { -1 }
            val left = ArrayList<Pt>(); val right = ArrayList<Pt>()
            for (y in g.y0..g.y1) {
                var l = -1; var r = -1
                for (x in g.x0..g.x1) if (inG(x, y)) { if (l < 0) l = x; r = x; bottom[x - g.x0] = y }
                if (l >= 0 && (y - g.y0) % 2 == 0) { left += Pt(l * k, y * k); right += Pt(r * k, y * k) }
            }
            // puntas de los dedos: máximos locales del borde distal con un surco a cada lado
            val prom = maxOf(1, (0.006f * c.height).toInt()); val win = maxOf(2, (0.02f * w).toInt())
            val toes = ArrayList<Pt>()
            for (i in bottom.indices) {
                val y = bottom[i]; if (y < 0 || y < g.y1 - 0.14f * c.height) continue
                val lo = (i - win).coerceAtLeast(0); val hi = (i + win).coerceAtMost(bottom.size - 1)
                if ((lo..hi).any { bottom[it] > y }) continue
                val lmin = (lo until i).minOfOrNull { bottom[it] } ?: y; val rmin = (i + 1..hi).minOfOrNull { bottom[it] } ?: y
                if (y - lmin < prom && i - win > 0 || y - rmin < prom && i + win < bottom.size - 1) continue
                if (toes.isNotEmpty() && (g.x0 + i) * k - toes.last().x < 0.025f) continue
                toes += Pt((g.x0 + i) * k, y * k)
            }
            // lo que hay justo después del borde distal (fuera del pie): fondo (dedos al aire) o ropa / calzado
            var cov = 0; var seen = 0
            val reach = maxOf(2, (0.012f * c.height).toInt())
            for (i in bottom.indices) {
                val y = bottom[i]; if (y < 0 || y < g.y1 - 0.14f * c.height) continue
                val x = g.x0 + i
                for (d in 1..reach) {
                    val yy = y + d; if (yy >= c.height) break
                    if (inG(x, yy)) continue
                    val q = c.at(x, yy)
                    seen++; if (q == FaceLabels.CLOTHES || q == FaceLabels.OTHER) cov++
                    break
                }
            }
            // dedos: contorno en nube (una curva por dedo), surcos desde cada valle y, desde el dedo gordo,
            // los surcos sin valle (ver ToeDetector)
            val td = runCatching {
                ToeDetector.detect(c.width, c.height, { x, y -> x in g.x0..g.x1 && y in g.y0..g.y1 && inG(x, y) }, c.rgb)
            }.getOrNull()
            fun u(p: Pair<Float, Float>) = Pt((p.first + 0.5f) * k, (p.second + 0.5f) * k)
            Foot(left + right.reversed(), td?.tips?.map(::u) ?: toes.takeIf { it.size in 3..6 }.orEmpty(), g,
                if (seen == 0) 0f else cov / seen.toFloat(), td?.count, td?.lines?.map { (a, b) -> u(a) to u(b) }.orEmpty())
        }
    }

    private fun feet(s: Scene): List<String> {
        val c = s.classes ?: return listOf(HINT_ANALYZING)
        if (skinBlobs(c).isEmpty()) return listOf(if (s.single) tr("Encuadre el pie descalzo") else tr("Encuadre los dos pies descalzos"))
        val mx = (FEET_MARGIN * c.width).toInt().coerceAtLeast(1); val my = (FEET_MARGIN * c.height).toInt().coerceAtLeast(1)
        val out = ArrayList<String>()
        val feet = feetOf(c).sortedByDescending { it.box.area }
        val two = feet.size >= 2 && feet[1].box.area > feet[0].box.area * 0.35f &&
            abs((feet[0].box.x0 + feet[0].box.x1) - (feet[1].box.x0 + feet[1].box.x1)) / 2f >= 0.2f * c.width
        if (s.single) { if (feet.isEmpty()) out += tr("Encuadre el pie descalzo") }
        else if (!two) out += tr("Muestre los dos pies, algo separados")
        feet.take(if (s.single) 1 else 2).sortedBy { it.box.x0 }.forEachIndexed { i, f ->
            val b = f.box
            if (b.x0 < mx || b.x1 > c.width - 1 - mx || b.y1 > c.height - 1 - my) { out += tr("Pies completos: los dedos y los bordes dentro del cuadro"); return@forEachIndexed }
            // las falanges: los dedos deben verse (su número y su forma son los del paciente)
            val left = if (s.single) (b.x0 + b.x1) < c.width else i == 0
            // los dedos: el extremo distal entero y al aire (no tapado por calcetín, ropa o calzado). Contar
            // cada dedo en la silueta no es fiable con fotos reales (se tocan): no se exige un número
            // y que se distingan los dedos: menos de MIN_TOES curvas en el borde distal es que no se ven
            if (f.coveredTip > COVERED_TIP || (f.toeCount != null && f.toeCount < MIN_TOES))
                out += tr("Pie de la %1\$s: que se vean los dedos", if (left) tr("izquierda") else tr("derecha"))
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
                if (s.single) return singleLeg(s)
                if (!has(p, L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R, L.ANKLE_L, L.ANKLE_R)) return listOf(tr("Encuadre las piernas enteras: caderas, rodillas y tobillos"))
                val ok = listOf(L.HIP_L to L.KNEE_L to L.ANKLE_L, L.HIP_R to L.KNEE_R to L.ANKLE_R).all { (hk, a) ->
                    angle(p[hk.first]!!, p[hk.second]!!, p[a]!!) >= KNEE_DEG
                }
                if (!ok) out += tr("Rodillas extendidas")
                val hipW = abs(p[L.HIP_L]!!.x - p[L.HIP_R]!!.x)
                val feetW = abs(p[L.ANKLE_L]!!.x - p[L.ANKLE_R]!!.x)
                if (feetW < hipW * 0.15f) out += tr("Separe un poco los pies")
                if (feetW > hipW * 2.5f) out += tr("Junte un poco los pies")
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
                // el lado según el detector se invierte de espaldas (vista posterior) y en primeros planos
                // solo se ve un brazo: se evalúa el brazo pedido si está entero y, si no, el que se vea entero
                val r0 = zone == Zone.ARM_R
                val whole = { right: Boolean -> if (right) has(p, L.SHOULDER_R, L.ELBOW_R, L.WRIST_R) else has(p, L.SHOULDER_L, L.ELBOW_L, L.WRIST_L) }
                val r = if (whole(r0)) r0 else if (whole(!r0)) !r0 else return listOf(tr("Encuadre el brazo entero: hombro, codo y muñeca"))
                val sh = if (r) L.SHOULDER_R else L.SHOULDER_L; val el = if (r) L.ELBOW_R else L.ELBOW_L
                val wr = if (r) L.WRIST_R else L.WRIST_L; val hp = if (r) L.HIP_R else L.HIP_L
                val a = p[sh]!!; val e = p[el]!!; val w = p[wr]!!
                if (angle(a, e, w) < ELBOW_DEG) out += tr("Codo extendido")
                // separación del cuerpo: ángulo entre el eje del tronco (centro de hombros → centro de caderas,
                // o hombro → cadera si falta el otro lado) y el brazo (hombro → muñeca)
                p[hp]?.let { h ->
                    val ax = if (has(p, L.SHOULDER_L, L.SHOULDER_R, L.HIP_L, L.HIP_R)) {
                        val ms = mid(p[L.SHOULDER_L]!!, p[L.SHOULDER_R]!!); val mh = mid(p[L.HIP_L]!!, p[L.HIP_R]!!)
                        Pt(a.x + (mh.x - ms.x), a.y + (mh.y - ms.y))
                    } else h
                    val abd = angle(ax, a, w)
                    if (abd < ARM_ABD_MIN) out += tr("Separe un poco el brazo del cuerpo (≈ 20°)")
                    if (abd > ARM_ABD_MAX) out += tr("Baje el brazo junto al cuerpo (≈ 20°)")
                }   // sin la cadera (primer plano del brazo) no se mide la separación
                val hw = hypot(e.x - a.x, e.y - a.y) * 0.18f
                // a lo largo del brazo y del antebrazo (con el codo algo flexionado, la recta hombro–muñeca se sale)
                uncovered(c, trn("el brazo"), c?.let { cl ->
                    val u = skinClothesAlong(cl, a, e, hw); val f = skinClothesAlong(cl, e, w, hw)
                    minOf(u.first, f.first) to maxOf(u.second, f.second)
                })?.let { out += it }
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

    /** Una sola pierna: la de cadera, rodilla y tobillo visibles (la más completa), extendida y descubierta. */
    private fun singleLeg(s: Scene): List<String> {
        val p = s.pose; val L = Landmarks; val c = s.classes
        val legs = listOf(Triple(L.HIP_L, L.KNEE_L, L.ANKLE_L), Triple(L.HIP_R, L.KNEE_R, L.ANKLE_R)).filter { (h, k, a) -> has(p, h, k, a) }
        val leg = legs.maxByOrNull { (h, _, a) -> dist(p[h]!!, p[a]!!) } ?: return listOf(tr("Encuadre la pierna entera: cadera, rodilla y tobillo"))
        val h = p[leg.first]!!; val k = p[leg.second]!!; val a = p[leg.third]!!
        val out = ArrayList<String>()
        if (angle(h, k, a) < KNEE_DEG) out += tr("Rodillas extendidas")
        if (a.y > s.aspect * 0.97f) out += tr("Los pies dentro del cuadro")
        if (c == null) out += HINT_ANALYZING else {
            val f = skinClothesAlong(c, Pt(h.x + (k.x - h.x) * 0.35f, h.y + (k.y - h.y) * 0.35f), k, dist(h, k) * 0.12f)
            uncovered(c, trn("los muslos"), f)?.let { out += it }
        }
        return out
    }

    /**
     * Contorno real (borde de la piel) de una mano: la mancha de piel que contiene la palma, limitada a
     * la mano (sin el antebrazo: se corta poco después de la muñeca) y recorrida por su borde exterior.
     * En unidades del ancho; null si la segmentación no la separa.
     */
    fun handOutline(c: Classes, h: Hand): List<Pt>? {
        val p = h.points; if (p.size < 21) return null
        val k = c.width.toFloat()
        val palm = dist(p[0], p[9]).coerceAtLeast(1e-4f)
        val ux = (p[9].x - p[0].x) / palm; val uy = (p[9].y - p[0].y) / palm
        val m = palm * 0.45f
        val bx0 = p.minOf { it.x } - m; val bx1 = p.maxOf { it.x } + m; val by0 = p.minOf { it.y } - m; val by1 = p.maxOf { it.y } + m
        // cerca del esqueleto de ESTA mano (falanges y palma): si las dos manos se tocan, no se funden
        val bones = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8, 5 to 9, 9 to 10, 10 to 11, 11 to 12,
            9 to 13, 13 to 14, 14 to 15, 15 to 16, 13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20, 0 to 9, 0 to 13, 1 to 5)
        val reach = 0.3f * palm
        fun segD(q: Pt, a: Pt, b: Pt): Float {
            val vx = b.x - a.x; val vy = b.y - a.y; val l2 = vx * vx + vy * vy
            val t = if (l2 < 1e-9f) 0f else (((q.x - a.x) * vx + (q.y - a.y) * vy) / l2).coerceIn(0f, 1f)
            return hypot(q.x - a.x - vx * t, q.y - a.y - vy * t)
        }
        val inHand = { x: Int, y: Int ->
            val px = (x + 0.5f) / k; val py = (y + 0.5f) / k; val q = Pt(px, py)
            px in bx0..bx1 && py in by0..by1 && ((px - p[0].x) * ux + (py - p[0].y) * uy) >= -0.12f * palm && isSkin(c.at(x, y)) &&
                bones.any { (i, j) -> segD(q, p[i], p[j]) <= reach }
        }
        // semilla: el centro de la palma (o un nudillo si ahí no hay piel)
        val seeds = listOf(Pt(p[0].x * 0.4f + p[9].x * 0.6f, p[0].y * 0.4f + p[9].y * 0.6f), p[9], p[5], p[13], p[0])
        val seed = seeds.map { (it.x * k).toInt() to (it.y * k).toInt() }.firstOrNull { (x, y) -> inHand(x, y) } ?: return null
        val pts = traceComponent(c.width, c.height, inHand, seed) ?: return null
        // la mancha debe ser del tamaño de una mano (no un trozo ni media escena)
        val area = pts.second
        if (area < 0.25f * palm * palm * k * k || area > 6f * palm * palm * k * k) return null
        return pts.first.map { (x, y) -> Pt((x + 0.5f) / k, (y + 0.5f) / k) }
    }

    /**
     * Borde exterior (recorrido de Moore, en el sentido de las agujas del reloj) de la componente 8-conexa
     * de [inside] que contiene [seed], simplificado a ~1 punto cada 2 píxeles; con su área en píxeles.
     */
    fun traceComponent(w: Int, h: Int, inside: (Int, Int) -> Boolean, seed: Pair<Int, Int>): Pair<List<Pair<Int, Int>>, Int>? {
        val comp = BooleanArray(w * h); val stack = IntArray(w * h); var sp = 0; var area = 0
        fun ok(x: Int, y: Int) = x in 0 until w && y in 0 until h && inside(x, y)
        if (!ok(seed.first, seed.second)) return null
        comp[seed.second * w + seed.first] = true; stack[sp++] = seed.second * w + seed.first
        while (sp > 0) {
            val q = stack[--sp]; area++
            val x = q % w; val y = q / w
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if ((dx != 0 || dy != 0) && ok(xx, yy) && !comp[yy * w + xx]) { comp[yy * w + xx] = true; stack[sp++] = yy * w + xx }
            }
        }
        val start = comp.indexOfFirst { it }
        val sx = start % w; val sy = start / w
        // vecinos en el sentido de las agujas del reloj (y hacia abajo): O, NO, N, NE, E, SE, S, SO
        val dxs = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1); val dys = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)
        fun inC(x: Int, y: Int) = x in 0 until w && y in 0 until h && comp[y * w + x]
        val out = ArrayList<Pair<Int, Int>>()
        var cx = sx; var cy = sy; var back = 0   // se entra desde el oeste (fondo: es el primer píxel de su fila)
        val maxSteps = 4 * (w + h) * 4
        var steps = 0
        do {
            if (steps % 2 == 0) out += cx to cy
            var moved = false
            for (i in 1..8) {
                val d = (back + i) % 8
                val nx = cx + dxs[d]; val ny = cy + dys[d]
                if (inC(nx, ny)) {
                    val pd = (back + i - 1) % 8
                    val bx = cx + dxs[pd] - nx; val by = cy + dys[pd] - ny
                    back = (0 until 8).first { dxs[it] == bx && dys[it] == by }
                    cx = nx; cy = ny; moved = true; break
                }
            }
            if (!moved) break   // píxel aislado
            steps++
        } while ((cx != sx || cy != sy) && steps < maxSteps)
        return if (out.size >= 8) out to area else null
    }

    /** Medio grosor de la piel perpendicular al segmento [a]→[b] en su punto medio (unidades del ancho); null si no se mide. */
    fun halfWidth(c: Classes, a: Pt, b: Pt): Float? {
        val k = c.width.toFloat(); val len = dist(a, b).coerceAtLeast(1e-4f)
        val nx = -(b.y - a.y) / len; val ny = (b.x - a.x) / len
        val ws = listOf(0.35f, 0.5f, 0.65f).mapNotNull { t ->
            val mx = a.x + (b.x - a.x) * t; val my = a.y + (b.y - a.y) * t
            if (!isSkin(c.at((mx * k).toInt(), (my * k).toInt()))) return@mapNotNull null
            fun reach(sg: Int): Float { var d = 0f; val step = 1f / k; while (d < len) { val x = mx + nx * d * sg; val y = my + ny * d * sg; if (!isSkin(c.at((x * k).toInt(), (y * k).toInt()))) break; d += step }; return d }
            (reach(1) + reach(-1)) / 2
        }
        return ws.sorted().getOrNull(ws.size / 2)?.takeIf { it > 0f && it < len }
    }

    val HINT_ANALYZING get() = tr("Comprobando la zona…")
    val HINT_HANDS_SEARCH get() = tr("Buscando las manos…")
    val HINT_BOTH_HANDS get() = tr("Muestre las dos manos enteras")

    private operator fun Pt.minus(o: Pt) = Pt(x - o.x, y - o.y)
    @Suppress("unused") private fun len(p: Pt) = sqrt(p.x * p.x + p.y * p.y)
}
