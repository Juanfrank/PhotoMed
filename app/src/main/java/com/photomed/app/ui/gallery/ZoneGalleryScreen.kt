package com.photomed.app.ui.gallery

import android.content.Intent
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.photomed.app.data.Expediente
import com.photomed.app.data.Photo
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneGalleryScreen(
    expId: String,
    zone: Zone,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onCompare: () -> Unit,
    onPhoto: (Photo) -> Unit,
) {
    val repo = LocalRepository.current
    var exp by remember { mutableStateOf<Expediente?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }

    LaunchedEffect(expId, zone) {
        withContext(Dispatchers.IO) {
            exp = repo.getExpediente(expId)
            photos = exp?.let { repo.photos(it, zone) }.orEmpty().reversed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(zone.label)
                        Text(exp?.patientName.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    if (photos.size >= 2) IconButton(onClick = onCompare) { Icon(Icons.Default.Compare, "Comparar") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCamera,
                icon = { Icon(Icons.Default.CameraAlt, null) },
                text = { Text("Tomar foto") },
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(3) }) {
                Text(zone.hint, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(6.dp))
            }
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Text("No hay fotografías de esta zona.", modifier = Modifier.padding(24.dp))
                }
            }
            items(photos, key = { it.key }) { photo ->
                Box(
                    Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp))
                        .clickable { onPhoto(photo) }
                ) {
                    AsyncImage(photo.file, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Text(
                        formatDateTime(photo.takenAt),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Color(0x88000000)).padding(4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(expId: String, zone: Zone, fileName: String, onBack: () -> Unit) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember { File(File(repo.root, expId), "${zone.id}/$fileName") }
    var photo by remember { mutableStateOf<Photo?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        photo = withContext(Dispatchers.IO) {
            repo.getExpediente(expId)?.let { repo.photos(it, zone) }?.firstOrNull { it.file == file }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                ),
                title = {
                    Column {
                        Text(zone.label)
                        photo?.let { Text(formatDateTime(it.takenAt), style = MaterialTheme.typography.bodySmall) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir fotografía"))
                    }) { Icon(Icons.Default.Share, "Compartir") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            ZoomableImage(model = file, modifier = Modifier.fillMaxSize())
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar fotografía") },
            text = { Text("¿Desea eliminar esta fotografía del expediente?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { file.delete() }
                        onBack()
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
