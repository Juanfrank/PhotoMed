package com.photomed.app.ui.gallery

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.ui.graphics.asImageBitmap
import com.photomed.app.ui.camera.drawAnnotations
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.photomed.app.ui.components.SafeBottomSheet
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
import com.photomed.app.data.Photo
import com.photomed.app.data.PhotoInfo
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.components.ConfirmDialog
import com.photomed.app.ui.components.rememberGalleryDelete
import com.photomed.app.ui.formatDateTime
import com.photomed.app.ui.theme.ImmersiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // capas de anotación y edición no destructiva (giro, espejo, recorte)
    val layersState = com.photomed.app.ui.compare.rememberAnnotLayers()
    var showLayers by remember { mutableStateOf(layersState.value.isNotEmpty()) }
    var editing by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf(false) }
    var editVersion by remember { mutableIntStateOf(0) }
    // línea tocada (ficha de medidas) y tabla de medidas
    var selected by remember { mutableStateOf<com.photomed.app.align.AnnotLine?>(null) }
    var metricsSheet by remember { mutableStateOf(false) }
    // correcciones del usuario: versión (vuelve a analizar) y modo «trazar surco»
    var userVersion by remember { mutableIntStateOf(0) }
    var tracing by remember { mutableStateOf(false) }
    // opacidad de los trazos y números (se recuerda; empieza en 50 %)
    val annotAlpha = com.photomed.app.ui.compare.rememberAnnotAlpha()
    // líneas analizadas de cada foto (para la tabla) y motivo si no hay medidas
    val annotsByUri = remember { androidx.compose.runtime.mutableStateMapOf<String, List<com.photomed.app.align.AnnotLine>>() }
    val notesByUri = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }

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
                    IconButton(onClick = { showLayers = !showLayers }) {
                        Icon(Icons.Default.Layers, if (showLayers) "Ocultar capas" else "Capas: contorno, línea del pelo, rasgos y surcos")
                    }
                    IconButton(onClick = { editing = !editing }) { Icon(Icons.Default.Crop, if (editing) "Terminar edición" else "Girar, espejo y recorte") }
                    IconButton(onClick = {
                        scope.launch { info = current to withContext(Dispatchers.IO) { repo.readInfo(current) } }
                    }) { Icon(Icons.Default.Info, "Información") }
                    Box {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, "Compartir") }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Compartir sin datos del paciente") },
                                leadingIcon = { Icon(Icons.Outlined.PrivacyTip, null) },
                                onClick = { shareMenu = false; scope.launch { if (!sharePhotos(context, listOf(current), anonymize = true)) snackbar.showSnackbar("No se pudo compartir la fotografía") } },
                            )
                            DropdownMenuItem(
                                text = { Text("Compartir con datos del paciente") },
                                leadingIcon = { Icon(Icons.Outlined.Badge, null) },
                                onClick = { shareMenu = false; scope.launch { if (!sharePhotos(context, listOf(current), anonymize = false)) snackbar.showSnackbar("No se pudo compartir la fotografía") } },
                            )
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            // selector de capas (arrugas, rasgos…) encima de la foto
            if (showLayers) com.photomed.app.ui.compare.LayerChips(layersState.value, { layersState.value = it },
                Modifier.fillMaxWidth().padding(vertical = 4.dp), onMetrics = { metricsSheet = !metricsSheet },
                onTrace = { tracing = !tracing; if (tracing) scope.launch { snackbar.showSnackbar("Arrastre un rectángulo sobre el surco que falta") } },
                tracing = tracing)
            if (showLayers) com.photomed.app.ui.compare.OpacitySlider(annotAlpha.value, { annotAlpha.value = it },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp), light = true)
            // análisis en curso: barra de proceso visible
            val analyzing = showLayers && annotsByUri[current.uri.toString()] == null
            if (analyzing) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.weight(1f).semantics { contentDescription = "Analizando arrugas y rasgos" })
                Text("Analizando arrugas y rasgos…", color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 10.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().background(Color.Black), key = { list[it].key },
                userScrollEnabled = !cropping && !tracing) { page ->
                val ph = list[page]
                PhotoPage(ph, zone, if (showLayers) layersState.value else emptySet(), editVersion, Modifier.fillMaxSize(),
                    selected = selected, analyze = showLayers, alpha = annotAlpha.value,
                    // referencia: fotos anteriores de la misma zona (la más reciente primero)
                    previous = list.filter { it.takenAt < ph.takenAt }.sortedByDescending { it.takenAt }.map { it.uri.toString() },
                    userVersion = userVersion, tracing = tracing && page == pager.currentPage,
                    onTraceRect = { rect, aspect ->
                        tracing = false
                        scope.launch {
                            val e = com.photomed.app.align.PhotoEdits.of(context, ph.uri.toString())
                            val pts = withContext(Dispatchers.Default) { runCatching {
                                com.photomed.app.align.RegionTrace.trace(com.photomed.app.align.ImageOps.loadFull(context, ph.uri, 1600, e), rect)
                            }.getOrNull() }
                            if (pts == null) snackbar.showSnackbar("No se encontró un surco en esa zona; pruebe con un rectángulo más ajustado a él")
                            else {
                                com.photomed.app.align.AnnotUserEdits.add(context, ph.uri.toString(), pts, e.hashCode(), aspect)
                                if (com.photomed.app.align.AnnotLayer.FOLDS !in layersState.value) layersState.value = layersState.value + com.photomed.app.align.AnnotLayer.FOLDS
                                userVersion++
                                snackbar.showSnackbar("Surco añadido")
                            }
                        }
                    },
                    onSelect = { hit ->
                        selected = hit
                        // toque sin línea medida y ninguna capa de arrugas visible: cómo verlas
                        val visibleMeasured = annotsByUri[list[page].uri.toString()].orEmpty().any { it.metrics != null && it.layer in layersState.value }
                        if (hit == null && !visibleMeasured) scope.launch {
                            snackbar.showSnackbar("Active Surcos, Frente, Ceño, Patas de gallo u Ojeras y toque una línea, o abra «Medidas»")
                        }
                    },
                    onAnalyzed = { lines, note ->
                        annotsByUri[list[page].uri.toString()] = lines
                        if (note != null) notesByUri[list[page].uri.toString()] = note else notesByUri.remove(list[page].uri.toString())
                    })
            }
            androidx.compose.foundation.layout.Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                selected?.takeIf { showLayers && it.layer in layersState.value }?.let { l ->
                    val nums = com.photomed.app.align.MeasureNumbering.of(listOf(annotsByUri[current.uri.toString()].orEmpty()))
                    com.photomed.app.ui.compare.MetricsCard(l, onTable = { metricsSheet = true }, onClose = { selected = null },
                        modifier = Modifier.fillMaxWidth().padding(8.dp), number = l.metrics?.name?.let { nums[it] })
                }
            }
            }
            // tabla de medidas: debajo de la foto, sin taparla
            if (metricsSheet && showLayers) {
                val u = current.uri.toString()
                val lines = annotsByUri[u]
                val dismissed = remember(u, userVersion) { com.photomed.app.align.AnnotUserEdits.of(context, u).dismissed.size }
                com.photomed.app.ui.compare.MetricsPanel(listOf(formatDateTime(current.takenAt) to lines.orEmpty()), layersState.value,
                    onClose = { metricsSheet = false }, loading = lines == null, note = notesByUri[u],
                    onShowLayers = { layersState.value = it },
                    onDelete = { name, _ ->
                        // trazo añadido a mano: se quita; detectado: se marca como falso positivo
                        val added = Regex("Surco añadido (\\d+)").matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
                        if (added != null) com.photomed.app.align.AnnotUserEdits.removeAdded(context, u, added - 1)
                        else com.photomed.app.align.AnnotUserEdits.dismiss(context, u, name)
                        selected = null; userVersion++
                    },
                    dismissedCount = dismissed,
                    onRestore = { com.photomed.app.align.AnnotUserEdits.restore(context, u); userVersion++ },
                    modifier = Modifier.fillMaxWidth().heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.45f).dp))
            }
            if (editing) androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))) {
                // la edición se lee al pulsar (no la de la última composición) para encadenar giro, espejo…
                fun now() = com.photomed.app.align.PhotoEdits.of(context, current.uri.toString())
                val e = remember(current.uri, editVersion) { now() }
                fun save(n: com.photomed.app.align.ImageEdit) { com.photomed.app.align.PhotoEdits.set(context, current.uri.toString(), n); editVersion++ }
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                    EditButton(Icons.Default.RotateRight, "Girar 90°") { save(now().rotated()) }
                    EditButton(Icons.Default.Flip, "Espejo") { save(now().mirrored()) }
                    EditButton(Icons.Default.Crop, "Recortar") { cropping = true }
                    EditButton(Icons.Default.Restore, "Original", enabled = !e.isIdentity) { save(com.photomed.app.align.ImageEdit.NONE) }
                }
                Text("La foto original no se modifica; la edición se aplica también en Comparar y al exportar.",
                    color = Color.LightGray, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
        }
    }

    if (cropping) {
        val photo = current
        val e = com.photomed.app.align.PhotoEdits.of(context, photo.uri.toString())
        val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo.uri, e) {
            value = runCatching { com.photomed.app.align.ImageOps.loadFull(context, photo.uri, 1600, e.copy(crop = null)).asImageBitmap() }.getOrNull()
        }
        img?.let { b ->
            com.photomed.app.ui.components.CropEditor(b, aspect = null, initial = e.crop, onCancel = { cropping = false }) { r ->
                com.photomed.app.align.PhotoEdits.set(context, photo.uri.toString(), e.copy(crop = r)); editVersion++; cropping = false
            }
        }
    }

    LaunchedEffect(pager.currentPage) { selected = null }
    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar fotografía?",
            text = "Se eliminará de la galería del teléfono y del expediente.",
            confirmLabel = "Eliminar",
            icon = Icons.Default.Delete,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch { galleryDelete(withContext(Dispatchers.IO) { current.uri.toString().let { com.photomed.app.align.ReliefStore.delete(context, it) }; repo.deletePhotos(listOf(current)) }) }
            },
        )
    }

    info?.let { (photo, meta) ->
        SafeBottomSheet(onDismiss = { info = null }) {
            Column(Modifier.padding(bottom = androidx.compose.ui.unit.Dp(16f))) {
                Text("Información de la fotografía", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = androidx.compose.ui.unit.Dp(24f)))
                InfoRow("Paciente", meta?.patientName ?: "—")
                InfoRow("Expediente", meta?.recordNumber?.ifBlank { null } ?: "—")
                InfoRow("Zona", meta?.zoneLabel ?: photo.zone.label)
                InfoRow("Fecha de captura", formatDateTime(photo.takenAt))
                InfoRow("Consentimiento", if (meta?.consent == true) "Registrado" else "No registrado")
                meta?.conditions?.takeIf { it.isNotEmpty() }?.let { c ->
                    val labels = mapOf("Engine" to "Motor de puntos", "Assist" to "Asistencia", "MatchPercent" to "Puntos coincidentes (%)",
                        "HeadYaw" to "Giro de la cabeza (°)", "HeadPitch" to "Cabeceo, barbilla arriba + (°)", "Side" to "Lado (extremidad)", "DistanceCm" to "Distancia estimada (cm)", "Zoom" to "Zoom", "Status" to "Estado")
                    c.forEach { (k, v) -> InfoRow(labels[k] ?: k, v) }
                }
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

@Composable
private fun EditButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (enabled) Color.White else Color.Gray) }
        Text(label, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Página del visor: la foto con su edición (giro, espejo, recorte) ajustada a la pantalla, con zoom
 * (pellizco, doble toque) y las capas de anotación visibles en el mismo sistema de coordenadas.
 */
@Composable
private fun PhotoPage(photo: Photo, zone: Zone, layers: Set<com.photomed.app.align.AnnotLayer>, version: Int, modifier: Modifier,
                      selected: com.photomed.app.align.AnnotLine? = null, onSelect: (com.photomed.app.align.AnnotLine?) -> Unit = {},
                      /** Analizar la foto (panel de capas abierto), aunque no haya capas encendidas: la tabla lo necesita. */
                      analyze: Boolean = layers.isNotEmpty(),
                      /** Opacidad de los trazos y números. */
                      alpha: Float = 0.5f,
                      /** Fotos anteriores del mismo paciente y zona (la más reciente primero): referencia de arrugas. */
                      previous: List<String> = emptyList(),
                      /** Cambia al eliminar o añadir líneas (vuelve a analizar). */
                      userVersion: Int = 0,
                      /** Modo «trazar surco»: arrastrar un rectángulo sobre la foto; devuelve la zona (unidades del ancho). */
                      tracing: Boolean = false,
                      onTraceRect: (FloatArray, Float) -> Unit = { _, _ -> },
                      onAnalyzed: (List<com.photomed.app.align.AnnotLine>, String?) -> Unit = { _, _ -> }) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val edit = remember(photo.uri, version) { com.photomed.app.align.PhotoEdits.of(context, photo.uri.toString()) }
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo.uri, edit) {
        value = runCatching { com.photomed.app.align.ImageOps.loadFull(context, photo.uri, 2048, edit).asImageBitmap() }.getOrNull()
    }
    val annots by androidx.compose.runtime.produceState<List<com.photomed.app.align.AnnotLine>?>(null, photo.uri, edit, analyze, userVersion) {
        if (!analyze) return@produceState
        val u = photo.uri.toString()
        val user = com.photomed.app.align.AnnotUserEdits.of(context, u).let { e -> if (e.editHash != edit.hashCode()) e.copy(added = emptyList()) else e }
        val key = "full|$u|${edit.hashCode()}|${com.photomed.app.align.ReliefStore.tag(context, u)}|" +
            "${com.photomed.app.align.AnnotUserEdits.tag(context, u)}|${com.photomed.app.align.LineMemory.tag(context, previous)}"
        val hit = com.photomed.app.align.PhotoAnnotator.cached(key)
        // sin análisis disponible (pruebas en la JVM): no se carga la imagen para nada
        if (hit == null && !com.photomed.app.align.PhotoAnnotator.enabled) {
            com.photomed.app.align.PhotoAnnotator.putNote(key, "Análisis no disponible"); value = emptyList()
            onAnalyzed(emptyList(), "Análisis no disponible"); return@produceState
        }
        value = hit ?: runCatching {
            com.photomed.app.align.PhotoAnnotator(context.applicationContext)
                .analyze(com.photomed.app.align.ImageOps.loadFull(context, photo.uri, 1600, edit), zone,
                    com.photomed.app.align.PhotoAnnotator.extrasFor(context, photo.uri, 1600, crop34 = false),
                    user = user, priors = com.photomed.app.align.LineMemory.priors(context, previous), memoryUri = u)
        }.getOrNull()?.let { (lines, note) ->
            com.photomed.app.align.PhotoAnnotator.put(key, lines); com.photomed.app.align.PhotoAnnotator.putNote(key, note); lines
        } ?: emptyList<com.photomed.app.align.AnnotLine>().also {
            com.photomed.app.align.PhotoAnnotator.putNote(key, "No se pudo analizar la foto.")
        }
        onAnalyzed(value.orEmpty(), com.photomed.app.align.PhotoAnnotator.note(key))
    }
    val imgState = androidx.compose.runtime.rememberUpdatedState(img)
    val annotsState = androidx.compose.runtime.rememberUpdatedState(annots)
    val layersState = androidx.compose.runtime.rememberUpdatedState(layers)
    val onSelectState = androidx.compose.runtime.rememberUpdatedState(onSelect)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val b = img
    Box(modifier
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero },
                onTap = { s ->
                    // toque → punto de la foto (deshace zoom y desplazamiento) → línea con medidas más cercana
                    val bb = imgState.value ?: return@detectTapGestures
                    val a = annotsState.value ?: return@detectTapGestures
                    val sw = size.width.toFloat(); val sh = size.height.toFloat()
                    val ia = bb.width / bb.height.toFloat()
                    val w = minOf(sw, sh * ia); val h = w / ia
                    val cx = sw / 2; val cy = sh / 2
                    val px = cx + (s.x - cx - offset.x) / scale; val py = cy + (s.y - cy - offset.y) / scale
                    val q = com.photomed.app.align.Pt((px - (sw - w) / 2) / w, (py - (sh - h) / 2) / w)
                    onSelectState.value(com.photomed.app.ui.compare.hitLine(a, layersState.value, q, 28.dp.toPx() / (w * scale)))
                },
            )
        }
        .semantics { contentDescription = "Fotografía ${photo.zone.label}, ${formatDateTime(photo.takenAt)}" },
        contentAlignment = Alignment.Center) {
        if (b == null) CircularProgressIndicator()
        else androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        }) {
            val ia = b.width / b.height.toFloat()
            val w = minOf(size.width, size.height * ia); val h = w / ia
            val left = (size.width - w) / 2; val top = (size.height - h) / 2
            drawImage(b, dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
            annots?.let { a ->
                val k = (w / 360.dp.toPx()).coerceIn(0.35f, 1.4f) / scale.coerceAtLeast(1f).let { kotlin.math.sqrt(it) }
                fun map(p: com.photomed.app.align.Pt) = androidx.compose.ui.geometry.Offset(left + p.x * w, top + p.y * w)
                // línea tocada: resalte blanco debajo
                selected?.takeIf { it.layer in layers }?.let { sl ->
                    val path = androidx.compose.ui.graphics.Path()
                    sl.points.forEachIndexed { i, q -> val o = map(q); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                    if (sl.closed) path.close()
                    drawPath(path, Color.White.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 7.dp.toPx() * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                drawAnnotations(a, layers, k, numbers = com.photomed.app.align.MeasureNumbering.of(listOf(a)), alpha = alpha) { p -> map(p) }
            }
        }
        // trazar un surco: rectángulo arrastrado sobre la foto (sin zoom)
        if (tracing && b != null) {
            var start by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
            var end by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()
                .semantics { contentDescription = "Zona donde buscar el surco" }
                .pointerInput(b) {
                    detectDragGestures(
                        onDragStart = { start = it; end = it },
                        onDrag = { ch, _ -> end = ch.position },
                        onDragEnd = {
                            val s0 = start; val e0 = end
                            if (s0 != null && e0 != null) {
                                val sw = size.width.toFloat(); val sh = size.height.toFloat()
                                val ia = b.width / b.height.toFloat()
                                val w = minOf(sw, sh * ia); val h = w / ia
                                fun n(o: androidx.compose.ui.geometry.Offset) = floatArrayOf((o.x - (sw - w) / 2) / w, (o.y - (sh - h) / 2) / w)
                                val a0 = n(s0); val a1 = n(e0)
                                onTraceRect(floatArrayOf(a0[0], a0[1], a1[0], a1[1]), b.height / b.width.toFloat())
                            }
                            start = null; end = null
                        },
                    )
                }) {
                val s0 = start; val e0 = end
                drawRect(Color.Black.copy(alpha = 0.25f))
                if (s0 != null && e0 != null) {
                    val tl = androidx.compose.ui.geometry.Offset(minOf(s0.x, e0.x), minOf(s0.y, e0.y))
                    val sz = androidx.compose.ui.geometry.Size(kotlin.math.abs(e0.x - s0.x), kotlin.math.abs(e0.y - s0.y))
                    drawRect(Color(0xFFFFAB40), tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                }
            }
        }
    }
}
