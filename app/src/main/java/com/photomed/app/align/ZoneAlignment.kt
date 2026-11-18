package com.photomed.app.align

import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone

enum class Detector { FACE, POSE, NONE }

/**
 * Estrategia de coincidencia por zona:
 * - [detector] y [landmarks]: puntos anatómicos automáticos (claves de [Landmarks]).
 * - [contours]: regiones anatómicas cuyos contornos se hacen coincidir (mandíbula, frente,
 *   orejas, caderas, falanges…), en coordenadas normalizadas del encuadre 3:4 (x ∈ [0,1], y ∈ [0,4/3]).
 * - [manual]: los dos puntos que el usuario marca en el modo manual.
 */
data class ZoneAlignment(
    val detector: Detector,
    val landmarks: Set<String>,
    val contours: List<ContourRegion>,
    val manual: List<String>,
    val description: String,
) {
    /** Centroide de las regiones (pivote de escala y referencia de las indicaciones en vivo). */
    val pivot: Pt
        get() {
            val ws = contours.sumOf { it.weight.toDouble() }.toFloat()
            return Pt(contours.sumOf { (it.center.x * it.weight).toDouble() }.toFloat() / ws,
                contours.sumOf { (it.center.y * it.weight).toDouble() }.toFloat() / ws)
        }

    companion object {
        private fun r(name: String, x: Float, y: Float, rx: Float, ry: Float, w: Float = 1f) =
            ContourRegion(name, Pt(x, y), Pt(rx, ry), w)

        private fun List<ContourRegion>.mirrored(m: Boolean) =
            if (!m) this else map { it.copy(center = Pt(1f - it.center.x, it.center.y)) }

        fun of(zone: Zone): ZoneAlignment {
            val L = Landmarks
            return when (zone.guide) {
                GuideType.FACE_FRONT -> ZoneAlignment(
                    Detector.FACE, L.FACE_ALL,
                    listOf(r("mandíbula", 0.5f, 1.12f, 0.32f, 0.12f), r("frente", 0.5f, 0.27f, 0.36f, 0.12f, 0.8f),
                        r("oreja derecha", 0.1f, 0.76f, 0.06f, 0.14f, 0.7f), r("oreja izquierda", 0.9f, 0.76f, 0.06f, 0.14f, 0.7f),
                        r("ojos y cejas", 0.5f, 0.6f, 0.32f, 0.08f, 0.6f)),
                    listOf("Pupila derecha", "Pupila izquierda"),
                    "óvalo facial, cejas, ojos, nariz y labios",
                )
                GuideType.FACE_OBLIQUE -> ZoneAlignment(
                    Detector.FACE, L.FACE_ALL,
                    listOf(r("mandíbula", 0.55f, 1.1f, 0.3f, 0.13f), r("frente", 0.55f, 0.3f, 0.35f, 0.13f, 0.8f),
                        r("oreja", 0.17f, 0.78f, 0.07f, 0.14f, 0.8f), r("mejilla y nariz", 0.83f, 0.85f, 0.08f, 0.22f, 0.8f),
                    ).mirrored(zone.mirrored),
                    listOf("Pupila derecha", "Pupila izquierda"),
                    "óvalo facial, ojos, nariz y labios",
                )
                GuideType.FACE_PROFILE -> ZoneAlignment(
                    Detector.FACE, setOf(L.EAR_L, L.EAR_R, L.NOSE_BASE, L.EYE_L, L.EYE_R, L.MOUTH_L, L.MOUTH_R) +
                        L.group("nose_") + L.group("eye_") + L.group("brow_"),
                    listOf(r("frente", 0.84f, 0.5f, 0.08f, 0.16f), r("nariz y labios", 0.92f, 0.86f, 0.07f, 0.2f),
                        r("mandíbula", 0.66f, 1.08f, 0.2f, 0.08f), r("oreja", 0.43f, 0.8f, 0.07f, 0.12f),
                        r("occipucio", 0.1f, 0.7f, 0.08f, 0.25f, 0.5f)).mirrored(zone.mirrored),
                    listOf("Tragus (oreja)", "Punta nasal"),
                    "oreja, ojo, nariz y perfil",
                )
                GuideType.FOREHEAD -> ZoneAlignment(
                    Detector.FACE, L.group("brow_") + L.group("eye_") + L.ovalRange(0..6) + L.ovalRange(30..35),
                    listOf(r("cejas", 0.5f, 0.9f, 0.4f, 0.08f), r("frente", 0.5f, 0.3f, 0.45f, 0.15f, 0.8f)),
                    listOf("Cabeza de ceja derecha", "Cabeza de ceja izquierda"),
                    "cejas y contorno frontal",
                )
                GuideType.PERIOCULAR -> ZoneAlignment(
                    Detector.FACE, L.group("eye_") + L.group("brow_") + setOf(L.EYE_L, L.EYE_R),
                    listOf(r("ojos", 0.5f, 0.69f, 0.4f, 0.08f), r("cejas", 0.5f, 0.5f, 0.4f, 0.06f, 0.7f)),
                    listOf("Pupila derecha", "Pupila izquierda"),
                    "contornos palpebrales y cejas",
                )
                GuideType.LIPS -> ZoneAlignment(
                    Detector.FACE, L.group("lip_") + L.group("nose_") + setOf(L.MOUTH_L, L.MOUTH_R),
                    listOf(r("labios", 0.5f, 0.69f, 0.3f, 0.1f), r("base nasal", 0.5f, 0.2f, 0.25f, 0.08f, 0.6f),
                        r("mentón", 0.5f, 1.2f, 0.4f, 0.1f, 0.6f)),
                    listOf("Comisura derecha", "Comisura izquierda"),
                    "bermellón, comisuras y base nasal",
                )
                GuideType.NECK -> ZoneAlignment(
                    Detector.POSE, setOf(L.SHOULDER_L, L.SHOULDER_R, L.MOUTH_L, L.MOUTH_R, L.EAR_L, L.EAR_R, L.NOSE),
                    listOf(r("mandíbula", 0.5f, 0.35f, 0.3f, 0.1f), r("hombros y clavículas", 0.5f, 0.78f, 0.45f, 0.14f)),
                    listOf("Mentón", "Escotadura yugular"),
                    "mandíbula, hombros y contorno cervical",
                )
                GuideType.TORSO_FRONT -> ZoneAlignment(
                    Detector.POSE, L.TRUNK,
                    listOf(r("cintura", 0.5f, 0.72f, 0.45f, 0.14f), r("caderas", 0.5f, 1.12f, 0.45f, 0.12f),
                        r("ombligo", 0.5f, 0.78f, 0.08f, 0.08f, 0.5f)),
                    listOf("Escotadura yugular", "Ombligo"),
                    "hombros, caderas, cintura y ombligo",
                )
                GuideType.BREASTS -> ZoneAlignment(
                    Detector.POSE, L.TRUNK,
                    listOf(r("contorno mamario", 0.5f, 0.62f, 0.35f, 0.12f), r("hombros", 0.5f, 0.15f, 0.45f, 0.1f, 0.7f)),
                    listOf("Pezón derecho", "Pezón izquierdo"),
                    "hombros, caderas y contorno torácico",
                )
                GuideType.TORSO_SIDE -> ZoneAlignment(
                    Detector.POSE, L.TRUNK + setOf(L.KNEE_L, L.KNEE_R, L.ELBOW_L, L.ELBOW_R),
                    listOf(r("abdomen", 0.7f, 0.75f, 0.12f, 0.3f), r("glúteo", 0.27f, 1.05f, 0.12f, 0.2f),
                        r("axila", 0.45f, 0.25f, 0.2f, 0.1f, 0.7f)).mirrored(zone.mirrored),
                    listOf("Hombro (acromion)", "Cadera (trocánter)"),
                    "silueta abdominal, glúteo y axila",
                )
                GuideType.BACK -> ZoneAlignment(
                    Detector.POSE, L.TRUNK,
                    listOf(r("escápulas", 0.5f, 0.4f, 0.35f, 0.12f), r("cintura", 0.5f, 0.95f, 0.42f, 0.15f),
                        r("hombros", 0.5f, 0.12f, 0.45f, 0.08f, 0.6f)),
                    listOf("Vértebra C7", "Inicio del pliegue interglúteo"),
                    "hombros, escápulas, cintura y caderas",
                )
                GuideType.GLUTES -> ZoneAlignment(
                    Detector.POSE, setOf(L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R),
                    listOf(r("pliegues glúteos", 0.5f, 0.75f, 0.35f, 0.08f), r("caderas", 0.5f, 0.4f, 0.45f, 0.15f)),
                    listOf("Pliegue glúteo derecho (centro)", "Pliegue glúteo izquierdo (centro)"),
                    "caderas, pliegues glúteos y muslos",
                )
                GuideType.LEGS -> ZoneAlignment(
                    Detector.POSE, setOf(L.HIP_L, L.HIP_R, L.KNEE_L, L.KNEE_R, L.ANKLE_L, L.ANKLE_R,
                        L.HEEL_L, L.HEEL_R, L.FOOT_INDEX_L, L.FOOT_INDEX_R),
                    listOf(r("rodillas", 0.5f, 0.66f, 0.35f, 0.08f), r("muslos", 0.5f, 0.35f, 0.45f, 0.2f)),
                    listOf("Rodilla derecha (centro)", "Rodilla izquierda (centro)"),
                    "caderas, rodillas, tobillos y bordes",
                )
                GuideType.ARM -> ZoneAlignment(
                    Detector.POSE, setOf(L.SHOULDER_L, L.SHOULDER_R, L.ELBOW_L, L.ELBOW_R, L.WRIST_L, L.WRIST_R,
                        L.INDEX_L, L.INDEX_R, L.PINKY_L, L.PINKY_R),
                    listOf(r("codo", 0.55f, 0.55f, 0.25f, 0.1f), r("brazo", 0.55f, 0.6f, 0.3f, 0.45f, 0.7f)).mirrored(zone.mirrored),
                    listOf("Acromion (hombro)", "Olécranon (codo)"),
                    "hombro, codo, muñeca y bordes del brazo",
                )
                GuideType.HANDS -> ZoneAlignment(
                    Detector.POSE, setOf(L.WRIST_L, L.WRIST_R, L.INDEX_L, L.INDEX_R, L.PINKY_L, L.PINKY_R, L.THUMB_L, L.THUMB_R),
                    listOf(r("falanges", 0.5f, 0.86f, 0.45f, 0.12f), r("dorso / palma", 0.5f, 0.62f, 0.45f, 0.12f, 0.7f)),
                    listOf("Punta del dedo medio derecho", "Punta del dedo medio izquierdo"),
                    "falanges, muñecas y bordes de las manos",
                )
                GuideType.FEET -> ZoneAlignment(
                    Detector.POSE, setOf(L.ANKLE_L, L.ANKLE_R, L.HEEL_L, L.HEEL_R, L.FOOT_INDEX_L, L.FOOT_INDEX_R),
                    listOf(r("falanges", 0.5f, 0.98f, 0.45f, 0.1f), r("tobillos y talones", 0.5f, 0.55f, 0.45f, 0.15f, 0.8f)),
                    listOf("Punta del primer dedo derecho", "Punta del primer dedo izquierdo"),
                    "dedos, tobillos, talones y bordes de los pies",
                )
            }
        }
    }
}

