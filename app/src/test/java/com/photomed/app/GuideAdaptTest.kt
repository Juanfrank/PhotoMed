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
import org.junit.Assert.assertTrue
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

    private fun rotated(p: Map<String, Pt>, deg: Float, c: Pt = Pt(0.5f, 0.6667f)): Map<String, Pt> {
        val t = Math.toRadians(deg.toDouble()); val cs = kotlin.math.cos(t).toFloat(); val sn = kotlin.math.sin(t).toFloat()
        return p.mapValues { (_, v) -> Pt(c.x + cs * (v.x - c.x) - sn * (v.y - c.y), c.y + sn * (v.x - c.x) + cs * (v.y - c.y)) }
    }

    @Test
    fun rollIsDetectedAndUndone() {
        val base = pts("face_1")
        val r0 = GuideWarp.roll(base)!!.first
        for (deg in listOf(-15f, -6f, 8f, 12f)) {
            val r = GuideWarp.roll(rotated(base, deg))!!.first
            assertEquals("roll $deg", r0 + deg, r, 0.3f)
            // anclas enderezadas ≈ las del rostro original (salvo el giro propio de la foto)
            val a = GuideWarp.analyze(rotated(base, deg)).anchors
            val b = GuideWarp.analyze(base).anchors
            for (k in listOf("pupilas", "boca", "mentón")) assertEquals("$k @ $deg°", b[k]!!, a[k]!!, 0.01f)
        }
    }

    @Test
    fun outlineFollowsDetectedOval() {
        val art = GuideArtByZone["rostro_frontal"]!!
        assertTrue("contorno de la ilustración", art.outline.size >= 30)
        for (f in listOf("face_1", "face_2", "face_3")) {
            val det = GuideWarp.analyze(pts(f))
            val w = GuideWarp.fit(art.anchors, det, art.outline)!!
            assertTrue(w.followsOutline)
            // a la altura de la boca, el borde de la guía cae sobre el óvalo detectado (±1,5 % del ancho)
            val ay = art.anchors["boca"]!!
            val k = (0 until art.outline.size / 3).minBy { kotlin.math.abs(art.outline[it * 3] - ay) }
            val (dl, dr) = GuideWarp.span(det.oval, w.y(art.outline[k * 3]))!!
            assertEquals("$f izq", dl, w.x(art.outline[k * 3 + 1], art.outline[k * 3]), 0.015f)
            assertEquals("$f der", dr, w.x(art.outline[k * 3 + 2], art.outline[k * 3]), 0.015f)
        }
    }

    @Test
    fun adaptedGuideOnTiltedFaces() {
        paparazzi.snapshot {
            Row(Modifier.background(Color(0xFF1B1B1F)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("face_1" to 10f, "face_2" to -12f).forEach { (f, deg) ->
                    val src = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/demo/$f.jpg"))
                    val m = android.graphics.Matrix().apply { postRotate(deg, src.width / 2f, src.height * 0.5f) }
                    val bmp = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(bmp).apply { drawColor(android.graphics.Color.BLACK); drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
                    // mismos puntos girados alrededor del centro de la imagen (y en unidades del ancho)
                    val p = rotated(pts(f), deg, Pt(0.5f, 0.5f * src.height / src.width))
                    Box(Modifier.width(190.dp).aspectRatio(3f / 4f)) {
                        Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        GuideOverlay(Zone.FACE_FRONTAL, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize(), adaptTo = p)
                    }
                }
            }
        }
    }
}
