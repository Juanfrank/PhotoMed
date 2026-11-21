package com.photomed.app.ui.protocol

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.photomed.app.data.Expediente
import com.photomed.app.data.Protocol
import com.photomed.app.data.Protocols
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Biblioteca de protocolos (pestaña «Protocolos»): los mismos protocolos sirven para todos los
 * pacientes. Crear, importar, editar, duplicar, compartir, eliminar y **aplicar a un paciente** (se
 * elige el expediente y la cámara empieza la primera toma).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolLibraryScreen(onEdit: (String) -> Unit, onApply: (String, Protocol) -> Unit) {
    val context = LocalContext.current
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var version by remember { mutableIntStateOf(0) }
    // al volver del editor la lista se recarga
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) version++ }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val list by produceState<List<Protocol>?>(null, version) { value = withContext(Dispatchers.IO) { Protocols.list(context) } }
    var applying by remember { mutableStateOf<Protocol?>(null) }
    var deleting by remember { mutableStateOf<Protocol?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching { "Protocolo «${Protocols.import(context, uri).name}» importado" }.getOrElse { it.message ?: "No se pudo importar el archivo" }
            }
            version++
            snackbar.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { com.photomed.app.ui.components.AppSectionTitle("Protocolos") }, actions = {
                IconButton(onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) {
                    Icon(Icons.Default.FileOpen, "Importar un protocolo")
                }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onEdit("new") }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nuevo protocolo") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val l = list
        if (l != null && l.isEmpty()) {
            com.photomed.app.ui.components.EmptyState(Icons.Default.PlaylistAddCheck, "Aún no hay protocolos",
                "Un protocolo es una lista de tomas (zonas, gestos, cantidad, coincidencia, luz y referencia) que se reutiliza con " +
                    "cualquier paciente y se comparte con otros profesionales.", Modifier.padding(padding),
                actionLabel = "Crear el primero", actionIcon = Icons.Default.Add, onAction = { onEdit("new") })
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(l.orEmpty(), key = { it.id }) { p ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text("${p.steps.size} tomas · ${p.totalShots} fotos" + (if (p.author.isNotBlank()) " · ${p.author}" else "") +
                                " · ${formatDate(p.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(p.steps.joinToString(" → ") { it.title }, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { applying = p }) { Text("Aplicar") }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones del protocolo ${p.name}") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit(p.id) })
                                DropdownMenuItem(text = { Text("Duplicar") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                                    menu = false
                                    Protocols.save(context, p.copy(id = UUID.randomUUID().toString(), name = p.name + " (copia)", createdAt = System.currentTimeMillis()))
                                    version++
                                })
                                DropdownMenuItem(text = { Text("Compartir") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menu = false; shareProtocol(context, p) })
                                DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; deleting = p })
                            }
                        }
                    }
                }
            }
        }
    }

    applying?.let { p ->
        ExpedientePicker("Aplicar «${p.name}» a…", onDismiss = { applying = null }) { e -> applying = null; onApply(e.id, p) }
    }
    deleting?.let { p ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("¿Eliminar el protocolo «${p.name}»?") },
            text = { Text("Se quita de la app; las fotos tomadas con él no se borran.") },
            confirmButton = { TextButton(onClick = { Protocols.delete(context, p.id); deleting = null; version++ }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

/** Elegir un expediente (buscando por nombre o número). */
@Composable
fun ExpedientePicker(title: String, onDismiss: () -> Unit, onPick: (Expediente) -> Unit) {
    val repo = LocalRepository.current
    var q by remember { mutableStateOf("") }
    val all by produceState<List<Expediente>>(emptyList()) { value = withContext(Dispatchers.IO) { repo.listExpedientes() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("Buscar por nombre o número") },
                    leadingIcon = { Icon(Icons.Default.PersonSearch, null) }, modifier = Modifier.fillMaxWidth())
                val shown = all.filter { q.isBlank() || it.patientName.contains(q, true) || it.recordNumber.contains(q, true) }
                LazyColumn(Modifier.height(320.dp)) {
                    items(shown, key = { it.id }) { e ->
                        ListItem(leadingContent = { PatientThumb(e) },
                            headlineContent = { Text(e.patientName.ifBlank { "(sin nombre)" }) },
                            supportingContent = { Text(listOfNotNull(e.recordNumber.takeIf { it.isNotBlank() }?.let { "Exp. $it" }, "${e.photoCount} fotos").joinToString(" · ")) },
                            modifier = Modifier.clickable { onPick(e) })
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

/**
 * Miniatura del paciente como en la pestaña Expedientes: la última foto de la cara (solo ángulos
 * faciales) o, si no hay, sus iniciales.
 */
@Composable
fun PatientThumb(e: Expediente, width: androidx.compose.ui.unit.Dp = 40.dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = LocalRepository.current
    val face by produceState<com.photomed.app.data.Photo?>(null, e.id) {
        value = withContext(Dispatchers.IO) { runCatching { com.photomed.app.data.ExpedienteSummaries.of(context, repo, e).lastFace }.getOrNull() }
    }
    androidx.compose.foundation.layout.Box(Modifier.width(width).aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        val f = face
        if (f != null) coil.compose.AsyncImage(com.photomed.app.ui.photoModel(context, f.uri), "Foto del paciente",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else com.photomed.app.ui.components.Avatar(e.patientName, width)
    }
}
