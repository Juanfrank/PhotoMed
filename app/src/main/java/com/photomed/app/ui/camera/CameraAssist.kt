package com.photomed.app.ui.camera

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photomed.app.align.Landmarks
import com.photomed.app.align.LiveFrame
import com.photomed.app.align.LiveMatch
import com.photomed.app.align.MatchEval
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Pt
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

/**
 * Indicaciones para el operador, expresadas en el sentido en que debe moverse el TELÉFONO tal como
 * se ve en pantalla. [eval] da el desplazamiento/escala/giro que necesita el contenido (en
 * coordenadas de la foto guardada); se pasa a coordenadas de pantalla según el espejo y la cámara.
 */
data class Guidance(
    /** −1 izquierda, +1 derecha, 0 nada. */
    val moveX: Int,
    /** −1 arriba, +1 abajo. */
    val moveY: Int,
    /** +1 acercarse / zoom +, −1 alejarse / zoom −. */
    val zoom: Int,
    /** Grados que hay que girar el teléfono (+ horario). */
    val rotateDeg: Float,
    val text: String,
    val extra: List<String>,
)

object CameraGuidance {
    fun of(eval: MatchEval?, frontCamera: Boolean, displayMirrored: Boolean): Guidance? {
        if (eval == null || eval.status == MatchStatus.SEARCHING || eval.status == MatchStatus.MATCH) return null
        val dxDisp = if (displayMirrored) -eval.dx else eval.dx
        // Trasera o frontal espejada: el contenido se mueve al revés que el teléfono.
        val sx = if (frontCamera && !displayMirrored) 1 else -1
        val mx = if (abs(eval.dx) > LiveMatch.MATCH_OFFSET * 0.7f) (sx * Math.signum(dxDisp)).toInt() else 0
        val my = if (abs(eval.dy) > LiveMatch.MATCH_OFFSET * 0.7f) (-Math.signum(eval.dy)).toInt() else 0
        val zoom = if (abs(ln(eval.scale)) > LiveMatch.MATCH_SCALE * 0.7f) (if (eval.scale > 1f) 1 else -1) else 0
        val rotDisp = if (displayMirrored) -eval.rotationDeg else eval.rotationDeg
        val rot = if (abs(eval.rotationDeg) > LiveMatch.MATCH_ROT * 0.7f) sx * rotDisp else 0f
        val parts = buildList {
            if (mx != 0) add(if (mx < 0) "← izquierda" else "derecha →")
            if (my != 0) add(if (my < 0) "↑ arriba" else "↓ abajo")
            if (zoom > 0) add("acérquese") else if (zoom < 0) add("aléjese")
        }
        val text = when {
            mx != 0 || my != 0 -> "Mueva el teléfono: " + parts.joinToString(" · ")
            zoom > 0 -> "Acérquese o use zoom +"
            zoom < 0 -> "Aléjese o use zoom −"
            rot != 0f -> "Gire el teléfono %.1f° %s".format(abs(rot), if (rot > 0) "↻" else "↺")
            else -> eval.hints.firstOrNull { !isTranslation(it) } ?: "Ajuste fino…"
        }
        val extra = eval.hints.filter { !isTranslation(it) && !it.startsWith("Acérquese") && !it.startsWith("Aléjese") && !it.startsWith("Gire la cámara") }
        return Guidance(mx, my, zoom, rot, text, extra)
    }

    private fun isTranslation(h: String) = h.startsWith("←") || h.startsWith("Mueva") || h.startsWith("↑") || h.startsWith("↓")

    /**
     * Distancia estimada a la cara (cm) por la distancia interpupilar media adulta (≈ 6,3 cm), la
     * focal y el zoom. Una toma a < 1 m agranda la nariz (efecto "selfie", Ward et al., JAMA Facial
     * Plast Surg 2018); se recomienda alejarse y usar zoom.
     */
    fun faceDistanceCm(points: Map<String, Pt>, focalNorm: Float?, zoom: Float): Float? {
        val l = points[Landmarks.EYE_L] ?: return null; val r = points[Landmarks.EYE_R] ?: return null
        val ipdHeight = hypot(l.x - r.x, l.y - r.y) / (4f / 3f) // en unidades de alto de imagen
        if (ipdHeight < 1e-3f) return null
        return 6.3f * (focalNorm ?: 0.75f) * zoom / ipdHeight
    }
}

