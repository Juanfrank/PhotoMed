package com.photomed.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.data.Expediente
import com.photomed.app.data.SessionTemplates
import com.photomed.app.data.Zone
import com.photomed.app.ui.camera.HintCues
import com.photomed.app.ui.expediente.ExpedienteContent
import com.photomed.app.ui.theme.PhotoMedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Plantillas de sesión, selección múltiple de zonas y secuencia de fotos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w600dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SequenceFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("photomed", Context.MODE_PRIVATE)
    private val exp = Expediente("0012_Ana", "Ana Gómez", "0012", "", true, 0L, File("."), 0, null)

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test
    fun templatesPersist() {
        SessionTemplates.save(prefs, com.photomed.app.data.SessionTemplate("Mi control", listOf(Zone.LIPS, Zone.FACE_FRONTAL)))
        val all = SessionTemplates.load(prefs)
        assertEquals(listOf(Zone.LIPS, Zone.FACE_FRONTAL), all.first { it.name == "Mi control" }.zones)
        SessionTemplates.delete(prefs, "Mi control")
        assertTrue(SessionTemplates.load(prefs).none { it.name == "Mi control" })
        assertTrue(SessionTemplates.BUILT_IN.size >= 4)
    }

    @Test
    fun longPressSelectsZonesAndStartsSequence() {
        var started: List<Zone>? = null
        compose.setContent { PhotoMedTheme(darkTheme = false) {
            ExpedienteContent(exp, emptyList(), SnackbarHostState(), {}, {}, {}, {}, {}, onSequence = { started = it })
        } }
        val grid = compose.onNode(hasScrollAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        // mantener presionada una zona inicia la selección; tocar otras las añade en orden
        grid.performScrollToNode(hasContentDescription("Labios", substring = true))
        compose.onNodeWithContentDescription("Labios", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("1 zona").assertExists()
        grid.performScrollToNode(hasContentDescription("Rostro frontal", substring = true))
        compose.onNodeWithContentDescription("Rostro frontal", substring = true).performClick()
        compose.onNodeWithText("2 zonas").assertExists()
        compose.waitForIdle()
        val v = compose.activity.window.decorView
        val img = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { v.draw(android.graphics.Canvas(img)) }
        File("build/reports").mkdirs()
        File("build/reports/expediente_seleccion.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
        compose.onNodeWithText("Iniciar secuencia de fotos (2)", useUnmergedTree = true).performClick()
        assertEquals(listOf(Zone.LIPS, Zone.FACE_FRONTAL), started)
    }

    @Test
    fun templateSelectsZonesAndCanBeSaved() {
        var started: List<Zone>? = null
        compose.setContent { PhotoMedTheme(darkTheme = false) {
            ExpedienteContent(exp, emptyList(), SnackbarHostState(), {}, {}, {}, {}, {}, onSequence = { started = it })
        } }
        compose.onNodeWithText("Rostro 5 vistas", substring = true).performClick()
        compose.onNodeWithText("5 zonas").assertExists()
        compose.onNodeWithContentDescription("Guardar como plantilla").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Control mensual")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitForIdle()
        assertTrue(SessionTemplates.custom(prefs).any { it.name == "Control mensual" && it.zones.size == 5 })
        compose.onNodeWithText("Iniciar secuencia de fotos (5)", useUnmergedTree = true).performClick()
        assertEquals(Zone.FACE_FRONTAL, started!!.first())
    }

    @Test
    fun cuesComeOneAtATimeByPriority() {
        val items = listOf("Mirada: al frente", "Barbilla: bajar 8° (Frankfort)", "Distancia: 70 cm → 120 cm", "Luz desigual: lado izquierdo más oscuro")
        assertEquals("Distancia: 70 cm → 120 cm", items.minBy(HintCues::priority))
        val d = HintCues.of("Distancia: 70 cm → 120 cm")!!
        assertEquals("70→120 cm", d.value)
    }
}
