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
    fun of(context: Context, repo: PhotoRepository, exp: Expediente): ExpedienteSummary {
        val photos = repo.photos(exp)
        val stamp = "${photos.size}|${photos.maxOfOrNull { it.takenAt }}|${Protocols.list(context).size}"
        synchronized(cache) { cache[exp.id]?.takeIf { it.first == stamp }?.let { return it.second } }
        val face = photos.filter { it.zone.category == ZoneCategory.FACIAL }.maxByOrNull { it.takenAt }
        // tomas de protocolos (condiciones guardadas en cada foto)
        val byProtocol = HashMap<String, Int>()
        photos.forEach { p -> runCatching { repo.readInfo(p)?.conditions?.get(ProtocolResults.KEY_ID) }.getOrNull()?.let { byProtocol[it] = (byProtocol[it] ?: 0) + 1 } }
        val protocols = byProtocol.mapNotNull { (id, n) -> Protocols.get(context, id)?.let { Triple(it.name, n, it.totalShots) } }
        val s = ExpedienteSummary(photos.size, photos.map { it.zone }.distinct().size, photos.maxOfOrNull { it.takenAt }, face,
            protocols.sumOf { (_, n, total) -> (total - n).coerceAtLeast(0) }, protocols)
        synchronized(cache) { cache[exp.id] = stamp to s }
        return s
    }
}
