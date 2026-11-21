package com.photomed.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.West
import androidx.compose.material.icons.outlined.ChangeHistory
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Herramienta de anotación: lápiz, símbolo (marcador con un toque o arrastrado de un extremo a otro),
 * seleccionar (arrastrar un trazo o símbolo), borrar trazos enteros o borrar como una goma.
 */
enum class InkTool(val label: String, val icon: ImageVector) {
    PEN("Lápiz", Icons.Default.Edit), SYMBOL("Símbolo", Icons.Default.Interests), SELECT("Seleccionar y mover", Icons.Default.OpenWith),
    ERASE_STROKE("Borrar trazo", Icons.Default.CleaningServices), ERASE_PAINT("Goma", Icons.Default.AutoFixOff)
}

/**
 * Herramienta, color (ARGB) y grosor (fracción del ancho de la foto); para los símbolos, cuál, su texto
 * opcional y el lado del texto («auto»: el que no se superpone).
 */
data class InkStyle(val tool: InkTool = InkTool.PEN, val color: Int = INK_COLORS[0], val width: Float = 0.006f,
                    val symbol: String = "circle", val text: String = "", val side: String = "auto") {
    /** Tamaño de los marcadores (fracción del ancho): proporcional al grosor elegido. */
    val symbolSize get() = width * 6f
}

val INK_COLORS = listOf(0xFFFF1744, 0xFFFFEA00, 0xFF00E676, 0xFF00B0FF, 0xFF2962FF, 0xFFD500F9, 0xFFFFFFFF, 0xFF000000).map { it.toInt() }
private val COLOR_NAMES = listOf("rojo", "amarillo", "verde", "celeste", "azul", "violeta", "blanco", "negro")
val SYMBOL_LABELS = linkedMapOf("dot" to "Punto", "circle" to "Círculo", "triangle" to "Triángulo", "square" to "Cuadrado",
    "x" to "X", "plus" to "Cruz", "star" to "Estrella", "line" to "Línea", "arrow" to "Flecha", "polyline" to "Ángulos (multipunto)")
val SIDE_LABELS = linkedMapOf("auto" to "Auto", "top" to "Arriba", "bottom" to "Abajo", "left" to "Izquierda", "right" to "Derecha")

/**
 * Barra flotante vertical (sobre la foto) con un icono por herramienta, deshacer, borrar todo (con
 * confirmación) y terminar.
 */
@Composable
fun InkRail(style: InkStyle, onStyle: (InkStyle) -> Unit, canUndo: Boolean, onUndo: () -> Unit, onClear: () -> Unit, onDone: () -> Unit,
            modifier: Modifier = Modifier) {
    var confirmClear by remember { mutableStateOf(false) }
    // si no cabe en el alto de la foto (tabla abierta, horizontal), se desplaza: nunca queda cortada
    Column(modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
        .verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        InkTool.entries.forEach { t ->
            val sel = style.tool == t
            IconButton(onClick = { onStyle(style.copy(tool = t)) },
                modifier = Modifier.semantics { selected = sel }
                    .then(if (sel) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), CircleShape) else Modifier)) {
                Icon(t.icon, t.label, tint = Color.White)
            }
        }
        IconButton(onClick = onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer", tint = if (canUndo) Color.White else Color.Gray) }
        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.Delete, "Borrar todas las anotaciones", tint = Color.White) }
        IconButton(onClick = onDone) { Icon(Icons.Default.Check, "Terminar de anotar", tint = Color.White) }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("¿Borrar todas las anotaciones?") },
        text = { Text("Se quitarán todos los trazos y símbolos de esta foto. Puede deshacerlo después.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Borrar todo") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } })
}

