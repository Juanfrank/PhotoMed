package com.photomed.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.Pt
import com.photomed.app.ui.compare.MetricsPanel
import com.photomed.app.ui.theme.PhotoMedTheme
import org.junit.Rule
import org.junit.Test

/** Tabla de medidas compacta (sin interlineado) en el 20 % de alto de la pantalla. */
class CompactTableScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun line(name: String, l: Float, w: Float, layer: AnnotLayer = AnnotLayer.FOLDS) =
        AnnotLine(layer, listOf(Pt(0.3f, 0.3f), Pt(0.4f, 0.5f)), false,
            metrics = LineMetrics(name, lengthMm = l, widthMm = w, contrastL = 9f, depthMm = 0.4f, grade = 2))

    @Test
    fun compactTables() {
        val a = listOf(line("Nasogeniano derecho", 31f, 3f), line("Nasogeniano izquierdo", 29f, 2.8f),
            line("Frente, línea 1", 42f, 1.2f, AnnotLayer.FOREHEAD), line("Frente, línea 2", 38f, 1.1f, AnnotLayer.FOREHEAD),
            line("Marioneta derecha", 18f, 1.5f))
        val b = a.map { it.copy(metrics = it.metrics!!.copy(lengthMm = it.metrics!!.lengthMm!! - 2f)) }
        val h = (851 * 0.20f).dp
        paparazzi.snapshot {
            PhotoMedTheme {
                Column {
                    MetricsPanel(listOf("20 nov 2026" to a), AnnotLayer.entries.toSet(), onClose = {}, onDelete = { _, _ -> },
                        modifier = Modifier.fillMaxWidth().heightIn(max = h).padding(bottom = 8.dp))
                    MetricsPanel(listOf("1 oct 2026" to a, "20 nov 2026" to b), AnnotLayer.entries.toSet(), onClose = {}, onDelete = { _, _ -> },
                        modifier = Modifier.fillMaxWidth().heightIn(max = h))
                }
            }
        }
    }
}
