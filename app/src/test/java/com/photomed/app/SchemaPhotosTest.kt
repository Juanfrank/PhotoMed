package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.BodyPoints
import com.photomed.app.align.BodySchema
import com.photomed.app.align.Detection
import com.photomed.app.align.LandmarkDetector
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneCriteria
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
 * Contorno (esquema de la zona) y puntos de referencia dibujados sobre fotos reales etiquetadas
 * (`stock/zones/eval_labels.json`, fuera del repositorio), como los ve el usuario en la cámara.
 * Informe: build/reports/zone_criteria/esquemas_fotos_reales.png
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SchemaPhotosTest {
    private val root = File("src/test/resources/stock/zones")

    @Test
    fun schemasOnRealPhotos() {
        val lf = File(root, "eval_labels.json")
        assumeTrue("sin fotos etiquetadas locales", lf.exists())
        val labels = JSONObject(lf.readText())
        val tiles = ArrayList<Bitmap>()
        val cache = HashMap<String, JSONObject>()
        val only = System.getProperty("schemaOnly")?.split(",")
        for (key in labels.keys().asSequence().sorted()) {
            val arr = labels.getJSONArray(key)
            val z = (0 until arr.length()).map { arr.getString(it) }.firstOrNull { !it.startsWith("!") }?.let { Zone.fromId(it) } ?: continue
            if (only != null && only.none { key.contains(it) }) continue
            val dir = key.substringBefore('/'); val name = key.substringAfter('/')
            val js = cache.getOrPut(dir) { File(root, "$dir/landmarks.json").takeIf { it.exists() }?.let { JSONObject(it.readText()) } ?: JSONObject() }
            // fotos de pies: la versión con los dedos abajo (como la guía)
            val nm = if (z.guide == com.photomed.app.data.GuideType.FEET && js.has("r_$name")) "r_$name" else name
            val e = js.optJSONObject(nm) ?: continue
            val img = BitmapFactory.decodeFile(File(root, "$dir/$nm").path) ?: continue
            val w = e.getInt("w"); val h = e.getInt("h")
            val pose = e.optJSONArray("pose")?.let { a ->
                LandmarkDetector.poseKeys((0 until a.length()).associateWith { i -> a.getJSONArray(i).let {
                    Triple(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w, it.getDouble(2).toFloat()) } })
            }.orEmpty()
            val hands = e.optJSONArray("hands")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { ho ->
                val pts = ho.getJSONArray("points")
                ZoneCriteria.Hand((0 until pts.length()).map { j -> pts.getJSONArray(j).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat() * h / w) } }, ho.getString("label"))
            } } }
            // máscara a la proporción de la foto, con el color (para dedos del pie y uñas)
            val classes = e.optString("classes", "").takeIf { it.length == 96 * 128 }?.let { str ->
                val hh = (96f * h / w).toInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(img, 96, hh, true)
                val rgb = IntArray(96 * hh).also { small.getPixels(it, 0, 96, 0, 0, 96, hh) }
                ZoneCriteria.Classes(96, hh, ByteArray(96 * hh) { i -> (str[((i / 96) * 128 / hh) * 96 + i % 96] - '0').toByte() }, rgb)
            }
            val scene = ZoneCriteria.Scene(pose, hands?.ifEmpty { null }, classes, h / w.toFloat())
            val schema = BodySchema.of(z, scene)
            val pts = runCatching { BodyPoints.of(z, scene) }.getOrDefault(emptyMap())
            val out = Bitmap.createScaledBitmap(img, 300, (300f * h / w).toInt(), true).copy(Bitmap.Config.ARGB_8888, true)
            val cv = Canvas(out); val k = 300f
            val pc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 2.5f }
            val pl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 2f }
            val pd = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW }
            val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.MAGENTA }
            schema?.let { s ->
                s.polygons.forEach { poly -> (poly + poly.take(1)).zipWithNext { a, b -> cv.drawLine(a.x * k, a.y * k, b.x * k, b.y * k, pc) } }
                s.capsules.forEach { c -> cv.drawLine(c.a.x * k, c.a.y * k, c.b.x * k, c.b.y * k, Paint(pc).apply { strokeWidth = c.r * k * 2; alpha = 90 }) }
                s.lines.forEach { l -> l.zipWithNext { a, b -> cv.drawLine(a.x * k, a.y * k, b.x * k, b.y * k, pl) } }
                s.dots.forEach { cv.drawCircle(it.x * k, it.y * k, 3.5f, pd) }
            }
            pts.values.forEach { cv.drawCircle(it.x * k, it.y * k, 2.5f, pp) }
            Canvas(out).drawText("${z.id} ${name.take(8)} pts ${pts.size}", 4f, 14f, Paint().apply { color = Color.RED; textSize = 13f; isFakeBoldText = true })
            tiles += out
        }
        val cols = 6; val th = 420
        val sheet = Bitmap.createBitmap(300 * cols, th * ((tiles.size + cols - 1) / cols).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply { drawColor(Color.WHITE); tiles.forEachIndexed { i, t -> drawBitmap(t, (i % cols) * 300f, (i / cols) * th.toFloat(), null) } }
        File("build/reports/zone_criteria").apply { mkdirs() }.resolve("esquemas_fotos_reales.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 90, it) }
        println("ESQUEMAS: ${tiles.size}")
    }
}
