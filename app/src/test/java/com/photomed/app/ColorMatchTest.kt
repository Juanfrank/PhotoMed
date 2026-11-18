package com.photomed.app

import android.graphics.BitmapFactory
import com.photomed.app.align.Aligner
import com.photomed.app.align.ColorMatch
import com.photomed.app.align.ColorSpace
import com.photomed.app.align.Lab
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.pow

/**
 * Igualación del color de la piel. Evaluación: cambios de sesión sintéticos y conocidos (luz cálida
 * o fría, exposición, contraste) sobre rostros reales; se mide la ΔE2000 píxel a píxel en la piel
 * entre la foto original y la corregida, para el método actual (Bradford + gradación) y el
 * anterior (Reinhard en CIELAB).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorMatchTest {
    @Test
    fun deltaE2000MatchesReferenceData() {
        // Sharma, Wu y Dalal (2005), pares de prueba 1, 2 y 7
        assertEquals(2.0425, ColorSpace.deltaE00(50.0, 2.6772, -79.7751, 50.0, 0.0, -82.7485), 1e-3)
        assertEquals(2.8615, ColorSpace.deltaE00(50.0, 3.1571, -77.2803, 50.0, 0.0, -82.7485), 1e-3)
        assertEquals(2.3669, ColorSpace.deltaE00(50.0, 0.0, 0.0, 50.0, -1.0, 2.0), 1e-3)
    }

    @Test
    fun bradfordMapsSourceToTarget() {
        val src = doubleArrayOf(0.30, 0.22, 0.12); val dst = doubleArrayOf(0.25, 0.21, 0.18)
        val m = ColorSpace.bradford(src, dst)
        val o = ColorSpace.mulv(m, src)
        for (k in 0 until 3) assertEquals(dst[k], o[k], 1e-5)
    }

    private data class Face(val name: String, val rgb: IntArray, val w: Int, val h: Int, val pts: Map<String, Pt>)

    private fun faces(): List<Face> {
        val out = ArrayList<Face>()
        fun load(json: String?, img: (String) -> String) {
            val o = JSONObject(json ?: return)
            for (k in o.keys()) {
                val p = o.getJSONObject(k).optJSONObject("pts") ?: continue
                val pts = p.keys().asSequence().associateWith { kk -> p.getJSONArray(kk).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
                val bmp = javaClass.getResourceAsStream(img(k))?.let { BitmapFactory.decodeStream(it) } ?: continue
                if (bmp.width.toFloat() / bmp.height !in 0.70f..0.80f) continue
                val rgb = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                out += Face(k, rgb, bmp.width, bmp.height, pts)
            }
        }
        load(javaClass.getResourceAsStream("/demo/faces.json")?.bufferedReader()?.readText()) { "/demo/$it.jpg" }
        load(javaClass.getResourceAsStream("/stock/stock_faces.json")?.bufferedReader()?.readText()) { "/stock/$it.jpg" }
        return out.filter { !it.name.contains("_r1") && !it.name.contains("_r-1") }   // sin las variantes giradas
    }

    /** Cambio de sesión: ganancias por canal en RGB lineal (iluminante y exposición) + curva de L*. */
    private fun session(rgb: IntArray, gains: DoubleArray, gammaL: Double): IntArray {
        val lab = DoubleArray(3)
        return IntArray(rgb.size) { i ->
            val c = rgb[i]
            val r = ColorSpace.gam((ColorSpace.lin((c shr 16) and 0xFF) * gains[0]).coerceIn(0.0, 1.0))
            val g = ColorSpace.gam((ColorSpace.lin((c shr 8) and 0xFF) * gains[1]).coerceIn(0.0, 1.0))
            val b = ColorSpace.gam((ColorSpace.lin(c and 0xFF) * gains[2]).coerceIn(0.0, 1.0))
            val px = (0xFF shl 24) or ((r * 255 + 0.5).toInt().coerceIn(0, 255) shl 16) or ((g * 255 + 0.5).toInt().coerceIn(0, 255) shl 8) or (b * 255 + 0.5).toInt().coerceIn(0, 255)
            Lab.fromRgb(px, lab)
            Lab.toRgb(100 * (lab[0].coerceIn(0.0, 100.0) / 100).pow(gammaL), lab[1], lab[2], 0xFF)
        }
    }

    /** ΔE2000 medio píxel a píxel en la máscara. */
    private fun pixelDeltaE(a: IntArray, b: IntArray, mask: BooleanArray): Double {
        val la = DoubleArray(3); val lb = DoubleArray(3); var s = 0.0; var n = 0
        for (i in a.indices) if (mask[i]) {
            Lab.fromRgb(a[i], la); Lab.fromRgb(b[i], lb)
            s += ColorSpace.deltaE00(la[0], la[1], la[2], lb[0], lb[1], lb[2]); n++
        }
        return s / n
    }

    @Test
    fun recoversSessionChangesBetterThanReinhard() {
        val fs = faces()
        assertTrue("rostros de prueba", fs.size >= 3)
        val cases = listOf(
            "luz cálida, −0,5 EV" to (doubleArrayOf(1.10, 0.78, 0.48) to 1.0),
            "luz fría, +0,4 EV" to (doubleArrayOf(1.05, 1.32, 1.70) to 1.0),
            "flash neutro, más contraste" to (doubleArrayOf(1.25, 1.25, 1.25) to 1.15),
            "fluorescente verdoso, menos contraste" to (doubleArrayOf(0.85, 1.05, 0.80) to 0.88),
        )
        val report = StringBuilder()
        var sumNew = 0.0; var sumOld = 0.0; var sumRaw = 0.0; var n = 0
        for ((label, cfg) in cases) {
            var cNew = 0.0; var cOld = 0.0; var cRaw = 0.0
            for (f in fs) {
                val skin = com.photomed.app.align.faceSkinMask(f.pts, f.w, f.h) ?: continue
                val sess = session(f.rgb, cfg.first, cfg.second)
                val ref = ColorMatch.stats(f.rgb, skin)!!; val cur = ColorMatch.stats(sess, skin)!!
                val raw = pixelDeltaE(f.rgb, sess, skin)
                val new = pixelDeltaE(f.rgb, ColorMatch.fit(cur, ref).apply(sess), skin)
                val old = pixelDeltaE(f.rgb, ColorMatch.fitReinhard(cur, ref).apply(sess), skin)
                cRaw += raw; cNew += new; cOld += old
                sumRaw += raw; sumNew += new; sumOld += old; n++
            }
            report.append("%s: sin corregir %.2f · Reinhard %.2f · Bradford+gradación %.2f\n".format(label, cRaw / fs.size, cOld / fs.size, cNew / fs.size))
        }
        report.append("Media (%d casos): sin corregir %.2f · Reinhard %.2f · Bradford+gradación %.2f".format(n, sumRaw / n, sumOld / n, sumNew / n))
        println("ΔE2000 piel (píxel a píxel)\n$report")
        java.io.File("build/reports").mkdirs(); java.io.File("build/reports/color_match.txt").writeText(report.toString())
        assertTrue("mejor que Reinhard", sumNew < sumOld)
        assertTrue("ΔE00 medio tras corregir < 2,5 (apenas perceptible)", sumNew / n < 2.5)
    }
}
