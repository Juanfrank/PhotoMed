package com.photomed.app.data

enum class ZoneCategory(val label: String) { FACIAL("Facial"), CORPORAL("Corporal") }

/** Tipo de guía (contorno) que se dibuja sobre la cámara para cada zona. */
enum class GuideType {
    FACE_FRONT, FACE_OBLIQUE, FACE_PROFILE, FOREHEAD, PERIOCULAR, LIPS, NECK,
    TORSO_FRONT, BREASTS, TORSO_SIDE, BACK, GLUTES, LEGS, ARM
}

/**
 * Zona anatómica estandarizada. [mirrored] invierte horizontalmente la guía
 * (p. ej. perfil derecho vs. izquierdo).
 */
enum class Zone(
    val id: String,
    val label: String,
    val category: ZoneCategory,
    val guide: GuideType,
    val mirrored: Boolean = false,
    val hint: String,
) {
    FACE_FRONTAL("rostro_frontal", "Rostro frontal", ZoneCategory.FACIAL, GuideType.FACE_FRONT,
        hint = "Mirada al frente, plano de Frankfort horizontal, cabello recogido."),
    FACE_OBLIQUE_R("rostro_oblicuo_der", "Oblicuo 45° derecho", ZoneCategory.FACIAL, GuideType.FACE_OBLIQUE,
        hint = "Girar 45° mostrando el lado derecho; punta nasal alineada con la mejilla."),
    FACE_OBLIQUE_L("rostro_oblicuo_izq", "Oblicuo 45° izquierdo", ZoneCategory.FACIAL, GuideType.FACE_OBLIQUE, mirrored = true,
        hint = "Girar 45° mostrando el lado izquierdo; punta nasal alineada con la mejilla."),
    FACE_PROFILE_R("rostro_perfil_der", "Perfil derecho", ZoneCategory.FACIAL, GuideType.FACE_PROFILE,
        hint = "Perfil a 90°, línea de Frankfort sobre la guía horizontal."),
    FACE_PROFILE_L("rostro_perfil_izq", "Perfil izquierdo", ZoneCategory.FACIAL, GuideType.FACE_PROFILE, mirrored = true,
        hint = "Perfil a 90°, línea de Frankfort sobre la guía horizontal."),
    FOREHEAD("tercio_superior", "Frente y glabela", ZoneCategory.FACIAL, GuideType.FOREHEAD,
        hint = "Encuadrar de la línea de implantación a las cejas; tomar en reposo y en contracción."),
    PERIOCULAR("periocular", "Periocular", ZoneCategory.FACIAL, GuideType.PERIOCULAR,
        hint = "Pupilas sobre la línea horizontal; tomar en reposo y sonriendo."),
    LIPS("labios", "Labios / peribucal", ZoneCategory.FACIAL, GuideType.LIPS,
        hint = "Labios relajados en contacto, comisuras sobre la línea horizontal."),
    NECK("cuello", "Cuello y escote", ZoneCategory.FACIAL, GuideType.NECK,
        hint = "Mentón al frente, hombros relajados."),
    TORSO_FRONT("abdomen_frontal", "Abdomen frontal", ZoneCategory.CORPORAL, GuideType.TORSO_FRONT,
        hint = "Brazos separados del cuerpo; ombligo sobre la cruz de referencia."),
    TORSO_SIDE("abdomen_lateral", "Abdomen lateral", ZoneCategory.CORPORAL, GuideType.TORSO_SIDE,
        hint = "Perfil a 90°, brazos cruzados sobre el pecho."),
    BREASTS("torax_mamas", "Tórax / mamas", ZoneCategory.CORPORAL, GuideType.BREASTS,
        hint = "Brazos a los lados; complejos areola-pezón sobre las marcas."),
    BACK("espalda", "Espalda", ZoneCategory.CORPORAL, GuideType.BACK,
        hint = "Columna sobre la línea media, brazos relajados."),
    GLUTES("gluteos", "Glúteos", ZoneCategory.CORPORAL, GuideType.GLUTES,
        hint = "Pies juntos, peso distribuido; pliegue glúteo sobre la guía."),
    LEGS("muslos", "Muslos / piernas", ZoneCategory.CORPORAL, GuideType.LEGS,
        hint = "Pies separados a la anchura de las caderas; rodillas sobre la línea."),
    ARM_R("brazo_der", "Brazo derecho", ZoneCategory.CORPORAL, GuideType.ARM,
        hint = "Brazo en abducción de 90°, codo flexionado; tomar posterior."),
    ARM_L("brazo_izq", "Brazo izquierdo", ZoneCategory.CORPORAL, GuideType.ARM, mirrored = true,
        hint = "Brazo en abducción de 90°, codo flexionado; tomar posterior.");

    companion object {
        fun fromId(id: String?): Zone? = entries.firstOrNull { it.id == id }
    }
}
