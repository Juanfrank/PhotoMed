package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.BodyDiagram
import com.photomed.app.align.LandmarkDetector
import com.photomed.app.align.TorsoMarks
import com.photomed.app.align.ZoneCriteria
import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
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
 * Axilas, pezones y ombligo ([TorsoMarks]) en fotos reales de tronco (locales, no versionadas): cuántas fotos los
 * dan y si caen donde la anatomía los espera respecto de hombros y caderas; y que el diagrama del tronco mida con
 * ellos. Informe: build/reports/torso_marks.md y torso_marks.png (para revisarlos a ojo).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TorsoMarksPhotosTest {
    private val root = File("src/test/resources/stock/zones")

    @Test fun marksOnRealTorsos() {
        assumeTrue("sin fotos locales", File(root, "torax/landmarks.json").exists())
        var photos = 0; val found = HashMap<String, Int>(); var plausible = 0; var checked = 0
        val tiles = ArrayList<Bitmap>(); var metricsLines = 0
        for (dir in listOf("torax", "abdomen_frontal", "eval_culturismo")) {
            val js = JSONObject(File(root, "$dir/landmarks.json").readText())
            for (name in js.keys().asSequence().sorted()) {
                val e = js.getJSONObject(name)
                val pa = e.optJSONArray("pose") ?: continue
                val str = e.optString("classes", "").takeIf { it.length == 96 * 128 } ?: continue
                val src = BitmapFactory.decodeFile(File(root, "$dir/$name").path) ?: continue
                val w = e.getInt("w"); val h = e.getInt("h")
                val pose = LandmarkDetector.poseKeys((0 until pa.length()).associateWith { i -> pa.getJSONArray(i).let {
                    Triple(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w, it.getDouble(2).toFloat()) } })
                val hh = (96f * h / w).toInt().coerceAtLeast(1)
                val classes = ZoneCriteria.Classes(96, hh, ByteArray(96 * hh) { i -> (str[((i / 96) * 128 / hh) * 96 + i % 96] - '0').toByte() })
                val iw = minOf(src.width, 1400); val ih = iw * src.height / src.width
                val img = Bitmap.createScaledBitmap(src, iw, ih, true).copy(Bitmap.Config.ARGB_8888, true)
                val hi = ZoneCriteria.Hi(IntArray(iw * ih).also { img.getPixels(it, 0, iw, 0, 0, iw, ih) }, iw, ih)
                val m = TorsoMarks.of(GuideType.TORSO_FRONT, classes, pose, hi)
                photos++
                m.keys.forEach { k -> found[k.substringBefore('_')] = (found[k.substringBefore('_')] ?: 0) + 1 }
                // plausibilidad: pezones entre hombros y la mitad del tronco; ombligo en su mitad inferior; axilas bajo los hombros
                val sy = listOfNotNull(pose[com.photomed.app.align.Landmarks.SHOULDER_L], pose[com.photomed.app.align.Landmarks.SHOULDER_R]).map { it.y }.average().toFloat()
                val hy = listOfNotNull(pose[com.photomed.app.align.Landmarks.HIP_L], pose[com.photomed.app.align.Landmarks.HIP_R]).map { it.y }.average().toFloat()
                m.forEach { (k, p) ->
                    val t = (p.y - sy) / (hy - sy); checked++
                    if (when { k.startsWith("pezon") -> t in 0.1f..0.55f; k == TorsoMarks.NAVEL -> t in 0.5f..1.05f; else -> t in -0.05f..0.5f }) plausible++
                }
                val lines = BodyDiagram.lines(Zone.TORSO_FRONT, ZoneCriteria.Scene(pose, null, classes, h / w.toFloat(), hi = hi))
                metricsLines += lines.count { it.metrics?.name in setOf("Pezones", "Ombligo", "Axilas") }
                if (m.keys.any { !it.startsWith("axila") } && tiles.size < 30) {
                    val c = Canvas(img)
                    m.forEach { (k, p) -> c.drawCircle(p.x * iw, p.y * iw, iw / 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = iw / 250f
                        color = when { k.startsWith("axila") -> Color.YELLOW; k.startsWith("pezon") -> Color.MAGENTA; else -> Color.GREEN } }) }
                    tiles += Bitmap.createScaledBitmap(img, 240, 240 * ih / iw, true)
                }
            }
        }
        val rep = "# Axilas, pezones y ombligo\n\nFotos: $photos. Con axila: ${found["axila"] ?: 0} (puntos), pezones: ${found["pezon"] ?: 0} (puntos), " +
            "ombligo: ${found["ombligo"] ?: 0}.\nPlausibles (altura respecto de hombros y caderas): $plausible/$checked. Líneas con medidas: $metricsLines.\n"
        File("build/reports").mkdirs(); File("build/reports/torso_marks.md").writeText(rep)
        if (tiles.isNotEmpty()) {
            val th = tiles.maxOf { it.height }.coerceAtMost(420); val cols = 6
            val sheet = Bitmap.createBitmap(240 * cols, th * ((tiles.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
            Canvas(sheet).apply { drawColor(Color.BLACK); tiles.forEachIndexed { i, t -> drawBitmap(t, (i % cols) * 240f, (i / cols) * th.toFloat(), null) } }
            File("build/reports/torso_marks.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        assumeTrue(photos > 0)
        assertTrue("puntos plausibles $plausible/$checked", checked == 0 || plausible >= 0.85 * checked)
        assertTrue("con alguna referencia: $found", (found["axila"] ?: 0) > 0 && (found["ombligo"] ?: 0) > 0)
    }
}
