package com.photomed.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Expedientes") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.PlaylistAddCheck, null) }, label = { Text("Protocolos") })
        }
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()).consumeWindowInsets(pad)) {
            if (tab == 0) com.photomed.app.ui.expedientes.ExpedientesScreen(onOpen = onOpen, onAbout = onAbout, onSettings = onSettings)
            else com.photomed.app.ui.protocol.ProtocolLibraryScreen(onEdit = onEditProtocol, onApply = onApplyProtocol)
        }
    }
}
