package com.photomed.app.align

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Densidad capilar aparente en una foto general del cuero cabelludo (sin tricoscopio): fracción de
 * cuero cabelludo visible entre el pelo, en el centro, en la raya frente a los lados, y grados
 * orientativos de las escalas clínicas.
 *
 * Método (como HairComb, JAMA Dermatol 2023, y la automatización de SALT): en la región de la cabeza
 * (pelo y piel de la segmentación multiclase), cada píxel recibe una visibilidad del cuero cabelludo
 * 0..1, media de (a) la segmentación (pelo = 0, piel = 1) y (b) su color proyectado entre el color del
 * pelo y el de la piel del propio paciente (CIELAB), que da los valores intermedios del pelo ralo.
 * Validado con las máscaras de pérdida de pelo de UniData (10 fotos cenitales de hombres y mujeres):
 * correlación 0,93 con la cobertura anotada, error medio ≈ 10 puntos. El ancho de la raya en mm no se
 * da: con pelo claro o brillos junto a la raya salía 3–10 veces el anotado; se compara en su lugar el
 * cuero visible en la franja central con el de los lados (lo que valoran Sinclair y Olsen).
 *
 * Escalas (grados **orientativos**, para el seguimiento del mismo paciente, no diagnósticos):
 * - **BASP** (Lee 2007): tipo específico V (vértice) y F (frontal), 0–3, por el cuero visible.
 * - **Ludwig** (I–III) y **Sinclair** (1–5): patrón femenino, por el centro de la vista cenital.
 * - **Hamilton‑Norwood** de la coronilla (III vértice … VI–VII).
 * Una foto general no permite contar pelos por cm² (eso es tricoscopia); el cuero visible aparente
 * depende del peinado, de la luz y del color del pelo: compárense tomas en las mismas condiciones.
 */
object HairDensity {
    /** Vista: cenital (vértice), coronilla o parietal (cabeza desde arriba o detrás), o frontal con la cabeza inclinada. */
    enum class View { VERTEX, CROWN, PARIETAL, FRONT_TILT }

    data class Result(
        /** Cuero cabelludo visible en toda la región, 0..1. */
        val visible: Float,
        /** En el centro (elipse de la mitad del tamaño) y en la periferia. */
        val center: Float,
        val periphery: Float,
        /**
         * Cuero visible en la franja central (la raya: ±[STRIP] del ancho de la cabeza en cada fila, en la línea
         * media) y en las dos franjas contiguas: la raya que se ensancha (Sinclair, Olsen) es más visible
         * en el centro que a los lados.
         */
        val midline: Float,
        val lateral: Float,
        /** Contraste de color pelo–piel (ΔE); bajo ([MIN_CONTRAST]): solo cuenta la segmentación. */
        val contrast: Float,
        /** Contorno de la región (x del ancho, y en unidades del ancho). */
        val outline: List<Pt>,
        /** Celdas con el cuero cabelludo más visible que cubierto (cuadrados, mismas unidades). */
        val cells: List<List<Pt>>,
        /** Bordes de la franja central (dos líneas, mismas unidades). */
        val strip: List<List<Pt>>,
        /** Píxeles de la región (a la resolución del análisis). */
        val area: Int,
        /** Región analizada (máscara a la resolución del análisis, fila mayor). */
        val roi: BooleanArray,
    )

    const val MIN_CONTRAST = 12f
    /** Peso del color frente a la segmentación en la visibilidad de cada píxel. */
    const val COLOR_WEIGHT = 0.5f
    /** Región mínima (fracción de la imagen) para dar resultado. */
    const val MIN_AREA = 0.04f

    private val DEFAULT_HAIR = floatArrayOf(30f, 4f, 6f)
    private val DEFAULT_SKIN = floatArrayOf(65f, 12f, 15f)

