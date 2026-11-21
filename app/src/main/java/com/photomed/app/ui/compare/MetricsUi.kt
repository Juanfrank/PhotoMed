@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.photomed.app.ui.compare

import com.photomed.app.i18n.trn
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.selected
import com.photomed.app.ui.camera.drawGuideSegment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.LineMeasure
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.Pt
import com.photomed.app.ui.camera.annotColor
import com.photomed.app.ui.components.SafeBottomSheet
import java.util.Locale
import kotlin.math.hypot

private fun f1(v: Float?) = v?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"
private fun f2(v: Float?) = v?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "—"
private fun signed(v: Float?, digits: Int) = v?.let { String.format(Locale.getDefault(), "%+.${digits}f", it) } ?: "—"

/**
 * Filas (etiqueta, valor) de las medidas de una línea o zona. La etiqueta queda en español (es la clave
 * que enlaza con [com.photomed.app.align.MeasureGuide.key]); se traduce al mostrarla con `tr(etiqueta)`.
 */
fun metricRows(m: LineMetrics): List<Pair<String, String>> =
    if (m.isFeature) m.features.map { (k, v) -> k.substringBeforeLast(",") to featureValue(k, v) } + (trn("Escala") to scaleLabel(m.scale))
    else if (m.areaMm2 != null || m.deltaE != null) listOfNotNull(
        trn("Área") to "${f1(m.areaMm2?.div(100f))} cm²",
        trn("ΔL* frente a la mejilla") to tr("%1\$s (más oscura)", f1(m.deltaL)),
        m.deltaE?.let { trn("ΔE*ab frente a la mejilla") to f1(it) },
        m.ita?.let { trn("ITA° ojera / mejilla") to "${f1(it)}° / ${f1(m.itaRef)}°" },
        m.type?.let { trn("Tipo probable") to it },
    ) else listOf(
        trn("Longitud") to "${f1(m.lengthMm)} mm",
        trn("Ancho (a media profundidad)") to "${f1(m.widthMm)} mm",
        (if (m.depthSource == "relieve") trn("Profundidad (relieve medido)") else trn("Profundidad estimada (sombreado)")) to "${f2(m.depthMm)} mm",
        trn("Contraste ΔL*") to f1(m.contrastL),
        trn("Grado orientativo (0–5)") to (m.grade?.let { "$it · ${gradeLabel(it)}" } ?: "—"),
        trn("Escala") to scaleLabel(m.scale),
    ) + listOfNotNull(m.note?.let { trn("Nota") to trData(it) })

