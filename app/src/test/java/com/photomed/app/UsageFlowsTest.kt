package com.photomed.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.filterToOne
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
        // cargador de imágenes nuevo por prueba: el de Coil es único en el proceso y queda ligado al
        // entorno (hilo principal) de la prueba anterior, con cargas aún en curso
        coil.Coil.reset()
        com.photomed.app.ui.components.CoachMarks.enabled = false; com.photomed.app.ui.compare.CompareIdle.enabled = false
        val gallery = File(tmp.root, "Pictures/PhotoMed")
        repo = PhotoRepository(File(tmp.root, "private"), FilePhotoStore(gallery),
            openStream = { uri -> File(uri.path!!).inputStream() })
        exp = repo.createExpediente("Paciente Prueba", "900", "", consent = true)
        val jpeg = File(tmp.root, "c.jpg").apply { writeBytes(this@UsageFlowsTest.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        photos = (0 until 4).map { repo.savePhoto(exp, Zone.LIPS, jpeg, takenAt = 1_763_400_000_000L + it * 30 * day) }
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @org.junit.After
    fun tearDown() {
        // cerrar la pantalla (cancela análisis y cargas en curso) antes de la siguiente prueba
        runCatching { compose.setContent {} ; compose.waitForIdle() }
        coil.Coil.reset()
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalRepository provides repo) { PhotoMedTheme(darkTheme = false) { content() } }
    }

    private fun waitText(text: String, substring: Boolean = false) = try {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) { println("ESPERANDO '$text'\n" + compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().size + "\n" + compose.onAllNodes(androidx.compose.ui.test.isRoot()).let { r -> (0 until r.fetchSemanticsNodes().size).joinToString("\n") { r[it].printToString() } }); throw e }

    /** Clave de caché de las anotaciones de una foto en el visor (como en PhotoPage). */
    private fun viewerKey(p: Photo): String {
        val ctx = ApplicationProvider.getApplicationContext<Context>(); val u = p.uri.toString()
        val edit = com.photomed.app.align.PhotoEdits.of(ctx, u)
        val previous = photos.filter { it.takenAt < p.takenAt }.sortedByDescending { it.takenAt }.map { it.uri.toString() }
        return "full|$u|${edit.hashCode()}|${com.photomed.app.align.ReliefStore.tag(ctx, u)}|" +
            "${com.photomed.app.align.AnnotUserEdits.tag(ctx, u)}|${com.photomed.app.align.LineMemory.tag(ctx, previous)}"
    }

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
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Atrás").fetchSemanticsNodes().isNotEmpty() }
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
        try { compose.waitUntil(15_000) { photoNodes().fetchSemanticsNodes().size == 4 } }
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
    fun compareShowsTimeOnlyForSameDayPhotos() {
        val jpeg = File(tmp.root, "d.jpg").apply { writeBytes(this@UsageFlowsTest.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        val cal = java.util.Calendar.getInstance()
        fun at(d: Int, h: Int, m: Int) = cal.apply { set(2026, 10, d, h, m, 0) }.timeInMillis
        val same = listOf(at(12, 9, 5), at(12, 17, 40)).map { repo.savePhoto(exp, Zone.FOREHEAD, jpeg, takenAt = it) }
        val other = repo.savePhoto(exp, Zone.FOREHEAD, jpeg, takenAt = at(19, 11, 0))
        setContent { CompareScreen(exp.id, Zone.FOREHEAD, onBack = {}, initialSelection = (same + other).map { it.displayName }) }
        compose.onNodeWithText("Cuadrícula").performClick()
        waitText("09:05", substring = true)
        waitText("17:40", substring = true)
        // la del otro día, solo con la fecha
        assertTrue(compose.onAllNodesWithText("11:00", substring = true).fetchSemanticsNodes().isEmpty())
        runCatching {
            val img = compose.onRoot().captureToImage().asAndroidBitmap()
            File("build/reports").mkdirs()
            File("build/reports/compare_same_day.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
        }
    }

    @Test
    fun exportSheetSingleChoiceAndGifPreview() {
        var choice: com.photomed.app.ui.compare.ExportChoice? = null
        val bmps = listOf(android.graphics.Color.RED, android.graphics.Color.BLUE).map { c ->
            android.graphics.Bitmap.createBitmap(90, 120, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(c) } to "x"
        }
        setContent {
            com.photomed.app.ui.compare.ExportSheet(count = 2, aligned = true, colorMatched = false, onDismiss = {},
                loadPreview = { bmps },
                onExport = { ch, _, _ -> choice = ch; null }, onMessage = {})
        }
        waitText("Animación GIF")
        // la vista previa es una animación sin fin: el reloj de la prueba avanza a mano
        compose.mainClock.autoAdvance = false
        // elección única: al marcar GIF se desmarca la cuadrícula
        compose.onNodeWithText("Animación GIF").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.onNode(androidx.compose.ui.test.hasText("Cuadrícula con fechas")).assertExists()
        waitText("Tiempo por imagen", substring = true)
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Vista previa del GIF").assertExists()
        compose.onNodeWithContentDescription("Tiempo de transición")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        compose.mainClock.advanceTimeBy(100)
        waitText("sin transición", substring = true)
        compose.onNodeWithText("  Guardar", substring = true).performClick()
        compose.mainClock.advanceTimeBy(200)
        repeat(50) { if (choice == null) { compose.mainClock.advanceTimeBy(100); Thread.sleep(20) } }
        assertEquals(com.photomed.app.ui.compare.ExportKind.GIF, choice!!.kind)
        assertEquals(0, choice!!.timing.transitionMs)
        assertEquals(1200, choice!!.timing.holdMs)
    }

    @Test
    fun expedienteCollapsedCategoriesAndCoachMark() {
        com.photomed.app.ui.components.CoachMarks.enabled = true
        setContent { com.photomed.app.ui.expediente.ExpedienteScreen(exp.id, onBack = {}, onZone = {}, onCompare = {}) }
        waitText("Con fotos", substring = true)
        // las zonas sin fotos van plegadas por categoría; al tocar el encabezado aparecen
        assertTrue(compose.onAllNodesWithText("Rostro frontal").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Facial · ", substring = true).performClick()
        waitText("Rostro frontal")
        // ayuda de primera vez: se cierra con "Entendido" y no vuelve
        waitText("Mantenga pulsada una zona", substring = true)
        compose.onNodeWithText("Entendido").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Mantenga pulsada una zona", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(com.photomed.app.ui.components.CoachMarks.seen(ApplicationProvider.getApplicationContext(), "expediente_seleccion"))
        com.photomed.app.ui.components.CoachMarks.enabled = false
    }

    @Test
    fun compareControlsHideWhenIdleAndReturnOnTouch() {
        com.photomed.app.ui.compare.CompareIdle.enabled = true
        setContent { CompareScreen(exp.id, Zone.LIPS, onBack = {}, initialSelection = photos.take(2).map { it.displayName }) }
        waitText("Antes", substring = true)
        compose.mainClock.advanceTimeBy(com.photomed.app.ui.compare.CompareIdle.HIDE_MS + 1000)
        compose.waitForIdle()
        assertTrue("controles ocultos", compose.onAllNodesWithContentDescription("Labios", substring = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Deslizar").performClick()
        compose.mainClock.advanceTimeBy(500)
        waitText("Labios", substring = true)
        com.photomed.app.ui.compare.CompareIdle.enabled = false
    }

    @Test
    fun viewerEditBarSavesRotationAndMirror() {
        com.photomed.app.align.PhotoEdits.clearCache()
        setContent { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, Zone.LIPS, photos[0].displayName, onBack = {}) }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Girar, espejo y recorte").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Girar, espejo y recorte").performClick()
        compose.onNodeWithContentDescription("Girar 90°").performClick()
        compose.onNodeWithContentDescription("Espejo").performClick()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val e = com.photomed.app.align.PhotoEdits.of(ctx, photos[0].uri.toString())
        assertEquals(1, e.rot); assertTrue(e.mirror)
        compose.onNodeWithContentDescription("Original").performClick()
        assertTrue(com.photomed.app.align.PhotoEdits.of(ctx, photos[0].uri.toString()).isIdentity)
        // capas: los interruptores están siempre (sin botón de capas) y se recuerdan
        compose.onNodeWithContentDescription("Contorno interno", substring = true).performScrollTo().performClick()
        waitText("Surcos")
        compose.onNodeWithText("Surcos").performClick()
        compose.onNodeWithText("Medidas").assertExists()
        compose.waitForIdle()
        assertTrue(ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).getString("annot_layers", "")!!.contains("FOLDS"))
    }

    /**
     * Medidas de las arrugas en el visor: con las capas de arrugas activadas, tocar una línea abre su
     * ficha y el chip «Medidas» abre la tabla. (Las líneas se ponen en la caché del analizador: en las
     * pruebas no hay MediaPipe.)
     */
    @Test
    fun viewerShowsWrinkleMeasurementsOnTapAndTable() {
        com.photomed.app.align.PhotoEdits.clearCache()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().putString("annot_layers", "FOREHEAD,FOLDS").commit()
        val p = photos[0]
        val edit = com.photomed.app.align.PhotoEdits.of(ctx, p.uri.toString())
        val forehead = com.photomed.app.align.AnnotLine(com.photomed.app.align.AnnotLayer.FOREHEAD,
            (0..10).map { com.photomed.app.align.Pt(0.1f + it * 0.08f, 0.5f) }, false,
            metrics = com.photomed.app.align.LineMetrics("Frente, línea 1", lengthMm = 42f, widthMm = 1.2f, contrastL = 8f, depthMm = 0.31f, grade = 2))
        com.photomed.app.align.PhotoAnnotator.put(viewerKey(p), listOf(forehead))
        setContent { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, Zone.LIPS, p.displayName, onBack = {}) }
        waitText("Medidas")
        // toque sobre la línea: centro de la foto ajustada a la página (y = 0,5 × ancho de la foto)
        val page = compose.onNodeWithContentDescription("Fotografía", substring = true).fetchSemanticsNode().boundsInRoot
        val ia = 30f / 40f
        val w = minOf(page.width, page.height * ia); val h = w / ia
        val x = page.left + (page.width - w) / 2 + 0.5f * w; val y = page.top + (page.height - h) / 2 + 0.5f * w
        compose.onNodeWithContentDescription("Fotografía", substring = true).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(x - page.left, y - page.top))
        }
        // sin ficha flotante: se abre la tabla con la fila de la línea seleccionada (resaltada)
        waitText("Arrugas y surcos")
        assertTrue(compose.onAllNodesWithText("Tabla de medidas").fetchSemanticsNodes().isEmpty())
        compose.onNode(isSelected() and hasText("Frente, línea 1", substring = true)).assertExists()
        // selección hecha en la foto: la tabla se filtra a esa línea, con «Ver todas» para volver
        compose.onNodeWithText("Ver todas (1)").assertExists()
        compose.onNodeWithText("0.31").assertExists()
        // diseño: selector de capas encima de la foto y tabla debajo, sin taparla
        val photoBox = compose.onNodeWithContentDescription("Fotografía", substring = true).fetchSemanticsNode().boundsInRoot
        val chips = compose.onNodeWithContentDescription("Contorno interno", substring = true).fetchSemanticsNode().boundsInRoot
        val table = compose.onNodeWithText("Arrugas y surcos").fetchSemanticsNode().boundsInRoot
        assertTrue("chips encima de la foto", chips.bottom <= photoBox.top + 1f)
        assertTrue("tabla debajo de la foto", table.top >= photoBox.bottom - 1f)
        // opacidad de trazos y números: empieza en 50 %
        compose.onNodeWithText("Opacidad 50 %").assertExists()
        compose.onNodeWithContentDescription("Opacidad de los trazos y números")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.8f) }
        compose.waitForIdle()
        assertEquals(0.8f, ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).getFloat("annot_alpha", 0f), 0.01f)
        // contorno interno (rasgos, arrugas, surcos y ojeras): desplegable con casillas y «Seleccionar todos»
        fun layersNow() = ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).getString("annot_layers", "")!!
        compose.onNodeWithContentDescription("Contorno interno", substring = true).performClick()
        waitText("Seleccionar todos")
        compose.onNodeWithText("Seleccionar todos").performClick()
        compose.waitForIdle()
        assertTrue(layersNow(), listOf("EYES", "NOSE", "LIPS", "FOLDS", "FOREHEAD", "GLABELLA", "CROWS_FEET", "DARK_CIRCLES").all { it in layersNow() })
        assertTrue(layersNow(), "CONTOUR" !in layersNow())
        compose.onNodeWithText("Seleccionar todos").performClick()
        compose.waitForIdle()
        assertTrue(layersNow(), listOf("EYES", "NOSE", "LIPS", "FOREHEAD").none { it in layersNow() })
        // una sola capa con su casilla (el menú sigue abierto)
        compose.onNodeWithText("Frente").performClick()
        compose.waitForIdle()
        assertEquals("FOREHEAD", layersNow())

        assertTrue(compose.onAllNodesWithText("Frente, línea 1", substring = true).fetchSemanticsNodes().isNotEmpty()) // en la tabla
        // eliminar la línea (falso positivo) desde la tabla y restaurarla
        compose.onNodeWithText("Trazar surco").assertExists()
        compose.onNodeWithContentDescription("Eliminar Frente, línea 1 (falso positivo)").performClick()
        // eliminar confirma los trazos (guardados en la foto) sin esa línea
        compose.waitUntil(15_000) { com.photomed.app.align.ConfirmedTraces.of(ctx, p.uri.toString())?.removed?.any { it.name == "Frente, línea 1" } == true }
        assertTrue(com.photomed.app.align.ConfirmedTraces.of(ctx, p.uri.toString())!!.traces.none { it.name == "Frente, línea 1" })
        waitText("Restaurar líneas eliminadas (1)")
        compose.onNodeWithText("Restaurar líneas eliminadas (1)").performScrollTo().performClick()
        compose.waitUntil(15_000) { com.photomed.app.align.ConfirmedTraces.of(ctx, p.uri.toString())?.traces?.any { it.name == "Frente, línea 1" } == true }
        waitText("Trazos confirmados", substring = true)
    }

    /**
     * Con las capas de arrugas apagadas (por defecto), abrir «Capas» y «Medidas» muestra igualmente
     * las medidas y ofrece mostrarlas en la foto. (Antes la tabla salía siempre vacía: no se
     * analizaba la foto sin capas encendidas y solo se listaban las capas visibles.)
     */
    /** Horizontal / tableta apaisada: la tabla de medidas va a la derecha de la foto, no debajo. */
    @Test
    @Config(sdk = [34], qualifiers = "w1200dp-h700dp-land")
    fun landscapeMetricsPanelBesideThePhoto() {
        com.photomed.app.align.PhotoEdits.clearCache()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().putString("annot_layers", "FOREHEAD,FOLDS").commit()
        val p = photos[0]
        val forehead = com.photomed.app.align.AnnotLine(com.photomed.app.align.AnnotLayer.FOREHEAD,
            (0..10).map { com.photomed.app.align.Pt(0.1f + it * 0.08f, 0.5f) }, false,
            metrics = com.photomed.app.align.LineMetrics("Frente, línea 1", lengthMm = 42f, widthMm = 1.2f, contrastL = 8f, depthMm = 0.31f, grade = 2))
        com.photomed.app.align.PhotoAnnotator.put(viewerKey(p), listOf(forehead))
        setContent { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, Zone.LIPS, p.displayName, onBack = {}) }
        waitText("Medidas")
        compose.onNodeWithText("Medidas").performClick()
        waitText("Arrugas y surcos")
        val photoBox = compose.onNodeWithContentDescription("Fotografía", substring = true).fetchSemanticsNode().boundsInRoot
        val table = compose.onNodeWithText("Arrugas y surcos").fetchSemanticsNode().boundsInRoot
        // controles y tabla en la mitad izquierda, la foto en la derecha (sin ampliarla a todo el ancho)
        assertTrue("tabla a la izquierda de la foto: $photoBox / $table", table.right <= photoBox.left + 1f)
        assertTrue("la foto ocupa la mitad derecha: $photoBox", photoBox.left >= 1200f * compose.density.density * 0.45f)
        assertTrue("la tabla no empuja la foto hacia arriba", table.top < photoBox.bottom)
    }

    /** Deslizar entre fotos funciona con las capas activas (el toque para seleccionar no se queda el gesto). */
    @Test
    fun swipeBetweenPhotosWithLayersOn() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().putString("annot_layers", "FOREHEAD,FOLDS").commit()
        val p = photos[0]
        setContent { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, Zone.LIPS, p.displayName, onBack = {}) }
        waitText("1/", substring = true)
        compose.onAllNodesWithContentDescription("Fotografía", substring = true)[0].performTouchInput { swipeLeft() }
        waitText("2/", substring = true)
    }

    /** Comparar en horizontal: controles a la izquierda y las fotos en la mitad derecha. */
    @Test
    @Config(sdk = [34], qualifiers = "w1200dp-h700dp-land")
    fun compareLandscapeControlsLeftPhotosRight() {
        val jpeg = File(tmp.root, "l.jpg").apply { writeBytes(this@UsageFlowsTest.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        val ps = listOf(1L, 2L).map { repo.savePhoto(exp, Zone.FOREHEAD, jpeg, takenAt = it * 86_400_000L * 30) }
        setContent { CompareScreen(exp.id, Zone.FOREHEAD, onBack = {}, initialSelection = ps.map { it.displayName }) }
        waitText("Cuadrícula")
        val modes = compose.onNodeWithText("Cuadrícula").fetchSemanticsNode().boundsInRoot
        val w = 1200f * compose.density.density
        assertTrue("controles en la mitad izquierda: $modes", modes.right <= w * 0.55f)
        runCatching {
            val img = compose.onRoot().captureToImage().asAndroidBitmap()
            File("build/reports").mkdirs()
            File("build/reports/compare_landscape.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
        }
    }

    @Test
    fun measurementsTableWorksWithWrinkleLayersOff() {
        com.photomed.app.align.PhotoEdits.clearCache()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val p = photos[1]
        val edit = com.photomed.app.align.PhotoEdits.of(ctx, p.uri.toString())
        val fold = com.photomed.app.align.AnnotLine(com.photomed.app.align.AnnotLayer.FOLDS,
            (0..10).map { com.photomed.app.align.Pt(0.3f, 0.3f + it * 0.03f) }, false,
            metrics = com.photomed.app.align.LineMetrics("Nasogeniano derecho", lengthMm = 31f, widthMm = 3f, contrastL = 18f, depthMm = 1.2f, grade = 4))
        com.photomed.app.align.PhotoAnnotator.put(viewerKey(p), listOf(fold))
        setContent { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, Zone.LIPS, p.displayName, onBack = {}) }
        waitText("Medidas")
        compose.onNodeWithText("Medidas").performClick()
        waitText("Nasogeniano derecho", substring = true)
        compose.onNodeWithText("Mostrar en la foto: Surcos").performClick()
        compose.waitForIdle()
        assertTrue(ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).getString("annot_layers", "")!!.contains("FOLDS"))
    }

    /** Anotar: lápiz sobre la foto (capa aparte), deshacer y la capa se puede ocultar. */
    @Test
    fun drawAnnotationsOnThePhoto() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val p = photos[0]; val u = p.uri.toString()
        setContent { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, Zone.LIPS, p.displayName, onBack = {}) }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Anotar sobre la foto").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Anotar sobre la foto").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Lápiz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Color verde").performClick()
        compose.onNodeWithContentDescription("Dibuje sobre la foto").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(120f, 40f), 300) }
        compose.waitUntil(15_000) { com.photomed.app.align.Annotations.of(ctx, u)?.strokes?.size == 1 }
        assertEquals(0xFF00E676.toInt(), com.photomed.app.align.Annotations.of(ctx, u)!!.strokes.single().color)
        // borrador por trazo sobre el mismo sitio: lo quita; deshacer lo devuelve
        compose.onNodeWithContentDescription("Borrar trazo").performClick()
        compose.onNodeWithContentDescription("Dibuje sobre la foto").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(120f, 40f), 300) }
        compose.waitUntil(15_000) { com.photomed.app.align.Annotations.of(ctx, u) == null }
        compose.onNodeWithContentDescription("Deshacer").performClick()
        compose.waitUntil(15_000) { com.photomed.app.align.Annotations.of(ctx, u)?.strokes?.size == 1 }
        // símbolo con texto: un toque lo coloca como marcador
        compose.onNodeWithContentDescription("Símbolo").performClick()
        waitText("Estrella")
        compose.onNodeWithText("Estrella").performClick()
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("1 ml")
        compose.onNodeWithText("Abajo").performClick()
        compose.onNodeWithContentDescription("Dibuje sobre la foto").performTouchInput { click(center + androidx.compose.ui.geometry.Offset(-100f, -100f)) }
        compose.waitUntil(15_000) { com.photomed.app.align.Annotations.of(ctx, u)?.strokes?.size == 2 }
        val st = com.photomed.app.align.Annotations.of(ctx, u)!!.strokes.last()
        assertEquals(listOf("symbol", "star", "1 ml", "bottom", 1), listOf(st.kind, st.shape, st.text, st.side, st.points.size))
        // arrastrando, el cuadrado ocupa de un extremo al otro
        compose.onNodeWithText("Cuadrado").performClick()
        compose.onNodeWithContentDescription("Dibuje sobre la foto").performTouchInput { swipe(center + androidx.compose.ui.geometry.Offset(0f, 120f), center + androidx.compose.ui.geometry.Offset(150f, 220f), 300) }
        compose.waitUntil(15_000) { com.photomed.app.align.Annotations.of(ctx, u)?.strokes?.size == 3 }
        assertEquals(2, com.photomed.app.align.Annotations.of(ctx, u)!!.strokes.last().points.size)
        // seleccionar y arrastrar el marcador
        val before = com.photomed.app.align.Annotations.of(ctx, u)!!.strokes[1].points.single()
        compose.onNodeWithContentDescription("Seleccionar y mover").performClick()
        compose.onNodeWithContentDescription("Dibuje sobre la foto").performTouchInput {
            swipe(center + androidx.compose.ui.geometry.Offset(-100f, -100f), center + androidx.compose.ui.geometry.Offset(-40f, -100f), 300) }
        compose.waitUntil(15_000) { com.photomed.app.align.Annotations.of(ctx, u)!!.strokes[1].points.single().x > before.x + 0.01f }
        // la papelera pide confirmación
        compose.onNodeWithContentDescription("Borrar todas las anotaciones").performClick()
        waitText("¿Borrar todas las anotaciones?")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(3, com.photomed.app.align.Annotations.of(ctx, u)!!.strokes.size)
        compose.onAllNodesWithContentDescription("Terminar de anotar")[0].performClick()
        // pantalla completa: sin la barra de arriba
        compose.onNodeWithContentDescription("Pantalla completa").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithContentDescription("Anotar sobre la foto").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Salir de pantalla completa").performClick()
        compose.waitForIdle()
        // capas de la vista (en la fila de capas): las anotaciones se pueden ocultar
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Vista", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Vista", substring = true).performScrollTo().performClick()
        waitText("Anotaciones")
        compose.onNodeWithText("Anotaciones").performClick()
        compose.waitForIdle()
        assertTrue(ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).getString("view_layers", "")!! == "1101")
    }

    /**
     * Trazos confirmados en el visor: se muestran sin analizar; la línea vuelta a trazar se puede
     * revertir; «Volver a trazar» abre el dibujo a mano alzada; «Regenerar» los borra de la foto.
     */
    @Test
    fun confirmedTracesEditRevertAndRegenerate() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().putString("annot_layers", "FOREHEAD").commit()
        val p = photos[0]; val u = p.uri.toString()
        val line = com.photomed.app.align.StoredTrace(com.photomed.app.align.AnnotLayer.FOREHEAD,
            listOf(com.photomed.app.align.Pt(0.2f, 0.4f), com.photomed.app.align.Pt(0.6f, 0.42f)), false, false, false,
            com.photomed.app.align.LineMetrics("Frente, línea 1", lengthMm = 40f, depthMm = 0.3f, grade = 2))
        var set = com.photomed.app.align.TraceSet(4f / 3f, listOf(line))
        set = com.photomed.app.align.ConfirmedTraces.replace(set, "Frente, línea 1",
            listOf(com.photomed.app.align.Pt(0.2f, 0.45f), com.photomed.app.align.Pt(0.6f, 0.47f)), null)
        com.photomed.app.align.ConfirmedTraces.save(ctx, u, set)
        setContent { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, Zone.LIPS, p.displayName, onBack = {}) }
        waitText("Medidas")
        compose.onNodeWithText("Medidas").performClick()
        waitText("Trazos confirmados", substring = true)
        compose.onNodeWithText("Regenerar").assertExists()
        // revertir la línea vuelta a trazar
        compose.onNodeWithContentDescription("Revertir el trazado de Frente, línea 1").performClick()
        compose.waitUntil(15_000) { com.photomed.app.align.ConfirmedTraces.of(ctx, u)!!.editedNames.isEmpty() }
        assertEquals(0.4f, com.photomed.app.align.ConfirmedTraces.of(ctx, u)!!.byName("Frente, línea 1")!!.points.first().y, 1e-4f)
        // volver a trazar: se abre el dibujo a mano alzada sobre la foto
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Volver a trazar Frente, línea 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Volver a trazar Frente, línea 1").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Dibuje el surco sobre la foto").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Dibuje el surco…").performClick()   // cancelar el dibujo
        compose.waitForIdle()
        // regenerar: se borran de la app y de la foto
        compose.onAllNodesWithText("Medidas").filterToOne(hasClickAction()).performClick()
        waitText("Regenerar")
        compose.onNodeWithText("Regenerar").performScrollTo().performClick()
        waitText("¿Regenerar los trazos?")
        compose.onAllNodesWithText("Regenerar").filterToOne(hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(15_000) { com.photomed.app.align.ConfirmedTraces.of(ctx, u) == null }
        assertTrue(com.photomed.app.data.PhotoMetadata.tracesOf(com.photomed.app.data.XmpFile.read(ctx, p.uri)) == null)
    }

    /** Comparar: lo que hay bajo las fotos se pliega y se despliega (se recuerda). */
    @Test
    fun comparePanelCollapses() {
        setContent { CompareScreen(exp.id, Zone.LIPS, onBack = {}) }
        waitText("Ocultar controles")
        assertTrue(compose.onAllNodes(hasClickLabel()).fetchSemanticsNodes().isNotEmpty())
        val strip = { compose.onAllNodes(hasContentDescription("Labios", substring = true) and hasClickLabel()).fetchSemanticsNodes().size }
        assertTrue(strip() > 0)
        compose.onNodeWithText("Ocultar controles").performClick()
        waitText("Mostrar controles")
        compose.waitUntil(3_000) { strip() == 0 }
        assertFalse(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("photomed", Context.MODE_PRIVATE).getBoolean("compare_panel_open", true))
        compose.onNodeWithText("Mostrar controles").performClick()
        compose.waitUntil(3_000) { strip() > 0 }
    }

    /**
     * Revisión tras la captura: solo la foto recién tomada, sin «Eliminar», con Guardar y Repetir;
     * Repetir la borra del expediente. Comparar abre la foto con la anterior de la misma expresión.
     */
    @Test
    fun reviewAfterCaptureKeepsOrRetriesAndOpensCompare() {
        var kept = 0; var retried = 0; var compared: List<String>? = null
        val last = photos.last()
        setContent { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, Zone.LIPS, last.displayName, onBack = {},
            onCompare = { compared = it },
            review = com.photomed.app.ui.gallery.ReviewActions(onKeep = { kept++ }, onRetry = { retried++ })) }
        waitText("Guardar")
        compose.onNodeWithText("Repetir", substring = true).assertExists()
        compose.onNodeWithText("1/1", substring = true).assertExists()
        assertTrue(compose.onAllNodesWithContentDescription("Eliminar").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Comparar").performClick()
        assertEquals(listOf(photos[2].displayName, last.displayName), compared)
        compose.onNodeWithText("Guardar").performClick()
        assertEquals(1, kept)
        compose.onNodeWithText("Repetir", substring = true).performClick()
        compose.waitUntil(15_000) { retried == 1 }
        assertEquals(3, repo.photos(exp, Zone.LIPS).size)
        assertTrue(repo.photos(exp, Zone.LIPS).none { it.displayName == last.displayName })
    }

    /** El visor normal también abre Comparar (y conserva Eliminar). */
    @Test
    fun viewerOpensCompare() {
        var compared: List<String>? = null
        setContent { com.photomed.app.ui.gallery.PhotoViewerScreen(exp.id, Zone.LIPS, photos[1].displayName, onBack = {}, onCompare = { compared = it }) }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Comparar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Eliminar").assertExists()
        compose.onNodeWithContentDescription("Comparar").performClick()
        assertEquals(listOf(photos[0].displayName, photos[1].displayName), compared)
    }

    /**
     * Expresiones (arrugas dinámicas): Comparar empieza con las fotos en reposo, ofrece filtrar por
     * expresión y avisa si se comparan expresiones distintas.
     */
    @Test
    fun compareFiltersByExpressionAndWarnsWhenMixed() {
        val jpeg = File(tmp.root, "e.jpg").apply { writeBytes(this@UsageFlowsTest.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        repo.savePhoto(exp, Zone.LIPS, jpeg, takenAt = 1_763_400_000_000L + 200 * day,
            variant = com.photomed.app.data.FacialExpression.variantTag(com.photomed.app.data.FacialExpression.SMILE))
        setContent { CompareScreen(exp.id, Zone.LIPS, onBack = {}) }
        waitText("Todas las expresiones")
        compose.onNodeWithText("Sonrisa máxima").assertExists()
        // selección inicial en reposo: sin aviso
        assertTrue(compose.onAllNodesWithText("compara expresiones distintas", substring = true).fetchSemanticsNodes().isEmpty())
        // con todas las expresiones, añadir la de la sonrisa → aviso
        compose.onNodeWithText("Todas las expresiones").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Labios", substring = true) and hasClickLabel())[0].performClick()
        compose.waitForIdle()
        waitText("compara expresiones distintas", substring = true)
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
        compose.onNodeWithText("Ajustes de comparación").performClick()
        waitText("Usar mirada (gaze)")
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
            compose.onNode(androidx.compose.ui.test.hasText("Ajustes de comparación") and androidx.compose.ui.test.hasClickAction()).performClick()
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

    @Before
    fun noHelpers() { com.photomed.app.ui.components.CoachMarks.enabled = false; com.photomed.app.ui.compare.CompareIdle.enabled = false }

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
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Comparar 3 fotos").fetchSemanticsNodes().isNotEmpty() }
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
        // lado a lado (una fila) y GIF (desplazamiento), con recorte común según la alineación
        val crop = com.photomed.app.ui.compare.overlapCrop(listOf(com.photomed.app.ui.compare.footprint(shift.transform, 0f)))
        val r2 = com.photomed.app.ui.compare.CompareExport.build(ctx, repo, photos, mapOf(photos[1].key to shift), emptyMap(),
            individual = false, grid = false, anonymize = true, dir = File(tmp.root, "out2"), side = true,
            gif = com.photomed.app.ui.compare.CompareExport.GifMode.SLIDE, crop = crop, order = listOf(2, 0, 1))
        assertEquals(2, r2.files.size)
        val side = android.graphics.BitmapFactory.decodeFile(r2.files[0].absolutePath)
        assertTrue("lado a lado: 3 en una fila", side.width > side.height * 2)
        val gif = r2.files[1]
        assertTrue(gif.name.endsWith(".gif"))
        val head = gif.readBytes().copyOfRange(0, 6).toString(Charsets.US_ASCII)
        assertEquals("GIF89a", head)
        println("GIF exportado: %.1f MB".format(gif.length() / 1e6))
        // vista girada 90° y con capas: cuadrícula apaisada y las líneas dibujadas
        val line = com.photomed.app.align.AnnotLine(com.photomed.app.align.AnnotLayer.LIPS,
            listOf(com.photomed.app.align.Pt(0.2f, 0.6f), com.photomed.app.align.Pt(0.8f, 0.6f)), false)
        val r3 = com.photomed.app.ui.compare.CompareExport.build(ctx, repo, photos.take(2), emptyMap(), emptyMap(),
            individual = true, grid = false, anonymize = true, dir = File(tmp.root, "out3"),
            annots = mapOf(photos[0].key to listOf(line)), layers = setOf(com.photomed.app.align.AnnotLayer.LIPS),
            orient = com.photomed.app.align.ImageEdit(rot = 1))
        val rotated = android.graphics.BitmapFactory.decodeFile(r3.files[0].absolutePath)
        assertTrue("girada: apaisada", rotated.width > rotated.height)
        // tras girar 90° en sentido horario, la línea horizontal y = 0,6·ancho queda vertical
        val x = (rotated.width - 1 - 0.6f * 1080 * 1f).toInt().coerceIn(0, rotated.width - 1)
        val hit = (0 until rotated.height).any { y -> val c = rotated.getPixel(x, y); android.graphics.Color.red(c) > 200 && android.graphics.Color.blue(c) > 150 && android.graphics.Color.green(c) < 170 }
        assertTrue("línea de labios dibujada", hit)
        File("build/reports").mkdirs(); gif.copyTo(File("build/reports/export_demo.gif"), overwrite = true)
        r2.files[0].copyTo(File("build/reports/export_side.jpg"), overwrite = true)
    }
}
