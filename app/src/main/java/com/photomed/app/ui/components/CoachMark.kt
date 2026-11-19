package com.photomed.app.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Indicaciones de primera vez: una tarjeta breve que explica gestos que no se ven (pulsación larga,
 * arrastrar, pellizcar, controles que se ocultan) y no vuelve a aparecer tras "Entendido".
 * No bloquea la pantalla.
 */
object CoachMarks {
    /** Las pruebas de uso las desactivan. */
    @Volatile var enabled = true
    private const val PREFS = "photomed"
    fun seen(context: Context, id: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("coach_$id", false)
    fun markSeen(context: Context, id: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("coach_$id", true).apply()
    /** Vuelve a mostrar todas (ajustes). */
    fun resetAll(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().apply { p.all.keys.filter { it.startsWith("coach_") }.forEach { remove(it) } }.apply()
    }
}

@Composable
fun CoachMark(id: String, icon: ImageVector, text: String, modifier: Modifier = Modifier, show: Boolean = true) {
    val context = LocalContext.current
    var visible by remember(id) { mutableStateOf(CoachMarks.enabled && !CoachMarks.seen(context, id)) }
    AnimatedVisibility(visible && show, modifier) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface),
        ) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(22.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                TextButton(onClick = { visible = false; CoachMarks.markSeen(context, id) }) {
                    Text("Entendido", color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
