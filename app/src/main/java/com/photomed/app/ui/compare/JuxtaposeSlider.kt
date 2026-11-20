package com.photomed.app.ui.compare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photomed.app.align.Similarity
import androidx.compose.ui.graphics.TransformOrigin

/** Estado de zoom compartido entre ambas imágenes, para que siempre coincidan. */
data class ZoomState(val scale: Float = 1f, val offset: Offset = Offset.Zero)

/**
 * Comparador tipo "juxtapose": [before] (antes) se ve a la izquierda del divisor y
 * [after] (después) a la derecha. Un dedo mueve el divisor; dos dedos hacen zoom/paneo
 * sincronizado en ambas imágenes.
 */
@Composable
fun JuxtaposeSlider(
    before: Any,
    after: Any,
    afterTransform: Similarity? = null,
    beforeTransform: Similarity? = null,
    position: Float,
    onPositionChange: (Float) -> Unit,
    zoom: ZoomState,
    onZoomChange: (ZoomState) -> Unit,
    beforeLabel: String,
    afterLabel: String,
    modifier: Modifier = Modifier,
) {
    val handleColor = MaterialTheme.colorScheme.primary
    val currentZoom by rememberUpdatedState(zoom)
    val setPosition by rememberUpdatedState(onPositionChange)
    val setZoom by rememberUpdatedState(onZoomChange)
    Box(
        modifier
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    setPosition((down.position.x / size.width).coerceIn(0f, 1f))
                    var current = currentZoom
                    var multi = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            multi = true
                            val s = (current.scale * event.calculateZoom()).coerceIn(1f, 6f)
                            val maxX = size.width * (s - 1f) / 2f
                            val maxY = size.height * (s - 1f) / 2f
                            val o = current.offset + event.calculatePan()
                            current = ZoomState(s, Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY)))
                            setZoom(current)
                        } else if (pressed.size == 1 && !multi) {
                            setPosition((pressed[0].position.x / size.width).coerceIn(0f, 1f))
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val layer = Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom.scale; scaleY = zoom.scale
            translationX = zoom.offset.x; translationY = zoom.offset.y
        }
        ComparePhoto(after, afterTransform, "Después", layer)
        Box(Modifier.fillMaxSize().drawWithContent {
            clipRect(right = size.width * position) { this@drawWithContent.drawContent() }
        }) {
            ComparePhoto(before, beforeTransform, "Antes", layer)
        }

        // Divisor y manija
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * position
            drawLine(Color(0x55000000), Offset(x, 0f), Offset(x, size.height), 5.dp.toPx())
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            val c = Offset(x, size.height / 2)
            drawCircle(Color.White, 18.dp.toPx(), c)
            drawCircle(handleColor, 15.dp.toPx(), c)
            val a = 5.dp.toPx()
            val gap = 4.dp.toPx()
            // flechas ◀ ▶
            val left = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x - gap, c.y - a); lineTo(c.x - gap - a, c.y); lineTo(c.x - gap, c.y + a); close()
            }
            val right = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x + gap, c.y - a); lineTo(c.x + gap + a, c.y); lineTo(c.x + gap, c.y + a); close()
            }
            drawPath(left, Color.White)
            drawPath(right, Color.White)
        }

        Label(beforeLabel, Modifier.align(Alignment.TopStart))
        Label(afterLabel, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(8.dp)
            .background(Color(0x99000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * Aplica la semejanza de alineación (después → antes, unidades = ancho de la imagen):
 * con origen en (0,0), graphicsLayer compone T·R·S, igual que [Similarity.apply].
 */
fun Modifier.alignedBy(t: Similarity?): Modifier = if (t == null) this else this.graphicsLayer {
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = t.scale
    scaleY = t.scale
    rotationZ = t.rotationDegrees
    translationX = t.tx * size.width
    translationY = t.ty * size.width
}
