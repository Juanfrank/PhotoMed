package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.FaceAnnotations
import com.photomed.app.align.FaceLabels
import com.photomed.app.align.GrayImage
import com.photomed.app.align.Jawline
import com.photomed.app.align.Mask
import com.photomed.app.align.Pt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Línea mandibular (nitidez, rectitud, ángulo goníaco), papada y ángulo cervicomentoniano de perfil. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JawlineTest {
    /** Perfil sintético mirando a la derecha: mentón, submentón casi horizontal y cuello vertical. */
    private fun profileMask(subDrop: Int, frontRight: Boolean = true): Mask {
        val w = 200; val h = 300
        val m = Mask(w, h)
        for (y in 20 until h) {
            val front = when {
                y < 110 -> 120
                y < 150 -> 120 + (20 - kotlin.math.abs(y - 130)) // nariz
                y < 175 -> 118
                y < 200 -> 130                                   // mentón
                y < 200 + subDrop -> 130 - (30 * (y - 200) / subDrop) // submentón: retrocede 30 px
                else -> 100                                      // cuello
            }
            for (x in 40 until front) { val xx = if (frontRight) x else w - 1 - x; m.data[y * w + xx] = true }
        }
        return m
    }

    @Test
    fun cervicomentalAngleFromSilhouette() {
        // submentón que baja 12 px en 30 px de fondo: ángulo ≈ 90 + atan(12/30) ≈ 112°
        fun angle(drop: Int, right: Boolean) = Jawline.profile(profileMask(drop, right), 200, 300)
            .first { it.layer == AnnotLayer.JAWLINE }.metrics!!.features.first().second
        val a = angle(12, true)
        assertTrue("ángulo $a", a in 100f..125f)
        // mirando a la izquierda: el mismo ángulo
        assertEquals(a, angle(12, false), 3f)
        // submentón más inclinado (cuello «borrado», papada): ángulo mayor
        val b = angle(40, true)
        assertTrue("más obtuso $b > $a", b > a + 10f)
        println("CERVICOMENTONIANO: definido %.0f°, borrado %.0f°".format(a, b))
        // la papada de perfil sale con su convexidad
        assertTrue(Jawline.profile(profileMask(12), 200, 300).any { it.layer == AnnotLayer.PAPADA })
    }

    @Test
    fun jawlineOnRealFaces() {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos nuevos", json != null)
        val o = JSONObject(json!!)
        var n = 0; var gonial = 0
        for (k in o.keys().asSequence().sorted()) {
            val arr = o.getJSONArray(k)
            val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            val img = javaClass.getResourceAsStream("/stock/new/$k.jpg")?.let { BitmapFactory.decodeStream(it) }?.copy(Bitmap.Config.ARGB_8888, true) ?: continue
            val labBmp = javaClass.getResourceAsStream("/stock/new/${k}_labels.png")?.let { BitmapFactory.decodeStream(it) }
            val labels = labBmp?.let { b -> val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                FaceLabels(b.width, b.height, ByteArray(px.size) { Color.red(px[it]).toByte() }) }
            val lines = com.photomed.app.align.FaceCrop.annotate(mesh, img, { _, _ -> labels })
            val jaw = lines.firstOrNull { it.layer == AnnotLayer.JAWLINE } ?: continue
            n++
            val f = jaw.metrics!!.features.toMap()
            f.filterKeys { it.startsWith("Rectitud") }.values.forEach { assertTrue("$k rectitud $it", it in 70f..100f) }
            f.filterKeys { it.startsWith("Ángulo goníaco") }.values.forEach { gonial++; assertTrue("$k goníaco $it", it in 95f..160f) }
            f["Nitidez: contraste cara / cuello, %"]?.let { assertTrue("$k contraste $it", it in 0f..100f) }
            println("MANDÍBULA $k: " + f.entries.joinToString("; ") { it.key.substringBefore(",") + " " + String.format("%.1f", it.value) } +
                (lines.firstOrNull { it.layer == AnnotLayer.PAPADA }?.let { " · papada" } ?: ""))
        }
        assertTrue("sin línea mandibular", n >= 10)
        println("MANDÍBULA: $n caras, $gonial con ángulo goníaco")
    }
}
