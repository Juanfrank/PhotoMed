package com.photomed.app.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.photomed.app.ui.components.SafeBottomSheet
import com.photomed.app.ui.theme.Md3Spacing
import kotlinx.coroutines.launch

/** Qué exportar (una sola opción a la vez). */
enum class ExportKind(val label: String, val needsTwo: Boolean) {
    INDIVIDUAL("Fotos ajustadas (una por foto)", false),
    GRID("Cuadrícula con fechas", true),
    SIDE("Lado a lado (una fila)", true),
    GIF("Animación GIF", true),
}

data class ExportChoice(
    val kind: ExportKind,
    val gif: CompareExport.GifMode = CompareExport.GifMode.SLIDE,
    val timing: CompareExport.GifTiming = CompareExport.GifTiming(),
    val anonymize: Boolean = true,
)

/**
 * Exportación de la comparación: una opción (fotos ajustadas, cuadrícula, lado a lado o GIF); sin
 * datos del paciente por defecto; guardar en la galería o compartir. Con GIF: modo, tiempo por
 * imagen, tiempo de transición y vista previa animada con esos mismos tiempos ([loadPreview]: fotos
 * ajustadas en tamaño reducido con su etiqueta).
 * [onExport] devuelve un mensaje para mostrar (o null) y recibe un callback de progreso.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    count: Int,
    aligned: Boolean,
    colorMatched: Boolean,
    onDismiss: () -> Unit,
    cropped: Boolean = false,
    loadPreview: suspend () -> List<Pair<android.graphics.Bitmap, String>> = { emptyList() },
    onExport: suspend (choice: ExportChoice, toGallery: Boolean, progress: (Int, Int) -> Unit) -> String?,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(if (count >= 2) ExportKind.GRID else ExportKind.INDIVIDUAL) }
    var gifMode by remember { mutableStateOf(CompareExport.GifMode.SLIDE) }
    var holdS by remember { mutableStateOf(1.2f) }
    var transS by remember { mutableStateOf(0.7f) }
    var anonymize by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val timing = CompareExport.GifTiming((holdS * 1000).roundToInt(), (transS * 1000).roundToInt())

    fun run(toGallery: Boolean) {
        if (progress != null) return
        progress = 0f
        scope.launch {
            val msg = runCatching {
                onExport(ExportChoice(kind, gifMode, timing, anonymize), toGallery) { i, n -> progress = if (n > 0) i / n.toFloat() else 1f }
            }.getOrElse { "No se pudo exportar: ${it.message ?: it.javaClass.simpleName}" }
            progress = null
            msg?.let(onMessage)
            onDismiss()
        }
    }

    SafeBottomSheet(onDismiss = { if (progress == null) onDismiss() }) {
        Column(Modifier.padding(horizontal = Md3Spacing.s6).padding(bottom = Md3Spacing.s6)
            .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
            Text("Exportar imágenes ajustadas", style = MaterialTheme.typography.titleLarge)
            Text(
                buildList {
                    add("$count fotos en el encuadre de la referencia")
                    add(if (aligned) "alineación aplicada" else "sin alinear (pulse “Alinear automáticamente” antes para exportarlas alineadas)")
                    if (colorMatched) add("color igualado")
                    if (cropped) add("recortadas según la alineación")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                ExportKind.entries.forEach { k ->
                    val enabled = !k.needsTwo || count >= 2
                    Row(Modifier.fillMaxWidth().selectable(kind == k, enabled = enabled, role = Role.RadioButton) { kind = k }
                        .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == k, onClick = null, enabled = enabled)
                        Text(k.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp),
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (kind == ExportKind.GIF && count >= 2) {
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CompareExport.GifMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = gifMode == m, onClick = { gifMode = m },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, CompareExport.GifMode.entries.size),
                        ) { Text(m.label) }
                    }
                }
                Text("Tiempo por imagen: %.1f s".format(holdS), style = MaterialTheme.typography.bodyMedium)
                Slider(holdS, { holdS = (it * 10).roundToInt() / 10f }, valueRange = 0.3f..5f,
                    modifier = Modifier.semantics { contentDescription = "Tiempo por imagen" })
                Text(if (transS < 0.05f) "Transición: sin transición (corte)" else "Transición: %.1f s".format(transS),
                    style = MaterialTheme.typography.bodyMedium)
                Slider(transS, { transS = (it * 10).roundToInt() / 10f }, valueRange = 0f..3f,
                    modifier = Modifier.semantics { contentDescription = "Tiempo de transición" })
                GifPreview(loadPreview, gifMode, timing, Modifier.align(Alignment.CenterHorizontally))
            }
            Row(Modifier.fillMaxWidth().toggleable(anonymize, role = Role.Switch) { anonymize = it }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sin datos del paciente", style = MaterialTheme.typography.bodyLarge)
                    Text("Nombres sin el nombre del paciente y sin metadatos de identificación.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = anonymize, onCheckedChange = null)
            }
            progress?.let { LinearProgressIndicator({ it }, Modifier.fillMaxWidth()) }
            val ok = !kind.needsTwo || count >= 2
            Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                OutlinedButton(onClick = { run(toGallery = false) }, enabled = ok && progress == null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null); Text("  Compartir")
                }
                Button(onClick = { run(toGallery = true) }, enabled = ok && progress == null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, null); Text("  Guardar")
                }
            }
        }
    }
}

/**
 * Vista previa del GIF: dibuja en bucle los mismos cuadros que el archivo (mismo dibujo y mismos
 * tiempos, ver [CompareExport.gifStateAt]), sin codificarlo.
 */
@Composable
fun GifPreview(load: suspend () -> List<Pair<android.graphics.Bitmap, String>>, mode: CompareExport.GifMode,
               timing: CompareExport.GifTiming, modifier: Modifier = Modifier) {
    val images by androidx.compose.runtime.produceState<List<Pair<android.graphics.Bitmap, String>>?>(null) {
        value = runCatching { load() }.getOrDefault(emptyList())
    }
    var timeMs by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(mode, timing) {
        timeMs = 0L
        val start = androidx.compose.runtime.withFrameMillis { it }
        while (true) androidx.compose.runtime.withFrameMillis { timeMs = it - start }
    }
    Box(modifier.width(200.dp).aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small)
        .background(androidx.compose.ui.graphics.Color.Black)
        .semantics { contentDescription = "Vista previa del GIF" }, contentAlignment = Alignment.Center) {
        val imgs = images
        when {
            imgs == null -> CircularProgressIndicator(Modifier.size(28.dp))
            imgs.size < 2 -> Text("Sin vista previa", color = androidx.compose.ui.graphics.Color.White)
            else -> androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val (i, t) = CompareExport.gifStateAt(timeMs, imgs.size, timing)
                val (a, la) = imgs[i]; val (b, lb) = imgs[(i + 1) % imgs.size]
                drawIntoCanvas { c ->
                    CompareExport.drawGifFrame(c.nativeCanvas, size.width.toInt(), size.height.toInt(), a, b, la, lb, t, mode)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked && enabled, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked && enabled, onCheckedChange = null, enabled = enabled)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}
