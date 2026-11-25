package com.photomed.app.ui.compare

import com.photomed.app.i18n.trn
import com.photomed.app.i18n.tr
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
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
import com.photomed.app.ui.components.FullScreenOverlay
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Igualación del color base de la piel: brillo, contraste, gamma, temperatura y tinte de "después"
 * para que su piel coincida con la de "antes". Automático (piel del rostro / silueta) o por un
 * punto de referencia tocado en cada foto (p. ej. la misma zona de la mejilla).
 */
@Composable
fun ColorMatchDialog(before: Photo, after: Photo, onDismiss: () -> Unit, onApply: (ColorAdjust) -> Unit) {
    FullScreenOverlay(onDismiss = onDismiss) {
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
    /** Modo: piel automática, punto de piel o punto neutro (carta gris/blanca). */
    var mode by remember { mutableStateOf(ColorMode.AUTO) }
    var deltaE by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    var job by remember { mutableStateOf<Job?>(null) }
    // El último cálculo pedido gana: se cancela el anterior para no mostrar un resultado obsoleto.
    fun compute(auto: Boolean) { val prev = job; job = scope.launch {
        prev?.cancelAndJoin()
        val b = bitmaps[0] ?: return@launch; val a = bitmaps[1] ?: return@launch
        busy = true
        try {
            val m = if (auto) ColorMode.AUTO else mode
            val masks = if (m == ColorMode.AUTO) listOf(aligner.skinMask(before.zone, b), aligner.skinMask(after.zone, a))
            else listOf(points[0]?.let { ColorMatch.patch(b.width, b.height, it) }, points[1]?.let { ColorMatch.patch(a.width, a.height, it) })
            val res = withContext(Dispatchers.Default) {
                val sb = masks[0]?.let { ColorMatch.stats(b.rgb(), it) }
                val sa = masks[1]?.let { ColorMatch.stats(a.rgb(), it) }
                if (sb == null || sa == null) null else {
                    val adj = when (m) {
                        ColorMode.AUTO -> ColorMatch.fit(sa, sb)
                        ColorMode.SKIN_POINT -> ColorMatch.fit(sa, sb, tone = false)
                        ColorMode.NEUTRAL -> ColorMatch.fitNeutral(sa, sb)
                    }
                    val px = adj.apply(a.rgb())
                    // ΔE2000 de la región usada, antes y después del ajuste
                    val after2 = ColorMatch.stats(px, masks[1]!!)
                    val de = ColorMatch.deltaE(sa, sb) to (after2?.let { ColorMatch.deltaE(it, sb) } ?: Double.NaN)
                    Triple(adj, Bitmap.createBitmap(a.width, a.height, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, a.width, 0, 0, a.width, a.height) }, de)
                }
            }
            ensureActive()
            adjust = res?.first; preview = res?.second; deltaE = res?.third
            // punto de piel elegido a mano que no parece piel (ropa, sombra, fondo): se avisa
            val unnatural = m == ColorMode.SKIN_POINT && withContext(Dispatchers.Default) {
                listOf(b to masks[0], a to masks[1]).any { (bm, mk) -> mk != null && ColorMatch.stats(bm.rgb(), mk)?.let { st -> !ColorMatch.isNaturalSkin(st.meanL, st.meanA, st.meanB) } == true }
            }
            source = when {
                res != null && unnatural -> m.done + " · " + tr("el punto elegido no parece piel natural: elija piel sin sombra ni brillo")
                res != null -> m.done
                m == ColorMode.AUTO -> tr("no se encontró piel: use un punto de piel o un punto neutro")
                else -> m.ask
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            adjust = null; preview = null; deltaE = null
            source = tr("No se pudo analizar la imagen: use un punto de referencia")
        } finally { busy = false }
    } }

    LaunchedEffect(Unit) {
        try {
            bitmaps[0] = ImageOps.load34(context, before.uri, 360)
            bitmaps[1] = ImageOps.load34(context, after.uri, 360)
            compute(true)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            busy = false
            source = tr("No se pudo leer la fotografía (%1\$s)", e.message ?: e.javaClass.simpleName)
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Igualar color de piel")) },
            navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("Cerrar")) } })
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Md3Spacing.marginCompact),
            verticalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Box(Modifier.height(4.dp))
            Text(mode.help,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                listOf(tr("Antes"), tr("Después (ajustada)")).forEachIndexed { i, label ->
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small).background(Color.Black)
                                .pointerInput(i) {
                                    detectTapGestures { pos ->
                                        points[i] = Pt(pos.x / size.width, pos.y / size.width)
                                        if (mode == ColorMode.AUTO) mode = ColorMode.SKIN_POINT
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2)) {
                ColorMode.entries.forEach { cm ->
                    FilterChip(
                        selected = mode == cm,
                        onClick = {
                            job?.cancel(); mode = cm; points[0] = null; points[1] = null
                            if (cm == ColorMode.AUTO) compute(true) else { adjust = null; preview = null; deltaE = null; source = cm.ask }
                        },
                        label = { Text(cm.label) },
                        leadingIcon = { Icon(if (cm == ColorMode.AUTO) Icons.Default.AutoFixHigh else Icons.Default.Colorize, null) },
                    )
                }
            }
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            adjust?.let { Text(it.describe(), style = MaterialTheme.typography.bodyMedium) }
            deltaE?.let { (b0, a0) ->
                Text(tr("Diferencia de color ΔE2000: %1\$.1f → %2\$.1f  (≤ 2: apenas perceptible)", b0, a0),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = { adjust?.let(onApply) }, enabled = adjust != null, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Aplicar a la comparación"))
            }
            Box(Modifier.height(Md3Spacing.s8))
        }
    }
}

/** Igualación automática (piel detectada) de [target] respecto de [ref]; null si no se encuentra piel. */
suspend fun autoColorAdjust(context: android.content.Context, aligner: Aligner, ref: Photo, target: Photo): ColorAdjust? {
    val b = ImageOps.load34(context, ref.uri, 360)
    val a = ImageOps.load34(context, target.uri, 360)
    val mb = aligner.skinMask(ref.zone, b) ?: return null
    val ma = aligner.skinMask(target.zone, a) ?: return null
    return withContext(Dispatchers.Default) {
        val sa = ColorMatch.stats(a.rgb(), ma); val sb = ColorMatch.stats(b.rgb(), mb)
        if (sa != null && sb != null) ColorMatch.fit(sa, sb) else null
    }
}

/** Modos de igualación de color. */
enum class ColorMode(private val labelEs: String, private val helpEs: String, private val askEs: String, private val doneEs: String) {
    AUTO(trn("Automático (piel)"),
        trn("Piel del rostro (sin ojos, cejas, labios ni fosas nasales) o de la extremidad: balance de blancos y exposición (Bradford) y gradación tonal."),
        "", trn("piel detectada automáticamente")),
    SKIN_POINT(trn("Punto de piel"),
        trn("Toque la misma zona de piel en cada foto (p. ej. el centro de la mejilla)."),
        trn("toque la misma zona de piel en cada foto"), trn("puntos de piel")),
    NEUTRAL(trn("Punto neutro (carta gris)"),
        trn("Toque una referencia neutra en cada foto (carta gris o blanca, bata): corrige el balance de blancos y la exposición sin suponer nada de la piel. Es lo más fiable si se fotografía una carta."),
        trn("toque la carta gris o blanca en cada foto"), trn("punto neutro")),
    ;
    val label: String get() = tr(labelEs)
    val help: String get() = tr(helpEs)
    val ask: String get() = if (askEs.isEmpty()) "" else tr(askEs)
    val done: String get() = tr(doneEs)
}
