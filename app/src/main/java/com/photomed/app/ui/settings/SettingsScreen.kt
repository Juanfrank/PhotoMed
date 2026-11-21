package com.photomed.app.ui.settings

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
import com.photomed.app.i18n.tr

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
        TopAppBar(title = { Text(tr("Ajustes")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Atrás")) } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // apariencia: tema claro, oscuro o el del sistema
            SectionHeader(tr("Apariencia"), Modifier.padding(horizontal = Md3Spacing.s6))
            com.photomed.app.ui.theme.ThemeMode.entries.forEach { m ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()
                    .selectable(com.photomed.app.ui.theme.AppTheme.mode == m, role = androidx.compose.ui.semantics.Role.RadioButton) {
                        com.photomed.app.ui.theme.AppTheme.set(context, m)
                    }.padding(horizontal = Md3Spacing.s6, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = com.photomed.app.ui.theme.AppTheme.mode == m, onClick = null)
                    Text(m.label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
            HorizontalDivider()
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
            // idioma de la interfaz (Android 13+: el mismo ajuste que «Idioma de la app» del sistema)
            SectionHeader(tr("Idioma"), Modifier.padding(horizontal = Md3Spacing.s6))
            val lang = remember { com.photomed.app.i18n.AppLanguage.current(context) }
            com.photomed.app.i18n.AppLanguage.entries.forEach { l ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()
                    .selectable(lang == l, role = androidx.compose.ui.semantics.Role.RadioButton) {
                        (context.findActivity())?.let { com.photomed.app.i18n.AppLanguage.set(it, l) }
                    }.padding(horizontal = Md3Spacing.s6, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = lang == l, onClick = null)
                    Text(tr(l.label), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
            HorizontalDivider()
            // sensibilidad de la detección de arrugas y surcos (ver docs/BENCHMARK.md)
            SectionHeader(tr("Detección de arrugas"), Modifier.padding(horizontal = Md3Spacing.s6))
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
            SectionHeader(tr("Alineación automática (comparar)"), Modifier.padding(horizontal = Md3Spacing.s6))
            listOf(
                Triple(tr("Usar puntos anatómicos"), tr("Ojos, cejas, nariz, boca, óvalo; articulaciones en el cuerpo."), AlignOptions.PREF_POINTS),
                Triple(tr("Usar contornos"), tr("Bordes anatómicos (mandíbula, frente, orejas, silueta) y perspectiva por proporciones."), AlignOptions.PREF_CONTOURS),
                Triple(tr("Usar mirada (gaze)"), tr("Comprueba que ambas fotos miren en la misma dirección."), AlignOptions.PREF_GAZE),
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
            // logo oficial para las imágenes exportadas (la posición se elige al exportar)
            SectionHeader(tr("Logo para exportar"), Modifier.padding(horizontal = Md3Spacing.s6))
            WatermarkSetting()
            HorizontalDivider()
            SectionHeader(tr("Ayudas"), Modifier.padding(horizontal = Md3Spacing.s6))
            ListItem(
                headlineContent = { Text(tr("Mostrar las ayudas de primera vez otra vez")) },
                supportingContent = { Text(tr("Indicaciones sobre gestos: pulsación larga, arrastrar, pellizcar…")) },
                modifier = Modifier.fillMaxWidth().clickable { com.photomed.app.ui.components.CoachMarks.resetAll(context) },
            )
            HorizontalDivider()
            SectionHeader(tr("Cámara"), Modifier.padding(horizontal = Md3Spacing.s6))
            ListItem(
                headlineContent = { Text(tr("Vista en espejo (cámara frontal)")) },
                supportingContent = { Text(tr("Como las cámaras del teléfono. Las fotos se guardan siempre sin espejar.")) },
                trailingContent = { Switch(checked = mirror, onCheckedChange = null) },
                modifier = Modifier.fillMaxWidth().toggleable(mirror, role = Role.Switch) {
                    mirror = it; prefs.edit().putBoolean("mirror_front", it).apply()
                },
            )
            HorizontalDivider()
            SectionHeader(tr("Registro de errores"), Modifier.padding(horizontal = Md3Spacing.s6))
            var logs by remember { mutableStateOf(com.photomed.app.CrashLog.all(context)) }
            ListItem(
                headlineContent = { Text(if (logs.isEmpty()) tr("Sin errores registrados") else tr("Compartir el registro (%1\$d)", logs.size)) },
                supportingContent = { Text(logs.firstOrNull()?.let { tr("Último: %1\$s", it.readLines().getOrNull(1)?.removePrefix("Fecha: ").orEmpty()) }
                    ?: tr("Si la app se cierra por un error, queda registrado aquí (sin fotos ni datos del paciente).")) },
                modifier = Modifier.fillMaxWidth().clickable(enabled = logs.isNotEmpty()) { com.photomed.app.CrashLog.share(context, logs) },
            )
            if (logs.isNotEmpty()) ListItem(
                headlineContent = { Text(tr("Borrar el registro")) },
                modifier = Modifier.fillMaxWidth().clickable { com.photomed.app.CrashLog.clear(context); logs = emptyList() },
            )
        }
    }
}

private fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c; c = c.baseContext }
    return null
}

/** Adjuntar, cambiar o quitar el logo de las exportaciones (PNG con transparencia recomendado). */
@Composable
private fun WatermarkSetting() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    val logo = remember(version) { com.photomed.app.export.Watermark.logo(context) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            error = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.photomed.app.export.Watermark.set(context, uri) }.exceptionOrNull()?.message
            }
            version++
        }
    }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.s6, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(width = 96.dp, height = 56.dp)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
                androidx.compose.material3.MaterialTheme.shapes.small),
            contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (logo != null) androidx.compose.foundation.Image(logo.asImageBitmap(), tr("Logo para exportar"),
                Modifier.padding(4.dp).fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            else Text(tr("Sin logo"), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(tr("Se añade a las fotos, cuadrículas, GIF y vídeos exportados. La esquina se elige al exportar."),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(onClick = { picker.launch("image/*") }) {
                    Text(if (logo == null) tr("Adjuntar logo") else tr("Cambiar"))
                }
                if (logo != null) androidx.compose.material3.TextButton(onClick = {
                    com.photomed.app.export.Watermark.clear(context); version++
                }) { Text(tr("Quitar")) }
            }
        }
    }
}
