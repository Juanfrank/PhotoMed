package com.photomed.app.ui.expedientes

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
fun ExpedientesScreen(onOpen: (String) -> Unit, onAbout: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var expedientes by remember { mutableStateOf<List<Expediente>?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        expedientes = withContext(Dispatchers.IO) { repo.listExpedientes() }
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
        snackbar = snackbar,
        onOpen = onOpen,
        onCreate = { showCreate = true },
        onAbout = onAbout,
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
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var menu by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Expedientes") },
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
            // Campo de búsqueda con forma completa y contenedor surface-container-high (M3 search bar).
            TextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Borrar búsqueda") }
                },
                placeholder = { Text("Buscar por nombre o número") },
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
            val filtered = list.filter {
                query.isBlank() || it.patientName.contains(query, true) || it.recordNumber.contains(query, true)
            }
            if (filtered.isEmpty()) {
                EmptyState(Icons.Default.SearchOff, "Sin resultados", "Ningún expediente coincide con “$query”.")
                return@Column
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                items(filtered, key = { it.id }) { exp ->
                    ListItem(
                        headlineContent = {
                            Text(exp.patientName.ifBlank { "(sin nombre)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        overlineContent = if (exp.recordNumber.isNotBlank()) {
                            { Text("Exp. ${exp.recordNumber}") }
                        } else null,
                        supportingContent = {
                            Text(
                                when (exp.photoCount) {
                                    0 -> "Sin fotografías"
                                    1 -> "1 fotografía"
                                    else -> "${exp.photoCount} fotografías"
                                } + (exp.lastPhotoAt?.let { " · última ${formatDate(it)}" } ?: ""),
                            )
                        },
                        leadingContent = { Avatar(exp.patientName) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        modifier = Modifier.clickable { onOpen(exp.id) },
                    )
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
