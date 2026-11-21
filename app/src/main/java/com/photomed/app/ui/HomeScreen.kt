package com.photomed.app.ui

import com.photomed.app.i18n.tr
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.photomed.app.data.Protocol

/** Inicio: dos pestañas, expedientes (pacientes) y protocolos (reutilizables con cualquier paciente). */
@Composable
fun HomeScreen(onOpen: (String) -> Unit, onAbout: () -> Unit, onSettings: () -> Unit,
               onEditProtocol: (String) -> Unit, onApplyProtocol: (String, Protocol) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = conf.screenWidthDp > conf.screenHeightDp
    @Composable fun content() {
        if (tab == 0) com.photomed.app.ui.expedientes.ExpedientesScreen(onOpen = onOpen, onAbout = onAbout, onSettings = onSettings)
        else com.photomed.app.ui.protocol.ProtocolLibraryScreen(onEdit = onEditProtocol, onApply = onApplyProtocol)
    }
    if (wide) {
        // horizontal: menú lateral (la barra inferior quitaba altura a la lista)
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            androidx.compose.material3.NavigationRail(Modifier.fillMaxHeight()) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                androidx.compose.material3.NavigationRailItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text(tr("Expedientes")) })
                androidx.compose.material3.NavigationRailItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.PlaylistAddCheck, null) }, label = { Text(tr("Protocolos")) })
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
        return
    }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text(tr("Expedientes")) })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.PlaylistAddCheck, null) }, label = { Text(tr("Protocolos")) })
        }
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()).consumeWindowInsets(pad)) { content() }
    }
}
