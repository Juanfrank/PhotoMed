package com.photomed.app.align

import java.io.File

/**
 * Escritura atómica de un archivo pequeño: se escribe aparte y se renombra. Así una lectura simultánea
 * (otra pantalla, otro hilo) nunca ve el archivo vacío o a medias.
 */
object AtomicFile {
    /**
     * Se escribe en un temporal, se fuerza a disco (fsync) y se renombra sobre el destino. Si el renombrado
     * falla se lanza la excepción: antes se escribía directamente sobre el destino (no atómico) y un corte a
     * medias dejaba el archivo dañado.
     */
    fun write(f: File, text: String) = writeBytes(f, text.toByteArray())

    fun writeBytes(f: File, bytes: ByteArray) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp-" + Thread.currentThread().id)
        try {
            java.io.FileOutputStream(tmp).use { out -> out.write(bytes); out.flush(); runCatching { out.fd.sync() } }
            if (!tmp.renameTo(f)) {
                // algunos sistemas de archivos no renombran sobre un destino existente
                if (!(f.delete() || !f.exists()) || !tmp.renameTo(f)) throw java.io.IOException("No se pudo reemplazar ${f.name}")
            }
        } finally { if (tmp.exists()) tmp.delete() }
    }
}
