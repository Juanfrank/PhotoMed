package com.photomed.app.ui.compare

import com.photomed.app.ui.components.detectTapPassThrough
import com.photomed.app.ui.components.detectZoomPanPassThrough
import androidx.compose.foundation.gestures.detectTapGestures
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.ImageOps
import com.photomed.app.align.Perspective
import com.photomed.app.align.PhotoAnnotator
import com.photomed.app.align.PhotoEdits
import com.photomed.app.align.Pt
import com.photomed.app.align.Similarity
import com.photomed.app.data.Zone
import com.photomed.app.ui.camera.annotColor
import com.photomed.app.ui.camera.drawAnnotations
import com.photomed.app.ui.camera.drawMeasureGuides
import com.photomed.app.ui.camera.drawInk
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput

private const val PREF_LAYERS = "annot_layers"
private const val PREF_ALPHA = "annot_alpha"

/** Opacidad de los trazos y números sobre las fotos (se recuerda; por defecto 50 %). */
@Composable
fun rememberAnnotAlpha(): MutableState<Float> {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photomed", Context.MODE_PRIVATE) }
    return remember {
        object : MutableState<Float> {
            private val s = mutableStateOf(prefs.getFloat(PREF_ALPHA, 0.5f))
            override var value: Float
                get() = s.value
                set(v) { s.value = v; prefs.edit().putFloat(PREF_ALPHA, v).apply() }
            override fun component1() = value
            override fun component2(): (Float) -> Unit = { value = it }
        }
    }
}

/** Control de la opacidad de los trazos y números (10–100 %). */
@Composable
fun OpacitySlider(alpha: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, light: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Opacidad ${(alpha * 100).toInt()} %", style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = if (light) androidx.compose.ui.graphics.Color.White else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp))
        androidx.compose.material3.Slider(value = alpha, onValueChange = onChange, valueRange = 0.1f..1f,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Opacidad de los trazos y números" })
    }
}

/** Capas de anotación visibles (se recuerdan entre sesiones; por defecto, ninguna). */
@Composable
fun rememberAnnotLayers(): MutableState<Set<AnnotLayer>> {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photomed", Context.MODE_PRIVATE) }
    return remember {
        object : MutableState<Set<AnnotLayer>> {
            private val s = mutableStateOf(prefs.getString(PREF_LAYERS, "").orEmpty().split(',')
                .mapNotNull { n -> AnnotLayer.entries.firstOrNull { it.name == n } }.toSet())
            override var value: Set<AnnotLayer>
                get() = s.value
                set(v) { s.value = v; prefs.edit().putString(PREF_LAYERS, v.joinToString(",") { it.name }).apply() }
            override fun component1() = value
            override fun component2(): (Set<AnnotLayer>) -> Unit = { value = it }
        }
    }
}

/** Capas del contorno externo (silueta de la cara y línea del pelo). */
val OUTER_LAYERS = listOf(AnnotLayer.CONTOUR, AnnotLayer.HAIRLINE, AnnotLayer.JAWLINE, AnnotLayer.PAPADA)
/** Capas del contorno interno: rasgos (ojos y cejas, nariz, labios), arrugas, surcos y ojeras. */
val INNER_LAYERS = AnnotLayer.entries - OUTER_LAYERS.toSet()

/**
 * Selector de capas: «Medidas», «Trazar surco» y dos desplegables con casillas, **Contorno externo** y
 * **Contorno interno**, cada uno con «Seleccionar todos» como primera opción.
 */
@Composable
fun LayerChips(layers: Set<AnnotLayer>, onChange: (Set<AnnotLayer>) -> Unit, modifier: Modifier = Modifier,
               /** Si se da, primer chip «Medidas» (tabla de longitud, ancho, profundidad y grado). */
               onMetrics: (() -> Unit)? = null,
               /** Si se da, chip «Trazar surco» (dibujar a mano alzada el surco que falta). */
               onTrace: (() -> Unit)? = null, tracing: Boolean = false,
               /** Si se da, chip «Vista» con las cuatro capas (imagen, diagrama, anotaciones, overlay). */
               viewLayers: MutableState<ViewLayers>? = null) {
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onMetrics != null) androidx.compose.material3.AssistChip(onClick = onMetrics, label = { Text("Medidas") },
            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.TableChart, null, Modifier.size(18.dp)) })
        if (onTrace != null) FilterChip(selected = tracing, onClick = onTrace, label = { Text(if (tracing) "Dibuje el surco…" else "Trazar surco") },
            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Gesture, null, Modifier.size(18.dp)) })
        LayerMenu("Contorno externo", OUTER_LAYERS, layers, onChange)
        LayerMenu("Contorno interno", INNER_LAYERS, layers, onChange)
        if (viewLayers != null) ViewLayersButton(viewLayers)
    }
}