/** Valor de una medida de rasgo con su unidad (la de la etiqueta tras la coma). */
fun featureValue(label: String, v: Float): String {
    if (label == com.photomed.app.align.FeatureMetrics.FACIAL_INDEX)
        return "${f1(v)} · ${com.photomed.app.align.FeatureMetrics.facialType(v).substringBefore(" (")}"
    val unit = label.substringAfterLast(",", "").trim()
    return when (unit) {
        "mm²" -> String.format(Locale.getDefault(), "%.0f mm²", v)
        "°" -> String.format(Locale.getDefault(), "%.1f°", v)
        "" -> f2(v)
        else -> "${f1(v)} $unit"
    }
}

fun scaleLabel(s: String) = when (s) {
    "sensor de profundidad" -> tr("sensor de profundidad")
    "interpupilar" -> tr("interpupilar (63 mm)")
    else -> tr("iris (11,7 mm)")
}

fun gradeLabel(g: Int) = tr(listOf(trn("sin arruga"), trn("apenas perceptible"), trn("superficial"), trn("moderada"), trn("profunda"), trn("muy profunda"))[g.coerceIn(0, 5)])

val METRICS_NOTE: String get() = tr("Medidas orientativas desde una sola foto: escala por el diámetro del iris (11,7 mm), " +
    "ancho y profundidad por el perfil de sombra (forma a partir del sombreado) y grado al estilo de Lemperle. " +
    "Dependen de la luz y la pose: compare fotos tomadas igual. No sustituyen a la perfilometría ni a la valoración clínica.")

/**
 * Línea con medidas más cercana a [p] (unidades del ancho) entre las capas visibles, a menos de [tol];
 * en las zonas rellenas (ojeras), si [p] cae dentro.
 */
fun hitLine(lines: List<AnnotLine>, layers: Set<AnnotLayer>, p: Pt, tol: Float): AnnotLine? {
    var best: AnnotLine? = null; var bd = tol
    for (l in lines) {
        if (l.layer !in layers) continue
        // trazo sin medidas propio (p. ej. el dorso o las alas de la nariz): cuenta como la línea medida de su capa
        if (l.metrics == null) {
            val owner = lines.filter { it.layer == l.layer && it.metrics != null }.singleOrNull() ?: continue
            for (i in 1 until l.points.size) { val d = segDist(p, l.points[i - 1], l.points[i]); if (d < bd) { bd = d; best = owner } }
            continue
        }
        if (l.filled && insidePoly(p, l.points)) { if (best == null || bd > tol * 0.5f) { best = l; bd = tol * 0.5f }; continue }
        for (i in 1 until l.points.size) {
            val d = segDist(p, l.points[i - 1], l.points[i])
            if (d < bd) { bd = d; best = l }
        }
    }
    return best
}

/**
 * Toque en la foto: si ya hay una línea seleccionada ([selectedName]) y se toca uno de sus tramos
 * medidos (las guías punteadas), ese tramo; si no, la línea más cercana entera. null: nada cerca.
 */
fun hitPick(lines: List<AnnotLine>, layers: Set<AnnotLayer>, p: Pt, tol: Float, selectedName: String?): Pair<AnnotLine, String?>? {
    lines.firstOrNull { selectedName != null && it.metrics?.name == selectedName }?.let { sel ->
        sel.guides.filter { it.key.isNotEmpty() }.minByOrNull { segDist(p, it.a, it.b) }
            ?.takeIf { segDist(p, it.a, it.b) < tol * 0.8f }?.let { return sel to it.key }
    }
    return hitLine(lines, layers, p, tol)?.let { it to null }
}

private fun segDist(p: Pt, a: Pt, b: Pt): Float {
    val vx = b.x - a.x; val vy = b.y - a.y; val l2 = vx * vx + vy * vy
    val t = if (l2 < 1e-12f) 0f else (((p.x - a.x) * vx + (p.y - a.y) * vy) / l2).coerceIn(0f, 1f)
    return hypot(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
}

private fun insidePoly(q: Pt, poly: List<Pt>): Boolean {
    var c = false; var j = poly.size - 1
    for (i in poly.indices) {
        if ((poly[i].y > q.y) != (poly[j].y > q.y) && q.x < (poly[j].x - poly[i].x) * (q.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x) c = !c
        j = i
    }
    return c
}

/** Muestra del estilo de trazo [style] (el mismo que el tramo en la foto), sobre fondo oscuro. */
@Composable
fun GuideSwatch(style: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(androidx.compose.ui.graphics.Color(0xFF303030), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        val pad = 7.dp.toPx()
        drawGuideSegment(androidx.compose.ui.geometry.Offset(pad, size.height / 2), androidx.compose.ui.geometry.Offset(size.width - pad, size.height / 2), style)
    }
}

/** Ficha de la línea tocada, con acceso a la tabla completa. */
@Composable
fun MetricsCard(line: AnnotLine, onTable: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, number: Int? = null) {
    val m = line.metrics ?: return
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f))) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(com.photomed.app.ui.camera.lineColor(line)) }
                Text((number?.let { "$it · " } ?: "") + trData(m.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, tr("Cerrar")) }
            }
            // la muestra del trazo de cada tramo va delante de su medida (sin leyenda aparte)
            val styles = com.photomed.app.ui.camera.guideStyleIndices(line)
            val styleOf = line.guides.indices.filter { styles[it] >= 0 && line.guides[it].key.isNotEmpty() }.associate { line.guides[it].key to styles[it] }
            val hasSw = styles.any { it >= 0 }
            @Composable fun row(label: String, value: String, style: Int?) {
                Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasSw) {
                        if (style != null) GuideSwatch(style, Modifier.size(30.dp, 12.dp).semantics { contentDescription = tr("Trazo %1\$d", style + 1) })
                        else Spacer(Modifier.width(30.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(trData(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            metricRows(m).forEach { (k, v) -> row(k, v, styleOf[k]) }
            // tramos sin fila propia (p. ej. alto y ancho de la ojera): su texto, con su trazo
            line.guides.forEachIndexed { gi, g ->
                if (styles[gi] < 0 || g.key.isNotEmpty() && metricRows(m).any { it.first == g.key }) return@forEachIndexed
                val label = g.label.substringBeforeLast(" ").substringBeforeLast(" ").replaceFirstChar { it.uppercase() }
                row(label, g.label.removePrefix(g.label.substringBeforeLast(" ").substringBeforeLast(" ")).trim(), styles[gi])
            }
            TextButton(onClick = onTable) {
                Icon(Icons.Default.TableChart, null, Modifier.size(18.dp)); Text(tr("Tabla de medidas"), Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Columnas numéricas de la tabla de líneas. */
private val LINE_COLS: List<String> get() = listOf(tr("Long.") + "\nmm", tr("Ancho") + "\nmm", tr("Prof.") + "\nmm", "ΔL*", tr("Grado"))
private fun lineVals(m: LineMetrics?): List<Float?> = listOf(m?.lengthMm, m?.widthMm, m?.depthMm, m?.contrastL, m?.grade?.toFloat())
private val AREA_COLS: List<String> get() = listOf(tr("Área") + "\ncm²", "ΔL*", "ΔE*ab", "ITA°", "")
private fun areaVals(m: LineMetrics?): List<Float?> = listOf(m?.areaMm2?.div(100f), m?.deltaL, m?.deltaE, m?.ita, null)
private fun fmt(i: Int, v: Float?, area: Boolean) = when {
    v == null -> "—"
    !area && i == 4 -> v.toInt().toString()
    !area && i == 2 -> f2(v)
    else -> f1(v)
}

/**
 * Tabla de medidas de una o varias fotos. Con una foto: una fila por línea. Con varias ([columns],
 * en el orden de la comparación): por cada línea y medida, el valor de cada foto y la diferencia
 * entre la última y la primera. Hoja inferior (se superpone); en el visor y en comparar se usa el
 * panel fijo [MetricsPanel], debajo de la foto.
 */
@Composable
fun MetricsSheet(columns: List<Pair<String, List<AnnotLine>>>, layers: Set<AnnotLayer>, onDismiss: () -> Unit,
                 loading: Boolean = false, note: String? = null, onShowLayers: ((Set<AnnotLayer>) -> Unit)? = null,
                 onDelete: ((String, Int?) -> Unit)? = null, dismissedCount: Int = 0, onRestore: (() -> Unit)? = null) {
    SafeBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
            Text(tr("Medidas de líneas y surcos"), style = MaterialTheme.typography.titleLarge)
            MetricsContent(columns, layers, loading, note, onShowLayers, onDelete, dismissedCount, onRestore)
        }
    }
}

/**
 * Acciones sobre los trazos de una foto (tabla con una sola foto): volver a trazar una línea
 * ([onEdit]) y revertirlo ([onRevert], solo en [edited]); confirmar los trazos (se guardan en la foto)
 * o regenerarlos (volver a analizar).
 */
class TraceActions(val confirmed: Boolean, val onConfirm: () -> Unit, val onRegenerate: () -> Unit,
                   val onEdit: ((String) -> Unit)? = null, val onRevert: ((String) -> Unit)? = null,
                   val edited: Set<String> = emptySet(), val savedInPhoto: Boolean = true)

/** Panel fijo de medidas (debajo de la foto, sin taparla), con su título y botón de cierre. */
@Composable
fun MetricsPanel(columns: List<Pair<String, List<AnnotLine>>>, layers: Set<AnnotLayer>, onClose: () -> Unit, modifier: Modifier = Modifier,
                 loading: Boolean = false, note: String? = null, onShowLayers: ((Set<AnnotLayer>) -> Unit)? = null,
                 /** Eliminar una línea (falso positivo): nombre y foto (índice de columna; null = en todas). */
                 onDelete: ((String, Int?) -> Unit)? = null, dismissedCount: Int = 0, onRestore: (() -> Unit)? = null,
                 traces: TraceActions? = null,
                 /** Tocar una fila selecciona su línea en la foto (y muestra lo que se mide). */
                 onSelectLine: ((AnnotLine, String?) -> Unit)? = null,
                 /** Línea seleccionada en la foto: su fila se resalta y se desplaza hasta verse. */
                 selected: AnnotLine? = null,
                 /** Tramo seleccionado de esa línea (fila de la ficha), o null para la línea entera. */
                 selectedKey: String? = null) {
    androidx.compose.material3.Surface(modifier, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth()) {
            // cabecera baja: el panel tiene poco alto (20 % de la pantalla) y es casi todo filas
            Row(Modifier.fillMaxWidth().height(32.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Medidas"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, tr("Cerrar las medidas"), Modifier.size(20.dp)) }
            }
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 12.dp).verticalScroll(rememberScrollState())) {
                MetricsContent(columns, layers, loading, note, onShowLayers, onDelete, dismissedCount, onRestore, traces, onSelectLine, selected, selectedKey)
            }
        }
    }
}

/** Contenido de la tabla de medidas (ver [MetricsSheet]). */
@Composable
fun MetricsContent(columns: List<Pair<String, List<AnnotLine>>>, layers: Set<AnnotLayer>,
                   loading: Boolean = false, note: String? = null, onShowLayers: ((Set<AnnotLayer>) -> Unit)? = null,
                   onDelete: ((String, Int?) -> Unit)? = null, dismissedCount: Int = 0, onRestore: (() -> Unit)? = null,
                   traces: TraceActions? = null, onSelectLine: ((AnnotLine, String?) -> Unit)? = null, selected: AnnotLine? = null,
                   selectedKey: String? = null) = androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    // reparto del ancho disponible: la primera columna hasta el 40 %, las cifras se reparten el resto
    val avail = maxWidth
    fun widthsFor(cells: Int, actions: Dp = 0.dp): Pair<Dp, Dp> {
        val first = (avail * 0.40f).coerceIn(120.dp, 230.dp)
        val v = if (cells <= 1) 72.dp else ((avail - first - actions - 4.dp) / (cells - 1)).coerceIn(58.dp, 110.dp)
        return first to v
    }
    Column { MetricsBody(columns, layers, loading, note, onShowLayers, onDelete, dismissedCount, onRestore, traces, onSelectLine, selected, selectedKey, ::widthsFor) }
}

@Composable
private fun MetricsBody(columns: List<Pair<String, List<AnnotLine>>>, layers: Set<AnnotLayer>,
                        loading: Boolean, note: String?, onShowLayers: ((Set<AnnotLayer>) -> Unit)?,
                        onDelete: ((String, Int?) -> Unit)?, dismissedCount: Int, onRestore: (() -> Unit)?,
                        traces: TraceActions?, onSelectLine: ((AnnotLine, String?) -> Unit)?, selected: AnnotLine?, selectedKey: String?,
                        widthsFor: (Int, Dp) -> Pair<Dp, Dp>) {
    val selName = selected?.metrics?.name
    // selección hecha en la foto (punto o tramo): la tabla muestra solo eso, con «Ver todas» para volver;
    // la hecha desde la propia tabla no la filtra (se seguiría eligiendo entre las filas)
    var fromTable by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<String?, String?>?>(null) }
    var showAll by androidx.compose.runtime.remember(selName, selectedKey) { androidx.compose.runtime.mutableStateOf(false) }
    val filtering = selName != null && fromTable != (selName to selectedKey) && !showAll
    val onSelectLine: ((AnnotLine, String?) -> Unit)? = onSelectLine?.let { f -> { l: AnnotLine, k: String? -> fromTable = l.metrics?.name to k; f(l, k) } }
    // la fila de la línea seleccionada se trae a la vista (en vertical y en horizontal)
    val bring = androidx.compose.runtime.remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    androidx.compose.runtime.LaunchedEffect(selName) { if (selName != null) { kotlinx.coroutines.delay(80); runCatching { bring.bringIntoView() } } }
    // estilos de trazo de la línea seleccionada, por fila de la ficha
    val selStyles = selected?.let { l -> val st = com.photomed.app.ui.camera.guideStyleIndices(l)
        l.guides.indices.filter { st[it] >= 0 && l.guides[it].key.isNotEmpty() }.associate { l.guides[it].key to st[it] } }.orEmpty()
    // estado de los trazos: confirmados (guardados en la foto, no se vuelven a analizar) o por confirmar
    if (traces != null && !loading) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (traces.confirmed) Icons.Default.Verified else Icons.Default.Info, null, Modifier.size(18.dp),
            tint = if (traces.confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (traces.confirmed) (if (traces.savedInPhoto) tr("Trazos confirmados: guardados en la foto") else tr("Trazos confirmados (guardados en la app)"))
            else tr("Trazos sin confirmar (análisis automático)"),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 6.dp))
        if (traces.confirmed) TextButton(onClick = traces.onRegenerate) { Text(tr("Regenerar")) }
        else androidx.compose.material3.FilledTonalButton(onClick = traces.onConfirm) { Text(tr("Confirmar")) }
    }
    // todas las líneas medidas, estén o no visibles sus capas en la foto
    val byName = LinkedHashMap<String, Pair<AnnotLine, MutableList<LineMetrics?>>>()
    columns.forEachIndexed { ci, (_, lines) ->
        lines.filter { it.metrics != null }.forEach { l ->
            val e = byName.getOrPut(l.metrics!!.name) { l to MutableList(columns.size) { null } }
            e.second[ci] = l.metrics
        }
    }
    val numbers = com.photomed.app.align.MeasureNumbering.of(columns.map { it.second })
    if (loading) Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.LinearProgressIndicator(Modifier.weight(1f))
        Text(tr("Analizando…"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
    if (byName.isEmpty() && !loading) Text(note ?: tr("No se encontraron arrugas ni surcos medibles."),
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
    // medidas parciales (sin malla facial): el motivo, también con medidas
    if (byName.isNotEmpty() && note != null) Text(note, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    val hidden = byName.values.map { it.first.layer }.toSet() - layers
    if (hidden.isNotEmpty() && onShowLayers != null) {
        TextButton(onClick = { onShowLayers(layers + hidden) }) {
            Text(tr("Mostrar en la foto: %1\$s", hidden.sortedBy { it.ordinal }.joinToString(", ") { it.label }))
        }
    }
    val ordered = byName.entries.sortedBy { numbers[it.key] ?: Int.MAX_VALUE }.filter { !filtering || it.key == selName }
    if (filtering && byName.containsKey(selName)) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.FilterAlt, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(tr("Solo la selección: %1\$s", numbers[selName]?.let { "$it  " }.orEmpty() + selName) + (selectedKey?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 6.dp))
        TextButton(onClick = { showAll = true }) { Text(tr("Ver todas (%1\$d)", byName.size)) }
    }
    val single = columns.size == 1
    // un desplazamiento horizontal por tabla (compartido, la tabla más estrecha limitaba a las demás)
    val scrolls = List(3) { rememberScrollState() }
    fun label(name: String, v: Pair<AnnotLine, List<LineMetrics?>>) = "${numbers[name] ?: ""}  ${trData(name)}" + if (v.second.any { it?.note != null }) " ✱" else ""
    for (group in 0..2) {
        val rows = ordered.filter { e -> e.value.second.firstNotNullOfOrNull { it }?.let { com.photomed.app.align.MeasureNumbering.group(it) } == group }
        if (rows.isEmpty()) continue
        val area = group == 1
        Text(listOf(tr("Arrugas y surcos"), tr("Ojeras"), tr("Rasgos"))[group], style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        Column(Modifier.horizontalScroll(scrolls[group])) {
            if (group == 2) {
                // rasgos: una fila por medida
                val featCells = listOf(tr("Rasgo · medida")) + (if (single) listOf(tr("Valor")) else columns.map { it.first.replace(", ", "\n") } + "Δ")
                val (fw, vw) = widthsFor(featCells.size, 0.dp)
                TableRow(featCells, header = true, firstW = fw, valueW = vw)
                rows.forEach { (name, v) ->
                    val labels = v.second.firstNotNullOfOrNull { it }?.features?.map { it.first }.orEmpty()
                        // tramo elegido en la foto: solo su fila
                        .filter { lab -> !filtering || selectedKey == null || lab.substringBeforeLast(",") == selectedKey }
                    labels.forEachIndexed { i, lab ->
                        val vals = v.second.map { m -> m?.features?.firstOrNull { it.first == lab }?.second }
                        val first = vals.firstOrNull { it != null }; val last = vals.lastOrNull { it != null }
                        val delta = if (!single && first != null && last != null && vals.count { it != null } >= 2) last - first else null
                        val key = lab.substringBeforeLast(",")
                        val isSel = name == selName && (selectedKey == null || selectedKey == key)
                        // cada fila es un tramo: al tocarla se selecciona solo ese tramo
                        TableRow(listOf(if (i == 0) "${label(name, v)}\n${lab}" else "   $lab") + vals.map { it?.let { x -> featureValue(lab, x) } ?: "—" } +
                            (if (single) emptyList() else listOf(signed(delta, if (lab.endsWith("mm²")) 0 else 1))), line = if (i == 0) v.first else null,
                            onClick = onSelectLine?.let { f -> { f(v.first, key.takeIf { k -> v.first.guides.any { it.key == k } }) } }, highlight = isSel,
                            swatch = if (name == selName) selStyles[key] else null,
                            bring = if (isSel && (i == 0 || selectedKey == key)) bring else null, firstW = fw, valueW = vw)
                    }
                    HorizontalDivider()
                }
            }
        }
        if (group != 2) {
            // arrugas, surcos y ojeras: las cifras se desplazan en horizontal y la columna de eliminar queda
            // fija a la derecha (antes iba al final de la fila y quedaba fuera de la pantalla)
            val cols = if (area) AREA_COLS.take(4) else LINE_COLS
            val specs = ArrayList<RowSpec>()
            if (single) {
                specs += RowSpec(listOf(if (area) tr("Zona") else tr("Línea")) + cols, header = true)
                rows.forEach { (name, v) ->
                    val m = v.second[0]
                    specs += RowSpec(listOf(label(name, v)) + (if (area) areaVals(m) else lineVals(m)).take(cols.size).mapIndexed { i, x -> fmt(i, x, area) },
                        line = v.first, deleteName = name)
                }
            } else {
                val head = columns.map { it.first.replace(", ", "\n") }
                specs += RowSpec(listOf(if (area) tr("Zona · medida") else tr("Línea · medida")) + head + "Δ", header = true)
                rows.forEach { (name, v) ->
                    cols.forEachIndexed { i, c ->
                        val vals = v.second.map { m -> (if (area) areaVals(m) else lineVals(m))[i] }
                        val first = vals.firstOrNull { it != null }; val last = vals.lastOrNull { it != null }
                        val delta = if (first != null && last != null && vals.count { it != null } >= 2) last - first else null
                        specs += RowSpec(listOf(if (i == 0) "${label(name, v)}\n${c.replace("\n", " ")}" else "   ${c.replace("\n", " ")}") +
                            vals.map { fmt(i, it, area) } + signed(delta, if (!area && i == 2) 2 else 1), line = if (i == 0) v.first else null,
                            deleteName = if (i == 0) name else null,
                            deletePhotos = if (i == 0) columns.mapIndexedNotNull { ci, col -> if (v.second[ci] != null) ci to col.first else null } else emptyList(),
                            divider = i == cols.size - 1, measure = if (area) null else i)
                    }
                }
            }
            // acciones fijas a la derecha: volver a trazar, revertir y eliminar
            val edit = traces?.onEdit?.takeIf { single && !area }
            val revert = traces?.onRevert?.takeIf { single && !area && traces.edited.isNotEmpty() }
            val nAct = listOfNotNull(edit, revert, onDelete).size
            val (fw, vw) = widthsFor(specs.firstOrNull()?.cells?.size ?: 2, 40.dp * nAct)
            // alto de fila según el tamaño de letra del sistema (accesibilidad): sin recortar el texto
            // alto de cada fila justo para su texto (sin interlineado): una línea, o dos (línea y medida)
            val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 2f)
            fun rowHOf(r: RowSpec) = (ROW_LINE * (if (r.header) 2 else r.cells.maxOf { it.count { ch -> ch == '\n' } + 1 }) + 2.dp) * fs
            val btn = (ROW_LINE + 2.dp) * fs
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).horizontalScroll(scrolls[group])) {
                    var owner: AnnotLine? = null
                    specs.forEach { r -> if (r.line != null) owner = r.line; if (r.header) owner = null
                        val o = owner
                        // tramo de la fila (en la tabla de varias fotos, una fila por medida)
                        val rowKey = r.measure?.let { if (it == 1) WIDTH_KEY else null }
                        val isSel = !r.header && o?.metrics?.name != null && o.metrics.name == selName &&
                            (selectedKey == null || r.measure == null || rowKey == selectedKey)
                        TableRow(r.cells, header = r.header, line = r.line, fixedHeight = rowHOf(r),
                            onClick = if (r.header || o == null) null else onSelectLine?.let { f -> { f(o, rowKey?.takeIf { o.guides.any { g -> g.key == it } }) } },
                            highlight = isSel, bring = if (isSel && (r.line != null || rowKey == selectedKey)) bring else null, firstW = fw, valueW = vw,
                            // una foto: la celda del ancho selecciona solo el tramo del ancho
                            onCellClick = if (single && !area && !r.header && o != null && onSelectLine != null && o.guides.any { it.key == WIDTH_KEY })
                                { c -> onSelectLine(o, if (c == 2) WIDTH_KEY else null) } else null,
                            swatchCell = if (single && !area && o?.metrics?.name == selName && o != null) selStyles[WIDTH_KEY]?.let { 2 to it } else
                                if (!single && rowKey != null && o?.metrics?.name == selName) selStyles[rowKey]?.let { 0 to it } else null)
                        if (r.divider) HorizontalDivider() }
                }
                if (nAct > 0) Column(Modifier.width(40.dp * nAct)) {
                    specs.forEach { r ->
                        Row(Modifier.height(rowHOf(r)).width(40.dp * nAct), verticalAlignment = Alignment.CenterVertically) {
                            val n = r.deleteName
                            if (edit != null) androidx.compose.foundation.layout.Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                if (n != null) IconButton(onClick = { edit(n) }, modifier = Modifier.size(btn)) {
                                    Icon(Icons.Default.Edit, tr("Volver a trazar %1\$s", n), Modifier.size(minOf(20.dp, btn - 4.dp)), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (revert != null) androidx.compose.foundation.layout.Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                if (n != null && n in traces.edited) IconButton(onClick = { revert(n) }, modifier = Modifier.size(btn)) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, tr("Revertir el trazado de %1\$s", n), Modifier.size(minOf(20.dp, btn - 4.dp)), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (onDelete != null) androidx.compose.foundation.layout.Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                n?.let { DeleteLineButton(it, if (single) emptyList() else r.deletePhotos, btn) { c -> onDelete(it, if (single) (c ?: 0) else c) } }
                            }
                        }
                        if (r.divider) HorizontalDivider()
                    }
                }
            }
        }
    }
    if (dismissedCount > 0 && onRestore != null) TextButton(onClick = onRestore) {
        Text(tr("Restaurar líneas eliminadas (%1\$d)", dismissedCount))
    }
    if (ordered.any { it.value.second.any { m -> m?.type != null } }) {
        Text(tr("Tipo probable de ojera: %1\$s", ordered.mapNotNull { e -> e.value.second.lastOrNull { it?.type != null }?.let { "${e.key}: ${it.type}" } }.joinToString("; ")),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
    if (ordered.any { it.value.second.any { m -> m?.note != null } }) Text(tr("✱ Parcialmente cubierto por barba o bigote: se mide solo la parte visible."),
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    val all = ordered.flatMap { it.value.second }.filterNotNull()
    val scale = all.firstOrNull()?.scale
    val relief = all.any { it.depthSource == "relieve" }
    Text(METRICS_NOTE + (if (scale == "interpupilar") " " + tr("(Escala: distancia interpupilar de 63 mm; iris no fiable.)") else "") +
        (if (scale == "sensor de profundidad") " " + tr("Escala medida con el sensor de profundidad del teléfono.") else "") +
        (if (relief) " " + tr("Profundidad medida en el relieve (estéreo fotométrico con la pantalla).") else "") +
        " " + tr("Grado: profundidad < %1\$s mm → 0 a 4; mayor → 5.", LineMeasure.GRADE_MM.joinToString(" / ") { f2(it) }) +
        " " + tr("Rasgos: distancias proyectadas en la foto (dependen del giro de la cabeza)."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp))
}

/** Alto fijo de las filas de arrugas, surcos y ojeras (alinea las cifras con la columna fija de eliminar). */
/** Alto de una línea de texto de la tabla (bodyMedium sin interlineado). */
private val ROW_LINE = 15.dp

/** Fila de la tabla preparada (celdas, línea, eliminar). */
private class RowSpec(val cells: List<String>, val header: Boolean = false, val line: AnnotLine? = null, val deleteName: String? = null,
                      val deletePhotos: List<Pair<Int, String>> = emptyList(), val divider: Boolean = false,
                      /** Índice de la medida (columna de [LINE_COLS]) en la tabla de varias fotos. */
                      val measure: Int? = null)

/** Fila de la ficha del tramo del ancho de una arruga o surco. */
private const val WIDTH_KEY = "Ancho (a media profundidad)"

/** Fila de la tabla: texto con contraste alto (cifras en negrita y color de primer plano). */
/** Texto sin interlineado extra (tabla compacta): alto de línea = tamaño de letra, sin relleno de la fuente. */
private fun tight(st: androidx.compose.ui.text.TextStyle) = st.copy(lineHeight = st.fontSize,
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        androidx.compose.ui.text.style.LineHeightStyle.Trim.Both),
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false))

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, line: AnnotLine? = null,
                     /** Acción al final de la fila (eliminar la línea). */
                     action: (@Composable () -> Unit)? = null, fixedHeight: Dp? = null, onClick: (() -> Unit)? = null,
                     /** Fila de la línea seleccionada (fondo resaltado). */
                     highlight: Boolean = false,
                     /** Estilo del trazo de esta medida en la foto (muestra delante del texto). */
                     swatch: Int? = null,
                     bring: androidx.compose.foundation.relocation.BringIntoViewRequester? = null,
                     /** Anchos de la primera columna y de las cifras (reparto del espacio). */
                     firstW: Dp = 170.dp, valueW: Dp = 72.dp,
                     /** Toque en una celda concreta (índice); si no, [onClick]. */
                     onCellClick: ((Int) -> Unit)? = null,
                     /** Muestra del trazo en una celda (índice, estilo). */
                     swatchCell: Pair<Int, Int>? = null) {
    Row((if (fixedHeight != null) Modifier.height(fixedHeight) else Modifier)
        .then(if (highlight) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) else Modifier)
        .then(if (bring != null) Modifier.bringIntoViewRequester(bring) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClickLabel = tr("Ver en la foto"), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
        .semantics { if (highlight) selected = true },
        verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, c ->
            val w: Dp = if (i == 0) firstW else valueW
            Row(Modifier.width(w).then(if (onCellClick != null) Modifier.clickable(onClickLabel = tr("Ver en la foto")) { onCellClick(i) } else Modifier),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (i == 0) Arrangement.Start else Arrangement.End) {
                if (i == 0 && swatch != null) { GuideSwatch(swatch, Modifier.size(26.dp, 11.dp)); Spacer(Modifier.width(4.dp)) }
                if (swatchCell?.first == i && !(i == 0 && swatch != null)) { GuideSwatch(swatchCell.second, Modifier.size(22.dp, 10.dp)); Spacer(Modifier.width(3.dp)) }
                if (i == 0 && line != null) Canvas(Modifier.size(9.dp)) { drawCircle(com.photomed.app.ui.camera.lineColor(line)) }
                val st = tight(if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall)
                val fw = when { header -> FontWeight.Bold; i > 0 -> FontWeight.SemiBold; else -> FontWeight.Normal }
                val col = if (header || i > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
                val pad = Modifier.padding(start = if (i == 0 && line != null) 6.dp else 0.dp)
                // cabecera: puede ocupar dos renglones; filas: cada renglón en una línea (los nombres largos acaban en «…»)
                if (header) Text(c, style = st, fontWeight = fw, color = col, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End, modifier = pad)
                else Column(pad, horizontalAlignment = if (i == 0) Alignment.Start else Alignment.End) {
                    c.split('\n').forEach { t ->
                        Text(t, style = st, fontWeight = fw, color = col, maxLines = 1, softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
                    }
                }
            }
        }
        action?.invoke()
    }
}

/**
 * Eliminar una línea (falso positivo): con una sola foto, directamente; con varias ([photos]: índice y
 * fecha de las fotos donde está), un menú para elegir en cuál o en todas.
 */
@Composable
private fun DeleteLineButton(name: String, photos: List<Pair<Int, String>>, size: Dp = 36.dp, onDelete: (Int?) -> Unit) {
    var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { if (photos.isEmpty()) onDelete(null) else menu = true }, modifier = Modifier.size(size)) {
            Icon(Icons.Default.Delete, tr("Eliminar %1\$s (falso positivo)", name), Modifier.size(minOf(20.dp, size - 4.dp)), tint = MaterialTheme.colorScheme.error)
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            photos.forEach { (i, d) -> androidx.compose.material3.DropdownMenuItem(text = { Text(tr("En la foto %1\$d · %2\$s", i + 1, d.replace("\n", " "))) },
                onClick = { menu = false; onDelete(i) }) }
            if (photos.size > 1) androidx.compose.material3.DropdownMenuItem(text = { Text(tr("En todas")) }, onClick = { menu = false; onDelete(null) })
        }
    }
}
