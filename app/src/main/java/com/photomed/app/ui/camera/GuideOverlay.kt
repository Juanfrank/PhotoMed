package com.photomed.app.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.remember
import com.photomed.app.align.Pt
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.photomed.app.data.GuideType
import com.photomed.app.data.Zone

/**
 * Guía anatómica en coordenadas normalizadas (0..1) dentro de un encuadre 3:4.
 * [contours]: siluetas (trazo continuo). [lines]: líneas de referencia (discontinuas).
 * [marks]: puntos de referencia (círculos pequeños).
 */
class Guide(
    val contours: List<Pair<List<Offset>, Boolean>>, // puntos + cerrado
    val lines: List<Pair<Offset, Offset>>,
    val marks: List<Offset> = emptyList(),
)

internal val ContourColor = Color(0xFF7CF5E4)
private val LineColor = Color(0xFFFFD54F)
private val GridColor = Color(0x99FFFFFF)
private val Shadow = Color(0x66000000)

/** Color de la guía según la coincidencia en vivo con la foto de referencia. */
enum class GuideTone(val color: Color) {
    NORMAL(ContourColor),
    CLOSE(Color(0xFFFFD54F)),
    MATCH(Color(0xFF69F0AE)),
}

/** Máxima fracción de celdas plegadas en el rostro para usar la deformación rasgo a rasgo. */
const val MAX_FOLDS = 0.04f

@Composable
fun GuideOverlay(
    zone: Zone,
    showContour: Boolean,
    showGrid: Boolean,
    modifier: Modifier = Modifier,
    tone: GuideTone = GuideTone.NORMAL,
    /** Puntos detectados en la foto de referencia: si hay, la guía se adapta a ellos. */
    adaptTo: Map<String, Pt> = emptyMap(),
    /** Silueta segmentada de la referencia (cuerpo y perfil): la guía se ajusta a sus bordes. */
    silhouette: com.photomed.app.align.Silhouette? = null,
    /** Modo individual de extremidades: una sola mano, pie o pierna (ver [LimbSide]). */
    side: LimbSide = LimbSide.BOTH,
    /** Modo libre: contorno de lo visible (referencia o cuadro actual). */
    free: com.photomed.app.align.FreeContour? = null,
) {
    if (zone.guide == com.photomed.app.data.GuideType.FREE) {
        FreeGuide(free, showContour, showGrid, modifier, tone)
        return
    }
    val base = GuideArtByZone[zone.id]
    val frame = base?.limbFrame(side)
    if (base == null || frame == null) {
        GuideLayer(zone, base, showContour, showGrid, modifier, tone, adaptTo, silhouette, emptyMap())
        return
    }
    // Modo individual: la guía se acerca a la mitad del dibujo de ese lado (encuadre 3:4) y se
    // ajusta a la silueta de esa extremidad; los datos de la foto pasan a coordenadas del dibujo.
    val outline = base.limbOutline(side)
    val n = outline?.size?.div(3) ?: 0
    val art = remember(base, side) {
        // extremos de la extremidad en el dibujo: recuadro de su mitad (incluye dedos y yemas)
        val hb = base.limbBox(side)
        val anchors = base.anchors + ("x:centro" to (frame.left + frame.right) / 2) + ("x:extremidad" to 1f) +
            (if (hb != null) mapOf("extremo_sup" to hb.top, "extremo_inf" to hb.bottom) else emptyMap())
        GuideArt(base.drawable, base.lines, base.marks, anchors, outline ?: FloatArray(0))
    }
    val pts = remember(adaptTo, frame) { adaptTo.mapValues { frame.toArt(it.value) } }
    val sil = remember(silhouette, frame) { silhouette?.let { frame.toArt(it) } }
    val extra = remember(silhouette, frame) {
        buildMap {
            silhouette?.top?.let { put("extremo_sup", frame.top + it * frame.height) }
            silhouette?.bottom?.let { put("extremo_inf", frame.top + it * frame.height) }
        }
    }
    Box(modifier.clipToBounds()) {
        if (showGrid) Canvas(Modifier.matchParentSize()) { drawGrid() }
        val keep = remember(base, side) { base.limbClip(side) }
        Box(Modifier.matchParentSize().graphicsLayer {
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            scaleX = 1f / frame.width; scaleY = 1f / frame.height
            translationX = -frame.left / frame.width * size.width
            translationY = -frame.top / frame.height * size.height
        }) {
            // solo la extremidad elegida (la otra mitad del dibujo no se dibuja)
            Box(Modifier.fillMaxSize().drawWithContent {
                val k = keep ?: return@drawWithContent drawContent()
                clipRect(k.left * size.width, k.top * size.height, k.right * size.width, k.bottom * size.height) { this@drawWithContent.drawContent() }
            }) {
                GuideLayer(zone, art, showContour, false, Modifier.fillMaxSize(), tone, pts, sil, extra)
            }
        }
    }
}

