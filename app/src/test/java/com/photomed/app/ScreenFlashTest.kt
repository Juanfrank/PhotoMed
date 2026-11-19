package com.photomed.app

import androidx.camera.core.ImageCapture
import com.photomed.app.ui.camera.ScreenFlashController
import com.photomed.app.ui.camera.effectiveFlashMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import android.view.WindowManager

/** Flash de la cámara frontal: flash de pantalla, como en las cámaras selfie. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenFlashTest {
    @Test
    fun frontCameraUsesScreenFlash() {
        val on = ImageCapture.FLASH_MODE_ON; val auto = ImageCapture.FLASH_MODE_AUTO; val off = ImageCapture.FLASH_MODE_OFF
        assertEquals(ImageCapture.FLASH_MODE_SCREEN, effectiveFlashMode(true, on, null))
        assertEquals(ImageCapture.FLASH_MODE_SCREEN, effectiveFlashMode(true, auto, 5f))   // poca luz
        assertEquals(off, effectiveFlashMode(true, auto, 300f))                            // luz suficiente
        assertEquals(off, effectiveFlashMode(true, auto, null))                            // sin sensor de luz
        assertEquals(off, effectiveFlashMode(true, off, 1f))
        // la trasera conserva el flash físico
        assertEquals(on, effectiveFlashMode(false, on, null))
        assertEquals(auto, effectiveFlashMode(false, auto, 1f))
    }

    @Test
    fun screenFlashTurnsScreenWhiteAtFullBrightnessAndRestores() {
        val activity = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        val before = activity.window.attributes.screenBrightness
        val c = ScreenFlashController(activity)
        var completed = false
        c.apply(System.currentTimeMillis() + 3000, object : ImageCapture.ScreenFlashListener { override fun onCompleted() { completed = true } })
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(c.active)
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL, activity.window.attributes.screenBrightness)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ScreenFlashController.RAMP_MS + 50))
        assertTrue(completed)
        c.clear(); shadowOf(Looper.getMainLooper()).idle()
        assertFalse(c.active)
        assertEquals(before, activity.window.attributes.screenBrightness)
    }

    @Test
    fun screenStaysWhiteDuringBurst() {
        val activity = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        val before = activity.window.attributes.screenBrightness
        val c = ScreenFlashController(activity)
        val looper = shadowOf(Looper.getMainLooper())
        c.hold(); looper.idle()
        var done = 0
        repeat(3) { k ->
            c.apply(System.currentTimeMillis() + 3000, object : ImageCapture.ScreenFlashListener { override fun onCompleted() { done++ } })
            looper.idleFor(java.time.Duration.ofMillis(ScreenFlashController.RAMP_MS + 50))
            c.clear(); looper.idle()
            // entre tomas la pantalla sigue blanca y al máximo
            assertTrue("toma ${k + 1}", c.active)
            assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL, activity.window.attributes.screenBrightness)
        }
        assertEquals(3, done)
        c.release(); looper.idle()
        assertFalse(c.active)
        assertEquals(before, activity.window.attributes.screenBrightness)
    }
}
