package com.heftreng.app.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heftreng.app.ui.theme.Amber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("hf_settings", Context.MODE_PRIVATE)

    private val _darkMode = MutableStateFlow(prefs.getBoolean("hf_dark", true))
    val darkMode = _darkMode.asStateFlow()

    private val _fontSize = MutableStateFlow(prefs.getInt("hf_font", 15))
    val fontSize = _fontSize.asStateFlow()

    private val _accent = MutableStateFlow(
        Color(prefs.getLong("hf_accent", Amber.value.toLong()))
    )
    val accent = _accent.asStateFlow()

    fun toggleDarkMode() {
        viewModelScope.launch {
            val next = !_darkMode.value
            _darkMode.value = next
            prefs.edit().putBoolean("hf_dark", next).apply()
        }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch {
            _fontSize.value = size.coerceIn(12, 22)
            prefs.edit().putInt("hf_font", _fontSize.value).apply()
        }
    }

    fun setAccent(color: Color) {
        viewModelScope.launch {
            _accent.value = color
            prefs.edit().putLong("hf_accent", color.value.toLong()).apply()
        }
    }
}
