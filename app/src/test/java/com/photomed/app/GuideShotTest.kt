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
        val gs = listOf("ancho alar" to 34.2f, "puente" to 10.7f, "dorso" to 9.9f, "longitud" to 36.1f, "alto" to 50.8f, "extra" to 5f, "extra2" to 4f)
        val line = AnnotLine(AnnotLayer.NOSE, listOf(Pt(0.4f, 0.5f), Pt(0.6f, 0.5f)), false, metrics = FeatureMetrics.noseM(List(478) { Pt(0.5f, 0.5f) }, 1f, "iris").metrics,
            guides = listOf(MeasureGuide("", Pt(0.1f, 0.05f), Pt(0.1f, 0.2f))) + gs.mapIndexed { i, (l, v) -> MeasureGuide(FeatureMetrics.mmText(l, v), Pt(0.1f, 0.25f + i * 0.1f), Pt(0.9f, 0.25f + i * 0.1f)) })
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
