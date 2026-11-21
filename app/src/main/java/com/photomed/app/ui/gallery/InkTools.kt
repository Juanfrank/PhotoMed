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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Herramienta de anotación: lápiz, borrador por trazo (quita trazos enteros) o tipo pintura. */
enum class InkTool(val label: String) { PEN("Lápiz"), ERASE_STROKE("Borrar trazo"), ERASE_PAINT("Borrador") }

/** Herramienta, color (ARGB) y grosor (fracción del ancho de la foto). */
data class InkStyle(val tool: InkTool = InkTool.PEN, val color: Int = INK_COLORS[0], val width: Float = 0.006f)

val INK_COLORS = listOf(0xFFFF1744, 0xFFFFEA00, 0xFF00E676, 0xFF00B0FF, 0xFF2962FF, 0xFFD500F9, 0xFFFFFFFF, 0xFF000000).map { it.toInt() }
private val COLOR_NAMES = listOf("rojo", "amarillo", "verde", "celeste", "azul", "violeta", "blanco", "negro")

/** Barra de anotación: herramientas, colores, grosor, deshacer, borrar todo y terminar. */
@Composable
fun InkToolbar(style: InkStyle, onStyle: (InkStyle) -> Unit, canUndo: Boolean, onUndo: () -> Unit, onClear: () -> Unit, onDone: () -> Unit,
               modifier: Modifier = Modifier) {
    Column(modifier.background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InkTool.entries.forEach { t ->
                FilterChip(selected = style.tool == t, onClick = { onStyle(style.copy(tool = t)) }, label = { Text(t.label) },
                    leadingIcon = if (t == InkTool.PEN) { { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) } } else null)
            }
            IconButton(onClick = onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer", tint = if (canUndo) Color.White else Color.Gray) }
            IconButton(onClick = onClear) { Icon(Icons.Default.Delete, "Borrar todas las anotaciones", tint = Color.White) }
            IconButton(onClick = onDone) { Icon(Icons.Default.Check, "Terminar de anotar", tint = Color.White) }
        }
        if (style.tool == InkTool.PEN) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INK_COLORS.forEachIndexed { i, c ->
                val sel = style.color == c
                Box(Modifier.size(30.dp).background(Color(c), CircleShape)
                    .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                    .clickable { onStyle(style.copy(color = c)) }
                    .semantics { contentDescription = "Color ${COLOR_NAMES[i]}"; selected = sel })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Grosor", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                val d = (style.width * 900f).coerceIn(2f, 22f).dp
                Box(Modifier.size(d).background(if (style.tool == InkTool.PEN) Color(style.color) else Color.LightGray, CircleShape))
            }
            Slider(value = style.width, onValueChange = { onStyle(style.copy(width = it)) }, valueRange = 0.002f..0.025f,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Grosor del trazo" })
        }
    }
}

@Suppress("unused") private fun Color.argb() = toArgb()
