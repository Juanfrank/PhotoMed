package com.photomed.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.ImportItem
import com.photomed.app.data.PhotoImport
import com.photomed.app.data.PhotoMetadata
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Zone
import com.photomed.app.ui.compare.OrientBox
import com.photomed.app.ui.expediente.ImportPhotosDialog
import com.photomed.app.ui.theme.PhotoMedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w600dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoImportTest {
    @get:Rule(order = 0) val tmp = TemporaryFolder()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun suggestsFaceAngleFromHeadTurn() {
        assertEquals(Zone.FACE_FRONTAL, PhotoImport.suggest(4f, -0.02f))
        assertEquals(Zone.FACE_OBLIQUE_R, PhotoImport.suggest(-42f, 0.3f))
        assertEquals(Zone.FACE_OBLIQUE_L, PhotoImport.suggest(40f, -0.3f))
        assertEquals(Zone.FACE_PROFILE_L, PhotoImport.suggest(80f, -0.6f))
        assertEquals(null, PhotoImport.suggest(null, null))
    }

    @Test fun readsExifDateAndImportsWithZones() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val jpeg = File(tmp.root, "src.jpg").apply { writeBytes(this@PhotoImportTest.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        ExifInterface(jpeg).apply { setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2025:03:14 10:20:30"); saveAttributes() }
        val t = jpeg.inputStream().use { PhotoImport.exifDate(it) }!!
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = t }
        assertEquals(2025, cal.get(java.util.Calendar.YEAR)); assertEquals(20, cal.get(java.util.Calendar.MINUTE))

        val repo = PhotoRepository(File(tmp.root, "private"), FilePhotoStore(File(tmp.root, "Pictures/PhotoMed")),
            openStream = { uri -> File(uri.path!!).inputStream() })
        val exp = repo.createExpediente("Paciente Importado", "77", "", consent = true)
        val bmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF997766.toInt()) }
        val items = listOf(
            ImportItem(Uri.parse("content://a/1"), t, true, Zone.FACE_FRONTAL),
            ImportItem(Uri.parse("content://a/2"), t, true, Zone.FACE_OBLIQUE_R),   // misma fecha: nombre distinto
            ImportItem(Uri.parse("content://a/3"), t, true, null),                   // sin ángulo: se omite
        )
        val saved = PhotoImport.import(ctx, repo, exp, items, decode = { bmp })
        assertEquals(2, saved.size)
        assertEquals(setOf(Zone.FACE_FRONTAL, Zone.FACE_OBLIQUE_R), saved.map { it.zone }.toSet())
        assertTrue(saved[0].displayName != saved[1].displayName)
        val photos = repo.photos(exp)
        assertEquals(2, photos.size)
        val info = repo.readInfo(photos.first { it.zone == Zone.FACE_FRONTAL })
        assertNotNull(info)
        assertEquals("Paciente Importado", info!!.patientName)
        assertEquals("importada", info.conditions["Source"])
        assertEquals("EXIF", info.conditions["DateSource"])
        assertEquals(t, photos.first { it.zone == Zone.FACE_FRONTAL }.takenAt)
    }

    @Test fun dialogNeedsAnAngleForEveryPhoto() {
        var result: List<ImportItem>? = null
        val items = listOf(ImportItem(Uri.parse("content://a/1"), 1_700_000_000_000L, true, Zone.FACE_FRONTAL, suggested = true),
            ImportItem(Uri.parse("content://a/2"), 1_700_000_000_000L, false, null))
        compose.setContent { PhotoMedTheme(darkTheme = false) { ImportPhotosDialog(items, {}) { result = it } } }
        compose.onNodeWithText("Importar 2 fotos").assertExists()
        compose.onNodeWithText("Importar al expediente").assertIsNotEnabled()
        compose.onNodeWithText("Elija el ángulo de 1 foto.").assertExists()
        // mismo ángulo para todas
        compose.onNodeWithText("Elegir").performClick()
        compose.onNodeWithText("Labios / peribucal").performClick()
        compose.onNodeWithText("Importar al expediente").assertIsEnabled().performClick()
        assertEquals(listOf(Zone.LIPS, Zone.LIPS), result!!.map { it.zone })
    }

    /**
     * El botón de importar no puede quedar bajo los botones de navegación del teléfono: con una barra
     * de navegación (insets) en la ventana del diálogo, el botón queda entero por encima de ella.
     */
    @Test fun importButtonStaysAboveSystemNavigationBar() {
        val items = listOf(ImportItem(Uri.parse("content://a/1"), 1_700_000_000_000L, true, Zone.FACE_FRONTAL))
        compose.setContent { PhotoMedTheme(darkTheme = false) { ImportPhotosDialog(items, {}) {} } }
        compose.waitForIdle()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!
        val decor = dialog.window!!.decorView
        val navPx = 150; val statusPx = 80
        val insets = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, navPx))
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars(), androidx.core.graphics.Insets.of(0, statusPx, 0, 0))
            .build()
        compose.runOnUiThread { androidx.core.view.ViewCompat.dispatchApplyWindowInsets(decor, insets) }
        compose.waitForIdle()
        val button = compose.onNodeWithText("Importar al expediente").fetchSemanticsNode().boundsInWindow
        val windowH = decor.height
        println("BOTÓN: ${button.top}–${button.bottom} px; ventana $windowH px; barra de navegación desde ${windowH - navPx} px")
        assertTrue("ventana del diálogo sin tamaño", windowH > 0)
        assertTrue("el botón (abajo en ${button.bottom}) queda bajo la barra de navegación (desde ${windowH - navPx})",
            button.bottom <= windowH - navPx)
        // y el título, bajo la barra de estado
        val title = compose.onNodeWithText("Importar 1 foto").fetchSemanticsNode().boundsInWindow
        assertTrue("título bajo la barra de estado", title.top >= statusPx)
    }

    /** Guía girada 90°: "fit" la deja entera dentro del 3:4; "fill34" cubre el 3:4 como la referencia girada. */
    @Test fun rotatedGuideKeepsItsProportions() {
        compose.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                OrientBox(1, false, Modifier.fillMaxSize(), fit = true) { Box(Modifier.fillMaxSize().testTag("fit")) }
                OrientBox(1, false, Modifier.fillMaxSize(), fill34 = true) { Box(Modifier.fillMaxSize().testTag("fill")) }
            }
        }
        // contenido 3:4 que, girado, ocupa 300 × 225 (cabe en 300 × 400)
        compose.onNodeWithTag("fit").assertWidthIsEqualTo(225.dp).assertHeightIsEqualTo(300.dp)
        // contenido 3:4 que, girado, ocupa 533 × 400: alto completo y recorte a los lados (como centerCrop34)
        compose.onNodeWithTag("fill").assertWidthIsEqualTo(400.dp).assertHeightIsEqualTo(533.3333.dp)
    }
}
