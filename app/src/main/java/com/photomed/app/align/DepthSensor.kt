package com.photomed.app.align

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteOrder
import kotlin.coroutines.resume

/**
 * Sensor de profundidad del teléfono (cámara ToF o de profundidad que Android expone con la
 * capacidad DEPTH_OUTPUT y el formato DEPTH16). Da la distancia real a la cara, que fija la escala
 * en mm sin suponer el tamaño del iris. Su resolución (típicamente 240 × 180) no distingue
 * arrugas: el relieve fino sale del estéreo fotométrico.
 *
 * Se abre aparte de CameraX; si el teléfono no permite usarlo a la vez que la cámara de fotos, no
 * hay medida (y se usa el iris).
 */
object DepthSensor {
    /** Id de la cámara de profundidad orientada como [facing] (CameraCharacteristics.LENS_FACING_*), o null. */
    fun cameraId(context: Context, facing: Int): String? = runCatching {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.firstOrNull { id ->
            val c = cm.getCameraCharacteristics(id)
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            c.get(CameraCharacteristics.LENS_FACING) == facing &&
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT in caps &&
                map?.getOutputSizes(ImageFormat.DEPTH16)?.isNotEmpty() == true
        }
    }.getOrNull()

    fun available(context: Context, facing: Int) = cameraId(context, facing) != null

    /**
     * Distancia (mm) a lo que hay en el centro del encuadre: mediana de los píxeles válidos y con
     * confianza ≥ 50 % de la zona central ([centerFraction] del lado). null si no hay sensor, no se
     * puede abrir o no hay medida en [timeoutMs].
     */
    @SuppressLint("MissingPermission")
    suspend fun centerDistanceMm(context: Context, facing: Int, centerFraction: Float = 0.3f, timeoutMs: Long = 2500): Float? {
        val id = cameraId(context, facing) ?: return null
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val size = cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.DEPTH16)?.maxByOrNull { it.width * it.height } ?: return null
        val thread = HandlerThread("profundidad").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.DEPTH16, 2)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        try {
            return withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Float?> { cont ->
                    var frames = 0
                    reader.setOnImageAvailableListener({ r ->
                        val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            // las primeras tomas del ToF pueden venir sin calibrar
                            if (++frames < 3) return@setOnImageAvailableListener
                            val plane = img.planes[0]
                            val buf = plane.buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val stride = plane.rowStride / 2
                            val v = parseCenter(ShortArray(buf.remaining()).also { buf.get(it) }, img.width, img.height, stride, centerFraction)
                            if (cont.isActive) cont.resume(v)
                        } finally { img.close() }
                    }, handler)
                    cm.openCamera(id, object : CameraDevice.StateCallback() {
                        override fun onOpened(d: CameraDevice) {
                            device = d
                            @Suppress("DEPRECATION")
                            d.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(s: CameraCaptureSession) {
                                    session = s
                                    runCatching {
                                        val req = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(reader.surface) }
                                        req.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                        s.setRepeatingRequest(req.build(), null, handler)
                                    }.onFailure { if (cont.isActive) cont.resume(null) }
                                }
                                override fun onConfigureFailed(s: CameraCaptureSession) { if (cont.isActive) cont.resume(null) }
                            }, handler)
                        }
                        override fun onDisconnected(d: CameraDevice) { d.close(); if (cont.isActive) cont.resume(null) }
                        override fun onError(d: CameraDevice, error: Int) { d.close(); if (cont.isActive) cont.resume(null) }
                    }, handler)
                }
            }
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { session?.close() }; runCatching { device?.close() }; runCatching { reader.close() }
            thread.quitSafely()
        }
    }

    /**
     * DEPTH16: 13 bits bajos = distancia en mm; 3 bits altos = confianza (0 = 100 %, k = (k − 1)/7).
     * Mediana de la zona central con confianza ≥ 50 % (o sin dato de confianza).
     */
    fun parseCenter(data: ShortArray, w: Int, h: Int, stride: Int, centerFraction: Float): Float? {
        val x0 = (w * (0.5f - centerFraction / 2)).toInt(); val x1 = (w * (0.5f + centerFraction / 2)).toInt()
        val y0 = (h * (0.5f - centerFraction / 2)).toInt(); val y1 = (h * (0.5f + centerFraction / 2)).toInt()
        val vals = ArrayList<Int>()
        for (y in y0 until y1) for (x in x0 until x1) {
            val s = data.getOrNull(y * stride + x)?.toInt()?.and(0xFFFF) ?: continue
            val mm = s and 0x1FFF; val conf = (s shr 13) and 0x7
            val ok = conf == 0 || (conf - 1) / 7f >= 0.5f
            if (mm in 50..3000 && ok) vals += mm
        }
        if (vals.size < 10) return null
        vals.sort()
        return vals[vals.size / 2].toFloat()
    }
}
