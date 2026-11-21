package com.photomed.app.ui.protocol

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.photomed.app.data.Expediente
import com.photomed.app.data.Protocol
import com.photomed.app.data.ProtocolResults
import com.photomed.app.data.Protocols
import com.photomed.app.ui.LocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Protocolos en el expediente: crear, importar (un protocolo o un paquete de resultados), iniciar,
 * editar, compartir el protocolo, compartir los resultados (fotos tomadas con él) y eliminar.
 */
@Composable
fun ProtocolsSection(exp: Expediente?, snackbar: SnackbarHostState, photoVersion: Int,
                     onStart: (Protocol) -> Unit, onEdit: (String, List<String>) -> Unit,
                     /** Tras importar fotos de resultados (para recargar el expediente). */
                     onPhotosImported: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val list by produceState(emptyList<Protocol>(), version) { value = withContext(Dispatchers.IO) { Protocols.list(context) } }
    // fotos tomadas con cada protocolo en este expediente
    val taken by produceState(emptyMap<String, Int>(), list, exp?.id, photoVersion) {
        val e = exp ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val counts = HashMap<String, Int>()
            repo.photos(e).forEach { p -> repo.readInfo(p)?.conditions?.get(ProtocolResults.KEY_ID)?.let { counts[it] = (counts[it] ?: 0) + 1 } }
            counts
        }
    }
    var confirmDelete by remember { mutableStateOf<Protocol?>(null) }
    var busy by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val name = withContext(Dispatchers.IO) { displayName(context, uri) }.orEmpty()
            val type = context.contentResolver.getType(uri).orEmpty()
            val zip = name.lowercase().endsWith(".zip") || type.contains("zip")
            val msg = runCatching {
                withContext(Dispatchers.IO) {
                    if (zip) {
                        val e = exp ?: error("Abra un expediente para importar resultados")
                        val r = ProtocolResults.importZip(context, repo, e, uri)
                        "Resultados importados: ${r.photos} fotos" + (r.protocol?.let { " del protocolo «${it.name}»" } ?: "") +
                            if (r.duplicates > 0) " (${r.duplicates} ya estaban)" else ""
                    } else "Protocolo «${Protocols.import(context, uri).name}» importado"
                }
            }.getOrElse { it.message ?: "No se pudo importar el archivo" }
            busy = false
            version++
            if (zip) onPhotosImported()
            snackbar.showSnackbar(msg)
        }
    }

    fun shareResults(p: Protocol, anonymize: Boolean) {
        val e = exp ?: return
        scope.launch {
            busy = true
            val f = runCatching { withContext(Dispatchers.IO) { ProtocolResults.buildZip(context, repo, e, p, anonymize) } }.getOrNull()
            busy = false
            if (f == null) { snackbar.showSnackbar("No se pudo preparar el paquete de resultados"); return@launch }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Resultados del protocolo: ${p.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, if (anonymize) "Compartir resultados (sin datos del paciente)" else "Compartir resultados"))
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Protocolos de fotos", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { importer.launch(arrayOf("application/json", "application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) {
                Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(4.dp)); Text("Importar")
            }
            TextButton(onClick = { onEdit("new", emptyList()) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Nuevo") }
        }
        if (list.isEmpty()) Text("Cree una lista de tomas (zonas, gestos, cantidad, coincidencia, luz y referencia) o importe una que le compartieron.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            list.forEach { p ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            val n = taken[p.id] ?: 0
                            Text("${p.steps.size} tomas · ${p.totalShots} fotos" + (if (p.author.isNotBlank()) " · ${p.author}" else "") +
                                if (n > 0) " · $n tomadas aquí" else "", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onStart(p) }) { Icon(Icons.Default.PlayArrow, "Iniciar el protocolo ${p.name}", tint = MaterialTheme.colorScheme.primary) }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones del protocolo ${p.name}") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menu = false; onEdit(p.id, emptyList()) })
                                DropdownMenuItem(text = { Text("Compartir el protocolo") }, leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { menu = false; shareProtocol(context, p) })
                                if ((taken[p.id] ?: 0) > 0) {
                                    DropdownMenuItem(text = { Text("Compartir resultados sin datos del paciente") }, leadingIcon = { Icon(Icons.Outlined.PrivacyTip, null) },
                                        onClick = { menu = false; shareResults(p, anonymize = true) })
                                    DropdownMenuItem(text = { Text("Compartir resultados con datos del paciente") }, leadingIcon = { Icon(Icons.Default.Unarchive, null) },
                                        onClick = { menu = false; shareResults(p, anonymize = false) })
                                }
                                DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { menu = false; confirmDelete = p })
                            }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { p ->
        AlertDialog(onDismissRequest = { confirmDelete = null },
            title = { Text("¿Eliminar el protocolo «${p.name}»?") },
            text = { Text("Se quita de la app; las fotos tomadas con él no se borran.") },
            confirmButton = { TextButton(onClick = { Protocols.delete(context, p.id); confirmDelete = null; version++ }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } })
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment
