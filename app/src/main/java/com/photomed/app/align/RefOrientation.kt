package com.photomed.app.align

/**
 * Giro de la referencia usado en una toma (botón «Girar la referencia 90°»). La foto nueva se tomó
 * haciendo coincidir el paciente con la foto anterior girada: su contenido está girado respecto a ella.
 * Se guarda en las condiciones de la toma ([KEY_ROT], en pasos de 90° en sentido horario, y [KEY_REF], la
 * foto de referencia) para que Comparar pueda alinear fotos con orientaciones distintas.
 */
object RefOrientation {
    const val KEY_ROT = "RefRotation"
    const val KEY_MIRROR = "RefMirror"
    const val KEY_REF = "RefPhoto"

    /**
     * Orientación absoluta de cada foto (pasos de 90° horarios respecto a la primera de su cadena): la
     * de su referencia más el giro usado al tomarla. [infos]: nombre de la foto → sus condiciones.
     */
    fun absolute(name: String, infos: Map<String, Map<String, String>>, depth: Int = 0): Int {
        val c = infos[name] ?: return 0
        val rot = c[KEY_ROT]?.toIntOrNull()?.let { (it / 90) } ?: 0
        val ref = c[KEY_REF]?.takeIf { it != name && depth < 20 } ?: return mod4(rot)
        return mod4(rot + absolute(ref, infos, depth + 1))
    }

    /** Giro (pasos de 90° horarios) que lleva el contenido de [before] al de [after]. */
    fun relative(before: String, after: String, infos: Map<String, Map<String, String>>): Int =
        mod4(absolute(after, infos) - absolute(before, infos))

    private fun mod4(v: Int) = ((v % 4) + 4) % 4

    /**
     * Semejanza de coordenadas de una foto 3:4 (unidades del ancho, y hasta 4/3) a las de la misma foto
     * girada [k] pasos de 90° en sentido horario y encajada entera en 3:4 (como
     * `ImageOps.fit34(ImageEdit(rot = k).apply(foto))`): giro alrededor del centro y escala 3/4 en los
     * giros impares. Es lo que deshace el giro de una foto tomada con la referencia girada: la cámara mostró
     * la referencia girada y recortada al centro (×4/3) y el encajado (×3/4) devuelve la escala original.
     */
    fun fitPrior(k: Int, aspect: Float = 4f / 3f): Similarity {
        val n = mod4(k); if (n == 0) return Similarity.IDENTITY
        val s = if (n % 2 == 1) 1f / aspect else 1f
        val r = (n * Math.PI / 2).toFloat()
        val c = Pt(0.5f, aspect / 2)
        val rc = Similarity(s, r, 0f, 0f).apply(c)
        return Similarity(s, r, c.x - rc.x, c.y - rc.y)
    }

    /**
     * Semejanza de coordenadas de una foto 3:4 (unidades del ancho, y hasta 4/3) a las de la misma foto
     * girada [k] pasos de 90° en sentido horario y recortada al centro en 3:4 (como
     * `ImageOps.centerCrop34(ImageEdit(rot = k).apply(foto))`): un giro de 90° alrededor del centro con
     * la escala del recorte (4/3 en los giros impares).
     */
    fun prior(k: Int, aspect: Float = 4f / 3f): Similarity {
        val a = aspect
        return when (mod4(k)) {
            1 -> Similarity(a, (Math.PI / 2).toFloat(), (a * a + 1) / 2, 0f)
            2 -> Similarity(1f, Math.PI.toFloat(), 1f, a)
            3 -> Similarity(a, (-Math.PI / 2).toFloat(), -(a * a - 1) / 2, a)
            else -> Similarity.IDENTITY
        }
    }
}
