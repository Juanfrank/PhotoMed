package com.photomed.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.photomed.app.align.Pt
import com.photomed.app.data.Zone
import com.photomed.app.ui.camera.GuideArtByZone
import com.photomed.app.ui.camera.GuideOverlay
import com.photomed.app.ui.camera.GuideWarp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GuideAdaptTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, useDeviceResolution = true)

    private val faces = JSONObject(javaClass.getResourceAsStream("/demo/faces.json")!!.bufferedReader().readText())

    private fun pts(name: String): Map<String, Pt> {
        val p = faces.getJSONObject(name).getJSONObject("pts")
        return p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
    }

    @Test
    fun warpMovesAnchorsToDetectedFeatures() {
        val art = GuideArtByZone["rostro_frontal"]!!
        val det = GuideWarp.detected(pts("face_1"))
        val w = GuideWarp.fit(art.anchors, det)!!
        for (k in listOf("cejas", "pupilas", "base nasal", "boca", "mentón")) {
            assertEquals(k, det[k]!!, w.y(art.anchors[k]!!), 1e-4f)
        }
        // escala horizontal por la distancia interpupilar
        assertEquals(det["x:pupila_der"]!!, w.x(art.anchors["x:pupila_der"]!!), 0.01f)
        assertEquals(det["x:pupila_izq"]!!, w.x(art.anchors["x:pupila_izq"]!!), 0.01f)
        // la línea pupilar de la guía se coloca a la altura detectada
        val pupil = art.lines.first { kotlin.math.abs(it.first.y - art.anchors["pupilas"]!!) < 0.004f }
        assertEquals(det["pupilas"]!!, w.line(pupil.first, pupil.second, art.anchors, det).first.y, 1e-4f)
    }

    @Test
    fun adaptedGuideOnFaces() {
        paparazzi.snapshot {
            Column(Modifier.background(Color(0xFF1B1B1F)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("face_1", "face_2", "face_3").forEach { f ->
                    val bmp = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/demo/$f.jpg"))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(false, true).forEach { adapt ->
                            Column(Modifier.width(150.dp)) {
                                Box(Modifier.aspectRatio(3f / 4f)) {
                                    Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                                    GuideOverlay(Zone.FACE_FRONTAL, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize(),
                                        adaptTo = if (adapt) pts(f) else emptyMap())
                                }
                                Text(if (adapt) "Adaptada a la referencia" else "Guía estándar", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
