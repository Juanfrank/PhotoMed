package com.photomed.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.photomed.app.data.PhotoRepository
import com.photomed.app.i18n.I18n
import com.photomed.app.i18n.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalRepository = staticCompositionLocalOf<PhotoRepository> { error("Repository not provided") }

private val spanish = Locale("es")

/**
 * Formato de fecha en el idioma de la interfaz: el patrón se traduce como un texto más; sin
 * traducción (interfaz en español), con el español de siempre.
 */
private fun dateFormat(pattern: String, translated: String): SimpleDateFormat =
    SimpleDateFormat(translated, if (translated == pattern) spanish else I18n.locale)

fun formatDateTime(millis: Long): String =
    dateFormat("d MMM yyyy, HH:mm", tr("d MMM yyyy, HH:mm")).format(Date(millis))

fun formatDate(millis: Long): String =
    dateFormat("d MMM yyyy", tr("d MMM yyyy")).format(Date(millis))

fun formatDayHeader(millis: Long): String =
    dateFormat("EEEE d 'de' MMMM 'de' yyyy", tr("EEEE d 'de' MMMM 'de' yyyy")).format(Date(millis)).replaceFirstChar { it.uppercase() }

fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "#" }