@Composable
private fun GuideLayer(
    zone: Zone,
    art: GuideArt?,
    showContour: Boolean,
    showGrid: Boolean,
    modifier: Modifier,
    tone: GuideTone,
    adaptTo: Map<String, Pt>,
    silhouette: com.photomed.app.align.Silhouette?,
    /** Anclas adicionales medidas en la foto (p. ej. extremos de la silueta de una extremidad). */
    extraAnchors: Map<String, Float>,
) {
    // Rostro: deformación rasgo a rasgo (thin-plate spline con ~140 puntos de control).
    val mesh = remember(art, adaptTo) {
        if (art == null || adaptTo.isEmpty() || art.controls.isEmpty()) null
        else runCatching { FaceMeshWarp.fit(FaceControlKeys, art.controls, adaptTo) }.getOrNull()
            // malla plegada dentro del rostro (detección ruidosa, cabeza muy girada): ajuste por franjas
            ?.takeIf { it.foldFraction(FaceControlKeys, art.controls) < MAX_FOLDS }
    }
    if (art != null && mesh != null) {
        val bmp = ImageBitmap.imageResource(art.drawable)
        val paint = remember(tone) {
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = android.graphics.LightingColorFilter(tone.color.toArgb(), 0)
            }
        }
        Canvas(modifier) {
            if (showGrid) drawGrid()
            if (showContour) {
                val cols = 36; val rows = 48
                val verts = mesh.meshVertices(cols, rows, size.width, size.height)
                drawIntoCanvas { it.nativeCanvas.drawBitmapMesh(bmp.asAndroidBitmap(), cols, rows, verts, 0, null, 0, paint) }
                drawGuide(Guide(emptyList(), art.lines.map { (a, b) -> mesh.map(a) to mesh.map(b) }, art.marks.map { mesh.map(it) }), mirror = false)
            }
        }
        return
    }
    val warp = remember(art, adaptTo, silhouette, extraAnchors) {
        if (art == null || (adaptTo.isEmpty() && silhouette == null)) null else {
            val d0 = GuideWarp.analyze(adaptTo, silhouette, art.anchors)
            val det = if (extraAnchors.isEmpty()) d0 else
                GuideWarp.Detected(d0.anchors + extraAnchors, d0.oval, d0.rollDeg, d0.pivot, d0.pitchDeg, d0.silhouette)
            // Rostro: el borde superior del contorno de la ilustración es su vértice del cráneo.
            val face = zone.category == com.photomed.app.data.ZoneCategory.FACIAL
            val artAnchors = if (face && art.outline.size >= 3) art.anchors + ("vértice" to art.outline[0]) else art.anchors
            GuideWarp.fit(artAnchors, det, art.outline)?.let { it to det.anchors }
        }
    }
    if (art != null && warp != null) {
        val (w, det) = warp
        val bmp = ImageBitmap.imageResource(art.drawable)
        val filter = ColorFilter.lighting(tone.color, Color.Black)
        Canvas(modifier) {
            if (showGrid) drawGrid()
            if (showContour) {
                // La guía sigue la inclinación de la cabeza de la referencia.
                rotate(w.rollDeg, Offset(w.pivot.x * size.width, w.pivot.y * size.height)) {
                    // Deformación por franjas horizontales; cada franja por mitades (izquierda/derecha
                    // de la línea media) para seguir el contorno del cráneo, mejillas y mandíbula.
                    val n = 160
                    for (i in 0 until n) {
                        val a0 = i / n.toFloat(); val a1 = (i + 1) / n.toFloat(); val am = (a0 + a1) / 2
                        val ac = w.rowArtCenter(am)
                        val cSrc = (ac * bmp.width).toInt().coerceIn(1, bmp.width - 1)
                        val y0 = w.y(a0) * size.height; val y1 = w.y(a1) * size.height
                        if (y1 - y0 < 0.5f) continue
                        val sy0 = (a0 * bmp.height).toInt(); val sy1 = ((a1 * bmp.height).toInt()).coerceAtMost(bmp.height)
                        val center = w.rowCenter(am) * size.width
                        val left = center - ac * w.rowLeftScale(am) * size.width
                        val right = center + (1f - ac) * w.rowRightScale(am) * size.width
                        val dy = IntOffset(0, y0.toInt()); val dh = (y1 - y0).toInt() + 1
                        val sh = (sy1 - sy0).coerceAtLeast(1)
                        drawImage(bmp, srcOffset = IntOffset(0, sy0), srcSize = IntSize(cSrc, sh),
                            dstOffset = dy.copy(x = left.toInt()), dstSize = IntSize((center - left).toInt().coerceAtLeast(1), dh),
                            colorFilter = filter)
                        drawImage(bmp, srcOffset = IntOffset(cSrc, sy0), srcSize = IntSize(bmp.width - cSrc, sh),
                            dstOffset = dy.copy(x = center.toInt()), dstSize = IntSize((right - center).toInt().coerceAtLeast(1), dh),
                            colorFilter = filter)
                    }
                    drawGuide(Guide(emptyList(), art.lines.map { (a, b) -> w.line(a, b, art.anchors, det) }, art.marks.map { w.map(it) }), mirror = false)
                }
            }
        }
        return
    }
    if (art != null) {
        // Ilustración anatómica (trazo blanco + halo oscuro): el color se aplica aquí,
        // multiplicando (blanco → color de la guía, el halo negro no cambia).
        Box(modifier) {
            if (showContour) {
                Image(painterResource(art.drawable), contentDescription = null,
                    contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize(),
                    colorFilter = ColorFilter.lighting(tone.color, Color.Black))
            }
            Canvas(Modifier.matchParentSize()) {
                if (showGrid) drawGrid()
                if (showContour) drawGuide(Guide(emptyList(), art.lines, art.marks), mirror = false)
            }
        }
        return
    }
    // Zonas sin ilustración (oblicuos 45°, abdomen lateral): contorno vectorial.
    val guide = guideFor(zone.guide)
    Canvas(modifier) {
        if (showGrid) drawGrid()
        if (showContour) drawGuide(guide, zone.mirrored)
    }
}

