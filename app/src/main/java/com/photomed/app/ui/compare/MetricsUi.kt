package com.photomed.app.ui.compare

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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

/** Filas (etiqueta, valor) de las medidas de una línea o zona. */
fun metricRows(m: LineMetrics): List<Pair<String, String>> =
    if (m.isFeature) m.features.map { (k, v) -> k.substringBeforeLast(",") to featureValue(k, v) } + ("Escala" to scaleLabel(m.scale))
    else if (m.areaMm2 != null || m.deltaE != null) listOfNotNull(
        "Área" to "${f1(m.areaMm2?.div(100f))} cm²",
        "ΔL* frente a la mejilla" to "${f1(m.deltaL)} (más oscura)",
        m.deltaE?.let { "ΔE*ab frente a la mejilla" to f1(it) },
        m.ita?.let { "ITA° ojera / mejilla" to "${f1(it)}° / ${f1(m.itaRef)}°" },
        m.type?.let { "Tipo probable" to it },
    ) else listOf(
        "Longitud" to "${f1(m.lengthMm)} mm",
        "Ancho (a media profundidad)" to "${f1(m.widthMm)} mm",
        (if (m.depthSource == "relieve") "Profundidad (relieve medido)" else "Profundidad estimada (sombreado)") to "${f2(m.depthMm)} mm",
        "Contraste ΔL*" to f1(m.contrastL),
        "Grado orientativo (0–5)" to (m.grade?.let { "$it · ${gradeLabel(it)}" } ?: "—"),
        "Escala" to scaleLabel(m.scale),
    ) + listOfNotNull(m.note?.let { "Nota" to it })

/** Valor de una medida de rasgo con su unidad (la de la etiqueta tras la coma). */
fun featureValue(label: String, v: Float): String {
    val unit = label.substringAfterLast(",", "").trim()
    return when (unit) {
        "mm²" -> String.format(Locale.getDefault(), "%.0f mm²", v)
        "°" -> String.format(Locale.getDefault(), "%.1f°", v)
        "" -> f2(v)
        else -> "${f1(v)} $unit"
    }
}

fun scaleLabel(s: String) = when (s) {
    "sensor de profundidad" -> "sensor de profundidad"
    "interpupilar" -> "interpupilar (63 mm)"
    else -> "iris (11,7 mm)"
}

fun gradeLabel(g: Int) = listOf("sin arruga", "apenas perceptible", "superficial", "moderada", "profunda", "muy profunda")[g.coerceIn(0, 5)]

const val METRICS_NOTE = "Medidas orientativas desde una sola foto: escala por el diámetro del iris (11,7 mm), " +
    "ancho y profundidad por el perfil de sombra (forma a partir del sombreado) y grado al estilo de Lemperle. " +
    "Dependen de la luz y la pose: compare fotos tomadas igual. No sustituyen a la perfilometría ni a la valoración clínica."

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

/** Ficha de la línea tocada, con acceso a la tabla completa. */
@Composable
fun MetricsCard(line: AnnotLine, onTable: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, number: Int? = null) {
    val m = line.metrics ?: return
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f))) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(com.photomed.app.ui.camera.lineColor(line)) }
                Text((number?.let { "$it · " } ?: "") + m.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
            }
            metricRows(m).forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 2.dp)) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            TextButton(onClick = onTable) {
                Icon(Icons.Default.TableChart, null, Modifier.size(18.dp)); Text("Tabla de medidas", Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Columnas numéricas de la tabla de líneas. */
private val LINE_COLS = listOf("Long.\nmm", "Ancho\nmm", "Prof.\nmm", "ΔL*", "Grado")
private fun lineVals(m: LineMetrics?): List<Float?> = listOf(m?.lengthMm, m?.widthMm, m?.depthMm, m?.contrastL, m?.grade?.toFloat())
private val AREA_COLS = listOf("Área\ncm²", "ΔL*", "ΔE*ab", "ITA°", "")
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
            Text("Medidas de líneas y surcos", style = MaterialTheme.typography.titleLarge)
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
                 onSelectLine: ((AnnotLine) -> Unit)? = null) {
    androidx.compose.material3.Surface(modifier, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Medidas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar las medidas") }
            }
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 12.dp).verticalScroll(rememberScrollState())) {
                MetricsContent(columns, layers, loading, note, onShowLayers, onDelete, dismissedCount, onRestore, traces, onSelectLine)
            }
        }
    }
}

