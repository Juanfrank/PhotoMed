package com.photomed.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.photomed.app.ui.camera.Isolated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La pantalla de la cámara se recomponía entera con cada cuadro analizado (~5 por segundo). Patrón aplicado:
 * el cuerpo lee solo valores derivados que cambian poco, y lo que cambia en cada cuadro se lee dentro de un
 * ámbito propio ([Isolated]). Con 10 cuadros nuevos, el cuerpo se compone una vez y el ámbito 11.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraRecompositionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun liveFramesRecomposeOnlyTheIsolatedLayers() {
        val live = mutableStateOf(0f)          // «calidad» del cuadro en vivo
        val body = mutableIntStateOf(0); val layer = mutableIntStateOf(0)
        compose.setContent {
            SideEffect { body.intValue++ }
            // valor derivado que cambia poco (como el estado de coincidencia)
            val matched by remember { derivedStateOf { live.value >= 1f } }
            Box {
                Text(if (matched) "coincide" else "buscando")
                Isolated {
                    SideEffect { layer.intValue++ }
                    Text("%.2f".format(live.value))
                }
            }
        }
        compose.waitForIdle()
        for (k in 1..10) { compose.runOnIdle { live.value = k / 20f }; compose.waitForIdle() }
        assertEquals("el cuerpo no se recompone con cada cuadro", 1, body.intValue)
        assertEquals(11, layer.intValue)
        // el derivado sí recompone el cuerpo cuando cambia de verdad
        compose.runOnIdle { live.value = 1f }; compose.waitForIdle()
        assertTrue(body.intValue == 2)
    }
}
