package com.photomed.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.AnnotUserEdits
import com.photomed.app.align.FaceAnnotations
import com.photomed.app.align.FaceFrame
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.GrayImage
import com.photomed.app.align.LabImage
import com.photomed.app.align.LineMemory
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.PriorLine
import com.photomed.app.align.Pt
import com.photomed.app.align.RegionTrace
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/** Correcciones del usuario (eliminar, trazar en una zona) y referencia de fotos anteriores. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserLinesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun mesh(): List<Pt> = MutableList(478) { Pt(0.5f, 0.6f) }.apply {
        this[468] = Pt(0.4f, 0.5f); this[473] = Pt(0.6f, 0.5f)
    }

    @Test fun faceFrameRoundTripAndIndependentOfFraming() {
        val m = mesh()
        val pts = listOf(Pt(0.42f, 0.7f), Pt(0.45f, 0.75f))
        val f = FaceFrame.toFace(m, pts)!!
        val back = FaceFrame.fromFace(m, f)!!
        for (i in pts.indices) { assertEquals(pts[i].x, back[i].x, 1e-5f); assertEquals(pts[i].y, back[i].y, 1e-5f) }
        // la misma cara más pequeña y desplazada en otra foto: mismas coordenadas de la cara
        val m2 = m.map { Pt(0.1f + it.x * 0.5f, 0.2f + it.y * 0.5f) }
        val f2 = FaceFrame.toFace(m2, pts.map { Pt(0.1f + it.x * 0.5f, 0.2f + it.y * 0.5f) })!!
        for (i in f.indices) { assertEquals(f[i].x, f2[i].x, 1e-4f); assertEquals(f[i].y, f2[i].y, 1e-4f) }
    }

    @Test fun dismissAddRestoreAndMemory() {
        val u = "content://media/1"
        AnnotUserEdits.dismiss(ctx, u, "Frente, línea 2"); AnnotUserEdits.dismiss(ctx, u, "Frente, línea 2")
        assertEquals(setOf("Frente, línea 2"), AnnotUserEdits.of(ctx, u).dismissed)
        AnnotUserEdits.add(ctx, u, listOf(Pt(0.1f, 0.2f), Pt(0.3f, 0.4f)), editHash = 7, aspect = 4f / 3f)
        AnnotUserEdits.add(ctx, u, listOf(Pt(0.5f, 0.6f), Pt(0.6f, 0.7f)), editHash = 7, aspect = 4f / 3f)
        assertEquals(2, AnnotUserEdits.of(ctx, u).added.size)
        AnnotUserEdits.removeAdded(ctx, u, 0)
        assertEquals(Pt(0.5f, 0.6f), AnnotUserEdits.of(ctx, u).added.single().first())
        // si la edición de la foto cambia, los trazos anteriores se descartan
        AnnotUserEdits.add(ctx, u, listOf(Pt(0.2f, 0.2f), Pt(0.3f, 0.3f)), editHash = 8, aspect = 4f / 3f)
        assertEquals(1, AnnotUserEdits.of(ctx, u).added.size)
        AnnotUserEdits.restore(ctx, u)
        assertTrue(AnnotUserEdits.of(ctx, u).dismissed.isEmpty())
        // foto más alta que 3:4: el encuadre de Comparar recorta arriba y abajo
        val tall = AnnotUserEdits.of(ctx, u).copy(aspect = 2f).addedIn34().single()
        assertEquals(0.2f - (2f - 4f / 3f) / 2, tall.first().y, 1e-5f)
        // memoria de líneas: solo arrugas y surcos, en coordenadas de la cara
        val m = mesh()
        val lines = listOf(AnnotLine(AnnotLayer.FOLDS, listOf(Pt(0.42f, 0.7f), Pt(0.44f, 0.75f), Pt(0.45f, 0.8f)), false, metrics = LineMetrics("Nasogeniano derecho")),
            AnnotLine(AnnotLayer.EYES, listOf(Pt(0.3f, 0.5f), Pt(0.4f, 0.5f)), true, metrics = LineMetrics("Ojo derecho")))
        LineMemory.save(ctx, u, m, lines)
        val p = LineMemory.priors(ctx, listOf(u)).single()
        assertEquals("Nasogeniano derecho", p.name)
        assertEquals(0.7f, FaceFrame.fromFace(m, p.points)!!.first().y, 1e-4f)
    }

    /** Zona elegida: un surco tenue (que el detector normal no acepta) se encuentra con el umbral concesivo. */
    @Test fun regionTraceFindsAFaintFold() {
        val w = 300; val h = 400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rnd = java.util.Random(4)
        for (y in 0 until h) for (x in 0 until w) {
            // valle diagonal suave (3 % más oscuro, ~3 px) sobre piel con ruido
            val d = kotlin.math.abs((x - 100) * 0.8f - (y - 120) * 0.6f)
            val v = (170 - 6 * kotlin.math.exp(-d * d / 8.0) + rnd.nextGaussian() * 2).toInt().coerceIn(0, 255)
            bmp.setPixel(x, y, Color.rgb(v, (v * 0.8).toInt(), (v * 0.7).toInt()))
        }
        val rect = floatArrayOf(90f / w, 100f / w, 200f / w, 250f / w)
        val pts = RegionTrace.trace(bmp, rect)
        assertNotNull("no se trazó el surco", pts)
        // los puntos caen sobre el valle
        val errs = pts!!.map { p -> val x = p.x * w; val y = p.y * w; kotlin.math.abs((x - 100) * 0.8f - (y - 120) * 0.6f) }.sorted()
        println("ZONA-PTS: " + pts.joinToString { p -> "(%.0f,%.0f d=%.1f)".format(p.x * w, p.y * w, kotlin.math.abs((p.x * w - 100) * 0.8f - (p.y * w - 120) * 0.6f)) })
        println("ZONA: ${pts.size} puntos, desviación mediana ${errs[errs.size / 2]} px, máxima ${errs.last()} px")
        assertTrue("lejos del valle: ${errs[errs.size / 2]} px", errs[errs.size / 2] < 1.5f && errs.last() < 3f)
        // trazo de una longitud útil (no un punto)
        assertTrue(com.photomed.app.align.LineMeasure.length(pts) * w > 120f)
        // zona sin surco: nada
        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(170, 136, 119)) }
        assertNull(RegionTrace.trace(flat, rect))
    }

    /**
     * Referencia de la foto anterior: con menos contraste (otra luz), el análisis sin referencia pierde
     * arrugas; con las de la foto anterior como referencia recupera más y con los mismos nombres.
     */
    @Test fun previousPhotoRecoversLostWrinkles() {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos", json != null)
        var lostTotal = 0; var recovered = 0; var sameName = 0
        for (k in listOf("px_12596614", "px_14697557", "px_3823495", "px_2839371")) {
            val arr = JSONObject(json!!).optJSONArray(k) ?: continue
            val m = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val img = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/stock/new/$k.jpg"))
            val labBmp = javaClass.getResourceAsStream("/stock/new/${k}_labels.png")?.let { BitmapFactory.decodeStream(it) }
            val labels = labBmp?.let { b -> val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                FaceLabels(b.width, b.height, ByteArray(px.size) { Color.red(px[it]).toByte() }) }
            val sw = 480; val sh = img.height * sw / img.width
            val px = IntArray(sw * sh).also { Bitmap.createScaledBitmap(img, sw, sh, true).getPixels(it, 0, sw, 0, 0, sw, sh) }
            fun lum(contrast: Float): GrayImage { val g = FloatArray(px.size) { i -> val c = px[i]; (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f }
                val mean = g.average().toFloat(); return GrayImage(sw, sh, FloatArray(g.size) { mean + (g[it] - mean) * contrast }) }
            val lab = LabImage.fromArgb(px, sw, sh)
            val wr = setOf(AnnotLayer.FOLDS, AnnotLayer.FOREHEAD, AnnotLayer.GLABELLA, AnnotLayer.CROWS_FEET)
            val before = FaceAnnotations.fromMesh(m, labels, lum(1f), lab).filter { it.layer in wr && it.metrics != null }
            val priors = before.map { PriorLine(it.metrics!!.name, it.layer, FaceFrame.toFace(m, it.points)!!) }
            val low = FaceAnnotations.fromMesh(m, labels, lum(0.55f), lab).filter { it.layer in wr && it.metrics != null }
            val withRef = FaceAnnotations.fromMesh(m, labels, lum(0.55f), lab, priors = priors).filter { it.layer in wr && it.metrics != null }
            val lost = before.size - low.size
            lostTotal += lost.coerceAtLeast(0); recovered += (withRef.size - low.size).coerceAtLeast(0)
            sameName += withRef.count { w -> before.any { it.metrics!!.name == w.metrics!!.name } }
            println("REFERENCIA $k: antes ${before.size}, con menos contraste ${low.size}, con la foto anterior ${withRef.size}")
        }
        println("REFERENCIA: perdidas $lostTotal, recuperadas $recovered")
        assertTrue(lostTotal > 0)
        assertTrue("la referencia no recupera arrugas", recovered >= lostTotal * 0.5)
        assertTrue(sameName > 0)
    }
}
