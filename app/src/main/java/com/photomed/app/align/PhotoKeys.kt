package com.photomed.app.align

import android.content.Context
import java.io.File

/**
 * Datos de la app guardados por URI de la foto (edición, correcciones, trazos confirmados, memoria de
 * líneas, relieve, anotaciones). Si la URI cambia (p. ej. al cambiar la fecha de una foto guardada en
 * archivo), se mueven a la nueva.
 */
object PhotoKeys {
    fun move(context: Context, from: String, to: String) {
        if (from == to) return
        // preferencias por URI
        for (prefs in listOf("photo_edits", "annot_user")) {
            val p = context.getSharedPreferences(prefs, Context.MODE_PRIVATE)
            p.getString(from, null)?.let { v -> p.edit().remove(from).putString(to, v).commit() }
        }
        PhotoEdits.clearCache()
        // archivos con la huella de la URI en el nombre
        fun sha(u: String) = java.security.MessageDigest.getInstance("SHA-1").digest(u.toByteArray()).joinToString("") { "%02x".format(it) }
        for (dir in listOf("trazos", "lineas", "relieve", "anotaciones")) {
            File(context.filesDir, dir).listFiles { f -> f.name.startsWith(sha(from)) }?.forEach { f ->
                f.renameTo(File(f.parentFile, sha(to) + f.name.removePrefix(sha(from))))
            }
        }
        ConfirmedTraces.forget(context, from)
    }
}
