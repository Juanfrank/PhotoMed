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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.photomed.app.align.ContourMetrics
import com.photomed.app.align.ImageOps
import com.photomed.app.align.MagicWand
import com.photomed.app.align.rgb
import com.photomed.app.ui.compare.MetricsOverlay
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Varita mágica sobre la ilustración frontal: toque en la mejilla → contorno facial y medidas. */
class WandScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, useDeviceResolution = true)

    @Test
    fun magicWandOnFace() {
        val src = BitmapFactory.decodeFile(File("../art/source/face_front.png").absolutePath)
        val bmp = ImageOps.centerCrop34(src, 360)
        val mask = MagicWand.select(bmp.rgb(), bmp.width, bmp.height, 120, 300, tolerance = 14f)
        val m = ContourMetrics.of(mask)!!
        paparazzi.snapshot {
            Row(Modifier.background(Color(0xFF1B1B1F)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(220.dp).aspectRatio(3f / 4f)) {
                    Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    MetricsOverlay(m)
                }
                Column(Modifier.width(150.dp)) {
                    listOf(
                        "Alto" to m.height, "Ancho máx." to m.maxWidth, "Ancho 25 %" to m.widthUpper,
                        "Ancho 50 %" to m.widthMiddle, "Ancho 75 %" to m.widthLower,
                    ).forEach { (k, v) -> Text(k + ": " + "%.1f".format(v * 100) + " %", color = Color.White, fontSize = 12.sp) }
                    Text("Ancho/alto: %.3f".format(m.aspect), color = Color.White, fontSize = 12.sp)
                    Text("Sup./inf.: %.3f".format(m.upperLowerRatio), color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
