package com.photomed.app.align

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Contorno de la mandíbula y el mentón (parte del óvalo bajo la boca), más preciso que el ajuste por
 * segmentación: la segmentación pelo / piel suele dar el cuello como piel de la cara (el contorno se
 * salía por debajo del mentón) y la barba como parte de la cara (el contorno rodeaba la barba).
 *
 * Se parte del óvalo de la malla (fiable en el mentón) y, a lo largo de la normal de cada punto, se
 * busca el borde de luminosidad de la mandíbula (la sombra bajo el mentón y la mandíbula: más claro
 * dentro, más oscuro fuera) en una ventana pequeña, sobre todo hacia dentro (−[IN] … +[OUT] del ancho
 * de la cara), con una penalización gaussiana por alejarse de la malla. Donde hay vello facial, una
 * mano u otra oclusión (etiqueta que no es piel), el punto se queda en la malla. Los desplazamientos
 * se suavizan a lo largo del contorno.
 */
object JawContour {
    const val IN = 0.08f
    const val OUT = 0.05f
    const val SIGMA = 0.04f

    /**
     * Contorno lateral e inferior de la cara (del nivel de los ojos al mentón), ajustado al borde visible:
     * - lados (mejillas y mandíbula): el mayor cambio de color L*a*b* entre dentro y fuera (cara —piel o
     *   vello— frente a fondo, pelo de la cabeza o ropa), en ambos sentidos (−[IN] … +[OUT] del ancho);
     * - bajo el mentón (normal hacia abajo): el borde de sombra claro → oscuro, poco hacia fuera, para no
     *   bajar al cuello (que tiene el mismo color de piel);
     * con penalización gaussiana (σ [SIGMA]) por alejarse de la malla; donde la etiqueta es ropa u otro
     * objeto delante de la cara (mano), el punto se queda en la malla. Desplazamientos suavizados.
     * [mesh]: claves de la malla sin ajustar; [snapped]: con el óvalo ajustado a la segmentación (la
     * frente se conserva de ahí).
     */
    fun refine(mesh: Map<String, Pt>, snapped: Map<String, Pt>, lum: GrayImage, labels: FaceLabels?, hair: FacialHair.Result?,
               lab: LabImage? = null): Map<String, Pt> {
        val oval = (0 until 36).map { mesh["oval_$it"] ?: return snapped }
        val noseY = mesh["nose_bottom_1"]?.y ?: (oval.minOf { it.y } + (oval.maxOf { it.y } - oval.minOf { it.y }) * 0.65f)
        val w = lum.width.toFloat()
        val faceW = oval.maxOf { it.x } - oval.minOf { it.x }
        val eyeY = listOfNotNull(mesh[Landmarks.EYE_L], mesh[Landmarks.EYE_R]).map { it.y }.average().toFloat()
            .takeIf { !it.isNaN() } ?: (oval.minOf { it.y } + (oval.maxOf { it.y } - oval.minOf { it.y }) * 0.4f)
        val sides = oval.indices.filter { oval[it].y > eyeY }
        if (sides.size < 8) return snapped
        val k = (faceW * w / 400f).coerceAtLeast(0.6f)
        val g = PhotometricStereo.blur(lum.data, lum.width, lum.height, 1.2f * k)
        val ls = lab?.let { Triple(PhotometricStereo.blur(it.l, it.width, it.height, 1.2f * k), PhotometricStereo.blur(it.a, it.width, it.height, 1.2f * k),
            PhotometricStereo.blur(it.b, it.width, it.height, 1.2f * k)) }
        fun idx(q: Pt, iw: Int, ih: Int): Int { val x = (q.x * iw).roundToInt().coerceIn(0, iw - 1); val y = (q.y * iw).roundToInt().coerceIn(0, ih - 1); return y * iw + x }
        fun lumAt(q: Pt) = g[idx(q, lum.width, lum.height)]
        fun labAt(q: Pt): FloatArray? = if (ls == null || lab == null) null else idx(q, lab.width, lab.height).let { floatArrayOf(ls.first[it], ls.second[it], ls.third[it]) }
        val cx = oval.map { it.x }.average().toFloat(); val cy = oval.map { it.y }.average().toFloat()
        fun normal(i: Int): Pt? {
            val o = oval[i]; val a = oval[(i + 35) % 36]; val b = oval[(i + 1) % 36]
            var nx = -(b.y - a.y); var ny = b.x - a.x
            val nn = hypot(nx, ny).takeIf { it > 1e-6f } ?: return null
            nx /= nn; ny /= nn
            if (nx * (o.x - cx) + ny * (o.y - cy) < 0) { nx = -nx; ny = -ny }
            return Pt(nx, ny)
        }
        val offs = FloatArray(36) { Float.NaN }
        val step = 1f / w
        for (i in sides) {
            val o = oval[i]; val n = normal(i) ?: continue
            // objeto DELANTE de la cara (mano, ropa subida…): se comprueba dentro de la cara (6 % hacia
            // dentro), no en el punto de la malla: si la malla cae fuera, sobre ropa o fondo (p. ej. un
            // pañuelo), es justo donde hay que ajustarlo
            val occl = labels?.let { l -> val q = Pt(o.x - n.x * 0.06f * faceW, o.y - n.y * 0.06f * faceW)
                val c = l[(q.x * l.width).roundToInt(), (q.y * l.width).roundToInt()]
                c == FaceLabels.CLOTHES || c == FaceLabels.OTHER } == true
            if (occl) { offs[i] = Float.NaN; continue }
            val chin = n.y > 0.75f   // bajo el mentón: sombra, poco hacia fuera
            // 1) borde de la región de la cara segmentada (piel del rostro, que incluye barba y bigote; con
            //    voto 3 × 3 para no seguir pelos sueltos): la transición dentro → fuera más cercana a la malla.
            //    Bajo el mentón, solo si no baja más de 2,5 % (si no, es el cuello sin barba: se usa la sombra)
            val segD = labels?.let { l -> segEdge(l, o, n, faceW, noseY) }
            // a la altura de las orejas (por encima de la base de la nariz) el borde de la piel puede ser la
            // oreja: como mucho un 1,5 % del ancho hacia fuera
            val earBand = o.y < noseY
            if (segD != null && (!(n.y > 0.6f) || segD <= 0.025f * faceW) && (!earBand || segD <= HairSkin.EAR_OUT * faceW)) { offs[i] = segD; continue }
            val dMin = -IN * faceW; val dMax = (if (chin) 0.02f else if (earBand) HairSkin.EAR_OUT else OUT) * faceW
            val h = 2.5f * step * k
            // color de la propia cara junto a este punto (10–14 % hacia dentro: piel o barba)
            val ref = if (lab == null) null else listOf(0.10f, 0.12f, 0.14f).mapNotNull { f -> labAt(Pt(o.x - n.x * f * faceW, o.y - n.y * f * faceW)) }
                .let { v -> FloatArray(3) { c -> v.map { it[c] }.average().toFloat() } }
            // parecido a la cara (0..1): ΔE con L* a 0,8 de peso (tolera algo de sombra en la mejilla, pero
            // no el fondo oscuro, que en L* se aleja mucho más)
            fun faceLike(q: Pt): Float {
                val r = ref ?: return 1f; val v = labAt(q) ?: return 1f
                val de = hypot(hypot((v[0] - r[0]) * 0.8f, v[1] - r[1]), v[2] - r[2])
                return 1f / (1f + exp((de - 20f) / 3f))
            }
            var best = 0f; var bestScore = 0f
            var d = dMin
            while (d <= dMax) {
                val q = Pt(o.x + n.x * d, o.y + n.y * d)
                val qi = Pt(q.x - n.x * h, q.y - n.y * h); val qo = Pt(q.x + n.x * h, q.y + n.y * h)
                val edge = when {
                    chin -> lumAt(qi) - lumAt(qo)                         // claro dentro → oscuro fuera
                    lab == null -> kotlin.math.abs(lumAt(qi) - lumAt(qo))
                    else -> { val a = labAt(qi)!!; val b = labAt(qo)!!
                        // ΔE*ab / 100 (en la misma escala que la luminancia 0..1)
                        hypot(hypot(a[0] - b[0], a[1] - b[1]), a[2] - b[2]) / 100f }
                }
                val prior = exp(-(d / (SIGMA * faceW)) * (d / (SIGMA * faceW)) / 2)
                // borde DE LA CARA: dentro parecido a la cara, fuera no (descarta bordes del fondo)
                val sc = edge * prior * (if (chin || ref == null) 1f else faceLike(qi) * (1f - faceLike(qo)))
                if (sc > bestScore) { bestScore = sc; best = d }
                d += step
            }
            offs[i] = if (bestScore > (if (chin) 0.015f else 0.03f)) best else Float.NaN
        }
        // suavizado solo con los puntos medidos (los que no tienen borde claro no arrastran a 0)
        val n36 = 36
        // suavizado ligero (mediana de 5 contra bordes sueltos y media 1-2-1): conserva el ángulo de la
        // mandíbula y las formas irregulares (antes, una media de 5 puntos los redondeaba)
        val med = FloatArray(n36) { i -> if (i !in sides) 0f else (-2..2).map { offs[(i + it + n36) % n36] }.filter { !it.isNaN() }
            .let { v -> if (v.isEmpty()) 0f else v.sorted()[v.size / 2] } }
        val sm = FloatArray(n36) { i -> if (i !in sides) 0f else (-1..1).map { med[(i + it + n36) % n36] * (2 - kotlin.math.abs(it)) }.sum() / 4f }
        val out = snapped.toMutableMap()
        for (i in sides) {
            val o = oval[i]; val n = normal(i) ?: continue
            out["oval_$i"] = Pt(o.x + n.x * sm[i], o.y + n.y * sm[i])
        }
        // transición con la parte superior (ajustada a la segmentación)
        for (i in oval.indices.filter { it !in sides && ((it + 1) % 36 in sides || (it + 35) % 36 in sides) }) {
            val s0 = snapped["oval_$i"] ?: continue; val r = out["oval_${if ((i + 1) % 36 in sides) (i + 1) % 36 else (i + 35) % 36}"] ?: continue
            out["oval_$i"] = Pt((s0.x * 2 + r.x) / 3, (s0.y * 2 + r.y) / 3)
        }
        return out
    }

