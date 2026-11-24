package com.photomed.app

import com.photomed.app.align.FaceLabels
import com.photomed.app.align.HairDensity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Densidad capilar aparente ([HairDensity]).
 *
 * Validación (si están las fotos, que no se suben al repositorio): fotos cenitales de los conjuntos de
 * pérdida de pelo de UniData (hombres y mujeres, con máscara anotada: blanco = pelo, gris = aclarado,
 * negro = calvo o fondo) y de la escala de Norwood; su segmentación, de `tools/eval/hair_labels.py`
 * (`stock/zones/hairval/labels.json`). Informe en `build/reports/hair_density.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HairDensityTest {
    private val root = File("src/test/resources/stock/zones")

    private fun scaled(f: File, w: Int, h: Int): IntArray {
        val src = android.graphics.BitmapFactory.decodeFile(f.path)
        val b = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
        return IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
    }

    /** Cobertura anotada (pelo 1, aclarado 0,5, calvo 0) dentro de la región analizada. */
    private fun maskCoverage(mask: File, roi: BooleanArray, w: Int, h: Int): Float {
        val m = scaled(mask, w, h)
        var s = 0.0; var n = 0
        for (i in roi.indices) if (roi[i]) {
            val v = m[i] and 0xFF
            s += if (v > 200) 1.0 else if (v > 60) 0.5 else 0.0; n++
        }
        return (s / n).toFloat()
    }

    @Test
    fun validatedAgainstAnnotatedMasks() {
        val lf = File(root, "hairval/labels.json")
        assumeTrue("sin fotos de validación", lf.exists())
        val js = JSONObject(lf.readText())
        val rep = StringBuilder("# Densidad capilar aparente (vista cenital)\n\n| foto | visible | centro | raya / lados | grados | anotado |\n|---|---|---|---|---|---|\n")
        val est = ArrayList<Float>(); val gt = ArrayList<Float>()
        val center = HashMap<String, Float>()
        for (key in js.keys().asSequence().sorted()) {
            val top = key.contains("top", ignoreCase = true)
            if (!top) continue
            val o = js.getJSONObject(key); val w = o.getInt("w"); val h = o.getInt("h")
            val labels = FaceLabels(w, h, java.util.Base64.getDecoder().decode(o.getString("labels")))
            val px = scaled(File(root, key.replace("src_", "src_")), w, h)
            val r = HairDensity.analyze(px, w, h, labels, HairDensity.View.VERTEX) ?: continue
            center[key] = r.center
            val (dir, name) = key.split("/")
            val maskName = when (dir) {
                "src_menhair" -> "men_${name.substringBefore("__")}_mask_top.png"
                "src_womenhair" -> "women_${name.substringBefore("__")}_mask_top.png"
                else -> null
            }
            val g = maskName?.let { File(root, "hairval/$it") }?.takeIf { it.exists() }?.let { maskCoverage(it, r.roi, w, h) }
            if (g != null) { est += 1 - r.visible; gt += g }
            rep.append("| $key | %.0f %% | %.0f %% | %s | %s | %s |\n".format(r.visible * 100, r.center * 100,
                "%.0f %% / %.0f %%".format(r.midline * 100, r.lateral * 100),
                HairDensity.grades(r, HairDensity.View.VERTEX), g?.let { "cobertura %.0f %%".format(it * 100) } ?: ""))
        }
        assumeTrue(est.size >= 6)
        val n = est.size
        val me = est.average(); val mg = gt.average()
        val cov = (0 until n).sumOf { (est[it] - me) * (gt[it] - mg) }
        val r = cov / sqrt((0 until n).sumOf { (est[it] - me).let { d -> d * d } } * (0 until n).sumOf { (gt[it] - mg).let { d -> d * d } })
        val mae = (0 until n).map { abs(est[it] - gt[it]) }.average()
        rep.append("\nCobertura estimada frente a la anotada (%d fotos): r = %.3f, error medio = %.3f\n".format(n, r, mae))
        File("build/reports").mkdirs(); File("build/reports/hair_density.md").writeText(rep.toString())
        println(rep)
        assertTrue("r=$r", r >= 0.85)
        assertTrue("mae=$mae", mae <= 0.15)
        // los hombres con calvicie de la coronilla (1, 2, 4) más visibles en el centro que las mujeres con raya
        val men = listOf(1, 2, 4).mapNotNull { center["src_menhair/${it}__top.png"] }
        val women = (1..5).mapNotNull { center["src_womenhair/${it}__top.png"] }
        assertTrue("$men vs $women", men.isNotEmpty() && women.isNotEmpty() && men.min() > women.max())
    }

    /** Sintético: cabeza con pelo oscuro uniforme (sin raya), con raya central y con la coronilla calva. */
    @Test
    fun syntheticPartAndCrown() {
        val w = 200; val h = 240
        val hair = 0xFF201810.toInt(); val skin = 0xFFD8A888.toInt(); val bg = 0xFF6090C0.toInt()
        fun head(paint: (Int, Int) -> Boolean): Pair<IntArray, FaceLabels> {
            val px = IntArray(w * h); val lb = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val inHead = ((x - 100f) / 80f).let { it * it } + ((y - 120f) / 100f).let { it * it } <= 1f
                if (!inHead) { px[i] = bg; continue }
                val s = paint(x, y)
                px[i] = if (s) skin else hair
                lb[i] = (if (s) FaceLabels.BODY_SKIN else FaceLabels.HAIR).toByte()
            }
            return px to FaceLabels(w, h, lb)
        }
        val (p0, l0) = head { _, _ -> false }
        val full = HairDensity.analyze(p0, w, h, l0, HairDensity.View.VERTEX)!!
        assertTrue(full.visible < 0.02f)
        assertTrue(full.midline < 0.02f)
        assertEquals(0, HairDensity.basp(full.center))
        // raya de 6 px: la franja central más visible que los lados; Sinclair sube
        val (p1, l1) = head { x, _ -> x in 97..102 }
        val part = HairDensity.analyze(p1, w, h, l1, HairDensity.View.VERTEX)!!
        assertTrue("raya ${part.midline} lados ${part.lateral}", part.midline - part.lateral > HairDensity.PART_WIDE)
        assertTrue(HairDensity.sinclair(part.center, part.midline - part.lateral) > HairDensity.sinclair(full.center, full.midline - full.lateral))
        assertEquals(2, part.strip.size)
        // coronilla calva: círculo central
        val (p2, l2) = head { x, y -> ((x - 100f) * (x - 100f) + (y - 120f) * (y - 120f)) < 45f * 45f }
        val crown = HairDensity.analyze(p2, w, h, l2, HairDensity.View.VERTEX)!!
        assertTrue("centro ${crown.center}", crown.center > 0.9f)
        assertEquals(3, HairDensity.basp(crown.center))
        assertTrue(HairDensity.grades(crown, HairDensity.View.VERTEX).startsWith("BASP V3"))
        assertTrue(crown.cells.isNotEmpty())
        // en el visor: contorno con las medidas y los grados, celdas rellenas, franja de la raya
        val lines = com.photomed.app.align.PhotoAnnotator.hairLinesOf(crown, HairDensity.View.VERTEX)
        val head = lines.first { it.metrics != null }
        assertEquals(com.photomed.app.align.AnnotLayer.SCALP_MAP, head.layer)
        assertTrue(head.metrics!!.type!!.startsWith("BASP V3"))
        assertTrue(head.metrics!!.features.any { it.first.startsWith("Cuero cabelludo visible") && it.second > 15f })
        assertTrue(lines.any { it.filled })
        assertEquals(2, lines.count { it.layer == com.photomed.app.align.AnnotLayer.HAIR_PART })
    }

    @Test
    fun baspBasicFromHairline() {
        assertEquals("L", HairDensity.baspBasic(60f, 63f))
        assertEquals("M1", HairDensity.baspBasic(60f, 75f))
        assertEquals("M3", HairDensity.baspBasic(60f, 100f))
        assertEquals("C2", HairDensity.baspBasic(95f, 98f))
        assertEquals("III", HairDensity.norwoodOfBasic("M2"))
    }
}
