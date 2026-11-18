package com.photomed.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.photomed.app.data.Expediente
import com.photomed.app.ui.expediente.ExpedienteContent
import com.photomed.app.ui.expedientes.ExpedienteForm
import com.photomed.app.ui.expedientes.ExpedientesContent
import com.photomed.app.ui.legal.TermsGate
import com.photomed.app.ui.theme.PhotoMedTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Capturas de las pantallas principales (./gradlew recordPaparazziDebug). */
class ScreensScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, useDeviceResolution = false)

    private val day = 86_400_000L
    private val now = 1_763_400_000_000L
    private val sample = listOf(
        Expediente("0012_Maria_Pena", "María José Peña", "0012", "Toxina botulínica tercio superior", true, now - 90 * day, File("."), 14, now - 2 * day),
        Expediente("0031_Carlos_Rivas", "Carlos Rivas", "0031", "", false, now - 40 * day, File("."), 6, now - 10 * day),
        Expediente("0045_Ana_Gomez", "Ana Gómez", "0045", "", true, now - 5 * day, File("."), 0, null),
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT))
        paparazzi.snapshot(name) { PhotoMedTheme(darkTheme = dark) { content() } }
    }

    @Test
    fun expedientesLight() = shot("expedientes_light") {
        ExpedientesContent(sample, SnackbarHostState(), {}, {}, {}, {})
    }

    @Test
    fun expedientesDark() = shot("expedientes_dark", dark = true) {
        ExpedientesContent(sample, SnackbarHostState(), {}, {}, {}, {})
    }

    @Test
    fun expedientesEmpty() = shot("expedientes_empty") {
        ExpedientesContent(emptyList(), SnackbarHostState(), {}, {}, {}, {})
    }

    @Test
    fun expediente() = shot("expediente") {
        ExpedienteContent(sample[0], emptyList(), SnackbarHostState(), {}, {}, {}, {}, {})
    }

    @Test
    fun expedienteDark() = shot("expediente_dark", dark = true) {
        ExpedienteContent(sample[1], emptyList(), SnackbarHostState(), {}, {}, {}, {}, {})
    }

    @Test
    fun form() = shot("form") {
        ExpedienteForm(title = "Nuevo expediente", onDismiss = {}, onConfirm = { _, _, _, _ -> })
    }

    @Test
    fun termsGate() = shot("terms") { TermsGate(onAccept = {}) }
}
