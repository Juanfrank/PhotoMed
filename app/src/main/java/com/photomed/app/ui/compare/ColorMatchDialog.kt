package com.photomed.app.ui.compare

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.photomed.app.align.Aligner
import com.photomed.app.align.ColorAdjust
import com.photomed.app.align.ColorMatch
import com.photomed.app.align.ImageOps
import com.photomed.app.align.Pt
import com.photomed.app.align.rgb
import com.photomed.app.data.Photo
import com.photomed.app.ui.theme.ImmersiveTheme
import com.photomed.app.ui.theme.Md3Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Igualación del color base de la piel: brillo, contraste, gamma, temperatura y tinte de "después"
 * para que su piel coincida con la de "antes". Automático (piel del rostro / silueta) o por un
 * punto de referencia tocado en cada foto (p. ej. la misma zona de la mejilla).
 */
@Composable
fun ColorMatchDialog(before: Photo, after: Photo, onDismiss: () -> Unit, onApply: (ColorAdjust) -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersiveTheme { ColorMatchContent(before, after, onDismiss, onApply) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorMatchContent(before: Photo, after: Photo, onDismiss: () -> Unit, onApply: (ColorAdjust) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aligner = remember { Aligner(context.applicationContext) }
    val bitmaps = remember { mutableStateListOf<Bitmap?>(null, null) }
    val points = remember { mutableStateListOf<Pt?>(null, null) }
    var adjust by remember { mutableStateOf<ColorAdjust?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(true) }
    var source by remember { mutableStateOf("") }

    fun compute(auto: Boolean) = scope.launch {
        val b = bitmaps[0] ?: return@launch; val a = bitmaps[1] ?: return@launch
        busy = true
        val masks = if (auto) listOf(aligner.skinMask(before.zone, b), aligner.skinMask(after.zone, a))
        else listOf(points[0]?.let { ColorMatch.patch(b.width, b.height, it) }, points[1]?.let { ColorMatch.patch(a.width, a.height, it) })
        withContext(Dispatchers.Default) {
            val sb = masks[0]?.let { ColorMatch.stats(b.rgb(), it) }
            val sa = masks[1]?.let { ColorMatch.stats(a.rgb(), it) }
            if (sb != null && sa != null) {
                val adj = ColorMatch.fit(sa, sb)
                adjust = adj
                val px = adj.apply(a.rgb())
                preview = Bitmap.createBitmap(a.width, a.height, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, a.width, 0, 0, a.width, a.height) }
                source = if (auto) "piel detectada automáticamente" else "puntos de referencia"
            } else {
                adjust = null; preview = null
                source = if (auto) "no se encontró piel: toque un punto de referencia en cada foto" else "toque un punto en cada foto"
            }
        }
        busy = false
    }

    LaunchedEffect(Unit) {
        bitmaps[0] = ImageOps.load34(context, before.uri, 360)
        bitmaps[1] = ImageOps.load34(context, after.uri, 360)
        compute(true)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Igualar color de piel") },
            navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") } })
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Md3Spacing.marginCompact),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Box(Modifier.height(4.dp))
            Text("Toque la misma zona de piel en cada foto para usarla como punto de referencia, o use la detección automática.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                listOf("Antes", "Después (ajustada)").forEachIndexed { i, label ->
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small).background(Color.Black)
                                .pointerInput(i) {
                                    detectTapGestures { pos ->
                                        points[i] = Pt(pos.x / size.width, pos.y / size.width)
                                        if (points[0] != null && points[1] != null) compute(false)
                                    }
                                },
                        ) {
                            val bmp = if (i == 1) preview ?: bitmaps[1] else bitmaps[0]
                            bmp?.let { Image(it.asImageBitmap(), label, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
                            points[i]?.let { p ->
                                Canvas(Modifier.fillMaxSize()) {
                                    val c = Offset(p.x * size.width, p.y * size.width)
                                    drawCircle(Color.Black, 0.04f * size.width + 2, c, style = Stroke(4f))
                                    drawCircle(Color(0xFFFFD54F), 0.04f * size.width, c, style = Stroke(2.5f))
                                }
                            }
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                AssistChip(onClick = { points[0] = null; points[1] = null; compute(true) }, label = { Text("Automático (piel)") },
                    leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) })
                AssistChip(onClick = { points[0] = null; points[1] = null; adjust = null; preview = null; source = "toque un punto en cada foto" },
                    label = { Text("Punto de referencia") }, leadingIcon = { Icon(Icons.Default.Colorize, null) })
            }
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            adjust?.let { Text(it.describe(), style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = { adjust?.let(onApply) }, enabled = adjust != null, modifier = Modifier.fillMaxWidth()) {
                Text("Aplicar a la comparación")
            }
            Box(Modifier.height(Md3Spacing.s8))
        }
    }
}
