package com.photomed.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged

/**
 * Gestos que no le quitan el dedo a nadie: el deslizador de Comparar, el zoom y el paso de una foto a
 * otra siguen funcionando aunque la foto también admita tocar una línea para seleccionarla.
 */

/**
 * Toque (sin consumir el evento): solo si el dedo no se movió más que el umbral de arrastre y fue un
 * único dedo. [onDoubleTap]: segundo toque cerca y a tiempo (el primero ya llamó a [onTap]).
 */
suspend fun PointerInputScope.detectTapPassThrough(onDoubleTap: ((Offset) -> Unit)? = null, onTap: (Offset) -> Unit) {
    var lastUp = 0L; var lastPos = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var moved = false; var multi = false
        while (true) {
            val ev = awaitPointerEvent()
            if (ev.changes.count { it.pressed } >= 2) multi = true
            ev.changes.firstOrNull { it.id == down.id }?.let { if ((it.position - down.position).getDistance() > slop) moved = true }
            if (ev.changes.none { it.pressed }) break
        }
        if (moved || multi) return@awaitEachGesture
        val now = System.currentTimeMillis()
        if (onDoubleTap != null && now - lastUp < viewConfiguration.doubleTapTimeoutMillis && (down.position - lastPos).getDistance() < slop * 4) {
            lastUp = 0L; onDoubleTap(down.position)
        } else { lastUp = now; lastPos = down.position; onTap(down.position) }
    }
}

/**
 * Zoom con dos dedos y desplazamiento con un dedo solo si [zoomed] (foto ampliada); con un dedo y sin
 * zoom el gesto no se consume (pasa al cambio de foto o al deslizador).
 */
suspend fun PointerInputScope.detectZoomPanPassThrough(zoomed: () -> Boolean, onGesture: (pan: Offset, zoom: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val ev = awaitPointerEvent()
            val pressed = ev.changes.count { it.pressed }
            val own = pressed >= 2 || (pressed == 1 && zoomed())
            if (own && ev.changes.none { it.isConsumed }) {
                val z = ev.calculateZoom(); val p = ev.calculatePan()
                if (z != 1f || p != Offset.Zero) {
                    onGesture(p, z)
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (ev.changes.any { it.pressed })
    }
}
