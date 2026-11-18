package com.photomed.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Ámbito de la aplicación para trabajo que no debe cancelarse al salir de una pantalla
 * (p. ej. guardar en la galería una foto recién tomada aunque el usuario cierre la cámara).
 */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
