package com.photomed.app.ui.compare

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Tune
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
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.components.EmptyState
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
        val inZone = photos.filter { it.zone == z }
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
            visible = photos.filter { zoneFilter == null || it.zone == zoneFilter }.reversed(),
            seq = seq, seqKeys = seqKeys, n = n, timeline = timeline, reference = reference, target = target,
            validAlign = alignedFor == seqKeys,
        )
    }
    fun resultOf(p: Photo): AlignmentResult? = if (useAligned && alignedFor.contains(p.key) && alignedFor == derive().seqKeys) alignments[p.key] else null
    fun transformOf(p: Photo): Similarity? = resultOf(p)?.transform
    val (photos, _, _, seq, seqKeys, n, timeline, reference, target, validAlign) = derive()

    // Condiciones de captura (XMP): avisar si alguna foto no es comparable con la referencia.
    LaunchedEffect(seqKeys) {
        val s = seq ?: run { conditionsWarning = null; return@LaunchedEffect }
        val infos = withContext(Dispatchers.IO) { s.map { repo.readInfo(it)?.conditions.orEmpty() } }
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
                    IconButton(onClick = { reversed = !reversed; resetAlignment() }, enabled = seq != null) {
                        Icon(Icons.Default.SwapHoriz, "Invertir el orden")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            Text("Alineación automática", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Md3Spacing.s3, vertical = Md3Spacing.s1))
                            CheckItem("Usar puntos anatómicos", options.points) { setOption(options.copy(points = it)) }
                            CheckItem("Usar contornos", options.contours) { setOption(options.copy(contours = it)) }
                            CheckItem("Usar mirada (gaze)", options.gaze) { setOption(options.copy(gaze = it)) }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (colorAdjusts.isEmpty()) "Igualar color de piel" else "Quitar igualación de color") },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                enabled = seq != null,
                                onClick = { menu = false; if (colorAdjusts.isEmpty()) colorDialog = true else colorAdjusts = emptyMap() },
                            )
                            if (n > 2 && colorAdjusts.isEmpty()) DropdownMenuItem(
                                text = { Text("Igualar color de todas (automático)") },
                                leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) },
                                onClick = {
                                    menu = false
                                    val s = seq ?: return@DropdownMenuItem
                                    scope.launch {
                                        val out = s.drop(1).mapNotNull { p ->
                                            runCatching { autoColorAdjust(context, aligner, s[0], p) }
                                                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                                .getOrNull()?.let { p.key to it }
                                        }.toMap()
                                        colorAdjusts = out
                                        snackbar.showSnackbar(if (out.isEmpty()) "No se encontró piel para igualar" else "Color igualado en ${out.size} fotos")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Contornos (configuración)") },
                                leadingIcon = { Icon(Icons.Default.Straighten, null) },
                                enabled = seq != null,
                                onClick = { menu = false; measure = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Perspectiva y ajustes") },
                                leadingIcon = { Icon(Icons.Default.Tune, null) },
                                enabled = seq != null,
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
        val (photos, zonesWithPhotos, visible, seq, seqKeys, n, timeline, reference, target, validAlign) = derive()
        val aligning = alignJob?.isActive == true
        // Modelos (con perspectiva y color corregidos) de cada foto de la secuencia.
        val models: Map<String, Any> = seq?.associate { p ->
            p.key to key(p.key) { rememberCorrected(p.uri, resultOf(p)?.keystoneDeg ?: 0f, Perspective.DEFAULT_FOCAL, colorAdjusts[p.key]) }
        }.orEmpty()
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
                        icon = {},
                    ) { Text(m.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }

            // Área de comparación
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = Md3Spacing.marginCompact, vertical = Md3Spacing.s2),
                contentAlignment = Alignment.Center,
            ) {
                if (seq == null) {
                    Text("Seleccione dos o más fotografías abajo para compararlas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                } else {
                    fun label(i: Int): String {
                        val d = formatDate(seq[i].takenAt)
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
                            modifier = Modifier.aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium),
                        )

                        Mode.SIDE -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val gap = Md3Spacing.s1
                            // Hasta 3 fotos caben a lo ancho; con más, desplazamiento horizontal (2,4 visibles).
                            val cellW: Dp = if (n <= 3) minOf((maxWidth - gap * (n - 1)) / n, maxHeight * 3f / 4f)
                            else minOf((maxWidth - gap * 2) / 2.4f, maxHeight * 3f / 4f)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                seq.forEachIndexed { i, p -> PhotoCell(models.getValue(p.key), label(i), transformOf(p), zoom, Modifier.width(cellW)) }
                            }
                        }

                        Mode.GRID -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val gap = Md3Spacing.s1
                            val cols = gridColumns(n, maxWidth.value, maxHeight.value)
                            val rows = (n + cols - 1) / cols
                            val cellW: Dp = minOf((maxWidth - gap * (cols - 1)) / cols, ((maxHeight - gap * (rows - 1)) / rows) * 3f / 4f)
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                seq.chunked(cols).forEachIndexed { r, rowPhotos ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                        rowPhotos.forEachIndexed { c, p ->
                                            PhotoCell(models.getValue(p.key), label(r * cols + c), transformOf(p), zoom, Modifier.width(cellW))
                                        }
                                    }
                                }
                            }
                        }

                        Mode.FADE -> Box(
                            Modifier.aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium).background(Color.Black).clipToBounds()
                        ) {
                            val layer = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.offset.x; translationY = zoom.offset.y
                            }
                            AsyncImage(models.getValue(from.key), label(timeline.from), contentScale = ContentScale.Crop,
                                modifier = layer.alignedBy(transformOf(from)))
                            AsyncImage(models.getValue(to.key), label(timeline.to), contentScale = ContentScale.Crop,
                                modifier = layer.alignedBy(transformOf(to)).graphicsLayer { alpha = timeline.fraction })
                            Label(if (timeline.fraction < 0.5f) label(timeline.from) else label(timeline.to), Modifier.align(Alignment.TopStart))
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
                            Column(Modifier.weight(1f)) {
                                Text(if (n > 2) "Alineadas a la foto 1 · ${alignments.size}/${n - 1}" + (alignProgress?.let { " · $it" } ?: "")
                                    else "Alineado", style = MaterialTheme.typography.labelLarge)
                                res?.let { AlignmentDetails(it, if (n > 2) "Foto ${s.indexOf(target) + 1}: " else "") }
                            }
                            Switch(checked = useAligned, onCheckedChange = null)
                        }
                    }
                }
                val gazeIssues = if (validAlign && options.gaze) s.drop(1).mapIndexedNotNull { i, p ->
                    alignments[p.key]?.gazeHint?.let { h -> if (n > 2) "Foto ${i + 2}: $h" else h }
                } else emptyList()
                (listOfNotNull(conditionsWarning) + gazeIssues).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = Md3Spacing.marginCompact))
                }
                if (s.map { it.zone }.distinct().size > 1) {
                    Text(
                        "Atención: zonas distintas (${s.map { it.zone.label }.distinct().joinToString(" / ")})",
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

            // Selección múltiple (toggle) de imágenes; el número indica el orden en la comparación.
            LazyRow(
                Modifier.fillMaxWidth().height(124.dp),
                contentPadding = PaddingValues(horizontal = Md3Spacing.marginCompact, vertical = Md3Spacing.s2),
                horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
            ) {
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

    if (settings && seq != null) {
        SafeBottomSheet(onDismiss = { settings = false }) {
            Column(Modifier.padding(horizontal = Md3Spacing.s6).padding(bottom = Md3Spacing.s8),
                verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                Text("Perspectiva vertical", style = MaterialTheme.typography.titleLarge)
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
    Box(modifier.aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small).background(Color.Black).clipToBounds()) {
        AsyncImage(model, label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.offset.x; translationY = zoom.offset.y
        }.alignedBy(t))
        Label(label, Modifier.align(Alignment.TopStart))
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
    val key = (pitchDeg * 2).roundToInt() / 2f
    val state = androidx.compose.runtime.produceState<Any>(uri, uri, key, focalNorm, color) {
        if (key == 0f && color == null) { value = uri; return@produceState }
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

/** Diferencias relevantes entre las condiciones de captura de dos fotos (o null si son comparables). */
fun captureConditionsWarning(b: Map<String, String>, a: Map<String, String>): String? {
    val issues = mutableListOf<String>()
    val yb = b["HeadYaw"]?.toFloatOrNull(); val ya = a["HeadYaw"]?.toFloatOrNull()
    if (yb != null && ya != null && kotlin.math.abs(yb - ya) > 6f) issues += "giro de la cabeza %.0f° vs %.0f°".format(yb, ya)
    val db = b["DistanceCm"]?.toFloatOrNull(); val da = a["DistanceCm"]?.toFloatOrNull()
    if (db != null && da != null && kotlin.math.abs(db - da) / maxOf(db, da) > 0.25f) issues += "distancia %.0f vs %.0f cm".format(db, da)
    if (b["Engine"] != null && a["Engine"] != null && b["Engine"] != a["Engine"]) issues += "motor de puntos distinto"
    listOf(b, a).forEachIndexed { i, c ->
        if (c["Status"] != null && c["Status"] != "MATCH") issues += (if (i == 0) "“antes”" else "“después”") + " sin encuadre confirmado"
    }
    return if (issues.isEmpty()) null else "Condiciones de captura distintas: " + issues.joinToString("; ")
}