/** Claves de puntos anatómicos (independientes del detector). */
object Landmarks {
    const val EYE_L = "eye_l"
    const val EYE_R = "eye_r"
    const val EAR_L = "ear_l"
    const val EAR_R = "ear_r"
    const val NOSE = "nose"
    const val NOSE_BASE = "nose_base"
    const val MOUTH_L = "mouth_l"
    const val MOUTH_R = "mouth_r"
    const val SHOULDER_L = "shoulder_l"
    const val SHOULDER_R = "shoulder_r"
    const val ELBOW_L = "elbow_l"
    const val ELBOW_R = "elbow_r"
    const val WRIST_L = "wrist_l"
    const val WRIST_R = "wrist_r"
    const val HIP_L = "hip_l"
    const val HIP_R = "hip_r"
    const val KNEE_L = "knee_l"
    const val KNEE_R = "knee_r"
    const val ANKLE_L = "ankle_l"
    const val ANKLE_R = "ankle_r"
    const val INDEX_L = "index_l"
    const val INDEX_R = "index_r"
    const val PINKY_L = "pinky_l"
    const val PINKY_R = "pinky_r"
    const val THUMB_L = "thumb_l"
    const val THUMB_R = "thumb_r"
    const val HEEL_L = "heel_l"
    const val HEEL_R = "heel_r"
    const val FOOT_INDEX_L = "foot_index_l"
    const val FOOT_INDEX_R = "foot_index_r"

