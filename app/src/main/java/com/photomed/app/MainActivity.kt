package com.photomed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.photomed.app.data.PhotoRepository
import com.photomed.app.ui.AppNavigation
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.theme.PhotoMedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = PhotoRepository.create(applicationContext)
        // Versión 1 guardaba las fotos en almacenamiento privado: se mueven a la galería.
        PhotoRepository.legacyRoot(applicationContext)?.let { legacy ->
            if (legacy.exists()) lifecycleScope.launch(Dispatchers.IO) { runCatching { repository.migrateLegacy(legacy) } }
        }
        setContent {
            PhotoMedTheme {
                CompositionLocalProvider(LocalRepository provides repository) {
                    AppNavigation()
                }
            }
        }
    }
}
