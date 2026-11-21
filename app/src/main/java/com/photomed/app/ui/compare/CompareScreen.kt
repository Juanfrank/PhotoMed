package com.photomed.app.ui.compare

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Palette
import com.photomed.app.align.ColorAdjust
import androidx.compose.material.icons.filled.Straighten
import com.photomed.app.ui.components.SafeBottomSheet
import com.photomed.app.align.ImageOps
import com.photomed.app.align.Perspective
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photomed.app.align.Aligner
import com.photomed.app.align.AlignmentResult
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.data.Expediente
import com.photomed.app.data.Photo
import com.photomed.app.data.expression
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.components.EmptyState
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.verticalScroll
import com.photomed.app.ui.formatDate
import com.photomed.app.ui.theme.Md3Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import com.photomed.app.align.AlignOptions
import com.photomed.app.align.Similarity
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private enum class Mode(val label: String) { SLIDER("Deslizar"), SIDE("Lado a lado"), GRID("Cuadrícula"), FADE("Fundido") }

/**
 * Comparación de 2…N fotos. Con N > 2 la yuxtaposición es progresiva: una línea de tiempo recorre
 * las transiciones 1→2→…→N (deslizador o fundido), y "Lado a lado"/"Cuadrícula" muestran todas.
 * Cada foto se alinea a la primera (referencia) con las condiciones activas en el menú.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(expId: String, initialZone: Zone?, onBack: () -> Unit, initialSelection: List<String> = emptyList()) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val aligner = remember { Aligner(context.applicationContext) }
    val prefs = remember { context.getSharedPreferences("photomed", android.content.Context.MODE_PRIVATE) }
    var exp by remember { mutableStateOf<Expediente?>(null) }
    var all by remember { mutableStateOf<List<Photo>?>(null) }
    var zoneFilter by remember { mutableStateOf(initialZone) }
    /** Filtro por expresión facial (arrugas dinámicas); null = todas. */
    var expFilter by remember { mutableStateOf<com.photomed.app.data.FacialExpression?>(null) }
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    var reversed by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.SLIDER) }
    var timelineState by remember { mutableStateOf(Timeline.of(2, 0.5f)) }
    var zoom by remember { mutableStateOf(ZoomState()) }
    /** Alineación de cada foto a la referencia (clave de la foto → resultado), válida para [alignedFor]. */
    var alignments by remember { mutableStateOf<Map<String, AlignmentResult>>(emptyMap()) }
    var alignedFor by remember { mutableStateOf<List<String>>(emptyList()) }
    var useAligned by remember { mutableStateOf(true) }
    var alignJob by remember { mutableStateOf<Job?>(null) }
    var alignProgress by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var keystoneAuto by remember { mutableStateOf(true) }
    var keystoneManual by remember { mutableFloatStateOf(0f) }
    var measure by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }
    var colorAdjusts by remember { mutableStateOf<Map<String, ColorAdjust>>(emptyMap()) }
    var contourOutlines by remember { mutableStateOf<Map<String, List<Pt>>>(emptyMap()) }
    var conditionsWarning by remember { mutableStateOf<String?>(null) }
    var hairlineInfo by remember { mutableStateOf<String?>(null) }
    var nearCaptured by remember { mutableStateOf(false) }
    var exportSheet by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    /** Recortar todas las fotos al área común que dejan tras alinearse (sin bordes negros). */
    var cropToAlign by remember { mutableStateOf(prefs.getBoolean("compare_crop", false)) }
    var crop by remember { mutableStateOf<Similarity?>(null) }
    /** Orden de la cuadrícula y lado a lado (solo presentación; la referencia no cambia). */
    var gridOrder by remember { mutableStateOf(GridOrder.OLDEST_FIRST) }
    // Vista de la comparación: capas de anotación, giro (pasos de 90°), espejo y recorte manual
    val layersState = rememberAnnotLayers()
    var showLayers by remember { mutableStateOf(layersState.value.isNotEmpty()) }
    var metricsSheet by remember { mutableStateOf(false) }
    // eliminar líneas (falsos positivos): vuelve a analizar
    var userVersion by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var regenerate by remember { mutableStateOf(false) }
    // panel bajo las fotos (alineación, filtros, miniaturas, medidas) desplegado o plegado (se recuerda)
    var panelOpen by remember { mutableStateOf(prefs.getBoolean("compare_panel_open", true)) }
    /** Cambia los trazos confirmados de [ps] (los que no los tienen, primero se confirman como se ven) y los guarda. */
    fun mutateTraces(ps: List<com.photomed.app.data.Photo>, previous: (com.photomed.app.data.Photo) -> List<String>,
                     change: (com.photomed.app.align.TraceSet) -> com.photomed.app.align.TraceSet) {
        scope.launch {
            var failed = 0
            withContext(Dispatchers.IO) {
                ps.forEach { p ->
                    val base = photoBaseLines(context, p.uri, previous(p)) ?: return@forEach
                    val set = com.photomed.app.align.ConfirmedTraces.ensure(context, p.uri, base, com.photomed.app.align.TraceFrame.C34)
                    if (!com.photomed.app.align.ConfirmedTraces.save(context, p.uri.toString(), change(set))) failed++
                }
            }
            userVersion++
            if (failed > 0) snackbar.showSnackbar("Trazos guardados en la app; no se pudieron escribir en $failed foto(s)")
        }
    }
    // opacidad de los trazos y números (se recuerda; empieza en 50 %)
    val annotAlpha = rememberAnnotAlpha()
    var viewRot by remember { mutableStateOf(0) }
    var viewMirror by remember { mutableStateOf(false) }
    /** Recorte elegido a mano (rectángulo 3:4 sobre la referencia); tiene prioridad sobre el automático. */
    var manualCrop by remember { mutableStateOf<FloatArray?>(null) }
    var cropEditing by remember { mutableStateOf(false) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var manualOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var options by remember { mutableStateOf(AlignOptions.fromPrefs(prefs)) }
    /** Cambia con cada reinicio de la alineación: un cálculo en curso solo se aplica si sigue vigente. */
    var alignToken by remember { mutableStateOf(0) }

    LaunchedEffect(expId) {
        val (e, list) = withContext(Dispatchers.IO) {
            val e = repo.getExpediente(expId)
            e to e?.let { repo.photos(it) }.orEmpty()
        }
        exp = e; all = list
        val photos = all.orEmpty()
        // Selección inicial: la recibida (selección múltiple de la galería) o primera y última de la zona.
        val pre = initialSelection.mapNotNull { n -> photos.firstOrNull { it.displayName == n }?.key }.take(Timeline.MAX_PHOTOS)
        if (pre.size >= 2) { selected = pre; return@LaunchedEffect }
        val z = zoneFilter ?: photos.lastOrNull()?.zone
        val zonePhotos = photos.filter { it.zone == z }
        // misma expresión: en reposo si hay dos o más; si no, la de la última foto
        val ex = if (zonePhotos.count { it.expression == com.photomed.app.data.FacialExpression.NEUTRAL } >= 2) com.photomed.app.data.FacialExpression.NEUTRAL
            else zonePhotos.lastOrNull()?.expression
        val inZone = zonePhotos.filter { it.expression == ex }
        if (zonePhotos.map { it.expression }.distinct().size > 1) expFilter = ex
        if (inZone.size >= 2) selected = listOf(inZone.first().key, inZone.last().key)
    }

    // Estado derivado. Se recalcula dentro de cada sección del Scaffold (que se subcompone aparte)
    // leyendo el estado en vivo, para no usar valores capturados de una composición anterior.
    fun derive(): CompareDerived {
        val photos = all.orEmpty()
        val chosen = selected.mapNotNull { k -> photos.firstOrNull { it.key == k } }.sortedBy { it.takenAt }
        val seq: List<Photo>? = if (chosen.size >= 2) (if (reversed) chosen.reversed() else chosen) else null
        val n = seq?.size ?: 0
        // La línea de tiempo sigue al número de fotos (sin escribir estado durante la composición).
        val timeline = if (timelineState.count == n || n < 2) timelineState else Timeline.of(n, if (n == 2) 0.5f else 0f)
        val reference = seq?.firstOrNull()
        val target = seq?.getOrNull(timeline.to)?.takeIf { it.key != reference?.key } ?: seq?.getOrNull(1)
        val seqKeys = seq?.map { it.key }.orEmpty()
        return CompareDerived(
            photos = photos,
            zonesWithPhotos = Zone.entries.filter { z -> photos.any { it.zone == z } },
            visible = photos.filter { (zoneFilter == null || it.zone == zoneFilter) && (expFilter == null || it.expression == expFilter) }.reversed(),
            seq = seq, seqKeys = seqKeys, n = n, timeline = timeline, reference = reference, target = target,
            validAlign = alignedFor == seqKeys,
        )
    }
    fun resultOf(p: Photo): AlignmentResult? = if (useAligned && alignedFor.contains(p.key) && alignedFor == derive().seqKeys) alignments[p.key] else null
    /** Recorte efectivo: el manual (rectángulo 3:4 normalizado) o el automático según la alineación. */
    fun effectiveCrop(): Similarity? = manualCrop?.let { r ->
        val w = (r[2] - r[0]).coerceAtLeast(0.05f); val k = 1f / w
        Similarity(k, 0f, -k * r[0], -k * r[1] * 4f / 3f)
    } ?: crop
    fun transformOf(p: Photo): Similarity? = CompareExport.withCrop(resultOf(p)?.transform, effectiveCrop())
    val (photos, _, _, seq, seqKeys, n, timeline, reference, target, validAlign) = derive()

    // Recorte común según la alineación: el mayor 3:4 que cubren todas las fotos alineadas.
    LaunchedEffect(cropToAlign, alignments, alignedFor, useAligned, seqKeys) {
        val s = derive().seq
        crop = if (!cropToAlign || s == null || !useAligned || alignedFor != s.map { it.key }) null
        else withContext(Dispatchers.Default) {
            overlapCrop(s.drop(1).mapNotNull { p -> alignments[p.key]?.let { footprint(it.transform, it.keystoneDeg) } })
        }
    }

    // Condiciones de captura (XMP): avisar si alguna foto no es comparable con la referencia.
    LaunchedEffect(seqKeys) {
        val s = seq ?: run { conditionsWarning = null; return@LaunchedEffect }
        val infos = withContext(Dispatchers.IO) { s.map { repo.readInfo(it)?.conditions.orEmpty() } }
        // tomadas "casi alineadas" (disparo permisivo): alinear automáticamente al abrir
        nearCaptured = infos.any { it["Status"] == "CLOSE" }
        // tricología: altura de la frente (cejas → línea de implantación) en cada foto
        hairlineInfo = hairlineSummary(infos)
        conditionsWarning = infos.drop(1).mapIndexedNotNull { i, c ->
            captureConditionsWarning(infos[0], c)?.let { w -> if (s.size > 2) "Foto ${i + 2}: $w" else w }
        }.joinToString("\n").ifEmpty { null }
    }

    fun resetAlignment() { alignToken++; alignJob?.cancel(); alignJob = null; alignments = emptyMap(); alignedFor = emptyList(); alignProgress = null; zoom = ZoomState() }

    fun setOption(o: AlignOptions) {
        options = o
        prefs.edit().putBoolean(AlignOptions.PREF_POINTS, o.points).putBoolean(AlignOptions.PREF_CONTOURS, o.contours)
            .putBoolean(AlignOptions.PREF_GAZE, o.gaze).apply()
        resetAlignment()
    }

    fun toggle(p: Photo) {
        if (p.key !in selected && selected.size >= Timeline.MAX_PHOTOS)
            scope.launch { snackbar.showSnackbar("Máximo ${Timeline.MAX_PHOTOS} fotos: se quitó la primera elegida") }
        selected = toggleSelection(selected, p.key)
        resetAlignment()
    }

    // Alinea cada foto a la referencia. Solo se aplica si la selección y las opciones no cambiaron
    // mientras tanto (las alineaciones anteriores se cancelan).
    fun runAlign() {
        val s = derive().seq ?: return
        val manualK = keystoneManual.takeIf { !keystoneAuto }
        val keys = s.map { it.key }
        val opts = options
        val outlines = contourOutlines
        val prev = alignJob
        val token = ++alignToken
        alignJob = scope.launch {
            prev?.cancelAndJoin()
            val out = mutableMapOf<String, AlignmentResult>()
            var failed: Throwable? = null
            for (i in 1 until s.size) {
                alignProgress = if (s.size > 2) "Alineando ${i}/${s.size - 1}…" else "Alineando…"
                val r = runCatching {
                    aligner.auto(s[0].zone, s[0].uri, s[i].uri, manualK,
                        opts.copy(beforeOutline = outlines[s[0].key], afterOutline = outlines[s[i].key]))
                }
                r.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
                r.onSuccess { out[s[i].key] = it }.onFailure { failed = it }
                if (alignToken != token) return@launch
                alignments = out.toMap(); alignedFor = keys
            }
            alignProgress = null
            useAligned = true
            val res = out.values
            val msg = when {
                failed != null && res.isEmpty() -> "No se pudo alinear: ${failed?.message}"
                s.size == 2 -> res.first().let { a ->
                    "Alineado: ${a.method}" + if (a.score.isNaN()) "" else " · contornos ${(a.score.coerceIn(0f, 1f) * 100).roundToInt()}%"
                }
                else -> "Alineadas ${res.size} fotos a la referencia (${formatDate(s[0].takenAt)})"
            }
            snackbar.showSnackbar(msg)
        }
    }

    // Ocultar los controles inferiores tras unos segundos sin tocar (solo con fotos en comparación)
    LaunchedEffect(lastTouch, seqKeys, alignJob, settings, exportSheet) {
        controlsVisible = true
        if (CompareIdle.enabled && seqKeys.size >= 2 && !settings && !exportSheet) {
            kotlinx.coroutines.delay(CompareIdle.HIDE_MS); controlsVisible = alignJob?.isActive == true
        }
    }

    LaunchedEffect(nearCaptured, seqKeys) {
        if (nearCaptured && seqKeys.size >= 2 && alignedFor != seqKeys && alignJob?.isActive != true) runAlign()
    }

    Scaffold(
        topBar = {
            val (_, _, _, seq, _, n) = derive()
            TopAppBar(
                title = {
                    Column {
                        Text(if (n > 2) "Comparar $n fotos" else "Comparar")
                        Text(exp?.patientName.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = { showLayers = !showLayers }, enabled = seq != null) {
                        Icon(Icons.Default.Layers, if (showLayers) "Ocultar capas" else "Capas: contorno, línea del pelo, rasgos y surcos")
                    }
                    IconButton(onClick = { reversed = !reversed; resetAlignment() }, enabled = seq != null) {
                        Icon(Icons.Default.SwapHoriz, "Invertir el orden")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            // menú corto: el resto, agrupado en la hoja de ajustes de comparación
                            DropdownMenuItem(
                                text = { Text("Ajustes de comparación") },
                                leadingIcon = { Icon(Icons.Default.Tune, null) },
                                onClick = { menu = false; settings = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar imágenes ajustadas") },
                                leadingIcon = { Icon(Icons.Default.IosShare, null) },
                                enabled = seq != null,
                                onClick = { menu = false; exportSheet = true },
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
        snackbarHost = {
            androidx.compose.foundation.layout.Column {
                val (_, _, _, seqC, _, nC) = derive()
                com.photomed.app.ui.components.CoachMark("comparar_gestos", Icons.Default.TouchApp,
                    "Los controles se ocultan solos: toque para verlos. En la cuadrícula, mantenga pulsada una foto y arrástrela para reordenar. " +
                        "Ajustes y recorte en ⋮.", show = seqC != null && nC >= 2)
                SnackbarHost(snackbar)
            }
        },
    ) { padding ->
        val (photos, zonesWithPhotos, visible, seq, seqKeys, n, timeline, reference, target, validAlign) = derive()
        // referencia de arrugas: fotos anteriores del paciente en la misma zona (la más reciente primero)
        fun previousOf(p: com.photomed.app.data.Photo) = photos.filter { it.zone == p.zone && it.expression == p.expression && it.takenAt < p.takenAt }
            .sortedByDescending { it.takenAt }.map { it.uri.toString() }
        val aligning = alignJob?.isActive == true
        // Modelos (con perspectiva y color corregidos) de cada foto de la secuencia.
        val models: Map<String, Any> = seq?.associate { p ->
            p.key to key(p.key) { rememberCorrected(p.uri, resultOf(p)?.keystoneDeg ?: 0f, Perspective.DEFAULT_FOCAL, colorAdjusts[p.key]) }
        }.orEmpty()
        // anotaciones (contorno, línea del pelo, rasgos, surcos) de cada foto, en su foto corregida
        val layers = if (showLayers) layersState.value else emptySet()
        val annots: Map<String, List<com.photomed.app.align.AnnotLine>> = seq?.mapNotNull { p ->
            // con el panel de capas abierto se analizan las fotos aunque no haya capas encendidas (la tabla de medidas lo necesita)
            key("a" + p.key) { rememberPhotoAnnotations(p.uri, p.zone, resultOf(p)?.keystoneDeg ?: 0f, showLayers,
                previous = previousOf(p), userVersion = userVersion).value }?.let { p.key to it }
        }?.toMap().orEmpty()
        val view = CompareView(viewRot, viewMirror, layers, models.mapNotNull { (k, m) -> annots[k]?.let { m to it } }.toMap(), annotAlpha.value)
        if (all != null && photos.size < 2) {
            EmptyState(Icons.Default.Compare, "Se necesitan dos fotografías",
                "Tome al menos dos fotografías de una zona para compararlas.", Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) { awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial); lastTouch = System.currentTimeMillis() }
            }
        }) {
            if (aligning) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact)) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                        icon = {},
                    ) { Text(m.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }

            if (showLayers) LayerChips(layersState.value, { layersState.value = it }, Modifier.fillMaxWidth().padding(top = 4.dp),
                onMetrics = { metricsSheet = !metricsSheet })
            if (showLayers) OpacitySlider(annotAlpha.value, { annotAlpha.value = it }, Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact))
            // expresiones distintas en la comparación: las arrugas dinámicas no son comparables
            seq?.map { it.expression }?.distinct()?.takeIf { it.size > 1 }?.let { ex ->
                Text("Atención: compara expresiones distintas (${ex.joinToString(" / ") { it.label }}); las arrugas dinámicas solo se comparan con la misma expresión.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Md3Spacing.marginCompact, vertical = 2.dp))
            }
            // análisis de arrugas y rasgos en curso: barra de proceso visible
            if (showLayers && seq != null && seq.any { annots[it.key] == null }) Row(
                Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(Modifier.weight(1f).semantics { contentDescription = "Analizando arrugas y rasgos" })
                Text("Analizando ${seq.count { annots[it.key] != null }}/${seq.size}…", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 10.dp))
            }
            // Área de comparación
            androidx.compose.runtime.CompositionLocalProvider(LocalCompareView provides view) {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact, vertical = Md3Spacing.s2),
                contentAlignment = Alignment.Center,
            ) {
                if (seq == null) {
                    Text("Seleccione dos o más fotografías abajo para compararlas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                } else {
                    // fecha; con hora y minuto solo en las fotos que comparten día con otra
                    val stamps = stampLabels(seq.map { it.takenAt })
                    // orden de presentación de la cuadrícula y lado a lado (índices de seq)
                    val shownOrder = displayOrder(seqKeys, gridOrder, manualOrder).mapNotNull { k -> seq.indexOfFirst { it.key == k }.takeIf { it >= 0 } }
                    fun label(i: Int): String {
                        val d = stamps[i]
                        return if (n == 2) (if (i == 0) "Antes · $d" else "Después · $d") else "${i + 1} · $d"
                    }
                    val from = seq[timeline.from]; val to = seq[timeline.to]
                    when (mode) {
                        Mode.SLIDER -> JuxtaposeSlider(
                            before = models.getValue(from.key), after = models.getValue(to.key),
                            beforeTransform = transformOf(from), afterTransform = transformOf(to),
                            position = timeline.dividerPosition, onPositionChange = { timelineState = timeline.withDivider(it) },
                            zoom = zoom, onZoomChange = { zoom = it },
                            beforeLabel = label(timeline.from), afterLabel = label(timeline.to),
                            modifier = Modifier.aspectRatio(view.aspect).clip(MaterialTheme.shapes.medium),
                        )

                        Mode.SIDE -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val gap = Md3Spacing.s1
                            // Hasta 3 fotos caben a lo ancho; con más, desplazamiento horizontal (2,4 visibles).
                            val cellW: Dp = if (n <= 3) minOf((maxWidth - gap * (n - 1)) / n, maxHeight * view.aspect)
                            else minOf((maxWidth - gap * 2) / 2.4f, maxHeight * view.aspect)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                shownOrder.forEach { i -> val p = seq[i]; PhotoCell(models.getValue(p.key), label(i), transformOf(p), zoom, Modifier.width(cellW)) }
                            }
                        }

                        Mode.GRID -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val gap = Md3Spacing.s1
                            val cols = gridColumns(n, maxWidth.value, maxHeight.value, view.aspect)
                            val rows = (n + cols - 1) / cols
                            val cellW: Dp = minOf((maxWidth - gap * (cols - 1)) / cols, ((maxHeight - gap * (rows - 1)) / rows) * view.aspect)
                            // Mantener pulsada una foto y arrastrarla: se reordena (orden manual)
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val stepX = with(density) { (cellW + gap).toPx() }; val stepY = with(density) { (cellW / view.aspect + gap).toPx() }
                            val orderKeys = shownOrder.map { seq[it].key }
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                shownOrder.chunked(cols).forEachIndexed { r, rowIdx ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                        rowIdx.forEachIndexed { c, si ->
                                            val p = seq[si]; val pos = r * cols + c
                                            val dragging = dragKey == p.key
                                            PhotoCell(models.getValue(p.key), label(si), transformOf(p), zoom, Modifier.width(cellW)
                                                .zIndex(if (dragging) 1f else 0f)
                                                .graphicsLayer {
                                                    if (dragging) {
                                                        translationX = dragOffset.x; translationY = dragOffset.y
                                                        scaleX = 1.05f; scaleY = 1.05f; shadowElevation = 16f
                                                    }
                                                }
                                                .pointerInput(p.key, orderKeys, cols) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { dragKey = p.key; dragOffset = androidx.compose.ui.geometry.Offset.Zero },
                                                        onDrag = { ch, d -> ch.consume(); dragOffset += d },
                                                        onDragEnd = {
                                                            val nc = (c + (dragOffset.x / stepX).roundToInt()).coerceIn(0, cols - 1)
                                                            val nr = (r + (dragOffset.y / stepY).roundToInt()).coerceIn(0, (orderKeys.size - 1) / cols)
                                                            val to = (nr * cols + nc).coerceIn(0, orderKeys.size - 1)
                                                            if (to != pos) { manualOrder = orderKeys.moved(pos, to); gridOrder = GridOrder.MANUAL }
                                                            dragKey = null; dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                                        },
                                                        onDragCancel = { dragKey = null; dragOffset = androidx.compose.ui.geometry.Offset.Zero },
                                                    )
                                                }
                                                .semantics {
                                                    customActions = listOfNotNull(
                                                        if (pos > 0) androidx.compose.ui.semantics.CustomAccessibilityAction("Mover antes") {
                                                            manualOrder = orderKeys.moved(pos, pos - 1); gridOrder = GridOrder.MANUAL; true } else null,
                                                        if (pos < orderKeys.size - 1) androidx.compose.ui.semantics.CustomAccessibilityAction("Mover después") {
                                                            manualOrder = orderKeys.moved(pos, pos + 1); gridOrder = GridOrder.MANUAL; true } else null,
                                                    )
                                                })
                                        }
                                    }
                                }
                            }
                        }

                        Mode.FADE -> Box(
                            Modifier.aspectRatio(view.aspect).clip(MaterialTheme.shapes.medium).background(Color.Black).clipToBounds()
                        ) {
                            val layer = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.offset.x; translationY = zoom.offset.y
                            }
                            ComparePhoto(models.getValue(from.key), transformOf(from), label(timeline.from), layer)
                            ComparePhoto(models.getValue(to.key), transformOf(to), label(timeline.to), layer,
                                Modifier.fillMaxSize().graphicsLayer { alpha = timeline.fraction })
                            Label(if (timeline.fraction < 0.5f) label(timeline.from) else label(timeline.to), Modifier.align(Alignment.TopStart))
                        }
                    }
                }
            }

            }

            // Línea de tiempo (fundido, o deslizador con más de dos fotos): recorre 1→2→…→N.
            if (seq != null && (mode == Mode.FADE || (mode == Mode.SLIDER && n > 2))) {
                Column(Modifier.padding(horizontal = Md3Spacing.marginCompact)) {
                    Slider(
                        value = timeline.value, onValueChange = { timelineState = Timeline.of(n, it) },
                        valueRange = 0f..(n - 1).toFloat(),
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = if (n > 2) "Progresión entre $n fotos" else "Fundido antes/después"
                        },
                    )
                    if (n > 2) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        seq.forEachIndexed { i, p ->
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (i == timeline.from || i == timeline.to) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = "Foto ${i + 1}, ${formatDate(p.takenAt)}" })
                        }
                    }
                }
            }

            // asa para plegar o desplegar todo lo que hay bajo las fotos (menos la línea de tiempo)
            if (seq != null) Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (panelOpen) "Plegar los controles" else "Desplegar los controles") {
                    panelOpen = !panelOpen; prefs.edit().putBoolean("compare_panel_open", panelOpen).apply()
                }.padding(horizontal = Md3Spacing.marginCompact),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(if (panelOpen) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    if (panelOpen) "Plegar los controles" else "Desplegar los controles", Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (panelOpen) "Ocultar controles" else "Mostrar controles", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.animation.AnimatedVisibility(panelOpen || seq == null) {
            Column {
            // tabla de medidas: debajo de las fotos, sin taparlas
            if (metricsSheet && showLayers && seq != null) {
                val stamps = stampLabels(seq.map { it.takenAt })
                MetricsPanel(seq.mapIndexed { i, p -> stamps[i] to annots[p.key].orEmpty() }, layersState.value,
                    onClose = { metricsSheet = false }, loading = seq.any { annots[it.key] == null },
                    note = seq.firstNotNullOfOrNull { photoAnnotationNote(context, it.uri, previousOf(it)) },
                    onShowLayers = { layersState.value = it },
                    // eliminar (falso positivo): los trazos de esa foto quedan confirmados sin la línea
                    onDelete = { name, col ->
                        val ps = if (col == null) seq else listOfNotNull(seq.getOrNull(col))
                        mutateTraces(ps, { previousOf(it) }) { com.photomed.app.align.ConfirmedTraces.remove(it, name) }
                    },
                    dismissedCount = remember(seqKeys, userVersion) { seq.sumOf { p ->
                        com.photomed.app.align.ConfirmedTraces.of(context, p.uri.toString())?.removed?.size
                            ?: com.photomed.app.align.AnnotUserEdits.of(context, p.uri.toString()).dismissed.size } },
                    onRestore = {
                        seq.forEach { p -> if (com.photomed.app.align.ConfirmedTraces.of(context, p.uri.toString()) == null)
                            com.photomed.app.align.AnnotUserEdits.restore(context, p.uri.toString()) }
                        mutateTraces(seq.filter { com.photomed.app.align.ConfirmedTraces.of(context, it.uri.toString()) != null }, { previousOf(it) }) {
                            com.photomed.app.align.ConfirmedTraces.restore(it) }
                    },
                    traces = run {
                        val allConfirmed = remember(seqKeys, userVersion) { seq.all { com.photomed.app.align.ConfirmedTraces.of(context, it.uri.toString()) != null } }
                        TraceActions(confirmed = allConfirmed, onConfirm = { mutateTraces(seq, { previousOf(it) }) { it } },
                            onRegenerate = { regenerate = true })
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.42f).dp))
            }

            // Controles bajo las fotos: se ocultan a los 3 s sin tocar (fotos más grandes); un toque los recupera
            androidx.compose.animation.AnimatedVisibility(controlsVisible || seq == null) {
            Column {
            // Alineación y datos de la selección
            seq?.let { s ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
                ) {
                    val res = target?.let { alignments[it.key] }.takeIf { validAlign }
                    if (!validAlign || alignments.isEmpty()) {
                        InputChip(
                            selected = false,
                            onClick = { runAlign() },
                            enabled = !aligning,
                            label = { Text(alignProgress ?: "Alinear automáticamente") },
                            leadingIcon = { Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp)) },
                        )
                        val days = TimeUnit.MILLISECONDS.toDays(kotlin.math.abs(s.last().takenAt - s.first().takenAt))
                        Text("$days días", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(
                            Modifier.weight(1f).toggleable(useAligned, role = Role.Switch) { useAligned = it },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (n > 2) "Alineadas a la foto 1 · ${alignments.size}/${n - 1}" + (alignProgress?.let { " · $it" } ?: "")
                                else "Alineado", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Switch(checked = useAligned, onCheckedChange = null)
                        }
                    }
                    // detalles de la alineación y avisos: detrás del botón de información (ocupaban mucho)
                    val gazeIssues = if (validAlign && options.gaze) s.drop(1).mapIndexedNotNull { i, p ->
                        alignments[p.key]?.gazeHint?.let { h -> if (n > 2) "Foto ${i + 2}: $h" else h }
                    } else emptyList()
                    val zoneWarn = if (s.map { it.zone }.distinct().size > 1) "Atención: zonas distintas (${s.map { it.zone.label }.distinct().joinToString(" / ")})" else null
                    val warnings = listOfNotNull(conditionsWarning, zoneWarn) + gazeIssues
                    if (res != null || hairlineInfo != null || warnings.isNotEmpty()) AlignmentInfoButton(warnings.isNotEmpty()) {
                        res?.let { AlignmentDetails(it, if (n > 2) "Foto ${s.indexOf(target) + 1}: " else "") }
                        hairlineInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        listOfNotNull(conditionsWarning).plus(gazeIssues).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        zoneWarn?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
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

            // Filtro por expresión (arrugas dinámicas): solo si hay fotos con distintas expresiones
            val exps = photos.filter { zoneFilter == null || it.zone == zoneFilter }.map { it.expression }.distinct()
            if (exps.size > 1) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Md3Spacing.marginCompact),
                horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
            ) {
                FilterChip(selected = expFilter == null, onClick = { expFilter = null }, label = { Text("Todas las expresiones") })
                exps.sortedBy { it.ordinal }.forEach { e ->
                    FilterChip(selected = expFilter == e, onClick = { expFilter = e }, label = { Text(e.label) },
                        leadingIcon = if (expFilter == e) { { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null)
                }
            }

            // Selección múltiple (toggle) de imágenes; el número indica el orden en la comparación.
            LazyRow(
                Modifier.fillMaxWidth().height(136.dp),
                contentPadding = PaddingValues(horizontal = Md3Spacing.marginCompact, vertical = Md3Spacing.s2),
                horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
            ) {
                val stripStamps = stampLabels(visible.map { it.takenAt }).let { l -> visible.indices.associate { visible[it].key to l[it] } }
                items(visible, key = { it.key }) { p ->
                    val isSel = p.key in selected
                    val order = seqKeys.indexOf(p.key).takeIf { it >= 0 }?.let { it + 1 }
                    val badge = order?.let { o -> if (n == 2) (if (o == 1) "A" else "B") else "$o" } ?: if (isSel) "✓" else null
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
                            AsyncImage(com.photomed.app.ui.photoModel(context, p.uri), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
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
                        Text((stripStamps[p.key] ?: formatDate(p.takenAt)).replace(", ", "\n") +
                            p.expression.takeIf { it != com.photomed.app.data.FacialExpression.NEUTRAL }?.let { "\n" + it.label.substringBefore(" (") }.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            }
            }
            }
            }
        }
    }

    if (regenerate) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { regenerate = false },
            title = { Text("¿Regenerar los trazos?") },
            text = { Text("Se borrarán los trazos confirmados de las fotos comparadas (también de sus metadatos) y se volverán a analizar.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                regenerate = false
                val ps = derive().seq.orEmpty()
                scope.launch { withContext(Dispatchers.IO) { ps.forEach { com.photomed.app.align.ConfirmedTraces.clear(context, it.uri.toString()) } }; userVersion++ }
            }) { Text("Regenerar") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { regenerate = false }) { Text("Cancelar") } })
    }

    if (settings) {
        val seq = derive().seq
        SafeBottomSheet(onDismiss = { settings = false }) {
            Column(Modifier.padding(horizontal = Md3Spacing.s6).padding(bottom = Md3Spacing.s8).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                Text("Ajustes de comparación", style = MaterialTheme.typography.titleLarge)
                // Alineación
                SectionTitle("Alineación automática")
                SheetSwitch("Usar puntos anatómicos", options.points) { setOption(options.copy(points = it)) }
                SheetSwitch("Usar contornos", options.contours) { setOption(options.copy(contours = it)) }
                SheetSwitch("Usar mirada (gaze)", options.gaze) { setOption(options.copy(gaze = it)) }
                androidx.compose.material3.TextButton(onClick = { settings = false; measure = true }, enabled = seq != null) {
                    Icon(Icons.Default.Straighten, null); Text("  Contornos (configuración)")
                }
                // Presentación
                SectionTitle("Presentación")
                SheetSwitch("Recortar según la alineación", cropToAlign,
                    "Todas las fotos al área común que dejan tras alinearse, sin bordes negros.") {
                    cropToAlign = it; prefs.edit().putBoolean("compare_crop", it).apply()
                    if (it && !validAlign) scope.launch { snackbar.showSnackbar("Se aplicará al alinear automáticamente") }
                }
                Text("Vista", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                    androidx.compose.material3.OutlinedButton(onClick = { viewRot = (viewRot + 1) % 4 }) { Icon(Icons.Default.RotateRight, null); Text(" 90°") }
                    androidx.compose.material3.OutlinedButton(onClick = { viewMirror = !viewMirror }) { Icon(Icons.Default.Flip, null); Text(" Espejo") }
                    androidx.compose.material3.OutlinedButton(onClick = { settings = false; cropEditing = true }, enabled = seq != null) {
                        Icon(Icons.Default.Crop, null); Text(" Recortar")
                    }
                }
                if (viewRot != 0 || viewMirror || manualCrop != null) androidx.compose.material3.TextButton(onClick = { viewRot = 0; viewMirror = false; manualCrop = null }) {
                    Text("Restablecer la vista (giro, espejo y recorte manual)")
                }
                Text("Orden de la cuadrícula y lado a lado", style = MaterialTheme.typography.bodyLarge)
                Column(Modifier.selectableGroup()) {
                    GridOrder.entries.forEach { o ->
                        Row(Modifier.fillMaxWidth().selectable(gridOrder == o, role = Role.RadioButton) {
                            if (o == GridOrder.MANUAL && gridOrder != GridOrder.MANUAL) manualOrder = displayOrder(derive().seqKeys, gridOrder, manualOrder)
                            gridOrder = o
                        }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = gridOrder == o, onClick = null)
                            Text(o.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                // Color
                SectionTitle("Color de la piel")
                Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                    if (colorAdjusts.isEmpty()) {
                        androidx.compose.material3.OutlinedButton(onClick = { settings = false; colorDialog = true }, enabled = seq != null) {
                            Icon(Icons.Default.Palette, null); Text("  Igualar")
                        }
                        if ((seq?.size ?: 0) > 2) androidx.compose.material3.OutlinedButton(onClick = {
                            val s = seq ?: return@OutlinedButton
                            settings = false
                            scope.launch {
                                val out = s.drop(1).mapNotNull { p ->
                                    runCatching { autoColorAdjust(context, aligner, s[0], p) }
                                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                        .getOrNull()?.let { p.key to it }
                                }.toMap()
                                colorAdjusts = out
                                snackbar.showSnackbar(if (out.isEmpty()) "No se encontró piel para igualar" else "Color igualado en ${out.size} fotos")
                            }
                        }) { Icon(Icons.Default.AutoFixHigh, null); Text("  Todas (automático)") }
                    } else androidx.compose.material3.OutlinedButton(onClick = { colorAdjusts = emptyMap() }) { Text("Quitar igualación de color") }
                }
                // Perspectiva
                SectionTitle("Perspectiva vertical")
                Row(
                    Modifier.fillMaxWidth().toggleable(keystoneAuto, role = Role.Switch) { keystoneAuto = it; resetAlignment() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Automática por proporciones de contornos", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Estima la inclinación entre tomas con la geometría del contorno (puntos faciales, contornos " +
                                "elegidos en “Contornos” o anchos de la silueta a cada altura), sin usar sensores.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = keystoneAuto, onCheckedChange = null)
                }
                target?.let { alignments[it.key] }?.takeIf { validAlign }?.let {
                    Text("Última estimación: %+.1f°".format(it.keystoneDeg) +
                        (it.keystoneSource.takeIf { s -> s.isNotEmpty() }?.let { s -> " · $s" } ?: ""),
                        style = MaterialTheme.typography.labelLarge)
                }
                Text("Ajuste manual de las fotos posteriores: %+.1f°".format(keystoneManual), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = keystoneManual, onValueChange = { keystoneManual = it; resetAlignment() },
                    valueRange = -15f..15f, enabled = !keystoneAuto,
                    modifier = Modifier.semantics { contentDescription = "Perspectiva vertical manual" },
                )
                Text("Vuelva a alinear para aplicar los cambios.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Recorte manual: rectángulo 3:4 sobre la referencia (alineada), aplicado a todas las fotos
    if (cropEditing) {
        val ref = derive().seq?.firstOrNull()
        val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, ref?.uri) {
            value = ref?.let { runCatching { com.photomed.app.align.ImageOps.load34(context, it.uri, 900).asImageBitmap() }.getOrNull() }
        }
        val b = img
        if (ref == null) cropEditing = false
        else if (b != null) com.photomed.app.ui.components.CropEditor(b, aspect = 3f / 4f, initial = manualCrop,
            onCancel = { cropEditing = false }) { r -> manualCrop = r; cropEditing = false }
    }

    if (exportSheet && seq != null) {
        val shown = seq.mapNotNull { p -> resultOf(p)?.let { p.key to it } }.toMap()
        // anotaciones ya calculadas (caché) para dibujarlas en lo exportado
        val exportAnnots = seq.mapNotNull { p ->
            com.photomed.app.align.PhotoAnnotator.cached("34|${p.uri}|${com.photomed.app.align.PhotoEdits.of(context, p.uri.toString()).hashCode()}")?.let { base ->
                val phi = shown[p.key]?.keystoneDeg ?: 0f
                p.key to (if (phi == 0f) base else base.map { l -> l.copy(points = l.points.map { com.photomed.app.align.Perspective.mapPt(it, phi) }) })
            }
        }.toMap()
        ExportSheet(
            count = seq.size, aligned = shown.isNotEmpty(), colorMatched = colorAdjusts.isNotEmpty(), cropped = crop != null,
            onDismiss = { exportSheet = false },
            // vista previa del GIF: las fotos ajustadas (alineación, recorte, color) en tamaño reducido
            loadPreview = {
                val labels = stampLabels(seq.map { it.takenAt })
                seq.mapIndexed { i, p ->
                    val d = labels[i]
                    CompareExport.render(context, p, shown[p.key], colorAdjusts[p.key], width = 300, crop = effectiveCrop(),
                        annots = exportAnnots[p.key], layers = if (showLayers) layersState.value else emptySet(),
                        orient = com.photomed.app.align.ImageEdit(viewRot, viewMirror), annotAlpha = annotAlpha.value) to
                        (if (seq.size == 2) (if (i == 0) "Antes · " else "Después · ") + d else "${i + 1} · $d")
                }
            },
            onExport = { ch, toGallery, progress ->
                val e = exp
                val anonymize = ch.anonymize
                val dir = java.io.File(context.cacheDir, "share/export").apply { deleteRecursively(); mkdirs() }
                val order = displayOrder(seq.map { it.key }, gridOrder, manualOrder).map { k -> seq.indexOfFirst { it.key == k } }
                val r = CompareExport.build(context, repo, seq, shown, colorAdjusts,
                    individual = ch.kind == ExportKind.INDIVIDUAL, grid = ch.kind == ExportKind.GRID, anonymize = anonymize, dir = dir,
                    onProgress = progress, side = ch.kind == ExportKind.SIDE, gif = ch.gif.takeIf { ch.kind == ExportKind.GIF },
                    gifTiming = ch.timing, crop = effectiveCrop(), labels = stampLabels(seq.map { it.takenAt }), order = order,
                    annots = exportAnnots, layers = if (showLayers) layersState.value else emptySet(), annotAlpha = annotAlpha.value,
                    orient = com.photomed.app.align.ImageEdit(viewRot, viewMirror))
                when {
                    r.files.isEmpty() -> "No se pudo exportar ninguna imagen"
                    toGallery && e != null -> {
                        val ok = CompareExport.saveToGallery(repo, e, r.files)
                        "Guardadas $ok imágenes en la galería: PhotoMed/${e.id}/${com.photomed.app.data.PhotoRepository.EXPORT_DIR}" +
                            if (r.failed > 0) " (${r.failed} no se pudieron leer)" else ""
                    }
                    else -> { CompareExport.share(context, r.files, anonymize); null }
                }
            },
            onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } },
        )
    }

    if (colorDialog && reference != null && target != null) {
        ColorMatchDialog(reference, target, onDismiss = { colorDialog = false }, onApply = {
            colorDialog = false; colorAdjusts = colorAdjusts + (target.key to it)
            scope.launch { snackbar.showSnackbar("Color igualado: ${it.describe()}") }
        })
    }

    if (measure && reference != null && target != null) {
        ContourMeasureDialog(
            before = reference, after = target,
            onDismiss = { measure = false },
            onContours = { b, a ->
                val m = contourOutlines.toMutableMap()
                if (b != null) m[reference.key] = b else m.remove(reference.key)
                if (a != null) m[target.key] = a else m.remove(target.key)
                if (m != contourOutlines) { contourOutlines = m; resetAlignment() }
            },
        )
    }
}

/** Ocultación automática de los controles de Comparar (las pruebas de uso la desactivan). */
object CompareIdle {
    @Volatile var enabled = true
    const val HIDE_MS = 3000L
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Md3Spacing.s3))
}

@Composable
private fun SheetSwitch(text: String, checked: Boolean, desc: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            desc?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CheckItem(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = { onChange(!checked) },
        modifier = Modifier.semantics { stateDescription = if (checked) "Activado" else "Desactivado" },
    )
}

@Composable
private fun PhotoCell(model: Any, label: String, t: Similarity?, zoom: ZoomState, modifier: Modifier) {
    Box(modifier.aspectRatio(LocalCompareView.current.aspect).clip(MaterialTheme.shapes.small).background(Color.Black).clipToBounds()) {
        ComparePhoto(model, t, label, Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.offset.x; translationY = zoom.offset.y
        })
        Label(label, Modifier.align(Alignment.TopStart))
    }
}

