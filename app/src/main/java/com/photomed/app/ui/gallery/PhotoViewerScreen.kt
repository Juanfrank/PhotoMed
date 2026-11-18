package com.photomed.app.ui.gallery

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.photomed.app.data.Photo
import com.photomed.app.data.PhotoInfo
import com.photomed.app.data.PhotoMetadata
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.components.ConfirmDialog
import com.photomed.app.ui.components.rememberGalleryDelete
import com.photomed.app.ui.formatDateTime
import com.photomed.app.ui.theme.ImmersiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(expId: String, zone: Zone, initialName: String, onBack: () -> Unit) = ImmersiveTheme {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var photos by remember { mutableStateOf<List<Photo>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<Photo, PhotoInfo?>?>(null) }

    LaunchedEffect(reload) {
        photos = withContext(Dispatchers.IO) { repo.getExpediente(expId)?.let { repo.photos(it, zone) }.orEmpty() }
        if (photos.isNullOrEmpty()) onBack()
    }
    val list = photos ?: return@ImmersiveTheme
    if (list.isEmpty()) return@ImmersiveTheme
    val pager = rememberPagerState(initialPage = list.indexOfFirst { it.displayName == initialName }.coerceAtLeast(0)) { list.size }
    val current = list[pager.currentPage.coerceIn(0, list.lastIndex)]
    val galleryDelete = rememberGalleryDelete { ok -> if (ok) reload++ }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                title = {
                    Column {
                        Text(zone.label)
                        Text("${formatDateTime(current.takenAt)} · ${pager.currentPage + 1}/${list.size}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = {
                        scope.launch { info = current to withContext(Dispatchers.IO) { repo.readInfo(current) } }
                    }) { Icon(Icons.Default.Info, "Información") }
                    Box {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, "Compartir") }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Compartir sin datos del paciente") },
                                leadingIcon = { Icon(Icons.Outlined.PrivacyTip, null) },
                                onClick = { shareMenu = false; scope.launch { share(context, current, anonymize = true) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Compartir con datos del paciente") },
                                leadingIcon = { Icon(Icons.Outlined.Badge, null) },
                                onClick = { shareMenu = false; scope.launch { share(context, current, anonymize = false) } },
                            )
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().background(Color.Black), key = { list[it].key }) { page ->
            ZoomableImage(model = list[page].uri, modifier = Modifier.fillMaxSize().padding(padding))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar fotografía?",
            text = "Se eliminará de la galería del teléfono y del expediente.",
            confirmLabel = "Eliminar",
            icon = Icons.Default.Delete,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch { galleryDelete(withContext(Dispatchers.IO) { repo.deletePhotos(listOf(current)) }) }
            },
        )
    }

    info?.let { (photo, meta) ->
        ModalBottomSheet(onDismissRequest = { info = null }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = androidx.compose.ui.unit.Dp(16f))) {
                Text("Información de la fotografía", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = androidx.compose.ui.unit.Dp(24f)))
                InfoRow("Paciente", meta?.patientName ?: "—")
                InfoRow("Expediente", meta?.recordNumber?.ifBlank { null } ?: "—")
                InfoRow("Zona", meta?.zoneLabel ?: photo.zone.label)
                InfoRow("Fecha de captura", formatDateTime(photo.takenAt))
                InfoRow("Consentimiento", if (meta?.consent == true) "Registrado" else "No registrado")
                InfoRow("Archivo", "Pictures/PhotoMed/$expId/${photo.displayName}")
                Text(
                    "Estos datos están incrustados en la imagen (EXIF y XMP).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = androidx.compose.ui.unit.Dp(24f)),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(overlineContent = { Text(label) }, headlineContent = { Text(value) })
}

/** Copia la foto a caché (opcionalmente sin metadatos del paciente) y abre el selector para compartir. */
private suspend fun share(context: Context, photo: Photo, anonymize: Boolean) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val name = if (anonymize) {
            "PhotoMed_${photo.zone.id}_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(photo.takenAt))}.jpg"
        } else photo.displayName
        File(dir, name).also { f ->
            context.contentResolver.openInputStream(photo.uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
            if (anonymize) PhotoMetadata.strip(f)
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, if (anonymize) "Compartir (anonimizada)" else "Compartir"))
}
