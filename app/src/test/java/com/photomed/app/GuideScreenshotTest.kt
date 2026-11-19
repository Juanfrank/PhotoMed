package com.photomed.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.photomed.app.data.Zone
import com.photomed.app.ui.camera.GuideOverlay
import org.junit.Rule
import org.junit.Test

/** Renderiza todas las guías anatómicas para revisión visual (./gradlew recordPaparazziDebug). */
class GuideScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3400), useDeviceResolution = true)

    @Test
    fun allGuides() {
        paparazzi.snapshot {
            Column(Modifier.background(Color(0xFF263238)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Zone.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { zone ->
                            Column(Modifier.width(92.dp)) {
                                Box(Modifier.aspectRatio(3f / 4f).background(Color(0xFF455A64))) {
                                    GuideOverlay(zone, showContour = true, showGrid = false, modifier = Modifier.matchParentSize())
                                }
                                Text(zone.label, color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

class GuideToneScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, useDeviceResolution = true)

    @Test
    fun tones() {
        paparazzi.snapshot {
            Row(Modifier.background(Color(0xFF1B1B1F)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    com.photomed.app.ui.camera.GuideTone.NORMAL to "Sin detección",
                    com.photomed.app.ui.camera.GuideTone(com.photomed.app.ui.camera.GuideTone.gradient(0.1f)) to "Lejos",
                    com.photomed.app.ui.camera.GuideTone(com.photomed.app.ui.camera.GuideTone.gradient(0.5f)) to "Cerca",
                    com.photomed.app.ui.camera.GuideTone(com.photomed.app.ui.camera.GuideTone.gradient(0.8f)) to "Casi",
                    com.photomed.app.ui.camera.GuideTone.MATCH to "Coincide",
                ).forEach { (tone, label) ->
                    Column(Modifier.width(68.dp)) {
                        Box(Modifier.aspectRatio(3f / 4f).background(Color(0xFF455A64))) {
                            GuideOverlay(Zone.FACE_FRONTAL, showContour = true, showGrid = false,
                                modifier = Modifier.matchParentSize(), tone = tone)
                        }
                        Text(label, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
