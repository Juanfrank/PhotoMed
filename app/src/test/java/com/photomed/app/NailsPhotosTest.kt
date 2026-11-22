package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.Nails
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneCriteria
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Uñas ([Nails]) con fotos reales (de internet, solo para evaluar; carpeta local `stock/zones`): por el
 * dorso de manos y pies deben verse uñas; por la palma y la planta, no. Informe:
 * build/reports/zone_criteria/unas.png y unas.txt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NailsPhotosTest {
    private val root = File("src/test/resources/stock/zones")
    private val tiles = ArrayList<Bitmap>()

    private fun mark(img: Bitmap, nails: List<Nails.Nail>, tips: List<Pair<Float, Float>>, label: String) {
        val out = img.copy(Bitmap.Config.ARGB_8888, true); val c = Canvas(out)
        val s = img.width / 480f
        tips.forEach { (x, y) -> c.drawCircle(x, y, 3f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW }) }
        nails.forEach { n -> c.drawCircle(n.x, n.y, n.r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 3f * s }) }
        c.drawText(label, 8f * s, 30f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; textSize = 26f * s; isFakeBoldText = true })
        tiles += Bitmap.createScaledBitmap(out, 240, (240f * img.height / img.width).toInt().coerceAtMost(400), true)
    }

    @Test
    fun nailsOnDorsalNotOnPalmOrSole() {
        assumeTrue("sin fotos locales", File(root, "pies_eval").exists())
        val rep = StringBuilder("UÑAS (por mano / pie: uñas halladas)\n")
        val counts = HashMap<String, MutableList<Int>>()
        for (dir in listOf("manos_eval_dorso", "manos_eval_palma")) {
            val jf = File(root, "$dir/hands_eval.json"); if (!jf.exists()) continue
            val o = JSONObject(jf.readText())
            for (name in o.keys().asSequence().sorted().filter { !it.startsWith("m_") && !it.startsWith("pm_") }) {
                val e = o.getJSONObject(name); val w = e.getInt("w"); val h = e.getInt("h")
                val hands = e.optJSONArray("hands") ?: continue
                val img = BitmapFactory.decodeFile(File(root, "$dir/" + e.optString("image", name)).path) ?: continue
                val iw = img.width; val ih = img.height
                val rgb = IntArray(iw * ih).also { img.getPixels(it, 0, iw, 0, 0, iw, ih) }
                val all = ArrayList<Nails.Nail>(); val tips = ArrayList<Pair<Float, Float>>(); val per = ArrayList<Int>()
                for (i in 0 until hands.length()) {
                    val pts = hands.getJSONObject(i).getJSONArray("points").let { a -> (0 until a.length()).map { j -> a.getJSONArray(j).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * ih / iw) } } }
                    rep.append("   contrastes $dir/$name: " + Nails.ofHand(rgb, iw, ih, pts, 0f).joinToString { it?.let { n -> "%.1f".format(n.contrast) } ?: "-" } + "\n")
                    val ns = Nails.ofHand(rgb, iw, ih, pts)
                    per += ns.count { it != null }; all += ns.filterNotNull()
                    listOf(4, 8, 12, 16, 20).forEach { tips += pts[it].x * iw to pts[it].y * iw }
                }
                counts.getOrPut(dir) { ArrayList() } += per
                rep.append("$dir/$name: ${per.joinToString()}\n")
                mark(img, all, tips, "$dir ${per.joinToString()}")
            }
        }
        val fdir = File(root, "pies_eval")
        for (f in fdir.listFiles { x -> x.name.endsWith(".png") && !x.name.endsWith("_mask.png") }.orEmpty().sortedBy { it.name }) {
            val img = BitmapFactory.decodeFile(f.path) ?: continue
            val mk = BitmapFactory.decodeFile(File(fdir, f.nameWithoutExtension + "_mask.png").path) ?: continue
            val w = img.width; val h = img.height
            val rgb = IntArray(w * h).also { img.getPixels(it, 0, w, 0, 0, w, h) }
            val m = IntArray(w * h).also { mk.getPixels(it, 0, w, 0, 0, w, h) }
            val c = ZoneCriteria.Classes(w, h, ByteArray(w * h) { Color.red(m[it]).toByte() }, rgb)
            val feet = ZoneCriteria.feetOf(c)
            val all = ArrayList<Nails.Nail>(); val tips = ArrayList<Pair<Float, Float>>(); val per = ArrayList<Int>()
            for (ft in feet) {
                rep.append("   contrastes ${f.nameWithoutExtension}: " + ZoneCriteria.footNails(c, ft, 0f).joinToString { "%.1f".format(it.contrast) } + " (de ${ft.toes.size} dedos)\n")
                val ns = ZoneCriteria.footNails(c, ft)
                per += ns.size; all += ns
                ft.toes.forEach { tips += it.x * w to it.y * w }
            }
            val dir = if ("planta" in f.name) "pies_planta" else "pies_dorso"
            counts.getOrPut(dir) { ArrayList() } += per
            rep.append("$dir/${f.nameWithoutExtension}: ${per.joinToString()}\n")
            mark(img, all, tips, "$dir ${per.joinToString()}")
        }
        counts.forEach { (k, v) -> rep.append("RESUMEN $k: media %.1f uñas, con ≥3: %d/%d, con ≤1: %d/%d\n".format(v.average(), v.count { it >= 3 }, v.size, v.count { it <= 1 }, v.size)) }
        println(rep)
        val dir = File("build/reports/zone_criteria").apply { mkdirs() }
        File(dir, "unas.txt").writeText(rep.toString())
        val cols = 6; val rows = (tiles.size + cols - 1) / cols
        val sheet = Bitmap.createBitmap(240 * cols, 400 * rows, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply { drawColor(Color.WHITE); tiles.forEachIndexed { i, t -> drawBitmap(t, (i % cols) * 240f, (i / cols) * 400f, null) } }
        File(dir, "unas.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
