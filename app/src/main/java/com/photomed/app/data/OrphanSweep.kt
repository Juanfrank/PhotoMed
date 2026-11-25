package com.photomed.app.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Datos privados de fotos que ya no existen (borradas desde la galería u otra app): relieve, trazos, memoria
 * de líneas y anotaciones se guardan con la huella de la URI de la foto y antes se acumulaban sin límite.
 * Una vez al día, en segundo plano: los de fotos que ya no aparecen en la galería se apartan a `huerfanos/` y se
 * borran a los [KEEP_DAYS] días (si una consulta de la galería volviera incompleta, nada se pierde de golpe:
 * se recuperan solos si la foto vuelve a aparecer).
 */
object OrphanSweep {
    const val KEEP_DAYS = 30
    private val DIRS = listOf("trazos", "lineas", "relieve", "anotaciones")
    private const val PREF = "orphan_sweep_at"

    private fun sha(u: String) = MessageDigest.getInstance("SHA-1").digest(u.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Devuelve cuántos archivos se apartaron (o -1 si no tocaba o no se pudo consultar la galería). */
    fun runDaily(context: Context, repo: PhotoRepository, now: Long = System.currentTimeMillis()): Int {
        val prefs = context.getSharedPreferences("photomed", Context.MODE_PRIVATE)
        if (now - prefs.getLong(PREF, 0L) < 24 * 3600_000L) return -1
        val n = sweep(context.filesDir, runCatching { repo.photosByExpediente() }.getOrNull() ?: return -1, now)
        prefs.edit().putLong(PREF, now).apply()
        return n
    }

    /** [photos]: las fotos que existen (por expediente). */
    fun sweep(filesDir: File, photos: Map<String, List<Photo>>, now: Long = System.currentTimeMillis()): Int {
        val live = photos.values.flatten().map { sha(it.uri.toString()) }.toHashSet()
        val bin = File(filesDir, "huerfanos")
        // vuelve lo que ya no es huérfano (la foto reapareció)
        for (d in DIRS) File(bin, d).listFiles()?.forEach { f -> if (f.name.take(40) in live) f.renameTo(File(File(filesDir, d).apply { mkdirs() }, f.name)) }
        // sin ninguna foto en la galería pero con expedientes: consulta sospechosa, no se aparta nada
        if (live.isEmpty() && File(filesDir, "expedientes").listFiles()?.isNotEmpty() == true) return 0
        var moved = 0
        for (d in DIRS) File(filesDir, d).listFiles()?.forEach { f ->
            val h = f.name.take(40)
            if (h.length == 40 && h.all { it in "0123456789abcdef" } && h !in live) {
                val dst = File(File(bin, d).apply { mkdirs() }, f.name)
                if (f.renameTo(dst)) { dst.setLastModified(now); moved++ }
            }
        }
        // borrado definitivo de los apartados hace más de KEEP_DAYS días
        val limit = now - KEEP_DAYS * 24 * 3600_000L
        bin.walkBottomUp().forEach { f -> if (f.isFile && f.lastModified() < limit) f.delete() }
        return moved
    }
}
