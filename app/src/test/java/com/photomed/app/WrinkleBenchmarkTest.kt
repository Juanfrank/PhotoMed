package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.FaceCrop
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.Pt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Benchmark de arrugas y surcos frente a anotación manual: FFHQ-Wrinkle (Moon, Chung, Jang, ICPR 2024;
 * 1 000 máscaras de 3 anotadores; aquí una muestra de 100). El motor de la app (FaceCrop.annotate, el
 * mismo que en el teléfono) se compara con la máscara manual con tolerancia de [TOL] px (fotos de 1024):
 * - precisión: fracción de lo trazado por el motor a ≤ TOL de una arruga marcada;
 * - exhaustividad: fracción de lo marcado a mano a ≤ TOL de un trazo del motor;
 * por región (frente, ceño, patas de gallo, nasogenianos) y en total. Datos: tools/eval/bench_wrinkles.py.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WrinkleBenchmarkTest {
    private val TOL = 8f

    /** Distancia (px, aproximada con chaflán 3-4) de cada píxel al más cercano marcado. */
    private fun distance(mask: BooleanArray, w: Int, h: Int): FloatArray {
        val inf = 1e9f
        val d = FloatArray(w * h) { if (mask[it]) 0f else inf }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; var v = d[i]
            if (x > 0) v = min(v, d[i - 1] + 3); if (y > 0) { v = min(v, d[i - w] + 3); if (x > 0) v = min(v, d[i - w - 1] + 4); if (x < w - 1) v = min(v, d[i - w + 1] + 4) }
            d[i] = v
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x; var v = d[i]
            if (x < w - 1) v = min(v, d[i + 1] + 3); if (y < h - 1) { v = min(v, d[i + w] + 3); if (x < w - 1) v = min(v, d[i + w + 1] + 4); if (x > 0) v = min(v, d[i + w - 1] + 4) }
            d[i] = v
        }
        for (i in d.indices) d[i] /= 3f
        return d
    }

    private class Acc { var predIn = 0; var pred = 0; var gtIn = 0; var gt = 0
        fun p() = if (pred == 0) Float.NaN else predIn.toFloat() / pred
        fun r() = if (gt == 0) Float.NaN else gtIn.toFloat() / gt
        fun f() = p().let { p -> r().let { r -> if (p + r > 0) 2 * p * r / (p + r) else 0f } } }

    /** Muestra aleatoria de 100 fotos (semilla 7). */
    @Test
    fun wrinklesAgainstManualMasks() = bench("wrinkles", "muestra aleatoria")

    /** Partición de prueba del artículo (test_file_lists.txt del repositorio): comparable con su tabla 3. */
    @Test
    fun wrinklesOnPaperTestSplit() = bench("wrinkles_test", "partición de prueba del artículo")

    /** Equilibrio precisión / exhaustividad según la sensibilidad (partición del artículo). */
    @Test
    fun sensitivitySweep() {
        assumeTrue(System.getenv("PM_SWEEP") != null)
        for ((k, kc, cap) in listOf(Triple(1f, 1f, 1f), Triple(0.8f, 0.9f, 1.5f), Triple(0.65f, 0.8f, 2f), Triple(0.5f, 0.7f, 2.5f))) {
            com.photomed.app.align.WrinkleTuning.k = k; com.photomed.app.align.WrinkleTuning.kc = kc; com.photomed.app.align.WrinkleTuning.capK = cap
            com.photomed.app.align.PhotoAnnotator.clearCache()
            println("BENCH SENSIBILIDAD k=$k kc=$kc cap=$cap")
            bench("wrinkles_test", "sensibilidad $k/$kc/$cap", save = false)
        }
        com.photomed.app.align.WrinkleTuning.k = 1f; com.photomed.app.align.WrinkleTuning.kc = 1f; com.photomed.app.align.WrinkleTuning.capK = 1f
    }

    private fun bench(name: String, what: String, save: Boolean = true) {
        val dir = File("src/test/resources/bench/$name")
        val meshFile = File(dir, "mesh.json")
        assumeTrue("sin datos del benchmark (tools/eval/bench_wrinkles.py)", meshFile.exists())
        val o = JSONObject(meshFile.readText())
        val regions = listOf("Frente", "Ceño", "Patas de gallo", "Nasogenianos")
        val acc = regions.associateWith { Acc() } + ("Total (regiones)" to Acc())
        val perImage = JSONArray()
        val out = File("build/reports/bench").apply { mkdirs() }
        var n = 0
        // métrica estricta del artículo (píxel a píxel en toda la foto): trazos del motor de 5 px de ancho
        var tp = 0L; var fp = 0L; var fn = 0L
        for (k in o.keys().asSequence().sorted()) {
            val arr = o.getJSONArray(k)
            val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val img = BitmapFactory.decodeFile(File(dir, "$k.jpg").path)?.copy(Bitmap.Config.ARGB_8888, true) ?: continue
            val labBmp = BitmapFactory.decodeFile(File(dir, "${k}_labels.png").path) ?: continue
            val gtBmp = BitmapFactory.decodeFile(File(dir, "${k}_gt.png").path) ?: continue
            val w = img.width; val h = img.height
            val labPx = IntArray(labBmp.width * labBmp.height).also { labBmp.getPixels(it, 0, labBmp.width, 0, 0, labBmp.width, labBmp.height) }
            // segmentación de la foto completa llevada al recorte del análisis
            val lines = FaceCrop.annotate(mesh, img, { b, c ->
                FaceLabels(b.width, b.height, ByteArray(b.width * b.height) { i ->
                    val x = ((c.x0 + (i % b.width) / c.scale) * labBmp.width / w).toInt().coerceIn(0, labBmp.width - 1)
                    val y = ((c.y0 + (i / b.width) / c.scale) * labBmp.height / h).toInt().coerceIn(0, labBmp.height - 1)
                    Color.red(labPx[y * labBmp.width + x]).toByte()
                })
            })
            val wr = lines.filter { it.layer in setOf(AnnotLayer.FOREHEAD, AnnotLayer.GLABELLA, AnnotLayer.CROWS_FEET, AnnotLayer.FOLDS) && !it.estimated }
            // trazos del motor como muestras cada píxel
            val samples = ArrayList<Pair<Float, Float>>()
            for (l in wr) for (s in 1 until l.points.size) {
                val a = l.points[s - 1]; val b = l.points[s]
                val ax = a.x * w; val ay = a.y * w; val bx = b.x * w; val by = b.y * w
                val steps = max(1, hypot(bx - ax, by - ay).toInt())
                for (t in 0..steps) samples += (ax + (bx - ax) * t / steps) to (ay + (by - ay) * t / steps)
            }
            val gtPx = IntArray(w * h).also { gtBmp.getPixels(it, 0, w, 0, 0, w, h) }
            val gt = BooleanArray(w * h) { Color.red(gtPx[it]) > 127 }
            val pred = BooleanArray(w * h).also { m -> samples.forEach { (x, y) -> val xi = x.toInt(); val yi = y.toInt(); if (xi in 0 until w && yi in 0 until h) m[yi * w + xi] = true } }
            val dGt = distance(gt, w, h); val dPred = distance(pred, w, h)
            run {
                val thick = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val tc = Canvas(thick)
                val tpaint = Paint().apply { color = Color.WHITE; strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
                for (l in wr) for (s2 in 1 until l.points.size) tc.drawLine(l.points[s2 - 1].x * w, l.points[s2 - 1].y * w, l.points[s2].x * w, l.points[s2].y * w, tpaint)
                val tpx = IntArray(w * h).also { thick.getPixels(it, 0, w, 0, 0, w, h) }
                for (i in gt.indices) { val pv = Color.red(tpx[i]) > 127
                    if (pv && gt[i]) tp++ else if (pv) fp++ else if (gt[i]) fn++ }
            }
            // regiones a partir de la malla (px)
            fun P(i: Int) = mesh[i].x * w to mesh[i].y * w
            val browY = listOf(105, 334, 70, 300).minOf { P(it).second }
            val ipd = hypot(P(468).first - P(473).first, P(468).second - P(473).second)
            fun region(x: Float, y: Float): String? {
                val (xl, _) = P(70); val (xr, _) = P(300)
                val fl = min(xl, xr); val fr = max(xl, xr)
                if (y < browY - 0.05f * ipd && y > browY - 1.6f * ipd && x in fl..fr) return "Frente"
                val (gl, _) = P(107); val (gr, _) = P(336); val (_, ny) = P(6)
                if (x in min(gl, gr) - 0.1f * ipd..max(gl, gr) + 0.1f * ipd && y in browY - 0.25f * ipd..ny) return "Ceño"
                for (c in listOf(33, 263)) {
                    val (cx, cy) = P(c); val (ox, _) = P(if (c == 33) 133 else 362)
                    val out = if (cx < ox) -1f else 1f   // hacia fuera de la cara
                    val dx = (x - cx) * out
                    if (dx in 0.03f * ipd..0.75f * ipd && kotlin.math.abs(y - cy) < 0.45f * ipd) return "Patas de gallo"
                }
                val (mlx, mly) = P(61); val (mrx, mry) = P(291); val (_, sy) = P(2)
                val l = min(mlx, mrx); val r = max(mlx, mrx)
                val nearMouth = x in l - 0.45f * ipd..r + 0.45f * ipd && y in sy - 0.1f * ipd..max(mly, mry) + 0.45f * ipd
                val lips = x in l..r && y in sy + 0.1f * ipd..max(mly, mry) + 0.25f * ipd
                if (nearMouth && !lips && (x < l + 0.05f * ipd || x > r - 0.05f * ipd)) return "Nasogenianos"
                return null
            }
            val per = regions.associateWith { Acc() }
            for ((x, y) in samples) {
                val xi = x.toInt(); val yi = y.toInt(); if (xi !in 0 until w || yi !in 0 until h) continue
                val rg = region(x, y) ?: continue
                val hit = dGt[yi * w + xi] <= TOL
                for (a in listOf(acc.getValue(rg), acc.getValue("Total (regiones)"), per.getValue(rg))) { a.pred++; if (hit) a.predIn++ }
            }
            for (i in gt.indices) if (gt[i]) {
                val rg = region((i % w).toFloat(), (i / w).toFloat()) ?: continue
                val hit = dPred[i] <= TOL
                for (a in listOf(acc.getValue(rg), acc.getValue("Total (regiones)"), per.getValue(rg))) { a.gt++; if (hit) a.gtIn++ }
            }
            n++
            perImage.put(JSONObject().put("id", k).apply { per.forEach { (r, a) -> put(r, JSONObject().put("precision", a.p().toDouble().takeIf { !it.isNaN() }).put("recall", a.r().toDouble().takeIf { !it.isNaN() })) } })
            // superposiciones de las primeras (verde: manual; rojo: motor)
            if (save && n <= 12) {
                val ov = img.copy(Bitmap.Config.ARGB_8888, true); val c = Canvas(ov)
                for (i in gt.indices) if (gt[i]) ov.setPixel(i % w, i / w, Color.rgb(0, 230, 118))
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; strokeWidth = 3f; style = Paint.Style.STROKE }
                for (l in wr) for (s in 1 until l.points.size) c.drawLine(l.points[s - 1].x * w, l.points[s - 1].y * w, l.points[s].x * w, l.points[s].y * w, p)
                File(out, "${name}_$k.jpg").outputStream().use { ov.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }
        }
        val strictF1 = 2.0 * tp / (2 * tp + fp + fn).coerceAtLeast(1); val jsi = tp.toDouble() / (tp + fp + fn).coerceAtLeast(1)
        println("BENCH  estricto (píxel a píxel, toda la foto): F1 %.3f  JSI %.3f".format(strictF1, jsi))
        val summary = JSONObject().put("images", n).put("toleranceFraction", TOL / 1024.0).put("strictF1", strictF1).put("strictJSI", jsi)
        println("BENCH ARRUGAS (FFHQ-Wrinkle, $what, $n fotos, tolerancia ${TOL.toInt()} px de 1024):")
        acc.forEach { (r, a) ->
            println("BENCH  %-18s precisión %.2f  exhaustividad %.2f  F1 %.2f  (trazado %d px, manual %d px)".format(r, a.p(), a.r(), a.f(), a.pred, a.gt))
            summary.put(r, JSONObject().put("precision", a.p().toDouble()).put("recall", a.r().toDouble()).put("f1", a.f().toDouble()).put("predPx", a.pred).put("gtPx", a.gt))
        }
        if (save) File(out, "$name.json").writeText(JSONObject().put("summary", summary).put("images", perImage).toString(2))
    }
}
