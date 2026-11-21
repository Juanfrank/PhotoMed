package com.photomed.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.ConfirmedTraces
import com.photomed.app.align.FreehandTrace
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.Pt
import com.photomed.app.align.StoredTrace
import com.photomed.app.align.TraceFrame
import com.photomed.app.align.TraceJson
import com.photomed.app.align.TraceMap
import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.PhotoMetadata
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.XmpFile
import com.photomed.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Trazos confirmados: coordenadas de la foto original que siguen en su sitio con cualquier giro,
 * espejo y recorte; JSON y XMP de la foto; operaciones del usuario; trazado a mano alzada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConfirmedTracesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private val edits = listOf(ImageEdit.NONE, ImageEdit(rot = 1), ImageEdit(rot = 2), ImageEdit(rot = 3), ImageEdit(mirror = true),
        ImageEdit(rot = 1, mirror = true), ImageEdit(crop = floatArrayOf(0.1f, 0.2f, 0.8f, 0.9f)),
        ImageEdit(rot = 3, mirror = true, crop = floatArrayOf(0.15f, 0.05f, 0.7f, 0.95f)),
        ImageEdit.NONE.rotated().mirrored().rotated().copy(crop = floatArrayOf(0.2f, 0.1f, 0.9f, 0.8f)))

    @Test fun mappingRoundTripsInEveryFrame() {
        val pts = listOf(Pt(0.3f, 0.4f), Pt(0.55f, 0.62f), Pt(0.71f, 0.33f))
        for (e in edits) for (f in TraceFrame.entries) for (a0 in listOf(4f / 3f, 0.75f, 1.9f)) {
            val back = TraceMap.fromView(e, a0, f, TraceMap.toView(e, a0, f, pts))
            pts.zip(back).forEach { (p, q) -> assertEquals("$e $f", p.x, q.x, 1e-4f); assertEquals(p.y, q.y, 1e-4f) }
            assertEquals(a0, TraceMap.baseAspect(e, TraceMap.editedAspect(e, a0)), 1e-4f)
        }
    }

    /** El punto llevado con [TraceMap] cae donde [ImageEdit.apply] deja el píxel marcado. */
    @Test fun mappingMatchesTheEditedImage() {
        val w = 120; val h = 160
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val u = 0.3f; val v = 0.35f
        for (dy in -1..1) for (dx in -1..1) src.setPixel((u * w).toInt() + dx, (v * h).toInt() + dy, Color.WHITE)
        for (e in edits) {
            val out = e.apply(src)
            var bx = 0f; var by = 0f; var n = 0
            for (y in 0 until out.height) for (x in 0 until out.width) if (Color.red(out.getPixel(x, y)) > 128) { bx += x; by += y; n++ }
            assertTrue("$e: punto fuera del recorte", n > 0)
            val q = TraceMap.forward(e, Pt(u + 0.5f / w, v + 0.5f / h))
            assertEquals("x con $e", bx / n + 0.5f, q.x * out.width, 1.6f)
            assertEquals("y con $e", by / n + 0.5f, q.y * out.height, 1.6f)
            // y en el encuadre del visor (unidades del ancho)
            val full = TraceMap.toView(e, h / w.toFloat(), TraceFrame.FULL, listOf(Pt(u + 0.5f / w, v + 0.5f / h))).single()
            assertEquals(bx / n + 0.5f, full.x * out.width, 1.6f); assertEquals(by / n + 0.5f, full.y * out.width, 1.6f)
        }
    }

    private fun sample(): com.photomed.app.align.TraceSet {
        val m = LineMetrics("Frente, línea 1", lengthMm = 42.25f, widthMm = 1.2f, depthMm = 0.31f, grade = 2, note = "ñ «x»",
            features = listOf("Ancho, mm" to 12.5f))
        return com.photomed.app.align.TraceSet(4f / 3f, listOf(
            StoredTrace(AnnotLayer.FOREHEAD, listOf(Pt(0.1f, 0.2f), Pt(0.3f, 0.25f)), false, false, false, m),
            StoredTrace(AnnotLayer.DARK_CIRCLES, listOf(Pt(0.4f, 0.5f), Pt(0.5f, 0.5f), Pt(0.45f, 0.55f)), true, true, false,
                LineMetrics("Ojera derecha", areaMm2 = 120f, deltaL = -4f))))
    }

    @Test fun jsonAndXmpRoundTrip() {
        val s = ConfirmedTraces.replace(sample(), "Frente, línea 1", listOf(Pt(0.11f, 0.21f), Pt(0.31f, 0.26f)), null)
        val back = TraceJson.read(TraceJson.write(s))!!
        assertEquals(s.traces.size, back.traces.size)
        assertEquals(42.25f, back.byName("Frente, línea 1")!!.metrics!!.lengthMm!!, 1e-3f)
        assertEquals(listOf("Ancho, mm" to 12.5f), back.byName("Frente, línea 1")!!.metrics!!.features)
        assertEquals(Pt(0.1f, 0.2f), back.byName("Frente, línea 1")!!.previous!!.points.first())
        assertTrue(back.traces[1].filled)
        // XMP: se añade, se reemplaza y se quita sin tocar los demás datos
        val info = com.photomed.app.data.PhotoInfo("José Núñez", "12", "e1", "rostro_frontal", "Rostro", 1L, true)
        val x1 = PhotoMetadata.withTraces(PhotoMetadata.xmp(info), TraceJson.write(s))
        assertEquals(TraceJson.write(s), PhotoMetadata.tracesOf(x1))
        assertEquals("José Núñez", PhotoMetadata.parseXmp(x1)!!.patientName)
        val x2 = PhotoMetadata.withTraces(x1, "{\"v\":1}")
        assertEquals("{\"v\":1}", PhotoMetadata.tracesOf(x2)); assertEquals(1, Regex("pm:Traces=").findAll(x2).count())
        assertNull(PhotoMetadata.tracesOf(PhotoMetadata.withTraces(x2, null)))
        assertEquals("{}", PhotoMetadata.tracesOf(PhotoMetadata.withTraces(null, "{}")))
    }

    @Test fun userOperations() {
        var s = sample()
        s = ConfirmedTraces.remove(s, "Frente, línea 1")
        assertEquals(listOf("Ojera derecha"), s.traces.map { it.name }); assertEquals(1, s.removed.size)
        s = ConfirmedTraces.restore(s); assertEquals(2, s.traces.size); assertTrue(s.removed.isEmpty())
        assertEquals("Surco añadido 1", ConfirmedTraces.nextAddedName(s))
        s = ConfirmedTraces.add(s, StoredTrace(AnnotLayer.FOLDS, listOf(Pt(0f, 0f), Pt(1f, 1f)), false, false, false, LineMetrics("Surco añadido 1"), "añadido"))
        assertEquals("Surco añadido 2", ConfirmedTraces.nextAddedName(s))
        // volver a trazar dos veces y revertir: vuelve al original
        val orig = s.byName("Frente, línea 1")!!
        s = ConfirmedTraces.replace(s, "Frente, línea 1", listOf(Pt(0.5f, 0.5f), Pt(0.6f, 0.6f)), LineMetrics("x", lengthMm = 10f))
        s = ConfirmedTraces.replace(s, "Frente, línea 1", listOf(Pt(0.7f, 0.5f), Pt(0.8f, 0.6f)), null)
        assertEquals(setOf("Frente, línea 1"), s.editedNames)
        assertEquals(10f, s.byName("Frente, línea 1")!!.metrics!!.lengthMm!!, 0f)
        s = ConfirmedTraces.revert(s, "Frente, línea 1")
        assertEquals(orig, s.byName("Frente, línea 1")); assertTrue(s.editedNames.isEmpty())
    }

    /** Se guarda en la foto (XMP) y en la app; borrada la copia de la app, se recupera de la foto. */
    @Test fun savedInThePhotoAndRecovered() {
        val repo = PhotoRepository(File(tmp.root, "p"), FilePhotoStore(File(tmp.root, "g")), openStream = { File(it.path!!).inputStream() })
        val exp = repo.createExpediente("Ana Peña", "7", "", consent = true)
        val jpeg = File(tmp.root, "c.jpg").apply { writeBytes(this@ConfirmedTracesTest.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        val photo = repo.savePhoto(exp, Zone.FACE_FRONTAL, jpeg, takenAt = 1_763_400_000_000L)
        val u = photo.uri.toString()
        // líneas tal como se ven en Comparar (3:4) con la foto girada: se guardan en la original
        PhotoEditsHelper.set(ctx, u, ImageEdit(rot = 1))
        val e = ImageEdit(rot = 1)
        val a0 = ConfirmedTraces.originalAspect(ctx, photo.uri)
        val shown = listOf(AnnotLine(AnnotLayer.FOLDS, listOf(Pt(0.2f, 0.5f), Pt(0.4f, 0.7f)), false, metrics = LineMetrics("Surco nasogeniano derecho", lengthMm = 30f)))
        val set = ConfirmedTraces.ensure(ctx, photo.uri, shown, TraceFrame.C34)
        assertTrue("no se pudo escribir en la foto", ConfirmedTraces.save(ctx, u, set))
        // el visor (foto completa) y Comparar (3:4) ven la misma línea en su sitio
        val again = ConfirmedTraces.of(ctx, u)!!
        val c34 = again.lines(e, TraceFrame.C34).single().points
        assertEquals(0.2f, c34[0].x, 1e-3f); assertEquals(0.7f, c34[1].y, 1e-3f)
        assertEquals(a0, again.baseAspect, 1e-3f)
        // los datos del paciente siguen en la foto
        assertEquals("Ana Peña", repo.readInfo(photo)!!.patientName)
        assertNotNull(PhotoMetadata.tracesOf(XmpFile.read(ctx, photo.uri)))
        // otra instalación: sin copia en la app, se recupera de la foto
        ConfirmedTraces.forget(ctx, u)
        assertEquals(30f, ConfirmedTraces.of(ctx, u)!!.traces.single().metrics!!.lengthMm!!, 1e-3f)
        // regenerar: fuera de la app y de la foto
        ConfirmedTraces.clear(ctx, u)
        assertNull(ConfirmedTraces.of(ctx, u)); assertNull(PhotoMetadata.tracesOf(XmpFile.read(ctx, photo.uri)))
        assertEquals("Ana Peña", repo.readInfo(photo)!!.patientName)
    }

    /** Surco tenue diagonal sobre piel con ruido. */
    private fun foldImage(): Bitmap {
        val w = 300; val h = 400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rnd = java.util.Random(4)
        for (y in 0 until h) for (x in 0 until w) {
            val d = abs((x - 100) * 0.8f - (y - 120) * 0.6f)
            val v = (170 - 8 * kotlin.math.exp(-d * d / 8.0) + rnd.nextGaussian() * 2).toInt().coerceIn(0, 255)
            bmp.setPixel(x, y, Color.rgb(v, (v * 0.8).toInt(), (v * 0.7).toInt()))
        }
        return bmp
    }
    private fun distToFold(p: Pt, w: Int) = abs((p.x * w - 100) * 0.8f - (p.y * w - 120) * 0.6f)

    @Test fun freehandLineSnapsToTheFold() {
        val bmp = foldImage(); val w = bmp.width
        // línea dibujada a mano, unos 5 px al lado del surco y algo temblorosa
        val stroke = (0..30).map { i -> val t = i / 30f; val x = 110f + 90f * t; val y = 120f + (x - 100f) * 0.8f / 0.6f
            Pt((x + 5f + (if (i % 2 == 0) 1.5f else -1.5f)) / w, (y - 2f) / w) }
        val r = FreehandTrace.trace(bmp, stroke)!!
        assertTrue("se esperaba ajustado a la sombra", r.snapped)
        val errs = r.points.map { distToFold(it, w) }.sorted()
        assertTrue("lejos del surco: ${errs[errs.size / 2]} px", errs[errs.size / 2] < 2f)
        assertFalse(FreehandTrace.isArea(stroke))
        // sin surco: se usa el trazo del usuario
        val flat = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(170, 136, 119)) }
        val r2 = FreehandTrace.trace(flat, stroke)!!
        assertFalse(r2.snapped); assertTrue(FreehandTrace.length(r2.points) > 0.8f * FreehandTrace.length(stroke))
    }

    @Test fun freehandAreaFindsTheFoldInside() {
        val bmp = foldImage(); val w = bmp.width
        // óvalo alrededor de un tramo del surco
        val c = Pt(150f, 120f + 50f * 0.8f / 0.6f)
        val lasso = (0..40).map { i -> val a = i / 40.0 * 2 * Math.PI
            val ax = 0.6f; val ay = 0.8f   // eje del surco
            val px = c.x + 70f * ax * kotlin.math.cos(a).toFloat() - 25f * ay * kotlin.math.sin(a).toFloat()
            val py = c.y + 70f * ay * kotlin.math.cos(a).toFloat() + 25f * ax * kotlin.math.sin(a).toFloat()
            Pt(px / w, py / w) }
        assertTrue(FreehandTrace.isArea(lasso))
        val r = FreehandTrace.trace(bmp, lasso)
        assertNotNull(r)
        val errs = r!!.points.map { distToFold(it, w) }.sorted()
        assertTrue("lejos del surco: ${errs[errs.size / 2]} px", errs[errs.size / 2] < 2f)
        assertTrue(r.points.all { hypot(it.x * w - c.x, it.y * w - c.y) < 80f })
    }
}

/** Acceso a [com.photomed.app.align.PhotoEdits] desde las pruebas. */
object PhotoEditsHelper {
    fun set(context: Context, uri: String, e: ImageEdit) = com.photomed.app.align.PhotoEdits.set(context, uri, e)
}
