package com.heftreng.app.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// Top-level extension — dosyanın en üstünde, class dışında olmalı
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hf_settings")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        val KEY_DARK = booleanPreferencesKey("hf_theme_dark")
        val KEY_LANG = stringPreferencesKey("hf_lang")
    }

    private val _darkMode = MutableStateFlow(true)
    val darkMode = _darkMode.asStateFlow()

    private val _language = MutableStateFlow("tr")   // "tr" | "ku"
    val language = _language.asStateFlow()

    init {
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                _darkMode.value = prefs[KEY_DARK] ?: true
                _language.value = prefs[KEY_LANG]  ?: "tr"
            }
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val next = !_darkMode.value
            _darkMode.value = next
            context.dataStore.edit { it[KEY_DARK] = next }
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            _language.value = lang
            context.dataStore.edit { it[KEY_LANG] = lang }
        }
    }
}
