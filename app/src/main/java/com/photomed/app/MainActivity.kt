package com.photomed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.photomed.app.data.PhotoRepository
import com.photomed.app.ui.AppNavigation
import com.photomed.app.ui.CrashNotice
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.theme.PhotoMedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleShared(intent)
    }

    /** Protocolo recibido («Abrir con» o «Compartir con» PhotoMed): se importa y se avisa. */
    private fun handleShared(intent: android.content.Intent?) {
        val i = intent ?: return
        val uri: android.net.Uri = when (i.action) {
            android.content.Intent.ACTION_VIEW -> i.data
            android.content.Intent.ACTION_SEND -> androidx.core.content.IntentCompat.getParcelableExtra(i, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            else -> null
        } ?: return
        lifecycleScope.launch {
            val msg = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { "Protocolo «${com.photomed.app.data.Protocols.import(applicationContext, uri).name}» importado: ábralo desde un expediente" }
                    .getOrElse { it.message ?: "No se pudo importar el archivo" }
            }
            android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
        }
        // una sola vez (no al recrear la actividad)
        i.action = null
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        // idioma elegido en Ajustes (antes de Android 13; después lo aplica el sistema)
        super.attachBaseContext(com.photomed.app.i18n.AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.photomed.app.i18n.I18n.init(applicationContext.createConfigurationContext(resources.configuration))
        // ningún cierre silencioso: los errores se guardan y se avisan al volver a abrir
        CrashLog.install(applicationContext)
        CrashLog.checkExitReasons(applicationContext)
        // versión nueva: fuera las cachés de la anterior (formatos que pueden haber cambiado)
        AppUpgrade.onStart(applicationContext)
        // sensibilidad de la detección de arrugas elegida en los ajustes
        com.photomed.app.align.WrinkleTuning.load(applicationContext)
        runCatching { com.photomed.app.data.BuiltInProtocols.seed(applicationContext) }
        com.photomed.app.ui.theme.AppTheme.load(applicationContext)
        enableEdgeToEdge()
        val repository = PhotoRepository.create(applicationContext)
        // Versión 1 guardaba las fotos en almacenamiento privado: se mueven a la galería.
        PhotoRepository.legacyRoot(applicationContext)?.let { legacy ->
            if (legacy.exists()) lifecycleScope.launch(Dispatchers.IO) { runCatching { repository.migrateLegacy(legacy) } }
        }
        handleShared(intent)
        setContent {
            PhotoMedTheme {
                CompositionLocalProvider(LocalRepository provides repository) {
                    AppNavigation()
                    CrashNotice()
                }
            }
        }
    }
}