/** Flechas pulsantes en el borde hacia donde mover el teléfono, e icono de zoom. */
@Composable
fun BoxScope.EdgeArrows(g: Guidance?, color: Color) {
    if (g == null) return
    val pulse by rememberInfiniteTransition(label = "pulso").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "alfa")
    @Composable
    fun arrow(icon: ImageVector, align: Alignment, desc: String) {
        Box(
            Modifier.align(align).padding(6.dp).size(40.dp).alpha(pulse)
                .background(Color(0x99000000), CircleShape).semantics { contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(28.dp)) }
    }
    if (g.moveX < 0) arrow(Icons.AutoMirrored.Filled.ArrowBack, Alignment.CenterStart, "Mover a la izquierda")
    if (g.moveX > 0) arrow(Icons.AutoMirrored.Filled.ArrowForward, Alignment.CenterEnd, "Mover a la derecha")
    if (g.moveY < 0) arrow(Icons.Default.ArrowUpward, Alignment.TopCenter, "Mover hacia arriba")
    if (g.moveY > 0) arrow(Icons.Default.ArrowDownward, Alignment.BottomCenter, "Mover hacia abajo")
    if (g.zoom != 0 && g.moveX == 0 && g.moveY == 0) {
        arrow(if (g.zoom > 0) Icons.Default.ZoomIn else Icons.Default.ZoomOut, Alignment.TopEnd,
            if (g.zoom > 0) "Acercarse" else "Alejarse")
    }
}

/** Franja de recomendación bajo la vista previa (no tapa la imagen). */
@Composable
fun RecommendationStrip(frame: LiveFrame?, g: Guidance?, autoCapture: Boolean, zoomInfo: String?, distanceHint: String?) {
    val eval = frame?.eval
    val color = when (eval?.status) {
        MatchStatus.MATCH -> GuideTone.MATCH.color
        MatchStatus.CLOSE -> GuideTone.CLOSE.color
        else -> Color.White
    }
    val title = when (eval?.status) {
        MatchStatus.MATCH -> if (frame.standard) (if (autoCapture) "Encuadre estándar · capturando" else "Encuadre estándar correcto")
            else if (autoCapture) "Coincide · capturando" else "Coincide con la referencia"
        MatchStatus.SEARCHING -> eval.hints.firstOrNull() ?: "Buscando referencias anatómicas…"
        null -> "Preparando la referencia…"
        else -> g?.text ?: if (frame?.standard == true) (eval.hints.firstOrNull() ?: "Ajuste la pose") else "Alinee con la referencia"
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .background(Color(0xFF1E1E22), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (eval?.status == MatchStatus.MATCH) {
                Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(18.dp))
                Box(Modifier.width(6.dp))
            }
            Text(title, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        eval?.pointFraction?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { it }, color = color, trackColor = Color(0x33FFFFFF),
                    modifier = Modifier.weight(1f).height(3.dp))
                Text("  puntos ${(it * 100).toInt()} % / ${(eval.threshold * 100).toInt()} %", color = color,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        val extraHint = if (frame?.standard == true) eval?.hints?.drop(1)?.firstOrNull() else g?.extra?.firstOrNull()
        val second = listOfNotNull(distanceHint, extraHint, zoomInfo).joinToString(" · ")
        if (second.isNotEmpty()) {
            Text(second, color = Color(0xFFCAC4D0), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

data class Tilt(val roll: Float, val pitch: Float)

/**
 * Nivel: horizonte actual (inclinación del teléfono) y, si hay que girar para coincidir con la
 * referencia, la línea objetivo discontinua con el ángulo a girar.
 */
@Composable
fun LevelIndicator(tilt: Tilt, rotateDeg: Float, modifier: Modifier = Modifier) {
    val level = abs(tilt.roll) < 1.5f && abs(tilt.pitch) < 3f
    val color = if (level) Color(0xFF66BB6A) else Color(0xFFFFB74D)
    val target = Color(0xFF4FC3F7)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(84.dp, 22.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            drawLine(Color(0x88FFFFFF), Offset(0f, c.y), Offset(size.width, c.y), 1.dp.toPx())
            if (rotateDeg != 0f) rotate(-tilt.roll + rotateDeg, c) {
                drawLine(target, Offset(4.dp.toPx(), c.y), Offset(size.width - 4.dp.toPx(), c.y), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            }
            rotate(-tilt.roll, c) {
                drawLine(color, Offset(14.dp.toPx(), c.y), Offset(size.width - 14.dp.toPx(), c.y), 3.dp.toPx())
            }
            drawCircle(color, 3.dp.toPx(), c)
        }
        Text(
            if (rotateDeg != 0f) "Gire %.1f° %s".format(abs(rotateDeg), if (rotateDeg > 0) "↻" else "↺")
            else "%.1f° · %.0f°".format(tilt.roll, tilt.pitch),
            color = if (rotateDeg != 0f) target else color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}
