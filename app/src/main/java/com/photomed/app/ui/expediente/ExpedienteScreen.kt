package com.photomed.app.ui.expediente

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
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
import com.photomed.app.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExpedienteScreen(
    expId: String,
    onBack: () -> Unit,
    onZone: (Zone) -> Unit,
    onCompare: () -> Unit,
    /** Secuencia guiada por las zonas elegidas (plantilla), en orden. */
    onSequence: (List<Zone>) -> Unit = {},
    /** Iniciar un protocolo (la cámara recorre sus pasos). */
    onProtocol: (com.photomed.app.data.Protocol) -> Unit = {},
    /** Crear ("new") o editar un protocolo; con fotos de partida (nombres) para crearlo desde ellas. */
    onEditProtocol: (String, List<String>) -> Unit = { _, _ -> },
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var exp by remember { mutableStateOf<Expediente?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // importación de fotos: selector del sistema → ángulo de cada foto → guardado en el expediente
    val context = androidx.compose.ui.platform.LocalContext.current
    var importItems by remember { mutableStateOf<List<com.photomed.app.data.ImportItem>?>(null) }
    var importBusy by remember { mutableStateOf<String?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            importBusy = if (uris.size == 1) tr("Analizando %1\$d foto…", uris.size) else tr("Analizando %1\$d fotos…", uris.size)
            importItems = runCatching { com.photomed.app.data.PhotoImport.prepare(context, uris, default = null) }.getOrNull()
                ?: uris.map { com.photomed.app.data.ImportItem(it, System.currentTimeMillis(), false, null) }
            importBusy = null
        }
    }

    // protocolos: importar un protocolo (.json) o un paquete de resultados (.zip)
    var protocolsVersion by remember { mutableIntStateOf(0) }
    val protocolPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importBusy = tr("Importando…")
            val (msg, photosIn) = com.photomed.app.ui.protocol.importProtocolFile(context, repo, exp, uri)
            importBusy = null
            protocolsVersion++
            if (photosIn) reload++
            snackbar.showSnackbar(msg)
        }
    }
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

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    ExpedienteContent(
        exp = exp, photos = photos, snackbar = snackbar,
        onBack = onBack, onZone = onZone, onCompare = onCompare,
        onEdit = { editing = true }, onDelete = { confirmDelete = true },
        onSequence = onSequence,
        onProtocol = onProtocol, onEditProtocol = onEditProtocol, photoVersion = reload, onPhotosImported = { reload++ }, repo = repo,
        onImportProtocol = { protocolPicker.launch(arrayOf("application/json", "application/zip", "application/octet-stream", "*/*")) },
        protocolsVersion = protocolsVersion,
        onImport = {
            picker.launch(androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
    importItems?.let { items ->
        ImportPhotosDialog(items, onDismiss = { importItems = null }) { chosen ->
            importItems = null
            val e = exp ?: return@ImportPhotosDialog
            scope.launch {
                importBusy = if (chosen.size == 1) tr("Importando %1\$d foto…", chosen.size) else tr("Importando %1\$d fotos…", chosen.size)
                val rr = runCatching { com.photomed.app.data.PhotoImport.importAll(context, repo, e, chosen) }
                rr.exceptionOrNull()?.let { com.photomed.app.CrashLog.write("Importar fotos", it) }
                val r = rr.getOrNull()
                importBusy = null
                reload++
                val saved = r?.saved?.size ?: 0; val dup = r?.duplicates ?: 0
                val failed = chosen.size - saved - dup
                snackbar.showSnackbar(if (r == null) tr("La importación se interrumpió por un error (queda en el registro de errores)")
                    else tr("Importadas %1\$d de %2\$d", saved, chosen.size) + (if (dup > 0) tr(" (%1\$d ya estaban en el expediente)", dup) else "") +
                    if (failed > 0) tr(" (%1\$d no se pudieron leer)", failed) else "")
            }
        }
    }
    }
    importBusy?.let { msg ->
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            androidx.compose.material3.Card {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp))
                    Text(msg, Modifier.padding(start = 16.dp))
                }
            }
        }
    }

    val current = exp
    if (editing && current != null) {
        ExpedienteForm(
            title = tr("Editar expediente"),
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
                    snackbar.showSnackbar(tr("Expediente actualizado. Las fotos nuevas usarán estos datos."))
                }
            }
        )
    }
    if (confirmDelete && current != null) {
        ConfirmDialog(
            title = tr("¿Eliminar expediente?"),
            text = tr("Se eliminarán el expediente de %1\$s y sus %2\$d fotografías " +
                "de la galería. Esta acción no se puede deshacer.", current.patientName, current.photoCount),
            confirmLabel = tr("Eliminar"),
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
    onSequence: (List<Zone>) -> Unit = {},
    /** Importar fotos de la galería u otra fuente, asignando el ángulo de cada una. */
    onImport: () -> Unit = {},
    onProtocol: (com.photomed.app.data.Protocol) -> Unit = {},
    onEditProtocol: (String, List<String>) -> Unit = { _, _ -> },
    photoVersion: Int = 0,
    onPhotosImported: () -> Unit = {},
    repo: com.photomed.app.data.PhotoRepository? = null,
    onImportProtocol: (() -> Unit)? = null,
    protocolsVersion: Int = 0,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("photomed", android.content.Context.MODE_PRIVATE) }
    /** Zonas elegidas para la secuencia, en el orden en que se eligieron (mantener presionado para empezar). */
    var selection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(listOf<String>()) }
    val selecting = selection.isNotEmpty()
    val scope = rememberCoroutineScope()
    /** Protocolos guardados desde aquí (para refrescar la lista). */
    var savedProtocols by remember { mutableStateOf(0) }
    /** Categorías desplegadas por el usuario (las zonas sin fotos van plegadas). */
    var openCats by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }
    var naming by remember { mutableStateOf(false) }
    /** Bloque «Iniciar sesión de fotos»: abierto si aún no hay fotos (o lo abre el usuario). */
    var sessionOpenUser by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Boolean?>(null) }
    val sessionOpen = sessionOpenUser ?: photos.isEmpty()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    androidx.activity.compose.BackHandler(enabled = selecting) { selection = emptyList() }
    fun toggleZone(z: Zone) { selection = if (z.id in selection) selection - z.id else selection + z.id }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selecting) androidx.compose.material3.TopAppBar(
                title = { Text(if (selection.size == 1) tr("%1\$d zona", selection.size) else tr("%1\$d zonas", selection.size)) },
                navigationIcon = { IconButton(onClick = { selection = emptyList() }) { Icon(Icons.Default.Close, tr("Cancelar selección")) } },
                actions = {
                    IconButton(onClick = { naming = true }) { Icon(Icons.Default.BookmarkAdd, tr("Guardar como protocolo")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) else MediumTopAppBar(
                title = { Text(exp?.patientName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Atrás")) } },
                actions = {
                    // comparar: acción secundaria (con al menos dos fotos)
                    if (photos.size >= 2) IconButton(onClick = onCompare) { Icon(Icons.Default.Compare, tr("Comparar")) }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, tr("Editar expediente")) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("Más opciones")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Importar fotos")) },
                                leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, null) },
                                onClick = { menu = false; onImport() },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Eliminar expediente")) },
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
            if (selecting) {
                ExtendedFloatingActionButton(
                    onClick = { onSequence(selection.mapNotNull { Zone.fromId(it) }); selection = emptyList() },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    text = { Text(tr("Iniciar secuencia de fotos (%1\$d)", selection.size)) },
                )
            } else {
                // la acción principal del expediente es fotografiar: abre los protocolos y los muestra
                ExtendedFloatingActionButton(
                    onClick = { sessionOpenUser = true; scope.launch { gridState.animateScrollToItem(if (exp != null) 1 else 0) } },
                    icon = { Icon(Icons.Default.PhotoCamera, null) },
                    text = { Text(tr("Iniciar sesión")) },
                )
            }
        },
        snackbarHost = {
            androidx.compose.foundation.layout.Column {
                com.photomed.app.ui.components.CoachMark("expediente_seleccion", Icons.Default.TouchApp,
                    tr("Mantenga pulsada una zona para elegir varias: inicie una secuencia de fotos o guárdela como protocolo."))
                SnackbarHost(snackbar)
            }
        },
    ) { padding ->
        val byZone = photos.groupBy { it.zone }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            state = gridState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Md3Spacing.marginCompact, Md3Spacing.s2, Md3Spacing.marginCompact, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s3),
        ) {
            if (exp != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { SummaryCard(exp, photos.size, photos.minOfOrNull { it.takenAt }?.takeIf { it > 0L }) }
            }
            // iniciar una sesión: protocolos (los predefinidos incluidos), plegados si el expediente ya tiene fotos
            item(span = { GridItemSpan(maxLineSpan) }, key = "sesion") {
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (sessionOpen) tr("Plegar") else tr("Desplegar")) { sessionOpenUser = !sessionOpen }
                    .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                    Text(tr("Iniciar sesión de fotos (protocolos)"), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).padding(start = 6.dp))
                    Icon(if (sessionOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (sessionOpen) item(span = { GridItemSpan(maxLineSpan) }, key = "protocolos") {
                com.photomed.app.ui.protocol.ProtocolsSection(exp, snackbar, photoVersion, repo, onStart = onProtocol, onEdit = onEditProtocol,
                    onPhotosImported = onPhotosImported, onImportFile = onImportProtocol, listVersion = protocolsVersion + savedProtocols)
            }
            // Primero las zonas con fotos; el resto, por categoría en secciones plegables
            // (abiertas si el expediente aún no tiene fotos o si hay zonas suyas seleccionadas)
            val withPhotos = Zone.entries.filter { byZone.containsKey(it) }
            if (withPhotos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(if (withPhotos.size == 1) tr("Con fotos · 1 zona") else tr("Con fotos · %1\$d zonas", withPhotos.size)) }
                items(withPhotos, key = { "f_" + it.id }) { zone ->
                    val order = selection.indexOf(zone.id).takeIf { it >= 0 }?.plus(1)
                    ZoneCard(zone, byZone[zone].orEmpty(), order, selecting,
                        onLongClick = { toggleZone(zone) }) { if (selecting) toggleZone(zone) else onZone(zone) }
                }
            }
            ZoneCategory.entries.forEach { cat ->
                val zones = Zone.entries.filter { it.category == cat && !byZone.containsKey(it) }
                if (zones.isEmpty()) return@forEach
                val open = cat.name in openCats || withPhotos.isEmpty() || zones.any { it.id in selection }
                item(span = { GridItemSpan(maxLineSpan) }, key = "cat_" + cat.name) {
                    CategoryHeader("${cat.label} · " + when {
                        zones.size == 1 && withPhotos.isEmpty() -> tr("1 zona"); zones.size == 1 -> tr("1 zona más")
                        withPhotos.isEmpty() -> tr("%1\$d zonas", zones.size); else -> tr("%1\$d zonas más", zones.size) }, open) {
                        openCats = if (cat.name in openCats) openCats - cat.name else openCats + cat.name
                    }
                }
                if (open) items(zones, key = { it.id }) { zone ->
                    val order = selection.indexOf(zone.id).takeIf { it >= 0 }?.plus(1)
                    ZoneCard(zone, byZone[zone].orEmpty(), order, selecting,
                        onLongClick = { toggleZone(zone) }) { if (selecting) toggleZone(zone) else onZone(zone) }
                }
            }
        }
    }
    if (naming) TemplateNameDialog(onDismiss = { naming = false }) { name ->
        naming = false
        // la selección de zonas se guarda como protocolo (una toma por zona, en el orden elegido)
        com.photomed.app.data.Protocols.save(context, com.photomed.app.data.BuiltInProtocols.of(name, selection.mapNotNull { Zone.fromId(it) }))
        savedProtocols++
        selection = emptyList()
        sessionOpenUser = true
        scope.launch { snackbar.showSnackbar(tr("Protocolo «%1\$s» guardado", name)) }
    }
}

