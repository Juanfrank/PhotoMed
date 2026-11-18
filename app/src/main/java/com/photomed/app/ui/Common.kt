package com.photomed.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.photomed.app.data.PhotoRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalRepository = staticCompositionLocalOf<PhotoRepository> { error("Repository not provided") }

private val spanish = Locale("es")

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", spanish).format(Date(millis))

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", spanish).format(Date(millis))