    /** Contornos faciales de ML Kit: grupo → número de puntos (claves "<grupo><n>"). */
    val FACE_CONTOURS: Map<String, Int> = linkedMapOf(
        "oval_" to 36,
        "brow_l_top_" to 5, "brow_l_bottom_" to 5, "brow_r_top_" to 5, "brow_r_bottom_" to 5,
        "eye_l_" to 16, "eye_r_" to 16,
        "lip_upper_top_" to 11, "lip_upper_bottom_" to 9, "lip_lower_top_" to 9, "lip_lower_bottom_" to 9,
        "nose_bridge_" to 2, "nose_bottom_" to 3,
    )

    fun group(prefix: String): Set<String> =
        FACE_CONTOURS.filterKeys { it.startsWith(prefix) }.flatMap { (g, n) -> (0 until n).map { "$g$it" } }.toSet()

    fun ovalRange(r: IntRange): Set<String> = r.map { "oval_$it" }.toSet()

    val FACE_ALL: Set<String> = FACE_CONTOURS.flatMap { (g, n) -> (0 until n).map { "$g$it" } }.toSet() +
        setOf(EYE_L, EYE_R, NOSE_BASE, MOUTH_L, MOUTH_R, EAR_L, EAR_R)

    val TRUNK = setOf(SHOULDER_L, SHOULDER_R, HIP_L, HIP_R, ELBOW_L, ELBOW_R)

