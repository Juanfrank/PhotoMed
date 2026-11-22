package com.photomed.app.align

/**
 * Máscara de clases estable en el tiempo: cada píxel toma la clase que coincide en al menos dos de las
 * últimas tres segmentaciones (si no, la más reciente). Un parpadeo del modelo en un cuadro (un dedo que
 * se pierde, un trozo de fondo tomado por piel) ya no cambia el pie ni la mano; el color es el del cuadro
 * más reciente. Si cambia el tamaño de la máscara, se empieza de nuevo.
 */
class ClassesFusion(private val depth: Int = 3) {
    private val recent = ArrayDeque<ZoneCriteria.Classes>()

    fun add(c: ZoneCriteria.Classes): ZoneCriteria.Classes {
        if (recent.isNotEmpty() && (recent.last().width != c.width || recent.last().height != c.height)) recent.clear()
        recent.addLast(c); while (recent.size > depth) recent.removeFirst()
        if (recent.size < 3) return c
        val (a, b, n) = Triple(recent[recent.size - 3].data, recent[recent.size - 2].data, c.data)
        val out = ByteArray(n.size) { i -> if (a[i] == b[i] && a[i] != n[i]) a[i] else n[i] }
        return ZoneCriteria.Classes(c.width, c.height, out, c.rgb)
    }

    fun reset() = recent.clear()
}
