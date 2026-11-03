package com.trackit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trackit.app.ui.AppRoot
import com.trackit.app.ui.theme.TrackitTheme
import com.trackit.app.ui.viewmodel.AppViewModel
import com.trackit.app.ui.viewmodel.VmFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = VmFactory((application as TrackitApp).container)
        setContent {
            val app: AppViewModel = viewModel(factory = factory)
            val dark by app.darkMode.collectAsStateWithLifecycle()
            TrackitTheme(darkTheme = dark ?: isSystemInDarkTheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(app, factory)
                }
            }
        }
    }
}
