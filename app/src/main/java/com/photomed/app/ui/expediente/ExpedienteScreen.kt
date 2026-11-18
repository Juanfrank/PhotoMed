package com.photomed.app.ui.expediente

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photomed.app.data.Expediente
import com.photomed.app.data.Photo
import com.photomed.app.data.Zone
import com.photomed.app.data.ZoneCategory
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.camera.GuideOverlay
import com.photomed.app.ui.expedientes.ExpedienteDialog
import com.photomed.app.ui.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpedienteScreen(
    expId: String,
    onBack: () -> Unit,
    onZone: (Zone) -> Unit,
    onCompare: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var exp by remember { mutableStateOf<Expediente?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        withContext(Dispatchers.IO) {
            val e = repo.getExpediente(expId)
            exp = e
            photos = e?.let { repo.photos(it) }.orEmpty()
        }
    }
    val current = exp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(current?.patientName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!current?.recordNumber.isNullOrBlank()) {
                            Text("Exp. ${current?.recordNumber}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Editar") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar") }
                }
            )
        },
        floatingActionButton = {
            if (photos.size >= 2) {
                ExtendedFloatingActionButton(
                    onClick = onCompare,
                    icon = { Icon(Icons.Default.Compare, null) },
                    text = { Text("Comparar") },
                )
            }
        }
    ) { padding ->
        val byZone = photos.groupBy { it.zone }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!current?.notes.isNullOrBlank()) {
                item(span = { GridItemSpan(2) }) {
                    Text(current!!.notes, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                }
            }
            ZoneCategory.entries.forEach { cat ->
                item(span = { GridItemSpan(2) }) {
                    Text(cat.label, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
                items(Zone.entries.filter { it.category == cat }, key = { it.id }) { zone ->
                    ZoneCard(zone, byZone[zone].orEmpty()) { onZone(zone) }
                }
            }
        }
    }

    if (editing && current != null) {
        ExpedienteDialog(
            title = "Editar expediente",
            initialName = current.patientName,
            initialRecord = current.recordNumber,
            initialNotes = current.notes,
            onDismiss = { editing = false },
            onConfirm = { n, r, notes ->
                editing = false
                scope.launch {
                    withContext(Dispatchers.IO) { repo.updateExpediente(current, n, r, notes) }
                    reload++
                }
            }
        )
    }
    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar expediente") },
            text = { Text("Se eliminarán el expediente y sus ${current.photoCount} fotografías. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.deleteExpediente(current) }
                        onBack()
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ZoneCard(zone: Zone, photos: List<Photo>, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Color(0xFF263238))
        ) {
            val last = photos.lastOrNull()
            if (last != null) {
                AsyncImage(model = last.file, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                GuideOverlay(zone = zone, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize())
            }
            if (photos.isNotEmpty()) {
                Text(
                    "${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(zone.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                photos.lastOrNull()?.let { "Última: ${formatDate(it.takenAt)}" } ?: "Sin fotos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
