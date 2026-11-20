package com.photomed.app.ui.compare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    if (m.areaMm2 != null || m.deltaE != null) listOfNotNull(
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
    )

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
        if (l.metrics == null || l.layer !in layers) continue
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
fun MetricsCard(line: AnnotLine, onTable: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val m = line.metrics ?: return
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f))) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(annotColor(line.layer)) }
                Text(m.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
            }
            metricRows(m).forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 2.dp)) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
 * entre la última y la primera.
 */
@Composable
fun MetricsSheet(columns: List<Pair<String, List<AnnotLine>>>, layers: Set<AnnotLayer>, onDismiss: () -> Unit) {
    SafeBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
            Text("Medidas de líneas y surcos", style = MaterialTheme.typography.titleLarge)
            val byName = LinkedHashMap<String, Pair<AnnotLayer, MutableList<LineMetrics?>>>()
            columns.forEachIndexed { ci, (_, lines) ->
                lines.filter { it.metrics != null && it.layer in layers }.forEach { l ->
                    val e = byName.getOrPut(l.metrics!!.name) { l.layer to MutableList(columns.size) { null } }
                    e.second[ci] = l.metrics
                }
            }
            if (byName.isEmpty()) {
                Text("No hay líneas medidas en las capas visibles. Active Surcos, Frente, Ceño, Patas de gallo u Ojeras.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
            }
            val ordered = byName.entries.sortedWith(compareBy({ it.value.first.ordinal }, { it.key }))
            val single = columns.size == 1
            val scroll = rememberScrollState()
            for (area in listOf(false, true)) {
                val rows = ordered.filter { (it.value.second.firstNotNullOfOrNull { m -> m }?.let { m -> m.areaMm2 != null || m.deltaE != null } ?: false) == area }
                if (rows.isEmpty()) continue
                val cols = if (area) AREA_COLS.take(4) else LINE_COLS
                Column(Modifier.horizontalScroll(scroll).padding(top = 12.dp)) {
                    if (single) {
                        TableRow(listOf(if (area) "Zona" else "Línea") + cols, header = true)
                        rows.forEach { (name, v) ->
                            val m = v.second[0]
                            TableRow(listOf(name) + (if (area) areaVals(m) else lineVals(m)).take(cols.size).mapIndexed { i, x -> fmt(i, x, area) }, dot = v.first)
                        }
                    } else {
                        val head = columns.map { it.first.replace(", ", "\n") }
                        TableRow(listOf(if (area) "Zona · medida" else "Línea · medida") + head + "Δ", header = true)
                        rows.forEach { (name, v) ->
                            cols.forEachIndexed { i, c ->
                                val vals = v.second.map { m -> (if (area) areaVals(m) else lineVals(m))[i] }
                                val first = vals.firstOrNull { it != null }; val last = vals.lastOrNull { it != null }
                                val delta = if (first != null && last != null && vals.count { it != null } >= 2) last - first else null
                                TableRow(listOf(if (i == 0) "$name\n${c.replace("\n", " ")}" else "   ${c.replace("\n", " ")}") +
                                    vals.map { fmt(i, it, area) } + signed(delta, if (!area && i == 2) 2 else 1), dot = if (i == 0) v.first else null)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            if (ordered.any { it.value.second.any { m -> m?.type != null } }) {
                Text("Tipo probable de ojera: " + ordered.mapNotNull { e -> e.value.second.lastOrNull { it?.type != null }?.let { "${e.key}: ${it.type}" } }.joinToString("; "),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            val all = ordered.flatMap { it.value.second }.filterNotNull()
            val scale = all.firstOrNull()?.scale
            val relief = all.any { it.depthSource == "relieve" }
            Text(METRICS_NOTE + (if (scale == "interpupilar") " (Escala: distancia interpupilar de 63 mm; iris no fiable.)" else "") +
                (if (scale == "sensor de profundidad") " Escala medida con el sensor de profundidad del teléfono." else "") +
                (if (relief) " Profundidad medida en el relieve (estéreo fotométrico con la pantalla)." else "") +
                " Grado: profundidad < ${LineMeasure.GRADE_MM.joinToString(" / ") { f2(it) }} mm → 0 a 4; mayor → 5.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, dot: AnnotLayer? = null) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, c ->
            val w: Dp = if (i == 0) 150.dp else 62.dp
            Row(Modifier.width(w), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (i == 0) Arrangement.Start else Arrangement.End) {
                if (i == 0 && dot != null) Canvas(Modifier.size(8.dp)) { drawCircle(annotColor(dot)) }
                Text(c, style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                    modifier = Modifier.padding(start = if (i == 0 && dot != null) 6.dp else 0.dp))
            }
        }
    }
}
