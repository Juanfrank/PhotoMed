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
            importBusy = "Analizando ${uris.size} ${if (uris.size == 1) "foto" else "fotos"}…"
            importItems = runCatching { com.photomed.app.data.PhotoImport.prepare(context, uris, default = null) }.getOrNull()
                ?: uris.map { com.photomed.app.data.ImportItem(it, System.currentTimeMillis(), false, null) }
            importBusy = null
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
                importBusy = "Importando ${chosen.size} ${if (chosen.size == 1) "foto" else "fotos"}…"
                val saved = runCatching { com.photomed.app.data.PhotoImport.import(context, repo, e, chosen) }.getOrDefault(emptyList())
                importBusy = null
                reload++
                val failed = chosen.size - saved.size
                snackbar.showSnackbar("Importadas ${saved.size} de ${chosen.size}" + if (failed > 0) " ($failed no se pudieron leer)" else "")
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
    onSequence: (List<Zone>) -> Unit = {},
    /** Importar fotos de la galería u otra fuente, asignando el ángulo de cada una. */
    onImport: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("photomed", android.content.Context.MODE_PRIVATE) }
    /** Zonas elegidas para la secuencia, en el orden en que se eligieron (mantener presionado para empezar). */
    var selection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(listOf<String>()) }
    val selecting = selection.isNotEmpty()
    var templates by remember { mutableStateOf(com.photomed.app.data.SessionTemplates.load(prefs)) }
    /** Categorías desplegadas por el usuario (las zonas sin fotos van plegadas). */
    var openCats by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }
    var naming by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = selecting) { selection = emptyList() }
    fun toggleZone(z: Zone) { selection = if (z.id in selection) selection - z.id else selection + z.id }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selecting) androidx.compose.material3.TopAppBar(
                title = { Text("${selection.size} zona" + if (selection.size == 1) "" else "s") },
                navigationIcon = { IconButton(onClick = { selection = emptyList() }) { Icon(Icons.Default.Close, "Cancelar selección") } },
                actions = {
                    IconButton(onClick = { naming = true }) { Icon(Icons.Default.BookmarkAdd, "Guardar como plantilla") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) else MediumTopAppBar(
                title = { Text(exp?.patientName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar expediente") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Importar fotos") },
                                leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, null) },
                                onClick = { menu = false; onImport() },
                            )
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
            if (selecting) {
                ExtendedFloatingActionButton(
                    onClick = { onSequence(selection.mapNotNull { Zone.fromId(it) }); selection = emptyList() },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    text = { Text("Iniciar secuencia de fotos (${selection.size})") },
                )
            } else if (photos.size >= 2) {
                ExtendedFloatingActionButton(
                    onClick = onCompare,
                    icon = { Icon(Icons.Default.Compare, null) },
                    text = { Text("Comparar") },
                )
            }
        },
        snackbarHost = {
            androidx.compose.foundation.layout.Column {
                com.photomed.app.ui.components.CoachMark("expediente_seleccion", Icons.Default.TouchApp,
                    "Mantenga pulsada una zona o una plantilla para elegir varias e iniciar una secuencia de fotos.")
                SnackbarHost(snackbar)
            }
        },
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                TemplatesRow(templates, selection,
                    onPick = { t -> selection = t.zones.map { it.id } },
                    onDelete = { t ->
                        com.photomed.app.data.SessionTemplates.delete(prefs, t.name)
                        templates = com.photomed.app.data.SessionTemplates.load(prefs)
                    })
            }
            // Primero las zonas con fotos; el resto, por categoría en secciones plegables
            // (abiertas si el expediente aún no tiene fotos o si hay zonas suyas seleccionadas)
            val withPhotos = Zone.entries.filter { byZone.containsKey(it) }
            if (withPhotos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Con fotos · ${withPhotos.size} zonas") }
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
                    CategoryHeader("${cat.label} · ${zones.size} ${if (withPhotos.isEmpty()) "zonas" else "zonas más"}", open) {
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
        com.photomed.app.data.SessionTemplates.save(prefs, com.photomed.app.data.SessionTemplate(name, selection.mapNotNull { Zone.fromId(it) }))
        templates = com.photomed.app.data.SessionTemplates.load(prefs)
    }
}

/** Encabezado plegable de una categoría de zonas. */
@Composable
private fun CategoryHeader(text: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClickLabel = if (open) "Plegar" else "Desplegar", onClick = onToggle)
            .padding(vertical = Md3Spacing.s2)
            .semantics { stateDescription = if (open) "Desplegada" else "Plegada"; heading() },
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
        title = { Text("Guardar plantilla") },
        text = {
            androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Plantillas de sesión: tocar una elige sus zonas (se puede ajustar dejando presionado y tocando
 * zonas); mantener presionada una plantilla propia la elimina.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TemplatesRow(templates: List<com.photomed.app.data.SessionTemplate>, selection: List<String>,
                         onPick: (com.photomed.app.data.SessionTemplate) -> Unit, onDelete: (com.photomed.app.data.SessionTemplate) -> Unit) {
    Column {
        SectionHeader("Plantillas de sesión · mantenga presionada una zona para elegir varias")
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
            items(templates.size) { i ->
                val t = templates[i]
                val active = selection == t.zones.map { it.id }
                // ficha propia: toque elige la plantilla; mantener presionada una propia la elimina
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.height(32.dp).clip(MaterialTheme.shapes.small)
                        .combinedClickable(onClick = { onPick(t) }, onLongClick = if (t.builtIn) null else ({ onDelete(t) }),
                            onClickLabel = "Elegir plantilla", onLongClickLabel = if (t.builtIn) null else "Eliminar plantilla"),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (t.builtIn) Icons.Default.PhotoLibrary else Icons.Default.Bookmark, null, Modifier.size(18.dp))
                        Text("  ${t.name} · ${t.zones.size}", style = MaterialTheme.typography.labelLarge)
                    }
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
                onClickLabel = if (selecting) (if (order != null) "Quitar de la secuencia" else "Añadir a la secuencia") else "Abrir",
                onLongClickLabel = "Elegir varias zonas")
            .semantics {
                contentDescription = "${zone.label}, ${photos.size} fotografías" + (order?.let { ", número $it de la secuencia" } ?: "")
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
                    last?.let { "Última: ${formatDate(it.takenAt)}" } ?: "Sin fotos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}
