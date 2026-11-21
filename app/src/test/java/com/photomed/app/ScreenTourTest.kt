package com.photomed.app

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.data.Expediente
import com.photomed.app.data.FacialExpression
import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.Photo
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Protocol
import com.photomed.app.data.ProtocolResults
import com.photomed.app.data.ProtocolStep
import com.photomed.app.data.Protocols
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.theme.PhotoMedTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Recorrido por las pantallas con datos de ejemplo (retratos de prueba locales): una captura de cada
 * una en build/reports/tour/ para revisar el aprovechamiento del espacio y el diseño. No comprueba
 * nada: solo genera las capturas (se omite sin los retratos de prueba).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenTourTest {
    @get:Rule(order = 0) val tmp = TemporaryFolder()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var repo: PhotoRepository
    private lateinit var exp: Expediente
    private lateinit var photos: List<Photo>
    private val out = File("build/reports/tour").apply { mkdirs() }

    @Before
    fun setUp() {
        coil.Coil.reset()
        com.photomed.app.ui.components.CoachMarks.enabled = false; com.photomed.app.ui.compare.CompareIdle.enabled = false
        val faces = File("src/test/resources/stock/new").listFiles { f -> f.name.endsWith(".jpg") }?.sorted().orEmpty()
        assumeTrue("sin retratos de prueba", faces.size >= 3)
        repo = PhotoRepository(File(tmp.root, "private"), FilePhotoStore(File(tmp.root, "Pictures/PhotoMed")),
            openStream = { uri -> File(uri.path!!).inputStream() })
        exp = repo.createExpediente("María José Peña", "0012", "Toxina botulínica tercio superior", consent = true)
        val day = 86_400_000L; val t0 = 1_760_000_000_000L
        val p = Protocol(name = "Control de toxina a 15 días", author = "Dra. Ejemplo", steps = listOf(
            ProtocolStep(zone = Zone.FACE_FRONTAL, count = 2, threshold = 0.9f, minLight = 0.4f),
            ProtocolStep(zone = Zone.FOREHEAD, expression = FacialExpression.SURPRISE),
            ProtocolStep(zone = Zone.FACE_OBLIQUE_R)))
        Protocols.list(ctx).forEach { Protocols.delete(ctx, it.id) }
        Protocols.save(ctx, p)
        photos = listOf(
            repo.savePhoto(exp, Zone.FACE_FRONTAL, faces[0], takenAt = t0),
            repo.savePhoto(exp, Zone.FACE_FRONTAL, faces[1], takenAt = t0 + 30 * day, conditions = ProtocolResults.conditions(p, 0, 1)),
            repo.savePhoto(exp, Zone.FACE_OBLIQUE_R, faces[2], takenAt = t0 + 31 * day),
        )
        listOf("Carlos Rivas" to "0031", "Ana Gómez" to "0045", "Lucía Fernández" to "0052").forEachIndexed { i, (n, r) ->
            val e = repo.createExpediente(n, r, "", consent = i != 1)
            if (i == 0) repo.savePhoto(e, Zone.FACE_FRONTAL, faces[3 % faces.size], takenAt = t0 + 10 * day)
        }
        ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().clear().putString("annot_layers", "CONTOUR,FOLDS,FOREHEAD,JAWLINE").commit()
    }

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun shot(name: String, settle: Long = 1500) {
        compose.mainClock.advanceTimeBy(settle)
        runCatching { compose.waitUntil(3_000) { false } }
        runCatching {
            // la vista de la ventana dibujada en un mapa de bits (sin esperar a que no haya animaciones)
            compose.runOnUiThread {
                val v = compose.activity.window.decorView
                val img = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(img))
                File(out, "$name.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 90, it) }
            }
        }.onFailure { println("CAPTURA $name: ${it.message}") }
    }

    private fun show(c: @Composable () -> Unit) { screen = c; compose.waitForIdle() }

    private fun waitFor(text: String) = runCatching {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun tour() {
        compose.setContent {
            CompositionLocalProvider(LocalRepository provides repo) { PhotoMedTheme(darkTheme = false) { screen?.invoke() } }
        }
        val z = Zone.FACE_FRONTAL
        show { com.photomed.app.ui.HomeScreen({}, {}, {}, {}, { _, _ -> }) }
        waitFor("Carlos"); shot("01_inicio_expedientes", 3000)
        compose.onNode(androidx.compose.ui.test.hasText("Protocolos") and androidx.compose.ui.test.hasClickAction()).performClick(); shot("02_inicio_protocolos")
        show { com.photomed.app.ui.expediente.ExpedienteScreen(exp.id, {}, {}, {}) }
        waitFor("María"); shot("03_expediente")
        show { com.photomed.app.ui.gallery.ZoneGalleryScreen(exp.id, z, {}, {}, {}, {}) }
        shot("04_galeria_zona", 2500)
        show { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, z, photos[1].displayName, onBack = {}) }
        waitFor("Medidas"); shot("05_visor", 2500)
        runCatching { compose.onNodeWithText("Medidas").performClick() }; shot("06_visor_medidas")
        runCatching { compose.onNodeWithContentDescription("Anotar sobre la foto").performClick() }; shot("07_visor_anotar")
        runCatching { compose.onNodeWithContentDescription("Símbolo").performClick() }; shot("08_visor_simbolos")
        show { com.photomed.app.ui.compare.CompareScreen(exp.id, z, onBack = {}, initialSelection = photos.take(2).map { it.displayName }) }
        waitFor("Comparar"); shot("09_comparar", 3000)
        runCatching { compose.onNodeWithText("Cuadrícula").performClick() }; shot("10_comparar_cuadricula")
        runCatching { compose.onNodeWithText("Medidas").performClick() }; shot("11_comparar_medidas")
        runCatching { compose.onNodeWithContentDescription("Más opciones").performClick() }; shot("12_comparar_menu")
        show { com.photomed.app.ui.protocol.ProtocolEditorScreen(exp.id, Protocols.list(ctx).first().id, onBack = {}) }
        shot("13_editor_protocolo", 2500)
        show { com.photomed.app.ui.settings.SettingsScreen(onBack = {}) }; shot("14_ajustes")
        show { com.photomed.app.ui.legal.AboutScreen(onBack = {}, onDoc = {}) }; shot("15_acerca_de")
        println("RECORRIDO: capturas en ${out.absolutePath}")
    }

    @Test
    @Config(sdk = [34], qualifiers = "es-w851dp-h393dp-land-xhdpi")
    fun tourLandscape() {
        compose.setContent {
            CompositionLocalProvider(LocalRepository provides repo) { PhotoMedTheme(darkTheme = false) { screen?.invoke() } }
        }
        val z = Zone.FACE_FRONTAL
        show { com.photomed.app.ui.HomeScreen({}, {}, {}, {}, { _, _ -> }) }
        waitFor("Carlos"); shot("20_horizontal_inicio", 3000)
        show { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, z, photos[1].displayName, onBack = {}) }
        waitFor("Medidas"); runCatching { compose.onNodeWithText("Medidas").performClick() }; shot("21_horizontal_visor_medidas", 2500)
        show { com.photomed.app.ui.compare.CompareScreen(exp.id, z, onBack = {}, initialSelection = photos.take(2).map { it.displayName }) }
        waitFor("Comparar"); shot("22_horizontal_comparar", 3000)
    }
}
