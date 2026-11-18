package com.photomed.app.ui.expediente

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photomed.app.data.Expediente
import com.photomed.app.data.Photo
import com.photomed.app.data.Zone
import com.photomed.app.data.ZoneCategory
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.camera.GuideOverlay
import com.photomed.app.ui.components.ConfirmDialog
import com.photomed.app.ui.components.SectionHeader
import com.photomed.app.ui.components.rememberGalleryDelete
import com.photomed.app.ui.expedientes.ExpedienteForm
import com.photomed.app.ui.formatDate
import com.photomed.app.ui.theme.Md3Palette
import com.photomed.app.ui.theme.Md3Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExpedienteScreen(
    expId: String,
    onBack: () -> Unit,
    onZone: (Zone) -> Unit,
    onCompare: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var exp by remember { mutableStateOf<Expediente?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        val (e, list) = withContext(Dispatchers.IO) {
            val e = repo.getExpediente(expId)
            e to e?.let { repo.photos(it) }.orEmpty()
        }
        exp = e; photos = list
    }
    val galleryDelete = rememberGalleryDelete { ok ->
        if (ok) scope.launch {
            withContext(Dispatchers.IO) { exp?.let { repo.deleteExpediente(it) } }
            onBack()
        }
    }

    ExpedienteContent(
        exp = exp, photos = photos, snackbar = snackbar,
        onBack = onBack, onZone = onZone, onCompare = onCompare,
        onEdit = { editing = true }, onDelete = { confirmDelete = true },
    )

    val current = exp
    if (editing && current != null) {
        ExpedienteForm(
            title = "Editar expediente",
            initialName = current.patientName,
            initialRecord = current.recordNumber,
            initialNotes = current.notes,
            initialConsent = current.consent,
            onDismiss = { editing = false },
            onConfirm = { n, r, notes, consent ->
                editing = false
                scope.launch {
                    withContext(Dispatchers.IO) { repo.updateExpediente(current, n, r, notes, consent) }
                    reload++
                    snackbar.showSnackbar("Expediente actualizado. Las fotos nuevas usarán estos datos.")
                }
            }
        )
    }
    if (confirmDelete && current != null) {
        ConfirmDialog(
            title = "¿Eliminar expediente?",
            text = "Se eliminarán el expediente de ${current.patientName} y sus ${current.photoCount} fotografías " +
                "de la galería. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            icon = Icons.Default.Delete,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    val result = withContext(Dispatchers.IO) { repo.deleteExpediente(current) }
                    galleryDelete(result)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpedienteContent(
    exp: Expediente?,
    photos: List<Photo>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onZone: (Zone) -> Unit,
    onCompare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(exp?.patientName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar expediente") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Eliminar expediente") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; onDelete() },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
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
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val byZone = photos.groupBy { it.zone }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Md3Spacing.marginCompact, Md3Spacing.s2, Md3Spacing.marginCompact, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s3),
        ) {
            if (exp != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { SummaryCard(exp, photos.size) }
            }
            ZoneCategory.entries.forEach { cat ->
                val zones = Zone.entries.filter { it.category == cat }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("${cat.label} · ${zones.count { byZone.containsKey(it) }}/${zones.size} zonas")
                }
                items(zones, key = { it.id }) { zone ->
                    ZoneCard(zone, byZone[zone].orEmpty()) { onZone(zone) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(exp: Expediente, count: Int) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Md3Spacing.s4), verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (exp.recordNumber.isNotBlank()) {
                        Text("Expediente ${exp.recordNumber}", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Creado el ${formatDate(exp.createdAt)} · $count fotografías",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AssistChip(
                onClick = {},
                label = { Text(if (exp.consent) "Consentimiento registrado" else "Consentimiento no registrado") },
                leadingIcon = {
                    Icon(
                        if (exp.consent) Icons.Default.Verified else Icons.Default.Warning, null,
                        Modifier.size(AssistChipDefaults.IconSize),
                        tint = if (exp.consent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                },
            )
            if (exp.notes.isNotBlank()) {
                Text(exp.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Álbum en la galería: Pictures/PhotoMed/${exp.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ZoneCard(zone: Zone, photos: List<Photo>, onClick: () -> Unit) {
    val last = photos.lastOrNull()
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "${zone.label}, ${photos.size} fotografías"
        },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium)
                .background(Md3Palette.neutral17) // fondo oscuro fijo: guía legible en claro y oscuro
        ) {
            if (last != null) {
                AsyncImage(model = last.uri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                GuideOverlay(zone = zone, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize().padding(8.dp))
            }
            if (photos.isNotEmpty()) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Md3Spacing.s2),
                ) { Text("${photos.size}", style = MaterialTheme.typography.labelMedium) }
            }
        }
        Column(Modifier.padding(horizontal = Md3Spacing.s3, vertical = Md3Spacing.s2)) {
            Text(zone.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    last?.let { "Última: ${formatDate(it.takenAt)}" } ?: "Sin fotos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}
