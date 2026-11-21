package com.photomed.app.ui.compare

import com.photomed.app.i18n.trn
import com.photomed.app.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.asImageBitmap
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
enum class ExportKind(private val labelEs: String, val needsTwo: Boolean) {
    INDIVIDUAL(trn("Fotos ajustadas (una por foto)"), false),
    GRID(trn("Cuadrícula con fechas"), true),
    SIDE(trn("Lado a lado (una fila)"), true),
    GIF(trn("Animación GIF"), true),
    MP4(trn("Vídeo MP4"), true);

    val label: String get() = tr(labelEs)

    /** Animación (GIF o MP4): mismo guion de ida y vuelta, en bucle sin salto. */
    val animated: Boolean get() = this == GIF || this == MP4
}

data class ExportChoice(
    val kind: ExportKind,
    val gif: CompareExport.GifMode = CompareExport.GifMode.SLIDE,
    val timing: CompareExport.GifTiming = CompareExport.GifTiming(),
    val anonymize: Boolean = true,
    /** Capas que lleva la imagen exportada. */
    val view: ViewLayers = ViewLayers(),
    /** Logo de los Ajustes y su posición (null: sin logo). */
    val watermark: com.photomed.app.export.Watermark.Mark? = null,
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
    /** Capas de la vista actual (las de la exportación empiezan igual). */
    initialView: ViewLayers = ViewLayers(),
    count: Int,
    aligned: Boolean,
    colorMatched: Boolean,
    onDismiss: () -> Unit,
    cropped: Boolean = false,
    /** Fotos ajustadas en tamaño reducido con su etiqueta, con las capas [ViewLayers] dadas (vista previa). */
    loadPreview: suspend (ViewLayers) -> List<Pair<android.graphics.Bitmap, String>> = { emptyList() },
    onExport: suspend (choice: ExportChoice, toGallery: Boolean, progress: (Int, Int) -> Unit) -> String?,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(if (count >= 2) ExportKind.GRID else ExportKind.INDIVIDUAL) }
    var gifMode by remember { mutableStateOf(CompareExport.GifMode.SLIDE) }
    var holdS by remember { mutableStateOf(1.2f) }
    var transS by remember { mutableStateOf(0.7f) }
    var anonymize by remember { mutableStateOf(true) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // logo adjunto en los Ajustes: la posición se elige aquí y se recuerda
    val logo = remember { com.photomed.app.export.Watermark.logo(ctx) }
    var logoPos by remember { mutableStateOf(com.photomed.app.export.Watermark.position(ctx)) }
    val mark = logo?.takeIf { logoPos != com.photomed.app.export.Watermark.Position.NONE }?.let { com.photomed.app.export.Watermark.Mark(it, logoPos) }
    var view by remember { mutableStateOf(initialView) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val timing = CompareExport.GifTiming((holdS * 1000).roundToInt(), (transS * 1000).roundToInt())

    fun run(toGallery: Boolean) {
        if (progress != null) return
        progress = 0f
        scope.launch {
            val msg = runCatching {
                onExport(ExportChoice(kind, gifMode, timing, anonymize, view, mark), toGallery) { i, n -> progress = if (n > 0) i / n.toFloat() else 1f }
            }.getOrElse { tr("No se pudo exportar: %1\$s", it.message ?: it.javaClass.simpleName) }
            progress = null
            msg?.let(onMessage)
            onDismiss()
        }
    }

    // el desplazamiento se conserva al cambiar de opción (hoja siempre expandida y vista previa de
    // altura fija: el contenido no cambia de tamaño bajo el dedo)
    val scroll = rememberScrollState()
    SafeBottomSheet(onDismiss = { if (progress == null) onDismiss() }, expanded = true) {
        Column(Modifier.padding(horizontal = Md3Spacing.s6).padding(bottom = Md3Spacing.s6)
            .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
            Text(tr("Exportar imágenes ajustadas"), style = MaterialTheme.typography.titleLarge)
            Text(
                buildList {
                    add(tr("%1\$d fotos en el encuadre de la referencia", count))
                    add(if (aligned) tr("alineación aplicada") else tr("sin alinear (pulse “Alinear automáticamente” antes para exportarlas alineadas)"))
                    if (colorMatched) add(tr("color igualado"))
                    if (cropped) add(tr("recortadas según la alineación"))
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
            if (kind.animated && count >= 2) {
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CompareExport.GifMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = gifMode == m, onClick = { gifMode = m },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, CompareExport.GifMode.entries.size),
                        ) { Text(m.label) }
                    }
                }
                Text(tr("Tiempo por imagen: %1\$.1f s", holdS), style = MaterialTheme.typography.bodyMedium)
                Slider(holdS, { holdS = (it * 10).roundToInt() / 10f }, valueRange = 0.3f..5f,
                    modifier = Modifier.semantics { contentDescription = tr("Tiempo por imagen") })
                Text(if (transS < 0.05f) tr("Transición: sin transición (corte)") else tr("Transición: %1\$.1f s", transS),
                    style = MaterialTheme.typography.bodyMedium)
                Slider(transS, { transS = (it * 10).roundToInt() / 10f }, valueRange = 0f..3f,
                    modifier = Modifier.semantics { contentDescription = tr("Tiempo de transición") })
            }
            // capas de la imagen exportada
            Text(tr("Capas en la imagen"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            listOf(
                Triple(tr("Imagen"), view.image) { b: Boolean -> view.copy(image = b) },
                Triple(tr("Diagrama facial (contornos y números)"), view.diagram) { b: Boolean -> view.copy(diagram = b) },
                Triple(tr("Anotaciones"), view.notes) { b: Boolean -> view.copy(notes = b) },
                Triple(tr("Overlay (fechas y rótulos)"), view.overlay) { b: Boolean -> view.copy(overlay = b) },
            ).forEach { (label, on, set) ->
                Row(Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox) { view = set(it) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = on, onCheckedChange = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
            // vista previa de la opción elegida, con las capas elegidas
            val previewImgs by androidx.compose.runtime.produceState<List<Pair<android.graphics.Bitmap, String>>?>(null, view) {
                value = null
                value = runCatching { loadPreview(view) }.getOrDefault(emptyList())
            }
            if (logo != null) {
                Text(tr("Logo"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.photomed.app.export.Watermark.Position.entries.forEach { p ->
                        androidx.compose.material3.FilterChip(selected = logoPos == p, onClick = {
                            logoPos = p; com.photomed.app.export.Watermark.setPosition(ctx, p)
                        }, label = { Text(p.label) })
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(272.dp), contentAlignment = Alignment.Center) {
                ExportPreview(kind, previewImgs, gifMode, timing, Modifier.padding(vertical = 4.dp), mark)
            }
            if (kind.animated) Text(tr("Se reproduce de ida y vuelta: al repetirse en bucle no se nota el salto.") +
                (if (kind == ExportKind.MP4) " " + tr("El vídeo dura un ciclo; la galería o el chat lo repiten.") else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().toggleable(anonymize, role = Role.Switch) { anonymize = it }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Sin datos del paciente"), style = MaterialTheme.typography.bodyLarge)
                    Text(tr("Nombres sin el nombre del paciente y sin metadatos de identificación."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = anonymize, onCheckedChange = null)
            }
            progress?.let { LinearProgressIndicator({ it }, Modifier.fillMaxWidth()) }
            val ok = !kind.needsTwo || count >= 2
            Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                OutlinedButton(onClick = { run(toGallery = false) }, enabled = ok && progress == null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null); Text("  " + tr("Compartir"))
                }
                Button(onClick = { run(toGallery = true) }, enabled = ok && progress == null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, null); Text("  " + tr("Guardar"))
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
fun ExportPreview(kind: ExportKind, images: List<Pair<android.graphics.Bitmap, String>>?, mode: CompareExport.GifMode,
                  timing: CompareExport.GifTiming, modifier: Modifier = Modifier,
                  mark: com.photomed.app.export.Watermark.Mark? = null) {
    if (kind.animated) { GifPreview(images, mode, timing, modifier, mark, smooth = kind == ExportKind.MP4); return }
    // fotos sueltas: la primera; cuadrícula y lado a lado: la composición tal como se exporta
    val bmp = androidx.compose.runtime.remember(images, kind, mark?.position) {
        val imgs = images ?: return@remember null
        if (imgs.isEmpty()) return@remember null
        com.photomed.app.export.Watermark.apply(when (kind) {
            ExportKind.INDIVIDUAL -> imgs.first().first
            ExportKind.GRID -> CompareExport.composite(imgs, cellW = 300)
            else -> CompareExport.composite(imgs, cellW = 300, cols0 = imgs.size)
        }, mark)
    }
    Box(modifier.heightIn(max = 260.dp).widthIn(max = 320.dp).clip(MaterialTheme.shapes.small)
        .background(androidx.compose.ui.graphics.Color.Black)
        .semantics { contentDescription = tr("Vista previa de la exportación") }, contentAlignment = Alignment.Center) {
        when {
            images == null -> CircularProgressIndicator(Modifier.padding(24.dp).size(28.dp))
            bmp == null -> Text(tr("Sin vista previa"), color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(24.dp))
            else -> androidx.compose.foundation.Image(bmp.asImageBitmap(), null,
                Modifier.aspectRatio(bmp.width / bmp.height.toFloat()), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        }
    }
}

@Composable
fun GifPreview(images: List<Pair<android.graphics.Bitmap, String>>?, mode: CompareExport.GifMode,
               timing: CompareExport.GifTiming, modifier: Modifier = Modifier,
               mark: com.photomed.app.export.Watermark.Mark? = null,
               /** Movimiento continuo como el MP4 (si no, los pasos del GIF). */
               smooth: Boolean = false) {
    var timeMs by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(mode, timing) {
        timeMs = 0L
        val start = androidx.compose.runtime.withFrameMillis { it }
        while (true) androidx.compose.runtime.withFrameMillis { timeMs = it - start }
    }
    Box(modifier.width(192.dp).aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small)
        .background(androidx.compose.ui.graphics.Color.Black)
        .semantics { contentDescription = tr("Vista previa del GIF") }, contentAlignment = Alignment.Center) {
        val imgs = images
        when {
            imgs == null -> CircularProgressIndicator(Modifier.size(28.dp))
            imgs.size < 2 -> Text(tr("Sin vista previa"), color = androidx.compose.ui.graphics.Color.White)
            else -> androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val (i, t) = if (smooth) CompareExport.smoothStateAt(timeMs.toDouble(), imgs.size, timing)
                    else CompareExport.gifStateAt(timeMs, imgs.size, timing)
                val (a, la) = imgs[i]; val (b, lb) = imgs[(i + 1) % imgs.size]
                drawIntoCanvas { c ->
                    CompareExport.drawGifFrame(c.nativeCanvas, size.width.toInt(), size.height.toInt(), a, b, la, lb, t, mode)
                    com.photomed.app.export.Watermark.draw(c.nativeCanvas, size.width.toInt(), size.height.toInt(), mark)
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