/** Encabezado plegable de una categoría de zonas. */
@Composable
private fun CategoryHeader(text: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClickLabel = if (open) tr("Plegar") else tr("Desplegar"), onClick = onToggle)
            .padding(vertical = Md3Spacing.s2)
            .semantics { stateDescription = if (open) tr("Desplegada") else tr("Plegada"); heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TemplateNameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Guardar como protocolo")) },
        text = {
            androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text(tr("Nombre")) }, singleLine = true)
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(tr("Guardar")) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun SummaryCard(exp: Expediente, count: Int, firstPhoto: Long?) {
    // una línea (número, fotos, consentimiento); al tocarla, las notas y el álbum
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth().clickable(onClickLabel = if (open) tr("Ocultar detalles") else tr("Ver detalles")) { open = !open }) {
        Column(Modifier.padding(horizontal = Md3Spacing.s3, vertical = Md3Spacing.s2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (exp.consent) Icons.Default.Verified else Icons.Default.Warning,
                    if (exp.consent) tr("Consentimiento registrado") else tr("Consentimiento no registrado"), Modifier.size(18.dp),
                    tint = if (exp.consent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                // fechas coherentes: el alta en la app y, si hay fotos anteriores (importadas), la primera; nunca una fecha cero
                val created = exp.createdAt.takeIf { it > 0L }
                val dates = when {
                    firstPhoto != null && (created == null || firstPhoto < created - 86_400_000L) ->
                        listOfNotNull(tr("primera foto: %1\$s", formatDate(firstPhoto)), created?.let { tr("alta: %1\$s", formatDate(it)) })
                    created != null -> listOf(tr("alta: %1\$s", formatDate(created)))
                    else -> emptyList()
                }
                Text((listOfNotNull(exp.recordNumber.takeIf { it.isNotBlank() }?.let { tr("Exp. %1\$s", it) },
                    if (count == 1) tr("1 foto") else tr("%1\$d fotos", count)) + dates)
                    .joinToString(" · "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 6.dp))
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (exp.notes.isNotBlank()) Text(exp.notes, style = MaterialTheme.typography.bodySmall, maxLines = if (open) 20 else 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (open) {
                Text(if (exp.consent) tr("Consentimiento registrado") else tr("Consentimiento no registrado"), style = MaterialTheme.typography.bodySmall,
                    color = if (exp.consent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text(tr("Álbum en la galería: %1\$s", "Pictures/PhotoMed/${exp.id}"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ZoneCard(zone: Zone, photos: List<Photo>, order: Int? = null, selecting: Boolean = false,
                     onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val last = photos.lastOrNull()
    Card(
        colors = CardDefaults.cardColors(containerColor = if (order != null) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest),
        border = if (order != null) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick,
                onClickLabel = if (selecting) (if (order != null) tr("Quitar de la secuencia") else tr("Añadir a la secuencia")) else tr("Abrir"),
                onLongClickLabel = tr("Elegir varias zonas"))
            .semantics {
                contentDescription = tr("%1\$s, %2\$d fotografías", zone.label, photos.size) + (order?.let { tr(", número %1\$d de la secuencia", it) } ?: "")
            },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium)
                .background(Md3Palette.neutral17) // fondo oscuro fijo: guía legible en claro y oscuro
        ) {
            if (last != null) {
                AsyncImage(model = com.photomed.app.ui.photoModel(LocalContext.current, last.uri), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                GuideOverlay(zone = zone, showContour = true, showGrid = false, modifier = Modifier.fillMaxSize().padding(8.dp))
            }
            if (order != null) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.align(Alignment.TopStart).padding(Md3Spacing.s2).size(24.dp),
                ) { Text("$order", style = MaterialTheme.typography.labelLarge) }
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
                    last?.let { tr("Última: %1\$s", formatDate(it.takenAt)) } ?: tr("Sin fotos"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}
