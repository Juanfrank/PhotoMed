package com.photomed.app.ui.camera

import android.Manifest
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import com.photomed.app.align.LiveMatch
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
import kotlin.coroutines.resume
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
    var threshold by remember { mutableFloatStateOf(prefs.getFloat("match_threshold", LiveMatch.DEFAULT_THRESHOLD)) }
    var showSettings by remember { mutableStateOf(false) }
    var autoArmed by remember { mutableStateOf(true) }
    var live by remember { mutableStateOf<LiveFrame?>(null) }
    var focalNorm by remember { mutableStateOf<Float?>(null) }
    val tilt = rememberTilt(true)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val matcher = remember(zone) { LiveMatcher(zone) }
    matcher.threshold = threshold
    val lastAnalysis = remember { AtomicLong(0L) }
    val matchingEnabled = liveMatch && showGhost && ghost != null
    val matchingFlag = remember { AtomicBoolean(false) }
    matchingFlag.set(matchingEnabled)
    DisposableEffect(matcher) {
        onDispose { analysisExecutor.execute { matcher.close() } }
    }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    // Referencia para la coincidencia en vivo: la foto superpuesta (encuadre 3:4).
    LaunchedEffect(ghost?.key) {
        val g = ghost ?: return@LaunchedEffect
        live = null
        val bmp = runCatching { ImageOps.load34(context, g.uri, 360) }.getOrNull() ?: return@LaunchedEffect
        analysisExecutor.execute { runCatching { matcher.setReference(bmp) } }
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
        withContext(Dispatchers.IO) {
            val e = repo.getExpediente(expId)
            exp = e
            photos = e?.let { repo.photos(it, zone) }.orEmpty()
        }
        if (ghost == null || photos.none { it.key == ghost?.key }) ghost = photos.lastOrNull()
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
                a.setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        val now = System.currentTimeMillis()
                        if (matchingFlag.get() && now - lastAnalysis.get() >= 180) {
                            lastAnalysis.set(now)
                            live = matcher.analyze(proxy.toUprightCrop(360))
                        }
                    } catch (_: Exception) {
                    } finally {
                        proxy.close()
                    }
                }
            }
    }

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
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo iniciar la cámara: ${e.message}", Toast.LENGTH_LONG).show()
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
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                repo.savePhoto(
                                    e, zone, temp,
                                    cameraPitch = Perspective.cameraPitchFromDevice(
                                        tilt.value.pitch, lensFacing == CameraSelector.LENS_FACING_FRONT),
                                    roll = tilt.value.roll,
                                    focalNorm = focalNorm,
                                )
                            }.also { temp.delete() }
                        }
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

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    temp.delete()
                    scope.launch { snackbar.showSnackbar("Error al capturar: ${exception.message}") }
                }
            })
    }


    // Disparo automático: coincidencia sostenida ~0,4 s; una captura por cada coincidencia.
    LaunchedEffect(status, autoCapture, matchingEnabled) {
        if (status != MatchStatus.MATCH || !matchingEnabled) { autoArmed = true; return@LaunchedEffect }
        if (autoCapture && autoArmed && !capturing) {
            kotlinx.coroutines.delay(400)
            if (live?.eval?.status == MatchStatus.MATCH && !capturing) {
                autoArmed = false
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
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(4.dp))) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                val g = ghost
                if (showGhost && g != null) {
                    AsyncImage(
                        model = g.uri,
                        contentDescription = "Foto anterior superpuesta",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            alpha = ghostAlpha
                            // La vista previa frontal está espejada; la foto guardada no.
                            scaleX = if (lensFacing == CameraSelector.LENS_FACING_FRONT) -1f else 1f
                        }
                    )
                }
                GuideOverlay(zone, showContour = showContour, showGrid = showGrid, modifier = Modifier.fillMaxSize(), tone = tone)
                if (matchingEnabled) {
                    live?.let { LivePoints(it, tone, mirror = lensFacing == CameraSelector.LENS_FACING_FRONT) }
                }

                // Toque para enfocar
                Box(Modifier.fillMaxSize().pointerInput(camera) {
                    detectTapGestures { pos ->
                        val point = previewView.meteringPointFactory.createPoint(pos.x, pos.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    }
                })

                if (showLevel) {
                    LevelIndicator(tilt.value, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                }
                if (matchingEnabled) {
                    MatchBanner(live, autoCapture, Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp))
                }
                if (showGhost && g != null) {
                    Text(
                        "Referencia: ${formatDate(g.takenAt)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                            .background(Color(0x88000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutterFlash.value }.background(Color.White))
            }
        }

        // Controles de capas (objetivo táctil ≥ 48 dp, etiquetas visibles)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.s2, vertical = Md3Spacing.s1),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LayerToggle("Cuadrícula", Icons.Default.GridOn, showGrid) { showGrid = it }
            LayerToggle("Contorno", Icons.Default.Face, showContour) { showContour = it }
            LayerToggle("Superponer", Icons.Default.Layers, showGhost, enabled = photos.isNotEmpty()) { showGhost = it }
            LayerToggle("Nivel", Icons.Default.Straighten, showLevel) { showLevel = it }
            LayerToggle("Coincidir", Icons.Default.CenterFocusStrong, liveMatch, enabled = photos.isNotEmpty()) { liveMatch = it }
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
            onAutoCapture = { autoCapture = it; prefs.edit().putBoolean("auto_capture", it).apply() },
            onThreshold = { threshold = it; prefs.edit().putFloat("match_threshold", it).apply() },
            onDismiss = { showSettings = false },
        )
    }
    }
}


