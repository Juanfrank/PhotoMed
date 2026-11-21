package com.photomed.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.FeatureMetrics
import com.photomed.app.align.MeasureGuide
import com.photomed.app.align.Pt
import com.photomed.app.ui.camera.drawMeasureGuides
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Captura: los siete estilos de trazo de los segmentos medidos y la leyenda en la ficha. */
class GuideShotTest {
    @get:Rule val paparazzi = app.cash.paparazzi.Paparazzi(deviceConfig = app.cash.paparazzi.DeviceConfig.PIXEL_5)
    @Test fun shot() {
        // nariz de una malla mínima (como en MeasureGuidesTest), con sus 5 tramos
        val m = MutableList(478) { Pt(0.5f, 0.5f) }
        fun p(x: Float, y: Float) = Pt(0.5f + x, 0.35f + y)
        m[98] = p(-0.12f, 0.45f); m[327] = p(0.12f, 0.45f); m[122] = p(-0.04f, 0.06f); m[351] = p(0.04f, 0.06f)
        m[196] = p(-0.035f, 0.2f); m[419] = p(0.035f, 0.2f); m[6] = p(0f, 0.03f); m[1] = p(0f, 0.4f); m[168] = p(0f, -0.06f); m[2] = p(0f, 0.5f)
        val nm = FeatureMetrics.noseM(m, 90f, "iris")
        val line = AnnotLine(AnnotLayer.NOSE, listOf(p(-0.05f, 0.42f), p(0f, 0.44f), p(0.05f, 0.42f)), false, metrics = nm.metrics, guides = nm.guides)
        paparazzi.snapshot {
            com.photomed.app.ui.theme.PhotoMedTheme(darkTheme = false) {
                Column(Modifier.background(Color.White)) {
                    Canvas(Modifier.width(400.dp).height(400.dp).background(Color(0xFF8D6E63))) { drawMeasureGuides(line, 1f) { p -> Offset(p.x * size.width, p.y * size.width) } }
                    com.photomed.app.ui.compare.MetricsCard(line, onTable = {}, onClose = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