private fun DrawScope.drawGrid() {
    val w = size.width
    val h = size.height
    val stroke = 1.dp.toPx()
    for (i in 1..2) {
        drawLine(GridColor, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), stroke)
        drawLine(GridColor, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), stroke)
    }
    // Cruz central
    val c = Offset(w / 2, h / 2)
    val s = 14.dp.toPx()
    drawLine(Color.White, c - Offset(s, 0f), c + Offset(s, 0f), 1.5f.dp.toPx())
    drawLine(Color.White, c - Offset(0f, s), c + Offset(0f, s), 1.5f.dp.toPx())
}

private fun DrawScope.drawGuide(guide: Guide, mirror: Boolean) {
    fun map(p: Offset) = Offset((if (mirror) 1f - p.x else p.x) * size.width, p.y * size.height)
    // Grosores proporcionales al tamaño (referencia: guía de 360 dp de ancho en la cámara),
    // para que miniaturas y cámara mantengan la misma jerarquía visual.
    val k = (size.width / 360.dp.toPx()).coerceIn(0.35f, 1.4f)
    fun d(v: Float) = v.dp.toPx() * k

    val dash = PathEffect.dashPathEffect(floatArrayOf(d(9f), d(7f)))
    guide.lines.forEach { (a, b) ->
        drawLine(Shadow, map(a), map(b), d(2.5f), pathEffect = dash)
        drawLine(LineColor, map(a), map(b), d(1.2f), pathEffect = dash)
    }
    guide.contours.forEach { (points, closed) ->
        val path = smoothPath(points.map(::map), closed)
        drawPath(path, Shadow, style = Stroke(d(4f), cap = StrokeCap.Round))
        drawPath(path, ContourColor, style = Stroke(d(1.6f), cap = StrokeCap.Round))
    }
    guide.marks.forEach { m ->
        drawCircle(Shadow, d(6f), map(m), style = Stroke(d(2.5f)))
        drawCircle(LineColor, d(6f), map(m), style = Stroke(d(1.2f)))
    }
}

