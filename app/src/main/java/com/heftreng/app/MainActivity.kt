package com.heftreng.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.heftreng.app.navigation.HeftrangNavHost
import com.heftreng.app.ui.theme.HeftrangTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Tema NavHost içinde isDark flow'una göre dinamik wrap ediliyor.
            // Burada sadece başlangıç wrapper — dark default.
            HeftrangTheme(darkMode = true) {
                HeftrangNavHost()
            }
        }
    }
}
