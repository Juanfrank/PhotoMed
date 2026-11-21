package com.photomed.app.align

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Barba y bigote en la parte inferior de la cara. La segmentación pelo / piel los clasifica como
 * piel del rostro, así que los surcos (nasogeniano, marioneta) se trazaban sobre el vello y se
 * desviaban hacia sus bordes oscuros. Se detectan por dos rasgos que la piel no tiene a la vez:
 * - **textura fina**: energía de alta frecuencia (|L* − gaussiana σ 1 px| promediada), alta en los
 *   pelos; un surco o una arruga son valles suaves y apenas la tienen;
 * - **color sin croma**: el vello (negro, castaño oscuro, gris, blanco) tiene un croma relativo
 *   C*ab / (L* + 10) mucho menor que la piel (la sombra baja L* y C* a la vez y lo conserva).
 * Referencia: piel de las mejillas junto a la nariz y de la frente (el mínimo de croma, por si hay
 * colorete); son puntos centrales que siguen sobre la piel aunque la cara esté girada (los de la
 * mejilla externa caían en el fondo y la referencia salía mal). Además del vello poblado se detecta el
 * ralo (barba de pocos días, canas dispersas): textura algo mayor que la de la piel con menos croma.
 * Región: óvalo de la cara bajo la base de la nariz, sin los labios. Se descartan manchas pequeñas.
 */
object FacialHair {
    /** Vello poco poblado: textura sobre la de la piel × [SPARSE_E] y croma relativo bajo × [SPARSE_C]. */
    const val SPARSE_E = 1.6f
    const val SPARSE_C = 0.75f
    class Result(val width: Int, val height: Int, val mask: BooleanArray, val fraction: Float) {
        /** ¿Vello en [p] (unidades del ancho)? */
        operator fun get(p: Pt): Boolean {
            val x = (p.x * width).roundToInt(); val y = (p.y * width).roundToInt()
            return x in 0 until width && y in 0 until height && mask[y * width + x]
        }
        /** Fracción de los puntos de [pts] sobre vello. */
        fun cover(pts: List<Pt>) = if (pts.isEmpty()) 0f else pts.count { this[it] } / pts.size.toFloat()
    }

