package com.photomed.app.align

/**
 * Máscara de clases estable en el tiempo: cada píxel toma la clase que coincide en al menos dos de las
 * últimas tres segmentaciones (si no, la más reciente). Un parpadeo del modelo en un cuadro (un dedo que
 * se pierde, un trozo de fondo tomado por piel) ya no cambia el pie ni la mano; el color es el del cuadro
 * más reciente. Si cambia el tamaño de la máscara, se empieza de nuevo. Solo con la escena quieta: si la
 * piel de la nueva máscara se solapa poco con la anterior (la cámara o el paciente se mueven), se descarta
 * la historia y vale la nueva (la fusión iría por detrás de lo que se ve).
 */
class ClassesFusion(private val depth: Int = 3, private val minOverlap: Float = 0.7f) {
    private val recent = ArrayDeque<ZoneCriteria.Classes>()

    /** Solapamiento (intersección / unión) de la piel de dos máscaras del mismo tamaño. */
    fun skinOverlap(a: ZoneCriteria.Classes, b: ZoneCriteria.Classes): Float {
        var inter = 0; var union = 0
        for (i in a.data.indices) {
            val sa = a.data[i].toInt().let { it == FaceLabels.BODY_SKIN || it == FaceLabels.FACE_SKIN }
            val sb = b.data[i].toInt().let { it == FaceLabels.BODY_SKIN || it == FaceLabels.FACE_SKIN }
            if (sa || sb) union++; if (sa && sb) inter++
        }
        return if (union == 0) 1f else inter / union.toFloat()
    }

    fun add(c: ZoneCriteria.Classes): ZoneCriteria.Classes {
        if (recent.isNotEmpty() && (recent.last().width != c.width || recent.last().height != c.height)) recent.clear()
        if (recent.isNotEmpty() && skinOverlap(recent.last(), c) < minOverlap) recent.clear()
        recent.addLast(c); while (recent.size > depth) recent.removeFirst()
        if (recent.size < 3) return c
        val (a, b, n) = Triple(recent[recent.size - 3].data, recent[recent.size - 2].data, c.data)
        val out = ByteArray(n.size) { i -> if (a[i] == b[i] && a[i] != n[i]) a[i] else n[i] }
        return ZoneCriteria.Classes(c.width, c.height, out, c.rgb)
    }

    fun reset() = recent.clear()
}
