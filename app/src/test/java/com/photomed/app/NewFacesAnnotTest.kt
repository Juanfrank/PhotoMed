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
            AnnotLayer.LIPS to Color.rgb(244, 143, 177), AnnotLayer.FOLDS to Color.rgb(255, 171, 145),
            AnnotLayer.FOREHEAD to Color.rgb(255, 224, 130), AnnotLayer.GLABELLA to Color.rgb(255, 138, 101),
            AnnotLayer.CROWS_FEET to Color.rgb(165, 214, 167), AnnotLayer.DARK_CIRCLES to Color.rgb(206, 147, 216))
        val counts = HashMap<AnnotLayer, Int>()
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
            val lab = com.photomed.app.align.LabImage.fromArgb(px, sw, sh)
            val lines = FaceAnnotations.fromMesh(mesh, labels, lum, lab)
            report.append(k).append('\n')
            lines.mapNotNull { it.metrics }.forEach { m -> report.append("   ").append(m).append('\n') }
            // líneas con medidas (para las imágenes de ejemplo)
            val ja = org.json.JSONArray()
            lines.filter { it.metrics != null }.forEach { l -> val m = l.metrics!!
                ja.put(JSONObject().put("layer", l.layer.name).put("name", m.name).put("filled", l.filled)
                    .put("pts", org.json.JSONArray(l.points.map { org.json.JSONArray(listOf(it.x.toDouble(), it.y.toDouble())) }))
                    .put("len", m.lengthMm?.toDouble()).put("width", m.widthMm?.toDouble()).put("depth", m.depthMm?.toDouble())
                    .put("dl", m.contrastL?.toDouble()).put("grade", m.grade).put("area", m.areaMm2?.toDouble()).put("dL", m.deltaL?.toDouble())
                    .put("dE", m.deltaE?.toDouble()).put("ita", m.ita?.toDouble()).put("itaRef", m.itaRef?.toDouble()).put("type", m.type)) }
            File("build/reports/new_faces").mkdirs(); File("build/reports/new_faces/$k.json").writeText(ja.toString())
            faces++
            val folds = lines.filter { it.layer == AnnotLayer.FOLDS }
            // nasogenianos: surcos cuyo inicio está junto al ala de la nariz
            // nasogeniano: nace en el pliegue alar-facial (129 / 358)
            val naso = folds.filter { f -> listOf(129, 358).any { i -> kotlin.math.hypot(f.points.first().x - mesh[i].x, f.points.first().y - mesh[i].y) < 0.025f } }
            if (naso.isNotEmpty()) withNaso++
            lines.groupBy { it.layer }.forEach { (l, v) -> counts[l] = (counts[l] ?: 0) + v.size }
            if (k == "px_1066116") youngFolds = lines.count { it.layer in setOf(AnnotLayer.FOLDS, AnnotLayer.FOREHEAD, AnnotLayer.GLABELLA, AnnotLayer.CROWS_FEET) }
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
                if (l.filled) { c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = (colors.getValue(l.layer) and 0x00FFFFFF) or 0x48000000 }); continue }
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = 0x66000000; strokeJoin = Paint.Join.ROUND })
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f; color = colors.getValue(l.layer); strokeJoin = Paint.Join.ROUND })
            }
            File(dir, "$k.jpg").outputStream().use { img.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        File(dir, "report.txt").writeText(report.toString())
        println(report)
        println("SURCOS: nasogeniano (desde el ala) en $withNaso de $faces rostros; rostro joven y liso: $youngFolds surcos")
        println("CAPAS: " + counts.entries.sortedBy { it.key.ordinal }.joinToString { "${it.key.label}=${it.value}" })
        // la mayoría de los rostros (casi todos adultos mayores) muestran el surco nasogeniano; la piel joven y lisa, casi nada
        org.junit.Assert.assertTrue(withNaso >= faces * 0.6)
        org.junit.Assert.assertTrue(youngFolds in 0..2)
    }
}
