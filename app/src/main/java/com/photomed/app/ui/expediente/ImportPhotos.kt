package com.photomed.app.ui.expediente

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.photomed.app.data.ImportItem
import com.photomed.app.data.Zone
import com.photomed.app.data.ZoneCategory
import com.photomed.app.ui.formatDateTime

/**
 * Importar fotos: una fila por foto con su miniatura, fecha y el ángulo (zona) al que corresponde.
 * Las vistas del rostro llegan con una sugerencia (giro de la cabeza) que se puede cambiar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPhotosDialog(initial: List<ImportItem>, onDismiss: () -> Unit, onImport: (List<ImportItem>) -> Unit) {
    var items by remember(initial) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Importar ${items.size} ${if (items.size == 1) "foto" else "fotos"}") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cancelar") } },
                )
            },
            bottomBar = {
                val missing = items.count { it.zone == null }
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (missing > 0) Text("Elija el ángulo de ${if (missing == 1) "1 foto" else "$missing fotos"}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { onImport(items) }, enabled = missing == 0 && items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Importar al expediente")
                    }
                }
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mismo ángulo para todas", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        ZonePicker(null, "Elegir") { z -> items = items.map { it.copy(zone = z, suggested = false) } }
                    }
                    Text("Las fotos se guardan en el álbum del paciente con sus datos y sin los metadatos originales " +
                        "(ubicación, dispositivo). Se conserva la fecha de toma si la foto la tiene.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
                itemsIndexed(items, key = { _, it -> it.uri.toString() }) { i, it ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(it.uri, "Foto ${i + 1} a importar", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(formatDateTime(it.takenAt), style = MaterialTheme.typography.bodyMedium)
                            Text(if (it.dateFromPhoto) "Fecha de la foto" else "Sin fecha en la foto: fecha de importación",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ZonePicker(it.zone, "Elegir ángulo", Modifier.semantics { contentDescription = "Ángulo de la foto ${i + 1}" }) { z ->
                                    items = items.toMutableList().also { l -> l[i] = l[i].copy(zone = z, suggested = false) }
                                }
                                if (it.suggested) Icon(Icons.Default.AutoFixHigh, "Ángulo sugerido por el giro de la cabeza",
                                    Modifier.padding(start = 4.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Botón con el ángulo elegido y un menú de todas las zonas, agrupadas por categoría. */
@Composable
fun ZonePicker(zone: Zone?, placeholder: String, modifier: Modifier = Modifier, onPick: (Zone) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }) {
            Text(zone?.label ?: placeholder, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ZoneCategory.entries.forEach { cat ->
                val zs = Zone.entries.filter { it.category == cat }
                if (zs.isEmpty()) return@forEach
                Text(cat.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                zs.forEach { z -> DropdownMenuItem(text = { Text(z.label) }, onClick = { open = false; onPick(z) }) }
            }
        }
    }
}
