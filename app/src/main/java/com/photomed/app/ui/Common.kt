package com.photomed.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.photomed.app.data.PhotoRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalRepository = staticCompositionLocalOf<PhotoRepository> { error("Repository not provided") }

private val spanish = Locale("es")

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", spanish).format(Date(millis))

fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", spanish).format(Date(millis))

fun formatDayHeader(millis: Long): String =
    SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", spanish).format(Date(millis)).replaceFirstChar { it.uppercase() }

fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "#" }
