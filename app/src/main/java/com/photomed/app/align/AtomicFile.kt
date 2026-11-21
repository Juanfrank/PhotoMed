package com.photomed.app.align

import java.io.File

/**
 * Escritura atómica de un archivo pequeño: se escribe aparte y se renombra. Así una lectura simultánea
 * (otra pantalla, otro hilo) nunca ve el archivo vacío o a medias.
 */
object AtomicFile {
    fun write(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp-" + Thread.currentThread().id)
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }
}
