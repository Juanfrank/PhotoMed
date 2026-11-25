package com.photomed.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.photomed.app.align.AnnotLayer
import com.photomed.app.align.AtomicFile
import com.photomed.app.align.ConfirmedTraces
import com.photomed.app.align.LineMetrics
import com.photomed.app.align.Pt
import com.photomed.app.align.StoredTrace
import com.photomed.app.align.TraceSet
import com.photomed.app.data.FilePhotoStore
import com.photomed.app.data.PendingPhotos
import com.photomed.app.data.PhotoLocks
import com.photomed.app.data.PhotoRepository
import com.photomed.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Tanda 1 de la revisión integral: integridad de los datos clínicos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataIntegrityTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val forgotten = mutableListOf<String>()

    private fun repo() = PhotoRepository(File(tmp.root, "private"), FilePhotoStore(File(tmp.root, "Pictures/PhotoMed")),
        openStream = { uri -> File(uri.path!!).inputStream() }, forgetPhoto = { forgotten += it.toString() })

    private fun jpeg(name: String = "c.jpg"): File = File(tmp.root, name).also {
        it.writeBytes(javaClass.getResourceAsStream("/blank.jpg")!!.readBytes())
    }

    /** expediente.json dañado: se reconstruye desde el XMP de sus fotos en lugar de desaparecer. */
    @Test fun corruptExpedienteIsRebuiltFromItsPhotos() {
        val r = repo()
        val exp = r.createExpediente("Ana Pérez", "001", "Toxina", consent = true)
        r.savePhoto(exp, Zone.LIPS, jpeg(), takenAt = 1_000_000L)
        File(exp.metaDir, "expediente.json").writeText("{\"patientName\": \"Ana")   // escritura cortada a medias
        val back = r.listExpedientes().single { it.id == exp.id }
        assertEquals("Ana Pérez", back.patientName)
        assertEquals(1, back.photoCount)
        assertTrue(exp.metaDir.listFiles()!!.any { it.name.startsWith("expediente.json.danado-") })
    }

    /** Los datos privados de una foto solo se borran con la foto (y también al borrar el expediente). */
    @Test fun privateDataForgottenOnlyAfterTheDeletionIsDone() {
        val r = repo()
        val exp = r.createExpediente("Luis", "002", "", consent = true)
        val p1 = r.savePhoto(exp, Zone.LIPS, jpeg(), takenAt = 1_000_000L)
        val p2 = r.savePhoto(exp, Zone.FACE_FRONTAL, jpeg(), takenAt = 2_000_000L)
        r.deletePhotos(listOf(p1))
        assertEquals(listOf(p1.uri.toString()), forgotten)
        r.deleteExpediente(r.getExpediente(exp.id)!!)
        assertEquals(listOf(p1.uri.toString(), p2.uri.toString()), forgotten)
    }

    /** Dos cambios de trazos a la vez sobre la misma foto: ninguno se pierde (antes ganaba el último). */
    @Test fun concurrentTraceEditsAreNotLost() {
        val uri = "file://" + File(tmp.root, "no-existe.jpg").path
        val threads = (0 until 16).map { i ->
            Thread {
                PhotoLocks.on(uri) {
                    val set = ConfirmedTraces.of(ctx, uri) ?: TraceSet(4f / 3f, emptyList())
                    Thread.sleep(2)
                    ConfirmedTraces.save(ctx, uri, ConfirmedTraces.add(set, StoredTrace(AnnotLayer.FOLDS, listOf(Pt(0f, 0f), Pt(1f, i / 16f)),
                        false, false, false, LineMetrics(ConfirmedTraces.nextAddedName(set)), "añadido")))
                }
            }
        }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(16, ConfirmedTraces.of(ctx, uri)!!.traces.size)
    }

    /** «Regenerar» sin poder reescribir la foto: la marca de borrado impide que vuelvan solos. */
    @Test fun clearedTracesStayClearedWhenThePhotoCannotBeRewritten() {
        val uri = "file://" + File(tmp.root, "solo-lectura.jpg").path
        ConfirmedTraces.save(ctx, uri, TraceSet(4f / 3f, listOf(StoredTrace(AnnotLayer.FOLDS, listOf(Pt(0f, 0f), Pt(1f, 1f)), false, false, false, LineMetrics("x")))))
        assertFalse(ConfirmedTraces.clear(ctx, uri))
        assertNull(ConfirmedTraces.of(ctx, uri))
    }

    /** Toma que no se pudo guardar en la galería: se conserva y se guarda al reintentar. */
    @Test fun pendingPhotoIsKeptAndRetried() {
        val r = repo()
        val exp = r.createExpediente("Eva", "003", "", consent = true)
        val kept = PendingPhotos.keep(ctx, exp.id, Zone.FOREHEAD, 5_000_000L, jpeg("toma.jpg"))!!
        assertTrue(kept.exists())
        assertEquals(1, PendingPhotos.count(ctx, exp.id))
        assertEquals(1, PendingPhotos.retry(ctx, r, exp))
        assertEquals(0, PendingPhotos.count(ctx, exp.id))
        assertEquals(1, r.photos(exp, Zone.FOREHEAD).size)
    }

    @Test fun atomicWriteReplacesTheFile() {
        val f = File(tmp.root, "a/b.json")
        AtomicFile.write(f, "uno"); AtomicFile.write(f, "dos")
        assertEquals("dos", f.readText())
        assertTrue(f.parentFile!!.listFiles()!!.none { it.name.contains(".tmp-") })
    }
}
