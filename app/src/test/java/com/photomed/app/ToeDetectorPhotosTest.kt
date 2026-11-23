package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.ZoneCriteria
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Dedos del pie ([com.photomed.app.align.ToeDetector]) con fotos reales de pies (de internet, solo para
 * evaluar: carpeta local `stock/zones/pies_eval`, fuera del repositorio, con cada foto girada con los
 * dedos hacia abajo y su máscara de segmentación). Informe: build/reports/zone_criteria/pies_fotos_reales.png
 * con el contorno, la separación entre dedos y la punta de cada dedo de cada pie.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToeDetectorPhotosTest {
    @Test
    fun toesOnRealFootPhotos() {
        val dir = File("src/test/resources/stock/zones/pies_eval")
        val names = dir.listFiles { f -> f.name.endsWith(".png") && !f.name.endsWith("_mask.png") }?.map { it.nameWithoutExtension }?.sorted().orEmpty()
        assumeTrue("sin fotos de pies locales", names.isNotEmpty())
        val tiles = ArrayList<Bitmap>(); val lines = ArrayList<String>()
        for (n in names) {
            val img = BitmapFactory.decodeFile(File(dir, "$n.png").path) ?: continue
            val mk = BitmapFactory.decodeFile(File(dir, "${n}_mask.png").path) ?: continue
            val w = img.width; val h = img.height
            val rgb = IntArray(w * h).also { img.getPixels(it, 0, w, 0, 0, w, h) }
            val mpx = IntArray(w * h).also { mk.getPixels(it, 0, w, 0, 0, w, h) }
            val c = ZoneCriteria.Classes(w, h, ByteArray(w * h) { Color.red(mpx[it]).toByte() }, rgb)
            val feet = ZoneCriteria.feetOf(c)
            lines += "$n: " + feet.joinToString { "${it.toeCount ?: "-"}" } + "  profundidad " + feet.joinToString { "%.2f".format(it.digitDepth) }
            val out = img.copy(Bitmap.Config.ARGB_8888, true); val cv = Canvas(out)
            val k = w.toFloat()
            val pc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 2f }
            val pl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 4f }
            val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW }
            for (f in feet) {
                f.outline.zipWithNext { a, b -> cv.drawLine(a.x * k, a.y * k, b.x * k, b.y * k, pc) }
                f.toeLines.forEach { (a, b) -> cv.drawLine(a.x * k, a.y * k, b.x * k, b.y * k, pl) }
                f.toes.forEach { cv.drawCircle(it.x * k, it.y * k, 6f, pt) }
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; textSize = 28f; isFakeBoldText = true }
                cv.drawText("${f.toeCount ?: "-"}", (f.box.x0 + f.box.x1) / 2f, f.box.y1 - 40f, tp)
            }
            tiles += Bitmap.createScaledBitmap(out, 240, (240f * h / w).toInt(), true)
        }
        val cols = 5; val rows = (tiles.size + cols - 1) / cols; val th = 320
        val sheet = Bitmap.createBitmap(240 * cols, th * rows, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply { drawColor(Color.WHITE); tiles.forEachIndexed { i, t -> drawBitmap(t, (i % cols) * 240f, (i / cols) * th.toFloat(), null) } }
        val rep = File("build/reports/zone_criteria").apply { mkdirs() }
        File(rep, "pies_fotos_reales.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("DEDOS POR PIE\n" + lines.joinToString("\n"))
    }
}
