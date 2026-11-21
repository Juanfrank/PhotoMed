package com.photomed.app.ui.protocol

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.photomed.app.align.LiveMatch
import com.photomed.app.align.ProtocolReference
import com.photomed.app.data.FacialExpression
import com.photomed.app.data.Photo
import com.photomed.app.data.Protocol
import com.photomed.app.data.ProtocolStep
import com.photomed.app.data.ProtocolDefaults
import com.photomed.app.data.Protocols
import com.photomed.app.data.Zone
import com.photomed.app.data.expression
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Comparte un protocolo como archivo (`.photomed.json`). */
fun shareProtocol(context: Context, p: Protocol) {
    val f = Protocols.shareFile(context, p)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "Protocolo fotográfico: ${p.name}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir protocolo"))
}

/**
 * Crear o editar un protocolo: nombre, autor, notas y sus pasos (zona, gesto, cantidad, coincidencia,
 * luz, cámara, indicaciones y referencia sin imagen de una foto del expediente). Con [fromPhotos]
 * (nombres de fotos del expediente) se crea un paso por foto, con su referencia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolEditorScreen(expId: String, protocolId: String?, fromPhotos: List<String> = emptyList(), onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val original = remember(protocolId) { protocolId?.let { Protocols.get(context, it) } }
    var name by remember { mutableStateOf(original?.name ?: "") }
    var author by remember { mutableStateOf(original?.author ?: "") }
    var notes by remember { mutableStateOf(original?.notes ?: "") }
    val steps = remember { mutableStateListOf<ProtocolStep>().apply { original?.steps?.let { addAll(it) } } }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    /** Pasos cuya referencia se está calculando (índice → texto). */
    var busy by remember { mutableStateOf<String?>(null) }
    var pickRefFor by remember { mutableStateOf<Int?>(null) }
    var pickMany by remember { mutableStateOf(false) }

    suspend fun refFrom(p: Photo) = runCatching { ProtocolReference.fromPhoto(context, repo, p) }.getOrNull()

    // expediente de donde salen las fotos de referencia (desde la biblioteca se elige)
    var refExp by remember { mutableStateOf(expId.takeIf { it != "-" }) }
    var refExpName by remember { mutableStateOf<String?>(null) }
    var refExpObj by remember { mutableStateOf<com.photomed.app.data.Expediente?>(null) }
    var pickExp by remember { mutableStateOf(false) }
    LaunchedEffect(refExp) {
        val e = refExp?.let { withContext(Dispatchers.IO) { repo.getExpediente(it) } }
        refExpName = e?.patientName; refExpObj = e
        photos = withContext(Dispatchers.IO) { e?.let { repo.photos(it) }.orEmpty() }
        // desde fotos: un paso por foto (zona, gesto) con su referencia
        if (original == null && fromPhotos.isNotEmpty() && steps.isEmpty()) {
            val chosen = fromPhotos.mapNotNull { n -> photos.firstOrNull { it.displayName == n } }
            if (name.isBlank()) name = "Protocolo del ${formatDate(System.currentTimeMillis())}"
            chosen.forEachIndexed { i, p ->
                busy = "Referencia ${i + 1} de ${chosen.size}…"
                steps += ProtocolDefaults.step(p.zone, p.expression, refFrom(p), LiveMatch.DEFAULT_THRESHOLD)
            }
            busy = null
        }
    }
    // tomas plegadas: las que ya tenía el protocolo empiezan plegadas; las nuevas, abiertas
    val initialIds = remember { steps.map { it.id }.toSet() }
    val open = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    fun isOpen(s: ProtocolStep) = open[s.id] ?: (s.id !in initialIds)

    fun current() = (original ?: Protocol(name = name)).copy(name = name.ifBlank { "Protocolo" }, author = author, notes = notes, steps = steps.toList())
    fun save(): Protocol? {
        if (steps.isEmpty()) { scope.launch { snackbar.showSnackbar("Añada al menos una toma") }; return null }
        return current().also { Protocols.save(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Nuevo protocolo" else "Editar protocolo") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = { save()?.let { shareProtocol(context, it) } }, enabled = busy == null) { Icon(Icons.Default.Share, "Guardar y compartir el protocolo") }
                    IconButton(onClick = { if (save() != null) onBack() }, enabled = busy == null) { Icon(Icons.Default.Check, "Guardar el protocolo") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nombre del protocolo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it.take(80) }, label = { Text("Autor (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("Notas e indicaciones generales (opcional)") }, modifier = Modifier.fillMaxWidth())
                busy?.let { Column(Modifier.padding(top = 8.dp)) { Text(it, style = MaterialTheme.typography.bodySmall); LinearProgressIndicator(Modifier.fillMaxWidth()) } }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    refExpObj?.let { PatientThumb(it, 32.dp); Spacer(Modifier.width(8.dp)) }
                    Text("Fotos de referencia: " + (refExpName ?: "ningún expediente"), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { pickExp = true }) { Text(if (refExp == null) "Elegir expediente" else "Cambiar") }
                }
                Text("Tomas en orden (${steps.sumOf { it.count }} fotos)", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(steps, key = { _, s -> s.id }) { i, s ->
                val refPhoto = s.ref?.sourceTakenAt?.let { t -> photos.firstOrNull { it.takenAt == t && it.zone == s.zone } }
                StepCard(i, s, steps.size, expanded = isOpen(s), onExpand = { open[s.id] = it }, refPhoto = refPhoto, onChange = { steps[i] = it },
                    onMove = { d -> val j = i + d; if (j in steps.indices) { val t = steps[i]; steps[i] = steps[j]; steps[j] = t } },
                    onDelete = { steps.removeAt(i) }, onPickRef = { pickRefFor = i })
            }
            item {
                OutlinedButton(onClick = { steps += ProtocolDefaults.step(steps.lastOrNull()?.zone ?: Zone.FACE_FRONTAL, threshold = LiveMatch.DEFAULT_THRESHOLD) },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Añadir toma")
                }
                OutlinedButton(onClick = { pickMany = true }, enabled = photos.isNotEmpty() && busy == null, modifier = Modifier.fillMaxWidth()) {
                    Text("Añadir tomas desde fotos del expediente")
                }
                Text("La referencia de una toma guarda solo puntos, pose, contornos y surcos de la foto elegida (no la imagen): " +
                    "quien use el protocolo deberá coincidir con ella.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (pickExp) ExpedientePicker("Fotos de referencia de…", onDismiss = { pickExp = false }) { e -> pickExp = false; refExp = e.id }
    if (pickMany) {
        val chosen = remember { mutableStateListOf<String>() }
        AlertDialog(onDismissRequest = { pickMany = false },
            title = { Text("Tomas desde fotos") },
            text = {
                Column {
                    Text("Cada foto elegida será una toma con su zona, su gesto y su referencia (sin la imagen).",
                        style = MaterialTheme.typography.bodySmall)
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(340.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(photos.sortedWith(compareBy({ it.zone.ordinal }, { it.takenAt })), key = { it.key }) { p ->
                            val k = chosen.indexOf(p.key)
                            Box(Modifier.clickable { if (k >= 0) chosen.remove(p.key) else chosen += p.key }
                                .semantics { contentDescription = "${p.zone.label}, ${formatDate(p.takenAt)}" + if (k >= 0) ", elegida ${k + 1}" else "" }) {
                                AsyncImage(com.photomed.app.ui.photoModel(context, p.uri), null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
                                Text(if (k >= 0) "${k + 1}" else p.zone.label, style = MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.ui.graphics.Color.White, maxLines = 2,
                                    modifier = Modifier.align(Alignment.BottomStart)
                                        .background(if (k >= 0) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                                        .padding(horizontal = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = chosen.isNotEmpty(), onClick = {
                    pickMany = false
                    val sel = chosen.mapNotNull { k -> photos.firstOrNull { it.key == k } }
                    scope.launch {
                        sel.forEachIndexed { i, p ->
                            busy = "Referencia ${i + 1} de ${sel.size}…"
                            steps += ProtocolDefaults.step(p.zone, p.expression, refFrom(p), LiveMatch.DEFAULT_THRESHOLD)
                        }
                        busy = null
                    }
                }) { Text("Añadir (${chosen.size})") }
            },
            dismissButton = { TextButton(onClick = { pickMany = false }) { Text("Cancelar") } })
    }
    pickRefFor?.let { i ->
        val s = steps.getOrNull(i) ?: run { pickRefFor = null; return@let }
        val candidates = photos.filter { it.zone == s.zone }.sortedByDescending { it.takenAt }
        AlertDialog(onDismissRequest = { pickRefFor = null },
            title = { Text("Referencia: ${s.zone.label}") },
            text = {
                if (candidates.isEmpty()) Text("No hay fotos de esta zona en el expediente.")
                else LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(320.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(candidates, key = { it.key }) { p ->
                        Column(Modifier.clickable {
                            pickRefFor = null
                            scope.launch {
                                busy = "Calculando la referencia…"
                                val r = refFrom(p)
                                busy = null
                                if (r == null) snackbar.showSnackbar("No se encontraron puntos ni contornos en esa foto")
                                else steps.indexOfFirst { it.id == s.id }.takeIf { it >= 0 }?.let { k ->
                                    // la luz pedida pasa a ser la de la foto de referencia
                                    steps[k] = ProtocolDefaults.withRefLight(steps[k].copy(ref = r,
                                        expression = p.expression.takeIf { e -> s.zone.guide in e.guides } ?: steps[k].expression))
                                }
                            }
                        }.semantics { contentDescription = "Foto del ${formatDate(p.takenAt)}" }) {
                            AsyncImage(com.photomed.app.ui.photoModel(context, p.uri), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
                            Text(formatDate(p.takenAt) + (p.expression.takeIf { it != FacialExpression.NEUTRAL }?.let { " · ${it.label}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickRefFor = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun StepCard(i: Int, s: ProtocolStep, total: Int, expanded: Boolean, onExpand: (Boolean) -> Unit, refPhoto: Photo?,
                     onChange: (ProtocolStep) -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit, onPickRef: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onExpand(!expanded) }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (expanded) "Plegar la toma ${i + 1}" else "Desplegar la toma ${i + 1}")
                }
                if (s.ref != null && !expanded) { RefThumb(s, refPhoto, 36.dp); Spacer(Modifier.width(8.dp)) }
                Column(Modifier.weight(1f).clickable { onExpand(!expanded) }) {
                    Text("${i + 1}. ${s.title}", style = MaterialTheme.typography.titleSmall)
                    if (!expanded) Text(stepSummary(s), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                IconButton(onClick = { onMove(-1) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, "Subir la toma ${i + 1}") }
                IconButton(onClick = { onMove(1) }, enabled = i < total - 1) { Icon(Icons.Default.ArrowDownward, "Bajar la toma ${i + 1}") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Quitar la toma ${i + 1}", tint = MaterialTheme.colorScheme.error) }
            }
            if (!expanded) return@Column
            // zona (ángulo)
            var zoneMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { zoneMenu = true }) { Text("Zona: ${s.zone.label}") }
                DropdownMenu(zoneMenu, { zoneMenu = false }) {
                    Zone.entries.forEach { z ->
                        DropdownMenuItem(text = { Text("${z.category.label} · ${z.label}") }, onClick = {
                            zoneMenu = false
                            onChange(s.copy(zone = z, ref = null, expression = s.expression.takeIf { z.guide in it.guides } ?: FacialExpression.NEUTRAL))
                        })
                    }
                }
            }
            // gesto facial
            val exprs = FacialExpression.forZone(s.zone)
            if (exprs.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                exprs.forEach { e -> FilterChip(selected = s.expression == e, onClick = { onChange(s.copy(expression = e)) }, label = { Text(e.label) }) }
            }
            // cantidad
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fotos", modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(s.copy(count = (s.count - 1).coerceAtLeast(1))) }) { Text("−") }
                Text("${s.count}", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onChange(s.copy(count = (s.count + 1).coerceAtMost(20))) }) { Text("+") }
            }
            val thr = s.threshold ?: LiveMatch.DEFAULT_THRESHOLD
            Text("Coincidencia mínima: ${(thr * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
            Slider(thr * 100, { onChange(s.copy(threshold = it.toInt() / 100f)) }, valueRange = 50f..100f,
                modifier = Modifier.semantics { contentDescription = "Coincidencia mínima de la toma ${i + 1}" })
            val minL = s.minLight ?: 0f
            Text(if (minL <= 0f) "Luz mínima en el rostro: sin exigir" else "Luz mínima en el rostro: ${(minL * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
            Slider(minL * 100, { onChange(s.copy(minLight = (it.toInt() / 100f).takeIf { v -> v > 0f })) }, valueRange = 0f..80f,
                modifier = Modifier.semantics { contentDescription = "Luz mínima de la toma ${i + 1}" })
            val asym = s.maxLightAsym ?: 0f
            Text(if (asym <= 0f) "Luz pareja entre mejillas: sin exigir" else "Diferencia de luz entre mejillas: máx. ${(asym * 100).toInt()} %",
                style = MaterialTheme.typography.bodyMedium)
            Slider(asym * 100, { onChange(s.copy(maxLightAsym = (it.toInt() / 100f).takeIf { v -> v > 0f })) }, valueRange = 0f..40f,
                modifier = Modifier.semantics { contentDescription = "Diferencia máxima de luz de la toma ${i + 1}" })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Disparo automático al coincidir", Modifier.weight(1f))
                Switch(s.autoCapture != false, { onChange(s.copy(autoCapture = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mirada al frente", Modifier.weight(1f))
                Switch(s.gaze != false, { onChange(s.copy(gaze = it)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cámara")
                listOf(null to "Cualquiera", "back" to "Trasera", "front" to "Frontal").forEach { (k, l) ->
                    FilterChip(selected = s.lens == k, onClick = { onChange(s.copy(lens = k)) }, label = { Text(l) })
                }
            }
            OutlinedTextField(s.instructions.orEmpty(), { onChange(s.copy(instructions = it.take(300).ifBlank { null })) },
                label = { Text("Indicaciones (opcional)") }, modifier = Modifier.fillMaxWidth())
            // referencia sin imagen
            val r = s.ref
            if (r != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RefThumb(s, refPhoto, 96.dp)
                r.light?.let { l -> Text("Luz de la foto: %.0f %%".format(l * 100) + (r.lightAsym?.let { " · diferencia entre mejillas %.0f %%".format(it * 100) } ?: "") +
                    "\nLa luz pedida se ajustó a la de la referencia.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
            }
            Text(if (r == null) "Sin referencia: se estandariza la toma (pose, luz, gesto)"
                else "Referencia" + (r.sourceTakenAt?.let { " del ${formatDate(it)}" } ?: "") +
                    ": ${r.points.size} puntos, ${r.lines.size} contornos y surcos" + (r.distanceCm?.let { " · %.0f cm".format(it) } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickRef) { Text(if (r == null) "Usar una foto como referencia" else "Cambiar la referencia") }
                if (r != null) TextButton(onClick = { onChange(s.copy(ref = null)) }) { Text("Quitar") }
            }
        }
    }
}

/** Resumen de una toma plegada: fotos, coincidencia, luz, disparo y referencia. */
private fun stepSummary(s: ProtocolStep): String = listOfNotNull(
    "${s.count} foto" + if (s.count != 1) "s" else "",
    "coincidencia ${((s.threshold ?: LiveMatch.DEFAULT_THRESHOLD) * 100).toInt()} %",
    s.minLight?.let { "luz ≥ ${(it * 100).toInt()} %" },
    s.maxLightAsym?.let { "mejillas ≤ ${(it * 100).toInt()} %" },
    if (s.autoCapture != false) "disparo automático" else null,
    if (s.ref != null) "con referencia" else null,
).joinToString(" · ")

/**
 * Miniatura de la referencia de una toma: la foto de la que sale si está en el expediente elegido y,
 * si no (protocolo importado), un esquema con sus contornos, surcos y puntos.
 */
@Composable
private fun RefThumb(s: ProtocolStep, photo: Photo?, width: androidx.compose.ui.unit.Dp) {
    val r = s.ref ?: return
    val context = LocalContext.current
    Box(Modifier.width(width).aspectRatio(3f / 4f).androidxClip().background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .semantics { contentDescription = "Miniatura de la referencia de ${s.title}" }) {
        if (photo != null) AsyncImage(com.photomed.app.ui.photoModel(context, photo.uri), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        val line = MaterialTheme.colorScheme.primary
        val dot = MaterialTheme.colorScheme.tertiary
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            fun o(p: com.photomed.app.align.Pt) = androidx.compose.ui.geometry.Offset(p.x * w, p.y * w)
            val sw = (w / 60f).coerceAtLeast(1f)
            for (l in r.lines) {
                val pts = l.points
                for (k in 1 until pts.size) drawLine(line, o(pts[k - 1]), o(pts[k]), sw)
                if (l.closed && pts.size > 2) drawLine(line, o(pts.last()), o(pts.first()), sw)
            }
            r.hairline?.let { h -> for (k in 1 until h.size) drawLine(line.copy(alpha = 0.7f), o(h[k - 1]), o(h[k]), sw) }
            if (r.lines.isEmpty()) r.points.values.forEach { drawCircle(dot, sw * 1.2f, o(it)) }
        }
    }
}

private fun Modifier.androidxClip() = this.then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)))