/**
 * Botón de información de la alineación: los detalles (método, escala, giro, coincidencia por rasgo,
 * frente) y los avisos se muestran en un globo al tocarlo. Con avisos, el icono va en color de aviso.
 */
@Composable
private fun AlignmentInfoButton(warn: Boolean, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(if (warn) Icons.Default.Warning else Icons.Default.Info, "Detalles de la alineación",
                tint = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.widthIn(max = 320.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Alineación", style = MaterialTheme.typography.titleSmall)
                content()
            }
        }
    }
}

@Composable
private fun AlignmentDetails(res: AlignmentResult, prefix: String) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        prefix + "%s · %.0f%% escala · %.1f°".format(
            res.landmarks.takeIf { it.isNotEmpty() }?.let { "${it.size} puntos (${res.landmarkRegions.take(4).joinToString(", ")})" }
                ?: res.method.lowercase(),
            res.transform.scale * 100, res.transform.rotationDegrees,
        ), style = style, color = color,
    )
    if (res.keystoneDeg != 0f) Text("Perspectiva vertical %+.1f° (%s)".format(res.keystoneDeg, res.keystoneSource), style = style, color = color)
    // validación por rasgo: ojos, cejas, nariz, labios y contorno tras alinear
    if (res.featureScores.isNotEmpty()) {
        Text("Coincidencia por rasgo: " + res.featureScores.joinToString(" · ") { (n, v) -> "$n ${(v * 100).roundToInt()} %" },
            style = style, color = color, modifier = Modifier.semantics { contentDescription = "Coincidencia por rasgo" })
        val low = res.featureScores.filter { it.second < com.photomed.app.align.FeatureMatch.WARN }.map { it.first }
        if (low.isNotEmpty() && low.size < res.featureScores.size)
            Text("No coinciden: ${low.joinToString(", ")} (expresión, pose o cambio local distinto del resto)",
                style = style, color = MaterialTheme.colorScheme.error)
    }
    if (res.regionScores.isNotEmpty() && res.method.contains("ontorno")) {
        Text("Contornos: " + res.regionScores.sortedByDescending { it.second }.take(4)
            .joinToString(" · ") { (nm, v) -> "$nm ${(v.coerceIn(0f, 1f) * 100).roundToInt()}%" }, style = style, color = color)
    }
    when (res.gazeMatches) {
        true -> Text("Mirada: misma dirección", style = style, color = color)
        false -> Unit   // se avisa debajo
        null -> Unit
    }
}

