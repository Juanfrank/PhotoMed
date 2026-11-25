package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.photomed.app.align.Nails
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
 * Uñas de las manos por el esqueleto y el color ([Nails.plates]) en fotos reales etiquetadas dorso / palma
 * (11k Hands y otras, locales, no versionadas): por el dorso deben verse uñas; por la palma, casi ninguna.
 * Comparado con el detector anterior por discos ([Nails.ofHand]). Informe: build/reports/nail_plates.md y .png.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NailPlatesTest {
    private val root = File("src/test/resources/stock/zones")

    @Test fun platesOnDorsalNotOnPalm() {
        val lf = File(root, "eval_labels.json")
        assumeTrue("sin fotos etiquetadas locales", lf.exists() && File(root, "eval_manos11k/landmarks.json").exists())
        val labels = JSONObject(lf.readText())
        val per = HashMap<String, MutableList<Float>>(); val old = HashMap<String, MutableList<Float>>()
        val tiles = ArrayList<Bitmap>()
        for (dir in listOf("eval_manos11k", "eval_manicura")) {
            val js = JSONObject(File(root, "$dir/landmarks.json").readText())
            for (name in js.keys().asSequence().sorted()) {
                val e = js.getJSONObject(name); val hands = e.optJSONArray("hands") ?: continue
                if (hands.length() == 0) continue
                val cls = labels.optJSONArray("$dir/$name")?.let { a -> (0 until a.length()).map { a.getString(it) }.firstOrNull { !it.startsWith("!") } } ?: continue
                if (cls != "manos_dorso" && cls != "manos_palma") continue
                val src = BitmapFactory.decodeFile(File(root, "$dir/$name").path) ?: continue
                val w = minOf(src.width, 1400); val h = w * src.height / src.width
                val img = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
                val rgb = IntArray(w * h).also { img.getPixels(it, 0, w, 0, 0, w, h) }
                val c = Canvas(img); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.MAGENTA }
                var n = 0; var no = 0
                for (i in 0 until hands.length()) {
                    val pa = hands.getJSONObject(i).getJSONArray("points")
                    val pts = (0 until pa.length()).map { j -> pa.getJSONArray(j).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w) } }
                    val ps = Nails.plates(rgb, w, h, pts).filterNotNull()
                    n += ps.size; no += Nails.ofHand(rgb, w, h, pts).count { it != null }
                    ps.forEach { p -> val o = p.outline(); val path = Path(); o.forEachIndexed { k, q -> if (k == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }; path.close(); c.drawPath(path, paint) }
                }
                per.getOrPut(cls) { ArrayList() } += n / hands.length().toFloat()
                old.getOrPut(cls) { ArrayList() } += no / hands.length().toFloat()
                if (tiles.size < 24) tiles += Bitmap.createScaledBitmap(img, 240, 240 * h / w, true)
            }
        }
        val rep = StringBuilder("# Uñas de las manos: esqueleto + color\n\n| clase | manos | uñas/mano (nuevo) | uñas/mano (anterior) | ≥2 uñas (nuevo) | ≥2 uñas (anterior) |\n|---|---|---|---|---|---|\n")
        for (k in listOf("manos_dorso", "manos_palma")) {
            val v = per[k].orEmpty(); val o = old[k].orEmpty(); if (v.isEmpty()) continue
            rep.append("| $k | ${v.size} | %.2f | %.2f | %d | %d |\n".format(v.average(), o.average(), v.count { it >= 2f }, o.count { it >= 2f }))
        }
        File("build/reports").mkdirs(); File("build/reports/nail_plates.md").writeText(rep.toString())
        if (tiles.isNotEmpty()) {
            val th = tiles.maxOf { it.height }; val cols = 6
            val sheet = Bitmap.createBitmap(240 * cols, th * ((tiles.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
            Canvas(sheet).apply { drawColor(Color.WHITE); tiles.forEachIndexed { i, t -> drawBitmap(t, (i % cols) * 240f, (i / cols) * th.toFloat(), null) } }
            File("build/reports/nail_plates.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        val d = per["manos_dorso"].orEmpty(); val p = per["manos_palma"].orEmpty()
        assumeTrue(d.isNotEmpty() && p.isNotEmpty())
        assertTrue("uñas por el dorso: ${d.average()}", d.average() >= 2.2)
        assertTrue("uñas por la palma: ${p.average()}", p.average() <= 0.9)
    }
}