    /** Óvalo inferior de la malla de MediaPipe (de oreja a oreja por el mentón). */
    val OVAL = intArrayOf(234, 93, 132, 58, 172, 136, 150, 149, 176, 148, 152, 377, 400, 378, 379, 365, 397, 288, 361, 323, 454)
    private val LIPS = intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146)

    /** Con la malla completa: región y referencias por sus índices. */
    fun fromMesh(lab: LabImage, mesh: List<Pt>): Result? {
        if (mesh.size < 468) return null
        val faceW = hypot(mesh[234].x - mesh[454].x, mesh[234].y - mesh[454].y)
        return detect(lab, OVAL.map { mesh[it] }, mesh[2].y, LIPS.map { mesh[it] }, listOf(mesh[205], mesh[425], mesh[151]), faceW)
    }

    /**
     * [region]: polígono de la cara (unidades del ancho); se usa solo por debajo de [topY]; [lips]:
     * se excluye; [refs]: centros de piel de referencia (discos de radio 0,035 × [faceW]).
     */
    fun detect(lab: LabImage, region: List<Pt>, topY: Float, lips: List<Pt>?, refs: List<Pt>, faceW: Float): Result? {
        val w = lab.width; val h = lab.height; val n = w * h
        val facePx = faceW * w
        if (facePx < 60f) return null
        val k = facePx / 400f
        // energía de textura fina y croma relativo
        val l = lab.l
        val g = PhotometricStereo.blur(l, w, h, 1.0f * k.coerceAtLeast(0.6f))
        val fine = FloatArray(n) { kotlin.math.abs(l[it] - g[it]) }
        val energy = box(fine, w, h, (4 * k).roundToInt().coerceAtLeast(2))
        val cr = FloatArray(n) { hypot(lab.a[it], lab.b[it]) / (l[it] + 10f) }
        val chroma = box(cr, w, h, (2 * k).roundToInt().coerceAtLeast(1))
        // referencias de piel
        val r = 0.035f * faceW * w
        val eRef = ArrayList<Float>(); val cMed = ArrayList<Float>()
        for (c in refs) {
            val cs = ArrayList<Float>()
            for (y in (c.y * w - r).toInt()..(c.y * w + r).toInt()) for (x in (c.x * w - r).toInt()..(c.x * w + r).toInt()) {
                if (x !in 0 until w || y !in 0 until h) continue
                eRef += energy[y * w + x]; cs += chroma[y * w + x]
            }
            if (cs.size > 10) cMed += cs.sorted()[cs.size / 2]
        }
        if (eRef.size < 30 || cMed.isEmpty()) return null
        eRef.sort()
        val em = eRef[eRef.size / 2]; val e90 = eRef[(eRef.size * 0.9f).toInt()]
        val refC = cMed.min()
        val thr = max(2.2f * em, 1.6f * e90)
        // región: óvalo bajo la nariz, sin labios
        val reg = rasterize(region, w, h)
        val lipMask = lips?.let { rasterize(it, w, h) }
        val top = (topY * w).roundToInt() - (3 * k).roundToInt()
        val m = Mask(w, h)
        for (i in 0 until n) {
            if (!reg[i] || i / w < top || lipMask?.get(i) == true) continue
            val c = chroma[i]; val e = energy[i]
            m.data[i] = (c < 0.55f * refC && e > 1.2f * em) || (e > thr && c < 0.8f * refC) ||
                // vello ralo (barba de pocos días, canas dispersas): algo de textura y menos croma
                (e > SPARSE_E * em && c < SPARSE_C * refC)
        }
        // limpieza: apertura 3 × 3, cierre ~7 × 7 y sin manchas pequeñas
        var mm = MaskOps.erode(m, 1); mm = MaskOps.dilate(mm, 1)
        val rc = (3 * k).roundToInt().coerceAtLeast(2)
        mm = MaskOps.erode(MaskOps.dilate(mm, rc), rc)
        val clean = removeSmall(mm, (0.004f * facePx * facePx).roundToInt())
        val regionPx = (0 until n).count { reg[it] && it / w >= top }
        val frac = if (regionPx == 0) 0f else clean.data.count { it } / regionPx.toFloat()
        return Result(w, h, clean.data, frac)
    }

    private fun box(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        // filas y luego columnas, cada pasada repartida entre núcleos
        Par.rows(h) { ya, yb -> for (y in ya until yb) { var s = 0f; var c = 0
            for (x in -r..r) if (x in 0 until w) { s += src[y * w + x]; c++ }
            for (x in 0 until w) {
                tmp[y * w + x] = s / c
                val xo = x - r; val xi = x + r + 1
                if (xo >= 0) { s -= src[y * w + xo]; c-- }
                if (xi < w) { s += src[y * w + xi]; c++ }
            } } }
        Par.rows(w) { xa, xb -> for (x in xa until xb) { var s = 0f; var c = 0
            for (y in -r..r) if (y in 0 until h) { s += tmp[y * w + x]; c++ }
            for (y in 0 until h) {
                out[y * w + x] = s / c
                val yo = y - r; val yi = y + r + 1
                if (yo >= 0) { s -= tmp[yo * w + x]; c-- }
                if (yi < h) { s += tmp[yi * w + x]; c++ }
            } } }
        return out
    }

    private fun rasterize(poly: List<Pt>, w: Int, h: Int): BooleanArray {
        val xs = poly.map { it.x * w }; val ys = poly.map { it.y * w }
        val out = BooleanArray(w * h)
        val y0 = max(0, ys.min().toInt()); val y1 = minOf(h - 1, ys.max().toInt())
        for (y in y0..y1) {
            val cross = ArrayList<Float>()
            var j = poly.size - 1
            for (i in poly.indices) {
                if ((ys[i] > y) != (ys[j] > y)) cross += xs[i] + (y - ys[i]) * (xs[j] - xs[i]) / (ys[j] - ys[i])
                j = i
            }
            cross.sort()
            for (c in 0 until cross.size - 1 step 2) for (x in max(0, cross[c].toInt())..minOf(w - 1, cross[c + 1].toInt())) out[y * w + x] = true
        }
        return out
    }

    private fun removeSmall(m: Mask, minArea: Int): Mask {
        val w = m.width; val h = m.height
        val out = Mask(w, h); val seen = BooleanArray(w * h); val stack = IntArray(w * h)
        for (s in 0 until w * h) {
            if (!m.data[s] || seen[s]) continue
            var sp = 0; stack[sp++] = s; seen[s] = true
            val comp = ArrayList<Int>()
            while (sp > 0) {
                val i = stack[--sp]; comp += i
                val x = i % w; val y = i / w
                for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val q = ny * w + nx
                    if (m.data[q] && !seen[q]) { seen[q] = true; stack[sp++] = q }
                }
            }
            if (comp.size >= minArea) comp.forEach { out.data[it] = true }
        }
        return out
    }
}