/** Icono de cada símbolo (en lugar del nombre; el nombre queda como descripción accesible). */
val SYMBOL_ICONS: Map<String, ImageVector> = linkedMapOf(
    "dot" to Icons.Default.Circle, "circle" to Icons.Outlined.Circle, "triangle" to Icons.Outlined.ChangeHistory,
    "square" to Icons.Outlined.CropSquare, "x" to Icons.Default.Close, "plus" to Icons.Default.Add, "star" to Icons.Outlined.StarOutline,
    "line" to Icons.Default.HorizontalRule, "arrow" to Icons.Default.NorthEast, "polyline" to Icons.Default.Timeline)
/** Icono del lado del texto. */
val SIDE_ICONS: Map<String, ImageVector> = linkedMapOf(
    "auto" to Icons.Default.AutoMode, "top" to Icons.Default.North, "bottom" to Icons.Default.South,
    "left" to Icons.Default.West, "right" to Icons.Default.East)

/** Fila de iconos para elegir uno (seleccionado con fondo de color). */
@Composable
private fun IconPicker(icons: Map<String, ImageVector>, labels: Map<String, String>, value: String, onPick: (String) -> Unit, what: String) =
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        icons.forEach { (k, ic) ->
            val sel = value == k
            IconButton(onClick = { onPick(k) }, modifier = Modifier.size(44.dp).semantics { selected = sel }
                .then(if (sel) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), CircleShape) else Modifier)) {
                Icon(ic, "$what: ${labels[k] ?: k}", tint = Color.White)
            }
        }
    }

/** Ajustes de la herramienta elegida (abajo): iconos en lugar de textos (más sitio para la foto). */
@Composable
fun InkOptions(style: InkStyle, onStyle: (InkStyle) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        if (style.tool == InkTool.SYMBOL) {
            IconPicker(SYMBOL_ICONS, SYMBOL_LABELS, style.symbol, { onStyle(style.copy(symbol = it)) }, "Símbolo")
            if (style.symbol != "polyline") Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TextFields, null, tint = Color.LightGray, modifier = Modifier.padding(end = 6.dp))
                androidx.compose.material3.OutlinedTextField(style.text, { onStyle(style.copy(text = it.take(30))) }, singleLine = true,
                    placeholder = { Text("Texto", color = Color.Gray) }, modifier = Modifier.weight(1f).semantics { contentDescription = "Texto corto (opcional)" },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            }
            if (style.text.isNotBlank() && style.symbol != "polyline") IconPicker(SIDE_ICONS, SIDE_LABELS, style.side, { onStyle(style.copy(side = it)) }, "Lado del texto")
        }
        if (style.tool in setOf(InkTool.PEN, InkTool.SYMBOL)) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            INK_COLORS.forEachIndexed { i, c ->
                val sel = style.color == c
                // área táctil de 44 dp (accesibilidad) con el círculo de color dentro
                Box(Modifier.size(44.dp).clickable(onClickLabel = "Elegir el color ${COLOR_NAMES[i]}") { onStyle(style.copy(color = c)) }
                    .semantics { contentDescription = "Color ${COLOR_NAMES[i]}"; selected = sel }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(28.dp).background(Color(c), CircleShape)
                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape))
                }
            }
        }
        if (style.tool != InkTool.SELECT) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (style.tool == InkTool.SYMBOL) Icons.Default.PhotoSizeSelectSmall else Icons.Default.LineWeight, null, tint = Color.LightGray)
            Box(Modifier.padding(start = 8.dp).size(24.dp), contentAlignment = Alignment.Center) {
                val d = (style.width * 900f).coerceIn(2f, 22f).dp
                Box(Modifier.size(d).background(if (style.tool in setOf(InkTool.PEN, InkTool.SYMBOL)) Color(style.color) else Color.LightGray, CircleShape))
            }
            Slider(value = style.width, onValueChange = { onStyle(style.copy(width = it)) }, valueRange = 0.002f..0.025f,
                modifier = Modifier.weight(1f).semantics { contentDescription = if (style.tool == InkTool.SYMBOL) "Tamaño del símbolo" else "Grosor del trazo" })
        }
    }
}
