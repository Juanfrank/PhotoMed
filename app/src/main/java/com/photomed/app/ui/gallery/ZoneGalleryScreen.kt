package com.photomed.app.ui.gallery

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
    var photos by remember { mutableStateOf<List<Photo>?>(null) }

    LaunchedEffect(expId, zone) {
        withContext(Dispatchers.IO) {
            exp = repo.getExpediente(expId)
            photos = exp?.let { repo.photos(it, zone) }.orEmpty().reversed()
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(zone.label)
                        Text(exp?.patientName.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    if ((photos?.size ?: 0) >= 2) IconButton(onClick = onCompare) { Icon(Icons.Default.Compare, "Comparar") }
                },
                scrollBehavior = scrollBehavior,
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
            item(span = { GridItemSpan(maxLineSpan) }) { HintCard(zone) }
            byDay.forEach { (_, dayPhotos) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val days = TimeUnit.MILLISECONDS.toDays(dayPhotos.first().takenAt - first)
                    SectionHeader(formatDayHeader(dayPhotos.first().takenAt) + if (days > 0) " · día $days" else " · basal")
                }
                items(dayPhotos, key = { it.key }) { photo ->
                    Card(
                        onClick = { onPhoto(photo) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    ) {
                        AsyncImage(photo.uri, "Fotografía del ${formatDateTime(photo.takenAt)}",
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small))
                    }
                }
            }
        }
    }
}

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
