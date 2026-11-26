package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.photomed.app.align.BrowRefine
import com.photomed.app.align.FaceCrop
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Cejas frente a una referencia por píxel (máscaras `*_parse.png` locales, no versionadas, de un modelo de face
 * parsing usado solo para evaluar): IoU del contorno de la malla, con el ajuste de [BrowRefine]. Informe:
 * build/reports/brow_fit.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowFitTest {
    private val dir = File("src/test/resources/stock/new")

    private fun iou(poly: List<Pt>, mask: BooleanArray, w: Int, h: Int): Float {
        val pm = BooleanArray(w * h); com.photomed.app.align.SkinLesions.fillPoly(pm, w, h, poly)
        var i = 0; var u = 0
        for (k in pm.indices) { if (pm[k] && mask[k]) i++; if (pm[k] || mask[k]) u++ }
        return if (u == 0) 0f else i / u.toFloat()
    }

    @Test fun browsAgainstParsing() {
        val mf = File(dir, "mesh.json"); assumeTrue(mf.exists() && dir.listFiles { f -> f.name.endsWith("_parse.png") }.orEmpty().isNotEmpty())
        val js = JSONObject(mf.readText())
        val rows = ArrayList<Pair<Float, Float>>(); val shaped = ArrayList<Float>()
        for (k in js.keys().asSequence().sorted()) {
            val img = BitmapFactory.decodeFile(File(dir, "$k.jpg").path) ?: continue
            val par = BitmapFactory.decodeFile(File(dir, "${k}_parse.png").path) ?: continue
            val w = img.width; val h = img.height
            val a = js.getJSONArray(k); val mesh = (0 until a.length()).map { a.getJSONArray(it).let { p -> Pt(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) } }
            val lum = FaceCrop.lum(img)
            val ref = BrowRefine.refineMesh(mesh, lum)
            val shp = BrowRefine.reshapeMesh(ref, lum)
            val pp = IntArray(w * h).also { par.getPixels(it, 0, w, 0, 0, w, h) }
            for ((top, bot) in listOf(BrowRefine.TOP_R to BrowRefine.BOT_R, BrowRefine.TOP_L to BrowRefine.BOT_L)) {
                fun poly(m: List<Pt>) = (top.map { m[it] } + bot.reversed().map { m[it] }).map { Pt(it.x * w, it.y * w) }
                val p0 = poly(mesh); val cx = p0.map { it.x }.average(); val cy = p0.map { it.y }.average()
                val ipd = kotlin.math.hypot((mesh[468].x - mesh[473].x) * w, (mesh[468].y - mesh[473].y) * w)
                // la ceja del parse (6 o 7) más cercana
                val best = listOf(6, 7).mapNotNull { l ->
                    var n = 0; var sx = 0.0; var sy = 0.0
                    for (i in pp.indices) if ((pp[i] and 0xFF) == l) { n++; sx += i % w; sy += i / w }
                    if (n < 200) null else Triple(l, kotlin.math.hypot(sx / n - cx, sy / n - cy), n)
                }.minByOrNull { it.second } ?: continue
                if (best.second > 0.4 * ipd) continue
                val mask = BooleanArray(w * h) { (pp[it] and 0xFF) == best.first }
                rows += iou(p0, mask, w, h) to iou(poly(ref), mask, w, h); shaped += iou(poly(shp), mask, w, h)
            }
        }
        val rep = "# Cejas frente a la referencia por píxel\n\nCejas: ${rows.size}. IoU malla %.3f; con BrowRefine %.3f (mejor en %d, peor en %d); con la forma corregida y ajustada %.3f (mejor que la malla en %d de %d).\n".format(
            rows.map { it.first }.average(), rows.map { it.second }.average(), rows.count { it.second > it.first + 1e-3f }, rows.count { it.second < it.first - 1e-3f }, shaped.average(), rows.indices.count { shaped[it] > rows[it].first }, rows.size)
        File("build/reports").mkdirs(); File("build/reports/brow_fit.md").writeText(rep)
        println(rep)
    }
}
