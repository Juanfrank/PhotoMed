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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
fun SafeBottomSheet(onDismiss: () -> Unit, expanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val nav = navigationBarHeight()
    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = expanded)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, contentWindowInsets = { WindowInsets(0) }) {
        content()
        Spacer(Modifier.height(nav))
    }
}

/**
 * Título de las pantallas principales: logo y nombre de la aplicación, y debajo la sección
 * (Expedientes, Protocolos). Va en el `title` de una TopAppBar.
 */
@Composable
fun AppSectionTitle(section: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = androidx.compose.ui.Modifier.semantics(mergeDescendants = true) {}) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.photomed.app.R.drawable.logo_app), "Logo de PhotoMed",
            Modifier.size(36.dp))
        androidx.compose.foundation.layout.Column(Modifier.padding(start = 10.dp)) {
            androidx.compose.material3.Text("PhotoMed", style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary, maxLines = 1)
            androidx.compose.material3.Text(section, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    }
}
