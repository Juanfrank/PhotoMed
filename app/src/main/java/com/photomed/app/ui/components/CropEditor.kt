package com.photomed.app.ui.components

import com.photomed.app.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Editor de recorte a pantalla completa: la imagen ajustada a la pantalla y un rectángulo con
 * esquinas que se arrastran (o se mueve arrastrando por dentro). [aspect]: ancho/alto fijo (p. ej.
 * 3/4) o null para libre. Devuelve el rectángulo normalizado (x0, y0, x1, y1) sobre la imagen.
 */
@Composable
fun CropEditor(image: ImageBitmap, aspect: Float?, initial: FloatArray?, onCancel: () -> Unit, onApply: (FloatArray) -> Unit) {
    var r by remember {
        mutableStateOf(initial?.copyOf() ?: run {
            val ia = image.width / image.height.toFloat()
            if (aspect == null) floatArrayOf(0.08f, 0.08f, 0.92f, 0.92f)
            else {
                // el mayor rectángulo centrado con esa proporción, con un margen
                val w = if (aspect > ia) 0.9f else 0.9f * aspect / ia
                val h = w * ia / aspect
                floatArrayOf(0.5f - w / 2, 0.5f - h / 2, 0.5f + w / 2, 0.5f + h / 2)
            }
        })
    }
    var fit by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text(tr("Recortar"), color = Color.White, modifier = Modifier.padding(16.dp))
            Canvas(
                Modifier.weight(1f).fillMaxWidth().padding(16.dp)
                    .semantics { contentDescription = tr("Área de recorte") }
                    .pointerInput(aspect) {
                        var mode = -1   // 0..3 esquinas, 4 = mover
                        detectDragGestures(
                            onDragStart = { pos ->
                                val corners = listOf(Offset(fit.left + r[0] * fit.width, fit.top + r[1] * fit.height), Offset(fit.left + r[2] * fit.width, fit.top + r[1] * fit.height),
                                    Offset(fit.left + r[2] * fit.width, fit.top + r[3] * fit.height), Offset(fit.left + r[0] * fit.width, fit.top + r[3] * fit.height))
                                val near = corners.indexOfFirst { (it - pos).getDistance() < 36.dp.toPx() }
                                mode = if (near >= 0) near else 4
                            },
                            onDrag = { ch, d ->
                                ch.consume()
                                val dx = d.x / fit.width; val dy = d.y / fit.height
                                val n = r.copyOf()
                                val minS = 0.08f
                                when (mode) {
                                    4 -> {
                                        val w = n[2] - n[0]; val h = n[3] - n[1]
                                        n[0] = (n[0] + dx).coerceIn(0f, 1f - w); n[1] = (n[1] + dy).coerceIn(0f, 1f - h); n[2] = n[0] + w; n[3] = n[1] + h
                                    }
                                    else -> {
                                        val xi = if (mode == 0 || mode == 3) 0 else 2; val yi = if (mode == 0 || mode == 1) 1 else 3
                                        n[xi] = (n[xi] + dx).coerceIn(0f, 1f); n[yi] = (n[yi] + dy).coerceIn(0f, 1f)
                                        if (aspect != null) {
                                            // proporción fija: el alto sigue al ancho (en píxeles de la imagen)
                                            val ia = image.width / image.height.toFloat()
                                            val wN = abs(n[2] - n[0]); val hN = wN * ia / aspect
                                            if (yi == 1) n[1] = n[3] - hN else n[3] = n[1] + hN
                                        }
                                        if (n[2] - n[0] < minS || n[3] - n[1] < minS || n[1] < -1e-3f || n[3] > 1f + 1e-3f) return@detectDragGestures
                                    }
                                }
                                r = n
                            },
                        )
                    }
            ) {
                val ia = image.width / image.height.toFloat()
                val w = minOf(size.width, size.height * ia); val h = w / ia
                fit = Rect(Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h))
                drawImage(image, dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()))
                val c = Rect(fit.left + r[0] * w, fit.top + r[1] * h, fit.left + r[2] * w, fit.top + r[3] * h)
                val dim = Color(0x99000000)
                drawRect(dim, fit.topLeft, Size(w, c.top - fit.top))
                drawRect(dim, Offset(fit.left, c.bottom), Size(w, fit.bottom - c.bottom))
                drawRect(dim, Offset(fit.left, c.top), Size(c.left - fit.left, c.height))
                drawRect(dim, Offset(c.right, c.top), Size(fit.right - c.right, c.height))
                for (i in 1..2) {
                    drawLine(Color(0x88FFFFFF), Offset(c.left + c.width * i / 3, c.top), Offset(c.left + c.width * i / 3, c.bottom), 1.dp.toPx())
                    drawLine(Color(0x88FFFFFF), Offset(c.left, c.top + c.height * i / 3), Offset(c.right, c.top + c.height * i / 3), 1.dp.toPx())
                }
                drawRect(Color.White, c.topLeft, c.size, style = Stroke(2.dp.toPx()))
                listOf(c.topLeft, c.topRight, c.bottomRight, c.bottomLeft).forEach { drawCircle(Color.White, 8.dp.toPx(), it) }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(tr("Cancelar")) }
                Button(onClick = { onApply(r.copyOf()) }, modifier = Modifier.weight(1f)) { Text(tr("Aplicar")) }
            }
        }
    }
}
