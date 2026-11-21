package com.photomed.app

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Al instalar una versión nueva se borran las cachés de la anterior (imágenes de Coil, temporales,
 * memoria de líneas y análisis guardados): sus formatos pueden haber cambiado y provocar cierres. Se
 * conservan los datos del usuario: expedientes, fotos, ediciones, correcciones, trazos confirmados,
 * relieves y preferencias.
 */
object AppUpgrade {
    private const val PREFS = "app_upgrade"
    private const val KEY = "version_code"

    /** Carpetas derivadas (se regeneran) dentro de los archivos de la app. */
    val DERIVED_DIRS = listOf("lineas")

    fun versionCode(context: Context): Long = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0L)

    /** Borra las cachés si la versión instalada no es la de la última ejecución. Devuelve si borró. */
    fun onStart(context: Context, current: Long = versionCode(context)): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY, -1L)
        if (last == current) return false
        clearCaches(context)
        prefs.edit().putLong(KEY, current).commit()
        return true
    }

    fun clearCaches(context: Context) {
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        runCatching { context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() } }
        DERIVED_DIRS.forEach { runCatching { File(context.filesDir, it).deleteRecursively() } }
        runCatching { com.photomed.app.align.PhotoAnnotator.clearCache() }
    }
}
