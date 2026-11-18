package com.photomed.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.ColumnScope

/**
 * Pantalla completa dentro de la ventana principal (no un Dialog): recibe los insets reales del
 * sistema, de modo que barras superiores e inferiores (Scaffold) nunca quedan bajo la barra de
 * estado o de navegación. Atrás la cierra.
 */
@Composable
fun FullScreenOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) { content() } }
}

/** Altura de la barra de navegación medida en la ventana principal. */
@Composable
fun navigationBarHeight(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Hoja inferior que reserva explícitamente la barra de navegación (medida en la ventana
 * principal), para que su contenido no quede debajo de los botones del sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val nav = navigationBarHeight()
    ModalBottomSheet(onDismissRequest = onDismiss, contentWindowInsets = { WindowInsets(0) }) {
        content()
        Spacer(Modifier.height(nav))
    }
}
