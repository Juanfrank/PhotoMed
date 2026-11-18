package com.photomed.app.align

import kotlin.math.cbrt
import kotlin.math.ln
import kotlin.math.pow
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

/** Estadísticos de color de una región de piel (CIELAB). */
class SkinStats(val meanL: Double, val stdL: Double, val meanA: Double, val meanB: Double, val n: Int,
                /** Muestras de L* (para calcular con exactitud la dispersión tras la gamma). */
                val samplesL: DoubleArray = DoubleArray(0))

/**
 * Ajuste de color para igualar la piel de "después" con la de "antes" (transferencia de color en
 * CIELAB, Reinhard et al., IEEE CG&A 2001), descompuesto en parámetros fotográficos:
 * gamma (tonos medios), contraste y brillo (L*), temperatura (b*: amarillo–azul) y tinte (a*).
 */
data class ColorAdjust(val gamma: Double, val contrast: Double, val brightness: Double,
                       val temperature: Double, val tint: Double, private val muSrc: Double, private val muDst: Double) {

    fun apply(rgb: IntArray): IntArray {
        val lab = DoubleArray(3)
        return IntArray(rgb.size) { i ->
            val c = rgb[i]
            Lab.fromRgb(c, lab)
            val lg = 100.0 * (lab[0].coerceIn(0.0, 100.0) / 100.0).pow(gamma)
            val l2 = muDst + (lg - muSrc) * contrast
            Lab.toRgb(l2, lab[1] + tint, lab[2] + temperature, (c ushr 24) and 0xFF)
        }
    }

    fun describe(): String = "brillo %+.1f · contraste ×%.2f · gamma %.2f · temperatura %+.1f · tinte %+.1f"
        .format(brightness, contrast, gamma, temperature, tint)

    companion object {
        val IDENTITY = ColorAdjust(1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

object ColorMatch {
    /** Estadísticos de la piel en los píxeles seleccionados (máscara) — se ignoran sombras y brillos extremos. */
    fun stats(rgb: IntArray, mask: BooleanArray): SkinStats? {
        val lab = DoubleArray(3)
        val ls = ArrayList<Double>(); var sa = 0.0; var sb = 0.0
        for (i in rgb.indices) if (mask[i]) {
            Lab.fromRgb(rgb[i], lab)
            if (lab[0] < 8 || lab[0] > 97) continue
            ls += lab[0]; sa += lab[1]; sb += lab[2]
        }
        if (ls.size < 30) return null
        val m = ls.average(); val sd = sqrt(ls.sumOf { (it - m) * (it - m) } / ls.size)
        return SkinStats(m, sd.coerceAtLeast(0.5), sa / ls.size, sb / ls.size, ls.size, ls.toDoubleArray())
    }

    /** Región circular (punto de referencia) en coordenadas normalizadas por el ancho. */
    fun patch(w: Int, h: Int, center: Pt, radius: Float = 0.04f): BooleanArray {
        val cx = center.x * w; val cy = center.y * w; val r = radius * w
        return BooleanArray(w * h) { i -> val x = i % w - cx; val y = i / w - cy; x * x + y * y <= r * r }
    }

    /** Ajuste que lleva la piel [src] ("después") a la de [dst] ("antes"). */
    fun fit(src: SkinStats, dst: SkinStats, srcSamplesL: DoubleArray? = src.samplesL.takeIf { it.isNotEmpty() }): ColorAdjust {
        val gamma = (ln(dst.meanL / 100) / ln(src.meanL / 100)).let { if (it.isFinite()) it else 1.0 }.coerceIn(0.6, 1.6)
        // media y desviación de L tras la gamma (aprox. de primer orden si no se dan muestras)
        val muG = srcSamplesL?.map { 100 * (it / 100).pow(gamma) }?.average() ?: (100 * (src.meanL / 100).pow(gamma))
        val sdG = srcSamplesL?.let { s -> val g = s.map { 100 * (it / 100).pow(gamma) }; sqrt(g.sumOf { (it - muG) * (it - muG) } / g.size) }
            ?: (src.stdL * gamma * (src.meanL / 100).pow(gamma - 1))
        val contrast = (dst.stdL / sdG.coerceAtLeast(0.5)).coerceIn(0.6, 1.6)
        return ColorAdjust(gamma, contrast, dst.meanL - src.meanL, dst.meanB - src.meanB, dst.meanA - src.meanA, muG, dst.meanL)
    }
}
