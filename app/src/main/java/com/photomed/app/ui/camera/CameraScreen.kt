package com.photomed.app.ui.camera

import android.Manifest
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import com.photomed.app.ui.components.SafeBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import com.photomed.app.align.LiveMatch
import com.photomed.app.AppScope
import com.photomed.app.align.ZoomController
import kotlin.coroutines.resume
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.photomed.app.align.ImageOps
import com.photomed.app.align.LiveFrame
import com.photomed.app.align.LiveMatcher
import com.photomed.app.align.AnalysisPipeline
import androidx.compose.runtime.SideEffect
import com.photomed.app.align.MatchStatus
import com.photomed.app.align.Perspective
import com.photomed.app.align.Pt
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import android.os.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.photomed.app.ui.components.EmptyState
import com.photomed.app.ui.theme.ImmersiveTheme
import com.photomed.app.ui.theme.Md3Motion
import com.photomed.app.ui.theme.Md3Spacing
import java.io.File
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Rational
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.photomed.app.data.Expediente
import com.photomed.app.data.Photo
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt


@Composable
fun CameraScreen(expId: String, zone: Zone, onBack: () -> Unit) = ImmersiveTheme {
    val context = LocalContext.current
    val needed = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    fun granted() = needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var hasPermission by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasPermission = granted() }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(needed.toTypedArray()) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraContent(expId, zone, onBack)
        } else {
            EmptyState(
                icon = Icons.Default.NoPhotography,
                title = "Se necesita acceso a la cámara",
                body = "PhotoMed usa la cámara solo para tomar las fotografías clínicas del expediente." +
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) " En esta versión de Android también necesita permiso para guardar en la galería." else "",
                actionLabel = "Conceder permiso",
                onAction = { launcher.launch(needed.toTypedArray()) },
            )
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
            }
        }
    }
}

