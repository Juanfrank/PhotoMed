package com.photomed.app.ui.expedientes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.photomed.app.ui.components.FullScreenOverlay
import com.photomed.app.ui.theme.Md3Spacing

/**
 * Formulario de expediente como diálogo a pantalla completa (patrón M3 para formularios en
 * pantallas compactas): cerrar a la izquierda, acción de confirmar en la barra superior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpedienteForm(
    title: String,
    initialName: String = "",
    initialRecord: String = "",
    initialNotes: String = "",
    initialConsent: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, record: String, notes: String, consent: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var record by rememberSaveable { mutableStateOf(initialRecord) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var consent by rememberSaveable { mutableStateOf(initialConsent) }
    var triedSave by rememberSaveable { mutableStateOf(false) }
    val nameError = triedSave && name.isBlank()

    FullScreenOverlay(onDismiss = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") } },
                    actions = {
                        TextButton(onClick = {
                            triedSave = true
                            if (name.isNotBlank()) onConfirm(name, record, notes, consent)
                        }) { Text("Guardar") }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = Md3Spacing.marginCompact, vertical = Md3Spacing.s2),
                verticalArrangement = Arrangement.spacedBy(Md3Spacing.s4),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre del paciente *") },
                    isError = nameError,
                    supportingText = { Text(if (nameError) "El nombre es obligatorio" else "Nombre y apellidos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = record, onValueChange = { record = it },
                    label = { Text("Número de expediente") },
                    supportingText = { Text("Se usa, junto con el nombre, para nombrar el álbum") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notas / tratamiento") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.toggleable(value = consent, role = Role.Switch, onValueChange = { consent = it })
                            .padding(Md3Spacing.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = Md3Spacing.s4)) {
                            Text("Consentimiento informado", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "El paciente autorizó por escrito la toma y el uso clínico de fotografías.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = consent, onCheckedChange = null)
                    }
                }
                Text(
                    "Las fotografías se guardan en la galería del teléfono, en el álbum del paciente, e incluyen " +
                        "estos datos como metadatos. Use “Compartir sin datos del paciente” para enviarlas anonimizadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
