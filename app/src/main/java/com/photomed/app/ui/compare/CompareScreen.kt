package com.photomed.app.ui.compare

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ModalBottomSheet
import com.photomed.app.align.AlignInput
import com.photomed.app.align.ImageOps
import com.photomed.app.align.Perspective
import com.photomed.app.data.PhotoInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photomed.app.align.Aligner
import com.photomed.app.align.AlignmentResult
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.data.Expediente
import com.photomed.app.data.Photo
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.components.EmptyState
import com.photomed.app.ui.formatDate
import com.photomed.app.ui.theme.Md3Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private enum class Mode(val label: String) { SLIDER("Deslizador"), SIDE("Lado a lado"), FADE("Fundido") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(expId: String, initialZone: Zone?, onBack: () -> Unit) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val aligner = remember { Aligner(context.applicationContext) }
    var exp by remember { mutableStateOf<Expediente?>(null) }
    var all by remember { mutableStateOf<List<Photo>?>(null) }
    var zoneFilter by remember { mutableStateOf(initialZone) }
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    var swapped by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.SLIDER) }
    var position by remember { mutableFloatStateOf(0.5f) }
    var fade by remember { mutableFloatStateOf(0.5f) }
    var zoom by remember { mutableStateOf(ZoomState()) }
    var alignment by remember { mutableStateOf<AlignmentResult?>(null) }
    var useAligned by remember { mutableStateOf(true) }
    var aligning by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var perspectiveOn by remember { mutableStateOf(true) }
    var pitchBefore by remember { mutableFloatStateOf(0f) }
    var pitchAfter by remember { mutableFloatStateOf(0f) }
    var infoBefore by remember { mutableStateOf<PhotoInfo?>(null) }
    var infoAfter by remember { mutableStateOf<PhotoInfo?>(null) }

    LaunchedEffect(expId) {
        withContext(Dispatchers.IO) {
            exp = repo.getExpediente(expId)
            all = exp?.let { repo.photos(it) }.orEmpty()
        }
        // Selección inicial: primera y última foto de la zona (o de la zona más reciente).
        val photos = all.orEmpty()
        val z = zoneFilter ?: photos.lastOrNull()?.zone
        val inZone = photos.filter { it.zone == z }
        if (inZone.size >= 2) selected = listOf(inZone.first().key, inZone.last().key)
    }

    val photos = all.orEmpty()
    val zonesWithPhotos = Zone.entries.filter { z -> photos.any { it.zone == z } }
    val visible = photos.filter { zoneFilter == null || it.zone == zoneFilter }.reversed()
    val chosen = selected.mapNotNull { k -> photos.firstOrNull { it.key == k } }.sortedBy { it.takenAt }
    val pair = if (chosen.size == 2) (if (swapped) chosen.reversed() else chosen) else null
    val transform = alignment?.transform?.takeIf { useAligned }

    // Metadatos de captura del par (inclinación de la cámara y focal) → perspectiva automática.
    LaunchedEffect(pair?.getOrNull(0)?.key, pair?.getOrNull(1)?.key) {
        val (b, a) = pair ?: return@LaunchedEffect
        infoBefore = withContext(Dispatchers.IO) { repo.readInfo(b) }
        infoAfter = withContext(Dispatchers.IO) { repo.readInfo(a) }
        pitchBefore = infoBefore?.cameraPitch ?: 0f
        pitchAfter = infoAfter?.cameraPitch ?: 0f
    }
    val corrB = if (perspectiveOn) pitchBefore else 0f
    val corrA = if (perspectiveOn) pitchAfter else 0f
    val focalB = infoBefore?.focalNorm ?: Perspective.DEFAULT_FOCAL
    val focalA = infoAfter?.focalNorm ?: Perspective.DEFAULT_FOCAL
    val beforeModel = pair?.let { rememberCorrected(it[0].uri, corrB, focalB) }
    val afterModel = pair?.let { rememberCorrected(it[1].uri, corrA, focalA) }

    fun resetAlignment() { alignment = null; zoom = ZoomState() }

    fun toggle(p: Photo) {
        selected = when {
            p.key in selected -> selected - p.key
            selected.size >= 2 -> selected.drop(1) + p.key
            else -> selected + p.key
        }
        resetAlignment()
    }

    fun runAlign(block: suspend (Photo, Photo) -> AlignmentResult) {
        val (b, a) = pair ?: return
        aligning = true
        scope.launch {
            val r = runCatching { block(b, a) }
            aligning = false
            r.onSuccess {
                alignment = it; useAligned = true
                val quality = if (it.score.isNaN()) "" else " · contornos ${(it.score.coerceIn(0f, 1f) * 100).roundToInt()}%"
                snackbar.showSnackbar("Alineado: ${it.method}$quality")
            }.onFailure { snackbar.showSnackbar("No se pudo alinear: ${it.message}") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Comparar")
                        Text(exp?.patientName.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = { swapped = !swapped; resetAlignment() }, enabled = pair != null) {
                        Icon(Icons.Default.SwapHoriz, "Intercambiar antes y después")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Alinear manualmente (2 puntos)") },
                                leadingIcon = { Icon(Icons.Default.TouchApp, null) },
                                enabled = pair != null,
                                onClick = { menu = false; manual = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Perspectiva y ajustes") },
                                leadingIcon = { Icon(Icons.Default.Tune, null) },
                                enabled = pair != null,
                                onClick = { menu = false; settings = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Restablecer zoom") },
                                leadingIcon = { Icon(Icons.Default.ZoomOutMap, null) },
                                onClick = { menu = false; zoom = ZoomState() },
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (all != null && photos.size < 2) {
            EmptyState(Icons.Default.Compare, "Se necesitan dos fotografías",
                "Tome al menos dos fotografías de una zona para compararlas.", Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (aligning) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact)) {
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
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact, vertical = Md3Spacing.s2),
                contentAlignment = Alignment.Center,
            ) {
                if (pair == null) {
                    Text("Seleccione dos fotografías abajo para compararlas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                } else {
                    val (before, after) = pair
                    val beforeLabel = "Antes · ${formatDate(before.takenAt)}"
                    val afterLabel = "Después · ${formatDate(after.takenAt)}"
                    when (mode) {
                        Mode.SLIDER -> JuxtaposeSlider(
                            before = beforeModel!!, after = afterModel!!, afterTransform = transform,
                            position = position, onPositionChange = { position = it },
                            zoom = zoom, onZoomChange = { zoom = it },
                            beforeLabel = beforeLabel, afterLabel = afterLabel,
                            modifier = Modifier.aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium),
                        )

                        Mode.SIDE -> Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s1)) {
                            listOf(Triple(beforeModel, beforeLabel, null), Triple(afterModel, afterLabel, transform)).forEach { (m, label, t) ->
                                Box(Modifier.weight(1f).aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small).background(Color.Black)) {
                                    AsyncImage(m, label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alignedBy(t))
                                    Label(label, Modifier.align(Alignment.TopStart))
                                }
                            }
                        }

                        Mode.FADE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.weight(1f, fill = false).aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium)
                                    .background(Color.Black).clipToBounds()
                            ) {
                                AsyncImage(beforeModel, beforeLabel, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                AsyncImage(afterModel, afterLabel, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().alignedBy(transform).graphicsLayer { alpha = fade })
                                Label(if (fade < 0.5f) beforeLabel else afterLabel, Modifier.align(Alignment.TopStart))
                            }
                            Slider(fade, { fade = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Fundido antes/después" })
                        }
                    }
                }
            }

            // Alineación y datos del par
            pair?.let { (b, a) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
                ) {
                    val res = alignment
                    if (res == null) {
                        InputChip(
                            selected = false,
                            onClick = { runAlign { bp, ap -> aligner.auto(bp.zone, AlignInput(bp.uri, corrB, focalB), AlignInput(ap.uri, corrA, focalA)) } },
                            enabled = !aligning,
                            label = { Text("Alinear automáticamente") },
                            leadingIcon = { Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp)) },
                        )
                    } else {
                        Row(
                            Modifier.weight(1f).toggleable(useAligned, role = Role.Switch) { useAligned = it },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Alineado", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "%s · %.0f%% escala · %.1f°".format(
                                        res.landmarks.takeIf { it.isNotEmpty() }?.let { "${it.size} puntos (${res.landmarkRegions.take(4).joinToString(", ")})" }
                                            ?: "solo contornos",
                                        res.transform.scale * 100, res.transform.rotationDegrees,
                                    ),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (res.regionScores.isNotEmpty()) {
                                    Text(
                                        "Contornos: " + res.regionScores.sortedByDescending { it.second }.take(4)
                                            .joinToString(" · ") { (n, v) -> "$n ${(v.coerceIn(0f, 1f) * 100).roundToInt()}%" },
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(checked = useAligned, onCheckedChange = null)
                        }
                    }
                    if (res == null) {
                        val days = TimeUnit.MILLISECONDS.toDays(kotlin.math.abs(a.takenAt - b.takenAt))
                        Text("$days días", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (b.zone != a.zone) {
                    Text(
                        "Atención: zonas distintas (${b.zone.label} / ${a.zone.label})",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = Md3Spacing.marginCompact),
                    )
                }
            }

            // Filtro por zona
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Md3Spacing.marginCompact),
                horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
            ) {
                FilterChip(selected = zoneFilter == null, onClick = { zoneFilter = null }, label = { Text("Todas") },
                    leadingIcon = if (zoneFilter == null) { { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null)
                zonesWithPhotos.forEach { z ->
                    FilterChip(selected = zoneFilter == z, onClick = { zoneFilter = z }, label = { Text(z.label) },
                        leadingIcon = if (zoneFilter == z) { { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null)
                }
            }

            // Selección (toggle) de imágenes
            LazyRow(
                Modifier.fillMaxWidth().height(124.dp),
                contentPadding = PaddingValues(horizontal = Md3Spacing.marginCompact, vertical = Md3Spacing.s2),
                horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
            ) {
                items(visible, key = { it.key }) { p ->
                    val isSel = p.key in selected
                    val badge = pair?.let { (b, a) -> if (p.key == b.key) "A" else if (p.key == a.key) "B" else null }
                        ?: if (isSel) "✓" else null
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(66.dp, 88.dp).clip(MaterialTheme.shapes.small)
                                .border(
                                    if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    MaterialTheme.shapes.small,
                                )
                                .clickable(onClickLabel = if (isSel) "Quitar de la comparación" else "Añadir a la comparación") { toggle(p) }
                                .semantics {
                                    this.selected = isSel
                                    contentDescription = "${p.zone.label}, ${formatDate(p.takenAt)}"
                                }
                        ) {
                            AsyncImage(p.uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                .graphicsLayer { alpha = if (isSel || selected.size < 2) 1f else 0.6f })
                            if (badge != null) {
                                Text(
                                    badge,
                                    color = MaterialTheme.colorScheme.onPrimary,
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

    if (settings && pair != null) {
        ModalBottomSheet(onDismissRequest = { settings = false }) {
            Column(Modifier.padding(horizontal = Md3Spacing.s6).padding(bottom = Md3Spacing.s8),
                verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                Text("Perspectiva vertical", style = MaterialTheme.typography.titleLarge)
                Row(
                    Modifier.fillMaxWidth().toggleable(perspectiveOn, role = Role.Switch) { perspectiveOn = it; resetAlignment() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Corregir inclinación de la cámara", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Endereza las verticales convergentes con la inclinación registrada al tomar cada foto.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = perspectiveOn, onCheckedChange = null)
                }
                listOf(
                    Triple("Antes", pitchBefore, infoBefore?.cameraPitch),
                    Triple("Después", pitchAfter, infoAfter?.cameraPitch),
                ).forEachIndexed { i, (label, value, recorded) ->
                    Text(
                        "$label: %+.1f° ".format(value) + (recorded?.let { "(registrado %+.1f°)".format(it) } ?: "(sin datos de inclinación)"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = value, onValueChange = { if (i == 0) pitchBefore = it else pitchAfter = it; resetAlignment() },
                        valueRange = -20f..20f, enabled = perspectiveOn,
                        modifier = Modifier.semantics { contentDescription = "Perspectiva vertical $label" },
                    )
                }
            }
        }
    }

    if (manual && pair != null) {
        val (b, a) = pair
        ManualAlignDialog(
            before = b, after = a,
            onDismiss = { manual = false },
            onDone = { bp: List<Pt>, ap: List<Pt> ->
                manual = false
                runAlign { bb, aa -> aligner.manual(bb.zone, AlignInput(bb.uri, corrB, focalB), AlignInput(aa.uri, corrA, focalA), bp, ap) }
            },
        )
    }
}

/** Descripción de la estrategia de la zona (para ayudas contextuales). */
fun alignmentHint(zone: Zone) = ZoneAlignment.of(zone).description

/** Imagen con la perspectiva vertical corregida (o la URI tal cual si no hay corrección). */
@Composable
private fun rememberCorrected(uri: android.net.Uri, pitchDeg: Float, focalNorm: Float): Any {
    val context = LocalContext.current
    val key = (pitchDeg * 2).roundToInt() / 2f
    val state = androidx.compose.runtime.produceState<Any>(uri, uri, key, focalNorm) {
        if (key == 0f) { value = uri; return@produceState }
        value = withContext(Dispatchers.Default) {
            runCatching { ImageOps.correctPerspective(ImageOps.load34(context, uri, 1080), key, focalNorm) }.getOrNull()
        } ?: uri
    }
    return state.value
}