    /**
     * Desplazamiento (unidades del ancho, por la normal [n]) del borde de la cara segmentada más cercano a
     * [o] entre −[IN] y +[OUT] del ancho de la cara; null si no hay transición. Dentro = piel del rostro o,
     * bajo la nariz, pelo (barba) — con voto de 3 × 3 vecinos.
     */
    fun segEdge(l: FaceLabels, o: Pt, n: Pt, faceW: Float, noseY: Float): Float? {
        val w = l.width.toFloat()
        fun inside(q: Pt): Boolean {
            var c = 0
            for (dy in -1..1) for (dx in -1..1) {
                val v = l[(q.x * w).roundToInt() + dx, (q.y * w).roundToInt() + dy]
                if (v == FaceLabels.FACE_SKIN || (v == FaceLabels.HAIR && q.y > noseY)) c++
            }
            return c >= 5
        }
        val step = 1f / w
        var best: Float? = null
        var d = -IN * faceW
        var prev = inside(Pt(o.x + n.x * d, o.y + n.y * d))
        while (d <= OUT * faceW) {
            val cur = inside(Pt(o.x + n.x * d, o.y + n.y * d))
            if (prev && !cur && (best == null || kotlin.math.abs(d) < kotlin.math.abs(best))) best = d - step / 2
            prev = cur; d += step
        }
        return best
    }
}