/** Estado derivado de la selección (ver `derive()` en [CompareScreen]). */
private data class CompareDerived(
    val photos: List<Photo>,
    val zonesWithPhotos: List<Zone>,
    val visible: List<Photo>,
    val seq: List<Photo>?,
    val seqKeys: List<String>,
    val n: Int,
    val timeline: Timeline,
    val reference: Photo?,
    val target: Photo?,
    val validAlign: Boolean,
)

/** Descripción de la estrategia de la zona (para ayudas contextuales). */
fun alignmentHint(zone: Zone) = ZoneAlignment.of(zone).description

/** Imagen con la perspectiva vertical corregida y el color igualado (o la URI tal cual si no hay cambios). */
@Composable
private fun rememberCorrected(uri: android.net.Uri, pitchDeg: Float, focalNorm: Float, color: ColorAdjust? = null): Any {
    val context = LocalContext.current
    // φ exacto: la semejanza se calculó sobre la foto corregida con este mismo ángulo
    val key = pitchDeg
    val state = androidx.compose.runtime.produceState<Any>(uri, uri, key, focalNorm, color) {
        if (key == 0f && color == null) { value = com.photomed.app.ui.photoModel(context, uri); return@produceState }
        value = withContext(Dispatchers.Default) {
            runCatching {
                var bmp = ImageOps.correctPerspective(ImageOps.load34(context, uri, 1080), key, focalNorm)
                if (color != null) {
                    val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                    val out = color.apply(px)
                    bmp = android.graphics.Bitmap.createBitmap(bmp.width, bmp.height, android.graphics.Bitmap.Config.ARGB_8888)
                        .apply { setPixels(out, 0, width, 0, 0, width, height) }
                }
                bmp
            }.getOrNull()
        } ?: uri
    }
    return state.value
}

