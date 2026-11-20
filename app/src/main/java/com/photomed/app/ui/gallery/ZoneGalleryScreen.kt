package com.photomed.app.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.photomed.app.ui.compare.Timeline
import com.photomed.app.ui.components.ConfirmDialog
import com.photomed.app.ui.components.rememberGalleryDelete
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photomed.app.data.Expediente
import com.photomed.app.data.Photo
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.components.EmptyState
import com.photomed.app.ui.components.SectionHeader
import com.photomed.app.ui.formatDateTime
import com.photomed.app.ui.formatDayHeader
import com.photomed.app.ui.theme.Md3Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ZoneGalleryScreen(
    expId: String,
    zone: Zone,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    /** Comparar: con selección múltiple recibe los nombres elegidos (vacío = selección automática). */
    onCompare: (List<String>) -> Unit,
    onPhoto: (Photo) -> Unit,
) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var exp by remember { mutableStateOf<Expediente?>(null) }
    var photos by remember { mutableStateOf<List<Photo>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    /** Selección múltiple (mantener pulsado para empezar); vacía = modo normal. */
    var selection by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selecting = selection.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    val galleryDelete = rememberGalleryDelete { ok -> if (ok) { selection = emptyList(); reload++ } }
    BackHandler(enabled = selecting) { selection = emptyList() }

    LaunchedEffect(expId, zone, reload) {
        // Lectura en IO con variables locales; el estado de Compose se actualiza en el hilo principal.
        val (e, list) = withContext(Dispatchers.IO) {
            val e = repo.getExpediente(expId)
            e to e?.let { repo.photos(it, zone) }.orEmpty().reversed()
        }
        exp = e; photos = list
        // Quitar de la selección las fotos que ya no existen.
        val keys = photos.orEmpty().map { it.key }.toSet()
        selection = selection.filter { it in keys }
    }
    // Función (no valor capturado): siempre lee la selección vigente al pulsar.
    fun selectedPhotos() = photos.orEmpty().filter { it.key in selection }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selecting) TopAppBar(
                title = { Text("${selection.size} seleccionada" + if (selection.size == 1) "" else "s") },
                navigationIcon = { IconButton(onClick = { selection = emptyList() }) { Icon(Icons.Default.Close, "Cancelar selección") } },
                actions = {
                    IconButton(onClick = { selection = photos.orEmpty().map { it.key } }) { Icon(Icons.Default.SelectAll, "Seleccionar todas") }
                    IconButton(onClick = { onCompare(selectedPhotos().map { it.displayName }) }, enabled = selection.size >= 2) {
                        Icon(Icons.Default.Compare, "Comparar seleccionadas")
                    }
                    Box {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, "Compartir seleccionadas") }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            listOf(true to "Compartir sin datos del paciente", false to "Compartir con datos del paciente").forEach { (anon, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    shareMenu = false
                                    val list = selectedPhotos()
                                    scope.launch { if (!sharePhotos(context, list, anon)) snackbar.showSnackbar("Algunas fotografías no se pudieron compartir") }
                                })
                            }
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar seleccionadas") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) else TopAppBar(
                title = {
                    Column {
                        Text(zone.label)
                        Text(exp?.patientName.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    if ((photos?.size ?: 0) >= 2) IconButton(onClick = { onCompare(emptyList()) }) { Icon(Icons.Default.Compare, "Comparar") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!selecting) ExtendedFloatingActionButton(
                onClick = onCamera,
                icon = { Icon(Icons.Default.CameraAlt, null) },
                text = { Text("Tomar foto") },
            )
        }
    ) { padding ->
        val list = photos ?: return@Scaffold
        if (list.isEmpty()) {
            Column(Modifier.padding(padding)) {
                HintCard(zone, Modifier.padding(Md3Spacing.marginCompact))
                EmptyState(
                    icon = Icons.Default.AddAPhoto,
                    title = "Sin fotografías de esta zona",
                    body = "La cámara mostrará el contorno y las líneas de referencia de ${zone.label.lowercase()}.",
                    actionLabel = "Tomar la primera",
                    actionIcon = Icons.Default.CameraAlt,
                    onAction = onCamera,
                )
            }
            return@Scaffold
        }
        val first = list.last().takenAt
        val byDay = list.groupBy { TimeUnit.MILLISECONDS.toDays(it.takenAt + java.util.TimeZone.getDefault().getOffset(it.takenAt)) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Md3Spacing.marginCompact, Md3Spacing.s2, Md3Spacing.marginCompact, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s1),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s1),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (selecting) Text("Toque para añadir o quitar fotos. Compare 2 o más (máx. ${Timeline.MAX_PHOTOS}).",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else HintCard(zone)
            }
            byDay.forEach { (_, dayPhotos) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val days = TimeUnit.MILLISECONDS.toDays(dayPhotos.first().takenAt - first)
                    SectionHeader(formatDayHeader(dayPhotos.first().takenAt) + if (days > 0) " · día $days" else " · basal")
                }
                items(dayPhotos, key = { it.key }) { photo ->
                    val isSel = photo.key in selection
                    val desc = "Fotografía del ${formatDateTime(photo.takenAt)}"
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small)
                            .combinedClickable(
                                onClickLabel = if (selecting) (if (isSel) "Quitar de la selección" else "Añadir a la selección") else "Ver",
                                onLongClickLabel = "Seleccionar",
                                onLongClick = { if (!isSel) selection = selection + photo.key },
                                onClick = {
                                    if (selecting) selection = if (isSel) selection - photo.key else selection + photo.key
                                    else onPhoto(photo)
                                },
                            )
                            .semantics { if (selecting) selected = isSel },
                    ) {
                        AsyncImage(com.photomed.app.ui.photoModel(LocalContext.current, photo.uri), desc, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().then(if (isSel) Modifier.padding(10.dp).clip(MaterialTheme.shapes.small) else Modifier))
                        if (selecting) {
                            Icon(
                                if (isSel) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
                                tint = if (isSel) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                                    .background(if (isSel) MaterialTheme.colorScheme.surface else Color(0x55000000), CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) DeleteSelectionDialog(selection.size, onDismiss = { confirmDelete = false }) {
        confirmDelete = false
        val list = selectedPhotos()
        scope.launch { galleryDelete(withContext(Dispatchers.IO) { list.forEach { com.photomed.app.align.ReliefStore.delete(context, it.uri.toString()) }; repo.deletePhotos(list) }) }
    }
}

@Composable
private fun DeleteSelectionDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) = ConfirmDialog(
    title = "¿Eliminar $count fotografía" + (if (count == 1) "?" else "s?"),
    text = "Se eliminarán de la galería del teléfono y del expediente.",
    confirmLabel = "Eliminar",
    icon = Icons.Default.Delete,
    onDismiss = onDismiss,
    onConfirm = onConfirm,
)

@Composable
private fun HintCard(zone: Zone, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Md3Spacing.s4), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Lightbulb, null, Modifier.size(20.dp))
            Spacer(Modifier.size(Md3Spacing.s3))
            Text(zone.hint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
