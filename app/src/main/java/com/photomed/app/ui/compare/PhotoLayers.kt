package com.photomed.app.ui.compare

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

private const val PREF_LAYERS = "annot_layers"

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

/** Fila de interruptores de las capas (contorno, línea del pelo, ojos, nariz, labios, surcos). */
@Composable
fun LayerChips(layers: Set<AnnotLayer>, onChange: (Set<AnnotLayer>) -> Unit, modifier: Modifier = Modifier,
               /** Si se da, primer chip «Medidas» (tabla de longitud, ancho, profundidad y grado). */
               onMetrics: (() -> Unit)? = null) {
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (onMetrics != null) androidx.compose.material3.AssistChip(onClick = onMetrics, label = { Text("Medidas") },
            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.TableChart, null, Modifier.size(18.dp)) })
        // contornos internos (ojos y cejas, nariz, labios) de una vez: se ven solo el contorno y las arrugas
        val inner = setOf(AnnotLayer.EYES, AnnotLayer.NOSE, AnnotLayer.LIPS)
        val innerOn = inner.all { it in layers }
        FilterChip(selected = innerOn, onClick = { onChange(if (innerOn) layers - inner else layers + inner) },
            label = { Text("Contornos internos") }, colors = FilterChipDefaults.filterChipColors())
        AnnotLayer.entries.forEach { l ->
            val on = l in layers
            FilterChip(
                selected = on, onClick = { onChange(if (on) layers - l else layers + l) },
                label = { Text(l.label) },
                leadingIcon = { Canvas(Modifier.size(10.dp)) { drawCircle(annotColor(l)) } },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/**
 * Anotaciones de una foto en su encuadre 3:4 (con su edición guardada), llevadas a la foto corregida
 * en perspectiva [phiDeg] (como se muestra en Comparar). null mientras se calculan.
 */
/** Motivo (si lo hay) por el que la foto no tiene medidas, tras analizarla. */
fun photoAnnotationNote(context: Context, uri: Uri): String? {
    val edit = PhotoEdits.of(context, uri.toString())
    return PhotoAnnotator.note("34|$uri|${edit.hashCode()}|${com.photomed.app.align.ReliefStore.tag(context, uri.toString())}")
}

@Composable
fun rememberPhotoAnnotations(uri: Uri, zone: Zone, phiDeg: Float, enabled: Boolean): State<List<AnnotLine>?> {
    val context = LocalContext.current
    val edit = PhotoEdits.of(context, uri.toString())
    return produceState<List<AnnotLine>?>(null, uri, edit, phiDeg, enabled) {
        if (!enabled) { value = null; return@produceState }
        val key = "34|$uri|${edit.hashCode()}|${com.photomed.app.align.ReliefStore.tag(context, uri.toString())}"
        val base = PhotoAnnotator.cached(key) ?: runCatching {
            PhotoAnnotator(context.applicationContext).analyze(ImageOps.load34(context, uri, 1080), zone,
                PhotoAnnotator.extrasFor(context, uri, 1080, crop34 = true))
        }.getOrNull()?.let { (lines, note) -> PhotoAnnotator.put(key, lines); PhotoAnnotator.putNote(key, note); lines }
            ?: emptyList<AnnotLine>().also { PhotoAnnotator.putNote(key, "No se pudo analizar la foto.") }
        value = base?.map { l -> if (phiDeg == 0f) l else l.copy(points = l.points.map { Perspective.mapPt(it, phiDeg) }) }
    }
}

/** Foto 3:4 (modelo de Coil) con la semejanza de alineación y sus anotaciones en la misma capa. */
@Composable
fun PhotoContent(model: Any, transform: Similarity?, annots: List<AnnotLine>?, layers: Set<AnnotLayer>,
                 description: String?, modifier: Modifier = Modifier,
                 /** Números de las líneas medidas (los mismos en todas las fotos y en la tabla). */
                 numbers: Map<String, Int>? = annots?.let { com.photomed.app.align.MeasureNumbering.of(listOf(it)) }) {
    Box(modifier.alignedBy(transform)) {
        AsyncImage(model, description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (annots != null && layers.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            val k = (size.width / 360.dp.toPx()).coerceIn(0.35f, 1.4f)
            drawAnnotations(annots, layers, k, numbers = numbers) { p: Pt -> Offset(p.x * size.width, p.y * size.width) }
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
) {
    /** Proporción (ancho/alto) de cada celda: 3:4, o 4:3 con giro de 90° o 270°. */
    val aspect: Float get() = if (rot % 2 == 1) 4f / 3f else 3f / 4f
}

val LocalCompareView = androidx.compose.runtime.staticCompositionLocalOf { CompareView() }

/** Una foto de la comparación con la vista actual (giro, espejo, capas) y su alineación. */
@Composable
fun ComparePhoto(model: Any, transform: Similarity?, description: String?, layer: Modifier, modifier: Modifier = Modifier.fillMaxSize()) {
    val v = LocalCompareView.current
    OrientBox(v.rot, v.mirror, modifier) {
        PhotoContent(model, transform, v.annots[model], v.layers, description, layer,
            numbers = com.photomed.app.align.MeasureNumbering.of(v.annots.values.toList()))
    }
}
