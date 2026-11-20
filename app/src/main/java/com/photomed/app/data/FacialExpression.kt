package com.photomed.app.data

import com.photomed.app.align.AnnotLayer

/**
 * Expresión facial de una foto, para fotografiar las arrugas dinámicas (las que aparecen con la
 * contracción muscular). Las escalas validadas de arrugas se puntúan en reposo y en contracción
 * máxima (Carruthers et al., escalas del tercio superior: frente con las cejas elevadas al máximo,
 * ceño con el fruncido máximo, patas de gallo con la sonrisa máxima), y el consenso de uso de toxina
 * botulínica describe además las líneas nasales, peribucales, del mentón y las bandas platismales,
 * cada una con su gesto. Comparar fotos solo tiene sentido con la misma expresión.
 *
 * [tag]: va en el nombre del archivo (`<zona>_exp-<tag>_<fecha>.jpg`); [muscle]: músculo que la
 * produce; [layers]: capas de arrugas que muestra.
 */
enum class FacialExpression(
    val tag: String, val label: String, val instruction: String, val muscle: String,
    val layers: Set<AnnotLayer>, val guides: Set<GuideType>,
) {
    NEUTRAL("reposo", "Reposo", "Cara relajada, labios juntos, mirada al frente", "—", AnnotLayer.entries.toSet(),
        GuideType.entries.toSet()),
    SURPRISE("sorpresa", "Sorpresa (cejas arriba)", "Levante las cejas al máximo, como sorprendido", "frontal",
        setOf(AnnotLayer.FOREHEAD), setOf(GuideType.FACE_FRONT, GuideType.FACE_OBLIQUE, GuideType.FOREHEAD)),
    FROWN("enojo", "Enojo (fruncir el ceño)", "Frunza el ceño con fuerza, como enojado", "corrugador y prócer",
        setOf(AnnotLayer.GLABELLA), setOf(GuideType.FACE_FRONT, GuideType.FOREHEAD)),
    SMILE("sonrisa", "Sonrisa máxima", "Sonría al máximo mostrando los dientes", "orbicular de los ojos y cigomáticos",
        setOf(AnnotLayer.CROWS_FEET, AnnotLayer.FOLDS), setOf(GuideType.FACE_FRONT, GuideType.FACE_OBLIQUE, GuideType.PERIOCULAR, GuideType.EYE, GuideType.LIPS)),
    SQUINT("ojos_apretados", "Ojos apretados", "Cierre los ojos con fuerza", "orbicular de los ojos",
        setOf(AnnotLayer.CROWS_FEET), setOf(GuideType.FACE_FRONT, GuideType.FACE_OBLIQUE, GuideType.PERIOCULAR, GuideType.EYE)),
    NOSE("nariz", "Arrugar la nariz", "Arrugue la nariz, como ante un mal olor", "nasal (líneas nasales)",
        setOf(AnnotLayer.GLABELLA, AnnotLayer.NOSE), setOf(GuideType.FACE_FRONT, GuideType.FACE_OBLIQUE)),
    PUCKER("beso", "Fruncir los labios", "Junte los labios como para dar un beso o silbar", "orbicular de los labios",
        setOf(AnnotLayer.LIPS, AnnotLayer.FOLDS), setOf(GuideType.FACE_FRONT, GuideType.FACE_OBLIQUE, GuideType.LIPS)),
    SAD("tristeza", "Tristeza (comisuras abajo)", "Baje las comisuras de la boca", "depresor del ángulo de la boca",
        setOf(AnnotLayer.FOLDS), setOf(GuideType.FACE_FRONT, GuideType.FACE_OBLIQUE, GuideType.LIPS)),
    CHIN("menton", "Mentón tenso", "Suba el labio inferior apretando el mentón", "mentoniano",
        setOf(AnnotLayer.LIPS), setOf(GuideType.FACE_FRONT, GuideType.FACE_OBLIQUE, GuideType.LIPS)),
    NECK("cuello", "Cuello tenso", "Tense el cuello mostrando los dientes inferiores", "platisma",
        emptySet(), setOf(GuideType.NECK));

    companion object {
        /** Expresiones que se pueden elegir en una zona (la de reposo siempre). */
        fun forZone(zone: Zone): List<FacialExpression> = entries.filter { zone.guide in it.guides }
            .takeIf { it.size > 1 } ?: listOf(NEUTRAL)

        fun ofTag(tag: String?) = entries.firstOrNull { it.tag == tag } ?: NEUTRAL

        /** Expresión de una foto por su nombre de archivo (sin etiqueta: reposo). */
        fun ofFileName(name: String): FacialExpression =
            Regex("_exp-([a-z_]+?)_\\d{8}").find(name)?.groupValues?.get(1)?.let { ofTag(it) } ?: NEUTRAL

        /** Parte del nombre del archivo (null en reposo, que no lleva etiqueta). */
        fun variantTag(e: FacialExpression): String? = if (e == NEUTRAL) null else "exp-${e.tag}"
    }
}

/** Expresión facial de la foto (por su nombre). */
val Photo.expression: FacialExpression get() = FacialExpression.ofFileName(displayName)
