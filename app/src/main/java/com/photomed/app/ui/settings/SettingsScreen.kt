package com.photomed.app.ui.settings

import androidx.compose.foundation.selection.selectable
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.photomed.app.align.LiveMatch
import com.photomed.app.ui.camera.MatchSettingsContent
import com.photomed.app.ui.components.SectionHeader
import com.photomed.app.ui.theme.Md3Spacing
import com.photomed.app.align.AlignOptions
import androidx.compose.material3.Checkbox

/** Ajustes globales (mismas preferencias que el panel ⚙ de la cámara). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photomed", Context.MODE_PRIVATE) }
    var auto by remember { mutableStateOf(prefs.getBoolean("auto_capture", true)) }
    var threshold by remember { mutableFloatStateOf(prefs.getFloat("match_threshold_v2", LiveMatch.DEFAULT_THRESHOLD)) }
    var zoom by remember { mutableStateOf(prefs.getBoolean("zoom_assist", true)) }
    var mirror by remember { mutableStateOf(prefs.getBoolean("mirror_front", true)) }
    var feedback by remember { mutableStateOf(prefs.getBoolean("guide_feedback", true)) }
    var nearCapture by remember { mutableStateOf(prefs.getBoolean("auto_capture_near", true)) }
    var burst by remember { mutableStateOf(prefs.getBoolean("burst", true)) }
    var alignPoints by remember { mutableStateOf(prefs.getBoolean(AlignOptions.PREF_POINTS, true)) }
    var alignContours by remember { mutableStateOf(prefs.getBoolean(AlignOptions.PREF_CONTOURS, true)) }
    var alignGaze by remember { mutableStateOf(prefs.getBoolean(AlignOptions.PREF_GAZE, true)) }
    var sensitivity by remember { mutableStateOf(runCatching { com.photomed.app.align.WrinkleTuning.Level.valueOf(
        prefs.getString(com.photomed.app.align.WrinkleTuning.PREF, "BALANCED")!!) }.getOrDefault(com.photomed.app.align.WrinkleTuning.Level.BALANCED)) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Ajustes") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            MatchSettingsContent(
                autoCapture = auto, threshold = threshold,
                onAutoCapture = { auto = it; prefs.edit().putBoolean("auto_capture", it).apply() },
                onThreshold = { threshold = it; prefs.edit().putFloat("match_threshold_v2", it).apply() },
                zoomAssist = zoom, onZoomAssist = { zoom = it; prefs.edit().putBoolean("zoom_assist", it).apply() },
                useGaze = alignGaze, onUseGaze = { alignGaze = it; prefs.edit().putBoolean(AlignOptions.PREF_GAZE, it).apply() },
                feedback = feedback, onFeedback = { feedback = it; prefs.edit().putBoolean("guide_feedback", it).apply() },
                nearCapture = nearCapture, onNearCapture = { nearCapture = it; prefs.edit().putBoolean("auto_capture_near", it).apply() },
                burst = burst, onBurst = { burst = it; prefs.edit().putBoolean("burst", it).apply() },
            )
            HorizontalDivider()
            // sensibilidad de la detección de arrugas y surcos (ver docs/BENCHMARK.md)
            SectionHeader("Detección de arrugas", Modifier.padding(horizontal = Md3Spacing.s6))
            com.photomed.app.align.WrinkleTuning.Level.entries.forEach { lv ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()
                    .selectable(sensitivity == lv, role = androidx.compose.ui.semantics.Role.RadioButton) {
                        sensitivity = lv; prefs.edit().putString(com.photomed.app.align.WrinkleTuning.PREF, lv.name).apply()
                        com.photomed.app.align.WrinkleTuning.apply(lv)
                    }.padding(horizontal = Md3Spacing.s6, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = sensitivity == lv, onClick = null)
                    androidx.compose.foundation.layout.Column(Modifier.padding(start = 8.dp)) {
                        Text(lv.label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        Text(lv.desc, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
            SectionHeader("Alineación automática (comparar)", Modifier.padding(horizontal = Md3Spacing.s6))
            listOf(
                Triple("Usar puntos anatómicos", "Ojos, cejas, nariz, boca, óvalo; articulaciones en el cuerpo.", AlignOptions.PREF_POINTS),
                Triple("Usar contornos", "Bordes anatómicos (mandíbula, frente, orejas, silueta) y perspectiva por proporciones.", AlignOptions.PREF_CONTOURS),
                Triple("Usar mirada (gaze)", "Comprueba que ambas fotos miren en la misma dirección.", AlignOptions.PREF_GAZE),
            ).forEach { (title, desc, key) ->
                val value = when (key) { AlignOptions.PREF_POINTS -> alignPoints; AlignOptions.PREF_CONTOURS -> alignContours; else -> alignGaze }
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text(desc) },
                    trailingContent = { Checkbox(checked = value, onCheckedChange = null) },
                    modifier = Modifier.fillMaxWidth().toggleable(value, role = Role.Checkbox) {
                        when (key) { AlignOptions.PREF_POINTS -> alignPoints = it; AlignOptions.PREF_CONTOURS -> alignContours = it; else -> alignGaze = it }
                        prefs.edit().putBoolean(key, it).apply()
                    },
                )
            }
            HorizontalDivider()
            SectionHeader("Ayudas", Modifier.padding(horizontal = Md3Spacing.s6))
            ListItem(
                headlineContent = { Text("Mostrar las ayudas de primera vez otra vez") },
                supportingContent = { Text("Indicaciones sobre gestos: pulsación larga, arrastrar, pellizcar…") },
                modifier = Modifier.fillMaxWidth().clickable { com.photomed.app.ui.components.CoachMarks.resetAll(context) },
            )
            HorizontalDivider()
            SectionHeader("Cámara", Modifier.padding(horizontal = Md3Spacing.s6))
            ListItem(
                headlineContent = { Text("Vista en espejo (cámara frontal)") },
                supportingContent = { Text("Como las cámaras del teléfono. Las fotos se guardan siempre sin espejar.") },
                trailingContent = { Switch(checked = mirror, onCheckedChange = null) },
                modifier = Modifier.fillMaxWidth().toggleable(mirror, role = Role.Switch) {
                    mirror = it; prefs.edit().putBoolean("mirror_front", it).apply()
                },
            )
            HorizontalDivider()
            SectionHeader("Registro de errores", Modifier.padding(horizontal = Md3Spacing.s6))
            var logs by remember { mutableStateOf(com.photomed.app.CrashLog.all(context)) }
            ListItem(
                headlineContent = { Text(if (logs.isEmpty()) "Sin errores registrados" else "Compartir el registro (${logs.size})") },
                supportingContent = { Text(logs.firstOrNull()?.let { "Último: " + it.readLines().getOrNull(1)?.removePrefix("Fecha: ").orEmpty() }
                    ?: "Si la app se cierra por un error, queda registrado aquí (sin fotos ni datos del paciente).") },
                modifier = Modifier.fillMaxWidth().clickable(enabled = logs.isNotEmpty()) { com.photomed.app.CrashLog.share(context, logs) },
            )
            if (logs.isNotEmpty()) ListItem(
                headlineContent = { Text("Borrar el registro") },
                modifier = Modifier.fillMaxWidth().clickable { com.photomed.app.CrashLog.clear(context); logs = emptyList() },
            )
        }
    }
}
