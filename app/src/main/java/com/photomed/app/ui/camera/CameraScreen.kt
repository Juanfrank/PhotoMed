package com.photomed.app.ui.camera

import androidx.compose.ui.graphics.asImageBitmap
import android.Manifest
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.material.icons.filled.Contrast
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
import com.photomed.app.data.expression
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
fun CameraScreen(
    expId: String, zone: Zone, onBack: () -> Unit,
    /** Secuencia de zonas (plantilla): tras guardar se pasa a la siguiente; null = fin. */
    sequence: List<Zone> = emptyList(),
    onNext: (Zone?) -> Unit = {},
    /** Abrir Comparar con estas fotos (desde la revisión tras la captura). */
    onCompare: (List<String>) -> Unit = {},
    /** Protocolo en curso y su paso (zona, gesto, cantidad, coincidencia, luz y referencia sin imagen). */
    protocol: com.photomed.app.data.Protocol? = null,
    stepIndex: Int = 0,
    /** Paso siguiente del protocolo (índice), o null al terminar. */
    onProtocolNext: (Int?) -> Unit = {},
) = ImmersiveTheme {
    val context = LocalContext.current
    // la cámara y las guías se calculan en vertical: esta pantalla se mantiene en vertical (el resto gira)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val act = context as? android.app.Activity
        val before = act?.requestedOrientation
        act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { act?.requestedOrientation = before ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
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
            CameraContent(expId, zone, onBack, sequence, onNext, onCompare, protocol, stepIndex, onProtocolNext)
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
private fun CameraContent(expId: String, zone: Zone, onBack: () -> Unit, sequence: List<Zone>, onNext: (Zone?) -> Unit,
                          onCompare: (List<String>) -> Unit = {}, protocol: com.photomed.app.data.Protocol? = null, stepIndex: Int = 0,
                          onProtocolNext: (Int?) -> Unit = {}) {
    // paso del protocolo (si se está ejecutando uno): fija gesto, coincidencia, luz, cámara y referencia
    val step = protocol?.steps?.getOrNull(stepIndex)
    val protoRef = step?.ref
    /** Fotos ya guardadas en este paso (se piden [ProtocolStep.count]). */
    var shotsDone by remember(stepIndex) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()

    var exp by remember { mutableStateOf<Expediente?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }

    var lensFacing by remember { mutableIntStateOf(if (step?.lens == "front") CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    // cuadrícula apagada por defecto (menos líneas sobre la imagen)
    var showGrid by remember { mutableStateOf(false) }
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
    // foto recién tomada en revisión (Guardar / Repetir); se conserva al volver de Comparar
    var reviewName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var lastNear by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var liveMatch by remember { mutableStateOf(true) }
    val prefs = remember { context.getSharedPreferences("photomed", android.content.Context.MODE_PRIVATE) }
    var autoCapture by remember { mutableStateOf(step?.autoCapture ?: prefs.getBoolean("auto_capture", false)) }
    /** Vibración (y sonido al coincidir) según el grado de coincidencia. */
    var feedback by remember { mutableStateOf(prefs.getBoolean("guide_feedback", true)) }
    /** Disparo automático también "casi" alineado (≥ 80 %): la alineación fina se hace en Comparar. */
    var nearCapture by remember { mutableStateOf(prefs.getBoolean("auto_capture_near", true)) }
    /** Ráfaga de 3 fotos: se guarda la de mayor coincidencia. */
    var burst by remember { mutableStateOf(prefs.getBoolean("burst", true)) }
    // Medición de relieve (estéreo fotométrico con la pantalla, cámara frontal)
    var reliefMode by remember { mutableStateOf(prefs.getBoolean("relief_mode", false)) }
    var psPattern by remember { mutableStateOf<com.photomed.app.align.PhotometricStereo.Pattern?>(null) }
    val frameGrab = remember { FrameGrab() }
    /**
     * Modo experto: líneas de referencia siempre, puntos detectados, porcentajes, franja de texto,
     * barra de zoom y todas las capas. El modo simple (por defecto) muestra solo el contorno (su color
     * dice qué tan lejos se está), un icono con la corrección principal y el anillo del disparador.
     */
    var expert by remember { mutableStateOf(prefs.getBoolean("camera_expert", false)) }
    /** Última interacción con el zoom (pellizco o barra): la barra se muestra unos segundos. */
    var zoomTouchedAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var opacityOpen by remember { mutableStateOf(false) }
    var opacityTouchedAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    /** Fantasma de la referencia como bordes (más legible que la foto translúcida). */
    var ghostEdges by remember { mutableStateOf(prefs.getBoolean("ghost_edges", false)) }
    // Modo individual de extremidades (manos, pies, piernas): lado recordado por zona
    var limbSide by remember(zone) { mutableStateOf(LimbSide.ofTag(prefs.getString("limb_side_${zone.id}", null))) }
    // Expresión facial de la toma (arrugas dinámicas): reposo por defecto en cada zona
    val expressions = remember(zone) { com.photomed.app.data.FacialExpression.forZone(zone) }
    var expression by remember(zone, stepIndex) { mutableStateOf(step?.expression ?: com.photomed.app.data.FacialExpression.NEUTRAL) }
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
    var refEdges by remember { mutableStateOf<com.photomed.app.align.FreeContour?>(null) }
    var refHairline by remember { mutableStateOf<List<com.photomed.app.align.Pt>?>(null) }
    /** Condiciones guardadas con la referencia (zoom, distancia…). */
    var refCond by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var adaptGuide by remember { mutableStateOf(true) }
    /** Giro (pasos de 90°) y espejo de la referencia superpuesta, o de la guía si no hay referencia. */
    var refOrient by remember { mutableStateOf(com.photomed.app.align.ImageEdit.NONE) }
    // guía adaptada a la referencia SIN girar (se dibuja girada entera cuando la referencia está girada)
    var refGuide by remember { mutableStateOf<com.photomed.app.align.LiveMatcher.GuideRef?>(null) }
    var ghostBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    /** Primera toma (sin referencia): el rostro coincide con el marco de la guía → la guía se ajusta a la cara. */
    var framedFace by remember { mutableStateOf(false) }
    var autoArmed by remember { mutableStateOf(true) }
    var live by remember { mutableStateOf<LiveFrame?>(null) }
    var focalNorm by remember { mutableStateOf<Float?>(null) }
    val tilt = rememberTilt(true)
    // Hilo de análisis con motor intercambiable: el analizador usa siempre el LiveMatcher actual;
    // al cambiar de motor el anterior se cierra en el mismo hilo (sin usar uno cerrado).
    val pipeline = remember { AnalysisPipeline<LiveMatcher>(Executors.newSingleThreadExecutor()) }
    val engine = com.photomed.app.align.FaceEngine.MEDIAPIPE
    val matcher = remember(zone, engine) { LiveMatcher(zone, context.applicationContext, engine) }
    SideEffect { pipeline.swap(matcher) }
    matcher.threshold = step?.threshold ?: threshold
    matcher.useGaze = step?.gaze ?: useGaze
    matcher.measureLight = step != null && (step.minLight != null || step.maxLight != null || step.maxLightAsym != null)
    DisposableEffect(Unit) { onDispose { pipeline.shutdown() } }
    val lastAnalysis = remember { AtomicLong(0L) }
    // Asistencia en vivo: con referencia (foto superpuesta) iguala el encuadre; sin ella, estandariza la toma.
    val matchingEnabled = liveMatch
    val referenceMode = protoRef != null || (showGhost && ghost != null)
    matcher.useReference = referenceMode
    matcher.expression = expression
    val matchingFlag = remember { AtomicBoolean(false) }
    matchingFlag.set(matchingEnabled && psPattern == null && reviewName == null)

    // Referencia para la coincidencia en vivo: la foto superpuesta (encuadre 3:4), con el motor elegido.
    LaunchedEffect(ghost?.key, matcher, refOrient) {
        val g = ghost ?: return@LaunchedEffect
        live = null
        refSilhouette = null
        refFree = null
        refEdges = null
        // referencia girada / espejada: se transforma la imagen misma (fantasma, puntos, bordes, guía
        // adaptada y coincidencia quedan coherentes)
        fun oriented(b: android.graphics.Bitmap, w: Int) = if (refOrient.isIdentity) b else ImageOps.centerCrop34(refOrient.apply(b), w)
        ghostBitmap = runCatching { oriented(ImageOps.load34(context, g.uri, 720), 720).asImageBitmap() }.getOrNull()
        val upright = runCatching { ImageOps.load34(context, g.uri, 360) }.getOrNull() ?: return@LaunchedEffect
        val bmp = oriented(upright, 360)
        val m = matcher
        val orient = refOrient
        refGuide = null
        pipeline.post {
            runCatching { m.setReference(bmp) }
            refGuide = if (orient.isIdentity) null else runCatching { m.guideOf(upright) }.getOrNull()
            refPoints = m.referencePoints
            refSilhouette = m.referenceSilhouette
            refFree = m.referenceFree
            refEdges = m.referenceEdges
            refHairline = m.referenceHairline
        }
    }
    // Referencia del protocolo (sin imagen): puntos, pose, línea del pelo y perfil; distancia y zoom de la toma
    LaunchedEffect(protoRef, matcher, camera) {
        val r = protoRef ?: return@LaunchedEffect
        ghost = null; live = null
        val m = matcher
        pipeline.post {
            m.setReferenceFeatures(com.photomed.app.align.ProtocolReference.detectionOf(r), r.hairline, r.profile)
            refPoints = m.referencePoints; refHairline = m.referenceHairline
            refSilhouette = null; refFree = null; refEdges = null
        }
        refCond = buildMap {
            r.distanceCm?.let { put("DistanceCm", "%.0f".format(java.util.Locale.US, it)) }
            r.zoom?.let { put("Zoom", "%.2f".format(java.util.Locale.US, it)) }
        }
        val z = r.zoom ?: return@LaunchedEffect
        val cam = camera ?: return@LaunchedEffect
        if (zoomAssist && !manualZoom && z > 1.01f) {
            val zz = z.coerceIn(zoomRange.start, zoomRange.endInclusive)
            cam.cameraControl.setZoomRatio(zz); zoomRatio = zz
        }
    }
    val status = live?.eval?.status
    // tomas de ráfagas interrumpidas (cámara cerrada a mitad): se borran al abrir
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val old = System.currentTimeMillis() - 10 * 60_000
            context.cacheDir.listFiles { f -> f.name.startsWith("capture_") && f.lastModified() < old }?.forEach { it.delete() }
        }
    }
    val toneGen = remember { runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { toneGen?.release() } }
    LaunchedEffect(status) {
        if (status == MatchStatus.MATCH) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (feedback) runCatching { toneGen?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 160) }
        }
    }
    // Pulsos que se aceleran al acercarse a la coincidencia (sin mirar la pantalla)
    val degree = if (matchingEnabled) GuideTone.degree(live?.eval) else null
    val degreeStep = degree?.let { (it * 10).toInt() }
    LaunchedEffect(degreeStep, feedback) {
        val d = degreeStep ?: return@LaunchedEffect
        if (!feedback || d < 3 || d >= 10) return@LaunchedEffect
        val interval = (1000L - d * 85L).coerceAtLeast(200L)
        while (true) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); kotlinx.coroutines.delay(interval) }
    }
    // ¿La cara del cuadro coincide con el marco de la guía? (solo sin foto de referencia superpuesta)
    LaunchedEffect(live, refPoints, showGhost) {
        val art = GuideArtByZone[zone.id]
        val pts = live?.currentPointsAll.orEmpty()
        val noRef = refPoints.isEmpty() || !showGhost
        val fit = if (noRef && art != null && art.controls.isNotEmpty() && pts.isNotEmpty())
            com.photomed.app.align.FaceFraming.of(art.controls, FaceControlKeys, pts) else null
        framedFace = com.photomed.app.align.FaceFraming.matches(fit, framedFace)
    }
    // si la segmentación de pelo y piel no carga en este teléfono, decirlo una vez con el motivo
    var hairSkinWarned by remember { mutableStateOf(false) }
    LaunchedEffect(live) {
        val h = live?.eval?.hints?.firstOrNull { it.startsWith("Detector (pelo y piel)") } ?: return@LaunchedEffect
        if (!hairSkinWarned) { hairSkinWarned = true; snackbar.showSnackbar("Línea del pelo no disponible. $h") }
    }
    val tone = GuideTone.of(live?.eval, matchingEnabled)
    /**
     * Cuero cabelludo: cada vista pide un ángulo de la cámara trasera hacia abajo (vértice cenital
     * 90°, corona ≈ 75°, parietales ≈ 45°; cabeceo del sensor de gravedad); fuera de tolerancia se
     * indica cuánto inclinarla.
     */
    fun overheadHint(): String? {
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) return null
        return com.photomed.app.align.Standardization.cameraAngleHint(zone, tilt.value.pitch)
    }
    /**
     * Distancia: con referencia, la de su toma como objetivo ("N cm → M cm" si difiere > 15 %);
     * sin referencia, aviso de efecto selfie (< 80 cm).
     */
    fun distanceHint(): String? {
        overheadHint()?.let { return it }
        if (zone.category != com.photomed.app.data.ZoneCategory.FACIAL) return null
        val d = live?.let { CameraGuidance.faceDistanceCm(it.currentPointsAll, focalNorm, zoomRatio) } ?: return null
        val target = refCond["DistanceCm"]?.toFloatOrNull()?.takeIf { (showGhost && ghost != null) || protoRef != null }
        return when {
            target != null && kotlin.math.abs(d - target) / target > 0.15f -> "Distancia: %.0f cm → %.0f cm".format(d, target)
            target != null -> null
            d < 80f -> "≈ %.0f cm: aléjese ≥ 1 m y use zoom".format(d)
            else -> "≈ %.0f cm".format(d)
        }
    }

    // Referencia: condiciones guardadas; su zoom se aplica al elegirla (salvo zoom manual)
    LaunchedEffect(ghost?.key, camera) {
        val g = ghost ?: run { if (protoRef == null) refCond = emptyMap(); return@LaunchedEffect }
        refCond = withContext(Dispatchers.IO) { runCatching { repo.readInfo(g)?.conditions }.getOrNull() }.orEmpty()
        val z = refCond["Zoom"]?.toFloatOrNull() ?: return@LaunchedEffect
        val cam = camera ?: return@LaunchedEffect
        if (zoomAssist && !manualZoom && z > 1.01f) {
            val zz = z.coerceIn(zoomRange.start, zoomRange.endInclusive)
            cam.cameraControl.setZoomRatio(zz); zoomRatio = zz
        }
    }

    LaunchedEffect(reload) {
        val (e, list) = withContext(Dispatchers.IO) {
            val e = repo.getExpediente(expId)
            e to e?.let { repo.photos(it, zone) }.orEmpty()
        }
        exp = e; photos = list
        // protocolo con referencia: la referencia es la del protocolo, no una foto del expediente
        if (protoRef == null && (ghost == null || photos.none { it.key == ghost?.key }))
            ghost = photos.lastOrNull { LimbSide.ofFileName(it.displayName, zone) == limbSide && it.expression == expression }
                ?: photos.lastOrNull { it.expression == expression } ?: photos.lastOrNull()
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
    // Flash: la cámara frontal usa el flash de pantalla (blanco a brillo máximo), como las selfies.
    val screenFlash = rememberScreenFlash()
    val ambientLux by rememberAmbientLux(lensFacing == CameraSelector.LENS_FACING_FRONT && flashMode == ImageCapture.FLASH_MODE_AUTO)
    val effectiveFlash = effectiveFlashMode(lensFacing == CameraSelector.LENS_FACING_FRONT, flashMode, ambientLux)
    LaunchedEffect(imageCapture, effectiveFlash) {
        runCatching {
            if (effectiveFlash == ImageCapture.FLASH_MODE_SCREEN) imageCapture.screenFlash = screenFlash
            imageCapture.flashMode = effectiveFlash
        }.onFailure { imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF }
    }
    val analysisRef = remember { java.util.concurrent.atomic.AtomicReference<ImageAnalysis?>() }
    val reliefHi = reliefMode && lensFacing == CameraSelector.LENS_FACING_FRONT && zone.isFaceZone()
    val analysis = remember(lensFacing, reliefHi) {
        ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    // la medición de relieve necesita más detalle (1280 × 960)
                    .setResolutionStrategy(ResolutionStrategy(if (reliefHi) Size(1280, 960) else Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build().also { a ->
                analysisRef.set(a)
                a.setAnalyzer({ r -> pipeline.post(r::run) }) { proxy ->
                    try {
                        val now = System.currentTimeMillis()
                        // secuencia de relieve: el fotograma pedido tiene prioridad
                        if (frameGrab.offer(now) { proxy.toUprightCrop(720) }) return@setAnalyzer
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

    LaunchedEffect(lensFacing, reliefHi) {
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
        // motor que dio los puntos: MediaPipe, o ML Kit si MediaPipe no estuvo disponible
        put("Engine", if (matcher.detectorUnavailable) "ML Kit (respaldo)" else engine.label)
        if (zone.hasLimbSides) put("Side", limbSide.label)
        put("Expression", expression.label)
        put("Zoom", "%.2f".format(java.util.Locale.US, zoomRatio))
        if (protocol != null && step != null) {
            putAll(com.photomed.app.data.ProtocolResults.conditions(protocol, stepIndex, shotsDone + 1))
            put("ProtocolThreshold", "%.0f".format(java.util.Locale.US, (step.threshold ?: threshold) * 100))
            if (protoRef != null) put("ProtocolReference", "sí")
        }
        live?.light?.let { l ->
            put("LightFace", "%.2f".format(java.util.Locale.US, l.face))
            val m = maxOf(l.leftCheek, l.rightCheek)
            if (m > 0f) put("LightAsymmetry", "%.2f".format(java.util.Locale.US, kotlin.math.abs(l.leftCheek - l.rightCheek) / m))
        }
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

    /** Una toma a [file]; true si se guardó. */
    suspend fun shoot(file: File): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { c ->
        imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { if (c.isActive) c.resume(true) }
                override fun onError(exception: ImageCaptureException) { file.delete(); if (c.isActive) c.resume(false) }
            })
    }

    /** Puntuación de una foto frente a la referencia, en el hilo de análisis (sin tocar el estado en vivo). */
    suspend fun scoreOf(file: File): Float {
        val bmp = runCatching { ImageOps.load34(context, android.net.Uri.fromFile(file), 360) }.getOrNull() ?: return 0f
        // con tiempo límite: si el análisis se detiene (cámara cerrada) no queda esperando
        return kotlinx.coroutines.withTimeoutOrNull(4000) {
            kotlinx.coroutines.suspendCancellableCoroutine<Float> { c ->
                pipeline.post { val sc = runCatching { pipeline.withEngine { it.scoreOf(bmp) } }.getOrNull() ?: 0f; if (c.isActive) c.resume(sc) }
            }
        } ?: 0f
    }

    /**
     * Medición de relieve: secuencia de la pantalla (oscura, mitades, blanca), foto normal con la
     * pantalla blanca y, en segundo plano, cálculo del relieve sobre la foto guardada.
     */
    fun reliefCapture() {
        val e = exp ?: return
        if (capturing || reviewName != null) return
        capturing = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val conditions = captureConditions() + ("Relief" to "pantalla")
        val pitch = Perspective.cameraPitchFromDevice(tilt.value.pitch, true)
        val roll = tilt.value.roll
        val focal = focalNorm; val zoom = zoomRatio
        scope.launch {
            val depthMm = runCatching { ReliefCapture.depth(context, front = true) }.getOrNull()
            screenFlash.hold()
            val file = File(context.cacheDir, "capture_relief_${System.currentTimeMillis()}.jpg")
            val frames = try {
                val f = ReliefCapture.sequence(camera, { psPattern = it }, frameGrab)
                psPattern = com.photomed.app.align.PhotometricStereo.Pattern.FULL
                ReliefCapture.lockExposure(camera, false)
                if (!shoot(file)) null else f
            } finally {
                ReliefCapture.lockExposure(camera, false)
                psPattern = null
                screenFlash.release()
            }
            if (frames == null) { capturing = false; snackbar.showSnackbar("Error al capturar"); return@launch }
            val conds = if (depthMm != null) conditions + ("DepthSensorCm" to "%.1f".format(java.util.Locale.US, depthMm / 10)) else conditions
            AppScope.launch {
                val saved = runCatching { repo.savePhoto(e, zone, file, cameraPitch = pitch, roll = roll, focalNorm = focal, conditions = conds,
                    variant = com.photomed.app.data.FacialExpression.variantTag(expression)) }
                    .also { file.delete() }
                scope.launch {
                    capturing = false
                    saved.onSuccess { lastSaved = it; reload++; lastNear = false; reviewName = it.displayName }.onFailure { snackbar.showSnackbar("No se pudo guardar: ${it.message}") }
                }
                val photo = saved.getOrNull() ?: return@launch
                val msg = runCatching { ReliefCapture.process(context, frames, photo.uri, focal, zoom, depthMm) }
                    .getOrElse { "Relieve: ${it.message}" }
                frames.forEach { it.bmp.recycle() }
                scope.launch { snackbar.showSnackbar(msg) }
            }
        }
    }

    fun capture() {
        val e = exp ?: return
        if (capturing || reviewName != null) return
        if (reliefHi) { reliefCapture(); return }
        capturing = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            shutterFlash.snapTo(0.8f)
            shutterFlash.animateTo(0f, tween(Md3Motion.durationMedium2, easing = Md3Motion.easingStandard))
        }
        val conditions = captureConditions()
        val pitch = Perspective.cameraPitchFromDevice(tilt.value.pitch, lensFacing == CameraSelector.LENS_FACING_FRONT)
        val roll = tilt.value.roll
        val focal = focalNorm
        val stamp = System.currentTimeMillis()
        val zoomNow = zoomRatio
        scope.launch {
            // sensor de profundidad (si el teléfono lo tiene): distancia real para la escala en mm
            val depthMm = if (zone.isFaceZone() && com.photomed.app.align.DepthSensor.available(context,
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK))
                runCatching { ReliefCapture.depth(context, lensFacing == CameraSelector.LENS_FACING_FRONT) }.getOrNull() else null
            // Ráfaga: 3 tomas seguidas; se guarda la de mayor coincidencia (evita repetir por un parpadeo)
            val shots = if (burst) 3 else 1
            // flash de pantalla en ráfaga: la pantalla sigue en blanco de la primera a la última toma
            val holdScreen = shots > 1 && effectiveFlash == ImageCapture.FLASH_MODE_SCREEN
            if (holdScreen) screenFlash.hold()
            val files = ArrayList<File>()
            try {
                for (k in 0 until shots) {
                    val f = File(context.cacheDir, "capture_${stamp}_$k.jpg")
                    if (shoot(f)) files += f
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                files.forEach { it.delete() }; throw e
            } finally {
                if (holdScreen) screenFlash.release()
            }
            if (files.isEmpty()) {
                capturing = false
                snackbar.showSnackbar("Error al capturar")
                return@launch
            }
            val best = if (files.size == 1) files[0] else files.maxBy { scoreOf(it) }
            files.filter { it !== best }.forEach { it.delete() }
            // la línea del pelo se mide después de guardar (en segundo plano, sin omitirla nunca)
            val conds = if (depthMm == null) conditions else conditions + ("DepthSensorCm" to "%.1f".format(java.util.Locale.US, depthMm / 10))
            // Se guarda en el ámbito de la app: termina aunque se cierre la cámara.
            AppScope.launch {
                val result = runCatching {
                    repo.savePhoto(e, zone, best, cameraPitch = pitch, roll = roll, focalNorm = focal, conditions = conds,
                        variant = listOfNotNull(limbSide.tag.takeIf { zone.hasLimbSides },
                            com.photomed.app.data.FacialExpression.variantTag(expression)).joinToString("_").ifEmpty { null })
                }.also { best.delete() }
                if (depthMm != null && focal != null) result.getOrNull()?.let { ReliefCapture.saveDepthScale(context, it.uri, depthMm, focal, zoomNow) }
                if (com.photomed.app.align.HairSkin.appliesTo(zone)) result.getOrNull()?.let { ph ->
                    AppScope.launch { com.photomed.app.align.HairlineMeasure.measureAndStore(context, ph.uri) }
                }
                scope.launch {
                    capturing = false
                    result.onSuccess {
                        lastSaved = it
                        reload++
                        lastNear = conditions["Status"] == MatchStatus.CLOSE.name
                        // se muestra la foto para guardarla o repetirla
                        reviewName = it.displayName
                    }.onFailure {
                        snackbar.showSnackbar("No se pudo guardar: ${it.message}")
                    }
                }
            }
        }
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
        zoomTouchedAt = System.currentTimeMillis()
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
    // Listo para disparar: coincide, o "casi" (≥ 80 %) si se permite; la alineación fina se hace después.
    /** Luz fuera de lo que pide el paso del protocolo (null: bien o no se pide). */
    fun lightIssue(f: LiveFrame?): String? {
        val st = step ?: return null
        if (st.minLight == null && st.maxLight == null && st.maxLightAsym == null) return null
        if (!zone.isFaceZone()) return null
        val l = f?.light ?: return "Luz: midiendo…"
        st.minLight?.let { if (l.face < it) return "Luz insuficiente: %.0f %% (mín. %.0f %%)".format(l.face * 100, it * 100) }
        st.maxLight?.let { if (l.face > it) return "Demasiada luz: %.0f %% (máx. %.0f %%)".format(l.face * 100, it * 100) }
        st.maxLightAsym?.let { a ->
            val m = maxOf(l.leftCheek, l.rightCheek)
            if (m > 0f && kotlin.math.abs(l.leftCheek - l.rightCheek) / m > a) return "Luz desigual entre mejillas (máx. %.0f %%)".format(a * 100)
        }
        return null
    }
    fun readyNow(e: com.photomed.app.align.MatchEval?) =
        // la expresión pedida es obligatoria (también con «casi alineada»); con protocolo, también su luz
        e != null && e.expressionOk && lightIssue(live) == null &&
            (e.status == MatchStatus.MATCH || (nearCapture && step == null && (GuideTone.degree(e) ?: 0f) >= 0.8f))
    val ready = matchingEnabled && readyNow(live?.eval)
    LaunchedEffect(ready, autoCapture) {
        if (!ready) { autoArmed = true; return@LaunchedEffect }
        if (autoCapture && autoArmed && !capturing) {
            kotlinx.coroutines.delay(400)
            while (zoomCtl.isSettling(System.currentTimeMillis())) kotlinx.coroutines.delay(100)
            val focused = kotlinx.coroutines.withTimeoutOrNull(2500) {
                kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { c -> focusOnPivot { ok -> if (c.isActive) c.resume(ok) } }
            }
            if (readyNow(live?.eval) && !capturing) {
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
                val idx = sequence.indexOf(zone)
                Text(when {
                    protocol != null && step != null -> "${protocol.name} · paso ${stepIndex + 1}/${protocol.steps.size} · foto ${shotsDone + 1}/${step.count}"
                    idx >= 0 -> "Secuencia ${idx + 1}/${sequence.size} · ${exp?.patientName.orEmpty()}"
                    else -> exp?.patientName.orEmpty()
                }, color = if (idx >= 0 || step != null) GuideTone.MATCH.color else Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
            if (protocol != null && step != null) {
                androidx.compose.material3.TextButton(onClick = { onProtocolNext((stepIndex + 1).takeIf { it < protocol.steps.size }) }) {
                    Text(if (stepIndex + 1 < protocol.steps.size) "Saltar" else "Terminar", color = Color.White)
                }
            } else if (zone in sequence) {
                androidx.compose.material3.TextButton(onClick = { onNext(sequence.getOrNull(sequence.indexOf(zone) + 1)) }) {
                    Text(if (sequence.indexOf(zone) + 1 < sequence.size) "Saltar" else "Terminar", color = Color.White)
                }
            }
            IconToggleButton(
                checked = autoCapture,
                onCheckedChange = { autoCapture = it; if (step?.autoCapture == null) prefs.edit().putBoolean("auto_capture", it).apply() },
                enabled = photos.isNotEmpty() || protoRef != null,
            ) {
                Icon(Icons.Default.AutoMode, if (autoCapture) "Disparo automático activado" else "Disparo automático desactivado",
                    tint = if (autoCapture) GuideTone.MATCH.color else Color.White)
            }
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
                    }, when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Flash de pantalla activado" else "Flash activado"
                        ImageCapture.FLASH_MODE_AUTO -> "Flash automático"
                        else -> "Flash desactivado"
                    }, tint = Color.White
                )
            }
            // Menú "⋯": ajustes, modo experto y ayudas (fuera de la barra para no saturarla)
            Box {
                var more by remember { mutableStateOf(false) }
                IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, "Más opciones de la cámara", tint = Color.White) }
                androidx.compose.material3.DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Ajustes de coincidencia") },
                        leadingIcon = { Icon(Icons.Default.Tune, null) },
                        onClick = { more = false; showSettings = true },
                    )
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT && zone.isFaceZone()) androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Medición de relieve (pantalla)") },
                        leadingIcon = { Icon(if (reliefMode) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null) },
                        onClick = {
                            reliefMode = !reliefMode; prefs.edit().putBoolean("relief_mode", reliefMode).apply(); more = false
                            if (reliefMode) scope.launch { snackbar.showSnackbar("Relieve: penumbra, teléfono a 25–35 cm y sin moverse unos 2 s al disparar") }
                        },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Modo experto") },
                        leadingIcon = { Icon(if (expert) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null) },
                        onClick = { expert = !expert; prefs.edit().putBoolean("camera_expert", expert).apply(); more = false },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (photos.isNotEmpty()) "Girar la referencia 90°" else "Girar la guía 90°") },
                        leadingIcon = { Icon(Icons.Default.RotateRight, null) },
                        onClick = { refOrient = refOrient.rotated() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (photos.isNotEmpty()) "Espejo de la referencia" else "Espejo de la guía") },
                        leadingIcon = { Icon(Icons.Default.Flip, null) },
                        onClick = { refOrient = refOrient.mirrored() },
                    )
                    if (!refOrient.isIdentity) androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Orientación original") },
                        leadingIcon = { Icon(Icons.Default.Restore, null) },
                        onClick = { refOrient = com.photomed.app.align.ImageEdit.NONE; more = false },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Mostrar las ayudas otra vez") },
                        leadingIcon = { Icon(Icons.Default.TouchApp, null) },
                        onClick = { more = false; com.photomed.app.ui.components.CoachMarks.resetAll(context) },
                    )
                }
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
                    val edges = refEdges
                    if (showGhost && g != null && ghostEdges && edges != null) {
                        EdgeGhost(edges, (ghostAlpha + 0.25f).coerceAtMost(1f), Modifier.fillMaxSize())
                    } else if (showGhost && g != null) {
                        val gb = ghostBitmap
                        if (gb != null) androidx.compose.foundation.Image(gb, "Foto anterior superpuesta", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ghostAlpha })
                        else AsyncImage(
                            model = com.photomed.app.ui.photoModel(context, g.uri),
                            contentDescription = "Foto anterior superpuesta",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ghostAlpha },
                        )
                    }
                    // líneas de referencia: en el modo simple, solo al acercarse a la coincidencia
                    val linesVisible = expert || !matchingEnabled || (GuideTone.degree(live?.eval) ?: 0f) >= 0.6f
                    androidx.compose.runtime.CompositionLocalProvider(LocalGuideLines provides linesVisible) {
                    // referencia girada: la guía se adapta a la referencia sin girar y se gira entera
                    // (la deformación de la guía solo admite cabezas casi verticales)
                    val rg = refGuide.takeIf { !refOrient.isIdentity && adaptGuide && showGhost && ghost != null }
                    val adaptPts = when {
                        rg != null -> rg.points
                        adaptGuide && showGhost && refPoints.isNotEmpty() -> refPoints
                        // primera toma: con la cara dentro del marco, el contorno se ajusta a ella
                        adaptGuide && framedFace -> live?.currentPointsAll.orEmpty()
                        else -> emptyMap()
                    }
                    val adaptSil = if (rg != null) rg.silhouette else if (adaptGuide && showGhost) refSilhouette else null
                    // sin referencia que la oriente, la guía dibujada se gira / espeja con el control de la referencia
                    val orientGuide = rg != null || (adaptPts.isEmpty() && adaptSil == null && (ghost == null || !showGhost))
                    com.photomed.app.ui.compare.OrientBox(if (orientGuide) refOrient.rot else 0, orientGuide && refOrient.mirror, Modifier.fillMaxSize(),
                        fit = rg == null, fill34 = rg != null) {
                    GuideOverlay(zone, showContour = showContour, showGrid = showGrid, modifier = Modifier.fillMaxSize(),
                        tone = tone, adaptTo = adaptPts,
                        silhouette = adaptSil,
                        side = if (zone.hasLimbSides) limbSide else LimbSide.BOTH,
                        // modo libre: contorno de la referencia (si se superpone) o de lo que se ve ahora
                        free = if (showGhost && refFree != null) refFree else live?.freeContour)
                    }
                    }
                    // línea de implantación frontal: la de la referencia (discontinua) y la actual (continua)
                    val hl = refHairline
                    val rgh = refGuide?.hairline.takeIf { !refOrient.isIdentity && refGuide != null }
                    if (showContour && showGhost && rgh != null)
                        com.photomed.app.ui.compare.OrientBox(refOrient.rot, refOrient.mirror, Modifier.fillMaxSize(), fill34 = true) {
                            HairlineGuide(rgh, tone.color, Modifier.fillMaxSize())
                        }
                    else if (showContour && showGhost && hl != null) HairlineGuide(hl, tone.color, Modifier.fillMaxSize())
                    // contornos y surcos de la referencia del protocolo (guía discontinua)
                    if (showContour && protoRef != null) protoRef.lines.forEach { l ->
                        HairlineGuide(if (l.closed && l.points.size > 2) l.points + l.points.first() else l.points,
                            Color.White.copy(alpha = 0.55f), Modifier.fillMaxSize())
                    }
                    val liveHl = live?.hairline
                    if (showContour && liveHl != null) HairlineGuide(liveHl, Color(0xFFFFD54F), Modifier.fillMaxSize(), dashed = false)
                    if (matchingEnabled && expert) live?.let { LivePoints(it, tone, mirror = false) }
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

                // nivel: en el modo simple solo cuando el teléfono está torcido (o hay que girarlo)
                val tilted = kotlin.math.abs(tilt.value.roll) >= 1.5f && kotlin.math.abs(tilt.value.pitch) < 60f
                if ((expert && showLevel) || tilted || (guidance?.rotateDeg ?: 0f) != 0f) {
                    LevelIndicator(tilt.value, guidance?.rotateDeg ?: 0f, Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                EdgeArrows(guidance, tone.color.takeIf { tone != GuideTone.NORMAL } ?: Color.White)
                // Correcciones como iconos translúcidos sobre la imagen, abajo (sobre la etiqueta de la
                // referencia y, si la hay, la flecha hacia abajo)
                if (matchingEnabled) {
                    CueOverlay(
                        correctionItems(live,
                            if (zoomAssist && !manualZoom && zoomRatio > 1.01f) "zoom asistido %.1f×".format(zoomRatio) else null,
                            distanceHint()),
                        tone.color.takeIf { tone != GuideTone.NORMAL } ?: GuideTone.CLOSE.color,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(bottom = if ((guidance?.moveY ?: 0) > 0) 54.dp else 28.dp),
                    )
                }
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
        if (matchingEnabled && expert) {
            RecommendationStrip(
                live, guidance, autoCapture,
                zoomInfo = if (zoomAssist && !manualZoom && zoomRatio > 1.01f) "zoom asistido %.1f×".format(zoomRatio) else null,
                distanceHint = distanceHint(),
                showCues = false,
            )
        }

        // barra de zoom: siempre en el modo experto; en el simple, unos segundos tras pellizcar
        var zoomBarNow by remember { mutableStateOf(false) }
        LaunchedEffect(zoomTouchedAt) {
            zoomBarNow = zoomTouchedAt > 0L
            if (zoomBarNow) { kotlinx.coroutines.delay(CONTROLS_HIDE_MS); zoomBarNow = false }
        }
        androidx.compose.animation.AnimatedVisibility(expert || zoomBarNow) {
            ZoomBar(
                ratio = zoomRatio, range = zoomRange, manual = manualZoom, assist = zoomAssist,
                onZoom = { setZoom(it) },
                onResumeAssist = { manualZoom = false; zoomCtl.reset(); zoomTouchedAt = System.currentTimeMillis() },
            )
        }

        // Expresión facial (arrugas dinámicas): reposo o contracción máxima de cada grupo muscular
        // con protocolo: el paso, sus indicaciones y lo que falta (el gesto lo fija el paso)
        if (step != null) Column(Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.s3, vertical = 2.dp)) {
            Text(step.title, color = Color.White, style = MaterialTheme.typography.labelLarge)
            if (step.expression != com.photomed.app.data.FacialExpression.NEUTRAL)
                Text("${step.expression.instruction} · ${step.expression.muscle}", color = Color(0xFFFFD54F), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { contentDescription = "Instrucción: ${step.expression.instruction}" })
            step.instructions?.let { Text(it, color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
            val req = listOfNotNull(
                "coincidencia ≥ ${((step.threshold ?: threshold) * 100).toInt()} %",
                step.minLight?.let { "luz ≥ ${(it * 100).toInt()} %" },
                if (protoRef != null) "igual a la referencia" + (protoRef.sourceTakenAt?.let { " del ${formatDate(it)}" } ?: "") else null)
            Text("Exige: " + req.joinToString(" · "), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            lightIssue(live)?.let { Text(it, color = GuideTone.FAR.color, style = MaterialTheme.typography.labelMedium) }
        }
        if (expressions.size > 1 && step == null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Md3Spacing.s2),
                horizontalArrangement = Arrangement.spacedBy(Md3Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Expresión", color = Color.White, style = MaterialTheme.typography.labelLarge)
                expressions.forEach { ex ->
                    androidx.compose.material3.FilterChip(
                        selected = expression == ex,
                        onClick = {
                            expression = ex
                            // referencia superpuesta: la última foto con la misma expresión
                            photos.lastOrNull { it.expression == ex }?.let { ghost = it }
                        },
                        label = { Text(ex.label) },
                    )
                }
            }
            if (expression != com.photomed.app.data.FacialExpression.NEUTRAL) Text(
                "${expression.instruction} · ${expression.muscle}",
                color = Color(0xFFFFD54F), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Md3Spacing.s3, vertical = 2.dp)
                    .semantics { contentDescription = "Instrucción: ${expression.instruction}" })
        }

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
            // modo simple: solo las capas de uso diario; el resto, en el modo experto
            if (expert) LayerToggle("Cuadrícula", Icons.Default.GridOn, showGrid) { showGrid = it }
            LayerToggle("Contorno", Icons.Default.Face, showContour) { showContour = it }
            LayerToggle("Superponer", Icons.Default.Layers, showGhost, enabled = photos.isNotEmpty()) { showGhost = it }
            if (expert) LayerToggle("Nivel", Icons.Default.Straighten, showLevel) { showLevel = it }
            LayerToggle("Asistir", Icons.Default.CenterFocusStrong, liveMatch) { liveMatch = it }
            if (expert || lensFacing == CameraSelector.LENS_FACING_FRONT)
                LayerToggle("Espejo", Icons.Default.Flip, mirrorOn, enabled = lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    mirrorOn = it; prefs.edit().putBoolean("mirror_front", it).apply()
                }
            if (expert) LayerToggle("Adaptar", Icons.Default.AutoFixHigh, adaptGuide, enabled = refPoints.isNotEmpty() || refSilhouette != null) { adaptGuide = it }
        }

        if (showGhost && photos.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                // girar (pasos de 90°) y espejar la referencia superpuesta
                IconButton(onClick = { refOrient = refOrient.rotated() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.RotateRight, "Girar la referencia 90°", tint = if (refOrient.rot != 0) GuideTone.MATCH.color else Color.White)
                }
                IconButton(onClick = { refOrient = refOrient.mirrored() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Flip, "Espejo de la referencia", tint = if (refOrient.mirror) GuideTone.MATCH.color else Color.White)
                }
                androidx.compose.material3.FilterChip(
                    selected = ghostEdges,
                    onClick = { ghostEdges = !ghostEdges; prefs.edit().putBoolean("ghost_edges", ghostEdges).apply() },
                    label = { Text("Bordes", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.padding(end = 8.dp).height(32.dp),
                )
                // opacidad: un chip; al tocarlo aparece el control y se oculta solo a los pocos segundos
                LaunchedEffect(opacityOpen, opacityTouchedAt) {
                    if (opacityOpen && !expert) { kotlinx.coroutines.delay(CONTROLS_HIDE_MS); opacityOpen = false }
                }
                if (expert || opacityOpen) {
                    Text("Opacidad", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = ghostAlpha,
                        onValueChange = { ghostAlpha = it; opacityTouchedAt = System.currentTimeMillis() },
                        valueRange = 0.05f..0.9f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(32.dp),
                    )
                    Text("${(ghostAlpha * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(36.dp))
                } else {
                    androidx.compose.material3.AssistChip(
                        onClick = { opacityOpen = true; opacityTouchedAt = System.currentTimeMillis() },
                        label = { Text("Opacidad ${(ghostAlpha * 100).toInt()} %", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = { Icon(Icons.Default.Contrast, null, Modifier.size(16.dp)) },
                        modifier = Modifier.height(32.dp),
                    )
                }
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
            // anillo de progreso: se llena con la coincidencia (rojo → naranja → verde) y queda verde al coincidir
            ShutterButton(
                degree = if (matchingEnabled) GuideTone.degree(live?.eval) else null,
                capturing = capturing,
                enabled = !capturing && exp != null,
                onClick = { capture() },
            )
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
    com.photomed.app.ui.components.CoachMark("camara_gestos", Icons.Default.TouchApp,
        "Pellizque para el zoom. El anillo del disparador se llena al coincidir con la guía. " +
            "Más capas y datos: modo experto en los ajustes (⋯).",
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp))
    ScreenFlashOverlay(screenFlash)
    ReliefPatternOverlay(psPattern)
    reviewName?.let { name ->
        fun keep() {
            reviewName = null
            val idx = sequence.indexOf(zone)
            if (protocol != null && step != null) {
                shotsDone++
                if (shotsDone < step.count) scope.launch { snackbar.showSnackbar("Foto $shotsDone de ${step.count} de este paso guardada") }
                else {
                    val next = (stepIndex + 1).takeIf { it < protocol.steps.size }
                    if (next == null) android.widget.Toast.makeText(context, "Protocolo «${protocol.name}» completado", android.widget.Toast.LENGTH_SHORT).show()
                    onProtocolNext(next)
                }
            } else if (idx >= 0) {
                // secuencia: pasa a la siguiente zona (o termina); la cabecera muestra "Secuencia i/N"
                if (idx + 1 >= sequence.size) android.widget.Toast.makeText(context, "Secuencia completada", android.widget.Toast.LENGTH_SHORT).show()
                onNext(sequence.getOrNull(idx + 1))
            } else scope.launch {
                snackbar.showSnackbar("Guardada en el álbum ${exp?.id.orEmpty()}" + if (lastNear) " · casi alineada: se alinea en Comparar" else "")
            }
        }
        // la revisión tapa la cámara (el análisis en vivo y el disparo automático quedan en pausa)
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {}) {
            com.photomed.app.ui.gallery.PhotoViewerScreen(expId, zone, name, onBack = { reviewName = null; lastSaved = null; reload++ },
                onCompare = onCompare,
                review = com.photomed.app.ui.gallery.ReviewActions(onKeep = ::keep, onRetry = {
                    reviewName = null; lastSaved = null; reload++
                }))
        }
    }
    if (showSettings) {
        MatchSettingsSheet(
            autoCapture = autoCapture,
            threshold = threshold,
            zoomAssist = zoomAssist,
            onZoomAssist = {
                zoomAssist = it; prefs.edit().putBoolean("zoom_assist", it).apply()
                if (!it) { camera?.cameraControl?.setZoomRatio(1f); zoomRatio = 1f }
            },
            onAutoCapture = { autoCapture = it; prefs.edit().putBoolean("auto_capture", it).apply() },
            onThreshold = { threshold = it; prefs.edit().putFloat("match_threshold_v2", it).apply() },
            useGaze = useGaze,
            onUseGaze = { useGaze = it; prefs.edit().putBoolean(com.photomed.app.align.AlignOptions.PREF_GAZE, it).apply() },
            feedback = feedback, onFeedback = { feedback = it; prefs.edit().putBoolean("guide_feedback", it).apply() },
            nearCapture = nearCapture, onNearCapture = { nearCapture = it; prefs.edit().putBoolean("auto_capture_near", it).apply() },
            burst = burst, onBurst = { burst = it; prefs.edit().putBoolean("burst", it).apply() },
            expert = expert, onExpert = { expert = it; prefs.edit().putBoolean("camera_expert", it).apply() },
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
    onAutoCapture: (Boolean) -> Unit,
    onThreshold: (Float) -> Unit,
    useGaze: Boolean = true,
    onUseGaze: (Boolean) -> Unit = {},
    feedback: Boolean = true,
    onFeedback: (Boolean) -> Unit = {},
    nearCapture: Boolean = true,
    onNearCapture: (Boolean) -> Unit = {},
    burst: Boolean = true,
    onBurst: (Boolean) -> Unit = {},
    expert: Boolean = false,
    onExpert: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {
    SafeBottomSheet(onDismiss = onDismiss) {
        MatchSettingsContent(autoCapture, threshold, onAutoCapture, onThreshold, zoomAssist, onZoomAssist, useGaze, onUseGaze,
            feedback, onFeedback, nearCapture, onNearCapture, burst, onBurst, expert, onExpert)
    }
}

@Composable
fun MatchSettingsContent(
    autoCapture: Boolean, threshold: Float, onAutoCapture: (Boolean) -> Unit, onThreshold: (Float) -> Unit,
    zoomAssist: Boolean = true, onZoomAssist: (Boolean) -> Unit = {},
    useGaze: Boolean = true,
    onUseGaze: (Boolean) -> Unit = {},
    feedback: Boolean = true,
    onFeedback: (Boolean) -> Unit = {},
    nearCapture: Boolean = true,
    onNearCapture: (Boolean) -> Unit = {},
    burst: Boolean = true,
    onBurst: (Boolean) -> Unit = {},
    expert: Boolean = false,
    onExpert: (Boolean) -> Unit = {},
) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Coincidencia con la referencia", style = MaterialTheme.typography.titleLarge)
        SettingSwitch("Modo experto", "Muestra siempre las líneas de referencia, los puntos detectados, los porcentajes, " +
            "la franja de indicaciones, la barra de zoom y todas las capas. En el modo simple solo se ven el contorno, " +
            "un icono con la corrección principal y el anillo del disparador.", expert, onExpert)
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
        SettingSwitch("Disparar también casi alineado", "Dispara al llegar al 80 % de coincidencia (verde amarillento); " +
            "la alineación fina se hace en Comparar.", nearCapture, onNearCapture)
        SettingSwitch("Ráfaga de 3 fotos", "Al disparar toma tres y guarda la de mayor coincidencia (evita repetir por un parpadeo).",
            burst, onBurst)
        SettingSwitch("Vibración y sonido de guía", "Pulsos que se aceleran al acercarse a la coincidencia y un tono al coincidir, " +
            "para no tener que mirar la pantalla.", feedback, onFeedback)
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
/** Zona del rostro (puntos faciales): admite relieve y escala por el iris. */
private fun com.photomed.app.data.Zone.isFaceZone() = com.photomed.app.align.ZoneAlignment.of(this).detector == com.photomed.app.align.Detector.FACE

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
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
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


@Composable
private fun SettingSwitch(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = null)
    }
}

/** Tiempo tras el que se ocultan solos los controles secundarios (zoom, opacidad) en el modo simple. */
const val CONTROLS_HIDE_MS = 3000L

/**
 * Disparador con anillo de progreso: el arco crece con el grado de coincidencia ([degree] 0..1,
 * null = sin detección: anillo blanco) y toma el color de la guía (rojo → naranja → verde).
 */
@Composable
fun ShutterButton(degree: Float?, capturing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val anim by androidx.compose.animation.core.animateFloatAsState(degree ?: 0f, label = "anillo")
    val ringColor = when {
        degree == null -> Color.White
        degree >= 1f -> GuideTone.MATCH.color
        else -> GuideTone.gradient(anim)
    }
    Box(Modifier.size(76.dp)
        .semantics {
            contentDescription = "Disparador"
            if (degree != null) stateDescription = "Coincidencia ${(degree * 100).toInt()} %"
        }, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val sw = 4.dp.toPx()
            val inset = sw / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
            drawCircle(Color.White.copy(alpha = if (degree == null) 1f else 0.35f), radius = size.minDimension / 2 - inset,
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            if (degree != null) drawArc(ringColor, -90f, 360f * anim.coerceIn(0f, 1f), false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Box(
            Modifier.size(60.dp).clip(CircleShape)
                .background(if (capturing) MaterialTheme.colorScheme.outline else Color.White)
                .clickable(enabled = enabled, onClickLabel = "Tomar fotografía", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { if (capturing) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
    }
}
