package com.photomed.app.align

import com.photomed.app.i18n.trn
import com.photomed.app.i18n.tr
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Conversión sRGB ↔ CIELAB (D65). */
object Lab {
    private fun lin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun gam(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
    private fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
    private fun fi(t: Double) = if (t * t * t > 216.0 / 24389) t * t * t else (116 * t - 16) / (24389.0 / 27)

    fun fromRgb(c: Int, out: DoubleArray) {
        val r = lin(((c shr 16) and 0xFF) / 255.0); val g = lin(((c shr 8) and 0xFF) / 255.0); val b = lin((c and 0xFF) / 255.0)
        val fx = f((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047)
        val fy = f(0.2126 * r + 0.7152 * g + 0.0722 * b)
        val fz = f((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883)
        out[0] = 116 * fy - 16; out[1] = 500 * (fx - fy); out[2] = 200 * (fy - fz)
    }

    fun toRgb(L: Double, a: Double, b: Double, alpha: Int): Int {
        val fy = (L + 16) / 116; val fx = fy + a / 500; val fz = fy - b / 200
        val x = fi(fx) * 0.95047; val y = fi(fy); val z = fi(fz) * 1.08883
        fun ch(v: Double) = (gam(v.coerceIn(0.0, 1.0)) * 255 + 0.5).toInt().coerceIn(0, 255)
        val r = ch(3.2406 * x - 1.5372 * y - 0.4986 * z)
        val g = ch(-0.9689 * x + 1.8758 * y + 0.0415 * z)
        val bl = ch(0.0557 * x - 0.2040 * y + 1.0570 * z)
        return (alpha shl 24) or (r shl 16) or (g shl 8) or bl
    }
}

/** Espacios de color: sRGB lineal ↔ XYZ (D65) y adaptación cromática de Bradford. */
object ColorSpace {
    fun lin(c: Int) = (c / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    fun gam(v: Double) = (if (v <= 0.0031308) 12.92 * v else 1.055 * v.coerceAtLeast(0.0).pow(1 / 2.4) - 0.055)
    val RGB2XYZ = doubleArrayOf(0.4124, 0.3576, 0.1805, 0.2126, 0.7152, 0.0722, 0.0193, 0.1192, 0.9505)
    /** Inversa exacta de [RGB2XYZ] (la ida y vuelta no introduce error). */
    val XYZ2RGB: DoubleArray = inv3(RGB2XYZ)

    fun inv3(m: DoubleArray): DoubleArray {
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        return doubleArrayOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det)
    }
    /** Matriz de Bradford (Lam, 1985; usada por ICC para la adaptación cromática). */
    val BRADFORD = doubleArrayOf(0.8951, 0.2664, -0.1614, -0.7502, 1.7135, 0.0367, 0.0389, -0.0685, 1.0296)
    val BRADFORD_INV: DoubleArray = inv3(BRADFORD)

    fun mul(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i -> (0 until 3).sumOf { k -> a[i / 3 * 3 + k] * b[k * 3 + i % 3] } }
    fun mulv(a: DoubleArray, v: DoubleArray) = DoubleArray(3) { i -> a[i * 3] * v[0] + a[i * 3 + 1] * v[1] + a[i * 3 + 2] * v[2] }

    /**
     * Matriz en RGB lineal que lleva el color [src] (RGB lineal) a [dst] con una adaptación de von
     * Kries en el espacio de conos de Bradford: corrige iluminante (balance de blancos) y exposición
     * sin las desviaciones de tono de un escalado directo en RGB.
     */
    fun bradford(src: DoubleArray, dst: DoubleArray): DoubleArray {
        val cs = mulv(BRADFORD, mulv(RGB2XYZ, src)); val cd = mulv(BRADFORD, mulv(RGB2XYZ, dst))
        val d = DoubleArray(9).also { for (k in 0 until 3) it[k * 4] = (cd[k] / cs[k].coerceAtLeast(1e-6)).coerceIn(0.2, 5.0) }
        return mul(XYZ2RGB, mul(BRADFORD_INV, mul(d, mul(BRADFORD, RGB2XYZ))))
    }

    /** Diferencia de color CIEDE2000 (Sharma, Wu y Dalal, 2005). */
    fun deltaE00(l1: Double, a1: Double, b1: Double, l2: Double, a2: Double, b2: Double): Double {
        val c1 = hypot(a1, b1); val c2 = hypot(a2, b2); val cm = (c1 + c2) / 2
        val g = 0.5 * (1 - sqrt(cm.pow(7) / (cm.pow(7) + 25.0.pow(7))))
        val ap1 = (1 + g) * a1; val ap2 = (1 + g) * a2
        val cp1 = hypot(ap1, b1); val cp2 = hypot(ap2, b2)
        fun hue(b: Double, a: Double) = if (b == 0.0 && a == 0.0) 0.0 else Math.toDegrees(atan2(b, a)).let { if (it < 0) it + 360 else it }
        val hp1 = hue(b1, ap1); val hp2 = hue(b2, ap2)
        val dL = l2 - l1; val dC = cp2 - cp1
        var dh = hp2 - hp1
        if (cp1 * cp2 == 0.0) dh = 0.0 else if (dh > 180) dh -= 360 else if (dh < -180) dh += 360
        val dH = 2 * sqrt(cp1 * cp2) * sin(Math.toRadians(dh / 2))
        val lm = (l1 + l2) / 2; val cpm = (cp1 + cp2) / 2
        var hm = hp1 + hp2
        if (cp1 * cp2 != 0.0) hm = if (abs(hp1 - hp2) > 180) (if (hp1 + hp2 < 360) (hp1 + hp2 + 360) / 2 else (hp1 + hp2 - 360) / 2) else (hp1 + hp2) / 2
        val t = 1 - 0.17 * cos(Math.toRadians(hm - 30)) + 0.24 * cos(Math.toRadians(2 * hm)) + 0.32 * cos(Math.toRadians(3 * hm + 6)) - 0.20 * cos(Math.toRadians(4 * hm - 63))
        val dTheta = 30 * exp(-((hm - 275) / 25).pow(2))
        val rc = 2 * sqrt(cpm.pow(7) / (cpm.pow(7) + 25.0.pow(7)))
        val sl = 1 + 0.015 * (lm - 50).pow(2) / sqrt(20 + (lm - 50).pow(2))
        val sc = 1 + 0.045 * cpm; val sh = 1 + 0.015 * cpm * t
        val rt = -sin(Math.toRadians(2 * dTheta)) * rc
        return sqrt((dL / sl).pow(2) + (dC / sc).pow(2) + (dH / sh).pow(2) + rt * (dC / sc) * (dH / sh))
    }
}

/** Estadísticos de color de una región de piel: media recortada en RGB lineal, CIELAB y cuantiles de L*. */
class SkinStats(
    val meanL: Double, val stdL: Double, val meanA: Double, val meanB: Double, val n: Int,
    /** Media recortada (sin brillos ni sombras) en RGB lineal. */
    val meanLin: DoubleArray = DoubleArray(3),
    /** L* en los percentiles 0, 5, …, 100 (para igualar la gradación tonal). */
    val quantilesL: DoubleArray = DoubleArray(0),
)

/**
 * Igualación de color de "después" con "antes":
 * 1. **Adaptación cromática de Bradford** (von Kries en espacio de conos; ICC) a partir de la piel
 *    (se supone la misma reflectancia en ambas sesiones) o de un punto neutro (carta gris o blanca,
 *    la práctica de referencia en fotografía clínica): corrige balance de blancos y exposición.
 * 2. **Gradación tonal de la piel** por cuantiles de L* (5–95 %), monótona: contraste y gamma.
 * Evaluación: ΔE2000 entre los colores medios de la piel antes y después del ajuste.
 */
class ColorAdjust(
    /** Matriz 3×3 en RGB lineal. */
    private val matrix: DoubleArray,
    /** Curva de L* (101 valores para L = 0…100) o null. */
    private val lut: DoubleArray?,
    val exposureEv: Double,
    /** Cambio de balance de la piel (Δa*: tinte verde–magenta; Δb*: temperatura azul–amarillo). */
    val tint: Double, val temperature: Double,
    val method: String,
) {
    /**
     * Aplica el ajuste con tablas: sRGB → lineal (256), lineal → sRGB (8192) y, con curva de L*, una tabla 3D
     * de 33³ nodos (el ajuste exacto en cada nodo, interpolación trilineal). Antes eran unas 12 potencias o
     * raíces y una reserva de memoria por píxel. Diferencia con [applyExact]: como mucho 2–3 niveles de 255.
     */
    fun apply(rgb: IntArray): IntArray {
        val cube = lut?.let { cube() }
        val m = matrix
        return IntArray(rgb.size) { i ->
            val c = rgb[i]
            val a = c and 0xFF000000.toInt()
            if (cube != null) a or cube.at((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
            else {
                val r0 = LIN[(c shr 16) and 0xFF]; val g0 = LIN[(c shr 8) and 0xFF]; val b0 = LIN[c and 0xFF]
                val r = gamByte(m[0] * r0 + m[1] * g0 + m[2] * b0)
                val g = gamByte(m[3] * r0 + m[4] * g0 + m[5] * b0)
                val b = gamByte(m[6] * r0 + m[7] * g0 + m[8] * b0)
                a or (r shl 16) or (g shl 8) or b
            }
        }
    }

    /** El ajuste exacto de un píxel (referencia de [apply] y nodos de la tabla 3D). */
    fun applyExact(rgb: IntArray): IntArray {
        val lab = DoubleArray(3); val v = DoubleArray(3)
        return IntArray(rgb.size) { i ->
            val c = rgb[i]
            v[0] = ColorSpace.lin((c shr 16) and 0xFF); v[1] = ColorSpace.lin((c shr 8) and 0xFF); v[2] = ColorSpace.lin(c and 0xFF)
            val o = ColorSpace.mulv(matrix, v)
            val r = (ColorSpace.gam(o[0].coerceIn(0.0, 1.0)) * 255 + 0.5).toInt().coerceIn(0, 255)
            val g = (ColorSpace.gam(o[1].coerceIn(0.0, 1.0)) * 255 + 0.5).toInt().coerceIn(0, 255)
            val b = (ColorSpace.gam(o[2].coerceIn(0.0, 1.0)) * 255 + 0.5).toInt().coerceIn(0, 255)
            val px = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            if (lut == null) px else {
                Lab.fromRgb(px, lab)
                val l = lab[0].coerceIn(0.0, 100.0); val k = l.toInt().coerceAtMost(99); val f = l - k
                Lab.toRgb(lut[k] + (lut[k + 1] - lut[k]) * f, lab[1], lab[2], (c ushr 24) and 0xFF)
            }
        }
    }

    @Volatile private var cubeCache: Cube? = null
    private fun cube(): Cube = cubeCache ?: Cube.of { r, g, b -> applyExact(intArrayOf((0xFF shl 24) or (r shl 16) or (g shl 8) or b))[0] and 0xFFFFFF }.also { cubeCache = it }

    /** Tabla 3D RGB → RGB de [N]³ nodos con interpolación trilineal. */
    private class Cube(private val r: FloatArray, private val g: FloatArray, private val b: FloatArray) {
        fun at(ri: Int, gi: Int, bi: Int): Int {
            val fx = ri * (N - 1) / 255f; val fy = gi * (N - 1) / 255f; val fz = bi * (N - 1) / 255f
            val x0 = fx.toInt().coerceAtMost(N - 2); val y0 = fy.toInt().coerceAtMost(N - 2); val z0 = fz.toInt().coerceAtMost(N - 2)
            val dx = fx - x0; val dy = fy - y0; val dz = fz - z0
            fun ch(t: FloatArray): Int {
                fun v(x: Int, y: Int, z: Int) = t[(x * N + y) * N + z]
                val c00 = v(x0, y0, z0) + (v(x0 + 1, y0, z0) - v(x0, y0, z0)) * dx
                val c10 = v(x0, y0 + 1, z0) + (v(x0 + 1, y0 + 1, z0) - v(x0, y0 + 1, z0)) * dx
                val c01 = v(x0, y0, z0 + 1) + (v(x0 + 1, y0, z0 + 1) - v(x0, y0, z0 + 1)) * dx
                val c11 = v(x0, y0 + 1, z0 + 1) + (v(x0 + 1, y0 + 1, z0 + 1) - v(x0, y0 + 1, z0 + 1)) * dx
                val c0 = c00 + (c10 - c00) * dy; val c1 = c01 + (c11 - c01) * dy
                return (c0 + (c1 - c0) * dz + 0.5f).toInt().coerceIn(0, 255)
            }
            return (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        }

        companion object {
            const val N = 33
            fun of(f: (Int, Int, Int) -> Int): Cube {
                val n3 = N * N * N; val r = FloatArray(n3); val g = FloatArray(n3); val b = FloatArray(n3)
                for (x in 0 until N) for (y in 0 until N) for (z in 0 until N) {
                    val o = f(x * 255 / (N - 1), y * 255 / (N - 1), z * 255 / (N - 1)); val i = (x * N + y) * N + z
                    r[i] = ((o shr 16) and 0xFF).toFloat(); g[i] = ((o shr 8) and 0xFF).toFloat(); b[i] = (o and 0xFF).toFloat()
                }
                return Cube(r, g, b)
            }
        }
    }

    fun describe(): String = tr("%1\$s · exposición %2\$+.2f EV · temperatura %3\$+.1f · tinte %4\$+.1f", tr(method), exposureEv, temperature, tint)

    companion object {
        private val LIN = DoubleArray(256) { ColorSpace.lin(it) }
        private const val GAM_N = 8192
        private val GAM = IntArray(GAM_N + 1) { (ColorSpace.gam(it / GAM_N.toDouble()) * 255 + 0.5).toInt().coerceIn(0, 255) }
        private fun gamByte(v: Double): Int = GAM[(v.coerceIn(0.0, 1.0) * GAM_N + 0.5).toInt()]

        val IDENTITY = ColorAdjust(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0), null, 0.0, 0.0, 0.0, trn("sin cambios"))
    }
}

object ColorMatch {
    /**
     * Estadísticos de la piel en los píxeles de la máscara. Se descartan sombras y brillos
     * especulares (por debajo del percentil 5 y por encima del 95 de L*), que no son color de piel.
     */
    fun stats(rgb: IntArray, mask: BooleanArray): SkinStats? {
        val lab = DoubleArray(3)
        val idx = ArrayList<Int>(); val ls = ArrayList<Double>()
        for (i in rgb.indices) if (mask[i]) {
            Lab.fromRgb(rgb[i], lab)
            if (lab[0] < 5 || lab[0] > 98) continue
            idx += i; ls += lab[0]
        }
        if (ls.size < 30) return null
        val sorted = ls.sorted()
        val lo = sorted[(sorted.size * 0.05).toInt()]; val hi = sorted[((sorted.size - 1) * 0.95).toInt()]
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var sa = 0.0; var sbb = 0.0; var sl = 0.0; var n = 0
        val keep = ArrayList<Double>()
        for ((j, i) in idx.withIndex()) {
            val l = ls[j]; if (l < lo || l > hi) continue
            val c = rgb[i]
            sr += ColorSpace.lin((c shr 16) and 0xFF); sg += ColorSpace.lin((c shr 8) and 0xFF); sb += ColorSpace.lin(c and 0xFF)
            Lab.fromRgb(c, lab); sl += lab[0]; sa += lab[1]; sbb += lab[2]; keep += l; n++
        }
        if (n < 20) return null
        val m = sl / n; val sd = sqrt(keep.sumOf { (it - m) * (it - m) } / n)
        val q = DoubleArray(21) { k -> sorted[((sorted.size - 1) * k / 20.0).toInt()] }
        return SkinStats(m, sd.coerceAtLeast(0.5), sa / n, sbb / n, n, doubleArrayOf(sr / n, sg / n, sb / n), q)
    }

    /** Región circular (punto de referencia) en coordenadas normalizadas por el ancho. */
    fun patch(w: Int, h: Int, center: Pt, radius: Float = 0.04f): BooleanArray {
        val cx = center.x * w; val cy = center.y * w; val r = radius * w
        return BooleanArray(w * h) { i -> val x = i % w - cx; val y = i / w - cy; x * x + y * y <= r * r }
    }

    /** Ajuste que lleva la piel [src] ("después") a la de [dst] ("antes"): Bradford + gradación de L*. */
    fun fit(src: SkinStats, dst: SkinStats, tone: Boolean = true): ColorAdjust {
        val m = ColorSpace.bradford(src.meanLin, dst.meanLin)
        val ev = ln(lum(dst.meanLin) / lum(src.meanLin).coerceAtLeast(1e-6)) / ln(2.0)
        // gradación: cuantiles de L* de la piel tras la adaptación (estimados con la misma matriz)
        val lut = if (!tone || src.quantilesL.size != 21 || dst.quantilesL.size != 21) null else {
            val k = lum(dst.meanLin) / lum(src.meanLin).coerceAtLeast(1e-6)
            val qs = DoubleArray(21) { i -> lStar(yOf(src.quantilesL[i]) * k) }
            toneCurve(qs, dst.quantilesL)
        }
        return ColorAdjust(m, lut, ev, dst.meanA - src.meanA, dst.meanB - src.meanB,
            if (lut != null) trn("Bradford + gradación de piel") else "Bradford")
    }

    /** Ajuste por un punto neutro (carta gris o blanca) en ambas fotos: solo adaptación cromática. */
    fun fitNeutral(src: SkinStats, dst: SkinStats): ColorAdjust {
        val m = ColorSpace.bradford(src.meanLin, dst.meanLin)
        val ev = ln(lum(dst.meanLin) / lum(src.meanLin).coerceAtLeast(1e-6)) / ln(2.0)
        return ColorAdjust(m, null, ev, dst.meanA - src.meanA, dst.meanB - src.meanB, trn("Bradford (punto neutro)"))
    }

    /** ΔE2000 entre los colores medios de dos regiones de piel. */
    fun deltaE(a: SkinStats, b: SkinStats) = ColorSpace.deltaE00(a.meanL, a.meanA, a.meanB, b.meanL, b.meanA, b.meanB)

    /** Método anterior (Reinhard et al., 2001, en CIELAB), para comparación. */
    fun fitReinhard(src: SkinStats, dst: SkinStats): ColorAdjustLab {
        val dl = dst.meanL - src.meanL; val da = dst.meanA - src.meanA; val db = dst.meanB - src.meanB
        val sc = (dst.stdL / src.stdL).coerceIn(0.6, 1.6)
        val lut = DoubleArray(101) { l -> (dst.meanL + (l - src.meanL) * sc).coerceIn(0.0, 100.0) }
        return ColorAdjustLab(lut, da, db)
    }

    private fun lum(v: DoubleArray) = 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2]
    private fun yOf(l: Double) = if (l > 8) ((l + 16) / 116).pow(3) else l / 903.3
    private fun lStar(y: Double) = if (y > 216.0 / 24389) 116 * cbrt(y) - 16 else 903.3 * y

    /** Curva monótona de L* que lleva los cuantiles [from] (5–95 %) a [to]; identidad fuera, con transición. */
    private fun toneCurve(from: DoubleArray, to: DoubleArray): DoubleArray {
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        for (k in 1..19) {
            val x = from[k]; val y = to[k]
            if (xs.isEmpty() || (x > xs.last() + 0.3 && y >= ys.last())) { xs += x; ys += y }
        }
        if (xs.size < 3) return DoubleArray(101) { it.toDouble() }
        return DoubleArray(101) { l0 ->
            val l = l0.toDouble()
            val y = when {
                l <= xs.first() -> ys.first() * l / xs.first().coerceAtLeast(1e-3)              // hacia el negro
                l >= xs.last() -> ys.last() + (100 - ys.last()) * (l - xs.last()) / (100 - xs.last()).coerceAtLeast(1e-3)
                else -> { val j = xs.indexOfLast { it <= l }; ys[j] + (ys[j + 1] - ys[j]) * (l - xs[j]) / (xs[j + 1] - xs[j]) }
            }
            y.coerceIn(0.0, 100.0)
        }
    }
}

/** Ajuste en CIELAB: curva de L* y desplazamiento de a* y b*. */
class ColorAdjustLab(private val lut: DoubleArray, private val da: Double, private val db: Double) {
    fun apply(rgb: IntArray): IntArray {
        val lab = DoubleArray(3)
        return IntArray(rgb.size) { i ->
            Lab.fromRgb(rgb[i], lab)
            val l = lab[0].coerceIn(0.0, 100.0); val k = l.toInt().coerceAtMost(99); val f = l - k
            Lab.toRgb(lut[k] + (lut[k + 1] - lut[k]) * f, lab[1] + da, lab[2] + db, (rgb[i] ushr 24) and 0xFF)
        }
    }
}
