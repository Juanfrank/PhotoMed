package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.BodyDiagram
import com.photomed.app.align.LandmarkDetector
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneCriteria
import com.photomed.app.align.isFaceZone
import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Esqueleto del cuerpo ([com.photomed.app.align.BodySkeleton]) sobre las fotos reales etiquetadas de la evaluación
 * de zonas: por toma, cuántas tienen esqueleto con medidas y si los ángulos y proporciones caen en rangos
 * anatómicos. Informe: build/reports/body_skeleton.md y body_skeleton.png.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodySkeletonPhotosTest {
    private val root = File("src/test/resources/stock/zones")

    /** Rangos plausibles de cada medida (fuera = medida errónea). */
    private fun plausible(label: String, v: Float): Boolean = when {
        label.startsWith("Ángulo") -> v in 30f..181f
        label.startsWith("Inclinación de") -> kotlin.math.abs(v) <= 35f
        label.startsWith("Inclinación del cuello") || label.startsWith("Inclinación del tronco") -> kotlin.math.abs(v) <= 45f
        label.startsWith("Cintura") -> v in 30f..110f
        label.startsWith("Grosor") -> v in 10f..120f
        label.startsWith("Antepié") -> v in 30f..250f
        label.startsWith("Eje del") -> kotlin.math.abs(v) <= 90f
        else -> true
    }

    @Test fun skeletonOnRealPhotos() {
        val lf = File(root, "eval_labels.json")
        assumeTrue("sin fotos etiquetadas locales", lf.exists())
        val labels = JSONObject(lf.readText())
        val cache = HashMap<String, JSONObject>()
        val seen = HashMap<GuideType, Int>(); val withM = HashMap<GuideType, Int>()
        var vals = 0; var ok = 0
        val bad = ArrayList<String>(); val tiles = ArrayList<Bitmap>(); val feetTiles = ArrayList<Bitmap>()
        for (key in labels.keys().asSequence().sorted()) {
            val arr = labels.getJSONArray(key)
            val z = (0 until arr.length()).map { arr.getString(it) }.firstOrNull { !it.startsWith("!") }?.let { Zone.fromId(it) } ?: continue
            if (z.isFaceZone() || z == Zone.FREE || z.guide == GuideType.HANDS || z.guide == GuideType.EYE || z.category == com.photomed.app.data.ZoneCategory.CAPILAR) continue
            if ((seen[z.guide] ?: 0) >= 40) continue
            val dir = key.substringBefore('/'); val name = key.substringAfter('/')
            val js = cache.getOrPut(dir) { File(root, "$dir/landmarks.json").takeIf { it.exists() }?.let { JSONObject(it.readText()) } ?: JSONObject() }
            val nm = if (z.guide == GuideType.FEET && js.has("r_$name")) "r_$name" else name
            val e = js.optJSONObject(nm) ?: continue
            val img = BitmapFactory.decodeFile(File(root, "$dir/$nm").path) ?: continue
            val w = e.getInt("w"); val h = e.getInt("h")
            val pose = e.optJSONArray("pose")?.let { a ->
                LandmarkDetector.poseKeys((0 until a.length()).associateWith { i -> a.getJSONArray(i).let {
                    Triple(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w, it.getDouble(2).toFloat()) } })
            }.orEmpty()
            val classes = e.optString("classes", "").takeIf { it.length == 96 * 128 }?.let { str ->
                val hh = (96f * h / w).toInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(img, 96, hh, true)
                val rgb = IntArray(96 * hh).also { small.getPixels(it, 0, 96, 0, 0, 96, hh) }
                ZoneCriteria.Classes(96, hh, ByteArray(96 * hh) { i -> (str[((i / 96) * 128 / hh) * 96 + i % 96] - '0').toByte() }, rgb)
            }
            val scene = ZoneCriteria.Scene(pose, null, classes, h / w.toFloat())
            val lines = BodyDiagram.lines(z, scene)
            seen[z.guide] = (seen[z.guide] ?: 0) + 1
            val m = lines.mapNotNull { it.metrics }.flatMap { it.features }
            if (m.isNotEmpty()) withM[z.guide] = (withM[z.guide] ?: 0) + 1
            m.forEach { (l, v) -> vals++; if (plausible(l, v)) ok++ else bad += "$key: $l = " + "%.1f".format(v) }
            val feetTile = z.guide == GuideType.FEET && feetTiles.size < 18
            val tw = if (feetTile) 480 else 240
            if ((tiles.size < 24 || feetTile) && m.isNotEmpty()) {
                val out = Bitmap.createScaledBitmap(img, tw, (tw.toFloat() * h / w).toInt(), true).copy(Bitmap.Config.ARGB_8888, true)
                val cv = Canvas(out)
                val pa = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f; style = Paint.Style.STROKE }
                for (l in lines) {
                    pa.color = when (l.layer) { AnnotLayer.BODY_AXES -> Color.WHITE; AnnotLayer.BODY_POINTS -> Color.MAGENTA; AnnotLayer.FINGERS -> Color.CYAN; else -> continue }
                    pa.style = if (l.filled) Paint.Style.FILL else Paint.Style.STROKE
                    for (i in 1 until l.points.size) cv.drawLine(l.points[i - 1].x * tw, l.points[i - 1].y * tw, l.points[i].x * tw, l.points[i].y * tw, pa)
                }
                if (feetTile) feetTiles += Bitmap.createScaledBitmap(out, 480, 640, true) else tiles += Bitmap.createScaledBitmap(out, 240, 320, true)
            }
        }
        val rep = StringBuilder("# Esqueleto del cuerpo en fotos reales\n\n| toma | fotos | con medidas |\n|---|---|---|\n")
        seen.toSortedMap().forEach { (g, n) -> rep.append("| $g | $n | ${withM[g] ?: 0} (%.0f %%) |\n".format((withM[g] ?: 0) * 100f / n)) }
        rep.append("\nMedidas plausibles: $ok de $vals (%.0f %%)\n\nFuera de rango:\n".format(ok * 100f / vals.coerceAtLeast(1)))
        bad.take(40).forEach { rep.append("- $it\n") }
        File("build/reports").mkdirs(); File("build/reports/body_skeleton.md").writeText(rep.toString())
        if (tiles.isNotEmpty()) {
            val cols = 6; val sheet = Bitmap.createBitmap(240 * cols, 320 * ((tiles.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
            val c = Canvas(sheet); tiles.forEachIndexed { i, t -> c.drawBitmap(t, (i % cols) * 240f, (i / cols) * 320f, null) }
            File("build/reports/body_skeleton.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        if (feetTiles.isNotEmpty()) {
            val cols = 4; val sheet = Bitmap.createBitmap(480 * cols, 640 * ((feetTiles.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
            val c = Canvas(sheet); feetTiles.forEachIndexed { i, t -> c.drawBitmap(t, (i % cols) * 480f, (i / cols) * 640f, null) }
            File("build/reports/feet_skeleton.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        println(rep)
        assert(vals > 0)
        assert(ok >= 0.9f * vals) { "medidas plausibles ${ok * 100 / vals} %" }
    }
}
