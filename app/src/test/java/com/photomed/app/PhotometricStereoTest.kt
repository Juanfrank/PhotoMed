package com.photomed.app

import com.photomed.app.align.GrayImage
import com.photomed.app.align.LineMeasure
import com.photomed.app.align.PhotometricStereo
import com.photomed.app.align.PhotometricStereo.Pattern
import com.photomed.app.align.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

class PhotometricStereoTest {
    private val screen = PhotometricStereo.Screen(70f, 150f)

    @Test fun fftRoundTrip() {
        val re = DoubleArray(64) { kotlin.math.sin(it * 0.3) + it % 5 }; val im = DoubleArray(64)
        val r0 = re.copyOf()
        PhotometricStereo.fft(re, im, false); PhotometricStereo.fft(re, im, true)
        for (i in re.indices) assertEquals(r0[i], re[i], 1e-9)
    }

    @Test fun lightsPointToTheLitHalf() {
        val l = PhotometricStereo.light(Pattern.LEFT, screen, 300f); val r = PhotometricStereo.light(Pattern.RIGHT, screen, 300f)
        val t = PhotometricStereo.light(Pattern.TOP, screen, 300f); val b = PhotometricStereo.light(Pattern.BOTTOM, screen, 300f)
        assertTrue(l[0] > 0 && r[0] < 0)          // izquierda del usuario = +x en la imagen sin espejar
        assertTrue(b[1] > t[1] && t[1] > 0)      // la pantalla está por debajo de la cámara
        assertTrue(l[2] < 0)                      // hacia la cámara
    }

    @Test fun integratesKnownSurface() {
        val n = 64
        val t = FloatArray(n * n) { k -> val x = k % n; val y = k / n; (kotlin.math.sin(2 * Math.PI * x / n) * kotlin.math.cos(2 * Math.PI * 2 * y / n)).toFloat() }
        val p = FloatArray(n * n) { k -> val x = k % n; val y = k / n; (t[y * n + (x + 1) % n] - t[y * n + (x - 1 + n) % n]) / 2 }
        val q = FloatArray(n * n) { k -> val x = k % n; val y = k / n; (t[((y + 1) % n) * n + x] - t[((y - 1 + n) % n) * n + x]) / 2 }
        val z = PhotometricStereo.frankotChellappa(p, q, n, n)
        val mz = z.average(); val mt = t.average()
        for (i in t.indices) assertEquals(t[i] - mt, z[i] - mz, 1e-3)
    }

    /**
     * Cara sintética (esfera de 80 mm de radio) con un surco gaussiano de 0,4 mm de profundidad,
     * vista a 30 cm e iluminada por las 4 mitades de la pantalla (Lambert + ruido del 0,2 %):
     * el relieve recuperado mide la profundidad del surco.
     */
    @Test fun recoversGrooveDepthFromScreenLights() {
        val n = 256; val mmPx = 0.4f; val depth = 0.4f; val sigma = 1.0f
        val rnd = java.util.Random(1)
        // altura hacia la cámara t(x, y) en mm
        val c = n / 2f
        fun height(x: Float, y: Float): Float {
            val dx = (x - c) * mmPx; val dy = (y - c) * mmPx
            val sphere = sqrt((80f * 80f - dx * dx - dy * dy).coerceAtLeast(0f))
            val gx = (x - (c + 20)) * mmPx
            return sphere - depth * exp(-gx * gx / (2 * sigma * sigma))
        }
        val mask = BooleanArray(n * n) { k -> val dx = (k % n - c) * mmPx; val dy = (k / n - c) * mmPx; dx * dx + dy * dy < 45f * 45f }
        val lights = PhotometricStereo.LIT.map { PhotometricStereo.light(it, screen, 300f) }
        val images = lights.map { l ->
            FloatArray(n * n) { k ->
                val x = (k % n).toFloat(); val y = (k / n).toFloat()
                val tx = (height(x + 0.5f, y) - height(x - 0.5f, y)) / mmPx; val ty = (height(x, y + 0.5f) - height(x, y - 0.5f)) / mmPx
                // normal hacia la cámara en coordenadas de la cámara: (−∂t/∂x, −∂t/∂y, −1)
                val nn = sqrt(tx * tx + ty * ty + 1)
                val v = 0.6f * ((-tx * l[0] - ty * l[1] - l[2]) / nn).coerceAtLeast(0f)
                v * (1f + 0.002f * rnd.nextGaussian().toFloat())
            }
        }
        val nm = PhotometricStereo.solve(images, lights, n, n, mask)
        assertTrue(nm.valid.count { it } > mask.count { it } * 0.9)
        val tpx = PhotometricStereo.heights(nm)
        val rel = PhotometricStereo.relief(tpx, n, n, 4f / mmPx, nm.valid)
        val reliefMm = GrayImage(n, n, FloatArray(n * n) { if (rel[it].isNaN()) Float.NaN else rel[it] * mmPx })
        // trazado del surco (vertical) en unidades del ancho
        val xg = (c + 20) / n
        val pts = (0..10).map { Pt(xg, (c - 40 + it * 8) / n) }
        val d = LineMeasure.reliefDepth(reliefMm, pts, mmPerUnit = n * mmPx, halfMm = 3f)!!
        println("SURCO: real $depth mm, recuperado $d mm")
        assertEquals(depth, d, 0.12f)
        // piel lisa (sin surco) a la izquierda: casi nada
        val flat = LineMeasure.reliefDepth(reliefMm, pts.map { Pt(it.x - 40f / n, it.y) }, n * mmPx, 3f)!!
        println("LISO: $flat mm")
        assertTrue("liso: $flat", flat < 0.08f)
    }

    @Test fun alignsShiftedFrames() {
        val n = 128
        val img = FloatArray(n * n) { k -> val x = k % n; val y = k / n; (0.5 + 0.3 * kotlin.math.sin(x * 0.21) * kotlin.math.cos(y * 0.17) + if ((x / 16 + y / 16) % 2 == 0) 0.1 else 0.0).toFloat() }
        val moved = PhotometricStereo.shift(img, n, n, 5, -3)
        // con otra iluminación (ganancia distinta): la alineación usa bordes normalizados
        val other = FloatArray(n * n) { moved[it] * 0.6f }
        // desplazamiento que devuelve la toma sobre la referencia
        assertEquals(-5 to 3, PhotometricStereo.align(img, other, n, n, maxShift = 12))
    }
}
