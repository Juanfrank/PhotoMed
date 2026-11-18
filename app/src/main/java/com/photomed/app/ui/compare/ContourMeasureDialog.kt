package com.photomed.app.ui.compare

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photomed.app.ui.components.FullScreenOverlay
import com.photomed.app.align.Aligner
import com.photomed.app.align.ContourMetrics
import com.photomed.app.align.KeystoneFit
import com.photomed.app.align.MagicWand
import com.photomed.app.align.rgb
import com.photomed.app.data.Photo
import com.photomed.app.ui.formatDate
import com.photomed.app.ui.theme.ImmersiveTheme
import com.photomed.app.ui.theme.Md3Spacing
import com.photomed.app.align.Pt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Medición de contornos: selección automática (óvalo facial / silueta del sujeto, como los
 * stickers) o con varita mágica (toque + tolerancia), métricas antes/después y estimación de la
 * perspectiva vertical por proporciones de anchos. No modifica la comparación directamente: los
 * contornos elegidos quedan como configuración de la alineación automática (perspectiva por
 * proporciones de estos contornos en lugar de los detectados).
 */
@Composable
fun ContourMeasureDialog(before: Photo, after: Photo, onDismiss: () -> Unit, onContours: (List<Pt>?, List<Pt>?) -> Unit) {
    FullScreenOverlay(onDismiss = onDismiss) {
        ImmersiveTheme { ContourMeasureContent(before, after, onDismiss, onContours) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContourMeasureContent(before: Photo, after: Photo, onDismiss: () -> Unit, onContours: (List<Pt>?, List<Pt>?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aligner = remember { Aligner(context.applicationContext) }
    val bitmaps = remember { mutableStateListOf<Bitmap?>(null, null) }
    val metrics = remember { mutableStateListOf<ContourMetrics?>(null, null) }
    var active by remember { mutableIntStateOf(0) }
    var tolerance by remember { mutableFloatStateOf(12f) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val photos = listOf(before, after)
    val jobs = remember { arrayOfNulls<Job>(2) }

    // Un solo cálculo por foto a la vez: el último toque gana (evita resultados obsoletos).
    fun auto(i: Int): Job {
        val prev = jobs[i]
        return scope.launch {
            prev?.cancelAndJoin()
            busy = true
            try {
                val (bmp, mask) = aligner.autoMask(photos[i].zone, photos[i].uri)
                val m = withContext(Dispatchers.Default) { mask?.let(ContourMetrics::of) }
                ensureActive()
                bitmaps[i] = bmp; metrics[i] = m
                error = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                error = "No se pudo leer la fotografía (${e.message ?: e.javaClass.simpleName})"
            } finally { busy = false }
        }.also { jobs[i] = it }
    }

    fun wand(i: Int, x: Float, y: Float) {
        val bmp = bitmaps[i] ?: return
        val prev = jobs[i]
        val tol = tolerance
        jobs[i] = scope.launch {
            prev?.cancelAndJoin()
            busy = true
            try {
                val m = withContext(Dispatchers.Default) {
                    ContourMetrics.of(MagicWand.select(bmp.rgb(), bmp.width, bmp.height,
                        (x * bmp.width).roundToInt().coerceIn(0, bmp.width - 1), (y * bmp.height).roundToInt().coerceIn(0, bmp.height - 1), tol))
                }
                ensureActive()
                metrics[i] = m
            } finally { busy = false }
        }
    }

    LaunchedEffect(Unit) { auto(0).join(); auto(1) }
    // Los contornos elegidos se guardan como configuración de la alineación.
    LaunchedEffect(metrics[0], metrics[1]) {
        if (metrics[0] != null || metrics[1] != null) onContours(metrics[0]?.outline, metrics[1]?.outline)
    }

    val keystone = remember(metrics[0], metrics[1]) {
        val a = metrics[0]; val b = metrics[1]
        if (a != null && b != null) KeystoneFit.fromOutlines(a.outline, b.outline) else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medir contornos") },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Md3Spacing.marginCompact),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Box(Modifier.height(4.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Antes · ${formatDate(before.takenAt)}", "Después · ${formatDate(after.takenAt)}").forEachIndexed { i, l ->
                    SegmentedButton(selected = active == i, onClick = { active = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l, maxLines = 1) }
                }
            }
            Text("Toque una región para seleccionarla con la varita mágica, o use la detección automática.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium).background(Color.Black)
                    .pointerInput(active) {
                        detectTapGestures { pos -> wand(active, pos.x / size.width, pos.y / size.height) }
                    },
            ) {
                bitmaps[active]?.let {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                }
                metrics[active]?.let { m -> MetricsOverlay(m) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                AssistChip(onClick = { auto(active) }, label = { Text("Detección automática") },
                    leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) })
                Text("Tolerancia ${tolerance.roundToInt()}", style = MaterialTheme.typography.labelLarge)
            }
            Slider(tolerance, { tolerance = it }, valueRange = 4f..40f)
            MetricsTable(metrics[0], metrics[1])
            keystone?.let { k ->
                HorizontalDivider()
                Text("Perspectiva vertical estimada por proporciones: %+.1f°".format(k),
                    style = MaterialTheme.typography.titleSmall)
                Text("Inclinación que iguala las proporciones de anchos (superior, medio, inferior) de ambos contornos.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Estos contornos se usarán como base al pulsar “Alinear automáticamente” (con “Usar contornos” activo).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Box(Modifier.height(Md3Spacing.s8))
        }
    }
}

@Composable
internal fun MetricsOverlay(m: ContourMetrics) {
    val color = Color(0xFF69F0AE)
    Canvas(Modifier.fillMaxSize()) {
        fun map(x: Float, y: Float) = Offset(x * size.width, y * size.width)
        if (m.outline.size > 2) {
            val path = Path().apply {
                moveTo(m.outline[0].x * size.width, m.outline[0].y * size.width)
                m.outline.drop(1).forEach { lineTo(it.x * size.width, it.y * size.width) }
                close()
            }
            drawPath(path, color.copy(alpha = 0.18f))
            drawPath(path, Color.Black.copy(alpha = 0.5f), style = Stroke(4.dp.toPx()))
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        listOf(0.25f to m.widthUpper, 0.5f to m.widthMiddle, 0.75f to m.widthLower).forEach { (f, w) ->
            val y = m.top + f * (m.bottom - m.top)
            val cx = (m.left + m.right) / 2
            drawLine(Color(0xFFFFD54F), map(cx - w / 2, y), map(cx + w / 2, y), 2.dp.toPx(), pathEffect = dash)
        }
        val x = m.right + 0.02f
        drawLine(Color(0xFFFFD54F), map(x, m.top), map(x, m.bottom), 2.dp.toPx())
    }
}

@Composable
private fun MetricsTable(a: ContourMetrics?, b: ContourMetrics?) {
    fun pct(v: Float?) = v?.let { "%.1f".format(it * 100) } ?: "—"
    fun num(v: Float?) = v?.let { "%.3f".format(it) } ?: "—"
    fun delta(x: Float?, y: Float?) = if (x != null && y != null && x != 0f) "%+.1f%%".format((y / x - 1) * 100) else "—"
    val rows = listOf(
        Triple("Alto (% ancho de imagen)", a?.height, b?.height) to ::pct,
        Triple("Ancho máximo", a?.maxWidth, b?.maxWidth) to ::pct,
        Triple("Ancho al 25 % (sup.)", a?.widthUpper, b?.widthUpper) to ::pct,
        Triple("Ancho al 50 % (medio)", a?.widthMiddle, b?.widthMiddle) to ::pct,
        Triple("Ancho al 75 % (inf.)", a?.widthLower, b?.widthLower) to ::pct,
        Triple("Área (% del ancho²)", a?.area, b?.area) to ::pct,
        Triple("Proporción ancho/alto", a?.aspect, b?.aspect) to ::num,
        Triple("Proporción sup./inf.", a?.upperLowerRatio, b?.upperLowerRatio) to ::num,
    )
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            listOf("Medida", "Antes", "Después", "Δ").forEachIndexed { i, h ->
                Text(h, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(if (i == 0) 2f else 1f))
            }
        }
        HorizontalDivider()
        rows.forEach { (r, fmt) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(r.first, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
                Text(fmt(r.second), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(fmt(r.third), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(delta(r.second, r.third), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        Text("Las medidas absolutas son comparables si ambas fotos tienen el mismo encuadre (use la guía y la coincidencia); " +
            "las proporciones no dependen de la distancia.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
