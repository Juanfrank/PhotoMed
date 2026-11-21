package com.photomed.app

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de errores: ningún cierre es silencioso. Cada error no capturado (y los de las tareas en
 * segundo plano) se guarda en `files/errores/` con la versión, el dispositivo y la traza; al volver a
 * abrir la app se avisa y se puede compartir. Se conservan los 20 más recientes.
 */
object CrashLog {
    private const val DIR = "errores"
    private const val MAX = 20
    @Volatile private var dir: File? = null
    private var version = ""

    fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    /** Instala el manejador de errores no capturados (una vez; conserva el del sistema). */
    fun install(context: Context) {
        if (dir != null) return
        dir = dir(context)
        version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write("Cierre de la app (hilo ${t.name})", e, fatal = true) }
            previous?.uncaughtException(t, e)
        }
    }

    /** Guarda un error (con [fatal] = false, uno capturado que no cerró la app). */
    fun write(title: String, e: Throwable, fatal: Boolean = false): File? {
        val d = dir ?: return null
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val now = Date()
        val text = buildString {
            appendLine(title)
            appendLine("Fecha: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now))
            appendLine("PhotoMed $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(if (fatal) "Tipo: cierre" else "Tipo: error en segundo plano (la app siguió abierta)")
            appendLine(); append(sw.toString())
        }
        val f = File(d, (if (fatal) "cierre_" else "error_") + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now) + ".txt")
        f.writeText(text)
        d.listFiles()?.sortedByDescending { it.name.substringAfter('_') }?.drop(MAX)?.forEach { it.delete() }
        return f
    }

    fun all(context: Context): List<File> = dir(context).listFiles()?.filter { it.name.endsWith(".txt") }
        ?.sortedByDescending { it.name.substringAfter('_') }.orEmpty()

    /** Cierres aún no vistos por el usuario (para avisar al abrir). */
    fun unseen(context: Context): List<File> {
        val seen = context.getSharedPreferences("photomed", Context.MODE_PRIVATE).getLong("crash_seen", 0L)
        return all(context).filter { it.name.startsWith("cierre_") && it.lastModified() > seen }
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences("photomed", Context.MODE_PRIVATE).edit().putLong("crash_seen", System.currentTimeMillis()).apply()
    }

    fun clear(context: Context) { all(context).forEach { it.delete() } }

    /** Compartir los registros como texto (correo, mensajería…). Sin datos del paciente: solo trazas. */
    fun share(context: Context, files: List<File> = all(context)) {
        if (files.isEmpty()) return
        val text = files.take(5).joinToString("\n\n──────────\n\n") { it.readText() }
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "PhotoMed: registro de errores")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(i, "Compartir el registro de errores").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
