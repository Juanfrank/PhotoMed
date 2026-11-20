package com.photomed.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.FaceAnnotations
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.PhotoEdits
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Edición no destructiva (giro, espejo, recorte) y anotaciones estéticas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageEditTest {
    /** 30×40 con un píxel rojo arriba a la izquierda y uno azul abajo a la derecha. */
    private fun probe(): Bitmap = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE); setPixel(0, 0, Color.RED); setPixel(29, 39, Color.BLUE)
    }

    @Test
    fun rotateMirrorCrop() {
        val r = ImageEdit(rot = 1).apply(probe())
        assertEquals(40, r.width); assertEquals(30, r.height)
        assertEquals(Color.RED, r.getPixel(39, 0))            // giro horario: arriba-izquierda → arriba-derecha
        val m = ImageEdit(mirror = true).apply(probe())
        assertEquals(Color.RED, m.getPixel(29, 0))
        val c = ImageEdit(crop = floatArrayOf(0.5f, 0.5f, 1f, 1f)).apply(probe())
        assertEquals(15, c.width); assertEquals(20, c.height)
        assertEquals(Color.BLUE, c.getPixel(14, 19))
        // cuatro giros = original; el espejo refleja también el recorte
        var e = ImageEdit(); repeat(4) { e = e.rotated() }
        assertTrue(e.isIdentity)
        val mc = ImageEdit(crop = floatArrayOf(0.1f, 0.2f, 0.4f, 0.9f)).mirrored()
        assertEquals(0.6f, mc.crop!![0], 1e-5f); assertEquals(0.9f, mc.crop!![2], 1e-5f)
        assertEquals(mc, ImageEdit.fromJson(JSONObject(mc.toJson().toString())))
    }

    @Test
    fun editsPersistAndApplyOnLoad() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = java.io.File(ctx.cacheDir, "p.png")
        Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE)
            for (y in 0 until 60) for (x in 0 until 60) setPixel(x, y, Color.RED) }
            .let { b -> f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val uri = android.net.Uri.fromFile(f)
        PhotoEdits.set(ctx, uri.toString(), ImageEdit(mirror = true))
        PhotoEdits.clearCache()
        assertEquals(ImageEdit(mirror = true), PhotoEdits.of(ctx, uri.toString()))
        // Coil necesita el hilo principal: la carga va en otro hilo y aquí se procesa el looper
        val fut = java.util.concurrent.Executors.newSingleThreadExecutor().submit<Bitmap> {
            kotlinx.coroutines.runBlocking { com.photomed.app.align.ImageOps.loadFull(ctx, uri, 400) }
        }
        val end = System.currentTimeMillis() + 30_000
        while (!fut.isDone && System.currentTimeMillis() < end) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10) }
        val full = fut.get(1, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue("espejo aplicado al cargar", Color.red(full.getPixel(full.width - 5, 5)) > 200 && Color.green(full.getPixel(full.width - 5, 5)) < 60)
        PhotoEdits.set(ctx, uri.toString(), ImageEdit.NONE)
        assertTrue(PhotoEdits.of(ctx, uri.toString()).isIdentity)
    }

    @Test
    fun annotationsOnDemoFace() {
        val o = JSONObject(javaClass.getResourceAsStream("/demo/faces.json")!!.bufferedReader().readText())
        val p = o.getJSONObject(o.keys().next()).getJSONObject("pts")
        val pts = p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        val lines = FaceAnnotations.face(pts, labels = null, lum = null)
        val layers = lines.map { it.layer }.toSet()
        // sin segmentación no hay línea del pelo; el resto, sí (surcos estimados, sin imagen)
        assertTrue(layers.toString(), layers.containsAll(listOf(AnnotLayer.CONTOUR, AnnotLayer.EYES, AnnotLayer.NOSE, AnnotLayer.LIPS, AnnotLayer.FOLDS)))
        assertTrue(lines.filter { it.layer == AnnotLayer.FOLDS }.all { it.estimated })
        assertEquals(6, lines.count { it.layer == AnnotLayer.FOLDS })
    }
}