    /** Nombre legible de la región de un punto (para informar qué contornos se usaron). */
    fun regionOf(key: String): String = when {
        key.startsWith("oval_") -> key.removePrefix("oval_").toInt().let {
            if (it in 0..6 || it in 30..35) "frente" else if (it in 12..24) "mandíbula" else "mejillas"
        }
        key.startsWith("brow_") -> "cejas"
        key.startsWith("eye") -> "ojos"
        key.startsWith("lip_") || key.startsWith("mouth") -> "labios"
        key.startsWith("nose") -> "nariz"
        key.startsWith("ear") -> "orejas"
        key.startsWith("shoulder") -> "hombros"
        key.startsWith("elbow") -> "codos"
        key.startsWith("wrist") || key.startsWith("index") || key.startsWith("pinky") || key.startsWith("thumb") -> "manos"
        key.startsWith("hip") -> "caderas"
        key.startsWith("knee") -> "rodillas"
        else -> "pies"
    }
}

/**
 * Combina los puntos anatómicos detectados en ambas fotos.
 * Devuelve la semejanza después → antes, o null si hay < 2 puntos comunes.
 * Rechazo iterativo de atípicos: mientras haya ≥ 4 puntos, se descarta el par con residuo
 * > 9 × la mediana (hasta un 25 % de los puntos).
 */
fun landmarkTransform(allowed: Set<String>, before: Map<String, Pt>, after: Map<String, Pt>): Pair<Similarity, List<String>>? {
    var keys = allowed.filter { it in before && it in after }.sorted()
    if (keys.size < 2) return null
    var t = Similarity.estimate(keys.map { after[it]!! }, keys.map { before[it]!! }) ?: return null
    val maxRemove = keys.size / 4
    var removed = 0
    while (keys.size >= 4 && removed < maxRemove.coerceAtLeast(1)) {
        val residuals = keys.map { k -> val d = t.apply(after[k]!!) - before[k]!!; k to (d.x * d.x + d.y * d.y) }
        val median = residuals.map { it.second }.sorted()[residuals.size / 2]
        val worst = residuals.maxBy { it.second }
        if (worst.second <= 9 * median || worst.second <= 1e-4f) break
        keys = keys - worst.first
        removed++
        t = Similarity.estimate(keys.map { after[it]!! }, keys.map { before[it]!! }) ?: break
    }
    return t to keys
}