/** Contenido de la tabla de medidas (ver [MetricsSheet]). */
@Composable
fun MetricsContent(columns: List<Pair<String, List<AnnotLine>>>, layers: Set<AnnotLayer>,
                   loading: Boolean = false, note: String? = null, onShowLayers: ((Set<AnnotLayer>) -> Unit)? = null,
                   onDelete: ((String, Int?) -> Unit)? = null, dismissedCount: Int = 0, onRestore: (() -> Unit)? = null,
                   traces: TraceActions? = null, onSelectLine: ((AnnotLine) -> Unit)? = null) {
    // estado de los trazos: confirmados (guardados en la foto, no se vuelven a analizar) o por confirmar
    if (traces != null && !loading) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (traces.confirmed) Icons.Default.Verified else Icons.Default.Info, null, Modifier.size(18.dp),
            tint = if (traces.confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (traces.confirmed) (if (traces.savedInPhoto) "Trazos confirmados: guardados en la foto" else "Trazos confirmados (guardados en la app)")
            else "Trazos sin confirmar (análisis automático)",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 6.dp))
        if (traces.confirmed) TextButton(onClick = traces.onRegenerate) { Text("Regenerar") }
        else androidx.compose.material3.FilledTonalButton(onClick = traces.onConfirm) { Text("Confirmar") }
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
        Text("Analizando…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
    if (byName.isEmpty() && !loading) Text(note ?: "No se encontraron arrugas ni surcos medibles.",
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
    // medidas parciales (sin malla facial): el motivo, también con medidas
    if (byName.isNotEmpty() && note != null) Text(note, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    val hidden = byName.values.map { it.first.layer }.toSet() - layers
    if (hidden.isNotEmpty() && onShowLayers != null) {
        TextButton(onClick = { onShowLayers(layers + hidden) }) {
            Text("Mostrar en la foto: " + hidden.sortedBy { it.ordinal }.joinToString(", ") { it.label })
        }
    }
    val ordered = byName.entries.sortedBy { numbers[it.key] ?: Int.MAX_VALUE }
    val single = columns.size == 1
    // un desplazamiento horizontal por tabla (compartido, la tabla más estrecha limitaba a las demás)
    val scrolls = List(3) { rememberScrollState() }
    fun label(name: String, v: Pair<AnnotLine, List<LineMetrics?>>) = "${numbers[name] ?: ""}  $name" + if (v.second.any { it?.note != null }) " ✱" else ""
    for (group in 0..2) {
        val rows = ordered.filter { e -> e.value.second.firstNotNullOfOrNull { it }?.let { com.photomed.app.align.MeasureNumbering.group(it) } == group }
        if (rows.isEmpty()) continue
        val area = group == 1
        Text(listOf("Arrugas y surcos", "Ojeras", "Rasgos")[group], style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        Column(Modifier.horizontalScroll(scrolls[group]).padding(top = 4.dp)) {
            if (group == 2) {
                // rasgos: una fila por medida
                TableRow(listOf("Rasgo · medida") + (if (single) listOf("Valor") else columns.map { it.first.replace(", ", "\n") } + "Δ"), header = true)
                rows.forEach { (name, v) ->
                    val labels = v.second.firstNotNullOfOrNull { it }?.features?.map { it.first }.orEmpty()
                    labels.forEachIndexed { i, lab ->
                        val vals = v.second.map { m -> m?.features?.firstOrNull { it.first == lab }?.second }
                        val first = vals.firstOrNull { it != null }; val last = vals.lastOrNull { it != null }
                        val delta = if (!single && first != null && last != null && vals.count { it != null } >= 2) last - first else null
                        TableRow(listOf(if (i == 0) "${label(name, v)}\n${lab}" else "   $lab") + vals.map { it?.let { x -> featureValue(lab, x) } ?: "—" } +
                            (if (single) emptyList() else listOf(signed(delta, if (lab.endsWith("mm²")) 0 else 1))), line = if (i == 0) v.first else null, wide = true,
                            onClick = onSelectLine?.let { f -> { f(v.first) } })
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
                specs += RowSpec(listOf(if (area) "Zona" else "Línea") + cols, header = true)
                rows.forEach { (name, v) ->
                    val m = v.second[0]
                    specs += RowSpec(listOf(label(name, v)) + (if (area) areaVals(m) else lineVals(m)).take(cols.size).mapIndexed { i, x -> fmt(i, x, area) },
                        line = v.first, deleteName = name)
                }
            } else {
                val head = columns.map { it.first.replace(", ", "\n") }
                specs += RowSpec(listOf(if (area) "Zona · medida" else "Línea · medida") + head + "Δ", header = true)
                rows.forEach { (name, v) ->
                    cols.forEachIndexed { i, c ->
                        val vals = v.second.map { m -> (if (area) areaVals(m) else lineVals(m))[i] }
                        val first = vals.firstOrNull { it != null }; val last = vals.lastOrNull { it != null }
                        val delta = if (first != null && last != null && vals.count { it != null } >= 2) last - first else null
                        specs += RowSpec(listOf(if (i == 0) "${label(name, v)}\n${c.replace("\n", " ")}" else "   ${c.replace("\n", " ")}") +
                            vals.map { fmt(i, it, area) } + signed(delta, if (!area && i == 2) 2 else 1), line = if (i == 0) v.first else null,
                            deleteName = if (i == 0) name else null,
                            deletePhotos = if (i == 0) columns.mapIndexedNotNull { ci, col -> if (v.second[ci] != null) ci to col.first else null } else emptyList(),
                            divider = i == cols.size - 1)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(Modifier.weight(1f).horizontalScroll(scrolls[group])) {
                    var owner: AnnotLine? = null
                    specs.forEach { r -> if (r.line != null) owner = r.line; if (r.header) owner = null
                        val o = owner
                        TableRow(r.cells, header = r.header, line = r.line, fixedHeight = ROW_H,
                            onClick = if (r.header || o == null) null else onSelectLine?.let { f -> { f(o) } })
                        if (r.divider) HorizontalDivider() }
                }
                // acciones fijas a la derecha: volver a trazar, revertir y eliminar
                val edit = traces?.onEdit?.takeIf { single && !area }
                val revert = traces?.onRevert?.takeIf { single && !area && traces.edited.isNotEmpty() }
                val nAct = listOfNotNull(edit, revert, onDelete).size
                if (nAct > 0) Column(Modifier.width(40.dp * nAct)) {
                    specs.forEach { r ->
                        Row(Modifier.height(ROW_H).width(40.dp * nAct), verticalAlignment = Alignment.CenterVertically) {
                            val n = r.deleteName
                            if (edit != null) androidx.compose.foundation.layout.Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                if (n != null) IconButton(onClick = { edit(n) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Edit, "Volver a trazar $n", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (revert != null) androidx.compose.foundation.layout.Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                if (n != null && n in traces.edited) IconButton(onClick = { revert(n) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, "Revertir el trazado de $n", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (onDelete != null) androidx.compose.foundation.layout.Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                n?.let { DeleteLineButton(it, if (single) emptyList() else r.deletePhotos) { c -> onDelete(it, if (single) (c ?: 0) else c) } }
                            }
                        }
                        if (r.divider) HorizontalDivider()
                    }
                }
            }
        }
    }
    if (dismissedCount > 0 && onRestore != null) TextButton(onClick = onRestore) {
        Text("Restaurar líneas eliminadas ($dismissedCount)")
    }
    if (ordered.any { it.value.second.any { m -> m?.type != null } }) {
        Text("Tipo probable de ojera: " + ordered.mapNotNull { e -> e.value.second.lastOrNull { it?.type != null }?.let { "${e.key}: ${it.type}" } }.joinToString("; "),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
    if (ordered.any { it.value.second.any { m -> m?.note != null } }) Text("✱ Parcialmente cubierto por barba o bigote: se mide solo la parte visible.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    val all = ordered.flatMap { it.value.second }.filterNotNull()
    val scale = all.firstOrNull()?.scale
    val relief = all.any { it.depthSource == "relieve" }
    Text(METRICS_NOTE + (if (scale == "interpupilar") " (Escala: distancia interpupilar de 63 mm; iris no fiable.)" else "") +
        (if (scale == "sensor de profundidad") " Escala medida con el sensor de profundidad del teléfono." else "") +
        (if (relief) " Profundidad medida en el relieve (estéreo fotométrico con la pantalla)." else "") +
        " Grado: profundidad < ${LineMeasure.GRADE_MM.joinToString(" / ") { f2(it) }} mm → 0 a 4; mayor → 5." +
        " Rasgos: distancias proyectadas en la foto (dependen del giro de la cabeza).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp))
}

/** Alto fijo de las filas de arrugas, surcos y ojeras (alinea las cifras con la columna fija de eliminar). */
private val ROW_H = 46.dp

/** Fila de la tabla preparada (celdas, línea, eliminar). */
private class RowSpec(val cells: List<String>, val header: Boolean = false, val line: AnnotLine? = null, val deleteName: String? = null,
                      val deletePhotos: List<Pair<Int, String>> = emptyList(), val divider: Boolean = false)

/** Fila de la tabla: texto con contraste alto (cifras en negrita y color de primer plano). */
@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, line: AnnotLine? = null, wide: Boolean = false,
                     /** Acción al final de la fila (eliminar la línea). */
                     action: (@Composable () -> Unit)? = null, fixedHeight: Dp? = null, onClick: (() -> Unit)? = null) {
    Row((if (fixedHeight != null) Modifier.height(fixedHeight) else Modifier.padding(vertical = 3.dp))
        .then(if (onClick != null) Modifier.clickable(onClickLabel = "Ver en la foto", onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, c ->
            val w: Dp = if (i == 0) (if (wide) 210.dp else 170.dp) else 72.dp
            Row(Modifier.width(w), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (i == 0) Arrangement.Start else Arrangement.End) {
                if (i == 0 && line != null) Canvas(Modifier.size(9.dp)) { drawCircle(com.photomed.app.ui.camera.lineColor(line)) }
                Text(c, style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = when { header -> FontWeight.Bold; i > 0 -> FontWeight.SemiBold; else -> FontWeight.Normal },
                    color = if (header || i > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                    modifier = Modifier.padding(start = if (i == 0 && line != null) 6.dp else 0.dp))
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
private fun DeleteLineButton(name: String, photos: List<Pair<Int, String>>, onDelete: (Int?) -> Unit) {
    var menu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { if (photos.isEmpty()) onDelete(null) else menu = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, "Eliminar $name (falso positivo)", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            photos.forEach { (i, d) -> androidx.compose.material3.DropdownMenuItem(text = { Text("En la foto ${i + 1} · ${d.replace("\n", " ")}") },
                onClick = { menu = false; onDelete(i) }) }
            if (photos.size > 1) androidx.compose.material3.DropdownMenuItem(text = { Text("En todas") }, onClick = { menu = false; onDelete(null) })
        }
    }
}