/** Desplegable con casillas de un grupo de capas; la primera opción marca o desmarca todas. */
@Composable
fun LayerMenu(title: String, group: List<AnnotLayer>, layers: Set<AnnotLayer>, onChange: (Set<AnnotLayer>) -> Unit) {
    var open by androidx.compose.runtime.remember { mutableStateOf(false) }
    val on = group.count { it in layers }
    Box {
        FilterChip(selected = on > 0, onClick = { open = true },
            label = { Text("$title ($on/${group.size})") },
            trailingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
            modifier = Modifier.semantics { contentDescription = "$title: $on de ${group.size} capas visibles" })
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val all = on == group.size
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Seleccionar todos", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                leadingIcon = {
                    androidx.compose.material3.TriStateCheckbox(
                        state = when (on) { 0 -> androidx.compose.ui.state.ToggleableState.Off; group.size -> androidx.compose.ui.state.ToggleableState.On
                            else -> androidx.compose.ui.state.ToggleableState.Indeterminate }, onClick = null)
                },
                onClick = { onChange(if (all) layers - group.toSet() else layers + group) })
            androidx.compose.material3.HorizontalDivider()
            group.forEach { l ->
                val checked = l in layers
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(l.label) },
                    leadingIcon = { androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null) },
                    trailingIcon = { Canvas(Modifier.size(10.dp)) { drawCircle(annotColor(l)) } },
                    onClick = { onChange(if (checked) layers - l else layers + l) })
            }
        }
    }
}

/**
 * Anotaciones de una foto en su encuadre 3:4 (con su edición guardada), llevadas a la foto corregida
 * en perspectiva [phiDeg] (como se muestra en Comparar). null mientras se calculan.
 */
/** Motivo (si lo hay) por el que la foto no tiene medidas, tras analizarla. */
fun photoAnnotationNote(context: Context, uri: Uri, previous: List<String> = emptyList()): String? {
    if (com.photomed.app.align.ConfirmedTraces.of(context, uri.toString()) != null) return null
    return PhotoAnnotator.note(key34(context, uri, previous))
}

private fun key34(context: Context, uri: Uri, previous: List<String>): String {
    val edit = PhotoEdits.of(context, uri.toString()); val u = uri.toString()
    return "34|$uri|${edit.hashCode()}|${com.photomed.app.align.ReliefStore.tag(context, u)}|" +
        "${com.photomed.app.align.AnnotUserEdits.tag(context, u)}|${com.photomed.app.align.LineMemory.tag(context, previous)}"
}

/**
 * Líneas de la foto en su encuadre 3:4 (sin corrección de perspectiva), tal como se analizaron o se
 * confirmaron; null si aún no se han calculado.
 */
fun photoBaseLines(context: Context, uri: Uri, previous: List<String> = emptyList()): List<AnnotLine>? =
    com.photomed.app.align.ConfirmedTraces.of(context, uri.toString())?.lines(PhotoEdits.of(context, uri.toString()), com.photomed.app.align.TraceFrame.C34)
        ?: PhotoAnnotator.cached(key34(context, uri, previous))

@Composable
fun rememberPhotoAnnotations(uri: Uri, zone: Zone, phiDeg: Float, enabled: Boolean,
                              /** Fotos anteriores del paciente en la zona (la más reciente primero): referencia de arrugas. */
                              previous: List<String> = emptyList(),
                              /** Cambia al eliminar líneas (vuelve a analizar). */
                              userVersion: Int = 0): State<List<AnnotLine>?> {
    val context = LocalContext.current
    val edit = PhotoEdits.of(context, uri.toString())
    return produceState<List<AnnotLine>?>(null, uri, edit, phiDeg, enabled, userVersion, previous) {
        if (!enabled) { value = null; return@produceState }
        val u = uri.toString()
        // trazos confirmados (guardados en la foto): se muestran tal cual, sin volver a analizar
        val confirmed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.photomed.app.align.ConfirmedTraces.of(context, u) }
        if (confirmed != null) {
            fun show(set: com.photomed.app.align.TraceSet) {
                value = set.lines(edit, com.photomed.app.align.TraceFrame.C34).map { l -> if (phiDeg == 0f) l else l.mapped { Perspective.mapPt(it, phiDeg) } }
            }
            show(confirmed)
            // trazos confirmados en 2.23–2.24 sin tramos de los rasgos: se recuperan también aquí
            if (com.photomed.app.align.GuideMigration.needsFeatureGuides(confirmed))
                com.photomed.app.align.GuideMigration.backfill(context, uri, zone)?.let { show(it) }
            return@produceState
        }
        val user = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.photomed.app.align.AnnotUserEdits.of(context, u) }.let { e ->
            // los trazos añadidos están en la foto completa: se llevan al encuadre 3:4 de Comparar
            if (e.editHash != edit.hashCode()) e.copy(added = emptyList()) else e.copy(added = e.addedIn34()) }
        val key = key34(context, uri, previous)
        val base = PhotoAnnotator.cached(key) ?: if (!PhotoAnnotator.enabled) emptyList() else runCatching {
            PhotoAnnotator(context.applicationContext).analyze(ImageOps.load34(context, uri, 1080), zone,
                PhotoAnnotator.extrasFor(context, uri, 1080, crop34 = true),
                user = user, priors = com.photomed.app.align.LineMemory.priors(context, previous), memoryUri = u)
        }.getOrNull()?.let { (lines, note) -> PhotoAnnotator.put(key, lines); PhotoAnnotator.putNote(key, note); lines }
            ?: emptyList<AnnotLine>().also { PhotoAnnotator.putNote(key, "No se pudo analizar la foto.") }
        value = base?.map { l -> if (phiDeg == 0f) l else l.mapped { Perspective.mapPt(it, phiDeg) } }
    }
}

