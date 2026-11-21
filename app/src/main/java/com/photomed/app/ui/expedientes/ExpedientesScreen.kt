package com.photomed.app.ui.expedientes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photomed.app.data.Expediente
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.components.Avatar
import com.photomed.app.ui.components.EmptyState
import com.photomed.app.ui.formatDate
import com.photomed.app.ui.theme.Md3Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExpedientesScreen(onOpen: (String) -> Unit, onAbout: () -> Unit, onSettings: () -> Unit = {}) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var expedientes by remember { mutableStateOf<List<Expediente>?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        expedientes = withContext(Dispatchers.IO) { repo.listExpedientes() }
    }
    // resúmenes (última foto de la cara, ángulos, pendientes): se completan en segundo plano
    val context = androidx.compose.ui.platform.LocalContext.current
    val summaries = remember { androidx.compose.runtime.mutableStateMapOf<String, com.photomed.app.data.ExpedienteSummary>() }
    LaunchedEffect(expedientes) {
        expedientes.orEmpty().forEach { e ->
            withContext(Dispatchers.IO) { runCatching { com.photomed.app.data.ExpedienteSummaries.of(context, repo, e) }.getOrNull() }
                ?.let { summaries[e.id] = it }
        }
    }

    fun recover() = scope.launch {
        val n = withContext(Dispatchers.IO) { runCatching { repo.recoverFromGallery() }.getOrDefault(0) }
        reload++
        snackbar.showSnackbar(if (n > 0) "Se recuperaron $n expedientes desde la galería" else "No se encontraron álbumes nuevos de PhotoMed")
    }
    val readPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recover() else scope.launch { snackbar.showSnackbar("Sin permiso no es posible leer fotos de otra instalación") }
    }

    ExpedientesContent(
        expedientes = expedientes,
        summaries = summaries,
        snackbar = snackbar,
        onOpen = onOpen,
        onCreate = { showCreate = true },
        onAbout = onAbout,
        onSettings = onSettings,
        onRecover = {
            readPermission.launch(
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
        },
    )

    if (showCreate) {
        ExpedienteForm(
            title = "Nuevo expediente",
            onDismiss = { showCreate = false },
            onConfirm = { name, record, notes, consent ->
                showCreate = false
                scope.launch {
                    val exp = withContext(Dispatchers.IO) { repo.createExpediente(name, record, notes, consent) }
                    reload++
                    onOpen(exp.id)
                }
            }
        )
    }
}

/** Contenido sin estado (también usado en las capturas de prueba). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpedientesContent(
    expedientes: List<Expediente>?,
    snackbar: SnackbarHostState,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onAbout: () -> Unit,
    onRecover: () -> Unit,
    initialQuery: String = "",
    onSettings: () -> Unit = {},
    summaries: Map<String, com.photomed.app.data.ExpedienteSummary> = emptyMap(),
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var menu by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // barra pequeña con el buscador dentro (el título grande ocupaba ~150 px; la pestaña ya dice dónde se está)
            androidx.compose.material3.TopAppBar(
                title = {
                    if (expedientes.isNullOrEmpty()) Text("Expedientes")
                    else TextField(
                        value = query, onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Borrar búsqueda") } },
                        placeholder = { Text("Buscar paciente o n.º", maxLines = 1) },
                        singleLine = true, shape = CircleShape,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(end = 4.dp).semantics { contentDescription = "Buscar paciente o número de expediente" },
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Recuperar desde la galería") },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                                onClick = { menu = false; onRecover() },
                            )
                            DropdownMenuItem(
                                text = { Text("Ajustes") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menu = false; onSettings() },
                            )
                            DropdownMenuItem(
                                text = { Text("Acerca de y legal") },
                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = { menu = false; onAbout() },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!expedientes.isNullOrEmpty()) ExtendedFloatingActionButton(
                onClick = onCreate,
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nuevo expediente") },
                modifier = Modifier.semantics { contentDescription = "Nuevo expediente" },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val list = expedientes
            if (list == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }
            if (list.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.PersonAdd,
                    title = "Aún no hay expedientes",
                    body = "Cree un expediente por paciente. Sus fotografías se organizarán por zona y " +
                        "aparecerán en la galería, en un álbum con su nombre.",
                    actionLabel = "Crear el primero",
                    actionIcon = Icons.Default.Add,
                    onAction = onCreate,
                )
                return@Column
            }
            ExpedientesTable(list, summaries, query, { query = it }, listState, onOpen, showSearch = false)
        }
    }
}

/** Orden de la tabla de expedientes. */
enum class ExpSort(val label: String) { NAME("Nombre"), PHOTOS("Fotos"), ANGLES("Ángulos"), LAST("Última toma"), PENDING("Pendientes") }

/** Filtros de la tabla de expedientes. */
enum class ExpFilter(val label: String) {
    WITH_PHOTOS("Con fotos"), NO_PHOTOS("Sin fotos"), PENDING("Con tomas pendientes"), RECENT("Últimos 30 días"),
    STALE("Sin fotos en 6 meses"), NO_CONSENT("Sin consentimiento")
}

/**
 * Tabla de expedientes: miniatura con la última foto de la cara (solo ángulos faciales), nombre y
 * número, fotos, ángulos, última toma y tomas pendientes de protocolos. Se busca por nombre o número,
 * se ordena tocando la cabecera de una columna (otra vez: orden inverso) y se filtra con los chips.
 */
