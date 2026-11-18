package com.photomed.app.ui.compare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.photomed.app.align.Pt
import com.photomed.app.align.ZoneAlignment
import com.photomed.app.data.Photo
import com.photomed.app.ui.theme.ImmersiveTheme
import com.photomed.app.ui.theme.Md3Spacing

/**
 * Alineación manual guiada: el usuario marca los mismos 2 puntos anatómicos de la zona
 * (p. ej. ambas pupilas) primero en "antes" y luego en "después".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAlignDialog(
    before: Photo,
    after: Photo,
    onDismiss: () -> Unit,
    onDone: (beforePts: List<Pt>, afterPts: List<Pt>) -> Unit,
) {
    val names = ZoneAlignment.of(before.zone).manual
    val points = remember { mutableStateListOf<Pt>() }
    val step = points.size
    val onBefore = step < 2
    val photo = if (onBefore) before else after
    val colors = listOf(Color(0xFFFFD54F), Color(0xFF4FC3F7))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Alineación manual") },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") } },
                    )
                },
            ) { padding ->
                Column(
                    Modifier.padding(padding).fillMaxSize().navigationBarsPadding().padding(Md3Spacing.marginCompact),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Md3Spacing.s3),
                ) {
                    LinearProgressIndicator(progress = { step / 4f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (step < 4) "Paso ${step + 1} de 4 · Foto ${if (onBefore) "ANTES" else "DESPUÉS"}" else "Listo",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (step < 4) "Toque: ${names[step % 2]}" else "Se calculará la alineación y se afinará por contornos.",
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    )
                    Box(
                        Modifier.weight(1f).aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium).background(Color.Black)
                            .pointerInput(step) {
                                detectTapGestures { pos ->
                                    if (points.size < 4) points += Pt(pos.x / size.width, pos.y / size.width)
                                }
                            },
                    ) {
                        AsyncImage(photo.uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize()) {
                            val visible = if (onBefore) points.take(2) else points.drop(2)
                            visible.forEachIndexed { i, p ->
                                val c = Offset(p.x * size.width, p.y * size.width)
                                drawCircle(Color.Black.copy(alpha = 0.5f), 14.dp.toPx(), c, style = Stroke(5.dp.toPx()))
                                drawCircle(colors[i], 14.dp.toPx(), c, style = Stroke(2.5f.dp.toPx()))
                                drawLine(colors[i], c - Offset(6.dp.toPx(), 0f), c + Offset(6.dp.toPx(), 0f), 2.dp.toPx())
                                drawLine(colors[i], c - Offset(0f, 6.dp.toPx()), c + Offset(0f, 6.dp.toPx()), 2.dp.toPx())
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s3)) {
                        OutlinedButton(onClick = { if (points.isNotEmpty()) points.removeAt(points.lastIndex) }, enabled = points.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null)
                            Text("  Deshacer")
                        }
                        Button(onClick = { onDone(points.take(2), points.drop(2)) }, enabled = points.size == 4) {
                            Text("Alinear")
                        }
                    }
                }
            }
        }
    }
}
