package com.photomed.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.AlignOptions
import com.photomed.app.data.Expediente
import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.Photo
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Zone
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.compare.CompareScreen
import com.photomed.app.ui.gallery.ZoneGalleryScreen
import com.photomed.app.ui.settings.SettingsScreen
import com.photomed.app.ui.theme.PhotoMedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
 * Pruebas de uso: recorren las pantallas como un usuario (toques, pulsación larga, atrás, cambios
 * rápidos de modo y de selección) para detectar cierres inesperados y estados incoherentes.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w600dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UsageFlowsTest {
    @get:Rule(order = 0)
    val tmp = TemporaryFolder()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var repo: PhotoRepository
    private lateinit var exp: Expediente
    private lateinit var photos: List<Photo>
    private val day = 86_400_000L

    @Before
    fun setUp() {
        val gallery = File(tmp.root, "Pictures/PhotoMed")
        repo = PhotoRepository(File(tmp.root, "private"), FilePhotoStore(gallery),
            openStream = { uri -> File(uri.path!!).inputStream() })
        exp = repo.createExpediente("Paciente Prueba", "900", "", consent = true)
        val jpeg = File(tmp.root, "c.jpg").apply { writeBytes(this@UsageFlowsTest.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        photos = (0 until 4).map { repo.savePhoto(exp, Zone.LIPS, jpeg, takenAt = 1_763_400_000_000L + it * 30 * day) }
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalRepository provides repo) { PhotoMedTheme(darkTheme = false) { content() } }
    }

    private fun waitText(text: String, substring: Boolean = false) = try {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) { println("ESPERANDO '$text'\n" + compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().size + "\n" + compose.onAllNodes(androidx.compose.ui.test.isRoot()).let { r -> (0 until r.fetchSemanticsNodes().size).joinToString("\n") { r[it].printToString() } }); throw e }

    private fun photoNodes() = compose.onAllNodesWithContentDescription("Fotografía del", substring = true)

    @Test
    fun doubleBackNeverLeavesTheApp() {
        // Doble toque en "Atrás" (o toque durante la animación de salida): antes se sacaban dos
        // pantallas de la pila, se vaciaba y la app se cerraba. Ahora solo regresa una.
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("photomed", Context.MODE_PRIVATE)
            .edit().putBoolean("terms_accepted_v1", true).commit()
        setContent { com.photomed.app.ui.AppNavigation() }
        waitText("Paciente Prueba")
        compose.onNodeWithText("Paciente Prueba").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Atrás").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.autoAdvance = false
        compose.onAllNodesWithContentDescription("Atrás")[0].performClick()
        compose.mainClock.advanceTimeBy(50)
        // la pantalla que sale sigue visible un instante: un segundo toque no debe sacar otra
        val again = compose.onAllNodesWithContentDescription("Atrás").fetchSemanticsNodes().isNotEmpty()
        if (again) compose.onAllNodesWithContentDescription("Atrás")[0].performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        waitText("Paciente Prueba")
        assertFalse(compose.activity.isFinishing)
        println("REGRESO doble: segundo toque probado = $again")
    }

    @Test
    fun galleryMultiSelectCompareShareDelete() {
        var compared: List<String>? = null
        setContent { ZoneGalleryScreen(exp.id, Zone.LIPS, {}, {}, { compared = it }, {}) }
        try { compose.waitUntil(5_000) { photoNodes().fetchSemanticsNodes().size == 4 } }
        catch (e: Throwable) { println(compose.onRoot().printToString()); throw e }

        // Pulsación larga inicia la selección; toques añaden/quitan.
        photoNodes()[0].performTouchInput { longClick() }
        compose.onNodeWithText("1 seleccionada").assertExists()
        photoNodes()[1].performClick(); photoNodes()[2].performClick()
        compose.onNodeWithText("3 seleccionadas").assertExists()
        photoNodes()[2].performClick()
        compose.onNodeWithText("2 seleccionadas").assertExists()
        // Pulsación larga sobre una ya elegida no la quita.
        photoNodes()[0].performTouchInput { longClick() }
        compose.onNodeWithText("2 seleccionadas").assertExists()

        compose.onNodeWithContentDescription("Comparar seleccionadas").performClick()
        assertEquals(2, compared?.size)
        assertTrue(compared!!.all { n -> photos.any { it.displayName == n } })

        // "Atrás" cancela la selección en lugar de salir.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Tomar foto", useUnmergedTree = true).assertExists()
        assertFalse(compose.onAllNodesWithText("seleccionada", substring = true).fetchSemanticsNodes().isNotEmpty())

        // Sin selección, comparar usa la selección automática.
        compose.onNodeWithContentDescription("Comparar").performClick()
        assertEquals(emptyList<String>(), compared)

        // Seleccionar todas y eliminar (con confirmación).
        photoNodes()[3].performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Seleccionar todas").performClick()
        compose.onNodeWithText("4 seleccionadas").assertExists()
        compose.onNodeWithContentDescription("Eliminar seleccionadas").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        waitText("Sin fotografías de esta zona")
        assertEquals(0, repo.photos(exp, Zone.LIPS).size)
    }

    @Test
    fun compareNPhotosModesAndSelectionStress() {
        setContent { CompareScreen(exp.id, Zone.LIPS, onBack = {}, initialSelection = photos.map { it.displayName }) }
        waitText("Comparar 4 fotos")

        // Cambios rápidos de modo (cruces de eventos de composición).
        repeat(3) { listOf("Cuadrícula", "Lado a lado", "Fundido", "Deslizar").forEach { compose.onNodeWithText(it).performClick() } }
        compose.onNodeWithText("Cuadrícula").performClick()
        compose.waitForIdle()
        (1..4).forEach { i -> assertTrue(compose.onAllNodesWithText("$i · ", substring = true).fetchSemanticsNodes().isNotEmpty()) }

        // Línea de tiempo progresiva: 1→2→3→4.
        compose.onNodeWithText("Fundido").performClick()
        compose.onNode(hasContentDescription("Progresión entre 4 fotos"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2.5f) }
        compose.onNodeWithText("Deslizar").performClick()
        compose.onNode(hasContentDescription("Progresión entre 4 fotos"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.waitForIdle()

        // Quitar fotos mientras se está en la última transición: no debe salirse de rango.
        val strip = { compose.onAllNodes(hasContentDescription("Labios", substring = true) and hasClickLabel()) }
        val chosen = { compose.onAllNodes(hasContentDescription("Labios", substring = true) and hasClickLabel() and isSelected()) }
        chosen()[0].performClick()
        waitText("Comparar 3 fotos")
        chosen()[0].performClick()
        waitText("Antes", substring = true)
        chosen()[0].performClick()
        waitText("Seleccione dos o más", substring = true)
        // Volver a elegir todas, rápido.
        (0 until 4).forEach { strip()[it].performClick() }
        waitText("Comparar 3 fotos")

        // Invertir orden repetidamente y menú con casillas persistentes.
        repeat(5) { compose.onNodeWithContentDescription("Invertir el orden").performClick() }
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Usar mirada (gaze)").performClick()
        compose.onNodeWithText("Usar contornos").performClick()
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("photomed", Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean(AlignOptions.PREF_GAZE, true))
        assertFalse(prefs.getBoolean(AlignOptions.PREF_CONTOURS, true))
        assertTrue(prefs.getBoolean(AlignOptions.PREF_POINTS, true))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun compareOverlaysOpenAndCloseQuickly() {
        setContent { CompareScreen(exp.id, Zone.LIPS, onBack = {}, initialSelection = photos.take(2).map { it.displayName }) }
        waitText("Antes", substring = true)
        repeat(2) {
            compose.onNodeWithContentDescription("Más opciones").performClick()
            compose.onNodeWithText("Perspectiva y ajustes").performClick()
            waitText("Perspectiva vertical")
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
        }
        // Cambiar la selección con la hoja cerrada y volver a abrir.
        compose.onNodeWithContentDescription("Invertir el orden").performClick()
        compose.onNodeWithText("Deslizar").assertExists()
    }

    @Test
    fun settingsAlignmentChecksPersist() {
        setContent { SettingsScreen(onBack = {}) }
        compose.onNodeWithText("Usar puntos anatómicos").performClick()
        compose.onNodeWithText("Usar puntos anatómicos").performClick()
        compose.onNodeWithText("Usar mirada (gaze)").performClick()
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("photomed", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean(AlignOptions.PREF_POINTS, false))
        assertFalse(prefs.getBoolean(AlignOptions.PREF_GAZE, true))
        // La cámara usa la misma preferencia ("Exigir la misma mirada").
        compose.onNodeWithText("Exigir la misma mirada").assertExists()
    }

    private fun hasClickLabel() = androidx.compose.ui.test.SemanticsMatcher("clickable") {
        it.config.contains(SemanticsActions.OnClick)
    }
}

/** Capturas de la comparación de N fotos con rostros sintéticos (build/reports/compare_*.png). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompareNCaptureTest {
    @get:Rule(order = 0)
    val tmp = TemporaryFolder()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureGridAndProgressive() {
        val repo = PhotoRepository(File(tmp.root, "private"), FilePhotoStore(File(tmp.root, "Pictures/PhotoMed")),
            openStream = { uri -> File(uri.path!!).inputStream() })
        val exp = repo.createExpediente("Demostración", "000", "", consent = true)
        val photos = (1..3).map { i ->
            val f = File(tmp.root, "f$i.jpg").apply { writeBytes(this@CompareNCaptureTest.javaClass.getResourceAsStream("/demo/face_$i.jpg")!!.readBytes()) }
            repo.savePhoto(exp, Zone.FACE_FRONTAL, f, takenAt = 1_763_400_000_000L + i * 45 * 86_400_000L)
        }
        compose.setContent {
            CompositionLocalProvider(LocalRepository provides repo) {
                PhotoMedTheme(darkTheme = false) {
                    CompareScreen(exp.id, Zone.FACE_FRONTAL, onBack = {}, initialSelection = photos.map { it.displayName })
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Comparar 3 fotos").fetchSemanticsNodes().isNotEmpty() }
        val out = File("build/reports").apply { mkdirs() }
        fun shot(name: String) {
            compose.waitForIdle(); Thread.sleep(400); compose.waitForIdle()
            val v = compose.activity.window.decorView
            val img = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { v.draw(android.graphics.Canvas(img)) }
            File(out, "compare_$name.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNode(hasContentDescription("Progresión entre 3 fotos"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.4f) }
        shot("progresivo")
        compose.onNodeWithText("Cuadrícula").performClick(); shot("cuadricula")
        compose.onNodeWithText("Lado a lado").performClick(); shot("lado_a_lado")
        compose.onNodeWithContentDescription("Más opciones").performClick(); shot("menu")
    }
}

/** Exportación de la comparación: archivos, anonimización, subálbum y misma transformación que la vista previa. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompareExportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun matrixMatchesPreviewTransform() {
        val t = com.photomed.app.align.Similarity(1.08f, Math.toRadians(4.0).toFloat(), 0.03f, -0.02f)
        val m = com.photomed.app.ui.compare.CompareExport.matrixOf(t, 1080f)
        for (p in listOf(com.photomed.app.align.Pt(0.2f, 0.3f), com.photomed.app.align.Pt(0.7f, 1.1f))) {
            val v = floatArrayOf(p.x * 1080f, p.y * 1080f); m.mapPoints(v)
            val e = t.apply(p)
            assertEquals(e.x * 1080f, v[0], 0.5f); assertEquals(e.y * 1080f, v[1], 0.5f)
        }
    }

    @Test
    fun exportsAdjustedPhotosAndGrid() {
        // Coil necesita el hilo principal: el trabajo va en otro hilo y aquí se procesa el looper.
        val fut = java.util.concurrent.Executors.newSingleThreadExecutor().submit<Unit> { kotlinx.coroutines.runBlocking { exportBody() } }
        val end = System.currentTimeMillis() + 60_000
        while (!fut.isDone && System.currentTimeMillis() < end) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10) }
        fut.get(1, java.util.concurrent.TimeUnit.SECONDS)
    }

    private suspend fun exportBody() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = PhotoRepository(File(tmp.root, "private"), FilePhotoStore(File(tmp.root, "Pictures/PhotoMed")),
            openStream = { uri -> File(uri.path!!).inputStream() })
        val exp = repo.createExpediente("Ana Pérez", "077", "", consent = true)
        val photos = (1..3).map { i ->
            val f = File(tmp.root, "f$i.jpg").apply { writeBytes(this@CompareExportTest.javaClass.getResourceAsStream("/demo/face_$i.jpg")!!.readBytes()) }
            repo.savePhoto(exp, Zone.FACE_FRONTAL, f, takenAt = 1_763_400_000_000L + i * 86_400_000L)
        }
        val shift = com.photomed.app.align.AlignmentResult(com.photomed.app.align.Similarity(1.05f, 0.02f, 0.01f, 0f), "test", emptyList(), 0.5f)
        val dir = File(tmp.root, "out")
        val r = com.photomed.app.ui.compare.CompareExport.build(ctx, repo, photos, mapOf(photos[1].key to shift), emptyMap(),
            individual = true, grid = true, anonymize = true, dir = dir)
        assertEquals(0, r.failed)
        assertEquals(4, r.files.size)
        assertTrue(r.files.none { it.name.contains("Ana") || it.name.contains("Perez") || it.name.contains("077") })
        val grid = android.graphics.BitmapFactory.decodeFile(r.files.last().absolutePath)
        assertTrue("cuadrícula 3 en fila", grid.width > grid.height)
        val one = android.graphics.BitmapFactory.decodeFile(r.files[1].absolutePath)
        assertEquals(1080, one.width); assertEquals(1440, one.height)
        // anonimizadas: sin metadatos del paciente
        assertTrue(r.files.take(3).all { f -> f.inputStream().use { com.photomed.app.data.PhotoMetadata.read(it) }?.patientName.isNullOrEmpty() })
        // en la galería: subálbum Exportadas, fuera de las fotos clínicas del expediente
        val saved = com.photomed.app.ui.compare.CompareExport.saveToGallery(repo, exp, r.files)
        assertEquals(4, saved)
        assertTrue(File(tmp.root, "Pictures/PhotoMed/${exp.id}/Exportadas").listFiles()!!.size == 4)
        assertEquals(3, repo.photos(exp).size)
    }
}
