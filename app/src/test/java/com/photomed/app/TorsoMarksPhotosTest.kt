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

    /** Ombligo y pezones marcados a mano (x, y en % del ancho de la foto) en fotos locales donde se ven. */
    private val GT = mapOf(
        "abdomen_frontal/c57.jpg" to (listOf(13f to 35f, 73f to 35f) to (50f to 96f)),
        "abdomen_frontal/c33.jpg" to (listOf(40f to 49f, 62f to 50f) to (50f to 80f)),
        "abdomen_frontal/c34.jpg" to (listOf(37f to 44f, 60f to 44f) to (49f to 72f)),
        "abdomen_frontal/c47.jpg" to (listOf(32f to 52f, 60f to 52f) to (48f to 84f)),
        "abdomen_frontal/c36.jpg" to (emptyList<Pair<Float, Float>>() to (73f to 98f)),
        "torax/c11.jpg" to (emptyList<Pair<Float, Float>>() to (63f to 123f)),
        "torax/c14.jpg" to (emptyList<Pair<Float, Float>>() to (40f to 105f)),
        "torax/c15.jpg" to (emptyList<Pair<Float, Float>>() to (22f to 94f)),
        "torax/c16.jpg" to (emptyList<Pair<Float, Float>>() to (28f to 128f)),
        "torax/c22.jpg" to (emptyList<Pair<Float, Float>>() to (48f to 98f)),
        "torax/c30.jpg" to (emptyList<Pair<Float, Float>>() to (39f to 91f)),
    )

    @Test fun navelAndNipplesAgainstManualMarks() {
        assumeTrue("sin fotos locales", File(root, "torax/landmarks.json").exists())
        var navHit = 0; var navWrong = 0; var navMiss = 0; var nipHit = 0; var nipWrong = 0; var nipMiss = 0
        val rep = StringBuilder("\n## Frente a marcas manuales (acierto: < 6 % del ancho)\n\n| foto | ombligo | pezones |\n|---|---|---|\n")
        for ((key, g) in GT) {
            val (dir, name) = key.split('/')
            val e = JSONObject(File(root, "$dir/landmarks.json").readText()).optJSONObject(name) ?: continue
            val str = e.optString("classes", "").takeIf { it.length == 96 * 128 } ?: continue
            val src = BitmapFactory.decodeFile(File(root, key).path) ?: continue
            val w = e.getInt("w"); val h = e.getInt("h")
            val pose = e.optJSONArray("pose")?.let { pa -> LandmarkDetector.poseKeys((0 until pa.length()).associateWith { i -> pa.getJSONArray(i).let {
                Triple(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w, it.getDouble(2).toFloat()) } }) }.orEmpty()
            val hh = (96f * h / w).toInt().coerceAtLeast(1)
            val classes = ZoneCriteria.Classes(96, hh, ByteArray(96 * hh) { i -> (str[((i / 96) * 128 / hh) * 96 + i % 96] - '0').toByte() })
            val iw = minOf(src.width, 1400); val ih = iw * src.height / src.width
            val img = Bitmap.createScaledBitmap(src, iw, ih, true)
            val m = TorsoMarks.of(GuideType.TORSO_FRONT, classes, pose, ZoneCriteria.Hi(IntArray(iw * ih).also { img.getPixels(it, 0, iw, 0, 0, iw, ih) }, iw, ih))
            fun err(p: com.photomed.app.align.Pt, q: Pair<Float, Float>) = kotlin.math.hypot(p.x * 100 - q.first, p.y * 100 - q.second)
            val nv = m[TorsoMarks.NAVEL]
            val navS = when { nv == null -> { navMiss++; "—" }; err(nv, g.second) < 6f -> { navHit++; "sí" }; else -> { navWrong++; "no (%.0f, %.0f)".format(nv.x * 100, nv.y * 100) } }
            val na = m[TorsoMarks.NIPPLE_A]; val nb = m[TorsoMarks.NIPPLE_B]
            val nipS = when {
                g.first.isEmpty() -> if (na != null) { nipWrong++; "falso" } else "(cubiertos)"
                na == null || nb == null -> { nipMiss++; "—" }
                g.first.all { q -> minOf(err(na, q), err(nb, q)) < 6f } -> { nipHit++; "sí" }
                else -> { nipWrong++; "no" }
            }
            rep.append("| $key | $navS | $nipS |\n")
        }
        rep.append("\nOmbligo: $navHit aciertos, $navWrong erróneos, $navMiss no hallados. Pezones: $nipHit aciertos, $nipWrong erróneos, $nipMiss no hallados.\n")
        File("build/reports").mkdirs(); File("build/reports/torso_marks_gt.md").writeText(rep.toString())
        assertTrue("ombligo: $navHit aciertos, $navWrong erróneos", navHit >= navWrong)
    }
}
