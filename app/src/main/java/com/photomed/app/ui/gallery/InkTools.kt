package com.photomed.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    "x" to "X", "plus" to "Cruz", "star" to "Estrella", "line" to "Línea", "arrow" to "Flecha")
val SIDE_LABELS = linkedMapOf("auto" to "Auto", "top" to "Arriba", "bottom" to "Abajo", "left" to "Izquierda", "right" to "Derecha")

/**
 * Barra flotante vertical (sobre la foto) con un icono por herramienta, deshacer, borrar todo (con
 * confirmación) y terminar.
 */
@Composable
fun InkRail(style: InkStyle, onStyle: (InkStyle) -> Unit, canUndo: Boolean, onUndo: () -> Unit, onClear: () -> Unit, onDone: () -> Unit,
            modifier: Modifier = Modifier) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp)).padding(vertical = 4.dp),
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

/** Ajustes de la herramienta elegida (abajo): colores, grosor o tamaño, símbolo, texto y su lado. */
@Composable
fun InkOptions(style: InkStyle, onStyle: (InkStyle) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        @Composable fun chips(map: Map<String, String>, value: String, onPick: (String) -> Unit) =
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                map.forEach { (k, label) -> FilterChip(selected = value == k, onClick = { onPick(k) }, label = { Text(label) }) }
            }
        Text(when (style.tool) {
            InkTool.PEN -> "Dibuje sobre la foto"
            InkTool.SYMBOL -> "Toque para un marcador; arrastre para ampliarlo (línea y flecha: de un extremo al otro)"
            InkTool.SELECT -> "Toque un trazo o símbolo y arrástrelo"
            InkTool.ERASE_STROKE -> "Pase por encima de un trazo o símbolo para quitarlo"
            InkTool.ERASE_PAINT -> "Pase la goma por donde quiera borrar"
        }, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
        if (style.tool == InkTool.SYMBOL) {
            chips(SYMBOL_LABELS, style.symbol) { onStyle(style.copy(symbol = it)) }
            androidx.compose.material3.OutlinedTextField(style.text, { onStyle(style.copy(text = it.take(30))) }, singleLine = true,
                label = { Text("Texto corto (opcional)") }, modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White, unfocusedLabelColor = Color.LightGray))
            if (style.text.isNotBlank()) chips(SIDE_LABELS, style.side) { onStyle(style.copy(side = it)) }
        }
        if (style.tool in setOf(InkTool.PEN, InkTool.SYMBOL)) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            INK_COLORS.forEachIndexed { i, c ->
                val sel = style.color == c
                // área táctil de 44 dp (accesibilidad) con el círculo de color dentro
                Box(Modifier.size(44.dp).clickable(onClickLabel = "Elegir el color ${COLOR_NAMES[i]}") { onStyle(style.copy(color = c)) }
                    .semantics { contentDescription = "Color ${COLOR_NAMES[i]}"; selected = sel }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(30.dp).background(Color(c), CircleShape)
                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape))
                }
            }
        }
        if (style.tool != InkTool.SELECT) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (style.tool == InkTool.SYMBOL) "Tamaño" else "Grosor", color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 8.dp))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                val d = (style.width * 900f).coerceIn(2f, 22f).dp
                Box(Modifier.size(d).background(if (style.tool in setOf(InkTool.PEN, InkTool.SYMBOL)) Color(style.color) else Color.LightGray, CircleShape))
            }
            Slider(value = style.width, onValueChange = { onStyle(style.copy(width = it)) }, valueRange = 0.002f..0.025f,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Grosor del trazo" })
        }
    }
}
