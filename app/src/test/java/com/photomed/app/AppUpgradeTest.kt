package com.photomed.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Versión nueva: se borran las cachés de la anterior y se conservan los datos del usuario. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpgradeTest {
    @Test fun clearsCachesOnlyWhenTheVersionChanges() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        fun touch(f: File) = f.apply { parentFile!!.mkdirs(); writeText("x") }
        AppUpgrade.onStart(ctx, 1)
        val img = touch(File(ctx.cacheDir, "image_cache/abc"))
        val mem = touch(File(ctx.filesDir, "lineas/abc.json"))
        val traces = touch(File(ctx.filesDir, "trazos/abc.json"))
        val records = touch(File(ctx.filesDir, "expedientes/e1/info.json"))
        val relief = touch(File(ctx.filesDir, "relieve/abc.png"))
        ctx.getSharedPreferences("photo_edits", Context.MODE_PRIVATE).edit().putString("u", "{}").commit()
        // misma versión: nada
        assertFalse(AppUpgrade.onStart(ctx, 1))
        assertTrue(img.exists() && mem.exists())
        // versión nueva: cachés fuera, datos del usuario intactos
        assertTrue(AppUpgrade.onStart(ctx, 2))
        assertFalse(img.exists()); assertFalse(mem.exists())
        assertTrue(traces.exists() && records.exists() && relief.exists())
        assertTrue(ctx.getSharedPreferences("photo_edits", Context.MODE_PRIVATE).contains("u"))
        assertFalse(AppUpgrade.onStart(ctx, 2))
    }
}
