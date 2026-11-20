package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.DepthSensor
import com.photomed.app.align.LineMeasure
import com.photomed.app.align.MeasureMeta
import com.photomed.app.align.PhotometricStereo
import com.photomed.app.align.PhotometricStereo.Pattern
import com.photomed.app.align.PsFrame
import com.photomed.app.align.Pt
import com.photomed.app.align.Relief
import com.photomed.app.align.ReliefStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReliefPipelineTest {
    private val screen = PhotometricStereo.Screen(70f, 150f)

    @Test fun depth16CenterMedian() {
        val w = 40; val h = 30
        // 300 mm con confianza total (0) en el centro; ruido de baja confianza (código 2 → 14 %) alrededor
        val data = ShortArray(w * h) { k -> val x = k % w; val y = k / w
            if (x in 14..25 && y in 10..19) 300.toShort() else ((2 shl 13) or 900).toShort() }
        assertEquals(300f, DepthSensor.parseCenter(data, w, h, w, 0.3f)!!, 0.1f)
        assertEquals(null, DepthSensor.parseCenter(ShortArray(w * h), w, h, w, 0.3f))
    }

    @Test fun storeRoundTrip() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val uri = "content://media/external/images/media/42"
        val b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF808080.toInt()) }
        ReliefStore.save(ctx, uri, b, MeasureMeta(true, irisFactor = 1.05f, distanceMm = 312f, distanceSource = "sensor de profundidad"))
        val m = ReliefStore.meta(ctx, uri)!!
        assertTrue(m.hasRelief); assertEquals(1.05f, m.irisFactor!!, 1e-4f); assertEquals("sensor de profundidad", m.distanceSource)
        assertNotNull(ReliefStore.reliefUri(ctx, uri))
        assertTrue(ReliefStore.tag(ctx, uri).startsWith("r"))
        val d = Relief.decode(b); assertEquals(0f, d[3, 3], 0.02f)
    }

    /**
     * Retrato real como albedo, cara con forma de elipsoide y una arruga horizontal en la frente de
     * 0,30 mm, iluminada por las mitades de la pantalla a 30 cm (con pequeños movimientos entre
     * tomas): el relieve llevado a la foto (normal y espejada) mide la arruga.
     */
    @Test fun endToEndOnRealFaceTexture() {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos", json != null)
        val k = "px_1066116"
        val arr = JSONObject(json!!).getJSONArray(k)
        val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        val src = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/stock/new/$k.jpg"))
        val fw = 720; val fh = (src.height * fw / src.width.toFloat()).toInt()
        val img = Bitmap.createScaledBitmap(src, fw, fh, true)
        val px = IntArray(fw * fh).also { img.getPixels(it, 0, fw, 0, 0, fw, fh) }
        val lut = FloatArray(256) { PhotometricStereo.linear(it / 255f) }
        val albedo = FloatArray(fw * fh) { val c = px[it]; 0.2126f * lut[(c shr 16) and 0xFF] + 0.7152f * lut[(c shr 8) and 0xFF] + 0.0722f * lut[c and 0xFF] }
        val irisPx = maxOf(hypot(mesh[469].x - mesh[471].x, mesh[469].y - mesh[471].y), hypot(mesh[474].x - mesh[476].x, mesh[474].y - mesh[476].y)) * fw
        val mmPx = LineMeasure.HVID_MM / irisPx
        val dist = 300f; val focalPx = dist / mmPx
        // forma: elipsoide centrado en la nariz; arruga en la frente (a media altura entre ceja y malla[10])
        val c = Pt(mesh[4].x * fw, mesh[4].y * fw)
        val yW = (mesh[10].y + mesh[9].y) / 2 * fw
        val depth = 0.30f; val sigma = 0.7f
        fun height(x: Float, y: Float): Float {
            val dx = (x - c.x) * mmPx; val dy = (y - c.y) * mmPx * 0.75f
            val base = sqrt((95f * 95f - dx * dx - dy * dy).coerceAtLeast(0f))
            val g = (y - yW) * mmPx
            return base - depth * exp(-g * g / (2 * sigma * sigma))
        }
        val rnd = java.util.Random(3)
        fun render(p: Pattern, sx: Int, sy: Int): Bitmap {
            val ls = when (p) { Pattern.DARK -> emptyList(); Pattern.FULL -> PhotometricStereo.LIT.map { PhotometricStereo.light(it, screen, dist) }; else -> listOf(PhotometricStereo.light(p, screen, dist)) }
            val out = IntArray(fw * fh)
            for (y in 0 until fh) for (x in 0 until fw) {
                val xs = (x - sx).coerceIn(1, fw - 2); val ys = (y - sy).coerceIn(1, fh - 2)
                val tx = (height(xs + 0.5f, ys.toFloat()) - height(xs - 0.5f, ys.toFloat())) / mmPx
                val ty = (height(xs.toFloat(), ys + 0.5f) - height(xs.toFloat(), ys - 0.5f)) / mmPx
                val nn = sqrt(tx * tx + ty * ty + 1)
                var e = 0.03f // luz ambiente
                for (l in ls) e += 0.5f * ((-tx * l[0] - ty * l[1] - l[2]) / nn).coerceAtLeast(0f)
                val v = (albedo[ys * fw + xs] * e * (1 + 0.003f * rnd.nextGaussian().toFloat())).coerceIn(0f, 1f)
                val g = ((if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1 / 2.4f) - 0.055f) * 255).toInt().coerceIn(0, 255)
                out[y * fw + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
            return Bitmap.createBitmap(out, fw, fh, Bitmap.Config.ARGB_8888)
        }
        val order = listOf(Pattern.DARK) + PhotometricStereo.LIT + PhotometricStereo.LIT + Pattern.DARK + Pattern.FULL
        val frames = order.mapIndexed { i, p -> PsFrame(p, render(p, if (p == Pattern.FULL) 0 else (i % 3) - 1, if (p == Pattern.FULL) 0 else (i % 2))) }
        val r = Relief.compute(frames, mesh, screen, focalPx, null)!!
        println("RELIEVE: señal/ambiente ${r.signalRatio}, válidos ${r.validFraction}, ${r.distanceMm} mm (${r.distanceSource})")
        assertTrue(r.signalRatio > 5f); assertTrue(r.validFraction > 0.7f)
        assertEquals(dist, r.distanceMm, 1f)
        // arruga a lo ancho de la frente (centro), en unidades del ancho
        val xs = (-6..6).map { (c.x + it * 6f / mmPx) / fw }
        for (mirror in listOf(false, true)) {
            val photoMesh = if (mirror) mesh.map { Pt(1f - it.x, it.y) } else mesh
            val (bmp, factor) = Relief.toPhoto(r, mesh, photoMesh, fw, fh, outW = 720)!!
            val rel = Relief.decode(bmp)
            val pts = xs.map { Pt(if (mirror) 1f - it else it, yW / fw) }
            val d = LineMeasure.reliefDepth(rel, pts, mmPerUnit = mmPx * fw, halfMm = 2f)!!
            val flat = LineMeasure.reliefDepth(rel, pts.map { Pt(it.x, it.y + 8f / mmPx / fw) }, mmPx * fw, 2f) ?: 0f
            println("RELIEVE espejo=$mirror: arruga ${"%.3f".format(d)} mm (real $depth), piel lisa ${"%.3f".format(flat)} mm, factor $factor")
            assertEquals(depth, d, 0.12f)
            assertTrue(flat < 0.1f)
            assertEquals(1f, factor!!, 0.05f)
        }
    }
}
