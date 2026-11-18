package com.photomed.app.ui.expedientes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photomed.app.data.Expediente
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpedientesScreen(onOpen: (String) -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var expedientes by remember { mutableStateOf<List<Expediente>?>(null) }
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        expedientes = withContext(Dispatchers.IO) { repo.listExpedientes() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Expedientes") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nuevo expediente") },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Buscar por nombre o número") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val list = expedientes
            val filtered = list.orEmpty().filter {
                query.isBlank() || it.patientName.contains(query, true) || it.recordNumber.contains(query, true)
            }
            when {
                list == null -> {}
                list.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { exp ->
                        ExpedienteRow(exp) { onOpen(exp.id) }
                    }
                }
            }
        }
    }

    if (showCreate) {
        ExpedienteDialog(
            title = "Nuevo expediente",
            onDismiss = { showCreate = false },
            onConfirm = { name, record, notes ->
                showCreate = false
                scope.launch {
                    val exp = withContext(Dispatchers.IO) { repo.createExpediente(name, record, notes) }
                    reload++
                    onOpen(exp.id)
                }
            }
        )
    }
}

@Composable
private fun ExpedienteRow(exp: Expediente, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        exp.patientName.split(" ").filter { it.isNotBlank() }.take(2)
                            .joinToString("") { it.first().uppercase() }.ifBlank { "#" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(exp.patientName.ifBlank { "(sin nombre)" }, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (exp.recordNumber.isNotBlank()) {
                    Text("Exp. ${exp.recordNumber}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${exp.photoCount} fotos" + (exp.lastPhotoAt?.let { " · última ${formatDate(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.FolderShared, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text("Aún no hay expedientes", style = MaterialTheme.typography.titleMedium)
        Text("Cree un expediente para comenzar a fotografiar por zonas.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ExpedienteDialog(
    title: String,
    initialName: String = "",
    initialRecord: String = "",
    initialNotes: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, record: String, notes: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var record by remember { mutableStateOf(initialRecord) }
    var notes by remember { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre del paciente") }, singleLine = true)
                OutlinedTextField(record, { record = it }, label = { Text("Número de expediente") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas / tratamiento") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, record, notes) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