/** Anotaciones de una foto en su encuadre 3:4, llevadas a la foto corregida en perspectiva [phiDeg]. */
@Composable
fun rememberPhotoInk(uri: Uri, phiDeg: Float, enabled: Boolean): State<List<com.photomed.app.align.InkStroke>?> {
    val context = LocalContext.current
    val edit = PhotoEdits.of(context, uri.toString())
    val tag = com.photomed.app.align.Annotations.tag(context, uri.toString())
    return produceState<List<com.photomed.app.align.InkStroke>?>(null, uri, edit, phiDeg, enabled, tag) {
        if (!enabled) { value = null; return@produceState }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.photomed.app.align.Annotations.of(context, uri.toString())?.inView(edit, com.photomed.app.align.TraceFrame.C34) }.getOrNull()
        }?.map { s -> if (phiDeg == 0f) s else s.copy(points = s.points.map { Perspective.mapPt(it, phiDeg) }) }.orEmpty()
    }
}

/** Foto 3:4 (modelo de Coil) con la semejanza de alineación y sus anotaciones en la misma capa. */
@Composable
fun PhotoContent(model: Any, transform: Similarity?, annots: List<AnnotLine>?, layers: Set<AnnotLayer>,
                 description: String?, modifier: Modifier = Modifier,
                 /** Números de las líneas medidas (los mismos en todas las fotos y en la tabla). */
                 numbers: Map<String, Int>? = annots?.let { com.photomed.app.align.MeasureNumbering.of(listOf(it)) },
                 alpha: Float = 0.5f,
                 showImage: Boolean = true, ink: List<com.photomed.app.align.InkStroke> = emptyList(), selected: String? = null,
                 selectedKey: String? = null, onSelect: ((AnnotLine?, String?) -> Unit)? = null) {
    val selectedNow = androidx.compose.runtime.rememberUpdatedState(selected)
    val annotsNow = androidx.compose.runtime.rememberUpdatedState(annots)
    val layersNow = androidx.compose.runtime.rememberUpdatedState(layers)
    Box(modifier.alignedBy(transform).then(if (onSelect != null && layers.isNotEmpty()) Modifier.pointerInput(Unit) {
        // tocar una línea (también con zoom: las coordenadas ya vienen en la foto) la selecciona
        // sin consumir el evento: el deslizador, el zoom y el arrastre siguen funcionando
        detectTapPassThrough { o ->
            val a = annotsNow.value ?: return@detectTapPassThrough
            val p = Pt(o.x / size.width, o.y / size.width)
            val hit = hitPick(a, layersNow.value, p, 24.dp.toPx() / size.width, selectedNow.value)
            onSelect(hit?.first, hit?.second)
        }
    } else Modifier)) {
        // capa 1: imagen
        AsyncImage(model, description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = if (showImage) 1f else 0f })
        Canvas(Modifier.fillMaxSize()) {
            val k = (size.width / 360.dp.toPx()).coerceIn(0.35f, 1.4f)
            fun map(p: Pt) = Offset(p.x * size.width, p.y * size.width)
            // capa 2: diagrama facial (con la línea seleccionada resaltada y sus tramos)
            if (annots != null && layers.isNotEmpty()) {
                val sl = selected?.let { n -> annots.firstOrNull { it.metrics?.name == n && it.layer in layers } }
                sl?.let { l ->
                    val path = androidx.compose.ui.graphics.Path()
                    l.points.forEachIndexed { i, q -> val o = map(q); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                    if (l.closed) path.close()
                    drawPath(path, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 6.dp.toPx() * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                drawAnnotations(annots, layers, k, numbers = numbers, alpha = alpha) { p: Pt -> map(p) }
                sl?.let { drawMeasureGuides(it, k, { p -> map(p) }, selectedKey) }
            }
            // capa 3: anotaciones
            drawInk(ink, size.width) { p -> map(p) }
        }
    }
}

/**
 * Giro en pasos de 90° y espejo horizontal (en pantalla) del contenido 3:4. Con giro impar, el
 * contenedor debe tener proporción 4:3 (el contenido conserva su 3:4 girado); con [fit], el
 * contenedor sigue siendo 3:4 y el contenido girado se reduce para caber entero.
 */
@Composable
fun OrientBox(rot: Int, mirror: Boolean, modifier: Modifier = Modifier, fit: Boolean = false,
              /**
               * Con giro impar en un contenedor 3:4: el contenido 3:4 girado lo cubre entero (como la
               * imagen girada y recortada al centro en 3:4, p. ej. la referencia de la cámara).
               */
              fill34: Boolean = false, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().graphicsLayer { scaleX = if (mirror) -1f else 1f }) {
        val odd = rot % 2 == 1
        // fit: el contenido girado (alto h, ancho visible h) cabe entero: h ≤ ancho y ¾h ≤ alto
        val hFit = minOf(maxWidth, maxHeight / 0.75f)
        Box(
            Modifier.align(Alignment.Center)
                .then(when {
                    !odd -> Modifier.fillMaxSize()
                    fill34 -> Modifier.requiredSize(maxHeight, maxHeight * 4f / 3f)
                    fit -> Modifier.requiredSize(hFit * 0.75f, hFit)
                    else -> Modifier.requiredSize(maxHeight, maxWidth)
                })
                .graphicsLayer { rotationZ = 90f * (rot % 4) },
        ) { content() }
    }
}

/** Vista de la comparación: giro y espejo de todas las fotos, capas visibles y anotaciones por modelo. */
data class CompareView(
    val rot: Int = 0,
    val mirror: Boolean = false,
    val layers: Set<AnnotLayer> = emptySet(),
    val annots: Map<Any, List<AnnotLine>> = emptyMap(),
    /** Opacidad de los trazos y números. */
    val alpha: Float = 0.5f,
    /** Capas de la vista: imagen, anotaciones y overlay (el diagrama son [layers]). */
    val view: ViewLayers = ViewLayers(),
    /** Anotaciones de cada foto (por modelo), en su encuadre 3:4. */
    val ink: Map<Any, List<com.photomed.app.align.InkStroke>> = emptyMap(),
    /** Línea seleccionada en la tabla (por nombre): resaltada y con sus tramos en todas las fotos. */
    val selected: String? = null,
    /** Tramo seleccionado de esa línea (o null). */
    val selectedKey: String? = null,
    /** Tocar una línea en una foto la selecciona (null: toque fuera de las líneas). */
    val onSelect: ((AnnotLine?, String?) -> Unit)? = null,
) {
    /** Proporción (ancho/alto) de cada celda: 3:4, o 4:3 con giro de 90° o 270°. */
    val aspect: Float get() = if (rot % 2 == 1) 4f / 3f else 3f / 4f
}

val LocalCompareView = androidx.compose.runtime.staticCompositionLocalOf { CompareView() }

/** Tamaño de la celda de foto en Comparar (para centrar la línea seleccionada con el zoom). */
val LocalCellSize = androidx.compose.runtime.staticCompositionLocalOf<MutableState<androidx.compose.ui.unit.IntSize>?> { null }

/** Una foto de la comparación con la vista actual (giro, espejo, capas) y su alineación. */
@Composable
fun ComparePhoto(model: Any, transform: Similarity?, description: String?, layer: Modifier, modifier: Modifier = Modifier.fillMaxSize()) {
    val v = LocalCompareView.current
    val cell = LocalCellSize.current
    OrientBox(v.rot, v.mirror, modifier.then(if (cell != null) Modifier.onSizeChanged { cell.value = it } else Modifier)) {
        PhotoContent(model, transform, v.annots[model], v.layers, description, layer,
            numbers = com.photomed.app.align.MeasureNumbering.of(v.annots.values.toList()), alpha = v.alpha,
            showImage = v.view.image, ink = if (v.view.notes) v.ink[model].orEmpty() else emptyList(), selected = v.selected, selectedKey = v.selectedKey,
            onSelect = v.onSelect)
    }
}
