package com.photomed.app.ui.compare

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

/** Qué exportar. */
data class ExportChoice(
    val individual: Boolean,
    val grid: Boolean,
    val side: Boolean,
    val gif: CompareExport.GifMode?,
    val anonymize: Boolean,
)

/**
 * Opciones de exportación de la comparación: fotos ajustadas (una por foto) y/o composición en
 * cuadrícula; sin datos del paciente (por defecto); guardar en la galería o compartir.
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
    onExport: suspend (choice: ExportChoice, toGallery: Boolean, progress: (Int, Int) -> Unit) -> String?,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var individual by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(count >= 2) }
    var side by remember { mutableStateOf(false) }
    var gif by remember { mutableStateOf(false) }
    var gifMode by remember { mutableStateOf(CompareExport.GifMode.SLIDE) }
    var anonymize by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf<Float?>(null) }

    fun run(toGallery: Boolean) {
        if (progress != null) return
        progress = 0f
        scope.launch {
            val msg = runCatching {
                onExport(ExportChoice(individual, grid && count >= 2, side && count >= 2, gifMode.takeIf { gif && count >= 2 }, anonymize), toGallery) { i, n -> progress = if (n > 0) i / n.toFloat() else 1f }
            }.getOrElse { "No se pudo exportar: ${it.message ?: it.javaClass.simpleName}" }
            progress = null
            msg?.let(onMessage)
            onDismiss()
        }
    }

    SafeBottomSheet(onDismiss = { if (progress == null) onDismiss() }) {
        Column(Modifier.padding(horizontal = Md3Spacing.s6).padding(bottom = Md3Spacing.s6),
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
            CheckRow("Fotos ajustadas (una por foto)", individual) { individual = it }
            CheckRow("Composición en cuadrícula con fechas", grid, enabled = count >= 2) { grid = it }
            CheckRow("Lado a lado (una fila)", side, enabled = count >= 2) { side = it }
            CheckRow("Animación GIF", gif, enabled = count >= 2) { gif = it }
            if (gif && count >= 2) {
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 40.dp)) {
                    CompareExport.GifMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = gifMode == m, onClick = { gifMode = m },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, CompareExport.GifMode.entries.size),
                        ) { Text(m.label) }
                    }
                }
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
            val any = individual || ((grid || side || gif) && count >= 2)
            Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                OutlinedButton(onClick = { run(toGallery = false) }, enabled = any && progress == null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null); Text("  Compartir")
                }
                Button(onClick = { run(toGallery = true) }, enabled = any && progress == null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, null); Text("  Guardar")
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
