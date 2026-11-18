package com.photomed.app.ui.compare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photomed.app.data.Expediente
import com.photomed.app.data.Photo
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private enum class Mode(val label: String) { SLIDER("Deslizador"), SIDE("Lado a lado"), FADE("Fundido") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(expId: String, initialZone: Zone?, onBack: () -> Unit) {
    val repo = LocalRepository.current
    var exp by remember { mutableStateOf<Expediente?>(null) }
    var all by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var zoneFilter by remember { mutableStateOf(initialZone) }
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    var swapped by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.SLIDER) }
    var position by remember { mutableFloatStateOf(0.5f) }
    var fade by remember { mutableFloatStateOf(0.5f) }
    var zoom by remember { mutableStateOf(ZoomState()) }

    LaunchedEffect(expId) {
        withContext(Dispatchers.IO) {
            exp = repo.getExpediente(expId)
            all = exp?.let { repo.photos(it) }.orEmpty()
        }
        // Selección inicial: primera y última foto de la zona (o de la zona más reciente).
        val z = zoneFilter ?: all.lastOrNull()?.zone
        val inZone = all.filter { it.zone == z }
        if (inZone.size >= 2) selected = listOf(inZone.first().key, inZone.last().key)
    }

    val zonesWithPhotos = Zone.entries.filter { z -> all.any { it.zone == z } }
    val visible = all.filter { zoneFilter == null || it.zone == zoneFilter }.reversed()
    val chosen = selected.mapNotNull { k -> all.firstOrNull { it.key == k } }.sortedBy { it.takenAt }
    val pair = if (chosen.size == 2) (if (swapped) chosen.reversed() else chosen) else null

    fun toggle(p: Photo) {
        selected = when {
            p.key in selected -> selected - p.key
            selected.size >= 2 -> selected.drop(1) + p.key
            else -> selected + p.key
        }
        zoom = ZoomState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Comparar")
                        Text(exp?.patientName.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = { zoom = ZoomState() }, enabled = zoom != ZoomState()) {
                        Icon(Icons.Default.ZoomOutMap, "Restablecer zoom")
                    }
                    IconButton(onClick = { swapped = !swapped }, enabled = pair != null) {
                        Icon(Icons.Default.SwapHoriz, "Intercambiar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) { Text(m.label) }
                }
            }

            // Área de comparación
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (pair == null) {
                    Text(
                        "Seleccione dos fotografías abajo para compararlas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val (before, after) = pair
                    val beforeLabel = "Antes · ${formatDate(before.takenAt)}"
                    val afterLabel = "Después · ${formatDate(after.takenAt)}"
                    when (mode) {
                        Mode.SLIDER -> JuxtaposeSlider(
                            before = before.file, after = after.file,
                            position = position, onPositionChange = { position = it },
                            zoom = zoom, onZoomChange = { zoom = it },
                            beforeLabel = beforeLabel, afterLabel = afterLabel,
                            modifier = Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)),
                        )

                        Mode.SIDE -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(before to beforeLabel, after to afterLabel).forEach { (p, label) ->
                                Box(Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                                    AsyncImage(p.file, label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    Label(label, Modifier.align(Alignment.TopStart))
                                }
                            }
                        }

                        Mode.FADE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.weight(1f, fill = false).aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black).clipToBounds()
                            ) {
                                AsyncImage(before.file, beforeLabel, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                AsyncImage(after.file, afterLabel, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade })
                                Label(if (fade < 0.5f) beforeLabel else afterLabel, Modifier.align(Alignment.TopStart))
                            }
                            Slider(fade, { fade = it }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            pair?.let { (b, a) ->
                val days = TimeUnit.MILLISECONDS.toDays(a.takenAt - b.takenAt)
                Text(
                    "Intervalo: ${if (days >= 0) days else -days} días" +
                        if (b.zone != a.zone) " · ⚠ zonas distintas (${b.zone.label} / ${a.zone.label})" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (b.zone != a.zone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Filtro por zona
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = zoneFilter == null, onClick = { zoneFilter = null }, label = { Text("Todas") })
                zonesWithPhotos.forEach { z ->
                    FilterChip(selected = zoneFilter == z, onClick = { zoneFilter = z }, label = { Text(z.label) })
                }
            }

            // Selección (toggle) de imágenes
            Text(
                "Toque para seleccionar/deseleccionar (máx. 2)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyRow(
                Modifier.fillMaxWidth().height(118.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.key }) { p ->
                    val isSel = p.key in selected
                    val badge = pair?.let { (b, a) -> if (p.key == b.key) "A" else if (p.key == a.key) "B" else null }
                        ?: if (isSel) "✓" else null
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(66.dp, 88.dp).clip(RoundedCornerShape(8.dp))
                                .border(
                                    if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                    else BorderStroke(1.dp, Color.LightGray),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { toggle(p) }
                        ) {
                            AsyncImage(p.file, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                .graphicsLayer { alpha = if (isSel || selected.size < 2) 1f else 0.6f })
                            if (badge != null) {
                                Text(
                                    badge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(formatDate(p.takenAt), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