    /**
     * [px]: ARGB de la foto a [w]×[h] (conviene ≈ 384 px de ancho); [labels]: su segmentación (cualquier
     * resolución). [keep]: filtro opcional de la región (x del ancho, y en unidades del ancho), p. ej. por
     * encima de la línea de implantación.
     */
    fun analyze(px: IntArray, w: Int, h: Int, labels: FaceLabels, view: View,
                keep: ((Pt) -> Boolean)? = null): Result? {
        val n = w * h
        if (n == 0 || px.size < n) return null
        val lab = IntArray(n)
        for (y in 0 until h) for (x in 0 until w) lab[y * w + x] = labels[x * labels.width / w, y * labels.height / h]
        val headCls = BooleanArray(n) { val c = lab[it]; c == FaceLabels.HAIR || c == FaceLabels.FACE_SKIN || c == FaceLabels.BODY_SKIN }
        if (keep != null) for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (headCls[i] && !keep(Pt((x + 0.5f) / w, (y + 0.5f) / w))) headCls[i] = false
        }
        var roi = largest(headCls, w, h) ?: return null
        roi = fillHoles(roi, w, h)
        // coronilla y parietal: sin el cuello ni los hombros (la parte baja de la cabeza en el cuadro)
        if (view == View.CROWN || view == View.PARIETAL) cutBelow(roi, w, h, if (view == View.CROWN) 0.72f else 0.8f)
        roi = erode(roi, w, h, max(1, w / 192))
        val area = roi.count { it }
        if (area < MIN_AREA * n) return null

        // colores del pelo y de la piel del paciente (medianas en CIELAB)
        val labOf = Array(n) { if (roi[it]) LineMeasure.srgbToLab(px[it]) else EMPTY }
        fun median(sel: (Int) -> Boolean): FloatArray? {
            val idx = (0 until n).filter { roi[it] && sel(it) }
            if (idx.size < 50) return null
            return FloatArray(3) { k -> idx.map { labOf[it][k] }.sorted()[idx.size / 2] }
        }
        val hair = median { lab[it] == FaceLabels.HAIR } ?: DEFAULT_HAIR
        val skin = median { lab[it] != FaceLabels.HAIR } ?: skinElsewhere(px, lab, n) ?: DEFAULT_SKIN
        val d = FloatArray(3) { skin[it] - hair[it] }
        val dd = d[0] * d[0] + d[1] * d[1] + d[2] * d[2]
        val contrast = sqrt(dd)
        val wc = if (contrast >= MIN_CONTRAST) COLOR_WEIGHT else 0f

        val vis = FloatArray(n)
        for (i in 0 until n) if (roi[i]) {
            val seg = if (lab[i] == FaceLabels.HAIR) 0f else 1f
            val c = labOf[i]
            val t = if (dd > 1e-3f) (((c[0] - hair[0]) * d[0] + (c[1] - hair[1]) * d[1] + (c[2] - hair[2]) * d[2]) / dd).coerceIn(0f, 1f) else seg
            vis[i] = wc * t + (1 - wc) * seg
        }
        val sm = blur(vis, roi, w, h, max(1, w / 128))