@Composable
private fun CameraContent(expId: String, zone: Zone, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()

    var exp by remember { mutableStateOf<Expediente?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var showGrid by remember { mutableStateOf(true) }
    var showContour by remember { mutableStateOf(true) }
    var showGhost by remember { mutableStateOf(true) }
    var showLevel by remember { mutableStateOf(true) }
    var ghostAlpha by remember { mutableFloatStateOf(0.4f) }
    var ghost by remember { mutableStateOf<Photo?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val shutterFlash = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    var lastSaved by remember { mutableStateOf<Photo?>(null) }
    var liveMatch by remember { mutableStateOf(true) }
    val prefs = remember { context.getSharedPreferences("photomed", android.content.Context.MODE_PRIVATE) }
    var autoCapture by remember { mutableStateOf(prefs.getBoolean("auto_capture", false)) }
    // Modo individual de extremidades (manos, pies, piernas): lado recordado por zona
    var limbSide by remember(zone) { mutableStateOf(LimbSide.ofTag(prefs.getString("limb_side_${zone.id}", null))) }
    var threshold by remember { mutableFloatStateOf(prefs.getFloat("match_threshold_v2", LiveMatch.DEFAULT_THRESHOLD)) }
    var showSettings by remember { mutableStateOf(false) }
    var mirrorOn by remember { mutableStateOf(prefs.getBoolean("mirror_front", true)) }
    var zoomAssist by remember { mutableStateOf(prefs.getBoolean("zoom_assist", true)) }
    var useGaze by remember { mutableStateOf(prefs.getBoolean(com.photomed.app.align.AlignOptions.PREF_GAZE, true)) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    /** Zoom manual (deslizador o pellizco): pausa el zoom asistido hasta que se reanude. */
    var manualZoom by remember { mutableStateOf(false) }
    var zoomRange by remember { mutableStateOf(1f..4f) }
    var refPoints by remember { mutableStateOf<Map<String, Pt>>(emptyMap()) }
    var refSilhouette by remember { mutableStateOf<com.photomed.app.align.Silhouette?>(null) }
    var refFree by remember { mutableStateOf<com.photomed.app.align.FreeContour?>(null) }
    var adaptGuide by remember { mutableStateOf(true) }
    var autoArmed by remember { mutableStateOf(true) }
    var live by remember { mutableStateOf<LiveFrame?>(null) }
    var focalNorm by remember { mutableStateOf<Float?>(null) }
    val tilt = rememberTilt(true)
    // Hilo de análisis con motor intercambiable: el analizador usa siempre el LiveMatcher actual;
    // al cambiar de motor el anterior se cierra en el mismo hilo (sin usar uno cerrado).
    val pipeline = remember { AnalysisPipeline<LiveMatcher>(Executors.newSingleThreadExecutor()) }
    var engine by remember { mutableStateOf(com.photomed.app.align.FaceEngine.get(context)) }
    val matcher = remember(zone, engine) { LiveMatcher(zone, context.applicationContext, engine) }
    SideEffect { pipeline.swap(matcher) }
    matcher.threshold = threshold
    matcher.useGaze = useGaze
    DisposableEffect(Unit) { onDispose { pipeline.shutdown() } }
    val lastAnalysis = remember { AtomicLong(0L) }
    // Asistencia en vivo: con referencia (foto superpuesta) iguala el encuadre; sin ella, estandariza la toma.
    val matchingEnabled = liveMatch
    val referenceMode = showGhost && ghost != null
    matcher.useReference = referenceMode
    val matchingFlag = remember { AtomicBoolean(false) }
    matchingFlag.set(matchingEnabled)

    // Referencia para la coincidencia en vivo: la foto superpuesta (encuadre 3:4), con el motor elegido.
    LaunchedEffect(ghost?.key, matcher) {
        val g = ghost ?: return@LaunchedEffect
        live = null
        refSilhouette = null
        refFree = null
        val bmp = runCatching { ImageOps.load34(context, g.uri, 360) }.getOrNull() ?: return@LaunchedEffect
        val m = matcher
        pipeline.post {
            runCatching { m.setReference(bmp) }
            refPoints = m.referencePoints
            refSilhouette = m.referenceSilhouette
            refFree = m.referenceFree
        }
    }
    val status = live?.eval?.status
    LaunchedEffect(status) {
        if (status == MatchStatus.MATCH) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val tone = when {
        !matchingEnabled -> GuideTone.NORMAL
        status == MatchStatus.MATCH -> GuideTone.MATCH
        status == MatchStatus.CLOSE -> GuideTone.CLOSE
        else -> GuideTone.NORMAL
    }

    LaunchedEffect(reload) {
        val (e, list) = withContext(Dispatchers.IO) {
            val e = repo.getExpediente(expId)
            e to e?.let { repo.photos(it, zone) }.orEmpty()
        }
        exp = e; photos = list
        if (ghost == null || photos.none { it.key == ghost?.key })
            ghost = photos.lastOrNull { LimbSide.ofFileName(it.displayName, zone) == limbSide } ?: photos.lastOrNull()
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val imageCapture = remember(lensFacing) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(Surface.ROTATION_0)
            .build()
    }
    LaunchedEffect(imageCapture, flashMode) { imageCapture.flashMode = flashMode }
    val analysisRef = remember { java.util.concurrent.atomic.AtomicReference<ImageAnalysis?>() }
    val analysis = remember(lensFacing) {
        ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build().also { a ->
                analysisRef.set(a)
                a.setAnalyzer({ r -> pipeline.post(r::run) }) { proxy ->
                    try {
                        val now = System.currentTimeMillis()
                        if (matchingFlag.get() && now - lastAnalysis.get() >= 180) {
                            lastAnalysis.set(now)
                            val frame = proxy.toUprightCrop(360)
                            pipeline.withEngine { it.analyze(frame) }?.let { live = it }
                        }
                    } catch (_: Exception) {
                    } finally {
                        proxy.close()
                    }
                }
            }
    }
    // Liberar el analizador de ESTA instancia al cambiar de cámara. (Antes se limpiaba el de
    // analysisRef, que ya apuntaba al nuevo: al cambiar de cámara el análisis en vivo se detenía.)
    DisposableEffect(analysis) { onDispose { runCatching { analysis.clearAnalyzer() } } }

    LaunchedEffect(lensFacing) {
        val provider = context.cameraProvider()
        val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build()
        preview.surfaceProvider = previewView.surfaceProvider
        // Mismo encuadre 3:4 para vista previa y captura: lo que se ve es lo que se guarda.
        val viewPort = ViewPort.Builder(Rational(3, 4), Surface.ROTATION_0)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageCapture)
            .addUseCase(analysis)
            .setViewPort(viewPort)
            .build()
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, group)
            focalNorm = camera?.let { focalNormOf(it) }
            zoomRatio = 1f
            // rango de zoom de esta cámara (el estado llega tras enlazar)
            repeat(20) {
                camera?.cameraInfo?.zoomState?.value?.let { zs ->
                    zoomRange = zs.minZoomRatio..minOf(zs.maxZoomRatio, MAX_MANUAL_ZOOM).coerceAtLeast(zs.minZoomRatio + 0.01f)
                    return@repeat
                }
                kotlinx.coroutines.delay(50)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo iniciar la cámara: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun captureConditions(): Map<String, String> = buildMap {
        put("Engine", engine.label)
        if (zone.hasLimbSides) put("Side", limbSide.label)
        put("Zoom", "%.2f".format(java.util.Locale.US, zoomRatio))
        live?.let { f ->
            f.eval.pointFraction?.let { put("MatchPercent", "%.0f".format(java.util.Locale.US, it * 100)) }
            put("Assist", if (f.standard) "estandarización" else "coincidencia con referencia")
            put("Status", f.eval.status.name)
            f.headYaw?.let { put("HeadYaw", "%.1f".format(java.util.Locale.US, it)) }
            f.headPitch?.let { put("HeadPitch", "%.1f".format(java.util.Locale.US, it)) }
            f.gaze?.let { put("Gaze", "%.2f,%.2f".format(java.util.Locale.US, it.x, it.y)) }
            CameraGuidance.faceDistanceCm(f.currentPointsAll, focalNorm, zoomRatio)?.let {
                put("DistanceCm", "%.0f".format(java.util.Locale.US, it))
            }
        }
    }

    fun capture() {
        val e = exp ?: return
        if (capturing) return
        capturing = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val temp = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(temp).build()
        scope.launch {
            shutterFlash.snapTo(0.8f)
            shutterFlash.animateTo(0f, tween(Md3Motion.durationMedium2, easing = Md3Motion.easingStandard))
        }
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val conditions = captureConditions()
                    val pitch = Perspective.cameraPitchFromDevice(tilt.value.pitch, lensFacing == CameraSelector.LENS_FACING_FRONT)
                    val roll = tilt.value.roll
                    val focal = focalNorm
                    // Se guarda en el ámbito de la app: termina aunque se cierre la cámara.
                    AppScope.launch {
                        val result = runCatching {
                            repo.savePhoto(e, zone, temp, cameraPitch = pitch, roll = roll, focalNorm = focal, conditions = conditions,
                                variant = limbSide.tag.takeIf { zone.hasLimbSides })
                        }.also { temp.delete() }
                        scope.launch {
                            capturing = false
                            result.onSuccess {
                                lastSaved = it
                                reload++
                                snackbar.showSnackbar("Guardada en el álbum ${e.id}")
                            }.onFailure {
                                snackbar.showSnackbar("No se pudo guardar: ${it.message}")
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    temp.delete()
                    scope.launch { snackbar.showSnackbar("Error al capturar: ${exception.message}") }
                }
            })
    }


    // Zoom asistido estable (ZoomController): zona muerta, tendencia sostenida, paso limitado y
    // espera a que el zoom se aplique y la cámara vuelva a enfocar antes de medir de nuevo.
    val zoomCtl = remember(lensFacing) { ZoomController() }
    val pivot = remember(zone) { com.photomed.app.align.ZoneAlignment.of(zone).pivot }
    fun focusOnPivot(onDone: (Boolean) -> Unit) {
        val cam = camera ?: return onDone(false)
        val w = previewView.width.toFloat(); val h = previewView.height.toFloat()
        if (w <= 0f || h <= 0f) return onDone(false)
        val point = previewView.meteringPointFactory.createPoint(pivot.x * w, (pivot.y / (4f / 3f)) * h)
        val f = cam.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(point).setAutoCancelDuration(4, java.util.concurrent.TimeUnit.SECONDS).build())
        f.addListener({ onDone(runCatching { f.get().isFocusSuccessful }.getOrDefault(false)) }, ContextCompat.getMainExecutor(context))
    }
    fun setZoom(r: Float) {
        val cam = camera ?: return
        val z = r.coerceIn(zoomRange.start, zoomRange.endInclusive)
        manualZoom = true
        zoomCtl.reset()
        zoomRatio = z
        cam.cameraControl.setZoomRatio(z)
    }
    LaunchedEffect(live, zoomAssist, manualZoom) {
        val e = live?.eval ?: return@LaunchedEffect
        val cam = camera ?: return@LaunchedEffect
        if (!zoomAssist || manualZoom || !matchingEnabled || e.status == MatchStatus.SEARCHING) return@LaunchedEffect
        val zs = cam.cameraInfo.zoomState.value ?: return@LaunchedEffect
        val target = zoomCtl.update(e.scale, zs.zoomRatio, System.currentTimeMillis())
            ?.coerceIn(zs.minZoomRatio, minOf(zs.maxZoomRatio, 4f)) ?: return@LaunchedEffect
        zoomCtl.focusing = true
        val zf = cam.cameraControl.setZoomRatio(target)
        zoomRatio = target
        zf.addListener({ focusOnPivot { zoomCtl.focusing = false } }, ContextCompat.getMainExecutor(context))
    }

    // Disparo automático: coincidencia sostenida ~0,4 s, sin zoom/enfoque en curso; enfoca en el
    // pivote anatómico, espera el resultado y dispara solo si sigue coincidiendo. Una vez por coincidencia.
    LaunchedEffect(status, autoCapture, matchingEnabled) {
        if (status != MatchStatus.MATCH || !matchingEnabled) { autoArmed = true; return@LaunchedEffect }
        if (autoCapture && autoArmed && !capturing) {
            kotlinx.coroutines.delay(400)
            while (zoomCtl.isSettling(System.currentTimeMillis())) kotlinx.coroutines.delay(100)
            val focused = kotlinx.coroutines.withTimeoutOrNull(2500) {
                kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { c -> focusOnPivot { ok -> if (c.isActive) c.resume(ok) } }
            }
            if (live?.eval?.status == MatchStatus.MATCH && !capturing) {
                autoArmed = false
                if (focused == false) snackbar.showSnackbar("Enfoque no confirmado: se tomó la foto igualmente")
                capture()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Barra superior
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(zone.label, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(exp?.patientName.orEmpty(), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
            IconToggleButton(
                checked = autoCapture,
                onCheckedChange = { autoCapture = it; prefs.edit().putBoolean("auto_capture", it).apply() },
                enabled = photos.isNotEmpty(),
            ) {
                Icon(Icons.Default.AutoMode, if (autoCapture) "Disparo automático activado" else "Disparo automático desactivado",
                    tint = if (autoCapture) GuideTone.MATCH.color else Color.White)
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Tune, "Ajustes de coincidencia", tint = Color.White) }
            IconButton(onClick = {
                flashMode = when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            }) {
                Icon(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    }, "Flash", tint = Color.White
                )
            }
        }

        // Vista previa 3:4 con capas
        val front = lensFacing == CameraSelector.LENS_FACING_FRONT
        // PreviewView ya espeja la cámara frontal; si el espejo está apagado se des-espeja.
        val displayMirrored = front && mirrorOn
        val guidance = if (matchingEnabled) CameraGuidance.of(live?.eval, front, displayMirrored) else null
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(4.dp))) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = if (front && !mirrorOn) -1f else 1f })

                // Capas en coordenadas de la foto: se espejan igual que la vista previa.
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = if (displayMirrored) -1f else 1f }) {
                    val g = ghost
                    if (showGhost && g != null) {
                        AsyncImage(
                            model = g.uri,
                            contentDescription = "Foto anterior superpuesta",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ghostAlpha },
                        )
                    }
                    GuideOverlay(zone, showContour = showContour, showGrid = showGrid, modifier = Modifier.fillMaxSize(),
                        tone = tone, adaptTo = if (adaptGuide && showGhost) refPoints else emptyMap(),
                        silhouette = if (adaptGuide && showGhost) refSilhouette else null,
                        side = if (zone.hasLimbSides) limbSide else LimbSide.BOTH,
                        // modo libre: contorno de la referencia (si se superpone) o de lo que se ve ahora
                        free = if (showGhost && refFree != null) refFree else live?.freeContour)
                    if (matchingEnabled) live?.let { LivePoints(it, tone, mirror = false) }
                }

                // Toque para enfocar
                // Toque para enfocar; pellizco para zoom manual
                val currentZoom by androidx.compose.runtime.rememberUpdatedState(zoomRatio)
                Box(Modifier.fillMaxSize().pointerInput(camera, mirrorOn, front) {
                    detectTapGestures { pos ->
                        val x = if (front && !mirrorOn) size.width - pos.x else pos.x
                        val point = previewView.meteringPointFactory.createPoint(x, pos.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    }
                }.pointerInput(camera) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) setZoom(currentZoom * zoomChange)
                    }
                })

                if (showLevel || (guidance?.rotateDeg ?: 0f) != 0f) {
                    LevelIndicator(tilt.value, guidance?.rotateDeg ?: 0f, Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                EdgeArrows(guidance, tone.color.takeIf { tone != GuideTone.NORMAL } ?: Color.White)
                val g = ghost
                if (showGhost && g != null) {
                    Text(
                        "Referencia: ${formatDate(g.takenAt)}" + if (zoomRatio > 1.01f) " · zoom %.1f×".format(zoomRatio) else "",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                            .background(Color(0x88000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutterFlash.value }.background(Color.White))
            }
        }
        if (matchingEnabled) {
            val dist = if (zone.category == com.photomed.app.data.ZoneCategory.FACIAL)
                live?.let { CameraGuidance.faceDistanceCm(it.currentPointsAll, focalNorm, zoomRatio) } else null
            RecommendationStrip(
                live, guidance, autoCapture,
                zoomInfo = if (zoomAssist && !manualZoom && zoomRatio > 1.01f) "zoom asistido %.1f×".format(zoomRatio) else null,
                distanceHint = dist?.let { d ->
                    if (d < 80f) "≈ %.0f cm: aléjese ≥ 1 m y use zoom".format(d) else "≈ %.0f cm".format(d)
                },
            )
        }

        ZoomBar(
            ratio = zoomRatio, range = zoomRange, manual = manualZoom, assist = zoomAssist,
            onZoom = { setZoom(it) },
            onResumeAssist = { manualZoom = false; zoomCtl.reset() },
        )

        // Modo individual de extremidades: ambas o una sola (misma zona)
        if (zone.hasLimbSides) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.s2),
                horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(when (zone.guide) { com.photomed.app.data.GuideType.HANDS -> "Mano"; com.photomed.app.data.GuideType.FEET -> "Pie"; else -> "Pierna" },
                    color = Color.White, style = MaterialTheme.typography.labelLarge)
                LimbSide.entries.forEach { sd ->
                    androidx.compose.material3.FilterChip(
                        selected = limbSide == sd,
                        onClick = {
                            limbSide = sd; prefs.edit().putString("limb_side_${zone.id}", sd.tag).apply()
                            photos.lastOrNull { LimbSide.ofFileName(it.displayName, zone) == sd }?.let { ghost = it }
                        },
                        label = { Text(if (sd == LimbSide.BOTH) "Ambas" else sd.label) },
                    )
                }
            }
        }

        // Controles de capas (objetivo táctil ≥ 48 dp, etiquetas visibles)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Md3Spacing.s2, vertical = Md3Spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s3),
        ) {
            LayerToggle("Cuadrícula", Icons.Default.GridOn, showGrid) { showGrid = it }
            LayerToggle("Contorno", Icons.Default.Face, showContour) { showContour = it }
            LayerToggle("Superponer", Icons.Default.Layers, showGhost, enabled = photos.isNotEmpty()) { showGhost = it }
            LayerToggle("Nivel", Icons.Default.Straighten, showLevel) { showLevel = it }
            LayerToggle("Asistir", Icons.Default.CenterFocusStrong, liveMatch) { liveMatch = it }
            LayerToggle("Espejo", Icons.Default.Flip, mirrorOn, enabled = lensFacing == CameraSelector.LENS_FACING_FRONT) {
                mirrorOn = it; prefs.edit().putBoolean("mirror_front", it).apply()
            }
            LayerToggle("Adaptar", Icons.Default.AutoFixHigh, adaptGuide, enabled = refPoints.isNotEmpty() || refSilhouette != null) { adaptGuide = it }
        }

        if (showGhost && photos.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Opacidad", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = ghostAlpha,
                    onValueChange = { ghostAlpha = it },
                    valueRange = 0.05f..0.9f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(32.dp),
                    
                )
                Text("${(ghostAlpha * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(36.dp))
            }
            LazyRow(
                Modifier.fillMaxWidth().height(58.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(photos.reversed(), key = { it.key }) { p ->
                    val selected = p.key == ghost?.key
                    AsyncImage(
                        model = p.uri,
                        contentDescription = formatDate(p.takenAt),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(42.dp, 56.dp).clip(RoundedCornerShape(6.dp))
                            .border(if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(6.dp))
                            .clickable { ghost = p; showGhost = true }
                    )
                }
            }
        }

        // Disparador
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(52.dp)) {
                photos.lastOrNull()?.let {
                    AsyncImage(it.uri, "Última fotografía", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small))
                }
            }
            Box(
                Modifier.size(76.dp).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                    .background(if (capturing) MaterialTheme.colorScheme.outline else Color.White)
                    .clickable(enabled = !capturing && exp != null, onClickLabel = "Tomar fotografía") { capture() }
                    .semantics { contentDescription = "Disparador" },
                contentAlignment = Alignment.Center,
            ) { if (capturing) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                    else CameraSelector.LENS_FACING_BACK
                },
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.Default.FlipCameraAndroid, "Cambiar cámara", tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp))
    if (showSettings) {
        MatchSettingsSheet(
            autoCapture = autoCapture,
            threshold = threshold,
            engine = engine,
            onEngine = { engine = it; com.photomed.app.align.FaceEngine.set(context, it) },
            zoomAssist = zoomAssist,
            onZoomAssist = {
                zoomAssist = it; prefs.edit().putBoolean("zoom_assist", it).apply()
                if (!it) { camera?.cameraControl?.setZoomRatio(1f); zoomRatio = 1f }
            },
            onAutoCapture = { autoCapture = it; prefs.edit().putBoolean("auto_capture", it).apply() },
            onThreshold = { threshold = it; prefs.edit().putFloat("match_threshold_v2", it).apply() },
            useGaze = useGaze,
            onUseGaze = { useGaze = it; prefs.edit().putBoolean(com.photomed.app.align.AlignOptions.PREF_GAZE, it).apply() },
            onDismiss = { showSettings = false },
        )
    }
    }
}


