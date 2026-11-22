package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.photomed.app.align.AlignOptions
import com.photomed.app.align.Aligner
import com.photomed.app.align.Detection
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.ImageOps
import com.photomed.app.align.LandmarkDetector
import com.photomed.app.align.Pt
import com.photomed.app.align.RefOrientation
import com.photomed.app.align.Similarity
import com.photomed.app.align.ZoneAlignment
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
import kotlin.math.hypot

/**
 * Alineación de Comparar en las zonas no faciales con fotos reales (de internet, solo para evaluar;
 * carpeta local `stock/zones`): cada foto («antes») y la misma movida, ampliada y girada un poco
 * («después», como otra sesión). El alineador ([Aligner.alignCore], con los puntos del cuerpo de la foto
 * si los hay y los contornos) debe deshacer el movimiento. También con la foto «después» tomada con la
 * referencia girada 90° ([RefOrientation]): la cámara mostró la referencia girada y recortada, y Comparar
 * deshace el giro antes de alinear. Informe: build/reports/zone_criteria/alineacion_cuerpo.txt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyAlignmentRealTest {
    private val root = File("src/test/resources/stock/zones")
    private val W = 360
    private val zones = listOf("manos_dorso" to Zone.HANDS_DORSAL, "manos_palma" to Zone.HANDS_PALMAR, "pies_dorso" to Zone.FEET_DORSAL,
        "pies_planta" to Zone.FEET_PLANTAR, "brazo" to Zone.ARM_R, "piernas" to Zone.LEGS, "espalda" to Zone.BACK,
        "abdomen_frontal" to Zone.TORSO_FRONT, "abdomen_lateral" to Zone.TORSO_SIDE, "gluteos" to Zone.GLUTES, "torax" to Zone.BREASTS)
    /** Movimiento entre sesiones: 3 % y −2 % del ancho, ×1,05 y 2,5° alrededor del centro. */
    private val move: Similarity = run {
        val c = Pt(0.5f, 2f / 3f); val r = Similarity(1.05f, Math.toRadians(2.5).toFloat(), 0f, 0f); val rc = r.apply(c)
        Similarity(1.05f, Math.toRadians(2.5).toFloat(), c.x - rc.x + 0.03f, c.y - rc.y - 0.02f)
    }

    /** Imagen con la semejanza [t] (en unidades del ancho) aplicada. */
    private fun warp(b: Bitmap, t: Similarity): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            setScale(t.scale, t.scale); postRotate(t.rotationDegrees); postTranslate(t.tx * b.width, t.ty * b.width)
        }
        // la matriz trabaja en píxeles: escala y giro en píxeles con la traslación del ancho
        Canvas(out).apply { drawColor(Color.rgb(128, 128, 128)); drawBitmap(b, m, Paint(Paint.FILTER_BITMAP_FLAG)) }
        return out
    }

    @Test
    fun bodyZonesAlign() {
        assumeTrue("sin fotos de zonas locales", File(root, "brazo/landmarks.json").exists())
        val rep = StringBuilder("ALINEACIÓN EN ZONAS NO FACIALES (error en % del ancho; ✓ ≤ 2 %)\n")
        var ok = 0; var n = 0; var okRot = 0; var nRot = 0
        for ((dir, zone) in zones) {
            val js = File(root, "$dir/landmarks.json"); if (!js.exists()) continue
            val o = JSONObject(js.readText())
            val spec = ZoneAlignment.of(zone)
            val errs = ArrayList<String>()
            for (name in o.keys().asSequence().sorted().filter { !it.startsWith("m_") && !it.startsWith("pm_") }.take(4)) {
                val e = o.getJSONObject(name); val w = e.getInt("w"); val h = e.getInt("h")
                val src = BitmapFactory.decodeFile(File(root, "$dir/" + e.optString("image", name)).path) ?: continue
                val b = ImageOps.centerCrop34(src, W)
                // puntos del cuerpo llevados al recorte 3:4
                val sc = maxOf(W.toFloat() / w, W * 4f / 3f / h); val ox = (w * sc - W) / 2; val oy = (h * sc - W * 4f / 3f) / 2
                val pose = e.optJSONArray("pose")?.let { a ->
                    LandmarkDetector.poseKeys((0 until a.length()).associateWith { i -> a.getJSONArray(i).let {
                        Triple((it.getDouble(0).toFloat() * w * sc - ox) / W, (it.getDouble(1).toFloat() * h * sc - oy) / W, it.getDouble(2).toFloat()) } })
                }.orEmpty().filterValues { it.x in 0f..1f && it.y in 0f..4f / 3f }
                val opts = AlignOptions(points = pose.size >= 4, contours = true, gaze = false)
                // los puntos de la foto «después», movidos igual
                val aPts = pose.mapValues { move.apply(it.value) }
                // error: un punto del centro del cuerpo, llevado de después a antes, debe volver a su sitio
                val probes = listOf(Pt(0.5f, 0.55f), Pt(0.4f, 0.7f), Pt(0.6f, 0.8f), Pt(0.5f, 0.9f))
                fun err(t: Similarity, fwd: (Pt) -> Pt) = probes.map { p -> val q = t.apply(fwd(p)); hypot(q.x - p.x, q.y - p.y) }.average().toFloat() * 100

                val a = warp(b, move)
                val r = Aligner.alignCore(spec, b, a, Detection(pose), Detection(aPts), 0f, opts)
                val e1 = err(r.transform) { move.apply(it) }
                n++; if (e1 <= 2f) ok++
                // después tomada con la referencia girada 90°: contenido girado y ampliado ×4/3 (lo que mostró la
                // cámara); Comparar lo encaja de vuelta y compone el giro
                val a90 = ImageOps.centerCrop34(ImageEdit(rot = 1).apply(a), W)
                val toA90 = RefOrientation.prior(1)
                val undo = 3
                val a0 = ImageOps.fit34(ImageEdit(rot = undo).apply(a90), W)
                val back = RefOrientation.fitPrior(undo)
                val a0Pts = aPts.mapValues { back.apply(toA90.apply(it.value)) }
                val r2 = Aligner.alignCore(spec, b, a0, Detection(pose), Detection(a0Pts), 0f, opts)
                val total = r2.transform.compose(back)
                val e2 = err(total) { toA90.apply(move.apply(it)) }
                nRot++; if (e2 <= 2f) okRot++
                errs += "$name %.1f%s / girada %.1f%s (%s)".format(e1, if (e1 <= 2f) "✓" else "✗", e2, if (e2 <= 2f) "✓" else "✗", r.method)
            }
            rep.append("${zone.id}: ${errs.joinToString("; ")}\n")
        }
        rep.append("TOTAL: $ok/$n alineadas; con la referencia girada 90°: $okRot/$nRot\n")
        println(rep)
        File("build/reports/zone_criteria").apply { mkdirs() }.resolve("alineacion_cuerpo.txt").writeText(rep.toString())
        assertTrue(rep.toString(), ok >= 0.7f * n && okRot >= 0.7f * nRot)
    }
}
