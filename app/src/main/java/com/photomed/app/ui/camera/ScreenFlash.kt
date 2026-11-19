package com.photomed.app.ui.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Flash de pantalla para la cámara frontal (comportamiento habitual de las cámaras selfie): al
 * disparar, la pantalla se vuelve blanca (tono cálido neutro) con el brillo al máximo, la cámara
 * mide la exposición con esa luz y toma la foto; después se restaura el brillo.
 * Se usa con [ImageCapture.FLASH_MODE_SCREEN] de CameraX, que gestiona la secuencia de captura.
 */
class ScreenFlashController(private val activity: Activity?) : ImageCapture.ScreenFlash {
    var active by mutableStateOf(false)
        private set
    private var saved: Float? = null
    private val main = Handler(Looper.getMainLooper())
    /** Ráfaga en curso: la pantalla sigue en blanco entre tomas (ver [hold]/[release]). */
    private var held = false
    private var litAt = 0L

    private fun light() {
        if (!active) litAt = System.currentTimeMillis()
        active = true
        activity?.window?.let { w ->
            val lp = w.attributes
            if (saved == null) saved = lp.screenBrightness
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            w.attributes = lp
        }
    }

    /** Mantener la pantalla blanca durante varias tomas (ráfaga); termina con [release]. */
    fun hold() { main.post { held = true; light() } }

    fun release() { main.post { held = false; restore() } }

    override fun apply(expirationTimeMillis: Long, listener: ImageCapture.ScreenFlashListener) {
        main.post {
            light()
            // tiempo para que se dibuje el blanco y suba el brillo antes de medir la exposición
            // (si ya estaba encendida en la ráfaga, solo lo que falte), sin pasar del límite de CameraX
            val pending = RAMP_MS - (System.currentTimeMillis() - litAt)
            val wait = minOf(pending, expirationTimeMillis - System.currentTimeMillis() - 100).coerceIn(0, RAMP_MS)
            main.postDelayed({ listener.onCompleted() }, wait)
        }
    }

    override fun clear() {
        // durante la ráfaga la pantalla sigue blanca hasta la última toma
        main.post { if (!held) restore() }
    }

    fun restore() {
        held = false
        active = false
        val s = saved ?: return
        activity?.window?.let { w -> val lp = w.attributes; lp.screenBrightness = s; w.attributes = lp }
        saved = null
    }

    companion object {
        const val RAMP_MS = 300L
        /** Color del flash de pantalla: blanco ligeramente cálido (más favorable para la piel). */
        val COLOR = Color(0xFFFFF6EC)
        /** Luz ambiente (lux) por debajo de la cual el modo automático usa el flash de pantalla. */
        const val AUTO_LUX = 40f
    }
}

@Composable
fun rememberScreenFlash(): ScreenFlashController {
    val context = LocalContext.current
    val c = remember { ScreenFlashController(context.findActivity()) }
    DisposableEffect(c) { onDispose { c.restore() } }
    return c
}

/** Capa blanca a pantalla completa mientras el flash de pantalla está activo. */
@Composable
fun ScreenFlashOverlay(controller: ScreenFlashController) {
    if (controller.active) Box(Modifier.fillMaxSize().background(ScreenFlashController.COLOR))
}

/** Luz ambiente del sensor de luz (en el frente del teléfono, junto a la cámara selfie); null si no hay. */
@Composable
fun rememberAmbientLux(enabled: Boolean): State<Float?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { state.value = e.values.firstOrNull() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (enabled && sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(l) }
    }
    return state
}

/**
 * Modo de flash efectivo: la cámara frontal no tiene flash físico, así que "Encendido" usa el flash
 * de pantalla y "Automático" lo usa solo con poca luz ([lux] ≤ [ScreenFlashController.AUTO_LUX];
 * sin sensor de luz, no). La trasera usa el modo elegido tal cual.
 */
fun effectiveFlashMode(front: Boolean, chosen: Int, lux: Float?): Int = when {
    !front -> chosen
    chosen == ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_SCREEN
    chosen == ImageCapture.FLASH_MODE_AUTO && lux != null && lux <= ScreenFlashController.AUTO_LUX -> ImageCapture.FLASH_MODE_SCREEN
    else -> ImageCapture.FLASH_MODE_OFF
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
