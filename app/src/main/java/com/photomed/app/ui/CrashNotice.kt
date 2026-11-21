package com.photomed.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.photomed.app.CrashLog

/** Aviso al abrir si la app se cerró por un error: se puede compartir el registro. */
@Composable
fun CrashNotice() {
    val context = LocalContext.current
    var files by remember { mutableStateOf(runCatching { CrashLog.unseen(context) }.getOrDefault(emptyList())) }
    if (files.isEmpty()) return
    AlertDialog(
        onDismissRequest = { CrashLog.markSeen(context); files = emptyList() },
        title = { Text("La app se cerró por un error") },
        text = { Text("Se guardó un registro del error (${files.size}). Compartirlo ayuda a corregirlo; no incluye fotos ni datos del paciente. " +
            "También está en Ajustes → Registro de errores.") },
        confirmButton = { TextButton(onClick = { CrashLog.share(context, files); CrashLog.markSeen(context); files = emptyList() }) { Text("Compartir") } },
        dismissButton = { TextButton(onClick = { CrashLog.markSeen(context); files = emptyList() }) { Text("Cerrar") } },
    )
}
