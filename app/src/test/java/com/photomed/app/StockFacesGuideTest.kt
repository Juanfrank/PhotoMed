package com.photomed.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photomed.app.align.FaceKeys
import com.photomed.app.align.Pt
import com.photomed.app.data.Zone
import com.photomed.app.ui.camera.FaceControlKeys
import com.photomed.app.ui.camera.FaceMeshWarp
import com.photomed.app.ui.camera.GuideArtByZone
import com.photomed.app.ui.camera.GuideOverlay
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Guía adaptada con retratos de stock reales (Unsplash/Pexels; las fotos no están en el
 * repositorio: `tools/eval/stock_faces.py` las prepara en test/resources/stock). Dos variantes de
 * puntos: MediaPipe tal cual y "otro motor" (cada contorno con orden de inicio/sentido y lado
 * distintos, más ruido), que debe dar el mismo resultado gracias al orden canónico.
 * Comprueba que la malla deformada no se pliega y genera láminas en build/reports/stock_guide_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1200dp-h1700dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StockFacesGuideTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val json = javaClass.getResourceAsStream("/stock/stock_faces.json")?.bufferedReader()?.readText()

    private fun pts(o: JSONObject): Map<String, Pt> {
        val p = o.getJSONObject("pts")
        return p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
    }

    /** Simula otro motor: cada grupo con otro punto de inicio/sentido, lados intercambiados y ruido. */
    private fun scrambled(p: Map<String, Pt>, seed: Int): Map<String, Pt> {
        val rnd = Random(seed)
        val out = HashMap<String, Pt>()
        val groups = p.keys.filter { it.last().isDigit() }.map { it.trimEnd { c -> c.isDigit() } }.toSet()
        for (g in groups) {
            val n = p.keys.count { it.startsWith(g) && it.removePrefix(g).all(Char::isDigit) }
            val list = (0 until n).map { p["$g$it"]!! }
            val closed = g == "oval_" || g.startsWith("eye_")
            var l = if (rnd.nextBoolean()) list.reversed() else list
            if (closed) { val s = rnd.nextInt(n); l = List(n) { l[(s + it) % n] } }
            l.forEachIndexed { i, v -> out["$g$i"] = Pt(v.x + rnd.nextFloat() * 0.008f - 0.004f, v.y + rnd.nextFloat() * 0.008f - 0.004f) }
        }
        p.filterKeys { !it.last().isDigit() }.forEach { (k, v) -> out[k] = v }
        // lados intercambiados (como si el motor nombrara izquierda/derecha al revés)
        if (rnd.nextBoolean()) {
            val sw = HashMap(out)
            for ((a, b) in listOf("eye_l_" to "eye_r_", "brow_l_top_" to "brow_r_top_", "brow_l_bottom_" to "brow_r_bottom_"))
                for (i in 0 until 16) { out["$a$i"]?.let { sw["$b$i"] = it }; out["$b$i"]?.let { sw["$a$i"] = it } }
            out.clear(); out.putAll(sw)
        }
        return out
    }

    /** Pliegues de la malla dentro del óvalo facial del dibujo (donde están los rasgos). */
    private fun folds(w: FaceMeshWarp, controls: FloatArray): Int {
        val keys = FaceControlKeys
        val oval = (0 until 36).map { i -> keys.indexOf("oval_$i").let { androidx.compose.ui.geometry.Offset(controls[it * 2], controls[it * 2 + 1]) } }
        fun inside(x: Float, y: Float): Boolean {
            var ins = false; var j = oval.size - 1
            for (i in oval.indices) { val a = oval[i]; val b = oval[j]
                if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) ins = !ins; j = i }
            return ins
        }
        var bad = 0
        val n = 40
        for (j in 0 until n) for (i in 0 until n) {
            val x = i / n.toFloat(); val y = j / n.toFloat()
            if (!inside(x + 0.5f / n, y + 0.5f / n)) continue
            fun m(a: Float, b: Float) = w.map(androidx.compose.ui.geometry.Offset(a, b))
            val p00 = m(x, y); val p10 = m(x + 1f / n, y); val p01 = m(x, y + 1f / n)
            val cross = (p10.x - p00.x) * (p01.y - p00.y) - (p10.y - p00.y) * (p01.x - p00.x)
            if (cross <= 0f) bad++
        }
        return bad
    }

    @Test
    fun stockFacesBothPointOrders() {
        assumeTrue("sin fotos de stock (ver tools/eval/stock_faces.py)", json != null)
        val faces = JSONObject(json!!)
        val art = GuideArtByZone["rostro_frontal"]!!
        val names = faces.keys().asSequence().toList().sorted()
        var foldsTotal = 0; var n = 0
        val report = StringBuilder()
        for ((idx, k) in names.withIndex()) {
            val p = pts(faces.getJSONObject(k))
            for ((variant, q) in listOf("mediapipe" to p, "otro_motor" to scrambled(p, idx))) {
                val w = FaceMeshWarp.fit(FaceControlKeys, art.controls, q) ?: error("$k $variant: sin deformación")
                val f = folds(w, art.controls); foldsTotal += f; n++
                // misma deformación con ambos órdenes (salvo el ruido de ±0,4 %)
                report.append("$k $variant pliegues=$f\n")
            }
            val a = FaceMeshWarp.fit(FaceControlKeys, art.controls, FaceKeys.canonical(p))!!
            val b = FaceMeshWarp.fit(FaceControlKeys, art.controls, scrambled(p, idx))!!
            for (t in listOf(0.3f to 0.48f, 0.5f to 0.66f, 0.7f to 0.48f, 0.5f to 0.9f)) {
                val o = androidx.compose.ui.geometry.Offset(t.first, t.second)
                val d = kotlin.math.hypot(a.map(o).x - b.map(o).x, a.map(o).y - b.map(o).y)
                assertTrue("$k: el orden de los puntos del motor cambia la guía ($d)", d < 0.05f)
            }
        }
        File("build/reports").mkdirs(); File("build/reports/stock_guide.txt").writeText(report.toString())
        println("Rostros de stock: $n deformaciones, pliegues totales $foldsTotal")
        renderSheets(names, faces)
        // pliegues dentro del rostro: pocos en promedio; los casos plegados usan el ajuste por franjas
        assertTrue("pliegues: $foldsTotal", foldsTotal <= 5 * n)
        val usable = names.count { k -> FaceMeshWarp.fit(FaceControlKeys, art.controls, pts(faces.getJSONObject(k)))!!
            .foldFraction(FaceControlKeys, art.controls) < com.photomed.app.ui.camera.MAX_FOLDS }
        println("Rostros con deformación rasgo a rasgo: $usable de ${names.size}")
        assertTrue(usable >= names.size * 0.8)
    }

    private fun renderSheets(names: List<String>, faces: JSONObject) {
        val per = 81
        File("build/reports").listFiles { f -> f.name.startsWith("stock_guide_") }?.forEach { it.delete() }
        for ((sheet, chunk) in names.chunked(per).withIndex()) {
            compose.setContent {
                Column(Modifier.background(Color(0xFF1B1B1F)).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chunk.chunked(9).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEachIndexed { c, k ->
                                val bmp = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/stock/$k.jpg"))
                                val p = pts(faces.getJSONObject(k))
                                val q = if ((r * 9 + c) % 2 == 0) p else scrambled(p, r * 9 + c)
                                Column(Modifier.width(128.dp)) {
                                    Box(Modifier.aspectRatio(3f / 4f)) {
                                        Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                                        GuideOverlay(Zone.FACE_FRONTAL, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize(), adaptTo = q)
                                    }
                                    Text(k.removePrefix("u_photo-").take(14) + if ((r * 9 + c) % 2 == 0) " · MediaPipe" else " · otro orden", color = Color.White, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val v = compose.activity.window.decorView
            val img = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { v.draw(android.graphics.Canvas(img)) }
            File("build/reports/stock_guide_$sheet.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
        }
    }
}