@Composable
fun ExpedientesTable(list: List<Expediente>, summaries: Map<String, com.photomed.app.data.ExpedienteSummary>,
                     query: String, onQuery: (String) -> Unit, listState: androidx.compose.foundation.lazy.LazyListState,
                     onOpen: (String) -> Unit,
                     /** Buscador propio (si no, está en la barra superior). */
                     showSearch: Boolean = true) {
    var sort by rememberSaveable { mutableStateOf(ExpSort.LAST) }
    var asc by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(setOf<String>()) }
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    Column {
        // Campo de búsqueda con forma completa y contenedor surface-container-high (M3 search bar).
        if (showSearch) TextField(
            value = query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Borrar búsqueda") }
            },
            placeholder = { Text("Buscar nombre o n.º de expediente") },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact, vertical = Md3Spacing.s2),
        )
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Md3Spacing.marginCompact),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpFilter.entries.forEach { f ->
                androidx.compose.material3.FilterChip(selected = f.name in filters,
                    onClick = { filters = if (f.name in filters) filters - f.name else filters + f.name }, label = { Text(f.label) })
            }
        }
        fun sumOf(e: Expediente) = summaries[e.id]
        val shown = list.filter { e ->
            val s = sumOf(e); val n = s?.photos ?: e.photoCount; val last = s?.lastAt ?: e.lastPhotoAt
            (query.isBlank() || e.patientName.contains(query, true) || e.recordNumber.contains(query, true)) &&
                (ExpFilter.WITH_PHOTOS.name !in filters || n > 0) &&
                (ExpFilter.NO_PHOTOS.name !in filters || n == 0) &&
                (ExpFilter.PENDING.name !in filters || (s?.pendingShots ?: 0) > 0) &&
                (ExpFilter.RECENT.name !in filters || (last != null && now - last <= 30 * day)) &&
                (ExpFilter.STALE.name !in filters || (n > 0 && last != null && now - last > 182 * day)) &&
                (ExpFilter.NO_CONSENT.name !in filters || !e.consent)
        }.let { l ->
            val c: Comparator<Expediente> = when (sort) {
                ExpSort.NAME -> compareBy { it.patientName.lowercase() }
                ExpSort.PHOTOS -> compareBy { sumOf(it)?.photos ?: it.photoCount }
                ExpSort.ANGLES -> compareBy { sumOf(it)?.angles ?: 0 }
                ExpSort.LAST -> compareBy { sumOf(it)?.lastAt ?: it.lastPhotoAt ?: 0L }
                ExpSort.PENDING -> compareBy { sumOf(it)?.pendingShots ?: 0 }
            }
            l.sortedWith(if (asc) c else c.reversed())
        }
        // cabecera: tocar una columna ordena por ella (otra vez: orden inverso)
        @Composable fun head(s: ExpSort, mod: Modifier, align: TextAlign = TextAlign.End) {
            val on = sort == s
            Text(s.label + if (on) (if (asc) " ▲" else " ▼") else "", style = MaterialTheme.typography.labelMedium,
                fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else null, textAlign = align, maxLines = 2,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = mod.clickable(onClickLabel = "Ordenar por ${s.label}") { if (sort == s) asc = !asc else { sort = s; asc = s == ExpSort.NAME } }
                    .padding(vertical = 6.dp))
        }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = Md3Spacing.marginCompact, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(52.dp))
            head(ExpSort.NAME, Modifier.weight(1f), TextAlign.Start)
            head(ExpSort.PHOTOS, Modifier.width(40.dp))
            head(ExpSort.ANGLES, Modifier.width(52.dp))
            head(ExpSort.LAST, Modifier.width(62.dp))
            head(ExpSort.PENDING, Modifier.width(66.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (shown.isEmpty()) {
            EmptyState(Icons.Default.SearchOff, "Sin resultados",
                if (query.isNotBlank()) "Ningún expediente coincide con “$query”." else "Ningún expediente cumple los filtros elegidos.")
            return@Column
        }
        Text("${shown.size} de ${list.size} expedientes", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Md3Spacing.marginCompact, top = 2.dp))
        val context = androidx.compose.ui.platform.LocalContext.current
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
            items(shown, key = { it.id }) { exp ->
                val s = sumOf(exp)
                val n = s?.photos ?: exp.photoCount
                val last = s?.lastAt ?: exp.lastPhotoAt
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Abrir expediente") { onOpen(exp.id) }
                        .padding(start = Md3Spacing.marginCompact, end = 8.dp, top = 6.dp, bottom = 6.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically) {
                    // última foto de la cara (solo ángulos faciales); si no hay, las iniciales
                    Box(Modifier.width(44.dp).height(58.dp).clip(MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                        val face = s?.lastFace
                        if (face != null) coil.compose.AsyncImage(com.photomed.app.ui.photoModel(context, face.uri), "Última foto de la cara",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Avatar(exp.patientName)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exp.patientName.ifBlank { "(sin nombre)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(exp.recordNumber.takeIf { it.isNotBlank() }?.let { "Exp. $it" }, if (!exp.consent) "sin consentimiento" else null)
                            .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    Text("$n", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                    Text(s?.angles?.toString() ?: "…", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
                    Text(last?.let { formatDate(it) } ?: "—", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, maxLines = 2,
                        modifier = Modifier.width(62.dp))
                    val pend = s?.pendingShots ?: 0
                    Text(when { s == null -> "…"; pend > 0 -> "$pend fotos"; s.protocols.isNotEmpty() -> "completo"; else -> "—" },
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, maxLines = 2,
                        color = if (pend > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(66.dp))
                }
                HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