/** Curva Catmull-Rom suave que pasa por todos los puntos. */
fun smoothPath(pts: List<Offset>, closed: Boolean): Path {
    val path = Path()
    if (pts.size < 2) return path
    val n = pts.size
    fun at(i: Int): Offset = if (closed) pts[(i + n) % n] else pts[i.coerceIn(0, n - 1)]
    path.moveTo(pts[0].x, pts[0].y)
    val segments = if (closed) n else n - 1
    for (i in 0 until segments) {
        val p0 = at(i - 1)
        val p1 = at(i)
        val p2 = at(i + 1)
        val p3 = at(i + 2)
        val c1 = p1 + (p2 - p0) / 6f
        val c2 = p2 - (p3 - p1) / 6f
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    if (closed) path.close()
    return path
}

private fun o(x: Float, y: Float) = Offset(x, y)
private fun hLine(y: Float, x0: Float = 0.04f, x1: Float = 0.96f) = o(x0, y) to o(x1, y)
private fun vLine(x: Float, y0: Float = 0.03f, y1: Float = 0.97f) = o(x, y0) to o(x, y1)
private fun mirrorX(points: List<Offset>) = points.map { o(1f - it.x, it.y) }
private fun open(p: List<Offset>) = p to false
private fun closed(p: List<Offset>) = p to true

fun guideFor(type: GuideType): Guide = when (type) {
    GuideType.FACE_FRONT -> Guide(
        contours = listOf(
            // Óvalo facial
            closed(listOf(
                o(0.50f, 0.14f), o(0.68f, 0.18f), o(0.77f, 0.32f), o(0.76f, 0.46f),
                o(0.71f, 0.60f), o(0.61f, 0.71f), o(0.50f, 0.75f), o(0.39f, 0.71f),
                o(0.29f, 0.60f), o(0.24f, 0.46f), o(0.23f, 0.32f), o(0.32f, 0.18f),
            )),
            // Cuello y hombros
            open(listOf(o(0.38f, 0.70f), o(0.37f, 0.84f), o(0.20f, 0.91f), o(0.04f, 0.95f))),
            open(listOf(o(0.62f, 0.70f), o(0.63f, 0.84f), o(0.80f, 0.91f), o(0.96f, 0.95f))),
        ),
        lines = listOf(
            vLine(0.5f),
            hLine(0.18f, 0.2f, 0.8f), // implantación capilar
            hLine(0.37f, 0.15f, 0.85f), // cejas / glabela
            hLine(0.42f, 0.15f, 0.85f), // línea pupilar
            hLine(0.56f, 0.2f, 0.8f), // base nasal
            hLine(0.75f, 0.3f, 0.7f), // mentón
            vLine(0.385f, 0.38f, 0.66f), vLine(0.615f, 0.38f, 0.66f), // líneas medio-pupilares
        ),
        marks = listOf(o(0.385f, 0.42f), o(0.615f, 0.42f)),
    )

    GuideType.FACE_OBLIQUE -> Guide(
        contours = listOf(
            closed(listOf(
                o(0.47f, 0.14f), o(0.64f, 0.18f), o(0.71f, 0.32f), o(0.72f, 0.45f),
                o(0.68f, 0.58f), o(0.60f, 0.70f), o(0.50f, 0.75f), o(0.40f, 0.71f),
                o(0.30f, 0.60f), o(0.25f, 0.46f), o(0.24f, 0.32f), o(0.31f, 0.18f),
            )),
            // Dorso nasal desplazado hacia el lado visible
            open(listOf(o(0.53f, 0.38f), o(0.58f, 0.46f), o(0.70f, 0.53f), o(0.66f, 0.56f), o(0.61f, 0.555f))),
            open(listOf(o(0.42f, 0.70f), o(0.40f, 0.84f), o(0.20f, 0.92f), o(0.04f, 0.95f))),
            open(listOf(o(0.60f, 0.70f), o(0.62f, 0.84f), o(0.80f, 0.91f), o(0.96f, 0.94f))),
        ),
        lines = listOf(
            vLine(0.70f, 0.10f, 0.80f), // eje: punta nasal alineada con mejilla
            hLine(0.37f, 0.15f, 0.85f),
            hLine(0.42f, 0.15f, 0.85f),
            hLine(0.56f, 0.2f, 0.8f),
        ),
        marks = listOf(o(0.40f, 0.42f), o(0.64f, 0.42f)),
    )

    GuideType.FACE_PROFILE -> Guide(
        contours = listOf(
            open(listOf(
                o(0.30f, 0.12f), o(0.50f, 0.12f), o(0.62f, 0.22f), o(0.65f, 0.33f),
                o(0.645f, 0.38f), o(0.69f, 0.45f), o(0.74f, 0.50f), o(0.67f, 0.535f),
                o(0.685f, 0.575f), o(0.665f, 0.60f), o(0.68f, 0.63f), o(0.65f, 0.665f),
                o(0.67f, 0.71f), o(0.61f, 0.76f), o(0.55f, 0.78f), o(0.55f, 0.95f),
            )),
            open(listOf(o(0.30f, 0.12f), o(0.22f, 0.25f), o(0.22f, 0.42f), o(0.29f, 0.58f), o(0.34f, 0.70f), o(0.34f, 0.95f))),
            // Oreja
            closed(listOf(o(0.40f, 0.40f), o(0.45f, 0.39f), o(0.46f, 0.46f), o(0.43f, 0.53f), o(0.39f, 0.50f))),
        ),
        lines = listOf(
            hLine(0.42f, 0.10f, 0.90f), // plano de Frankfort
            vLine(0.65f, 0.08f, 0.85f), // vertical verdadera por glabela
            hLine(0.535f, 0.55f, 0.90f), // subnasal
        ),
        marks = listOf(o(0.395f, 0.42f), o(0.585f, 0.42f)), // tragion / orbitale
    )

    GuideType.FOREHEAD -> Guide(
        contours = listOf(
            // Cejas
            open(listOf(o(0.12f, 0.70f), o(0.24f, 0.63f), o(0.38f, 0.64f), o(0.45f, 0.68f))),
            open(listOf(o(0.88f, 0.70f), o(0.76f, 0.63f), o(0.62f, 0.64f), o(0.55f, 0.68f))),
            // Implantación capilar
            open(listOf(o(0.06f, 0.30f), o(0.20f, 0.17f), o(0.50f, 0.12f), o(0.80f, 0.17f), o(0.94f, 0.30f))),
            // Contorno lateral
            open(listOf(o(0.06f, 0.30f), o(0.05f, 0.60f), o(0.08f, 0.92f))),
            open(listOf(o(0.94f, 0.30f), o(0.95f, 0.60f), o(0.92f, 0.92f))),
        ),
        lines = listOf(vLine(0.5f), hLine(0.68f), hLine(0.40f, 0.15f, 0.85f)),
    )

    GuideType.PERIOCULAR -> Guide(
        contours = listOf(
            closed(listOf(o(0.12f, 0.52f), o(0.22f, 0.45f), o(0.34f, 0.46f), o(0.43f, 0.53f), o(0.32f, 0.57f), o(0.20f, 0.56f))),
            closed(listOf(o(0.88f, 0.52f), o(0.78f, 0.45f), o(0.66f, 0.46f), o(0.57f, 0.53f), o(0.68f, 0.57f), o(0.80f, 0.56f))),
            open(listOf(o(0.06f, 0.36f), o(0.20f, 0.29f), o(0.36f, 0.30f), o(0.45f, 0.35f))),
            open(listOf(o(0.94f, 0.36f), o(0.80f, 0.29f), o(0.64f, 0.30f), o(0.55f, 0.35f))),
            // Surco nasoyugal / ojera
            open(listOf(o(0.40f, 0.62f), o(0.28f, 0.70f), o(0.15f, 0.70f))),
            open(listOf(o(0.60f, 0.62f), o(0.72f, 0.70f), o(0.85f, 0.70f))),
        ),
        lines = listOf(vLine(0.5f), hLine(0.515f)),
        marks = listOf(o(0.275f, 0.515f), o(0.725f, 0.515f)),
    )

    GuideType.LIPS -> Guide(
        contours = listOf(
            // Labio superior
            open(listOf(o(0.18f, 0.52f), o(0.30f, 0.44f), o(0.42f, 0.39f), o(0.50f, 0.42f),
                o(0.58f, 0.39f), o(0.70f, 0.44f), o(0.82f, 0.52f))),
            // Labio inferior
            open(listOf(o(0.18f, 0.52f), o(0.30f, 0.60f), o(0.50f, 0.65f), o(0.70f, 0.60f), o(0.82f, 0.52f))),
            // Línea de cierre
            open(listOf(o(0.18f, 0.52f), o(0.35f, 0.515f), o(0.50f, 0.525f), o(0.65f, 0.515f), o(0.82f, 0.52f))),
            // Surcos nasogenianos
            open(listOf(o(0.24f, 0.18f), o(0.14f, 0.40f), o(0.10f, 0.62f))),
            open(listOf(o(0.76f, 0.18f), o(0.86f, 0.40f), o(0.90f, 0.62f))),
            // Base nasal
            open(listOf(o(0.36f, 0.20f), o(0.44f, 0.24f), o(0.50f, 0.23f), o(0.56f, 0.24f), o(0.64f, 0.20f))),
        ),
        lines = listOf(vLine(0.5f), hLine(0.52f)),
    )

    GuideType.NECK -> Guide(
        contours = listOf(
            open(listOf(o(0.20f, 0.04f), o(0.27f, 0.18f), o(0.40f, 0.27f), o(0.50f, 0.29f),
                o(0.60f, 0.27f), o(0.73f, 0.18f), o(0.80f, 0.04f))),
            open(listOf(o(0.34f, 0.24f), o(0.33f, 0.45f), o(0.30f, 0.58f), o(0.15f, 0.66f), o(0.03f, 0.72f))),
            open(listOf(o(0.66f, 0.24f), o(0.67f, 0.45f), o(0.70f, 0.58f), o(0.85f, 0.66f), o(0.97f, 0.72f))),
            // Clavículas
            open(listOf(o(0.45f, 0.64f), o(0.30f, 0.66f), o(0.12f, 0.70f))),
            open(listOf(o(0.55f, 0.64f), o(0.70f, 0.66f), o(0.88f, 0.70f))),
        ),
        lines = listOf(vLine(0.5f), hLine(0.29f, 0.2f, 0.8f), hLine(0.64f)),
        marks = listOf(o(0.5f, 0.62f)),
    )

    GuideType.TORSO_FRONT -> Guide(
        contours = listOf(torsoLeft, mirrorX(torsoLeft)).map { it to false },
        lines = listOf(vLine(0.5f), hLine(0.66f, 0.2f, 0.8f), hLine(0.90f, 0.15f, 0.85f)),
        marks = listOf(o(0.5f, 0.66f)),
    )

    GuideType.BREASTS -> Guide(
        contours = listOf(torsoLeft, mirrorX(torsoLeft)).map { it to false } + listOf(
            open(listOf(o(0.25f, 0.30f), o(0.26f, 0.42f), o(0.36f, 0.48f), o(0.46f, 0.43f))),
            open(listOf(o(0.75f, 0.30f), o(0.74f, 0.42f), o(0.64f, 0.48f), o(0.54f, 0.43f))),
        ),
        lines = listOf(vLine(0.5f), hLine(0.38f, 0.15f, 0.85f), hLine(0.47f, 0.2f, 0.8f)),
        marks = listOf(o(0.36f, 0.38f), o(0.64f, 0.38f), o(0.5f, 0.12f)),
    )

    GuideType.TORSO_SIDE -> Guide(
        contours = listOf(
            open(listOf(o(0.42f, 0.03f), o(0.40f, 0.20f), o(0.39f, 0.40f), o(0.37f, 0.55f),
                o(0.33f, 0.70f), o(0.34f, 0.82f), o(0.40f, 0.97f))),
            open(listOf(o(0.56f, 0.03f), o(0.62f, 0.15f), o(0.66f, 0.27f), o(0.60f, 0.36f),
                o(0.60f, 0.48f), o(0.64f, 0.62f), o(0.60f, 0.76f), o(0.58f, 0.97f))),
        ),
        lines = listOf(vLine(0.48f), hLine(0.62f, 0.2f, 0.8f), hLine(0.80f, 0.2f, 0.8f)),
        marks = listOf(o(0.64f, 0.62f)),
    )

    GuideType.BACK -> Guide(
        contours = listOf(torsoLeft, mirrorX(torsoLeft)).map { it to false } + listOf(
            // Escápulas
            open(listOf(o(0.30f, 0.16f), o(0.40f, 0.20f), o(0.40f, 0.33f), o(0.33f, 0.40f))),
            open(listOf(o(0.70f, 0.16f), o(0.60f, 0.20f), o(0.60f, 0.33f), o(0.67f, 0.40f))),
        ),
        lines = listOf(vLine(0.5f), hLine(0.40f, 0.2f, 0.8f), hLine(0.70f, 0.2f, 0.8f)),
    )

    GuideType.GLUTES -> Guide(
        contours = listOf(
            open(listOf(o(0.24f, 0.04f), o(0.20f, 0.22f), o(0.13f, 0.42f), o(0.14f, 0.62f), o(0.20f, 0.80f), o(0.24f, 0.97f))),
            open(listOf(o(0.76f, 0.04f), o(0.80f, 0.22f), o(0.87f, 0.42f), o(0.86f, 0.62f), o(0.80f, 0.80f), o(0.76f, 0.97f))),
            // Pliegues glúteos
            open(listOf(o(0.49f, 0.60f), o(0.40f, 0.66f), o(0.28f, 0.66f), o(0.18f, 0.62f))),
            open(listOf(o(0.51f, 0.60f), o(0.60f, 0.66f), o(0.72f, 0.66f), o(0.82f, 0.62f))),
            // Pliegue interglúteo
            open(listOf(o(0.50f, 0.30f), o(0.50f, 0.58f))),
            // Muslos (cara interna)
            open(listOf(o(0.49f, 0.66f), o(0.47f, 0.80f), o(0.46f, 0.97f))),
            open(listOf(o(0.51f, 0.66f), o(0.53f, 0.80f), o(0.54f, 0.97f))),
        ),
        lines = listOf(vLine(0.5f), hLine(0.30f, 0.15f, 0.85f), hLine(0.64f, 0.1f, 0.9f)),
    )

    GuideType.LEGS -> Guide(
        contours = listOf(legLeft, mirrorX(legLeft), legLeftInner, mirrorX(legLeftInner)).map { it to false },
        lines = listOf(vLine(0.5f), hLine(0.58f, 0.1f, 0.9f), hLine(0.08f, 0.1f, 0.9f)),
        marks = listOf(o(0.31f, 0.58f), o(0.69f, 0.58f)),
    )

    GuideType.HANDS, GuideType.FEET -> Guide(emptyList(), listOf(vLine(0.5f)))
    GuideType.FREE -> Guide(emptyList(), emptyList())

    GuideType.ARM -> Guide(
        // Brazo en abducción (vista posterior): hombro a la izquierda, mano hacia arriba
        contours = listOf(
            open(listOf(o(0.04f, 0.42f), o(0.25f, 0.40f), o(0.50f, 0.42f), o(0.70f, 0.44f), o(0.76f, 0.40f), o(0.78f, 0.20f), o(0.79f, 0.04f))),
            open(listOf(o(0.04f, 0.62f), o(0.25f, 0.64f), o(0.50f, 0.60f), o(0.72f, 0.58f), o(0.86f, 0.52f), o(0.90f, 0.30f), o(0.89f, 0.04f))),
        ),
        lines = listOf(hLine(0.52f), vLine(0.80f, 0.04f, 0.70f)),
        marks = listOf(o(0.82f, 0.56f)),
    )
}

private val torsoLeft = listOf(
    o(0.40f, 0.03f), o(0.38f, 0.08f), o(0.20f, 0.12f), o(0.14f, 0.18f),
    o(0.19f, 0.32f), o(0.24f, 0.50f), o(0.26f, 0.62f), o(0.22f, 0.78f), o(0.19f, 0.97f),
)

private val legLeft = listOf(
    o(0.14f, 0.03f), o(0.13f, 0.20f), o(0.19f, 0.42f), o(0.24f, 0.58f), o(0.22f, 0.72f), o(0.27f, 0.90f), o(0.30f, 0.97f),
)
private val legLeftInner = listOf(
    o(0.49f, 0.10f), o(0.44f, 0.30f), o(0.40f, 0.50f), o(0.39f, 0.60f), o(0.40f, 0.75f), o(0.38f, 0.97f),
)


/**
 * Guía del modo libre: el borde exterior de lo visible (trazo con halo, en el color de la guía) o,
 * si no se pudo aislar un sujeto, el dibujo lineal de sus bordes dominantes.
 */
@Composable
private fun FreeGuide(free: com.photomed.app.align.FreeContour?, showContour: Boolean, showGrid: Boolean, modifier: Modifier, tone: GuideTone) {
    val edgeBmp = remember(free) {
        val f = free?.takeIf { it.edges != null } ?: return@remember null
        val px = IntArray(f.edgeW * f.edgeH) { if (f.edges!![it]) 0xFFFFFFFF.toInt() else 0 }
        android.graphics.Bitmap.createBitmap(px, f.edgeW, f.edgeH, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Canvas(modifier) {
        if (showGrid) drawGrid()
        if (!showContour || free == null) return@Canvas
        if (free.outline.size >= 3) {
            val path = Path().apply {
                moveTo(free.outline[0].x * size.width, free.outline[0].y * size.width)
                free.outline.drop(1).forEach { lineTo(it.x * size.width, it.y * size.width) }
                close()
            }
            drawPath(path, Color.Black.copy(alpha = 0.45f), style = Stroke(5.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(path, tone.color, style = Stroke(2.5f.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
        } else if (edgeBmp != null) {
            drawImage(edgeBmp, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                colorFilter = ColorFilter.tint(tone.color), filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
        }
    }
}