/**
 * Altura de la frente (cejas → línea de implantación, en distancias interpupilares) de cada foto y
 * su cambio respecto de la primera; null si menos de dos fotos la tienen. Una línea que retrocede
 * (frente más alta) indica pérdida de pelo frontal; normalizada por la interpupilar, no depende de
 * la distancia ni del zoom.
 */
fun hairlineSummary(infos: List<Map<String, String>>): String? {
    val v = infos.map { it["HairlineIpd"]?.toFloatOrNull() }
    val first = v.firstOrNull() ?: return null
    if (v.count { it != null } < 2) return null
    val parts = v.map { x -> x?.let { "%.2f".format(it) } ?: "—" }
    val last = v.last() ?: return "Frente (cejas → implantación): " + parts.joinToString(" → ") + " ×IPD"
    val pct = (last - first) / first * 100
    return "Frente (cejas → implantación): " + parts.joinToString(" → ") + " ×IPD (%+.0f %%%s)".format(pct,
        if (kotlin.math.abs(pct) < 4) ", estable" else if (pct > 0) ", la línea retrocede" else ", la línea avanza")
}

/** Diferencias relevantes entre las condiciones de captura de dos fotos (o null si son comparables). */
fun captureConditionsWarning(b: Map<String, String>, a: Map<String, String>): String? {
    val issues = mutableListOf<String>()
    val yb = b["HeadYaw"]?.toFloatOrNull(); val ya = a["HeadYaw"]?.toFloatOrNull()
    if (yb != null && ya != null && kotlin.math.abs(yb - ya) > 6f) issues += "giro de la cabeza %.0f° vs %.0f°".format(yb, ya)
    if (b["Side"] != null && a["Side"] != null && b["Side"] != a["Side"]) issues += "lado distinto (${b["Side"]} / ${a["Side"]})"
    val pb = b["HeadPitch"]?.toFloatOrNull(); val pa = a["HeadPitch"]?.toFloatOrNull()
    if (pb != null && pa != null && kotlin.math.abs(pb - pa) > 6f) issues += "cabeceo (barbilla) %+.0f° vs %+.0f°".format(pb, pa)
    val db = b["DistanceCm"]?.toFloatOrNull(); val da = a["DistanceCm"]?.toFloatOrNull()
    if (db != null && da != null && kotlin.math.abs(db - da) / maxOf(db, da) > 0.25f) issues += "distancia %.0f vs %.0f cm".format(db, da)
    if (b["Engine"] != null && a["Engine"] != null && b["Engine"] != a["Engine"]) issues += "motor de puntos distinto"
    listOf(b, a).forEachIndexed { i, c ->
        if (c["Status"] != null && c["Status"] != "MATCH") issues += (if (i == 0) "“antes”" else "“después”") + " sin encuadre confirmado"
    }
    return if (issues.isEmpty()) null else "Condiciones de captura distintas: " + issues.joinToString("; ")
}
