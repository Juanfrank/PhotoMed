package com.photomed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.photomed.app.data.PhotoRepository
import com.photomed.app.ui.AppNavigation
import com.photomed.app.ui.LocalRepository
import com.photomed.app.ui.theme.PhotoMedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = PhotoRepository(applicationContext)
        setContent {
            PhotoMedTheme {
                CompositionLocalProvider(LocalRepository provides repository) {
                    AppNavigation()
                }
            }
        }
    }
}
