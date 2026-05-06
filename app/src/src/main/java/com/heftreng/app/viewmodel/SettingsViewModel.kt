package com.heftreng.app.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
) : ViewModel() {

    val isAdmin: Boolean
        get() = auth.currentUser?.email == "siirgibi49@gmail.com"

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hf_settings", Context.MODE_PRIVATE)

    private val _darkMode       = MutableStateFlow(prefs.getBoolean("hf_theme_dark", true))
    val darkMode = _darkMode.asStateFlow()

    private val _language       = MutableStateFlow(prefs.getString("hf_lang", "tr") ?: "tr")
    val language = _language.asStateFlow()

    private val _pushEnabled    = MutableStateFlow(prefs.getBoolean("hf_push", true))
    val pushEnabled = _pushEnabled.asStateFlow()

    private val _privateAccount = MutableStateFlow(prefs.getBoolean("hf_private", false))
    val privateAccount = _privateAccount.asStateFlow()

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

    fun togglePush() {
        viewModelScope.launch {
            val next = !_pushEnabled.value
            _pushEnabled.value = next
            prefs.edit().putBoolean("hf_push", next).apply()
        }
    }

    fun togglePrivate() {
        viewModelScope.launch {
            val next = !_privateAccount.value
            _privateAccount.value = next
            prefs.edit().putBoolean("hf_private", next).apply()
        }
    }
}