        // centro y periferia: elipse de la mitad de los semiejes del recuadro, en el centroide
        var sx = 0.0; var sy = 0.0; var x0 = w; var x1 = 0; var y0 = h; var y1 = 0
        for (y in 0 until h) for (x in 0 until w) if (roi[y * w + x]) {
            sx += x; sy += y; x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
        }
        val cx = (sx / area).toFloat(); val cy = (sy / area).toFloat()
        val ax = (x1 - x0) / 4f; val ay = (y1 - y0) / 4f
        var vc = 0.0; var nc = 0; var vp = 0.0; var np = 0; var vt = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; if (!roi[i]) continue
            vt += vis[i]
            val e = ((x - cx) / ax).let { it * it } + ((y - cy) / ay).let { it * it }
            if (e <= 1f) { vc += vis[i]; nc++ } else { vp += vis[i]; np++ }
        }

        // raya: en la vertical del cuadro (la guía pone la frente arriba) o, en fotos giradas, en la horizontal;
        // la orientación donde el centro destaca más
        val sv = strip(vis, roi, w, h, y0, y1)
        val sh = strip(transpose(vis, w, h), transpose(roi, w, h), h, w, x0, x1).let { q ->
            // de vuelta a la foto: (x, y) ↔ (y, x), en unidades del ancho de la foto
            fun back(p: Pt) = Pt(p.y * h / w, p.x * h / w)
            q.copy(edges = q.edges.map { l -> l.map(::back) })
        }
        val st = if (sv.central - sv.lateral >= sh.central - sh.lateral) sv else sh
        return Result(
            visible = (vt / area).toFloat(), center = if (nc > 0) (vc / nc).toFloat() else (vt / area).toFloat(),
            periphery = if (np > 0) (vp / np).toFloat() else (vt / area).toFloat(), midline = st.central, lateral = st.lateral,
            contrast = contrast, outline = outline(roi, w, h, cx, cy), cells = cells(sm, roi, w, h), strip = st.edges, area = area, roi = roi,
        )
    }

    // --- grados orientativos ---

    /** BASP específico (V o F), 0–3, por el cuero visible de la zona. */
    fun basp(visible: Float): Int = when {
        visible < 0.15f -> 0
        visible < 0.35f -> 1
        visible < 0.60f -> 2
        else -> 3
    }

    /** Ludwig 0 (sin aclaramiento) … 3, por el centro de la vista cenital. */
    fun ludwig(center: Float): Int = basp(center)

    /** Sinclair 1–5 por el centro de la vista cenital; la raya más visible que los lados sube un grado en los primeros. */
    fun sinclair(center: Float, midlineExcess: Float): Int {
        val g = when {
            center < 0.08f -> 1
            center < 0.15f -> 2
            center < 0.30f -> 3
            center < 0.50f -> 4
            else -> 5
        }
        return if (g <= 2 && midlineExcess > PART_WIDE) g + 1 else g
    }

    /** Raya ensanchada: cuero visible en la franja central que supera al de los lados en más de esto. */
    const val PART_WIDE = 0.15f
    /** Media anchura de la franja central (fracción del ancho de la cabeza en cada fila). */
    const val STRIP = 0.08f

    private val ROMAN = arrayOf("0", "I", "II", "III")

    /** Texto de los grados de una vista (tipo probable en la ficha de medidas). */
    fun grades(r: Result, view: View): String = when (view) {
        View.VERTEX -> {
            val v = basp(r.center)
            val norwood = when (v) { 0 -> "–"; 1 -> "III vértice"; 2 -> "IV–V"; else -> "VI–VII" }
            "BASP V$v · Norwood ≈ $norwood · Ludwig ${ROMAN[ludwig(r.center)]} · Sinclair ${sinclair(r.center, r.midline - r.lateral)}"
        }
        View.CROWN -> "BASP V${basp(r.center)}"
        View.PARIETAL -> "BASP ${basp(r.visible)} (parietal)"
        View.FRONT_TILT -> "BASP F${basp(r.visible)}"
    }

    // --- tipo básico BASP de la línea de implantación frontal ---

    /**
     * Tipo básico BASP por la forma de la línea frontal: L (sin retroceso), M (las entradas retroceden
     * más que el centro, [templeRecessMm] ≥ 10 mm: altura de la línea en los cantos externos menos en el
     * centro), C (retrocede la línea media). [pupilHairIpd]: altura de la línea en el centro sobre las
     * pupilas, en distancias interpupilares (independiente de la escala de la foto).
     *
     * Calibrado con CelebA (atributo «Receding_Hairline» etiquetado por personas; 38 hombres con entradas
     * y 39 sin ellas): la altura central ≥ [RECEDED_IPD] los separa con una exactitud equilibrada del 79 %
     * (`BaspPhotosTest`). El umbral de las entradas (forma en M) no se pudo validar: esas fotos (≈ 180 px)
     * no resuelven las sienes; es el de las definiciones de la escala.
     */
    fun baspBasic(pupilHairIpd: Float, templeRecessMm: Float?): String = when {
        templeRecessMm != null && templeRecessMm >= 10f -> "M" + when { templeRecessMm < 20f -> 1; templeRecessMm < 35f -> 2; else -> 3 }
        pupilHairIpd >= RECEDED_IPD -> "C" + when { pupilHairIpd < 1.35f -> 1; pupilHairIpd < 1.55f -> 2; else -> 3 }
        else -> "L"
    }

    /** Línea frontal retrocedida: altura central sobre las pupilas, en distancias interpupilares (≈ 73 mm). */
    const val RECEDED_IPD = 1.16f

    /** Norwood aproximado por el tipo básico BASP (patrón frontal). */
    fun norwoodOfBasic(t: String): String = when (t) {
        "L" -> "I"; "M1" -> "II"; "M2" -> "III"; "M3" -> "IV"
        "C1" -> "IIa"; "C2" -> "IIIa"; "C3" -> "IVa"; else -> "–"
    }

    // --- utilidades de máscara ---

    private val EMPTY = FloatArray(3)

    private fun skinElsewhere(px: IntArray, lab: IntArray, n: Int): FloatArray? {
        val idx = (0 until n).filter { lab[it] == FaceLabels.FACE_SKIN || lab[it] == FaceLabels.BODY_SKIN }
        if (idx.size < 50) return null
        val step = max(1, idx.size / 4000)
        val s = idx.filterIndexed { k, _ -> k % step == 0 }.map { LineMeasure.srgbToLab(px[it]) }
        return FloatArray(3) { k -> s.map { it[k] }.sorted()[s.size / 2] }
    }

    private fun largest(m: BooleanArray, w: Int, h: Int): BooleanArray? {
        val comp = IntArray(m.size) { -1 }
        var best = -1; var bestN = 0; var id = 0
        val q = IntArray(m.size)
        for (s in m.indices) if (m[s] && comp[s] < 0) {
            var qh = 0; var qt = 0; q[qt++] = s; comp[s] = id; var cnt = 0
            while (qh < qt) {
                val i = q[qh++]; cnt++
                val x = i % w; val y = i / w
                if (x > 0 && m[i - 1] && comp[i - 1] < 0) { comp[i - 1] = id; q[qt++] = i - 1 }
                if (x < w - 1 && m[i + 1] && comp[i + 1] < 0) { comp[i + 1] = id; q[qt++] = i + 1 }
                if (y > 0 && m[i - w] && comp[i - w] < 0) { comp[i - w] = id; q[qt++] = i - w }
                if (y < h - 1 && m[i + w] && comp[i + w] < 0) { comp[i + w] = id; q[qt++] = i + w }
            }
            if (cnt > bestN) { bestN = cnt; best = id }
            id++
        }
        if (best < 0) return null
        return BooleanArray(m.size) { comp[it] == best }
    }

    private fun fillHoles(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(m.size)
        val q = IntArray(m.size); var qh = 0; var qt = 0
        fun push(i: Int) { if (!m[i] && !out[i]) { out[i] = true; q[qt++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (qh < qt) {
            val i = q[qh++]; val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }
        return BooleanArray(m.size) { !out[it] }
    }

    private fun cutBelow(m: BooleanArray, w: Int, h: Int, frac: Float) {
        var y0 = h; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (m[y * w + x]) { y0 = min(y0, y); y1 = max(y1, y) }
        if (y1 < y0) return
        val cut = y0 + ((y1 - y0) * frac).toInt()
        for (y in cut + 1 until h) for (x in 0 until w) m[y * w + x] = false
    }

    private fun erode(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        var cur = m
        repeat(r) {
            val prev = cur
            cur = BooleanArray(m.size) { i ->
                val x = i % w; val y = i / w
                prev[i] && x > 0 && x < w - 1 && y > 0 && y < h - 1 && prev[i - 1] && prev[i + 1] && prev[i - w] && prev[i + w]
            }
        }
        return cur
    }

    /** Media en un cuadrado de lado 2r+1 dentro de la región. */
    private fun blur(v: FloatArray, roi: BooleanArray, w: Int, h: Int, r: Int): FloatArray {
        val out = FloatArray(v.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; if (!roi[i]) continue
            var s = 0f; var c = 0
            for (yy in max(0, y - r)..min(h - 1, y + r)) for (xx in max(0, x - r)..min(w - 1, x + r)) {
                val j = yy * w + xx; if (roi[j]) { s += v[j]; c++ }
            }
            out[i] = s / c
        }
        return out
    }

    private data class Strip(val central: Float, val lateral: Float, val edges: List<List<Pt>>)

    /** Franja central (±[STRIP] de cada fila, del 10 al 90 % de la altura) y franjas contiguas (hasta ±3·[STRIP]). */
    private fun strip(v: FloatArray, roi: BooleanArray, w: Int, h: Int, y0: Int, y1: Int): Strip {
        var sc = 0.0; var nc = 0; var sl = 0.0; var nl = 0
        val left = ArrayList<Pt>(); val right = ArrayList<Pt>()
        val ya = y0 + ((y1 - y0) * 0.10f).toInt(); val yb = y0 + ((y1 - y0) * 0.90f).toInt()
        for (y in ya..yb) {
            var l = -1; var r = -1
            for (x in 0 until w) if (roi[y * w + x]) { if (l < 0) l = x; r = x }
            if (l < 0 || r - l < 8) continue
            val mid = (l + r) / 2f; val half = STRIP * (r - l)
            for (x in l..r) {
                val i = y * w + x; if (!roi[i]) continue
                val d = abs(x - mid)
                if (d <= half) { sc += v[i]; nc++ } else if (d <= 3 * half) { sl += v[i]; nl++ }
            }
            left += Pt((mid - half) / w, (y + 0.5f) / w); right += Pt((mid + half) / w, (y + 0.5f) / w)
        }
        if (nc == 0 || nl == 0) return Strip(0f, 0f, emptyList())
        return Strip((sc / nc).toFloat(), (sl / nl).toFloat(), listOf(left, right))
    }

    private fun transpose(a: FloatArray, w: Int, h: Int) = FloatArray(a.size) { i -> val x = i / h; val y = i % h; a[y * w + x] }
    private fun transpose(a: BooleanArray, w: Int, h: Int) = BooleanArray(a.size) { i -> val x = i / h; val y = i % h; a[y * w + x] }

    /** Contorno: el punto de la región más lejano en 72 direcciones desde el centroide. */
    private fun outline(roi: BooleanArray, w: Int, h: Int, cx: Float, cy: Float): List<Pt> {
        val out = ArrayList<Pt>()
        for (k in 0 until 72) {
            val a = 2 * Math.PI * k / 72
            val dx = kotlin.math.cos(a).toFloat(); val dy = kotlin.math.sin(a).toFloat()
            var last: Pt? = null
            var t = 0f
            while (true) {
                val x = (cx + dx * t).toInt(); val y = (cy + dy * t).toInt()
                if (x < 0 || y < 0 || x >= w || y >= h) break
                if (roi[y * w + x]) last = Pt((x + 0.5f) / w, (y + 0.5f) / w)
                t += 1f
            }
            last?.let { out += it }
        }
        return out
    }

    /** Celdas (≈ 1/40 del ancho) con la visibilidad media > 0,5 y la mayor parte dentro de la región. */
    private fun cells(v: FloatArray, roi: BooleanArray, w: Int, h: Int): List<List<Pt>> {
        val cs = max(2, w / 40)
        val out = ArrayList<List<Pt>>()
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                var s = 0f; var c = 0; var all = 0
                for (yy in y until min(h, y + cs)) for (xx in x until min(w, x + cs)) {
                    all++; val i = yy * w + xx; if (roi[i]) { s += v[i]; c++ }
                }
                if (c >= 0.6f * all && s / c > 0.5f) {
                    val a = x.toFloat() / w; val b = y.toFloat() / w; val e = cs.toFloat() / w
                    out += listOf(Pt(a, b), Pt(a + e, b), Pt(a + e, b + e), Pt(a, b + e))
                }
                x += cs
            }
            y += cs
        }
        return out
    }
}
