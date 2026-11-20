package com.photomed.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.Pt
import com.photomed.app.ui.compare.MetricsPanel
import com.photomed.app.ui.gallery.screenToPhoto
import com.photomed.app.ui.theme.PhotoMedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Zona de «Trazar surco» con zoom y botón de eliminar visible en un teléfono estrecho. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TraceAndDeleteTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** La conversión pantalla → foto es la inversa exacta del dibujo con zoom y desplazamiento. */
    @Test fun screenToPhotoUndoesZoomAndPan() {
        val sw = 1080f; val sh = 1700f; val iw = 3000; val ih = 4000
        for ((scale, ox, oy) in listOf(Triple(1f, 0f, 0f), Triple(2.5f, -300f, 420f), Triple(4f, 900f, -1200f))) {
            val p = Pt(0.37f, 0.81f)
            // dibujo: foto ajustada al área, luego zoom alrededor del centro y desplazamiento
            val w = minOf(sw, sh * iw / ih.toFloat()); val h = w * ih / iw
            val cx = (sw - w) / 2 + p.x * w; val cy = (sh - h) / 2 + p.y * w
            val sx = sw / 2 + (cx - sw / 2) * scale + ox; val sy = sh / 2 + (cy - sh / 2) * scale + oy
            val q = screenToPhoto(sx, sy, sw, sh, iw, ih, scale, ox, oy)
            assertEquals(p.x, q.x, 1e-4f); assertEquals(p.y, q.y, 1e-4f)
            // sin deshacer el zoom (como antes) el punto sale en otro sitio
            if (scale > 1f) assertTrue(kotlin.math.abs(screenToPhoto(sx, sy, sw, sh, iw, ih, 1f, 0f, 0f).x - p.x) > 0.05f)
        }
    }

    /** En un teléfono de 360 dp la papelera de cada surco se ve (columna fija a la derecha) y elimina. */
    @Test fun deleteButtonVisibleOnNarrowPhone() {
        var deleted: String? = null
        val line = AnnotLine(AnnotLayer.FOLDS, listOf(Pt(0.1f, 0.1f), Pt(0.2f, 0.2f)), false,
            metrics = LineMetrics("Nasogeniano derecho", lengthMm = 31f, widthMm = 3f, contrastL = 18f, depthMm = 1.2f, grade = 4))
        compose.setContent { PhotoMedTheme(darkTheme = false) {
            MetricsPanel(listOf("20 nov 2026" to listOf(line)), setOf(AnnotLayer.FOLDS), onClose = {}, onDelete = { n, _ -> deleted = n })
        } }
        val btn = compose.onNodeWithContentDescription("Eliminar Nasogeniano derecho (falso positivo)")
        btn.assertIsDisplayed()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val b = btn.fetchSemanticsNode().boundsInRoot
        assertTrue("fuera de la pantalla: ${b.right} > ${root.right}", b.right <= root.right + 0.5f)
        btn.performClick()
        assertEquals("Nasogeniano derecho", deleted)
    }
}
