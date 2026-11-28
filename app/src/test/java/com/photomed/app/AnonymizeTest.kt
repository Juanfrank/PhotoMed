package com.photomed.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.photomed.app.data.Photo
import com.photomed.app.data.PhotoMetadata
import com.photomed.app.data.Zone
import com.photomed.app.ui.gallery.anonymizedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Compartir sin datos del paciente: ningún metadato (tampoco cámara, fechas ni miniatura), orientación aplicada, día relativo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnonymizeTest {
    @Test fun anonymizedCopyHasNoMetadataAtAll() {
        val f = File.createTempFile("anon", ".jpg")
        val bmp = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(f).apply {
            setAttribute(ExifInterface.TAG_MAKE, "Marca"); setAttribute(ExifInterface.TAG_MODEL, "Modelo")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:20 10:00:00"); setAttribute(ExifInterface.TAG_XMP, "<x>Ana Pérez</x>")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes()
        }
        PhotoMetadata.anonymize(f)
        val e = ExifInterface(f)
        for (t in listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_XMP))
            assertNull("queda $t", e.getAttribute(t))
        assertFalse("queda la miniatura", e.hasThumbnail())
        assertFalse(f.readBytes().toString(Charsets.ISO_8859_1).contains("Ana"))
        // la orientación se aplica a los píxeles (20×40) en lugar de quedar en una etiqueta
        val out = BitmapFactory.decodeFile(f.path)
        assertEquals(20, out.width); assertEquals(40, out.height)
        f.delete()
    }

    @Test fun anonymizedNamesUseRelativeDays() {
        val day = 86_400_000L
        val a = Photo(android.net.Uri.parse("file:///x/a.jpg"), "a.jpg", Zone.LIPS, 1_763_400_000_000L)
        val b = Photo(android.net.Uri.parse("file:///x/b.jpg"), "b.jpg", Zone.LIPS, a.takenAt + 30 * day)
        assertEquals("PhotoMed_${Zone.LIPS.id}_dia0_01.jpg", anonymizedName(a, 1, a.takenAt))
        assertEquals("PhotoMed_${Zone.LIPS.id}_dia30_02.jpg", anonymizedName(b, 2, a.takenAt))
        assertTrue(!anonymizedName(b, base = a.takenAt).contains("2025") && !anonymizedName(b, base = a.takenAt).contains("2026"))
    }
}
