package com.photomed.app.ui.camera

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import com.photomed.app.i18n.tr
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
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
    if (g.moveX < 0) arrow(Icons.AutoMirrored.Filled.ArrowBack, Alignment.CenterStart, tr("Mover a la izquierda"))
    if (g.moveX > 0) arrow(Icons.AutoMirrored.Filled.ArrowForward, Alignment.CenterEnd, tr("Mover a la derecha"))
    if (g.moveY < 0) arrow(Icons.Default.ArrowUpward, Alignment.TopCenter, tr("Mover hacia arriba"))
    if (g.moveY > 0) arrow(Icons.Default.ArrowDownward, Alignment.BottomCenter, tr("Mover hacia abajo"))
    if (g.zoom != 0 && g.moveX == 0 && g.moveY == 0) {
        arrow(if (g.zoom > 0) Icons.Default.ZoomIn else Icons.Default.ZoomOut, Alignment.TopEnd,
            if (g.zoom > 0) tr("Acercarse") else tr("Alejarse"))
    }
}

/** Indicaciones de movimiento de la cámara: se muestran solo con flechas (y el nivel), sin texto. */
fun isCameraMove(h: String): Boolean = h.startsWith("←") || h.startsWith("↑") || h.startsWith("↓") || h.endsWith("→") ||
    h.startsWith("Mueva") || h.startsWith("Acérquese") || h.startsWith("Aléjese") || h.startsWith("Gire la cámara") || h.startsWith("Gire el teléfono")

/**
 * Franja de recomendación bajo la vista previa (no tapa la imagen): una línea de estado y, debajo,
 * una línea por cada corrección pendiente (pose, mirada, luz, distancia…). Los movimientos de la
 * cámara no se escriben: los indican las flechas del borde y el nivel.
 */
/** Correcciones pendientes (sin los movimientos de la cámara, que van con flechas). */
fun correctionItems(frame: LiveFrame?, zoomInfo: String?, distanceHint: String?): List<String> = buildList {
    addAll(frame?.eval?.hints.orEmpty().filterNot(::isCameraMove))
    distanceHint?.let { add(it) }
    zoomInfo?.let { add(it) }
}.distinct()

