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
}
