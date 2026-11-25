package com.photomed.app.ui.gallery

import com.photomed.app.align.isFaceZone
import com.photomed.app.ui.components.detectTapPassThrough
import com.photomed.app.ui.components.detectZoomPanPassThrough
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.ui.graphics.asImageBitmap
import com.photomed.app.ui.camera.drawAnnotations
import com.photomed.app.ui.camera.drawMeasureGuides
import com.photomed.app.ui.camera.drawInk
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import com.photomed.app.ui.components.SafeBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.photomed.app.data.Photo
import com.photomed.app.data.expression
import com.photomed.app.data.PhotoInfo
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.components.ConfirmDialog
import com.photomed.app.ui.components.rememberGalleryDelete
import com.photomed.app.ui.formatDateTime
import com.photomed.app.ui.theme.ImmersiveTheme
import com.photomed.app.i18n.tr
import com.photomed.app.i18n.trn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Revisión tras la captura: la foto recién tomada, con Guardar (se queda) o Repetir (se elimina). */
class ReviewActions(val onKeep: () -> Unit, val onRetry: () -> Unit)

/**
 * Visor de fotos de una zona. [onCompare]: abre Comparar con las fotos dadas (nombres). [review]:
 * modo revisión tras la captura (solo la foto recién tomada, con Guardar y Repetir).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(expId: String, zone: Zone, initialName: String, onBack: () -> Unit,
                      onCompare: ((List<String>) -> Unit)? = null, review: ReviewActions? = null) = ImmersiveTheme {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var photos by remember { mutableStateOf<List<Photo>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<Photo, PhotoInfo?>?>(null) }
    // capas de anotación y edición no destructiva (giro, espejo, recorte)
    val layersState = com.photomed.app.ui.compare.rememberAnnotLayers()
    // capas (contorno, rasgos, surcos, arrugas) solo en las zonas del rostro: en manos, pies o cuerpo no
    // hay nada facial que analizar
    val showLayers = zone.isFaceZone()
    // manos, pies, tronco y miembros: su diagrama (contorno, ejes, dedos, uñas, puntos de referencia), sin medidas
    val bodyDiagram = !zone.isFaceZone() && zone != com.photomed.app.data.Zone.FREE
    // cuero cabelludo (vértice, coronilla, parietales): densidad aparente con su ficha de medidas
    val hairMetrics = bodyDiagram && zone.category == com.photomed.app.data.ZoneCategory.CAPILAR
    var editing by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf(false) }
    var editVersion by remember { mutableIntStateOf(0) }
    // línea tocada (ficha de medidas) y tabla de medidas
    var selected by remember { mutableStateOf<com.photomed.app.align.AnnotLine?>(null) }
    // fila de la tabla tocada: la foto se desplaza para que la línea se vea
    var focusTick by remember { mutableIntStateOf(0) }
    // tramo seleccionado en la tabla (fila de la ficha), o null para la línea entera
    var selectedKey by remember { mutableStateOf<String?>(null) }
    // capas de la vista y anotaciones (dibujo de planificación)
    val viewLayers = com.photomed.app.ui.compare.rememberViewLayers()
    var inking by remember { mutableStateOf(false) }
    // pantalla completa: solo la foto (sin barras de la app ni del sistema)
    var fullscreen by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(fullscreen) {
        val w = (view.context as? android.app.Activity)?.window
        val ctl = w?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (fullscreen) { ctl?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
        else ctl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { ctl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    var inkStyle by remember { mutableStateOf(InkStyle()) }
    /** Línea multipunto en curso (foto editada, unidades del ancho) y alto / ancho de esa foto. */
    var polyDraft by remember { mutableStateOf<List<com.photomed.app.align.Pt>>(emptyList()) }
    var polyAspect by remember { mutableStateOf(1f) }
    val polyMode = inking && inkStyle.tool == InkTool.SYMBOL && inkStyle.symbol == com.photomed.app.align.InkRenderer.POLYLINE
    // fuera del modo (otra herramienta, otra foto o sin anotar): el borrador se descarta
    LaunchedEffect(polyMode) { if (!polyMode) polyDraft = emptyList() }
    var inkVersion by remember { mutableIntStateOf(0) }
    val inkUndo = remember { androidx.compose.runtime.mutableStateMapOf<String, List<com.photomed.app.align.InkSet>>() }
    var metricsSheet by remember { mutableStateOf(false) }
    // horizontal o tableta apaisada: la tabla de medidas va a la derecha de la foto
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = conf.screenWidthDp >= 600 && conf.screenWidthDp > conf.screenHeightDp
    // correcciones del usuario: versión (vuelve a analizar) y modo «trazar surco»
    var userVersion by remember { mutableIntStateOf(0) }
    var tracing by remember { mutableStateOf(false) }
    // trazado a mano: null = añadir un surco nuevo; nombre = volver a trazar esa línea
    var traceTarget by remember { mutableStateOf<String?>(null) }
    var traceBusy by remember { mutableStateOf(false) }
    var confirmRegen by remember { mutableStateOf(false) }
    // si el último guardado pudo escribirse en el XMP de la foto
    val savedInPhoto = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    // opacidad de los trazos y números (se recuerda; empieza en 50 %)
    val annotAlpha = com.photomed.app.ui.compare.rememberAnnotAlpha()
    // líneas analizadas de cada foto (para la tabla) y motivo si no hay medidas
    val annotsByUri = remember { androidx.compose.runtime.mutableStateMapOf<String, List<com.photomed.app.align.AnnotLine>>() }
    val notesByUri = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }

    // nombre de la foto mostrada (cambia si se edita su fecha: el nombre lleva la fecha)
    var trackName by remember { mutableStateOf(initialName) }
    var editDate by remember { mutableStateOf<Photo?>(null) }
    LaunchedEffect(reload) {
        photos = withContext(Dispatchers.IO) { repo.getExpediente(expId)?.let { repo.photos(it, zone) }.orEmpty() }
        if (photos.isNullOrEmpty() || (review != null && photos.orEmpty().none { it.displayName == trackName })) onBack()
    }
    // en revisión: solo la foto recién tomada (las anteriores siguen sirviendo de referencia)
    val all = photos ?: return@ImmersiveTheme
    val list = if (review != null) all.filter { it.displayName == trackName } else all
    if (list.isEmpty()) return@ImmersiveTheme
    val pager = rememberPagerState(initialPage = list.indexOfFirst { it.displayName == initialName }.coerceAtLeast(0)) { list.size }
    // tras cambiar la fecha, la lista se reordena: se vuelve a la misma foto
    LaunchedEffect(list) { list.indexOfFirst { it.displayName == trackName }.takeIf { it >= 0 && it != pager.currentPage }?.let { pager.scrollToPage(it) } }
    val current = list[pager.currentPage.coerceIn(0, list.lastIndex)]
    var retrying by remember { mutableStateOf(false) }
    // fotos cuyo borrado espera la confirmación del sistema: al aceptarla se completa y se limpian sus datos
    var pendingDelete by remember { mutableStateOf<List<Photo>>(emptyList()) }
    val galleryDelete = rememberGalleryDelete { ok ->
        val del = pendingDelete; pendingDelete = emptyList()
        if (ok && del.isNotEmpty()) scope.launch { withContext(Dispatchers.IO) { runCatching { repo.deletePhotos(del) } } }
        if (retrying) { retrying = false; if (ok) review?.onRetry?.invoke() } else if (ok) reload++
    }
    if (review != null) androidx.activity.compose.BackHandler(onBack = review.onKeep)
    // Atrás sale primero de los modos de dibujo (anotar, trazar surco)
    androidx.activity.compose.BackHandler(enabled = inking || tracing || fullscreen) { if (fullscreen) fullscreen = false else { inking = false; tracing = false } }
    /** Comparar: la foto actual y la anterior con la misma expresión (si la hay). */
    /** Cambia los trazos confirmados de [ph] (si no los hay, primero se confirman los mostrados) y los guarda. */
    fun mutateTraces(ph: Photo, change: (com.photomed.app.align.TraceSet) -> com.photomed.app.align.TraceSet) {
        val u = ph.uri.toString()
        scope.launch {
            val ok = withContext(Dispatchers.IO) { com.photomed.app.data.PhotoLocks.on(u) {
                val set = com.photomed.app.align.ConfirmedTraces.ensure(context, ph.uri, annotsByUri[u].orEmpty(), com.photomed.app.align.TraceFrame.FULL)
                com.photomed.app.align.ConfirmedTraces.save(context, u, change(set))
            } }
            savedInPhoto[u] = ok
            userVersion++
        }
    }

    /** Trazo a mano alzada ([path] en la foto con su edición, unidades del ancho): surco nuevo o [target] de nuevo. */
    fun tracePath(ph: Photo, path: List<com.photomed.app.align.Pt>, target: String?) {
        val u = ph.uri.toString()
        scope.launch {
            traceBusy = true
            try {
                val e = com.photomed.app.align.PhotoEdits.of(context, u)
                val out = withContext(Dispatchers.Default) { runCatching {
                    val bmp = com.photomed.app.align.ImageOps.loadFull(context, ph.uri, 1600, e)
                    val r = com.photomed.app.align.FreehandTrace.trace(bmp, path) ?: return@runCatching null
                    val m = if (!com.photomed.app.align.PhotoAnnotator.enabled) null else com.photomed.app.align.PhotoAnnotator(context.applicationContext)
                        .measureTrace(bmp, zone, com.photomed.app.align.PhotoAnnotator.extrasFor(context, ph.uri, 1600, crop34 = false), r.points)
                    Triple(r, m, bmp.height / bmp.width.toFloat())
                }.getOrNull() }
                if (out == null) { snackbar.showSnackbar(tr("No se pudo trazar ahí; dibuje una línea sobre el surco o rodéelo")); return@launch }
                val (r, m, aE) = out
                val ok = withContext(Dispatchers.IO) { com.photomed.app.data.PhotoLocks.on(u) {
                    val set = com.photomed.app.align.ConfirmedTraces.of(context, u)
                        ?: com.photomed.app.align.ConfirmedTraces.fromLines(annotsByUri[u].orEmpty(), e, aE, com.photomed.app.align.TraceFrame.FULL)
                    val orig = com.photomed.app.align.TraceMap.fromView(e, set.baseAspect, com.photomed.app.align.TraceFrame.FULL, r.points)
                    val note = if (r.snapped) "Trazado a mano (ajustado a la sombra de la piel)" else "Trazado a mano (sin sombra clara: se usa el trazo)"
                    val next = if (target != null && set.byName(target) != null) com.photomed.app.align.ConfirmedTraces.replace(set, target, orig, m)
                        else {
                            val name = com.photomed.app.align.ConfirmedTraces.nextAddedName(set)
                            com.photomed.app.align.ConfirmedTraces.add(set, com.photomed.app.align.StoredTrace(com.photomed.app.align.AnnotLayer.FOLDS, orig,
                                closed = false, filled = false, estimated = false,
                                metrics = (m ?: com.photomed.app.align.LineMetrics("x")).copy(name = name, note = note), origin = "añadido"))
                        }
                    com.photomed.app.align.ConfirmedTraces.save(context, u, next)
                } }
                savedInPhoto[u] = ok
                if (com.photomed.app.align.AnnotLayer.FOLDS !in layersState.value) layersState.value = layersState.value + com.photomed.app.align.AnnotLayer.FOLDS
                userVersion++
                snackbar.showSnackbar((if (target != null) tr("«%1\$s» trazado de nuevo", target) else tr("Surco añadido")) +
                    (if (!r.snapped) tr(" (se usó su trazo: no se halló una sombra clara)") else "") +
                    (if (!ok) tr(" · guardado en la app (no se pudo escribir en la foto)") else ""))
            } finally { traceBusy = false }
        }
    }

    /** Trazo de anotación ([path] en la foto editada, unidades del ancho; [aE]: alto / ancho de la editada). */
    fun inkPath(ph: Photo, path: List<com.photomed.app.align.Pt>, aE: Float) {
        val u = ph.uri.toString(); val st = inkStyle
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { com.photomed.app.data.PhotoLocks.on(u) {
                val e = com.photomed.app.align.PhotoEdits.of(context, u)
                val old = com.photomed.app.align.Annotations.of(context, u) ?: com.photomed.app.align.InkSet(com.photomed.app.align.TraceMap.baseAspect(e, aE))
                val fr = com.photomed.app.align.TraceFrame.FULL
                val orig = com.photomed.app.align.TraceMap.fromView(e, old.baseAspect, fr, path)
                // grosor: del ancho de la foto editada al de la original
                val (a, b) = com.photomed.app.align.TraceMap.fromView(e, old.baseAspect, fr, listOf(path[0], com.photomed.app.align.Pt(path[0].x + st.width, path[0].y)))
                val wOrig = kotlin.math.hypot(b.x - a.x, (b.y - a.y) * old.baseAspect)
                val next = when (st.tool) {
                    InkTool.PEN -> old.copy(strokes = old.strokes + com.photomed.app.align.InkStroke(st.color, wOrig, orig))
                    InkTool.SYMBOL -> {
                        // un toque: marcador del tamaño elegido; arrastrando: de un extremo al otro
                        val dragged = path.maxOf { kotlin.math.hypot(it.x - path[0].x, it.y - path[0].y) } > 0.02f
                        old.copy(strokes = old.strokes + com.photomed.app.align.InkStroke(st.color, wOrig,
                            if (dragged) listOf(orig.first(), orig.last()) else listOf(orig.last()),
                            kind = com.photomed.app.align.InkStroke.SYMBOL, shape = st.symbol, text = st.text.trim().ifEmpty { null },
                            side = if (st.side == "auto") "auto" else sideInOriginal(st.side, e), size = wOrig * 6f))
                    }
                    InkTool.SELECT -> old
                    InkTool.ERASE_PAINT -> if (old.strokes.isEmpty()) old else old.copy(strokes = old.strokes + com.photomed.app.align.InkStroke(0, wOrig, orig, erase = true))
                    InkTool.ERASE_STROKE -> com.photomed.app.align.Annotations.eraseStrokes(old, orig, wOrig / 2)
                }
                if (next != old) { inkUndo[u] = (inkUndo[u].orEmpty() + old).takeLast(30); com.photomed.app.align.Annotations.save(context, u, next) }
                true
            } }.onFailure { com.photomed.app.CrashLog.write("Guardar una anotación", it) }.getOrDefault(false) }
            if (!ok) snackbar.showSnackbar(tr("No se pudo guardar la anotación"))
            inkVersion++
        }
    }

    /** Guarda la línea multipunto [pts] (foto editada, unidades del ancho) como anotación. */
    fun inkPoly(ph: Photo, pts: List<com.photomed.app.align.Pt>, aE: Float) {
        if (pts.size < 2) return
        val u = ph.uri.toString(); val st = inkStyle
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { com.photomed.app.data.PhotoLocks.on(u) {
                val e = com.photomed.app.align.PhotoEdits.of(context, u)
                val old = com.photomed.app.align.Annotations.of(context, u) ?: com.photomed.app.align.InkSet(com.photomed.app.align.TraceMap.baseAspect(e, aE))
                val fr = com.photomed.app.align.TraceFrame.FULL
                val orig = com.photomed.app.align.TraceMap.fromView(e, old.baseAspect, fr, pts)
                val (a, b) = com.photomed.app.align.TraceMap.fromView(e, old.baseAspect, fr, listOf(pts[0], com.photomed.app.align.Pt(pts[0].x + st.width, pts[0].y)))
                val wOrig = kotlin.math.hypot(b.x - a.x, (b.y - a.y) * old.baseAspect)
                val next = old.copy(strokes = old.strokes + com.photomed.app.align.InkStroke(st.color, wOrig, orig,
                    kind = com.photomed.app.align.InkStroke.SYMBOL, shape = com.photomed.app.align.InkRenderer.POLYLINE, size = wOrig * 6f))
                inkUndo[u] = (inkUndo[u].orEmpty() + old).takeLast(30); com.photomed.app.align.Annotations.save(context, u, next)
                true
            } }.onFailure { com.photomed.app.CrashLog.write("Guardar una línea multipunto", it) }.getOrDefault(false) }
            if (!ok) snackbar.showSnackbar(tr("No se pudo guardar la línea"))
            inkVersion++
        }
    }

    /** Mueve el punto [vi] de la línea multipunto [idx] a [to] (foto editada, unidades del ancho). */
    fun inkVertex(ph: Photo, idx: Int, vi: Int, to: com.photomed.app.align.Pt) {
        val u = ph.uri.toString()
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { com.photomed.app.data.PhotoLocks.on(u) {
                val e = com.photomed.app.align.PhotoEdits.of(context, u)
                val old = com.photomed.app.align.Annotations.of(context, u) ?: return@runCatching
                val view = old.inView(e, com.photomed.app.align.TraceFrame.FULL)
                if (idx !in view.indices || vi !in view[idx].points.indices) return@runCatching
                val moved = view[idx].points.toMutableList().also { it[vi] = to }
                val orig = com.photomed.app.align.TraceMap.fromView(e, old.baseAspect, com.photomed.app.align.TraceFrame.FULL, moved)
                val next = old.copy(strokes = old.strokes.toMutableList().also { it[idx] = it[idx].copy(points = orig) })
                inkUndo[u] = (inkUndo[u].orEmpty() + old).takeLast(30)
                com.photomed.app.align.Annotations.save(context, u, next)
            } }.onFailure { com.photomed.app.CrashLog.write("Mover un punto de una línea", it) } }
            inkVersion++
        }
    }

    /** Mover el trazo [idx] (seleccionar) [delta] en la foto editada (unidades del ancho). */
    fun inkMove(ph: Photo, idx: Int, delta: com.photomed.app.align.Pt) {
        val u = ph.uri.toString()
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { com.photomed.app.data.PhotoLocks.on(u) {
                val e = com.photomed.app.align.PhotoEdits.of(context, u)
                val old = com.photomed.app.align.Annotations.of(context, u) ?: return@runCatching
                val view = old.inView(e, com.photomed.app.align.TraceFrame.FULL)
                if (idx !in view.indices) return@runCatching
                val moved = view[idx].points.map { com.photomed.app.align.Pt(it.x + delta.x, it.y + delta.y) }
                val orig = com.photomed.app.align.TraceMap.fromView(e, old.baseAspect, com.photomed.app.align.TraceFrame.FULL, moved)
                val next = old.copy(strokes = old.strokes.toMutableList().also { it[idx] = it[idx].copy(points = orig) })
                inkUndo[u] = (inkUndo[u].orEmpty() + old).takeLast(30)
                com.photomed.app.align.Annotations.save(context, u, next)
            } }.onFailure { com.photomed.app.CrashLog.write("Mover una anotación", it) } }
            inkVersion++
        }
    }

    fun compareNames(p: Photo): List<String> =
        listOfNotNull(all.filter { it.takenAt < p.takenAt && it.expression == p.expression }.maxByOrNull { it.takenAt }?.displayName, p.displayName)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            // pantalla completa: sin barra (solo la foto y el botón para salir)
            if (!fullscreen) TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                title = {
                    Column {
                        Text(zone.label + current.expression.takeIf { it != com.photomed.app.data.FacialExpression.NEUTRAL }?.let { " · ${it.label}" }.orEmpty())
                        Text("${formatDateTime(current.takenAt)} · ${pager.currentPage + 1}/${list.size}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = review?.onKeep ?: onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Atrás")) } },
                actions = {
                    // tres acciones a la vista (el título no se aplasta); el resto en ⋯
                    // en la revisión tras la captura, Comparar a la vista (no hay Eliminar)
                    if (review != null && onCompare != null) IconButton(onClick = { onCompare(compareNames(current)) }) {
                        Icon(Icons.Default.CompareArrows, tr("Comparar"))
                    }
                    IconButton(onClick = { inking = !inking; if (inking) { tracing = false; metricsSheet = false; viewLayers.value = viewLayers.value.copy(notes = true) } }) {
                        Icon(Icons.Default.Draw, if (inking) tr("Terminar de anotar") else tr("Anotar sobre la foto"))
                    }
                    IconButton(onClick = { fullscreen = true }) { Icon(Icons.Default.Fullscreen, tr("Pantalla completa")) }
                    Box {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, tr("Compartir")) }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Compartir sin datos del paciente")) },
                                leadingIcon = { Icon(Icons.Outlined.PrivacyTip, null) },
                                onClick = { shareMenu = false; scope.launch { if (!sharePhotos(context, listOf(current), anonymize = true)) snackbar.showSnackbar(tr("No se pudo compartir la fotografía")) } },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Compartir con datos del paciente")) },
                                leadingIcon = { Icon(Icons.Outlined.Badge, null) },
                                onClick = { shareMenu = false; scope.launch { if (!sharePhotos(context, listOf(current), anonymize = false)) snackbar.showSnackbar(tr("No se pudo compartir la fotografía")) } },
                            )
                        }
                    }
                    Box {
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, tr("Más opciones")) }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            if (onCompare != null && review == null) DropdownMenuItem(text = { Text(tr("Comparar")) }, leadingIcon = { Icon(Icons.Default.CompareArrows, null) },
                                onClick = { more = false; onCompare(compareNames(current)) })
                            DropdownMenuItem(text = { Text(if (editing) tr("Terminar edición") else tr("Girar, espejo y recorte")) }, leadingIcon = { Icon(Icons.Default.Crop, null) },
                                onClick = { more = false; editing = !editing })
                            DropdownMenuItem(text = { Text(tr("Información")) }, leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = { more = false; scope.launch { info = current to withContext(Dispatchers.IO) { repo.readInfo(current) } } })
                            if (review == null) DropdownMenuItem(text = { Text(tr("Eliminar")) }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { more = false; confirmDelete = true })
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (review != null) androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedButton(enabled = !retrying, modifier = Modifier.weight(1f), onClick = {
                    retrying = true
                    pendingDelete = listOf(current)
                    scope.launch { galleryDelete(withContext(Dispatchers.IO) { repo.deletePhotos(listOf(current)) }) }
                }) {
                    Icon(Icons.Default.Replay, null); androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp)); Text(tr("Repetir"), color = Color.White)
                }
                androidx.compose.material3.Button(onClick = review.onKeep, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, null); androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp)); Text(tr("Guardar"))
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            // tabla de medidas: debajo de la foto (vertical) o a su derecha (horizontal / tableta), sin taparla
            @Composable fun metricsPanel(mod: Modifier) {
                val u = current.uri.toString()
                val lines = annotsByUri[u]
                // lectura de los trazos confirmados fuera del hilo principal (puede leer el XMP de la foto)
                val set by androidx.compose.runtime.produceState<com.photomed.app.align.TraceSet?>(null, u, userVersion) {
                    value = withContext(Dispatchers.IO) { com.photomed.app.align.ConfirmedTraces.of(context, u) }
                }
                val dismissed = set?.removed?.size ?: remember(u, userVersion) { com.photomed.app.align.AnnotUserEdits.of(context, u).dismissed.size }
                com.photomed.app.ui.compare.MetricsPanel(listOf(formatDateTime(current.takenAt) to lines.orEmpty()), layersState.value,
                    onClose = { metricsSheet = false }, loading = lines == null, note = notesByUri[u],
                    onShowLayers = { layersState.value = it },
                    // eliminar (falso positivo): los trazos quedan confirmados sin esa línea
                    onDelete = { name, _ -> selected = null; mutateTraces(current) { com.photomed.app.align.ConfirmedTraces.remove(it, name) } },
                    dismissedCount = dismissed,
                    onRestore = {
                        if (set != null) mutateTraces(current) { com.photomed.app.align.ConfirmedTraces.restore(it) }
                        else { com.photomed.app.align.AnnotUserEdits.restore(context, u); userVersion++ }
                    },
                    traces = com.photomed.app.ui.compare.TraceActions(confirmed = set != null,
                        onConfirm = { mutateTraces(current) { it } },
                        onRegenerate = { confirmRegen = true },
                        onEdit = { name -> traceTarget = name; tracing = true; metricsSheet = false
                            scope.launch { snackbar.showSnackbar(tr("Vuelva a dibujar «%1\$s»: una línea por donde se ve, o rodee el área", name)) } },
                        onRevert = { name -> mutateTraces(current) { com.photomed.app.align.ConfirmedTraces.revert(it, name) } },
                        edited = set?.editedNames.orEmpty(), savedInPhoto = savedInPhoto[u] != false),
                    onSelectLine = { l, key -> selected = l; selectedKey = key; focusTick++; if (l.layer !in layersState.value) layersState.value = layersState.value + l.layer },
                    selected = selected, selectedKey = selectedKey,
                    modifier = mod)
            }
            // controles de arriba: capas, opacidad y análisis en curso
            @Composable fun topControls() {
            // selector de capas (arrugas, rasgos…) encima de la foto
            if (showLayers && !fullscreen) com.photomed.app.ui.compare.LayerChips(layersState.value, { layersState.value = it },
                Modifier.fillMaxWidth().padding(vertical = 4.dp), onMetrics = { metricsSheet = !metricsSheet },
                onTrace = { traceTarget = null; tracing = !tracing
                    if (tracing) scope.launch { snackbar.showSnackbar(tr("Dibuje sobre el surco: una línea por donde se ve, o rodee el área")) } },
                tracing = tracing, viewLayers = viewLayers, opacity = annotAlpha.value, onOpacity = { annotAlpha.value = it })
            else if (bodyDiagram && !fullscreen) com.photomed.app.ui.compare.LayerChips(layersState.value, { layersState.value = it },
                Modifier.fillMaxWidth().padding(vertical = 4.dp), onMetrics = if (hairMetrics) ({ metricsSheet = !metricsSheet }) else null,
                viewLayers = viewLayers, opacity = annotAlpha.value, onOpacity = { annotAlpha.value = it }, body = true)
            // análisis en curso: barra de proceso visible
            val analyzing = (showLayers || bodyDiagram) && annotsByUri[current.uri.toString()] == null || traceBusy
            if (analyzing) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.weight(1f).semantics { contentDescription = tr("Analizando arrugas y rasgos") })
                Text(if (traceBusy) tr("Trazando y midiendo el surco…") else if (hairMetrics) tr("Analizando el cuero cabelludo…") else if (bodyDiagram) tr("Analizando el contorno y los puntos…") else tr("Analizando arrugas y rasgos…"), color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 10.dp))
            }
            }
            // controles de abajo: anotación y edición
            @Composable fun bottomControls() {
            if (inking && !fullscreen) InkOptions(inkStyle, { inkStyle = it }, Modifier.fillMaxWidth().navigationBarsPadding())
            if (editing) androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))) {
                // la edición se lee al pulsar (no la de la última composición) para encadenar giro, espejo…
                fun now() = com.photomed.app.align.PhotoEdits.of(context, current.uri.toString())
                val e = remember(current.uri, editVersion) { now() }
                fun save(n: com.photomed.app.align.ImageEdit) { com.photomed.app.align.PhotoEdits.set(context, current.uri.toString(), n); editVersion++ }
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                    EditButton(Icons.Default.RotateRight, tr("Girar 90°")) { save(now().rotated()) }
                    EditButton(Icons.Default.Flip, tr("Espejo")) { save(now().mirrored()) }
                    EditButton(Icons.Default.Crop, tr("Recortar")) { cropping = true }
                    EditButton(Icons.Default.Restore, tr("Original"), enabled = !e.isIdentity) { save(com.photomed.app.align.ImageEdit.NONE) }
                }
                Text(tr("La foto original no se modifica; la edición se aplica también en Comparar y al exportar."),
                    color = Color.LightGray, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
            }
            // la foto (con su paginador, la barra de anotación y la pantalla completa)
            @Composable fun photoArea(mod: Modifier) {
            Box(mod) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().background(Color.Black), key = { list[it].key },
                userScrollEnabled = !cropping && !tracing && !inking) { page ->
                val ph = list[page]
                PhotoPage(ph, zone, if ((showLayers || bodyDiagram) && viewLayers.value.diagram) layersState.value else emptySet(), editVersion, Modifier.fillMaxSize(),
                    showImage = viewLayers.value.image, showInk = viewLayers.value.notes, inkVersion = inkVersion,
                    ink = if (inking && page == pager.currentPage) inkStyle else null,
                    onInk = { path, aE -> inkPath(ph, path, aE) },
                    onInkMove = { idx, d -> inkMove(ph, idx, d) },
                    poly = if (polyMode && page == pager.currentPage) polyDraft else null,
                    onPoly = { pts, aE -> polyDraft = pts; polyAspect = aE },
                    onInkVertex = { idx, vi, to -> inkVertex(ph, idx, vi, to) },
                    selected = selected, selectedKey = selectedKey, analyze = showLayers || bodyDiagram, alpha = annotAlpha.value,
                    focusTick = if (page == pager.currentPage) focusTick else 0,
                    // referencia: fotos anteriores de la misma zona (la más reciente primero)
                    // (solo con la misma expresión: las arrugas dinámicas aparecen con el gesto)
                    previous = all.filter { it.takenAt < ph.takenAt && it.expression == ph.expression }.sortedByDescending { it.takenAt }.map { it.uri.toString() },
                    userVersion = userVersion, tracing = tracing && page == pager.currentPage,
                    onTracePath = { path -> tracing = false; tracePath(ph, path, traceTarget); traceTarget = null },
                    onSelect = { hit, key ->
                        selected = hit; selectedKey = key
                        // sin ficha flotante: la tabla de medidas se abre en la fila de la línea
                        if (hit != null) metricsSheet = true
                        // toque sin línea medida y ninguna capa de arrugas visible: cómo verlas
                        val visibleMeasured = annotsByUri[list[page].uri.toString()].orEmpty().any { it.metrics != null && it.layer in layersState.value }
                        if (hit == null && !visibleMeasured) scope.launch {
                            snackbar.showSnackbar(tr("Active Surcos, Frente, Ceño, Patas de gallo u Ojeras y toque una línea, o abra «Medidas»"))
                        }
                    },
                    onAnalyzed = { lines, note ->
                        annotsByUri[list[page].uri.toString()] = lines
                        if (note != null) notesByUri[list[page].uri.toString()] = note else notesByUri.remove(list[page].uri.toString())
                    })
            }
                // herramientas de anotación: barra flotante vertical, un icono por opción
                if (inking) InkRail(inkStyle, { inkStyle = it }, canUndo = inkUndo[current.uri.toString()].orEmpty().isNotEmpty(),
                    onUndo = {
                        val u = current.uri.toString(); val st = inkUndo[u].orEmpty()
                        st.lastOrNull()?.let { prev -> inkUndo[u] = st.dropLast(1)
                            scope.launch { withContext(Dispatchers.IO) { com.photomed.app.data.PhotoLocks.on(u) { com.photomed.app.align.Annotations.save(context, u, prev) } }; inkVersion++ } }
                    },
                    onClear = {
                        val u = current.uri.toString()
                        scope.launch {
                            withContext(Dispatchers.IO) { com.photomed.app.data.PhotoLocks.on(u) { com.photomed.app.align.Annotations.of(context, u)?.let { old ->
                                inkUndo[u] = inkUndo[u].orEmpty() + old; com.photomed.app.align.Annotations.save(context, u, old.copy(strokes = emptyList())) } } }
                            inkVersion++
                        }
                    },
                        onDone = { inking = false }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
                // línea multipunto en curso: confirmar, quitar el último punto o descartar
                if (polyMode && polyDraft.isNotEmpty()) androidx.compose.foundation.layout.Row(
                    Modifier.align(Alignment.TopCenter).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("%1\$d puntos", polyDraft.size), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { polyDraft = polyDraft.dropLast(1) }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, tr("Quitar el último punto"), tint = Color.White) }
                    IconButton(onClick = { polyDraft = emptyList() }) { Icon(Icons.Default.Close, tr("Descartar la línea"), tint = Color.White) }
                    IconButton(onClick = { inkPoly(current, polyDraft, polyAspect); polyDraft = emptyList() }, enabled = polyDraft.size >= 2) {
                        Icon(Icons.Default.Check, tr("Confirmar la línea"), tint = if (polyDraft.size >= 2) Color(0xFF69F0AE) else Color.Gray) }
                }
                // en pantalla completa: botón semitransparente para salir, arriba a la derecha de la foto
                if (fullscreen) IconButton(onClick = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)) {
                    Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        if (fullscreen) tr("Salir de pantalla completa") else tr("Pantalla completa"), tint = Color.White.copy(alpha = 0.9f))
                }
            }
            }
            if (fullscreen) photoArea(Modifier.fillMaxSize())
            else if (wide) Row(Modifier.fillMaxSize()) {
                // horizontal / tableta apaisada: controles y tabla en la mitad izquierda, la foto en la derecha
                Column(Modifier.weight(1f).fillMaxHeight().background(Color.Black)) {
                    topControls()
                    if (metricsSheet && (showLayers || hairMetrics)) metricsPanel(Modifier.fillMaxWidth().weight(1f))
                    else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    bottomControls()
                }
                photoArea(Modifier.weight(1f).fillMaxHeight())
            } else {
                topControls()
                photoArea(Modifier.weight(1f).fillMaxWidth())
            if (!wide && metricsSheet && (showLayers || hairMetrics) && !fullscreen)
                metricsPanel(Modifier.fillMaxWidth().heightIn(max = (conf.screenHeightDp * 0.20f).dp))
                bottomControls()
            }
        }
    }

    if (cropping) {
        val photo = current
        val e = com.photomed.app.align.PhotoEdits.of(context, photo.uri.toString())
        val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo.uri, e) {
            value = runCatching { com.photomed.app.align.ImageOps.loadFull(context, photo.uri, 1600, e.copy(crop = null)).asImageBitmap() }.getOrNull()
        }
        img?.let { b ->
            com.photomed.app.ui.components.CropEditor(b, aspect = null, initial = e.crop, onCancel = { cropping = false }) { r ->
                com.photomed.app.align.PhotoEdits.set(context, photo.uri.toString(), e.copy(crop = r)); editVersion++; cropping = false
            }
        }
    }

    LaunchedEffect(pager.currentPage) { selected = null }
    if (confirmRegen) {
        ConfirmDialog(
            title = tr("¿Regenerar los trazos?"),
            text = tr("Se borrarán los trazos confirmados de esta foto (también de sus metadatos), con sus ediciones y " +
                "surcos añadidos, y se volverá a analizar."),
            confirmLabel = tr("Regenerar"),
            icon = Icons.Default.Replay,
            onDismiss = { confirmRegen = false },
            onConfirm = {
                confirmRegen = false
                val ph = current
                scope.launch {
                    withContext(Dispatchers.IO) { com.photomed.app.align.ConfirmedTraces.clear(context, ph.uri.toString()) }
                    annotsByUri.remove(ph.uri.toString()); selected = null; userVersion++
                }
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = tr("¿Eliminar fotografía?"),
            text = tr("Se eliminará de la galería del teléfono y del expediente."),
            confirmLabel = tr("Eliminar"),
            icon = Icons.Default.Delete,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                pendingDelete = listOf(current)
                scope.launch { galleryDelete(withContext(Dispatchers.IO) { repo.deletePhotos(listOf(current)) }) }
            },
        )
    }

    editDate?.let { ph ->
        com.photomed.app.ui.components.DateTimePickerDialog(ph.takenAt, onDismiss = { editDate = null }, onPick = { t ->
            editDate = null
            scope.launch {
                val np = withContext(Dispatchers.IO) { runCatching {
                    com.photomed.app.data.CaptureDate.write(context, ph.uri, t)
                    val e = repo.getExpediente(expId) ?: error("Sin expediente")
                    repo.setCaptureDate(e, ph, t).also { com.photomed.app.align.PhotoKeys.move(context, ph.uri.toString(), it.uri.toString()) }
                }.onFailure { com.photomed.app.CrashLog.write("Cambiar la fecha de captura", it) }.getOrNull() }
                if (np == null) snackbar.showSnackbar(tr("No se pudo cambiar la fecha"))
                else { trackName = np.displayName; reload++; snackbar.showSnackbar(tr("Fecha de captura: %1\$s", formatDateTime(np.takenAt))) }
            }
        })
    }

    info?.let { (photo, meta) ->
        SafeBottomSheet(onDismiss = { info = null }) {
            Column(Modifier.padding(bottom = androidx.compose.ui.unit.Dp(16f))) {
                Text(tr("Información de la fotografía"), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = androidx.compose.ui.unit.Dp(24f)))
                InfoRow(tr("Paciente"), meta?.patientName ?: "—")
                InfoRow(tr("Expediente"), meta?.recordNumber?.ifBlank { null } ?: "—")
                InfoRow(tr("Zona"), meta?.zoneLabel ?: photo.zone.label)
                ListItem(overlineContent = { Text(tr("Fecha de captura")) }, headlineContent = { Text(formatDateTime(photo.takenAt)) },
                    trailingContent = { IconButton(onClick = { editDate = photo; info = null }) { Icon(Icons.Default.EditCalendar, tr("Editar la fecha de captura")) } })
                InfoRow(tr("Consentimiento"), if (meta?.consent == true) tr("Registrado") else tr("No registrado"))
                meta?.conditions?.takeIf { it.isNotEmpty() }?.let { c ->
                    val labels = mapOf("Engine" to trn("Motor de puntos"), "Assist" to trn("Asistencia"), "MatchPercent" to trn("Puntos coincidentes (%)"),
                        "HeadYaw" to trn("Giro de la cabeza (°)"), "HeadPitch" to trn("Cabeceo, barbilla arriba + (°)"), "Side" to trn("Lado (extremidad)"), "DistanceCm" to trn("Distancia estimada (cm)"), "Zoom" to trn("Zoom"), "Status" to trn("Estado"))
                    c.forEach { (k, v) -> InfoRow(labels[k]?.let { tr(it) } ?: k, v) }
                }
                InfoRow(tr("Archivo"), "Pictures/PhotoMed/$expId/${photo.displayName}")
                Text(
                    tr("Estos datos están incrustados en la imagen (EXIF y XMP)."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = androidx.compose.ui.unit.Dp(24f)),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(overlineContent = { Text(label) }, headlineContent = { Text(value) })
}

@Composable
private fun EditButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (enabled) Color.White else Color.Gray) }
        Text(label, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Página del visor: la foto con su edición (giro, espejo, recorte) ajustada a la pantalla, con zoom
 * (pellizco, doble toque) y las capas de anotación visibles en el mismo sistema de coordenadas.
 */
@Composable
private fun PhotoPage(photo: Photo, zone: Zone, layers: Set<com.photomed.app.align.AnnotLayer>, version: Int, modifier: Modifier,
                      selected: com.photomed.app.align.AnnotLine? = null, onSelect: (com.photomed.app.align.AnnotLine?, String?) -> Unit = { _, _ -> },
                      selectedKey: String? = null,
                      /** Cambia al tocar una fila de la tabla: se desplaza la foto hasta la línea seleccionada. */
                      focusTick: Int = 0,
                      /** Capas de la vista: imagen y anotaciones. */
                      showImage: Boolean = true, showInk: Boolean = true,
                      /** Cambia al guardar anotaciones (vuelve a leerlas). */
                      inkVersion: Int = 0,
                      /** Anotando con este estilo (null = no): un dedo dibuja. */
                      ink: InkStyle? = null,
                      onInk: (List<com.photomed.app.align.Pt>, Float) -> Unit = { _, _ -> },
                      /** Seleccionar: mover el trazo (índice) un desplazamiento (foto editada, unidades del ancho). */
                      onInkMove: (Int, com.photomed.app.align.Pt) -> Unit = { _, _ -> },
                      /** Línea multipunto en curso (foto editada, unidades del ancho); null = no se está trazando una. */
                      poly: List<com.photomed.app.align.Pt>? = null,
                      /** Nuevos puntos de la línea en curso y alto / ancho de la foto editada. */
                      onPoly: (List<com.photomed.app.align.Pt>, Float) -> Unit = { _, _ -> },
                      /** Seleccionar: mover un punto (índice) de una línea multipunto guardada (índice) a una posición. */
                      onInkVertex: (Int, Int, com.photomed.app.align.Pt) -> Unit = { _, _, _ -> },
                      /** Analizar la foto (panel de capas abierto), aunque no haya capas encendidas: la tabla lo necesita. */
                      analyze: Boolean = layers.isNotEmpty(),
                      /** Opacidad de los trazos y números. */
                      alpha: Float = 0.5f,
                      /** Fotos anteriores del mismo paciente y zona (la más reciente primero): referencia de arrugas. */
                      previous: List<String> = emptyList(),
                      /** Cambia al eliminar o añadir líneas (vuelve a analizar). */
                      userVersion: Int = 0,
                      /** Modo «trazar surco»: dibujar a mano alzada; devuelve el trazo (foto editada, unidades del ancho). */
                      tracing: Boolean = false,
                      onTracePath: (List<com.photomed.app.align.Pt>) -> Unit = {},
                      onAnalyzed: (List<com.photomed.app.align.AnnotLine>, String?) -> Unit = { _, _ -> }) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val edit = remember(photo.uri, version) { com.photomed.app.align.PhotoEdits.of(context, photo.uri.toString()) }
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo.uri, edit) {
        value = runCatching { com.photomed.app.align.ImageOps.loadFull(context, photo.uri, 2048, edit).asImageBitmap() }.getOrNull()
    }
    val annots by androidx.compose.runtime.produceState<List<com.photomed.app.align.AnnotLine>?>(null, photo.uri, edit, analyze, userVersion) {
        if (!analyze) return@produceState
        val u = photo.uri.toString()
        // trazos confirmados (guardados en la foto): se muestran tal cual, sin volver a analizar
        val confirmed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.photomed.app.align.ConfirmedTraces.of(context, u) }
        if (confirmed != null) {
            value = confirmed.lines(edit, com.photomed.app.align.TraceFrame.FULL)
            onAnalyzed(value.orEmpty(), null)
            // trazos confirmados en 2.23–2.24 (sin tramos de los rasgos): se recuperan una vez de la cara,
            // sin cambiar los trazos ni las cifras confirmadas
            if (com.photomed.app.align.GuideMigration.needsFeatureGuides(confirmed)) {
                com.photomed.app.align.GuideMigration.backfill(context, photo.uri, zone)?.let { updated ->
                    value = updated.lines(edit, com.photomed.app.align.TraceFrame.FULL)
                    onAnalyzed(value.orEmpty(), null)
                }
            }
            return@produceState
        }
        val user = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.photomed.app.align.AnnotUserEdits.of(context, u) }.let { e -> if (e.editHash != edit.hashCode()) e.copy(added = emptyList()) else e }
        val key = "full|$u|${edit.hashCode()}|${com.photomed.app.align.ReliefStore.tag(context, u)}|" +
            "${com.photomed.app.align.AnnotUserEdits.tag(context, u)}|${com.photomed.app.align.LineMemory.tag(context, previous)}"
        val hit = com.photomed.app.align.PhotoAnnotator.cached(key)
        // sin análisis disponible (pruebas en la JVM): no se carga la imagen para nada
        if (hit == null && !com.photomed.app.align.PhotoAnnotator.enabled) {
            com.photomed.app.align.PhotoAnnotator.putNote(key, tr("Análisis no disponible")); value = emptyList()
            onAnalyzed(emptyList(), tr("Análisis no disponible")); return@produceState
        }
        value = hit ?: runCatching {
            com.photomed.app.align.PhotoAnnotator(context.applicationContext)
                .analyze(com.photomed.app.align.ImageOps.loadFull(context, photo.uri, 1600, edit), zone,
                    com.photomed.app.align.PhotoAnnotator.extrasFor(context, photo.uri, 1600, crop34 = false),
                    user = user, priors = com.photomed.app.align.LineMemory.priors(context, previous), memoryUri = u)
        }.getOrNull()?.let { (lines, note) ->
            com.photomed.app.align.PhotoAnnotator.put(key, lines); com.photomed.app.align.PhotoAnnotator.putNote(key, note); lines
        } ?: emptyList<com.photomed.app.align.AnnotLine>().also {
            com.photomed.app.align.PhotoAnnotator.putNote(key, tr("No se pudo analizar la foto."))
        }
        onAnalyzed(value.orEmpty(), com.photomed.app.align.PhotoAnnotator.note(key))
    }
    // anotaciones de la foto, en su encuadre completo con la edición
    val inkLines by androidx.compose.runtime.produceState<List<com.photomed.app.align.InkStroke>>(emptyList(), photo.uri, edit, inkVersion) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.photomed.app.align.Annotations.of(context, photo.uri.toString())?.inView(edit, com.photomed.app.align.TraceFrame.FULL) }.getOrNull().orEmpty()
        }
    }
    val imgState = androidx.compose.runtime.rememberUpdatedState(img)
    val annotsState = androidx.compose.runtime.rememberUpdatedState(annots)
    val layersState = androidx.compose.runtime.rememberUpdatedState(layers)
    val onSelectState = androidx.compose.runtime.rememberUpdatedState(onSelect)
    val selectedState = androidx.compose.runtime.rememberUpdatedState(selected)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val b = img
    // fila de la tabla tocada: la línea seleccionada al centro, ampliada si es pequeña
    LaunchedEffect(focusTick) {
        val l = selected ?: return@LaunchedEffect
        val bb = img ?: return@LaunchedEffect
        if (focusTick == 0 || boxSize.width == 0 || l.points.isEmpty()) return@LaunchedEffect
        val sw = boxSize.width.toFloat(); val sh = boxSize.height.toFloat()
        val ia = bb.width / bb.height.toFloat()
        val w = minOf(sw, sh * ia); val h = w / ia
        // con un tramo seleccionado, se centra ese tramo
        val pts = l.guides.filter { it.key == selectedKey }.flatMap { listOf(it.a, it.b) }.ifEmpty { l.points + l.guides.flatMap { listOf(it.a, it.b) } }
        val x0 = pts.minOf { it.x }; val x1 = pts.maxOf { it.x }; val y0 = pts.minOf { it.y }; val y1 = pts.maxOf { it.y }
        val size = maxOf(x1 - x0, y1 - y0).coerceAtLeast(0.02f) * w
        val s = (minOf(sw, sh) * 0.5f / size).coerceIn(1f, 4f).coerceAtLeast(scale)
        val px = (sw - w) / 2 + (x0 + x1) / 2 * w; val py = (sh - h) / 2 + (y0 + y1) / 2 * w
        scale = s
        offset = if (s == 1f) androidx.compose.ui.geometry.Offset.Zero else androidx.compose.ui.geometry.Offset(-(px - sw / 2) * s, -(py - sh / 2) * s)
    }
    Box(modifier
        .onSizeChanged { boxSize = it }
        .pointerInput(Unit) {
            // zoom con dos dedos; con un dedo solo se desplaza si hay zoom (si no, se pasa de foto)
            detectZoomPanPassThrough({ scale > 1f }) { pan, zoom ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
            }
        }
        .pointerInput(Unit) {
            // toque sin consumir el evento (no bloquea el paso de una foto a otra)
            detectTapPassThrough(
                onDoubleTap = { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero },
                onTap = { s ->
                    // toque → punto de la foto (deshace zoom y desplazamiento) → línea con medidas más cercana
                    val bb = imgState.value ?: return@detectTapPassThrough
                    val a = annotsState.value ?: return@detectTapPassThrough
                    val sw = size.width.toFloat(); val sh = size.height.toFloat()
                    val q = screenToPhoto(s.x, s.y, sw, sh, bb.width, bb.height, scale, offset.x, offset.y)
                    val w = minOf(sw, sh * bb.width / bb.height.toFloat())
                    // con una línea ya seleccionada, tocar uno de sus tramos punteados selecciona ese tramo
                    val hit = com.photomed.app.ui.compare.hitPick(a, layersState.value, q, 28.dp.toPx() / (w * scale), selectedState.value?.metrics?.name)
                    onSelectState.value(hit?.first, hit?.second)
                },
            )
        }
        .semantics { contentDescription = tr("Fotografía %1\$s, %2\$s", photo.zone.label, formatDateTime(photo.takenAt)) },
        contentAlignment = Alignment.Center) {
        if (b == null) CircularProgressIndicator()
        else androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        }) {
            val ia = b.width / b.height.toFloat()
            val w = minOf(size.width, size.height * ia); val h = w / ia
            val left = (size.width - w) / 2; val top = (size.height - h) / 2
            // capa 1: la imagen
            if (showImage) drawImage(b, dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
            annots?.let { a ->
                val k = (w / 360.dp.toPx()).coerceIn(0.35f, 1.4f) / scale.coerceAtLeast(1f).let { kotlin.math.sqrt(it) }
                fun map(p: com.photomed.app.align.Pt) = androidx.compose.ui.geometry.Offset(left + p.x * w, top + p.y * w)
                // línea tocada: resalte blanco debajo
                selected?.takeIf { it.layer in layers }?.let { sl ->
                    val path = androidx.compose.ui.graphics.Path()
                    sl.points.forEachIndexed { i, q -> val o = map(q); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                    if (sl.closed) path.close()
                    drawPath(path, Color.White.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 7.dp.toPx() * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                drawAnnotations(a, layers, k, numbers = com.photomed.app.align.MeasureNumbering.of(listOf(a)), alpha = alpha) { p -> map(p) }
                // lo que se mide de la línea seleccionada (ancho, alto, distancias), punteado
                selected?.takeIf { it.layer in layers }?.let { sl -> drawMeasureGuides(sl, k, { p -> map(p) }, selectedKey) }
            }
            // capa 3: anotaciones (encima del diagrama)
            if (showInk) drawInk(inkLines, w) { p -> androidx.compose.ui.geometry.Offset(left + p.x * w, top + p.y * w) }
        }
        // anotar: un dedo dibuja (el trazo se ve mientras se dibuja); la foto no se mueve
        if (ink != null && b != null) {
            val path = remember { androidx.compose.runtime.mutableStateListOf<androidx.compose.ui.geometry.Offset>() }
            // seleccionar: trazo tocado y desplazamiento (unidades del ancho de la foto)
            var picked by remember { mutableIntStateOf(-1) }
            var moveBy by remember { mutableStateOf(com.photomed.app.align.Pt(0f, 0f)) }
            /** Seleccionar: punto de una línea multipunto que se arrastra (índice del trazo, índice del punto). */
            var pickedVertex by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            val inkNow = androidx.compose.runtime.rememberUpdatedState(inkLines)
            val polyNow = androidx.compose.runtime.rememberUpdatedState(poly)
            fun d(a: com.photomed.app.align.Pt, q: com.photomed.app.align.Pt) = kotlin.math.hypot(a.x - q.x, a.y - q.y)
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()
                .semantics { contentDescription = tr("Dibuje sobre la foto") }
                .pointerInput(b, ink) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()   // un toque al anotar no selecciona líneas
                        val sw = size.width.toFloat(); val sh = size.height.toFloat()
                        fun toPhoto(o: androidx.compose.ui.geometry.Offset) = screenToPhoto(o.x, o.y, sw, sh, b.width, b.height, scale, offset.x, offset.y)
                        val aE = b.height / b.width.toFloat()
                        // línea multipunto: un toque añade un punto; arrastrar un punto lo mueve
                        val draft = polyNow.value
                        if (ink.tool == InkTool.SYMBOL && draft != null) {
                            val q0 = toPhoto(down.position)
                            val tolP = 24.dp.toPx() / (minOf(sw, sh * b.width / b.height.toFloat()) * scale)
                            val vi = draft.indices.minByOrNull { d(draft[it], q0) }?.takeIf { d(draft[it], q0) < tolP }
                            var last = q0
                            drag(down.id) { ch ->
                                last = toPhoto(ch.position)
                                if (vi != null) onPoly(draft.toMutableList().also { it[vi] = last }, aE)
                                ch.consume()
                            }
                            if (vi == null) onPoly(draft + last, aE)
                            return@awaitEachGesture
                        }
                        if (ink.tool == InkTool.SELECT) {
                            val q0 = toPhoto(down.position)
                            val wImg = minOf(sw, sh * b.width / b.height.toFloat()) * scale
                            // un punto de una línea multipunto: se mueve solo ese punto
                            val tolV = 22.dp.toPx() / wImg
                            var hitV: Pair<Int, Int>? = null; var bestV = tolV
                            inkNow.value.forEachIndexed { si, st ->
                                if (st.kind == com.photomed.app.align.InkStroke.SYMBOL && st.shape == com.photomed.app.align.InkRenderer.POLYLINE)
                                    st.points.forEachIndexed { vi, p -> val dd = d(p, q0); if (dd < bestV) { bestV = dd; hitV = si to vi } }
                            }
                            hitV?.let { hv ->
                                pickedVertex = hv
                                drag(down.id) { ch -> val q = toPhoto(ch.position); moveBy = com.photomed.app.align.Pt(q.x - q0.x, q.y - q0.y); ch.consume() }
                                val st = inkNow.value.getOrNull(hv.first)
                                if (st != null && kotlin.math.hypot(moveBy.x, moveBy.y) > 1e-4f) {
                                    val p = st.points[hv.second]
                                    onInkVertex(hv.first, hv.second, com.photomed.app.align.Pt(p.x + moveBy.x, p.y + moveBy.y))
                                }
                                pickedVertex = null; moveBy = com.photomed.app.align.Pt(0f, 0f)
                                return@awaitEachGesture
                            }
                            picked = com.photomed.app.align.Annotations.hit(inkNow.value, q0, 18.dp.toPx() / wImg)
                            if (picked < 0) return@awaitEachGesture
                            drag(down.id) { ch -> val q = toPhoto(ch.position); moveBy = com.photomed.app.align.Pt(q.x - q0.x, q.y - q0.y); ch.consume() }
                            if (kotlin.math.hypot(moveBy.x, moveBy.y) > 1e-4f) onInkMove(picked, moveBy)
                            picked = -1; moveBy = com.photomed.app.align.Pt(0f, 0f)
                            return@awaitEachGesture
                        }
                        path.clear(); path += down.position
                        drag(down.id) { ch -> path += ch.position; ch.consume() }
                        if (path.isNotEmpty()) onInk(path.map(::toPhoto), b.height / b.width.toFloat())
                        path.clear()
                    }
                }) {
                val ia = b.width / b.height.toFloat()
                val wImg = minOf(size.width, size.height * ia) * scale
                // de la foto (unidades del ancho) a la pantalla, con el zoom y el desplazamiento
                val w0 = minOf(size.width, size.height * ia); val h0 = w0 / ia
                fun toScreen(p: com.photomed.app.align.Pt) = android.graphics.PointF(
                    size.width / 2 + ((size.width - w0) / 2 + p.x * w0 - size.width / 2) * scale + offset.x,
                    size.height / 2 + ((size.height - h0) / 2 + p.y * w0 - size.height / 2) * scale + offset.y)
                val pv = pickedVertex
                if (ink.tool == InkTool.SELECT && pv != null && pv.first in inkLines.indices) {
                    // el punto arrastrado de la línea multipunto (los ángulos se actualizan al moverlo)
                    val st0 = inkLines[pv.first]
                    val st = st0.copy(points = st0.points.mapIndexed { i, p -> if (i == pv.second) com.photomed.app.align.Pt(p.x + moveBy.x, p.y + moveBy.y) else p })
                    drawIntoCanvas { c -> com.photomed.app.align.InkRenderer.draw(c.nativeCanvas, listOf(st), wImg, ::toScreen, density) }
                    val q = toScreen(st.points[pv.second])
                    drawCircle(Color.White, 12.dp.toPx(), androidx.compose.ui.geometry.Offset(q.x, q.y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                } else if (ink.tool == InkTool.SYMBOL && poly != null) {
                    // línea multipunto en curso: segmentos, puntos (con un anillo para tocarlos) y ángulos
                    if (poly.isNotEmpty()) {
                        val st = com.photomed.app.align.InkStroke(ink.color, ink.width, poly, kind = com.photomed.app.align.InkStroke.SYMBOL,
                            shape = com.photomed.app.align.InkRenderer.POLYLINE)
                        drawIntoCanvas { c -> com.photomed.app.align.InkRenderer.draw(c.nativeCanvas, listOf(st), wImg, ::toScreen, density) }
                        poly.forEach { p -> val q = toScreen(p)
                            drawCircle(Color.White.copy(alpha = 0.9f), 11.dp.toPx(), androidx.compose.ui.geometry.Offset(q.x, q.y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) }
                    }
                } else if (ink.tool == InkTool.SELECT && picked in inkLines.indices) {
                    // el trazo elegido, movido, con un recuadro discontinuo
                    val st = inkLines[picked]
                    val mv = { p: com.photomed.app.align.Pt -> toScreen(com.photomed.app.align.Pt(p.x + moveBy.x, p.y + moveBy.y)) }
                    drawIntoCanvas { c -> com.photomed.app.align.InkRenderer.drawOne(c.nativeCanvas, st, wImg, mv) }
                    val o = com.photomed.app.align.InkRenderer.outline(st).map(mv)
                    val r = st.size * wImg / 2
                    drawRect(Color.White, androidx.compose.ui.geometry.Offset(o.minOf { it.x } - r - 6f, o.minOf { it.y } - r - 6f),
                        androidx.compose.ui.geometry.Size(o.maxOf { it.x } - o.minOf { it.x } + 2 * r + 12f, o.maxOf { it.y } - o.minOf { it.y } + 2 * r + 12f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
                } else if (path.isNotEmpty() && ink.tool == InkTool.SYMBOL) {
                    // vista previa del símbolo: marcador con un toque, de un extremo al otro arrastrando
                    val dragged = path.maxOf { kotlin.math.hypot(it.x - path[0].x, it.y - path[0].y) } > 0.02f * wImg
                    val pts = if (dragged) listOf(path.first(), path.last()) else listOf(path.last())
                    val st = com.photomed.app.align.InkStroke(ink.color, ink.width, pts.map { com.photomed.app.align.Pt(it.x, it.y) },
                        kind = com.photomed.app.align.InkStroke.SYMBOL, shape = ink.symbol, text = ink.text.ifBlank { null }, side = ink.side, size = ink.symbolSize)
                    drawIntoCanvas { c -> com.photomed.app.align.InkRenderer.draw(c.nativeCanvas, listOf(st), wImg, { q -> android.graphics.PointF(q.x, q.y) }, density) }
                } else if (path.isNotEmpty()) {
                    val sw = (ink.width * wImg).coerceAtLeast(2f)
                    val p = androidx.compose.ui.graphics.Path().apply { moveTo(path[0].x, path[0].y); path.drop(1).forEach { lineTo(it.x, it.y) } }
                    val col = if (ink.tool == InkTool.PEN) Color(ink.color) else Color.White.copy(alpha = 0.45f)
                    drawPath(p, col, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
        // trazar un surco a mano alzada: el trazo se ve mientras se dibuja (con o sin zoom)
        if (tracing && b != null) {
            val path = remember { androidx.compose.runtime.mutableStateListOf<androidx.compose.ui.geometry.Offset>() }
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()
                .semantics { contentDescription = tr("Dibuje el surco sobre la foto") }
                .pointerInput(b) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        path.clear(); path += down.position
                        drag(down.id) { ch -> path += ch.position; ch.consume() }
                        if (path.size >= 2) {
                            // de la pantalla a la foto, deshaciendo el zoom y el desplazamiento
                            val sw = size.width.toFloat(); val sh = size.height.toFloat()
                            onTracePath(path.map { o -> screenToPhoto(o.x, o.y, sw, sh, b.width, b.height, scale, offset.x, offset.y) })
                        }
                        path.clear()
                    }
                }) {
                drawRect(Color.Black.copy(alpha = 0.2f))
                if (path.size >= 2) {
                    val p = androidx.compose.ui.graphics.Path().apply { moveTo(path[0].x, path[0].y); path.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(p, Color.Black.copy(alpha = 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawPath(p, Color(0xFFFFAB40), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
    }
}


/**
 * Punto de la pantalla (en el área de la foto: [sw] × [sh] px) → punto de la foto en unidades de su
 * ancho, para una foto de [imgW] × [imgH] ajustada al área y mostrada con zoom [scale] alrededor del
 * centro y desplazamiento ([ox], [oy]).
 */
fun screenToPhoto(x: Float, y: Float, sw: Float, sh: Float, imgW: Int, imgH: Int, scale: Float, ox: Float, oy: Float): com.photomed.app.align.Pt {
    val ia = imgW / imgH.toFloat()
    val w = minOf(sw, sh * ia); val h = w / ia
    val cx = sw / 2; val cy = sh / 2
    val px = cx + (x - cx - ox) / scale; val py = cy + (y - cy - oy) / scale
    return com.photomed.app.align.Pt((px - (sw - w) / 2) / w, (py - (sh - h) / 2) / w)
}

/**
 * Lado del texto en la foto original a partir del lado visto con la edición [e] (giro y espejo
 * inversos: espejo primero, luego el giro contrario).
 */
internal fun sideInOriginal(side: String, e: com.photomed.app.align.ImageEdit): String {
    // rotateSide aplica giro y después espejo; para deshacerlo: espejo y luego el giro contrario
    val m = if (e.mirror) com.photomed.app.align.rotateSide(side, com.photomed.app.align.ImageEdit(mirror = true)) else side
    return com.photomed.app.align.rotateSide(m, com.photomed.app.align.ImageEdit(rot = (4 - e.rot % 4) % 4))
}
