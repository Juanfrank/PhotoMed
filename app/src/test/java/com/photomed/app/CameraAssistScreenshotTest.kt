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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Flip
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.photomed.app.align.LiveFrame
import com.photomed.app.align.LiveMatch
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Pt
import com.photomed.app.align.Similarity
import com.photomed.app.data.Zone
import com.photomed.app.ui.camera.CameraGuidance
import com.photomed.app.ui.camera.EdgeArrows
import com.photomed.app.ui.camera.GuideOverlay
import com.photomed.app.ui.camera.GuideTone
import com.photomed.app.ui.camera.LevelIndicator
import com.photomed.app.ui.camera.MatchSettingsContent
import com.photomed.app.ui.camera.RecommendationStrip
import com.photomed.app.ui.camera.Tilt
import com.photomed.app.ui.theme.ImmersiveTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CameraAssistScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, useDeviceResolution = true)

    private fun pts(name: String): Map<String, Pt> {
        val p = JSONObject(javaClass.getResourceAsStream("/demo/faces.json")!!.bufferedReader().readText()).getJSONObject(name).getJSONObject("pts")
        return p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
    }

    @Test
    fun phoneDirectionsDependOnCameraAndMirror() {
        // el contenido debe ir a la derecha (dx > 0)
        val e = LiveMatch.evaluate(Similarity(tx = 0.06f), Pt(0.5f, 0.66f), pointFraction = 0.3f)
        assertEquals(-1, CameraGuidance.of(e, frontCamera = false, displayMirrored = false)!!.moveX) // trasera: teléfono a la izquierda
        assertEquals(1, CameraGuidance.of(e, frontCamera = true, displayMirrored = false)!!.moveX)   // frontal sin espejo: a la derecha
        assertEquals(1, CameraGuidance.of(e, frontCamera = true, displayMirrored = true)!!.moveX)    // frontal espejada (en pantalla el contenido va a la izq.)
        val down = LiveMatch.evaluate(Similarity(ty = 0.06f), Pt(0.5f, 0.66f), pointFraction = 0.3f)
        assertEquals(-1, CameraGuidance.of(down, frontCamera = false, displayMirrored = false)!!.moveY) // subir el teléfono
    }

    @Test
    fun assistUi() {
        val bmp = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/demo/face_2.jpg"))
        val eval = LiveMatch.evaluate(Similarity(1.05f, Math.toRadians(4.0).toFloat(), 0.05f, -0.035f), Pt(0.5f, 0.6f),
            pointFraction = 0.42f, keystoneDeg = 2f)
        val g = CameraGuidance.of(eval, frontCamera = false, displayMirrored = false)
        paparazzi.snapshot {
            ImmersiveTheme {
                Row(Modifier.background(Color.Black).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.width(240.dp)) {
                        Box(Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(4.dp))) {
                            Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                            GuideOverlay(Zone.FACE_FRONTAL, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize(),
                                tone = GuideTone.CLOSE, adaptTo = pts("face_2"))
                            LevelIndicator(Tilt(0.8f, 1f), g!!.rotateDeg, Modifier.align(Alignment.TopStart).padding(6.dp))
                            EdgeArrows(g, GuideTone.CLOSE.color)
                        }
                        RecommendationStrip(LiveFrame(eval, method = "puntos anatómicos", totalPoints = 140), g, autoCapture = true,
                            zoomInfo = "zoom asistido 1.3×", distanceHint = "≈ 45 cm: aléjese ≥ 1 m y use zoom")
                    }
                    Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(96.dp).clip(CircleShape)) {
                            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
                        }
                        Text("Icono adaptativo", color = Color.White, fontSize = 10.sp)
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) {
                            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun settings() {
        paparazzi.snapshot {
            ImmersiveTheme { Surface { MatchSettingsContent(true, LiveMatch.DEFAULT_THRESHOLD, {}, {}, true, {}) } }
        }
    }

    @Test
    fun layerToggles() {
        paparazzi.snapshot {
            ImmersiveTheme {
                Box(Modifier.background(Color.Black).padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.photomed.app.ui.camera.LayerToggle("Cuadrícula", androidx.compose.material.icons.Icons.Default.GridOn, true) {}
                        com.photomed.app.ui.camera.LayerToggle("Contorno", androidx.compose.material.icons.Icons.Default.Face, false) {}
                        com.photomed.app.ui.camera.LayerToggle("Superponer", androidx.compose.material.icons.Icons.Default.Layers, true) {}
                        com.photomed.app.ui.camera.LayerToggle("Nivel", androidx.compose.material.icons.Icons.Default.Straighten, false) {}
                        com.photomed.app.ui.camera.LayerToggle("Espejo", androidx.compose.material.icons.Icons.Default.Flip, true, enabled = false) {}
                    }
                }
            }
        }
    }
}
