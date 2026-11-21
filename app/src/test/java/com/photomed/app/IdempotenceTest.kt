package com.photomed.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AnnotLine
import com.photomed.app.align.FaceAnnotations
import com.photomed.app.align.FaceKeys
import com.photomed.app.align.GrayImage
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.ImageOps
import com.photomed.app.align.LabImage
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.MeasureNumbering
import com.photomed.app.align.PhotoEdits
import com.photomed.app.align.Pt
import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.ImportItem
import com.photomed.app.data.PhotoImport
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Zone
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Idempotencia de las funciones de la app: repetir una operación da lo mismo que hacerla una vez,
 * y los ciclos (girar 4 veces, espejo 2 veces) vuelven al estado inicial.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdempotenceTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun sameEdit(a: ImageEdit, b: ImageEdit) {
        assertEquals(a.rot, b.rot); assertEquals(a.mirror, b.mirror)
        val ca = a.crop; val cb = b.crop
        if (ca == null || cb == null) assertEquals(ca == null, cb == null) else assertArrayEquals(ca, cb, 1e-5f)
    }

    /** Imagen 40 × 30 con cuadrantes de colores distintos (para ver giros, espejos y recortes). */
    private fun quadrants(): Bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until 30) for (x in 0 until 40) setPixel(x, y, when {
            x < 20 && y < 15 -> Color.RED; x >= 20 && y < 15 -> Color.GREEN; x < 20 -> Color.BLUE; else -> Color.YELLOW })
    }

    private fun rot90(b: Bitmap, cw: Boolean): Bitmap =
        Bitmap.createBitmap(b, 0, 0, b.width, b.height, android.graphics.Matrix().apply { postRotate(if (cw) 90f else -90f) }, true)

    /** Misma imagen (±1 px de tamaño por el redondeo del recorte), comparada en posiciones relativas lejos de los bordes de color. */
    private fun samePixels(a: Bitmap, b: Bitmap) {
        assertTrue("${a.width}×${a.height} vs ${b.width}×${b.height}", kotlin.math.abs(a.width - b.width) <= 1 && kotlin.math.abs(a.height - b.height) <= 1)
        for (fy in listOf(0.1f, 0.3f, 0.7f, 0.9f)) for (fx in listOf(0.1f, 0.3f, 0.7f, 0.9f)) {
            val pa = a.getPixel((fx * (a.width - 1)).toInt(), (fy * (a.height - 1)).toInt())
            val pb = b.getPixel((fx * (b.width - 1)).toInt(), (fy * (b.height - 1)).toInt())
            assertEquals("($fx,$fy)", pa, pb)
        }
    }

    @Test fun rotateFourTimesAndMirrorTwiceKeepTheEditAndItsCrop() {
        for (mirror in listOf(false, true)) {
            val e = ImageEdit(rot = 1, mirror = mirror, crop = floatArrayOf(0.1f, 0.2f, 0.6f, 0.9f))
            sameEdit(e, e.rotated().rotated().rotated().rotated())
            sameEdit(e, e.mirrored().mirrored())
            // el recorte gira con la imagen: el resultado es la imagen recortada, girada en pantalla
            // (horario sin espejo; con espejo, el giro de la foto se ve antihorario)
            val src = quadrants()
            samePixels(rot90(e.apply(src), cw = !mirror), e.rotated().apply(src))
        }
    }

    @Test fun savingTheSameEditOrTemplateTwiceChangesNothing() {
        val uri = "content://x/1"
        val e = ImageEdit(rot = 2, mirror = true)
        PhotoEdits.set(ctx, uri, e); PhotoEdits.set(ctx, uri, e)
        PhotoEdits.clearCache()
        sameEdit(e, PhotoEdits.of(ctx, uri))
        // sembrar los protocolos predefinidos dos veces no los duplica
        val prefs = ctx.getSharedPreferences("photomed", Context.MODE_PRIVATE)
        com.photomed.app.data.BuiltInProtocols.seed(ctx)
        prefs.edit().remove("protocols_builtin_v1").commit()
        com.photomed.app.data.BuiltInProtocols.seed(ctx)
        assertEquals(1, com.photomed.app.data.Protocols.list(ctx).count { it.name == "Rostro 5 vistas" })
    }

    @Test fun cropAndCanonicalKeysAreIdempotent() {
        val b = Bitmap.createScaledBitmap(quadrants(), 400, 300, true)
        val once = ImageOps.centerCrop34(b, 120)
        samePixels(once, ImageOps.centerCrop34(once, 120))
        val pts = (0 until 36).associate { "oval_$it" to Pt(0.5f + 0.3f * kotlin.math.cos(it * 0.17f), 0.6f + 0.4f * kotlin.math.sin(it * 0.17f)) } +
            mapOf("eye_l_c" to Pt(0.6f, 0.5f), "eye_r_c" to Pt(0.4f, 0.5f))
        val c = FaceKeys.canonical(pts)
        assertEquals(c, FaceKeys.canonical(c))
    }

    @Test fun importingTheSamePhotoTwiceAddsItOnce() = runBlocking {
        val repo = PhotoRepository(File(tmp.root, "private"), FilePhotoStore(File(tmp.root, "Pictures/PhotoMed")),
            openStream = { uri -> File(uri.path!!).inputStream() })
        val exp = repo.createExpediente("Paciente", "1", "", consent = true)
        val bmp = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF886655.toInt()) }
        val a = ImportItem(Uri.parse("content://a/1"), 1_700_000_000_000L, true, Zone.FACE_FRONTAL)
        val b = ImportItem(Uri.parse("content://a/2"), 1_700_000_000_000L, true, Zone.LIPS)
        val hashes = mapOf(a.uri to "h1", b.uri to "h2")
        val first = PhotoImport.importAll(ctx, repo, exp, listOf(a, b, a), decode = { bmp }, hashOf = { hashes[it] })
        assertEquals(2, first.saved.size); assertEquals(1, first.duplicates)   // repetida en la misma tanda
        val again = PhotoImport.importAll(ctx, repo, exp, listOf(a, b), decode = { bmp }, hashOf = { hashes[it] })
        assertEquals(0, again.saved.size); assertEquals(2, again.duplicates)
        assertEquals(2, repo.photos(exp).size)
    }

    @Test fun numberingDoesNotDependOnPhotoOrderOrRepetition() {
        fun l(layer: AnnotLayer, name: String) = AnnotLine(layer, listOf(Pt(0f, 0f), Pt(1f, 1f)), false, metrics = LineMetrics(name))
        val a = listOf(l(AnnotLayer.FOLDS, "Nasogeniano derecho"), l(AnnotLayer.FOREHEAD, "Frente, línea 1"),
            AnnotLine(AnnotLayer.LIPS, listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f)), true, metrics = LineMetrics("Labio superior", features = listOf("Alto, mm" to 7f))))
        val b = listOf(l(AnnotLayer.FOLDS, "Marioneta derecho"), l(AnnotLayer.FOLDS, "Nasogeniano derecho"))
        val n1 = MeasureNumbering.of(listOf(a, b)); val n2 = MeasureNumbering.of(listOf(b, a)); val n3 = MeasureNumbering.of(listOf(a, b, a, b))
        assertEquals(n1, n2); assertEquals(n1, n3)
        assertEquals(setOf(1, 2, 3, 4), n1.values.toSet())
        // rasgos después de las arrugas
        assertEquals(4, n1["Labio superior"])
    }

    /** El análisis de una foto es determinista: dos veces sobre la misma imagen, las mismas líneas y medidas. */
    @Test fun analysisIsDeterministic() {
        val json = javaClass.getResourceAsStream("/stock/new/mesh.json")?.bufferedReader()?.readText()
        assumeTrue("sin retratos", json != null)
        val k = "px_12596614"
        val arr = JSONObject(json!!).getJSONArray(k)
        val mesh = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Pt(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        val img = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/stock/new/$k.jpg"))
        val sw = 480; val sh = img.height * sw / img.width
        val px = IntArray(sw * sh).also { Bitmap.createScaledBitmap(img, sw, sh, true).getPixels(it, 0, sw, 0, 0, sw, sh) }
        fun run(): List<AnnotLine> {
            val lum = GrayImage(sw, sh, FloatArray(px.size) { i -> val c = px[i]; (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f })
            return FaceAnnotations.fromMesh(mesh, null, lum, LabImage.fromArgb(px, sw, sh))
        }
        val r1 = run(); val r2 = run()
        assertEquals(r1.size, r2.size)
        for (i in r1.indices) { assertEquals(r1[i].points, r2[i].points); assertEquals(r1[i].metrics, r2[i].metrics) }
        assertTrue(r1.any { it.metrics?.isFeature == true })
    }
}
