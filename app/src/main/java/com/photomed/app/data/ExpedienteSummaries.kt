package com.photomed.app.data

import android.content.Context

/**
 * Resumen de un expediente para la lista: fotos, ángulos (zonas distintas), última toma, última foto
 * de la cara (solo ángulos faciales, nunca otras partes del cuerpo) y protocolos con tomas pendientes.
 */
data class ExpedienteSummary(
    val photos: Int,
    /** Zonas distintas fotografiadas. */
    val angles: Int,
    val lastAt: Long?,
    /** Última foto de un ángulo facial (para la miniatura). */
    val lastFace: Photo?,
    /** Fotos pedidas por los protocolos usados en el expediente que aún faltan. */
    val pendingShots: Int,
    /** Protocolos usados en el expediente (nombre → fotos tomadas / pedidas). */
    val protocols: List<Triple<String, Int, Int>>,
)

object ExpedienteSummaries {
    private val cache = HashMap<String, Pair<String, ExpedienteSummary>>()

    /** Resumen (con caché mientras el expediente no cambie: mismas fotos y misma última toma). */
    /**
     * [photos] y [protocolCount]: si el llamador ya los tiene (listado de todos los expedientes), sin volver a
     * consultar la galería ni a leer todos los protocolos por cada expediente.
     */
    fun of(context: Context, repo: PhotoRepository, exp: Expediente,
           photos: List<Photo> = repo.photos(exp), protocolCount: Int = Protocols.list(context).size): ExpedienteSummary {
        val stamp = "${photos.size}|${photos.maxOfOrNull { it.takenAt }}|$protocolCount"
        synchronized(cache) { cache[exp.id]?.takeIf { it.first == stamp }?.let { return it.second } }
        val face = photos.filter { it.zone.category == ZoneCategory.FACIAL }.maxByOrNull { it.takenAt }
        // tomas de protocolos (condiciones guardadas en cada foto): guardadas en disco por expediente, así un
        // arranque en frío no abre todas las fotos para leer su XMP mientras el expediente no cambie
        val disk = java.io.File(exp.metaDir, "resumen.json")
        val photoStamp = "${photos.size}|${photos.maxOfOrNull { it.takenAt }}|${photos.sumOf { it.uri.toString().hashCode().toLong() }}"
        val stored = runCatching { org.json.JSONObject(disk.readText()).takeIf { it.optString("stamp") == photoStamp }?.optJSONObject("protocolos") }.getOrNull()
        val byProtocol = HashMap<String, Int>()
        if (stored != null) stored.keys().forEach { k -> byProtocol[k] = stored.optInt(k) }
        else {
            photos.forEach { p -> runCatching { repo.readInfo(p)?.conditions?.get(ProtocolResults.KEY_ID) }.getOrNull()?.let { byProtocol[it] = (byProtocol[it] ?: 0) + 1 } }
            runCatching { com.photomed.app.align.AtomicFile.write(disk, org.json.JSONObject().put("stamp", photoStamp)
                .put("protocolos", org.json.JSONObject(byProtocol as Map<*, *>)).toString()) }
        }
        val protocols = byProtocol.mapNotNull { (id, n) -> Protocols.get(context, id)?.let { Triple(it.name, n, it.totalShots) } }
        val s = ExpedienteSummary(photos.size, photos.map { it.zone }.distinct().size, photos.maxOfOrNull { it.takenAt }, face,
            protocols.sumOf { (_, n, total) -> (total - n).coerceAtLeast(0) }, protocols)
        synchronized(cache) { cache[exp.id] = stamp to s }
        return s
    }
}
