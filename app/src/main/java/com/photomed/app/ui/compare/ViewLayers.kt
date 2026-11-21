package com.photomed.app.ui.compare

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Las cuatro capas de la vista de una foto, de abajo arriba: la **imagen**, el **diagrama facial**
 * (contorno interno y externo con su numeración), las **anotaciones** (dibujo de planificación) y el
 * **overlay** (fechas y rótulos). Se recuerdan.
 */
data class ViewLayers(val image: Boolean = true, val diagram: Boolean = true, val notes: Boolean = true, val overlay: Boolean = true) {
    fun encode() = listOf(image, diagram, notes, overlay).joinToString("") { if (it) "1" else "0" }
    companion object {
        fun decode(s: String?) = s?.takeIf { it.length == 4 }?.let { ViewLayers(it[0] == '1', it[1] == '1', it[2] == '1', it[3] == '1') } ?: ViewLayers()
    }
}

@Composable
fun rememberViewLayers(): MutableState<ViewLayers> {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photomed", Context.MODE_PRIVATE) }
    return remember {
        object : MutableState<ViewLayers> {
            private val s = mutableStateOf(ViewLayers.decode(prefs.getString("view_layers", null)))
            override var value: ViewLayers
                get() = s.value
                set(v) { s.value = v; prefs.edit().putString("view_layers", v.encode()).apply() }
            override fun component1() = value
            override fun component2(): (ViewLayers) -> Unit = { value = it }
        }
    }
}

/** Botón con el menú de las cuatro capas (casillas). */
@Composable
fun ViewLayersButton(state: MutableState<ViewLayers>, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    var open by remember { mutableStateOf(false) }
    val v = state.value
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Visibility, "Vista: imagen, diagrama, anotaciones y overlay", tint = tint) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                Triple("Imagen", v.image) { b: Boolean -> v.copy(image = b) },
                Triple("Diagrama facial (contornos y números)", v.diagram) { b: Boolean -> v.copy(diagram = b) },
                Triple("Anotaciones", v.notes) { b: Boolean -> v.copy(notes = b) },
                Triple("Overlay (fechas y rótulos)", v.overlay) { b: Boolean -> v.copy(overlay = b) },
            ).forEach { (label, on, set) ->
                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Checkbox(checked = on, onCheckedChange = null) },
                    onClick = { state.value = set(!on) })
            }
        }
    }
}
