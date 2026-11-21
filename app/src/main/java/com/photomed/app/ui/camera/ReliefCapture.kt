package com.photomed.app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.photomed.app.align.DepthSensor
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.ImageOps
import com.photomed.app.align.LineMeasure
import com.photomed.app.align.MeasureMeta
import com.photomed.app.align.MediaPipeFace
import com.photomed.app.align.PhotometricStereo
import com.photomed.app.align.PhotometricStereo.Pattern
import com.photomed.app.align.PsFrame
import com.photomed.app.i18n.tr
import com.photomed.app.align.Relief
import com.photomed.app.align.ReliefStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Pantalla durante la medición de relieve: negra con la mitad indicada en blanco (la del usuario
 * que mira la pantalla), toda blanca (FULL) o toda negra (DARK).
 */
@Composable
fun ReliefPatternOverlay(p: Pattern?) {
    if (p == null) return
    val white = ScreenFlashController.COLOR
    Box(Modifier.fillMaxSize().background(if (p == Pattern.FULL) white else Color.Black)) {
        when (p) {
            Pattern.LEFT -> Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterStart).background(white))
            Pattern.RIGHT -> Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterEnd).background(white))
            Pattern.TOP -> Column(Modifier.fillMaxSize()) { Box(Modifier.fillMaxWidth().weight(1f).background(white)); Box(Modifier.weight(1f)) }
            Pattern.BOTTOM -> Column(Modifier.fillMaxSize()) { Box(Modifier.weight(1f)); Box(Modifier.fillMaxWidth().weight(1f).background(white)) }
            else -> {}
        }
    }
}

/** Petición de un fotograma del análisis posterior a un instante (después de cambiar la pantalla). */
class FrameGrab {
    private val req = AtomicReference<Pair<Long, CompletableDeferred<Bitmap>>?>(null)
    val pending: Boolean get() = req.get() != null

    /** Llamar desde el analizador: true si se ha entregado el fotograma (no analizar más). */
    fun offer(now: Long, make: () -> Bitmap): Boolean {
        val r = req.get() ?: return false
        if (now < r.first) return true // esperando a que la pantalla y la exposición cambien
        if (!req.compareAndSet(r, null)) return false
        r.second.complete(make())
        return true
    }

    suspend fun next(delayMs: Long, timeoutMs: Long = 1500): Bitmap? {
        val d = CompletableDeferred<Bitmap>()
        req.set(System.currentTimeMillis() + delayMs to d)
        return withTimeoutOrNull(timeoutMs) { d.await() }.also { req.set(null) }
    }
}

object ReliefCapture {
    /** Ciclos de las 4 mitades (se promedian). */
    const val CYCLES = 2
    /** Espera tras cambiar la pantalla hasta que la cámara la ve (dibujo + pantalla + exposición). */
    const val SETTLE_MS = 160L

    /** Geometría de la pantalla del teléfono (mm). */
    fun screen(context: Context): PhotometricStereo.Screen {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels / dm.xdpi * 25.4f; val h = dm.heightPixels / dm.ydpi * 25.4f
        return PhotometricStereo.Screen(minOf(w, h), maxOf(w, h))
    }

    /** Bloquea (o libera) la exposición automática y el balance de blancos. */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun lockExposure(camera: Camera?, lock: Boolean) {
        val c = camera ?: return
        runCatching {
            val ctl = Camera2CameraControl.from(c.cameraControl)
            if (lock) ctl.captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                .build()
            else ctl.clearCaptureRequestOptions()
        }
    }

    /**
     * Secuencia: pantalla blanca (exposición) → bloqueo → oscura → [CYCLES] × (izquierda, derecha,
     * arriba, abajo) → oscura → blanca (referencia). [show] cambia la pantalla; [grab] da el
     * siguiente fotograma. Unos 2 s.
     */
    suspend fun sequence(camera: Camera?, show: (Pattern?) -> Unit, grab: FrameGrab): List<PsFrame> {
        val out = ArrayList<PsFrame>()
        show(Pattern.FULL)
        kotlinx.coroutines.delay(700) // la exposición automática se ajusta a la pantalla blanca
        lockExposure(camera, true)
        kotlinx.coroutines.delay(150)
        val order = listOf(Pattern.DARK) + List(CYCLES) { PhotometricStereo.LIT }.flatten() + Pattern.DARK + Pattern.FULL
        for (p in order) {
            show(p)
            val b = grab.next(SETTLE_MS) ?: continue
            out += PsFrame(p, b)
        }
        return out
    }

