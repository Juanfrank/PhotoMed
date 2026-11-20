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

    /**
     * Cara pequeña en el encuadre (como una foto frontal con pelo, cuello y margen): cada retrato se
     * coloca, a su resolución, en un lienzo 2,6 veces más ancho (cara ≈ 35 % del ancho). Analizada
     * entera a 480 px se perdían las arrugas finas; con el recorte de la cara ([FaceCrop]) se
     * recuperan casi todas las del retrato ajustado.
     */
    @Test
    fun wrinklesFoundWhenFaceIsSmallInFrame() {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos nuevos", json != null)
        val o = JSONObject(json!!)
        val wr = setOf(AnnotLayer.FOREHEAD, AnnotLayer.GLABELLA, AnnotLayer.CROWS_FEET, AnnotLayer.FOLDS)
        var tight = 0; var oldSmall = 0; var newSmall = 0; var measured = 0; var newLines = 0
        for (k in o.keys().asSequence().sorted()) {
            val arr = o.getJSONArray(k)
            val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            // retrato a ≤ 800 px de ancho (la cara sigue a ≥ 400 px): lienzos menores, menos memoria
            val img0 = javaClass.getResourceAsStream("/stock/new/$k.jpg")?.let { BitmapFactory.decodeStream(it) } ?: continue
            val img = if (img0.width <= 800) img0 else Bitmap.createScaledBitmap(img0, 800, img0.height * 800 / img0.width, true).also { img0.recycle() }
            val labBmp = javaClass.getResourceAsStream("/stock/new/${k}_labels.png")?.let { BitmapFactory.decodeStream(it) } ?: continue
            val lpx = IntArray(labBmp.width * labBmp.height).also { labBmp.getPixels(it, 0, labBmp.width, 0, 0, labBmp.width, labBmp.height) }
            fun labAt(nx: Float, ny: Float): Byte { // en unidades del ancho del retrato
                val x = (nx * labBmp.width).toInt(); val y = (ny * labBmp.width).toInt()
                return if (x in 0 until labBmp.width && y in 0 until labBmp.height) Color.red(lpx[y * labBmp.width + x]).toByte() else 0
            }
            val W = img.width; val H = img.height
            // 1) retrato ajustado (referencia), como en annotateNewRealFaces
            fun lum480(b: Bitmap): GrayImage { val sw = 480; val sh = b.height * sw / b.width
                val sm = Bitmap.createScaledBitmap(b, sw, sh, true); val px = IntArray(sw * sh).also { sm.getPixels(it, 0, sw, 0, 0, sw, sh) }
                return GrayImage(sw, sh, FloatArray(px.size) { i -> val c = px[i]; (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f }) }
            val labelsTight = FaceLabels(labBmp.width, labBmp.height, ByteArray(lpx.size) { Color.red(lpx[it]).toByte() })
            tight += FaceAnnotations.fromMesh(mesh, labelsTight, lum480(img)).count { it.layer in wr }
            // 2) cara pequeña en un lienzo mayor (misma resolución de la cara)
            val W2 = (W * 2.6f).toInt(); val H2 = (H * 1.9f).toInt(); val padX = (W2 - W) / 2; val padY = (H2 - H) / 3
            val big = Bitmap.createBitmap(W2, H2, Bitmap.Config.ARGB_8888)
            Canvas(big).apply { drawColor(Color.rgb(90, 95, 100)); drawBitmap(img, padX.toFloat(), padY.toFloat(), null) }
            val meshBig = mesh.map { Pt((it.x * W + padX) / W2, (it.y * W + padY) / W2) }
            fun labelsGrid(gw: Int, gh: Int, toPhotoPx: (Int, Int) -> Pair<Float, Float>) = FaceLabels(gw, gh, ByteArray(gw * gh) { i ->
                val (px, py) = toPhotoPx(i % gw, i / gw); labAt((px - padX) / W, (py - padY) / W) })
            // análisis anterior: foto entera a 480 px
            val l480 = lum480(big)
            val labOld = labelsGrid(480, l480.height) { x, y -> x * W2 / 480f to y * W2 / 480f }
            oldSmall += FaceAnnotations.fromMesh(meshBig, labOld, l480).count { it.layer in wr }
            // análisis nuevo: recorte de la cara
            val lines = com.photomed.app.align.FaceCrop.annotate(meshBig, big, { b, c ->
                labelsGrid(b.width, b.height) { x, y -> c.x0 + x / c.scale to c.y0 + y / c.scale } })
            val w = lines.filter { it.layer in wr }
            newSmall += w.size; newLines += w.size; measured += w.count { it.metrics?.lengthMm != null }
            // las líneas vuelven a la foto completa: dentro de la cara
            for (l in w) for (q in l.points) org.junit.Assert.assertTrue(q.x in meshBig.minOf { it.x } - 0.05f..meshBig.maxOf { it.x } + 0.05f)
            big.recycle(); img.recycle(); labBmp.recycle()
        }
        println("CARA PEQUEÑA: retrato ajustado $tight líneas; cara pequeña antes $oldSmall, ahora $newSmall ($measured con medidas)")
        org.junit.Assert.assertTrue("se pierden arrugas con la cara pequeña: $newSmall de $tight", newSmall >= tight * 0.7)
        org.junit.Assert.assertTrue(newSmall > oldSmall)
        org.junit.Assert.assertEquals(newLines, measured)
    }

    /**
     * Sin malla de MediaPipe (falla en el teléfono): con los puntos de tipo ML Kit, los surcos
     * ajustados a la sombra llevan medidas (escala interpupilar) en la mayoría de los rostros.
     */
    @Test
    fun foldsMeasuredWithoutMesh() {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos nuevos", json != null)
        val o = JSONObject(json!!)
        var faces = 0; var withMeasured = 0; var total = 0
        for (k in o.keys().asSequence().sorted()) {
            val arr = o.getJSONArray(k)
            val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val img = javaClass.getResourceAsStream("/stock/new/$k.jpg")?.let { BitmapFactory.decodeStream(it) } ?: continue
            val sw = 480; val sh = img.height * sw / img.width
            val sm = Bitmap.createScaledBitmap(img, sw, sh, true); val px = IntArray(sw * sh).also { sm.getPixels(it, 0, sw, 0, 0, sw, sh) }
            val lum = GrayImage(sw, sh, FloatArray(px.size) { i -> val c = px[i]; (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f })
            val lines = FaceAnnotations.face(FaceAnnotations.keysFromMesh(mesh), null, lum)
            val m = lines.mapNotNull { it.metrics }
            faces++; total += m.size; if (m.isNotEmpty()) withMeasured++
            m.forEach { mm -> org.junit.Assert.assertEquals("interpupilar", mm.scale); org.junit.Assert.assertTrue(mm.lengthMm!! in 3f..80f) }
            img.recycle(); sm.recycle()
        }
        println("SIN MALLA: surcos medidos en $withMeasured de $faces rostros ($total líneas)")
        org.junit.Assert.assertTrue(withMeasured >= faces * 0.7)
    }

    /**
     * Barba y bigote: la segmentación los da como piel, así que los surcos se trazaban sobre el vello.
     * Con [com.photomed.app.align.FacialHair] los surcos van solo por piel visible. Los rostros sin
     * vello no deben marcarlo. Genera build/reports/beard/ (antes | ahora, vello en magenta).
     */
    @Test
    fun foldsAvoidBeardAndMustache() {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos nuevos", json != null)
        val o = JSONObject(json!!)
        val dir = File("build/reports/beard").apply { mkdirs() }
        var bearded = 0; var onHairBefore = 0f; var onHairAfter = 0f; var cleanFalse = 0
        val report = StringBuilder()
        for (k in o.keys().asSequence().sorted()) {
            val arr = o.getJSONArray(k)
            val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val img = javaClass.getResourceAsStream("/stock/new/$k.jpg")?.let { BitmapFactory.decodeStream(it) } ?: continue
            val labBmp = javaClass.getResourceAsStream("/stock/new/${k}_labels.png")?.let { BitmapFactory.decodeStream(it) }
            val labels = labBmp?.let { b -> val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                FaceLabels(b.width, b.height, ByteArray(px.size) { Color.red(px[it]).toByte() }) }
            val sw = 480; val sh = img.height * sw / img.width
            val small = Bitmap.createScaledBitmap(img, sw, sh, true)
            val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
            val lum = GrayImage(sw, sh, FloatArray(px.size) { i -> val c = px[i]; (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f })
            val lab = com.photomed.app.align.LabImage.fromArgb(px, sw, sh)
            val hair = com.photomed.app.align.FacialHair.fromMesh(lab, mesh) ?: continue
            // antes: sin la máscara de vello (sin Lab); ahora: con ella
            val before = FaceAnnotations.fromMesh(mesh, labels, lum).filter { it.layer == AnnotLayer.FOLDS }
            val after = FaceAnnotations.fromMesh(mesh, labels, lum, lab).filter { it.layer == AnnotLayer.FOLDS }
            fun onHair(ls: List<com.photomed.app.align.AnnotLine>) = ls.flatMap { it.points }.let { pts -> if (pts.isEmpty()) 0f else hair.cover(pts) }
            report.append("$k vello ${"%.0f".format(hair.fraction * 100)} %: surcos sobre vello antes ${"%.0f".format(onHair(before) * 100)} %, ahora ${"%.0f".format(onHair(after) * 100)} %\n")
            if (hair.fraction >= 0.15f) { bearded++; onHairBefore += onHair(before); onHairAfter += onHair(after) }
            if (k in setOf("px_1066116", "px_5990259", "px_638196", "px_8727557", "px_8558898", "px_29716433")) if (hair.fraction > 0.03f) cleanFalse++
            // imagen antes | ahora (solo con vello)
            if (hair.fraction >= 0.15f) {
                val out = Bitmap.createBitmap(sw * 2, sh, Bitmap.Config.ARGB_8888); val c = Canvas(out)
                for ((j, ls) in listOf(before, after).withIndex()) {
                    c.save(); c.translate((j * sw).toFloat(), 0f); c.drawBitmap(small, 0f, 0f, null)
                    if (j == 1) { val mp = Paint().apply { color = 0x66FF00FF }
                        for (y in 0 until sh step 2) for (x in 0 until sw step 2) if (hair.mask[y * sw + x]) c.drawRect(x.toFloat(), y.toFloat(), x + 2f, y + 2f, mp) }
                    for (l in ls) { val path = android.graphics.Path(); l.points.forEachIndexed { i, q -> if (i == 0) path.moveTo(q.x * sw, q.y * sw) else path.lineTo(q.x * sw, q.y * sw) }
                        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = 0x88000000.toInt() })
                        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(255, 171, 145) }) }
                    c.restore()
                }
                File(dir, "$k.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }
            img.recycle(); small.recycle()
        }
        println(report); println("BARBA: $bearded rostros con vello; surcos sobre vello antes ${"%.0f".format(onHairBefore / bearded.coerceAtLeast(1) * 100)} %, ahora ${"%.0f".format(onHairAfter / bearded.coerceAtLeast(1) * 100)} %; falsos en rostros sin vello: $cleanFalse")
        org.junit.Assert.assertTrue(bearded >= 3)
        org.junit.Assert.assertTrue("los surcos siguen sobre el vello", onHairAfter / bearded <= 0.02f)
        org.junit.Assert.assertTrue(onHairAfter < onHairBefore)
        org.junit.Assert.assertEquals(0, cleanFalse)
    }
}
