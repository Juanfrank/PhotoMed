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

    // --- migas: últimas acciones (para entender un cierre sin traza de Java) ---
    private val crumbs = ArrayDeque<String>()
    @Volatile private var crumbFile: File? = null

    /** Anota una acción (p. ej. «análisis de una foto»); se guardan las 40 últimas en disco. */
    fun crumb(text: String) {
        val f = crumbFile ?: return
        val line = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()) + " " + text
        synchronized(crumbs) {
            crumbs.addLast(line); while (crumbs.size > 40) crumbs.removeFirst()
            runCatching { f.writeText(crumbs.joinToString("\n")) }
        }
    }

    private fun previousCrumbs(): String = dir?.let { File(it, "migas_prev.txt").takeIf { f -> f.exists() }?.readText() }.orEmpty()
    private fun currentCrumbs(): String = synchronized(crumbs) { crumbs.joinToString("\n") }

    /** Instala el manejador de errores no capturados (una vez; conserva el del sistema). */
    @Volatile private var installed = false

    fun install(context: Context) {
        val d = dir(context)
        if (installed && dir == d) return
        dir = d
        // migas de la sesión anterior (por si terminó sin traza) y nuevas para esta
        runCatching { File(dir, "migas.txt").takeIf { it.exists() }?.renameTo(File(dir, "migas_prev.txt")) }
        crumbFile = File(dir, "migas.txt")
        version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        if (installed) return
        installed = true
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
            currentCrumbs().takeIf { it.isNotEmpty() }?.let { appendLine(); appendLine("Últimas acciones:"); appendLine(it) }
        }
        val f = File(d, (if (fatal) "cierre_" else "error_") + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now) + ".txt")
        f.writeText(text)
        d.listFiles()?.sortedByDescending { it.name.substringAfter('_') }?.drop(MAX)?.forEach { it.delete() }
        return f
    }

    /**
     * Cierres que no pasaron por Java (fallo en código nativo, falta de memoria, bloqueo), según Android
     * (API 30+): se registran al volver a abrir la app, con el motivo y las últimas acciones.
     */
    fun checkExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val prefs = context.getSharedPreferences("photomed", Context.MODE_PRIVATE)
            val since = prefs.getLong("exit_checked", 0L)
            val am = context.getSystemService(android.app.ActivityManager::class.java) ?: return
            val infos = am.getHistoricalProcessExitReasons(context.packageName, 0, 5).filter { it.timestamp > since }
            prefs.edit().putLong("exit_checked", System.currentTimeMillis()).apply()
            if (since == 0L) return   // primera vez: no se conocen las anteriores a esta versión
            for (i in infos) {
                val name = when (i.reason) {
                    android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "fallo en código nativo (modelos de visión, cámara…)"
                    android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "el sistema cerró la app por falta de memoria"
                    android.app.ApplicationExitInfo.REASON_ANR -> "la app dejó de responder (ANR)"
                    android.app.ApplicationExitInfo.REASON_SIGNALED -> "cierre por señal del sistema"
                    android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excesivo de recursos"
                    android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "fallo al iniciar"
                    android.app.ApplicationExitInfo.REASON_OTHER -> "otro motivo del sistema"
                    else -> null
                } ?: continue
                // la memoria en segundo plano es normal: solo si la app estaba a la vista
                if (i.reason == android.app.ApplicationExitInfo.REASON_LOW_MEMORY && i.importance > android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) continue
                val trace = runCatching { i.traceInputStream?.use { it.readBytes() } }.getOrNull()?.let { bytes ->
                    // texto legible del volcado (ANR: texto; nativo: mensajes y bibliotecas)
                    String(bytes, Charsets.ISO_8859_1).split(Regex("[^\\x20-\\x7E\\n]+")).filter { it.length >= 6 }.joinToString("\n").take(6000)
                }
                val d = dir ?: dir(context)
                val now = Date(i.timestamp)
                val text = buildString {
                    appendLine("Cierre sin traza de Java: $name")
                    appendLine("Fecha: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now))
                    appendLine("PhotoMed $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Tipo: cierre · motivo ${i.reason} · estado ${i.status} · importancia ${i.importance} · memoria PSS ${i.pss / 1024} MB, RSS ${i.rss / 1024} MB")
                    i.description?.let { appendLine("Descripción: $it") }
                    previousCrumbs().takeIf { it.isNotEmpty() }?.let { appendLine(); appendLine("Últimas acciones:"); appendLine(it) }
                    trace?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("Volcado:"); appendLine(it) }
                }
                File(d, "cierre_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now) + ".txt").writeText(text)
            }
        }.onFailure { write("No se pudo leer el motivo del último cierre", it) }
    }

    fun all(context: Context): List<File> = dir(context).listFiles()?.filter { it.name.endsWith(".txt") && !it.name.startsWith("migas") }
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
