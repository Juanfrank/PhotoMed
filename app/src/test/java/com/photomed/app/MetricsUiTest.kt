package com.photomed.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.Pt
import com.photomed.app.ui.compare.MetricsCard
import com.photomed.app.ui.compare.MetricsSheet
import com.photomed.app.ui.theme.PhotoMedTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w600dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MetricsUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun line(name: String, len: Float, depth: Float, grade: Int) = AnnotLine(AnnotLayer.FOLDS, listOf(Pt(0.1f, 0.1f), Pt(0.2f, 0.2f)), false,
        metrics = LineMetrics(name, lengthMm = len, widthMm = 3f, contrastL = 15f, depthMm = depth, grade = grade))
    private val ojera = AnnotLine(AnnotLayer.DARK_CIRCLES, listOf(Pt(0f, 0f), Pt(0.1f, 0f), Pt(0.1f, 0.1f)), true, filled = true,
        metrics = LineMetrics("Ojera derecha", areaMm2 = 420f, deltaL = 9f, deltaE = 11f, ita = 20f, itaRef = 40f, type = "vascular (azulado o violáceo)"))

    @Test fun tappedLineCardOpensTable() {
        var table = false
        compose.setContent { PhotoMedTheme(darkTheme = false) {
            MetricsCard(line("Nasogeniano derecho", 31.2f, 1.25f, 4), onTable = { table = true }, onClose = {})
        } }
        compose.onNodeWithText("Nasogeniano derecho").assertIsDisplayed()
        compose.onNodeWithText("31.2 mm", substring = true).assertIsDisplayed()
        compose.onNodeWithText("4 · profunda").assertIsDisplayed()
        compose.onNodeWithText("Tabla de medidas").performClick()
        assertTrue(table)
    }

    @Test fun compareTableShowsEachPhotoAndDifference() {
        compose.setContent { PhotoMedTheme(darkTheme = false) {
            MetricsSheet(listOf(
                "1 ene 2026" to listOf(line("Nasogeniano derecho", 32f, 1.40f, 4), ojera),
                "1 abr 2026" to listOf(line("Nasogeniano derecho", 30f, 0.90f, 3)),
            ), setOf(AnnotLayer.FOLDS, AnnotLayer.DARK_CIRCLES), onDismiss = {})
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Medidas de líneas y surcos").assertIsDisplayed()
        // número de la línea (el mismo que en la foto) + nombre + medida
        compose.onNodeWithText("1  Nasogeniano derecho\nLong. mm").assertExists()
        compose.onNodeWithText("-2.0").assertExists()      // longitud
        compose.onNodeWithText("-0.50").assertExists()     // profundidad
        assertTrue(compose.onAllNodesWithText("Ojera derecha", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("vascular", substring = true).assertExists()
    }
}
