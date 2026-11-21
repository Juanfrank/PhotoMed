package com.photomed.app

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.Annotations
import com.photomed.app.align.ConfirmedTraces
import com.photomed.app.align.FaceAnnotations
import com.photomed.app.align.ImageEdit
import com.photomed.app.align.InkSet
import com.photomed.app.align.InkStroke
import com.photomed.app.align.Pt
import com.photomed.app.align.TraceFrame
import com.photomed.app.align.TraceSet
import com.photomed.app.data.CaptureDate
import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.hypot

/** Registro de errores, fecha de captura editable, anotaciones y surcos regulares. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V230Test {
    @get:Rule val tmp = TemporaryFolder()
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun crashesAreLoggedAndAnnounced() {
        CrashLog.install(ctx); CrashLog.clear(ctx); CrashLog.markSeen(ctx)
        Thread.sleep(5)
        CrashLog.write("Error en segundo plano", IllegalStateException("prueba"))
        assertTrue(CrashLog.unseen(ctx).isEmpty())          // no cerró la app: no se avisa al abrir
        val f = CrashLog.write("Cierre de la app", RuntimeException("boom"), fatal = true)!!
        assertTrue(f.readText().contains("RuntimeException: boom"))
        assertEquals(1, CrashLog.unseen(ctx).size)
        assertEquals(2, CrashLog.all(ctx).size)
        CrashLog.markSeen(ctx); assertTrue(CrashLog.unseen(ctx).isEmpty())
    }

    @Test fun captureDateEditedKeepsTheTracesAndNotes() {
        val repo = PhotoRepository(File(tmp.root, "p"), FilePhotoStore(File(tmp.root, "g")), openStream = { File(it.path!!).inputStream() })
        val exp = repo.createExpediente("A", "1", "", consent = true)
        val jpeg = File(tmp.root, "c.jpg").apply { writeBytes(this@V230Test.javaClass.getResourceAsStream("/blank.jpg")!!.readBytes()) }
        val photo = repo.savePhoto(exp, Zone.FACE_FRONTAL, jpeg, takenAt = 1_763_400_000_000L, variant = "exp-sonrisa")
        val u = photo.uri.toString()
        ConfirmedTraces.save(ctx, u, TraceSet(4f / 3f, emptyList()))
        Annotations.save(ctx, u, InkSet(4f / 3f, listOf(InkStroke(-65536, 0.01f, listOf(Pt(0.1f, 0.1f), Pt(0.2f, 0.2f))))))
        val t = 1_700_000_000_000L
        assertTrue(CaptureDate.write(ctx, photo.uri, t))
        val np = repo.setCaptureDate(exp, photo, t)
        com.photomed.app.align.PhotoKeys.move(ctx, u, np.uri.toString())
        val list = repo.photos(exp, Zone.FACE_FRONTAL)
        assertEquals(listOf(t), list.map { it.takenAt })
        assertTrue(list.single().displayName.startsWith("rostro_frontal_exp-sonrisa_2023"))
        assertEquals(com.photomed.app.data.FacialExpression.SMILE, com.photomed.app.data.FacialExpression.ofFileName(list.single().displayName))
        assertNotNull(ExifInterface(File(np.uri.path!!)).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("A", repo.readInfo(np)!!.patientName)
        // los datos de la foto siguen con ella
        assertNotNull(ConfirmedTraces.of(ctx, np.uri.toString()))
        assertEquals(1, Annotations.of(ctx, np.uri.toString())!!.strokes.size)
    }

    @Test fun notesFollowEditsAndErase() {
        val s = InkSet(4f / 3f, listOf(InkStroke(-65536, 0.02f, listOf(Pt(0.3f, 0.3f), Pt(0.5f, 0.3f))),
            InkStroke(-16711936, 0.02f, listOf(Pt(0.3f, 0.7f), Pt(0.5f, 0.7f))), InkStroke(0, 0.05f, listOf(Pt(0.4f, 0.3f)), erase = true)))
        val back = Annotations.read(Annotations.write(s))!!
        assertEquals(3, back.strokes.size); assertTrue(back.strokes[2].erase)
        // girada 90°: el trazo horizontal queda vertical y conserva su grosor relativo
        val v = back.inView(ImageEdit(rot = 1), TraceFrame.FULL)[0]
        assertEquals(v.points[0].x, v.points[1].x, 1e-4f)
        assertTrue(v.width > 0f)
        // borrador por trazo: quita solo el que toca
        val left = Annotations.eraseStrokes(back, listOf(Pt(0.4f, 0.71f)), 0.01f)
        assertEquals(listOf(-65536, 0), left.strokes.map { it.color })
    }

    @Test fun foldWithoutIrregularTail() {
        val axis = (0..10).map { Pt(0.4f + it * 0.01f, 0.5f + it * 0.02f) }
        val good = (0..20).map { Pt(0.4f + it * 0.005f, 0.5f + it * 0.01f) }
        // la cola se tuerce hacia un lado
        val tail = (1..8).map { Pt(0.5f + it * 0.012f, 0.7f - it * 0.002f) }
        val out = FaceAnnotations.regularize(good + tail, axis)
        assertTrue("se conservó la cola irregular", out.last().x < 0.53f)
        assertTrue(com.photomed.app.align.LineMeasure.length(out) > 0.8f * com.photomed.app.align.LineMeasure.length(good))
        assertTrue(hypot(out.first().x - 0.4f, out.first().y - 0.5f) < 1e-3f)
    }
}
