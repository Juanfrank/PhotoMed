package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.FaceAnnotations
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.GrayImage
import com.photomed.app.align.Pt
import com.photomed.app.align.RidgeTracker
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capas estéticas con la malla completa de MediaPipe y surcos detectados (crestas Hessianas) sobre
 * retratos reales nuevos (tools/eval/new_faces.py, carpeta ignorada). Genera imágenes para revisión.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NewFacesAnnotTest {
    @Test
    fun annotateNewRealFaces() {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos nuevos", json != null)
        val o = JSONObject(json!!)
        val dir = File("build/reports/new_faces").apply { mkdirs() }
        val colors = mapOf(AnnotLayer.CONTOUR to Color.rgb(124, 245, 228), AnnotLayer.HAIRLINE to Color.rgb(255, 213, 79),
            AnnotLayer.EYES to Color.rgb(128, 222, 234), AnnotLayer.NOSE to Color.rgb(179, 157, 219),
            AnnotLayer.LIPS to Color.rgb(244, 143, 177), AnnotLayer.FOLDS to Color.rgb(255, 171, 145))
        val report = StringBuilder()
        var withNaso = 0; var faces = 0; var youngFolds = -1
        for (k in o.keys().asSequence().sorted()) {
            val arr = o.getJSONArray(k)
            val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val img = javaClass.getResourceAsStream("/stock/new/$k.jpg")?.let { BitmapFactory.decodeStream(it) }?.copy(Bitmap.Config.ARGB_8888, true) ?: continue
            val labBmp = javaClass.getResourceAsStream("/stock/new/${k}_labels.png")?.let { BitmapFactory.decodeStream(it) }
            val labels = labBmp?.let { b -> val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                FaceLabels(b.width, b.height, ByteArray(px.size) { Color.red(px[it]).toByte() }) }
            // luminancia como en la app (≤ 480 px de ancho)
            val sw = 480; val sh = img.height * sw / img.width
            val small = Bitmap.createScaledBitmap(img, sw, sh, true)
            val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
            val lum = GrayImage(sw, sh, FloatArray(px.size) { i -> val c = px[i]; (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f })
            val lines = FaceAnnotations.fromMesh(mesh, labels, lum)
            faces++
            val folds = lines.filter { it.layer == AnnotLayer.FOLDS }
            // nasogenianos: surcos cuyo inicio está junto al ala de la nariz
            if (folds.any { f -> kotlin.math.hypot(f.points.first().x - mesh[206].x, f.points.first().y - mesh[206].y) < 0.08f ||
                    kotlin.math.hypot(f.points.first().x - mesh[426].x, f.points.first().y - mesh[426].y) < 0.08f }) withNaso++
            if (k == "px_1066116") youngFolds = folds.size
            // diagnóstico de cada pasillo (sin umbrales)
            val faceW = kotlin.math.hypot(mesh[234].x - mesh[454].x, mesh[234].y - mesh[454].y)
            val diag = FaceAnnotations.FOLD_CORRIDORS.joinToString(" ") { (n, idx) ->
                val skin = labels?.let { l -> { q: Pt -> l[(q.x * l.width).toInt(), (q.y * l.width).toInt()] == FaceLabels.FACE_SKIN } }
                val r = RidgeTracker.track(lum, idx.map { mesh[it] }, FaceAnnotations.corridorHalf(n) * faceW, minStrength = 0f, minContrast = 0f, skin = skin)
                "${n.take(4)}=" + (r?.let { "%.3f/%.1f".format(it.strength, it.contrast) } ?: "—")
            }
            report.append("$k folds=${lines.count { it.layer == AnnotLayer.FOLDS }} $diag\n")
            val c = Canvas(img); val w = img.width.toFloat()
            for (l in lines) {
                val path = android.graphics.Path()
                l.points.forEachIndexed { i, q -> if (i == 0) path.moveTo(q.x * w, q.y * w) else path.lineTo(q.x * w, q.y * w) }
                if (l.closed) path.close()
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = 0x66000000; strokeJoin = Paint.Join.ROUND })
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f; color = colors.getValue(l.layer); strokeJoin = Paint.Join.ROUND })
            }
            File(dir, "$k.jpg").outputStream().use { img.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        File(dir, "report.txt").writeText(report.toString())
        println(report)
        println("SURCOS: nasogeniano en $withNaso de $faces rostros; rostro joven y liso: $youngFolds surcos")
        // la mayoría de los rostros (casi todos adultos mayores) muestran el surco nasogeniano; la piel joven y lisa, casi nada
        org.junit.Assert.assertTrue(withNaso >= faces * 0.6)
        org.junit.Assert.assertTrue(youngFolds in 0..1)
    }
}
