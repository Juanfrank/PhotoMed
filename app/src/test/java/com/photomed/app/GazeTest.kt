package com.photomed.app

import android.graphics.BitmapFactory
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.photomed.app.align.Gaze
import com.photomed.app.align.Landmarks
import com.photomed.app.align.Pt
import com.photomed.app.align.rgb
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class GazeTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun pts(o: JSONObject): Map<String, Pt> {
        val p = o.getJSONObject("pts")
        return p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
    }

    @Test
    fun irisGazeOnRealFacesAndShift() {
        val faces = JSONObject(javaClass.getResourceAsStream("/eval/face_matching.json")!!.bufferedReader().readText()).getJSONObject("faces")
        val gz = faces.keys().asSequence().take(200).mapNotNull { Gaze.fromIris(pts(faces.getJSONObject(it))) }.toList()
        val medX = gz.map { abs(it.x) }.sorted()[gz.size / 2]
        println("Mirada LFW: ${gz.size} rostros, |x| mediana %.3f".format(medX))
        assertTrue(gz.size > 150 && medX < Gaze.TOL_X) // la mayoría mira a la cámara
        // desplazar ambos iris 20 % de la semianchura → la mirada cambia y deja de coincidir
        val p = pts(faces.getJSONObject(faces.keys().next()))
        val base = Gaze.fromIris(p)!!
        val eyeW = abs(p["eye_l_0"]!!.x - p["eye_l_8"]!!.x) / 2
        val moved = p.toMutableMap().apply {
            this[Landmarks.EYE_L] = Pt(p[Landmarks.EYE_L]!!.x + 0.2f * eyeW, p[Landmarks.EYE_L]!!.y)
            this[Landmarks.EYE_R] = Pt(p[Landmarks.EYE_R]!!.x + 0.2f * eyeW, p[Landmarks.EYE_R]!!.y)
        }
        val g2 = Gaze.fromIris(moved)!!
        assertEquals(0.2f, g2.x - base.x, 0.05f)
        assertTrue(!Gaze.matches(base, g2))
        assertTrue(Gaze.hint(Pt(g2.x - base.x, 0f))!!.contains("izquierda"))
    }

    @Test
    fun imageBasedIrisAgreesWithModelIris() {
        // ML Kit no da iris: se estima en la imagen. Debe concordar con el iris de MediaPipe.
        val faces = JSONObject(javaClass.getResourceAsStream("/demo/faces.json")!!.bufferedReader().readText())
        for (name in listOf("face_1", "face_2", "face_3")) {
            val p = pts(faces.getJSONObject(name))
            val bmp = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/demo/$name.jpg"))
            val model = Gaze.fromIris(p)!!
            val image = Gaze.fromImage(p, bmp.rgb(), bmp.width, bmp.height)!!
            println("$name: iris del modelo (%.2f, %.2f) · por imagen (%.2f, %.2f)".format(model.x, model.y, image.x, image.y))
            assertTrue("$name: diferencia de mirada", Gaze.matches(model, image))
        }
    }
}