/**
 * Iconos de corrección sobre la imagen (abajo, translúcidos y pequeños): no ocupan espacio fuera
 * de la vista previa. Las indicaciones sin icono siguen en la franja de abajo.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CueOverlay(items: List<String>, accent: Color, modifier: Modifier = Modifier) {
    // De una en una: la corrección más importante (ver HintCues.priority) y "+N" con las demás;
    // al tocar "+N" se ven todas, y al tocar de nuevo se recogen.
    // las que no tienen icono (criterios de manos, pies y cuerpo: dedos, codo, ropa…) van como texto corto;
    // antes no se mostraban en el modo simple y la cámara no disparaba sin decir por qué
    val sorted = items.sortedBy(HintCues::priority)
    if (sorted.isEmpty()) return
    val cues = sorted.map { h -> HintCues.of(h) ?: HintCue(Icons.Default.Info, null, null, HintText.display(h)) }
    val isText = sorted.map { HintCues.of(it) == null }
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.FlowRow(
        modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (expanded) {
            cues.forEachIndexed { i, c -> if (isText[i]) TextCue(c.description) else CueChip(c, accent, compact = true) }
            if (cues.size > 1) MoreChip(Icons.Default.ExpandMore, null, tr("Mostrar solo la indicación principal")) { expanded = false }
        } else {
            if (isText[0]) TextCue(cues.first().description) else CueChip(cues.first(), accent, compact = true)
            if (cues.size > 1) MoreChip(Icons.Default.MoreHoriz, "+${cues.size - 1}",
                tr("%1\$d indicaciones más: %2\$s", cues.size - 1, cues.drop(1).joinToString("; ") { it.description })) { expanded = true }
        }
    }
}

/** Indicación sin icono, como texto corto sobre la imagen. */
@Composable
private fun TextCue(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 2,
        modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun MoreChip(icon: ImageVector, count: String?, description: String, onClick: () -> Unit) {
    Row(
        Modifier.background(Color(0x66000000), RoundedCornerShape(8.dp)).clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp).height(22.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count != null) Text(count, color = Color.White, style = MaterialTheme.typography.labelMedium)
        else Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RecommendationStrip(frame: LiveFrame?, g: Guidance?, autoCapture: Boolean, zoomInfo: String?, distanceHint: String?,
                       /** false: los iconos se dibujan sobre la imagen ([CueOverlay]); aquí solo estado y textos. */
                       showCues: Boolean = true) {
    val eval = frame?.eval
    // mismo color que el contorno (gris → rojo → naranja → verde); sin detección, blanco
    val color = GuideTone.of(eval).let { if (it == GuideTone.NORMAL) Color.White else it.color }
    val moving = g != null && (g.moveX != 0 || g.moveY != 0 || g.zoom != 0 || g.rotateDeg != 0f)
    val title = when (eval?.status) {
        MatchStatus.MATCH -> if (frame.standard) (if (autoCapture) tr("Encuadre estándar · capturando") else tr("Encuadre estándar correcto"))
            else if (autoCapture) tr("Coincide · capturando") else tr("Coincide con la referencia")
        MatchStatus.SEARCHING -> tr("Buscando al paciente…")
        null -> tr("Preparando la referencia…")
        else -> when {
            frame.standard && moving -> tr("Ajuste la pose · siga las flechas")
            frame.standard -> tr("Ajuste la pose")
            moving -> tr("Alinee con la referencia · siga las flechas")
            else -> tr("Alinee con la referencia")
        }
    }
    val items = correctionItems(frame, zoomInfo, distanceHint)
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
            Text(title, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
        eval?.pointFraction?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { it }, color = color, trackColor = Color(0x33FFFFFF),
                    modifier = Modifier.weight(1f).height(3.dp))
                Text(tr("  puntos %1\$d %% / %2\$d %%", (it * 100).toInt(), (eval.threshold * 100).toInt()), color = color,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        // Correcciones como iconos (qué + hacia dónde + valor corto); las que no tienen icono, en texto,
        // una por línea. El texto completo queda para TalkBack.
        val cues = if (showCues) items.mapNotNull { HintCues.of(it) } else emptyList()
        val texts = items.filter { HintCues.of(it) == null }
        if (cues.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { cues.forEach { CueChip(it, color) } }
        }
        texts.forEach { item ->
            Row(Modifier.padding(top = 2.dp)) {
                Text("•  ", color = Color(0xFFCAC4D0), style = MaterialTheme.typography.labelMedium)
                Text(HintText.display(item), color = Color(0xFFE6E0E9), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Ficha de una indicación: icono con insignia de dirección y valor corto ([compact]: sobre la imagen). */
@Composable
fun CueChip(cue: HintCue, accent: Color, compact: Boolean = false) {
    val box = if (compact) 22.dp else 30.dp; val ic = if (compact) 18.dp else 24.dp
    val bd = if (compact) 12.dp else 17.dp; val bi = if (compact) 9.dp else 13.dp
    Row(
        Modifier.background(if (compact) Color(0x66000000) else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 5.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = cue.description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(box)) {
            Icon(cue.icon, null, tint = Color.White.copy(alpha = if (compact) 0.9f else 1f),
                modifier = Modifier.size(ic).align(Alignment.TopStart))
            cue.badge?.let { b ->
                Box(Modifier.align(Alignment.BottomEnd).size(bd).background(accent.copy(alpha = if (compact) 0.9f else 1f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(b, null, tint = Color(0xFF1E1E22), modifier = Modifier.size(bi))
                }
            }
        }
        cue.value?.let {
            Text(it, color = Color.White, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = if (compact) 2.dp else 4.dp))
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
            if (rotateDeg != 0f) tr("Gire %1\$.1f° %2\$s", abs(rotateDeg), if (rotateDeg > 0) "↻" else "↺")
            else "%.1f° · %.0f°".format(tilt.roll, tilt.pitch),
            color = if (rotateDeg != 0f) target else color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}


/**
 * Interruptor de capa de la cámara con estado inequívoco (no solo por el tono):
 * - **Encendido**: círculo relleno del color primario, icono en contraste, insignia ✓ y etiqueta
 *   en negrita con el color primario.
 * - **Apagado**: solo contorno tenue, icono atenuado y etiqueta normal.
 * - **No disponible**: todo al 38 %.
 * El estado también se anuncia a TalkBack ("Activado"/"Desactivado").
 */
@androidx.compose.runtime.Composable
fun LayerToggle(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val on = checked && enabled
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    val disabledAlpha = if (enabled) 1f else 0.38f
    val bg by androidx.compose.animation.animateColorAsState(if (on) cs.primary else Color.Transparent, label = "bg")
    val fg by androidx.compose.animation.animateColorAsState(if (on) cs.onPrimary else Color.White.copy(alpha = 0.7f), label = "fg")
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .toggleable(on, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
            .semantics { stateDescription = if (on) tr("Activado") else tr("Desactivado"); contentDescription = label }
            .graphicsLayer { alpha = disabledAlpha }
            .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(bg)
                    .then(if (on) Modifier else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp)) }
            if (on) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(cs.primaryContainer)
                        .border(1.5.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, null,
                        tint = cs.onPrimaryContainer, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
            color = if (on) cs.primary else Color.White.copy(alpha = 0.7f),
        )
        // Barra indicadora bajo la etiqueta (como una pestaña activa).
        Box(Modifier.padding(top = 2.dp).size(width = 16.dp, height = 3.dp).clip(RoundedCornerShape(2.dp))
            .background(if (on) cs.primary else Color.Transparent))
    }
}


/** Zoom máximo ofrecido en el deslizador (más allá el zoom digital degrada la imagen clínica). */
const val MAX_MANUAL_ZOOM = 8f

/**
 * Zoom manual: deslizador (escala logarítmica, como las cámaras) con el valor actual y, si el zoom
 * asistido estaba activo y el usuario tomó el control, botón para reanudarlo. También se puede
 * hacer zoom con el pellizco sobre la vista previa.
 */
@Composable
fun ZoomBar(
    ratio: Float,
    range: ClosedFloatingPointRange<Float>,
    manual: Boolean,
    assist: Boolean,
    onZoom: (Float) -> Unit,
    onResumeAssist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lo = ln(range.start.coerceAtLeast(0.1f)); val hi = ln(range.endInclusive.coerceAtLeast(range.start + 0.01f))
    val pos = ((ln(ratio.coerceIn(range.start, range.endInclusive)) - lo) / (hi - lo)).coerceIn(0f, 1f)
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ZoomOut, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        androidx.compose.material3.Slider(
            value = pos,
            onValueChange = { onZoom(kotlin.math.exp(lo + it * (hi - lo))) },
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                .semantics { contentDescription = tr("Zoom") },
        )
        Icon(Icons.Default.ZoomIn, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        Text("%.1f×".format(ratio), color = Color.White, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(44.dp).padding(start = 6.dp))
        if (assist && manual) {
            androidx.compose.material3.TextButton(onClick = onResumeAssist, modifier = Modifier.height(36.dp)) {
                Text(tr("Auto"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