    /**
     * Relieve de la secuencia, llevado a la foto guardada [photoUri], y escala del sensor de
     * profundidad. Devuelve un mensaje para el usuario.
     */
    suspend fun process(context: Context, frames: List<PsFrame>, photoUri: Uri, focalNorm: Float?, zoom: Float, depthMm: Float?): String =
        com.photomed.app.align.NativeGate.run("relieve") { withContext(Dispatchers.Default) {
            val full = frames.lastOrNull { it.pattern == Pattern.FULL } ?: return@withContext tr("Relieve: faltan tomas")
            MediaPipeFace(context).use { mp ->
                val frameMesh = mp.mesh(full.bmp) ?: return@withContext tr("Relieve: no se encontró la cara")
                val focalPx = focalNorm?.let { it * maxOf(full.bmp.width, full.bmp.height) * zoom }
                val r = Relief.compute(frames, frameMesh, screen(context), focalPx, depthMm) ?: return@withContext tr("Relieve: no se pudo calcular")
                if (r.signalRatio < 0.25f) return@withContext tr("Relieve no guardado: demasiada luz ambiente (use penumbra)")
                if (r.validFraction < 0.5f) return@withContext tr("Relieve no guardado: hubo movimiento o sombras")
                val photo = ImageOps.loadFull(context, photoUri, 1440, ImageEdit.NONE)
                val photoMesh = mp.mesh(photo) ?: return@withContext tr("Relieve: no se encontró la cara en la foto")
                val (bmp, factor) = Relief.toPhoto(r, frameMesh, photoMesh, photo.width, photo.height)
                    ?: return@withContext tr("Relieve: la foto no coincide con la secuencia")
                ReliefStore.save(context, photoUri.toString(), bmp, MeasureMeta(true,
                    irisFactor = factor?.takeIf { r.distanceSource == "sensor de profundidad" },
                    distanceMm = r.distanceMm, distanceSource = r.distanceSource,
                    signalRatio = r.signalRatio, validFraction = r.validFraction))
                tr("Relieve guardado (%1\$s cm, %2\$s)", "%.0f".format(r.distanceMm / 10),
                    com.photomed.app.i18n.trData(r.distanceSource))
            }
        } }

    /**
     * Escala de una foto normal con el sensor de profundidad: mm por ancho de la foto a la
     * distancia medida (FOV de la cámara) frente a la escala del iris. Solo rostros.
     */
    suspend fun saveDepthScale(context: Context, photoUri: Uri, depthMm: Float, focalNorm: Float, zoom: Float) =
        com.photomed.app.align.NativeGate.run("escala del sensor de profundidad") { saveDepthScaleGated(context, photoUri, depthMm, focalNorm, zoom) }

    private suspend fun saveDepthScaleGated(context: Context, photoUri: Uri, depthMm: Float, focalNorm: Float, zoom: Float) = withContext(Dispatchers.Default) {
        runCatching {
            val photo = ImageOps.loadFull(context, photoUri, 1440, ImageEdit.NONE)
            val mesh = MediaPipeFace(context).use { it.mesh(photo) } ?: return@runCatching
            val iris = LineMeasure.scale(mesh)?.takeIf { it.second == "iris" }?.first ?: return@runCatching
            // ancho de la foto = lado corto del sensor (3/4 del largo) a la distancia medida
            val shortSide = minOf(photo.width, photo.height) / maxOf(photo.width, photo.height).toFloat()
            val widthMm = depthMm * (if (photo.width <= photo.height) shortSide else 1f) / focalNorm / zoom
            ReliefStore.save(context, photoUri.toString(), null,
                MeasureMeta(false, irisFactor = widthMm / iris, distanceMm = depthMm, distanceSource = "sensor de profundidad"))
        }
    }

    /** Distancia del sensor de profundidad orientado como la cámara (o null). */
    suspend fun depth(context: Context, front: Boolean): Float? =
        DepthSensor.centerDistanceMm(context, if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK)
}
