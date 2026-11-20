package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.HairSkin
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Línea de implantación frontal con la segmentación multiclase (pelo / piel del rostro) sobre
 * retratos de stock (etiquetas de `tools/eval/hair_labels.py`, carpeta ignorada).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HairSkinTest {
    private fun labels(name: String): FaceLabels? {
        val bmp = javaClass.getResourceAsStream("/stock/labels/$name.png")?.let { BitmapFactory.decodeStream(it) } ?: return null
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        return FaceLabels(bmp.width, bmp.height, ByteArray(px.size) { Color.red(px[it]).toByte() })
    }

    @Test
    fun hairlineOnStockPortraits() {
        val json = javaClass.getResourceAsStream("/stock/stock_faces.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos de stock", json != null)
        val o = JSONObject(json!!)
        val dir = File("build/reports/hairline").apply { mkdirs() }
        var n = 0; var found = 0; val heights = ArrayList<Float>()
        for (k in o.keys().asSequence().filter { it.endsWith("_r0") }.sorted()) {
            val lab = labels(k) ?: continue
            val p = o.getJSONObject(k).getJSONObject("pts")
            val pts = p.keys().asSequence().associateWith { kk -> p.getJSONArray(kk).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            n++
            val h = HairSkin.hairline(lab, pts)
            if (h != null) { found++; h.heightIpd?.let { heights += it } }
            // vista previa para revisión visual (no se guarda en el repositorio)
            val img = javaClass.getResourceAsStream("/stock/$k.jpg")?.let { BitmapFactory.decodeStream(it) }?.copy(Bitmap.Config.ARGB_8888, true) ?: continue
            val c = Canvas(img); val paint = Paint().apply { color = Color.GREEN; strokeWidth = 3f }
            h?.points?.forEach { c.drawCircle(it.x * img.width, it.y * img.width, 2.5f, paint) }
            File(dir, "$k.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 90, it) }
            println("PELO $k: ${h?.points?.size ?: 0} puntos, frente %s, cobertura %s".format(
                h?.heightIpd?.let { "%.2f×IPD".format(it) } ?: "—", h?.coverage?.let { "%.0f %%".format(it * 100) } ?: "—"))
        }
        assumeTrue(n > 0)
        println("PELO: línea en $found de $n retratos; frente mediana %.2f×IPD".format(heights.sorted().getOrElse(heights.size / 2) { 0f }))
        assertTrue(found >= n * 0.6)
        // frente adulta ≈ 5–7 cm sobre las cejas; interpupilar ≈ 6,3 cm
        assertTrue(heights.all { it in 0.4f..1.8f })
    }

    @Test
    fun hairlineSummaryInCompare() {
        val s = com.photomed.app.ui.compare.hairlineSummary(listOf(mapOf("HairlineIpd" to "0.80"), mapOf("HairlineIpd" to "0.90")))
        assertTrue(s!!, s.contains("0.80 → 0.90") || s.contains("0,80 → 0,90"))
        assertTrue(s, s.contains("retrocede"))
        assertTrue(com.photomed.app.ui.compare.hairlineSummary(listOf(mapOf("HairlineIpd" to "0.8"), emptyMap())) == null)
    }

    @Test
    fun ovalSnapAndOcclusionOnStock() {
        val json = javaClass.getResourceAsStream("/stock/stock_faces.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos de stock", json != null)
        val o = JSONObject(json!!)
        val dir = File("build/reports/oval_snap").apply { mkdirs() }
        val moves = ArrayList<Float>(); val occl = HashMap<String, Int>()
        for (k in o.keys().asSequence().filter { it.endsWith("_r0") }.sorted()) {
            val lab = labels(k) ?: continue
            val p = o.getJSONObject(k).getJSONObject("pts")
            val pts = com.photomed.app.align.FaceKeys.canonical(p.keys().asSequence().associateWith { kk -> p.getJSONArray(kk).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } })
            val snapped = HairSkin.snapOval(lab, pts)
            val faceW = (0 until 36).maxOf { pts["oval_$it"]!!.x } - (0 until 36).minOf { pts["oval_$it"]!!.x }
            val m = (0 until 36).map { i -> val a = pts["oval_$i"]!!; val b = snapped["oval_$i"]!!; kotlin.math.hypot(a.x - b.x, a.y - b.y) / faceW }
            moves += m.average().toFloat()
            occl[k] = HairSkin.occluded(lab, pts).size
            val img = javaClass.getResourceAsStream("/stock/$k.jpg")?.let { BitmapFactory.decodeStream(it) }?.copy(Bitmap.Config.ARGB_8888, true) ?: continue
            val c = Canvas(img)
            val red = Paint().apply { color = Color.RED; strokeWidth = 2f }; val green = Paint().apply { color = Color.GREEN; strokeWidth = 2f }
            for (i in 0 until 36) {
                val a = pts["oval_$i"]!!; val b = snapped["oval_$i"]!!
                c.drawCircle(a.x * img.width, a.y * img.width, 2f, red); c.drawCircle(b.x * img.width, b.y * img.width, 2.5f, green)
            }
            File(dir, "$k.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        assumeTrue(moves.isNotEmpty())
        println("ÓVALO: desplazamiento medio %.1f %% del ancho de la cara (máx. %.1f %%)".format(moves.average() * 100, moves.max() * 100))
        println("TAPADOS: " + occl.entries.sortedByDescending { it.value }.take(6).joinToString { "${it.key.take(18)}=${it.value}" })
        // ajustes moderados (la segmentación corrige, no reemplaza) y pocos puntos tapados en rostros despejados
        assertTrue(moves.average() < 0.05)
        assertTrue(occl.values.count { it > 20 } <= 3)
    }

    @Test
    fun aestheticAnnotationsOnStock() {
        val json = javaClass.getResourceAsStream("/stock/stock_faces.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos de stock", json != null)
        val o = JSONObject(json!!)
        val dir = File("build/reports/annot").apply { mkdirs() }
        var n = 0; var refined = 0; var folds = 0
        val colors = mapOf(com.photomed.app.align.AnnotLayer.CONTOUR to Color.CYAN, com.photomed.app.align.AnnotLayer.HAIRLINE to Color.YELLOW,
            com.photomed.app.align.AnnotLayer.EYES to Color.rgb(128, 222, 234), com.photomed.app.align.AnnotLayer.NOSE to Color.rgb(179, 157, 219),
            com.photomed.app.align.AnnotLayer.LIPS to Color.rgb(244, 143, 177), com.photomed.app.align.AnnotLayer.FOLDS to Color.rgb(255, 171, 145))
        for (k in o.keys().asSequence().filter { it.endsWith("_r0") }.sorted()) {
            val lab = labels(k) ?: continue
            val img = javaClass.getResourceAsStream("/stock/$k.jpg")?.let { BitmapFactory.decodeStream(it) }?.copy(Bitmap.Config.ARGB_8888, true) ?: continue
            val p = o.getJSONObject(k).getJSONObject("pts")
            val pts = p.keys().asSequence().associateWith { kk -> p.getJSONArray(kk).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val px = IntArray(img.width * img.height).also { img.getPixels(it, 0, img.width, 0, 0, img.width, img.height) }
            val lum = com.photomed.app.align.GrayImage(img.width, img.height, FloatArray(px.size) { i -> val c = px[i]; (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f })
            val lines = com.photomed.app.align.FaceAnnotations.face(pts, lab, lum)
            n++
            lines.filter { it.layer == com.photomed.app.align.AnnotLayer.FOLDS }.forEach { folds++; if (!it.estimated) refined++ }
            val c = Canvas(img)
            for (l in lines) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.getValue(l.layer); strokeWidth = 1.6f; style = Paint.Style.STROKE
                    if (l.estimated) pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 4f), 0f) }
                val path = android.graphics.Path()
                l.points.forEachIndexed { i, q -> if (i == 0) path.moveTo(q.x * img.width, q.y * img.width) else path.lineTo(q.x * img.width, q.y * img.width) }
                if (l.closed) path.close()
                c.drawPath(path, paint)
            }
            File(dir, "$k.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 90, it) }
            // capas del método de respaldo (sin malla): contorno, ojos, nariz, labios y surcos
            assertTrue(k, listOf(com.photomed.app.align.AnnotLayer.CONTOUR, com.photomed.app.align.AnnotLayer.EYES, com.photomed.app.align.AnnotLayer.NOSE,
                com.photomed.app.align.AnnotLayer.LIPS, com.photomed.app.align.AnnotLayer.FOLDS).all { layer -> lines.any { it.layer == layer } })
        }
        assumeTrue(n > 0)
        println("ANOTACIONES: $n rostros · surcos ajustados al valle de sombra $refined de $folds")
    }
}
