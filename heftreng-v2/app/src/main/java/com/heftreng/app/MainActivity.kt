package com.heftreng.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.heftreng.app.navigation.HeftrangNavHost
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsVm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkMode by settingsVm.darkMode.collectAsState()
            val accent   by settingsVm.accent.collectAsState()
            val fontSize by settingsVm.fontSize.collectAsState()

            // Global state güncelle — tüm ekranlar bu değerleri okur
            AppDark     = darkMode
            AppAccent   = accent
            AppFontSize = fontSize

            HeftrangTheme(darkMode = darkMode, accent = accent) {
                HeftrangNavHost(settingsVm = settingsVm)
            }
        }
    }
}