@Composable
private fun LayerToggle(label: String, icon: ImageVector, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconToggleButton(
            checked = checked && enabled,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.size(48.dp).semantics { stateDescription = if (checked) "Activado" else "Desactivado" },
        ) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
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

/** Estado de la coincidencia con la foto de referencia e indicaciones para conseguirla. */
@Composable
private fun MatchBanner(frame: LiveFrame?, autoCapture: Boolean, modifier: Modifier = Modifier) {
    val eval = frame?.eval
    val pct = eval?.pointFraction?.let { (it * 100).toInt() }
    val thr = ((eval?.threshold ?: LiveMatch.DEFAULT_THRESHOLD) * 100).toInt()
    val (color, title) = when (eval?.status) {
        MatchStatus.MATCH -> GuideTone.MATCH.color to (if (autoCapture) "✓ Coincide · capturando" else "✓ Coincide con la referencia")
        MatchStatus.CLOSE -> GuideTone.CLOSE.color to "Casi alineado"
        MatchStatus.FAR -> Color.White to "Alinee con la referencia"
        else -> Color.White to "Buscando referencias anatómicas…"
    }
    Column(
        modifier.background(Color(0xB0000000), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = color, style = MaterialTheme.typography.labelLarge)
        if (pct != null) {
            Text("Puntos coincidentes $pct % · umbral $thr %" + (frame.totalPoints.takeIf { it > 0 }?.let { " · $it puntos" } ?: ""),
                color = color, style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(
                progress = { eval.pointFraction ?: 0f },
                color = color, trackColor = Color(0x55FFFFFF),
                modifier = Modifier.width(180.dp).padding(vertical = 3.dp),
            )
        }
        eval?.hints?.take(2)?.forEach { Text(it, color = Color.White, style = MaterialTheme.typography.labelSmall) }
        if (frame != null && frame.method.isNotEmpty() && eval?.status != MatchStatus.SEARCHING) {
            Text("por ${frame.method}" + if (autoCapture) " · disparo automático" else "",
                color = Color(0xFFBDBDBD), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Ajustes de coincidencia: disparo automático y umbral mínimo de puntos coincidentes. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MatchSettingsSheet(
    autoCapture: Boolean,
    threshold: Float,
    onAutoCapture: (Boolean) -> Unit,
    onThreshold: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) { MatchSettingsContent(autoCapture, threshold, onAutoCapture, onThreshold) }
}

@Composable
fun MatchSettingsContent(autoCapture: Boolean, threshold: Float, onAutoCapture: (Boolean) -> Unit, onThreshold: (Float) -> Unit) {
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
        Text("Umbral mínimo de puntos coincidentes: ${(threshold * 100).toInt()} %", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = threshold * 100, onValueChange = { onThreshold((it.toInt()) / 100f) },
            valueRange = 80f..100f, steps = 19,
            modifier = Modifier.semantics { contentDescription = "Umbral de puntos coincidentes" },
        )
        Text("Un punto coincide si está a menos del 1,5 % del ancho de su posición en la foto de referencia. " +
            "Por defecto 99 %.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onThreshold(LiveMatch.DEFAULT_THRESHOLD) }) { Text("Restablecer 99 %") }
    }
}

/** Cuadro de análisis enderezado (rotación del sensor) y recortado al encuadre 3:4 del viewport. */
private fun ImageProxy.toUprightCrop(width: Int): Bitmap {
    val full = toBitmap()
    val r = cropRect
    val cropped = Bitmap.createBitmap(full, r.left, r.top, r.width(), r.height())
    val m = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
    val upright = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, m, true)
    return ImageOps.centerCrop34(upright, width)
}

/** Focal de la cámara en unidades de alto de imagen (lado largo del sensor), para la corrección de perspectiva. */
@OptIn(ExperimentalCamera2Interop::class)
private fun focalNormOf(camera: Camera): Float? = runCatching {
    val info = Camera2CameraInfo.from(camera.cameraInfo)
    val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val size = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    if (focal == null || size == null) null else focal / maxOf(size.width, size.height)
}.getOrNull()

/** Inclinación lateral (roll) y cabeceo (pitch) en grados. */
data class Tilt(val roll: Float, val pitch: Float)

@Composable
private fun LevelIndicator(tilt: Tilt, modifier: Modifier = Modifier) {
    val level = abs(tilt.roll) < 1.5f && abs(tilt.pitch) < 3f
    val color = if (level) Color(0xFF66BB6A) else Color(0xFFFFB74D)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(120.dp, 20.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            drawLine(Color(0x88FFFFFF), Offset(0f, c.y), Offset(size.width, c.y), 1.dp.toPx())
            rotate(-tilt.roll, c) {
                drawLine(color, Offset(10.dp.toPx(), c.y), Offset(size.width - 10.dp.toPx(), c.y), 3.dp.toPx())
            }
            drawCircle(color, 3.dp.toPx(), c)
        }
        Text(
            "Inclinación ${"%.1f".format(tilt.roll)}° · Cabeceo ${"%.0f".format(tilt.pitch)}°",
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

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
