package com.photomed.app.ui.components

import com.photomed.app.i18n.tr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.TimeZone

/** Fecha y hora (dos pasos: calendario y reloj), en la hora local. [initial] y el resultado en ms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit, title: String = tr("Fecha de captura")) {
    val local = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    // el calendario trabaja en UTC: el mismo día a medianoche UTC
    val utcDay = remember(initial) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    var day by remember { mutableStateOf<Long?>(null) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = utcDay, selectableDates = object : androidx.compose.material3.SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
    })
    val timeState = rememberTimePickerState(local.get(Calendar.HOUR_OF_DAY), local.get(Calendar.MINUTE), is24Hour = true)
    val d = day
    if (d == null) {
        DatePickerDialog(onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { day = dateState.selectedDateMillis ?: utcDay }) { Text(tr("Siguiente")) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } }) {
            DatePicker(dateState, title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Hora de captura")) },
            text = { TimePicker(timeState) },
            confirmButton = { TextButton(onClick = {
                val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = d }
                val out = Calendar.getInstance().apply {
                    clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute,
                        local.get(Calendar.SECOND))
                    set(Calendar.MILLISECOND, local.get(Calendar.MILLISECOND))
                }.timeInMillis
                onPick(out)
            }) { Text(tr("Guardar")) } },
            dismissButton = { TextButton(onClick = { day = null }) { Text(tr("Atrás")) } })
    }
}