/** Puntos anatómicos: referencia (anillos) y cuadro actual (puntos), en el color de la guía. */
@Composable
private fun LivePoints(frame: LiveFrame, tone: GuideTone, mirror: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        fun map(p: Pt) = Offset((if (mirror) 1f - p.x else p.x) * size.width, p.y * size.width)
        frame.referencePoints.forEach { drawCircle(Color(0xFFFFD54F), 4.dp.toPx(), map(it), style = Stroke(1.2f.dp.toPx())) }
        frame.currentPoints.forEach { drawCircle(tone.color, 2.2f.dp.toPx(), map(it)) }
    }
}

/** Ajustes de coincidencia: disparo automático y umbral mínimo de puntos coincidentes. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MatchSettingsSheet(
    autoCapture: Boolean,
    threshold: Float,
    zoomAssist: Boolean,
    onZoomAssist: (Boolean) -> Unit,
    engine: com.photomed.app.align.FaceEngine = com.photomed.app.align.FaceEngine.ML_KIT,
    onEngine: (com.photomed.app.align.FaceEngine) -> Unit = {},
    onAutoCapture: (Boolean) -> Unit,
    onThreshold: (Float) -> Unit,
    useGaze: Boolean = true,
    onUseGaze: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {
    SafeBottomSheet(onDismiss = onDismiss) {
        MatchSettingsContent(autoCapture, threshold, onAutoCapture, onThreshold, zoomAssist, onZoomAssist, engine, onEngine, useGaze, onUseGaze)
    }
}

@Composable
fun MatchSettingsContent(
    autoCapture: Boolean, threshold: Float, onAutoCapture: (Boolean) -> Unit, onThreshold: (Float) -> Unit,
    zoomAssist: Boolean = true, onZoomAssist: (Boolean) -> Unit = {},
    engine: com.photomed.app.align.FaceEngine = com.photomed.app.align.FaceEngine.ML_KIT,
    onEngine: (com.photomed.app.align.FaceEngine) -> Unit = {},
    useGaze: Boolean = true,
    onUseGaze: (Boolean) -> Unit = {},
) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Coincidencia con la referencia", style = MaterialTheme.typography.titleLarge)
        Row(
            Modifier.fillMaxWidth().toggleable(autoCapture, role = Role.Switch, onValueChange = onAutoCapture),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Disparo automático", style = MaterialTheme.typography.bodyLarge)
                Text("Toma la foto sola cuando los puntos de referencia coinciden (se mantiene ~0,4 s).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = autoCapture, onCheckedChange = null)
        }
        Row(
            Modifier.fillMaxWidth().toggleable(zoomAssist, role = Role.Switch, onValueChange = onZoomAssist),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Zoom asistido", style = MaterialTheme.typography.bodyLarge)
                Text("Ajusta el zoom para igualar el tamaño de la referencia; permite alejarse (≥ 1 m en el rostro) " +
                    "sin perder exactitud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = zoomAssist, onCheckedChange = null)
        }
        Row(
            Modifier.fillMaxWidth().toggleable(useGaze, role = Role.Switch, onValueChange = onUseGaze),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Exigir la misma mirada", style = MaterialTheme.typography.bodyLarge)
                Text("La coincidencia también requiere que el paciente mire en la misma dirección que en la referencia " +
                    "(posición del iris dentro del ojo).", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = useGaze, onCheckedChange = null)
        }
        Text("Motor de puntos faciales", style = MaterialTheme.typography.bodyLarge)
        com.photomed.app.align.FaceEngine.entries.forEach { e ->
            Row(
                Modifier.fillMaxWidth().selectable(engine == e, role = Role.RadioButton) { onEngine(e) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = engine == e, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(e.label, style = MaterialTheme.typography.bodyMedium)
                    Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Umbral mínimo de puntos coincidentes: ${(threshold * 100).toInt()} %", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = threshold * 100, onValueChange = { onThreshold((it.toInt()) / 100f) },
            valueRange = 50f..100f, steps = 49,
            modifier = Modifier.semantics { contentDescription = "Umbral de puntos coincidentes" },
        )
        Text("Un punto coincide si está a menos del 3 % del ancho de su posición en la foto de referencia. " +
            "Por defecto 80 %.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onThreshold(LiveMatch.DEFAULT_THRESHOLD) }) { Text("Restablecer 80 %") }
    }
}

/** Cuadro de análisis enderezado (rotación del sensor) y recortado al encuadre 3:4 del viewport. */
private fun ImageProxy.toUprightCrop(width: Int): Bitmap {
    val full = toBitmap()
    val r = cropRect
    val cropped = Bitmap.createBitmap(full, r.left, r.top, r.width(), r.height())
    val m = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
    val upright = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, m, true)
    val out = ImageOps.centerCrop34(upright, width)
    // liberar intermedios (≈ 15 bitmaps/s): menos presión sobre la memoria y el recolector
    for (b in listOf(full, cropped, upright)) if (b !== out && !b.isRecycled) b.recycle()
    return out
}

/** Focal de la cámara en unidades de alto de imagen (lado largo del sensor), para la corrección de perspectiva. */
@OptIn(ExperimentalCamera2Interop::class)
private fun focalNormOf(camera: Camera): Float? = runCatching {
    val info = Camera2CameraInfo.from(camera.cameraInfo)
    val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val size = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    if (focal == null || size == null) null else focal / maxOf(size.width, size.height)
}.getOrNull()

@Composable
private fun rememberTilt(enabled: Boolean): State<Tilt> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Tilt(0f, 0f)) }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val g = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val a = 0.15f // filtro paso bajo
                for (i in 0..2) g[i] = g[i] + a * (event.values[i] - g[i])
                val roll = Math.toDegrees(atan2(g[0].toDouble(), g[1].toDouble())).toFloat()
                val pitch = Math.toDegrees(atan2(g[2].toDouble(), sqrt((g[0] * g[0] + g[1] * g[1]).toDouble()))).toFloat()
                state.value = Tilt(roll, pitch)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (enabled && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
}
