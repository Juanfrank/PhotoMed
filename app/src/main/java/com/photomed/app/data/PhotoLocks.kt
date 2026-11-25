package com.photomed.app.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Exclusión por foto para las lecturas-modificaciones-escrituras de sus metadatos (XMP, fecha de captura,
 * trazos confirmados, anotaciones). Antes, dos toques seguidos (o el visor y Comparar a la vez) leían el
 * mismo estado y el último en guardar borraba el cambio del otro, o reescribían el JPEG a la vez.
 * Reentrante (synchronized): una sección puede llamar a otra de la misma foto.
 */
object PhotoLocks {
    private val locks = ConcurrentHashMap<String, Any>()

    fun lockOf(uri: String): Any = locks.computeIfAbsent(uri) { Any() }

    inline fun <T> on(uri: String, block: () -> T): T = synchronized(lockOf(uri)) { block() }
}
