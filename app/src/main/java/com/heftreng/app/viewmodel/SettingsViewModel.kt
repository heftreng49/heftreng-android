package com.heftreng.app.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// DataStore dependency olmadığı için SharedPreferences kullanıyoruz.
// DataStore eklemek istersen build.gradle.kts'e şunu ekle:
//   implementation("androidx.datastore:datastore-preferences:1.1.1")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hf_settings", Context.MODE_PRIVATE)

    private val _darkMode = MutableStateFlow(prefs.getBoolean("hf_theme_dark", true))
    val darkMode = _darkMode.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString("hf_lang", "tr") ?: "tr")
    val language = _language.asStateFlow()

    fun toggleDarkMode() {
        viewModelScope.launch {
            val next = !_darkMode.value
            _darkMode.value = next
            prefs.edit().putBoolean("hf_theme_dark", next).apply()
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            _language.value = lang
            prefs.edit().putString("hf_lang", lang).apply()
        }
    }
}
